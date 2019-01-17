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
import io.servicetalk.concurrent.internal.ConcurrentSubscription;
import io.servicetalk.concurrent.internal.DelayedSubscription;
import io.servicetalk.concurrent.internal.QueueFullException;
import io.servicetalk.concurrent.internal.TerminalNotification;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.annotation.Nullable;

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

    private static final Logger LOGGER = LoggerFactory.getLogger(PublisherAsBlockingIterable.class);

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
        original.subscribe(subscriberAndIterator);
        return subscriberAndIterator;
    }

    private static final class SubscriberAndIterator<T> implements Subscriber<T>, BlockingIterator<T> {
        private static final Object CANCELLED_SIGNAL = new Object();
        private static final Object NULL_PLACEHOLDER = new Object();
        private static final TerminalNotification COMPLETE_NOTIFICATION = complete();

        private final BlockingQueue<Object> data;
        private final int maxBufferedItems;
        private final DelayedSubscription subscription = new DelayedSubscription();
        private final int queueCapacity;
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
            // max items => queueCapacityHint + 1 terminal + 1 CANCELLED_SIGNAL
            this.queueCapacity = queueCapacity + 2;
            data = new LinkedBlockingQueue<>(this.queueCapacity);
        }

        @Override
        public void onSubscribe(final Subscription s) {
            // Subscription is requested from here as well as hasNext. Also, it can be cancelled from close(). So, we
            // need to protect it from concurrent access.
            subscription.setDelayedSubscription(ConcurrentSubscription.wrap(s));
            subscription.request(maxBufferedItems);
        }

        @Override
        public void close() {
            subscription.cancel();
            if (!terminated && !data.offer(CANCELLED_SIGNAL)) {
                throw new QueueFullException("Unexpected reject from queue while offering cancel. Queue size: " +
                        data.size() + ", capacity: " + queueCapacity);
            }
        }

        @Override
        public void onNext(@Nullable T t) {
            if (!data.offer(t == null ? NULL_PLACEHOLDER : t)) { // We have received more data than we requested.
                throw new QueueFullException("publisher-iterator", queueCapacity);
            }
        }

        @Override
        public void onError(final Throwable t) {
            if (!data.offer(error(t))) {
                LOGGER.error("Unexpected reject from queue while offering terminal event. Queue size: {}, capacity: {}",
                        data.size(), queueCapacity, t);
            }
        }

        @Override
        public void onComplete() {
            if (!data.offer(COMPLETE_NOTIFICATION)) {
                LOGGER.error("Unexpected reject from queue while offering terminal event. Queue size: {}, capacity: {}",
                        data.size(), queueCapacity);
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
                return ((TerminalNotification) next).getCause() != null;
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
                Throwable cause = terminalNotification.getCause();
                if (cause == null) {
                    throw new NoSuchElementException();
                }
                if (cause instanceof RuntimeException) {
                    throw (RuntimeException) cause;
                }
                throw new RuntimeException(cause);
            }
            if (signal == NULL_PLACEHOLDER) {
                return null;
            }
            @SuppressWarnings("unchecked")
            T t = (T) signal;
            return t;
        }
    }
}
