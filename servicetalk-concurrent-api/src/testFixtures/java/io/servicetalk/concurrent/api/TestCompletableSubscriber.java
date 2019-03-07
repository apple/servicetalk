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
import io.servicetalk.concurrent.CompletableSource;
import io.servicetalk.concurrent.CompletableSource.Subscriber;
import io.servicetalk.concurrent.internal.TerminalNotification;

import javax.annotation.Nullable;

import static java.util.Objects.requireNonNull;

/**
 * A {@link Subscriber} that can be used to verify the signals delivered by a {@link CompletableSource}.
 * <p>
 * The {@link Cancellable} received by {@link #onSubscribe(Cancellable)} is exposed through the {@link Cancellable}
 * interface methods.
 *
 * @see TestCompletableSubscriber.Builder
 */
public final class TestCompletableSubscriber implements Subscriber, Cancellable {

    private final CollectingCompletableSubscriber collector;
    private final Subscriber delegate;

    /**
     * Create a {@code TestCompletableSubscriber} with the defaults.
     *
     * @see TestCompletableSubscriber.Builder
     */
    public TestCompletableSubscriber() {
        collector = new CollectingCompletableSubscriber();
        delegate = collector;
    }

    private TestCompletableSubscriber(final CollectingCompletableSubscriber collector, final Subscriber delegate) {
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
     * Get the last {@link TerminalNotification} {@link #onError(Throwable)} or {@link #onComplete()} received.
     *
     * @return the {@link TerminalNotification}, or {@code null} if none have been received.
     */
    @Nullable
    public TerminalNotification terminal() {
        return collector.terminal();
    }

    /**
     * Get the last {@link TerminalNotification} {@link #onError(Throwable)} or {@link #onComplete()} received, and
     * clears the internal value.
     *
     * @return the {@link TerminalNotification}, or {@code null} if none have been received.
     */
    @Nullable
    public TerminalNotification takeTerminal() {
        return collector.takeTerminal();
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
    public void onComplete() {
        delegate.onComplete();
    }

    @Override
    public void onError(final Throwable t) {
        delegate.onError(t);
    }

    /**
     * Allows for creating {@link TestCompletableSubscriber}s with non-default settings.
     */
    public static class Builder {
        @Nullable
        private String loggingName;

        /**
         * Enables logging signals, with the default logger name. The default is disabled.
         *
         * @return this.
         */
        public Builder enableLogging() {
            return enableLogging(TestCompletableSubscriber.class.getName());
        }

        /**
         * Enables logging signals, with the specified logger name. The default is disabled.
         *
         * @param loggingName The logger name to use
         * @return this.
         */
        public Builder enableLogging(final String loggingName) {
            this.loggingName = requireNonNull(loggingName);
            return this;
        }

        /**
         * Disables logging signals. The default is disabled.
         *
         * @return this.
         */
        public Builder disableLogging() {
            this.loggingName = null;
            return this;
        }

        /**
         * Create a {@link TestCompletableSubscriber} as configured by the builder.
         *
         * @return a new {@link TestCompletableSubscriber}.
         */
        public TestCompletableSubscriber build() {
            final CollectingCompletableSubscriber collector = new CollectingCompletableSubscriber();
            Subscriber delegate = collector;

            if (loggingName != null) {
                delegate = new LoggingCompletableSubscriber(loggingName, delegate);
            }

            return new TestCompletableSubscriber(collector, delegate);
        }
    }
}
