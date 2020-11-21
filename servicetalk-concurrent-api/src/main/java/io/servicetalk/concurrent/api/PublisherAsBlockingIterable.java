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

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.SubscriberApiUtils.unwrapNullUnchecked;
import static io.servicetalk.concurrent.api.SubscriberApiUtils.wrapNull;
import static io.servicetalk.concurrent.internal.TerminalNotification.complete;
import static io.servicetalk.concurrent.internal.TerminalNotification.error;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.Thread.currentThread;
import static java.util.Objects.requireNonNull;

/**
 * As returned by {@link Publisher#toIterable(int)} and {@link Publisher#toIterable()}.
 *
 * @param <T> Type of items emitted by the {@link Publisher} from which this {@link BlockingIterable} is created.
 */
final class PublisherAsBlockingIterable<T> implements BlockingIterable<T> {
    private final Publisher<T> original;
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
        this.queueCapacityHint = min(queueCapacityHint, 128);
    }

    @Override
    public BlockingIterator<T> iterator() {
        SubscriberAndIterator<T> subscriberAndIterator = new SubscriberAndIterator<>(queueCapacityHint);
        original.subscribeInternal(subscriberAndIterator);
        return subscriberAndIterator;
    }

    private static final class SubscriberAndIterator<T> implements Subscriber<T>, BlockingIterator<T> {
        private static final Object CANCELLED_SIGNAL = new Object();
        private static final TerminalNotification COMPLETE_NOTIFICATION = complete();
        @SuppressWarnings("rawtypes")
        private static final AtomicIntegerFieldUpdater<SubscriberAndIterator> onNextQueuedUpdater =
                AtomicIntegerFieldUpdater.newUpdater(SubscriberAndIterator.class, "onNextQueued");

        private final BlockingQueue<Object> data;
        private final int maxBufferedItems;
        private final DelayedSubscription subscription = new DelayedSubscription();
        private final int queueCapacity;
        private volatile int onNextQueued;
        /**
         * Number of items to emit from {@link #next()} till we request more.
         * Alternatively we can {@link Subscription#request(long) request(1)} every time we emit an item.
         * This approach aids batch production of data without sacrificing responsiveness.
         * Assumption here is that the source does not necessarily wait to produce all data before emitting hence
         * {@link Subscription#request(long) request(n)} should be as fast as
         * {@link Subscription#request(long) request(1)}.
         * <p>
         * Only accessed from {@link Iterator} methods and not from {@link Subscriber} methods.
         */
        private int itemsToNextRequest;

        /**
         * Next item to return from {@link #next()}
         */
        @Nullable
        private Object next;
        private boolean terminated;

        SubscriberAndIterator(int queueCapacity) {
            maxBufferedItems = queueCapacity;
            itemsToNextRequest = max(1, maxBufferedItems / 2);
            this.queueCapacity = queueCapacity;
            data = new LinkedBlockingQueue<>();
        }

        @Override
        public void onSubscribe(final Subscription s) {
            // Subscription is requested from here as well as hasNext. Also, it can be cancelled from close(). So, we
            // need to protect it from concurrent access.
            subscription.delayedSubscription(ConcurrentSubscription.wrap(s));
            subscription.request(maxBufferedItems);
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
            // optimistically increment. there is no concurrency allowed in onNext.
            if (onNextQueuedUpdater.incrementAndGet(this) > queueCapacity) {
                onNextQueuedUpdater.decrementAndGet(this);
                throw new QueueFullException("publisher-iterator", queueCapacity);
            }
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
            boolean offered = data.offer(o);
            assert offered;
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
            if (--itemsToNextRequest == 0) {
                itemsToNextRequest = max(1, maxBufferedItems / 2);
                subscription.request(itemsToNextRequest);
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
                if (cause instanceof RuntimeException) {
                    throw (RuntimeException) cause;
                }
                throw new RuntimeException(cause);
            }
            onNextQueuedUpdater.decrementAndGet(this);
            return unwrapNullUnchecked(signal);
        }
    }
}
