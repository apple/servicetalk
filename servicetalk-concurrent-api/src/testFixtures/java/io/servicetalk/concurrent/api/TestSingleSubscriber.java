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

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.SingleSource;
import io.servicetalk.concurrent.SingleSource.Subscriber;

import javax.annotation.Nullable;

import static java.util.Objects.requireNonNull;

/**
 * A {@link Subscriber} that can be used to verify the signals delivered by a {@link SingleSource}.
 * <p>
 * The {@link Cancellable} received by {@link #onSubscribe(Cancellable)} is exposed through the {@link Cancellable}
 * interface methods.
 *
 * @param <T> Type of the result of this {@code Subscriber}.
 * @see TestSingleSubscriber.Builder
 */
public final class TestSingleSubscriber<T> implements Subscriber<T>, Cancellable {

    private final CollectingSingleSubscriber<T> collector;
    private final Subscriber<T> delegate;

    /**
     * Create a {@code TestSingleSubscriber} with the defaults.
     *
     * @see TestSingleSubscriber.Builder
     */
    public TestSingleSubscriber() {
        collector = new CollectingSingleSubscriber<>();
        delegate = collector;
    }

    private TestSingleSubscriber(final CollectingSingleSubscriber<T> collector, final Subscriber<T> delegate) {
        this.collector = collector;
        this.delegate = delegate;
    }

    /**
     * Returns {@code true} if {@link #onSubscribe(Cancellable)} has been called, {@code false} otherwise.
     *
     * @return {@code true} if {@link #onSubscribe(Cancellable)} has been called, {@code false} otherwise.
     */
    public boolean cancellableReceived() {
        return collector.cancellableReceived();
    }

    /**
     * Returns the {@link Cancellable} received by {@link #onSubscribe(Cancellable)}, or {@code null} if no
     * {@link Cancellable} has been received.
     *
     * @return the {@link Cancellable} received by {@link #onSubscribe(Cancellable)}, or {@code null} if no
     * {@link Cancellable} has been received.
     */
    public Cancellable cancellable() {
        return collector.cancellable();
    }

    /**
     * Returns {@code true} if {@link #onSuccess(Object)} was the most recently received signal, {@code false}
     * otherwise.
     *
     * @return {@code true} if {@link #onSuccess(Object)} was the most recently received signal, {@code false}
     * otherwise.
     */
    public boolean hasResult() {
        return collector.hasResult();
    }

    /**
     * Get the result received through {@link #onSuccess(Object)}.
     *
     * @return the list of items.
     */
    @Nullable
    public T result() {
        return collector.result();
    }

    /**
     * Get the result received through {@link #onSuccess(Object)}, and clear the internal value.
     *
     * @return the list of items.
     */
    @Nullable
    public T takeResult() {
        return collector.takeResult();
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
     * Get the last {@link Throwable} received by {@link #onError(Throwable)}, and clears the internal value.
     *
     * @return The {@link Throwable}, or {@code null} if none have been received.
     */
    @Nullable
    public Throwable takeError() {
        return collector.takeError();
    }

    /**
     * Returns {@code true} if {@link #onSuccess(Object)} was the most recently received signal, {@code false}
     * otherwise.
     *
     * @return {@code true} if {@link #onSuccess(Object)} was the most recently received signal, {@code false}
     * otherwise.
     */
    public boolean isSuccess() {
        return collector.isSuccess();
    }

    /**
     * Returns {@code true} if {@link #onError(Throwable)} was the most recently received signal, {@code false}
     * otherwise.
     *
     * @return {@code true} if {@link #onError(Throwable)} was the most recently received signal, {@code false}
     * otherwise.
     */
    public boolean isErrored() {
        return collector.isErrored();
    }

    /**
     * Cancels the {@link Cancellable} received by {@link #onSubscribe(Cancellable)}.
     * <p>
     * If no {@link Cancellable} has been received yet, the cancellation will be buffered, and the
     * {@link Cancellable} will be cancelled when it is received.
     */
    @Override
    public void cancel() {
        collector.cancel();
    }

    @Override
    public void onSubscribe(final Cancellable s) {
        delegate.onSubscribe(s);
    }

    @Override
    public void onSuccess(final T result) {
        delegate.onSuccess(result);
    }

    @Override
    public void onError(final Throwable t) {
        delegate.onError(t);
    }

    /**
     * Allows for creating {@link TestSingleSubscriber}s with non-default settings.
     *
     * @param <T> Type of the result of the {@code Subscriber}.
     */
    public static class Builder<T> {
        @Nullable
        private String loggingName;

        /**
         * Enables logging signals, with the default logger name. The default is disabled.
         *
         * @return this.
         */
        public Builder<T> enableLogging() {
            return enableLogging(TestSingleSubscriber.class.getName());
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
         * Create a {@link TestSingleSubscriber} as configured by the builder.
         *
         * @return a new {@link TestSingleSubscriber}.
         */
        public TestSingleSubscriber<T> build() {
            final CollectingSingleSubscriber<T> collector = new CollectingSingleSubscriber<>();
            Subscriber<T> delegate = collector;

            if (loggingName != null) {
                delegate = new LoggingSingleSubscriber<>(loggingName, delegate);
            }

            return new TestSingleSubscriber<>(collector, delegate);
        }
    }
}
