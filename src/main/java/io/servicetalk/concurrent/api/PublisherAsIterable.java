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

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.internal.DelayedSubscription;
import io.servicetalk.concurrent.internal.TerminalNotification;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.internal.TerminalNotification.complete;
import static io.servicetalk.concurrent.internal.TerminalNotification.error;
import static java.lang.Math.min;
import static java.lang.Thread.currentThread;
import static java.util.Objects.requireNonNull;

/**
 * As returned by {@link Publisher#toIterable(int)} and {@link Publisher#toIterable()}.
 *
 * @param <T> Type of items emitted by the {@link Publisher} from which this {@link Iterable} is created.
 */
final class PublisherAsIterable<T> implements Iterable<T> {

    private static final Logger LOGGER = LoggerFactory.getLogger(PublisherAsIterable.class);

    private final Publisher<T> original;
    private final int queueCapacityHint;

    PublisherAsIterable(final Publisher<T> original) {
        this(original, 16);
    }

    PublisherAsIterable(final Publisher<T> original, int queueCapacityHint) {
        this.original = requireNonNull(original);
        if (queueCapacityHint <= 0) {
            throw new IllegalArgumentException("Invalid queueCapacityHint: " + queueCapacityHint + " (expected > 0).");
        }
        // Add a sane upper bound to the capacity to reduce buffering.
        this.queueCapacityHint = min(queueCapacityHint, 128);
    }

    @Override
    public CancellableIterator<T> iterator() {
        SubscriberAndIterator<T> subscriberAndIterator = new SubscriberAndIterator<>(queueCapacityHint);
        original.subscribe(subscriberAndIterator);
        return subscriberAndIterator;
    }

    /**
     * An {@link Iterator} that can be cancelled.
     * @param <T> Type of items returned by this {@link Iterator}.
     */
    interface CancellableIterator<T> extends Iterator<T>, Cancellable {
    }

    private static final class SubscriberAndIterator<T> implements Subscriber<T>, CancellableIterator<T> {
        private static final Object CANCELLED_SIGNAL = new Object();
        private static final Object NULL_PLACEHOLDER = new Object();
        private static final TerminalNotification COMPLETE_NOTIFICATION = complete();

        private final BlockingQueue<Object> data;
        private final int maxBufferedItems;
        private final DelayedSubscription subscription = new DelayedSubscription();
        private final int queueCapacity;

        private int requested;

        @Nullable
        private Object next; // Next item to return from next()
        private boolean terminated;

        SubscriberAndIterator(int queueCapacity) {
            maxBufferedItems = queueCapacity;
            // max items => queueCapacityHint + 1 terminal + 1 CANCELLED_SIGNAL
            this.queueCapacity = queueCapacity + 2;
            data = new LinkedBlockingQueue<>(this.queueCapacity);
            requested = queueCapacity;
        }

        @Override
        public void onSubscribe(final Subscription s) {
            subscription.setDelayedSubscription(s);
            subscription.request(maxBufferedItems);
        }

        @Override
        public void cancel() {
            subscription.cancel();
            if (!data.offer(CANCELLED_SIGNAL)) {
                LOGGER.error("Unexpected reject from queue while offering cancel. Requested: " + requested
                        + ", capacity: " + (queueCapacity));
            }
        }

        @Override
        public void onNext(@Nullable T t) {
            --requested;
            t = wrapNull(t);
            if (!data.offer(t)) { // We have received more data than we requested.
                subscription.cancel();
                throw new QueueFullException("publisher-iterator", maxBufferedItems);
            }
            // Alternatively we can request(1) every time we receive an item.
            // This approach aids batch production of data without sacrificing responsiveness.
            // Assumption here is that the source does not necessarily wait to produce all data before emitting hence
            // request(n) should be as fast as request(1).
            if (requested == maxBufferedItems / 2) {
                long delta = maxBufferedItems - requested;
                requested += delta;
                // Replenish buffer
                subscription.request(delta);
            }
        }

        @Override
        public void onError(final Throwable t) {
            if (!data.offer(error(t))) {
                LOGGER.error("Unexpected reject from queue while offering terminal event. Requested: " + requested
                        + ", capacity: " + (queueCapacity), t);
            }
        }

        @Override
        public void onComplete() {
            if (!data.offer(COMPLETE_NOTIFICATION)) {
                LOGGER.error("Unexpected reject from queue while offering terminal event. Requested: " + requested
                        + ", capacity: " + (queueCapacity));
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
            } catch (InterruptedException e) {
                currentThread().interrupt(); // Reset the interrupted flag.
                terminated = true;
                next = error(e);
                subscription.cancel();
                return true; // Return true so that the InterruptedException can be thrown from next()
            }
        }

        @Nullable
        @Override
        public T next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
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

        @SuppressWarnings("unchecked")
        private T wrapNull(@Nullable final T t) {
            return t == null ? (T) NULL_PLACEHOLDER : t;
        }
    }
}
