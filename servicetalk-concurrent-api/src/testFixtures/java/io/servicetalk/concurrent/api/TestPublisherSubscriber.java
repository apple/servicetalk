/*
 * Copyright © 2019 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.PublisherSource;
import io.servicetalk.concurrent.PublisherSource.Subscriber;
import io.servicetalk.concurrent.PublisherSource.Subscription;
import io.servicetalk.concurrent.SingleSource;
import io.servicetalk.concurrent.internal.TerminalNotification;

import java.util.List;
import javax.annotation.Nullable;

import static java.util.Objects.requireNonNull;

/**
 * A {@link Subscriber} that can be used to verify the signals delivered by a {@link PublisherSource}. By default, it
 * asserts that items are not delivered without sufficient demand. (This can be disabled with
 * {@link Builder#disableDemandCheck()}.)
 * <p>
 * The {@link Subscriber} received by {@link #onSubscribe(Subscription)} is exposed through the {@link Subscription}
 * interface methods.
 *
 * @param <T> Type of items received by this {@code Subscriber}.
 * @see TestPublisherSubscriber.Builder
 */
public final class TestPublisherSubscriber<T> implements Subscriber<T>, Subscription {

    private final CollectingPublisherSubscriber<T> collector;
    private final Subscriber<T> delegate;

    /**
     * Create a {@code TestPublisherSubscriber} with the defaults.
     *
     * @see TestPublisherSubscriber.Builder
     */
    public TestPublisherSubscriber() {
        collector = new CollectingPublisherSubscriber<>();
        delegate = new DemandCheckingSubscriber<>(collector);
    }

    private TestPublisherSubscriber(final CollectingPublisherSubscriber<T> collector, final Subscriber<T> delegate) {
        this.collector = collector;
        this.delegate = delegate;
    }

    /**
     * Get the list of items received through {@link #onNext(Object)}, retaining the internal list.
     *
     * @return the list of items.
     */
    public List<T> items() {
        return collector.items();
    }

    /**
     * Get the list of items received through {@link #onNext(Object)}, and clear the internal list.
     *
     * @return the list of items.
     */
    public List<T> takeItems() {
        return collector.takeItems();
    }

    /**
     * Get the last {@link TerminalNotification} {@link #onError(Throwable)} or {@link #onComplete()} received.
     *
     * @return the {@link TerminalNotification}, or {@code null} if none have been received.
     */
    @Nullable
    public TerminalNotification terminal() {
        return collector.terminal();
    }

    /**
     * Get the last {@link Throwable} received by {@link #onError(Throwable)}.
     *
     * @return The {@link Throwable}, or {@code null} if none have been received.
     */
    @Nullable
    public Throwable error() {
        return collector.error();
    }

    /**
     * Returns {@code true} if {@link #onSubscribe(Subscription)} has been called.
     *
     * @return {@code true} if {@link #onSubscribe(Subscription)} has been called, {@code false} otherwise.
     */
    public boolean subscriptionReceived() {
        return collector.subscriptionReceived();
    }

    /**
     * Returns the {@link Subscription} received by {@link #onSubscribe(Subscription)}, or {@code null} if no
     * {@link Subscription} has been received.
     *
     * @return the {@link Subscription} received by {@link #onSubscribe(Subscription)}, or {@code null} if no
     * {@link Subscription} has been received.
     */
    public Subscription subscription() {
        return collector.subscription();
    }

    /**
     * Returns {@code true} if {@link #onComplete()} was the most recently received terminal signal, {@code false}
     * otherwise.
     *
     * @return {@code true} if {@link #onComplete()} was the most recently received terminal signal, {@code false}
     * otherwise.
     */
    public boolean isCompleted() {
        return collector.isCompleted();
    }

    /**
     * Returns {@code true} if {@link #onError(Throwable)} was the most recently received terminal signal,
     * {@code false} otherwise.
     *
     * @return {@code true} if {@link #onError(Throwable)} was the most recently received terminal signal,
     * {@code false} otherwise.
     */
    public boolean isErrored() {
        return collector.isErrored();
    }

    /**
     * Returns {@code true} if either {@link #onComplete()} or {@link #onError(Throwable)} have been received,
     * {@code false} otherwise.
     *
     * @return {@code true} if either {@link #onComplete()} or {@link #onError(Throwable)} have been received,
     * {@code false} otherwise.
     */
    public boolean isTerminated() {
        return collector.isTerminated();
    }

    /**
     * Requests {@code n} items via the {@link Subscription} received by {@link #onSubscribe(Subscription)}.
     * <p>
     * If no {@link Subscription} has been received yet, requests will be buffered and sent once one is received.
     *
     * @param n Number of items to request.
     */
    @Override
    public void request(final long n) {
        collector.request(n);
    }

    /**
     * Cancels the {@link Subscription} received by {@link #onSubscribe(Subscription)}.
     * <p>
     * If no {@link Subscription} has been received yet, the cancellation will be buffered, and the
     * {@link Subscription} will be cancelled when it is received.
     */
    @Override
    public void cancel() {
        collector.cancel();
    }

    @Override
    public void onSubscribe(final Subscription s) {
        delegate.onSubscribe(s);
    }

    @Override
    public void onNext(final T item) {
        delegate.onNext(item);
    }

    @Override
    public void onError(final Throwable t) {
        delegate.onError(t);
    }

    @Override
    public void onComplete() {
        delegate.onComplete();
    }

    // TODO(derek) remove once the single version is ready
    public SingleSource.Subscriber<T> forSingle() {
        return new SingleSubscriber<>(this, this);
    }

    /**
     * Clear received items and any terminal signals.
     * <p>
     * Does not affect subscribed/subscription state.
     */
    public void clear() {
        collector.clear();
    }

    /**
     * Allows for creating {@link TestPublisherSubscriber}s with non-default settings.
     *
     * @param <T> Type of items received by the {@code TestPublisherSubscriber}.
     */
    public static class Builder<T> {
        private boolean checkDemand = true;
        @Nullable
        private String loggingName;

        /**
         * Enables asserting items are not delivered without sufficient demand. The default is enabled.
         *
         * @return this.
         */
        public Builder<T> enableDemandCheck() {
            this.checkDemand = true;
            return this;
        }

        /**
         * Disables asserting items are not delivered without sufficient demand. The default is enabled.
         *
         * @return this.
         */
        public Builder<T> disableDemandCheck() {
            this.checkDemand = false;
            return this;
        }

        /**
         * Enables logging signals, with the default logger name. The default is disabled.
         *
         * @return this.
         */
        public Builder<T> enableLogging() {
            return enableLogging(TestPublisherSubscriber.class.getName());
        }

        /**
         * Enables logging signals, with the specified logger name. The default is disabled.
         *
         * @param loggingName The logger name to use
         * @return this.
         */
        public Builder<T> enableLogging(final String loggingName) {
            this.loggingName = requireNonNull(loggingName);
            return this;
        }

        /**
         * Disables logging signals. The default is disabled.
         *
         * @return this.
         */
        public Builder<T> disableLogging() {
            this.loggingName = null;
            return this;
        }

        /**
         * Create a {@link TestPublisherSubscriber} as configured by the builder.
         *
         * @return a new {@link TestPublisherSubscriber}.
         */
        public TestPublisherSubscriber<T> build() {
            final CollectingPublisherSubscriber<T> collector = new CollectingPublisherSubscriber<>();
            Subscriber<T> delegate = collector;

            if (checkDemand) {
                delegate = new DemandCheckingSubscriber<>(delegate);
            }
            if (loggingName != null) {
                delegate = new LoggingSubscriber<>(loggingName, delegate);
            }

            return new TestPublisherSubscriber<>(collector, delegate);
        }
    }
}
