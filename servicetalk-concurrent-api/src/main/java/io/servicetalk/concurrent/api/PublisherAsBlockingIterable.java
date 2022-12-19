/*
 * Copyright © 2018 Apple Inc. and the ServiceTalk project authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.servicetalk.concurrent.api;

import io.servicetalk.concurrent.BlockingIterable;
import io.servicetalk.concurrent.BlockingIterator;
import io.servicetalk.concurrent.PublisherSource.Subscriber;
import io.servicetalk.concurrent.PublisherSource.Subscription;
import io.servicetalk.concurrent.internal.ConcurrentSubscription;
import io.servicetalk.concurrent.internal.DelayedSubscription;
import io.servicetalk.concurrent.internal.QueueFullException;
import io.servicetalk.concurrent.internal.TerminalNotification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;
import java.util.concurrent.locks.LockSupport;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.SubscriberApiUtils.unwrapNullUnchecked;
import static io.servicetalk.concurrent.api.SubscriberApiUtils.wrapNull;
import static io.servicetalk.concurrent.internal.TerminalNotification.complete;
import static io.servicetalk.concurrent.internal.TerminalNotification.error;
import static io.servicetalk.utils.internal.PlatformDependent.newLinkedSpscQueue;
import static io.servicetalk.utils.internal.ThrowableUtils.throwException;
import static java.lang.Integer.getInteger;
import static java.lang.Long.getLong;
import static java.lang.Math.min;
import static java.lang.Thread.currentThread;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

/**
 * As returned by {@link Publisher#toIterable(int)} and {@link Publisher#toIterable()}.
 *
 * @param <T> Type of items emitted by the {@link Publisher} from which this {@link BlockingIterable} is created.
 */
final class PublisherAsBlockingIterable<T> implements BlockingIterable<T> {
    private static final int MAX_OUTSTANDING_DEMAND = 128;
    final Publisher<T> original;
    private final int queueCapacityHint;

    PublisherAsBlockingIterable(final Publisher<T> original) {
        this(original, 16);
    }

    PublisherAsBlockingIterable(final Publisher<T> original, int queueCapacityHint) {
        this.original = requireNonNull(original);
        if (queueCapacityHint <= 0) {
            throw new IllegalArgumentException("Invalid queueCapacityHint: " + queueCapacityHint + " (expected > 0).");
        }
        // Add a sane upper bound to the capacity to reduce buffering.
        this.queueCapacityHint = min(queueCapacityHint, MAX_OUTSTANDING_DEMAND);
    }

    @Override
    public BlockingIterator<T> iterator() {
        SubscriberAndIterator<T> subscriberAndIterator = new SubscriberAndIterator<>(queueCapacityHint);
        original.subscribeInternal(subscriberAndIterator);
        return subscriberAndIterator;
    }

    private static final class SubscriberAndIterator<T> implements Subscriber<T>, BlockingIterator<T> {
        private static final Logger LOGGER = LoggerFactory.getLogger(SubscriberAndIterator.class);
        private static final Object CANCELLED_SIGNAL = new Object();
        private static final TerminalNotification COMPLETE_NOTIFICATION = complete();
        private final BlockingQueue<Object> data;
        private final DelayedSubscription subscription = new DelayedSubscription();
        private final int requestN;
        /**
         * Number of items to emit from {@link #next()} till we request more.
         * Alternatively we can {@link Subscription#request(long) request(1)} every time we emit an item.
         * This approach aids batch production of data without sacrificing responsiveness.
         * Assumption here is that the source does not necessarily wait to produce all data before emitting hence
         * {@link Subscription#request(long) request(n)} should be as fast as
         * {@link Subscription#request(long) request(1)}.
         * <p>
         * Only accessed from {@link Iterator} methods and not from {@link Subscriber} methods (after initialization).
         */
        private int itemsToNextRequest;

        /**
         * Next item to return from {@link #next()}
         */
        @Nullable
        private Object next;
        private boolean terminated;

        SubscriberAndIterator(int queueCapacity) {
            requestN = queueCapacity;
            data = new SpscBlockingQueue<>(newLinkedSpscQueue());
        }

        @Override
        public void onSubscribe(final Subscription s) {
            // Subscription is requested from here as well as hasNext. Also, it can be cancelled from close(). So, we
            // need to protect it from concurrent access.
            subscription.delayedSubscription(ConcurrentSubscription.wrap(s));
            itemsToNextRequest = requestN;
            subscription.request(itemsToNextRequest);
        }

        @Override
        public void close() {
            try {
                subscription.cancel();
            } finally {
                if (!terminated) {
                    offer(CANCELLED_SIGNAL);
                }
            }
        }

        @Override
        public void onNext(@Nullable T t) {
            offer(wrapNull(t));
        }

        @Override
        public void onError(final Throwable t) {
            offer(error(t));
        }

        @Override
        public void onComplete() {
            offer(COMPLETE_NOTIFICATION);
        }

        private void offer(Object o) {
            if (!data.offer(o)) {
                enqueueFailed(o);
            }
        }

        @Override
        public boolean hasNext() {
            if (terminated) {
                return next != null && next != COMPLETE_NOTIFICATION;
            }
            if (next != null) {
                return true; // Keep returning true till next() is called which sets next to null
            }
            try {
                next = data.take();
                requestMoreIfRequired();
            } catch (InterruptedException e) {
                return hasNextInterrupted(e);
            }
            return hasNextProcessNext();
        }

        @Override
        public boolean hasNext(final long timeout, final TimeUnit unit) throws TimeoutException {
            if (terminated) {
                return next != null && next != COMPLETE_NOTIFICATION;
            }
            if (next != null) {
                return true; // Keep returning true till next() is called which sets next to null
            }
            try {
                next = data.poll(timeout, unit);
                if (next == null) {
                    terminated = true;
                    subscription.cancel();
                    throw new TimeoutException("timed out after: " + timeout + " units: " + unit);
                }
                requestMoreIfRequired();
            } catch (InterruptedException e) {
                return hasNextInterrupted(e);
            }
            return hasNextProcessNext();
        }

        private void enqueueFailed(Object item) {
            LOGGER.error("Queue should be unbounded, but an offer failed for item {}!", item);
            // Note that we throw even if the item represents a terminal signal (even though we don't expect another
            // terminal signal to be delivered from the upstream source because we are already terminated). If we fail
            // to enqueue a terminal event async control flow won't be completed and the user won't be notified. This
            // is a relatively extreme failure condition and we fail loudly to clarify that signal delivery is
            // interrupted and the user may experience hangs.
            throw new QueueFullException("data");
        }

        private boolean hasNextInterrupted(InterruptedException e) {
            currentThread().interrupt(); // Reset the interrupted flag.
            terminated = true;
            next = error(e);
            subscription.cancel();
            return true; // Return true so that the InterruptedException can be thrown from next()
        }

        private boolean hasNextProcessNext() {
            if (next instanceof TerminalNotification) {
                terminated = true;
                // If we have an error, return true, so that the same can be thrown from next().
                return ((TerminalNotification) next).cause() != null;
            }
            if (next == CANCELLED_SIGNAL) {
                terminated = true;
                next = null;
                return false;
            }
            return true;
        }

        private void requestMoreIfRequired() {
            // Request more when we half of the outstanding demand has been delivered. This attempts to keep some
            // outstanding demand in the event there is impedance mismatch between producer and consumer (as opposed to
            // waiting until outstanding demand reaches 0) while still having an upper bound.
            if (--itemsToNextRequest == requestN >>> 1) {
                final int toRequest = requestN - itemsToNextRequest;
                itemsToNextRequest = requestN;
                subscription.request(toRequest);
            }
        }

        @Nullable
        @Override
        public T next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            return processNext();
        }

        @Nullable
        @Override
        public T next(final long timeout, final TimeUnit unit) throws TimeoutException {
            if (!hasNext(timeout, unit)) {
                throw new NoSuchElementException();
            }
            return processNext();
        }

        @Nullable
        private T processNext() {
            final Object signal = next;
            assert next != null;
            next = null;
            if (signal instanceof TerminalNotification) {
                TerminalNotification terminalNotification = (TerminalNotification) signal;
                Throwable cause = terminalNotification.cause();
                if (cause == null) {
                    throw new NoSuchElementException();
                }
                throwException(cause);
            }
            return unwrapNullUnchecked(signal);
        }
    }

    private static final class SpscBlockingQueue<T> implements BlockingQueue<T> {
        /**
         * Amount of times to call {@link Thread#yield()} before calling {@link LockSupport#park()}.
         * {@link LockSupport#park()} can be expensive and if the producer is generating data it is likely we will see
         * it without parking.
         */
        private static final int POLL_YIELD_COUNT =
                getInteger("io.servicetalk.concurrent.internal.blockingIterableYieldCount", 1);
        /**
         * Amount of nanoseconds to spin on {@link Thread#yield()} before calling {@link LockSupport#parkNanos(long)}.
         * {@link LockSupport#parkNanos(long)} can be expensive and if the producer is generating data it is likely
         * we will see it without parking.
         */
        private static final long POLL_YIELD_SPIN_NS =
                getLong("io.servicetalk.concurrent.internal.blockingIterableYieldNs", 1024);

        @SuppressWarnings("rawtypes")
        private static final AtomicLongFieldUpdater<SpscBlockingQueue> producerConsumerIndexUpdater =
                AtomicLongFieldUpdater.newUpdater(SpscBlockingQueue.class, "producerConsumerIndex");
        private final Queue<T> spscQueue;
        @Nullable
        private Thread consumerThread;
        /**
         * high 32 bits == producer index (see {@link #producerIndex(long)})
         * low 32 bits == consumer index (see {@link #consumerIndex(long)}}
         * @see #combineIndexes(int, int)
         */
        private volatile long producerConsumerIndex;

        SpscBlockingQueue(Queue<T> spscQueue) {
            this.spscQueue = requireNonNull(spscQueue);
        }

        @Override
        public boolean add(final T t) {
            if (spscQueue.add(t)) {
                producerSignalAdded();
                return true;
            }
            return false;
        }

        @Override
        public boolean offer(final T t) {
            if (spscQueue.offer(t)) {
                producerSignalAdded();
                return true;
            }
            return false;
        }

        @Override
        public T remove() {
            final T t = spscQueue.remove();
            consumerSignalRemoved(1);
            return t;
        }

        @Override
        public T poll() {
            final T t = spscQueue.poll();
            if (t != null) {
                consumerSignalRemoved(1);
            }
            return t;
        }

        @Override
        public T element() {
            final T t = poll();
            if (t == null) {
                throw new NoSuchElementException();
            }
            return t;
        }

        @Override
        public T peek() {
            return spscQueue.peek();
        }

        @Override
        public void put(final T t) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean offer(final T t, final long timeout, final TimeUnit unit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public T take() throws InterruptedException {
            return take0(this::pollAndParkIgnoreTime, 0, NANOSECONDS);
        }

        @Override
        public T poll(final long timeout, final TimeUnit unit) throws InterruptedException {
            return take0(this::pollAndPark, timeout, unit);
        }

        @Override
        public int remainingCapacity() {
            return Integer.MAX_VALUE;
        }

        @Override
        public boolean remove(final Object o) {
            if (spscQueue.remove(o)) {
                consumerSignalRemoved(1);
                return true;
            }
            return false;
        }

        @Override
        public boolean containsAll(final Collection<?> c) {
            return spscQueue.containsAll(c);
        }

        @Override
        public boolean addAll(final Collection<? extends T> c) {
            boolean added = false;
            for (T t : c) {
                if (add(t)) {
                    added = true;
                }
            }
            return added;
        }

        @Override
        public boolean removeAll(final Collection<?> c) {
            int removed = 0;
            try {
                for (Object t : c) {
                    if (spscQueue.remove(t)) {
                        ++removed;
                    }
                }
            } finally {
                consumerSignalRemoved(removed);
            }
            return removed > 0;
        }

        @Override
        public boolean retainAll(final Collection<?> c) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void clear() {
            int removed = 0;
            while (spscQueue.poll() != null) {
                ++removed;
            }
            consumerSignalRemoved(removed);
        }

        @Override
        public int size() {
            return spscQueue.size();
        }

        @Override
        public boolean isEmpty() {
            return spscQueue.isEmpty();
        }

        @Override
        public boolean contains(final Object o) {
            return spscQueue.contains(o);
        }

        @Override
        public Iterator<T> iterator() {
            return spscQueue.iterator();
        }

        @Override
        public Object[] toArray() {
            return spscQueue.toArray();
        }

        @Override
        public <T1> T1[] toArray(final T1[] a) {
            return spscQueue.toArray(a);
        }

        @Override
        public int drainTo(final Collection<? super T> c) {
            int added = 0;
            int removed = 0;
            T item;
            try {
                while ((item = spscQueue.poll()) != null) {
                    ++removed;
                    if (c.add(item)) {
                        ++added;
                    }
                }
            } finally {
                consumerSignalRemoved(removed);
            }
            return added;
        }

        @Override
        public int drainTo(final Collection<? super T> c, final int maxElements) {
            int added = 0;
            int removed = 0;
            T item;
            try {
                while (added < maxElements && (item = spscQueue.poll()) != null) {
                    ++removed;
                    if (c.add(item)) {
                        ++added;
                    }
                }
            } finally {
                consumerSignalRemoved(removed);
            }
            return added;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof SpscBlockingQueue && spscQueue.equals(((SpscBlockingQueue<?>) o).spscQueue);
        }

        @Override
        public int hashCode() {
            return spscQueue.hashCode();
        }

        @Override
        public String toString() {
            return spscQueue.toString();
        }

        private void producerSignalAdded() {
            for (;;) {
                final long currIndex = producerConsumerIndex;
                final int producer = producerIndex(currIndex);
                final int consumer = consumerIndex(currIndex);
                if (producerConsumerIndexUpdater.compareAndSet(this, currIndex,
                        combineIndexes(producer + 1, consumer))) {
                    if (producer - consumer <= 0 && consumerThread != null) {
                        final Thread wakeThread = consumerThread;
                        consumerThread = null;
                        LockSupport.unpark(wakeThread);
                    }
                    break;
                }
            }
        }

        private T take0(BiLongFunction<TimeUnit, T> taker, long timeout, TimeUnit unit) throws InterruptedException {
            final Thread currentThread = Thread.currentThread();
            for (;;) {
                long currIndex = producerConsumerIndex;
                final int producer = producerIndex(currIndex);
                final int consumer = consumerIndex(currIndex);
                if (producer == consumer) {
                    // Set consumerThread before pcIndex, to establish happens-before with producer thread.
                    consumerThread = currentThread;
                    if (producerConsumerIndexUpdater.compareAndSet(this, currIndex,
                            combineIndexes(producer, consumer + 1))) {
                        return taker.apply(timeout, unit);
                    }
                } else {
                    final T item = spscQueue.poll();
                    if (item != null) {
                        while (!producerConsumerIndexUpdater.compareAndSet(this, currIndex,
                                combineIndexes(producer, consumer + 1))) {
                            currIndex = producerConsumerIndex;
                        }
                        return item;
                    }
                    // It is possible the producer insertion is not yet visible to this thread, yield.
                    Thread.yield();
                }
            }
        }

        private void consumerSignalRemoved(final int i) {
            for (;;) {
                final long currIndex = producerConsumerIndex;
                final int producer = producerIndex(currIndex);
                final int consumer = consumerIndex(currIndex);
                if (producerConsumerIndexUpdater.compareAndSet(this, currIndex,
                        combineIndexes(producer, consumer + i))) {
                    break;
                }
            }
        }

        private T pollAndParkIgnoreTime(@SuppressWarnings("unused") final long timeout,
                                        @SuppressWarnings("unused") final TimeUnit unit) throws InterruptedException {
            T item;
            int yieldCount = 0;
            while ((item = spscQueue.poll()) == null) {
                // Benchmarks show that park/unpark is expensive when producer is the EventLoop thread and
                // unpark has to wakeup a thread that is parked. Yield has been shown to lower this cost
                // on the EventLoop thread and increase throughput in these scenarios.
                if (yieldCount < POLL_YIELD_COUNT) {
                    Thread.yield();
                    ++yieldCount;
                } else {
                    LockSupport.park();
                }
                checkInterrupted();
            }
            return item;
        }

        @Nullable
        private T pollAndPark(final long timeout, final TimeUnit unit) throws InterruptedException {
            T item;
            final long originalNs = unit.toNanos(timeout);
            long remainingNs = originalNs;
            long beforeTimeNs = System.nanoTime();
            while ((item = spscQueue.poll()) == null) {
                // Benchmarks show that park/unpark is expensive when producer is the EventLoop thread and
                // unpark has to wakeup a thread that is parked. Yield has been shown to lower this cost
                // on the EventLoop thread and increase throughput in these scenarios.
                if (originalNs - remainingNs <= POLL_YIELD_SPIN_NS) {
                    Thread.yield();
                } else {
                    LockSupport.parkNanos(remainingNs);
                }
                checkInterrupted();
                final long afterTimeNs = System.nanoTime();
                final long durationNs = afterTimeNs - beforeTimeNs;
                if (durationNs > remainingNs) {
                    return null;
                }
                remainingNs -= durationNs;
                beforeTimeNs = afterTimeNs;
            }
            return item;
        }

        private static void checkInterrupted() throws InterruptedException {
            if (Thread.interrupted()) {
                throw new InterruptedException();
            }
        }

        private static long combineIndexes(int producer, int consumer) {
            return ((long) producer << 32) | consumer;
        }

        private static int consumerIndex(long producerConsumerIndex) {
            return (int) producerConsumerIndex;
        }

        private static int producerIndex(long producerConsumerIndex) {
            return (int) (producerConsumerIndex >>> 32);
        }

        private interface BiLongFunction<T, R> {
            R apply(long l, T t) throws InterruptedException;
        }
    }
}
