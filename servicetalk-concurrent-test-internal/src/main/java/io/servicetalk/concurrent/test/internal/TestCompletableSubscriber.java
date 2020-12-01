/*
 * Copyright © 2020 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.concurrent.test.internal;

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.CompletableSource.Subscriber;
import io.servicetalk.concurrent.PublisherSource;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import javax.annotation.Nullable;

/**
 * A {@link Subscriber} that enqueues signals and provides blocking methods to consume them.
 */
public final class TestCompletableSubscriber implements Subscriber {
    private final TestPublisherSubscriber<Void> publisherSubscriber = new TestPublisherSubscriber<>();
    @Override
    public void onSubscribe(final Cancellable cancellable) {
        publisherSubscriber.onSubscribe(new PublisherSource.Subscription() {
            @Override
            public void request(final long n) {
            }

            @Override
            public void cancel() {
                cancellable.cancel();
            }
        });
    }

    @Override
    public void onComplete() {
        publisherSubscriber.onComplete();
    }

    @Override
    public void onError(final Throwable t) {
        publisherSubscriber.onError(t);
    }

    /**
     * Block until {@link #onSubscribe(Cancellable)}.
     *
     * @return The {@link PublisherSource.Subscription} from {@link #onSubscribe(Cancellable)}.
     */
    public Cancellable awaitSubscription() {
        return publisherSubscriber.awaitSubscription();
    }

    /**
     * Block until {@link Subscriber#onSubscribe(Cancellable)}.
     *
     * @return The {@link Cancellable} from {@link #onSubscribe(Cancellable)}.
     * @throws InterruptedException if the calling thread is interrupted while waiting for signals.
     */
    public Cancellable awaitSubscriptionInterruptible() throws InterruptedException {
        return publisherSubscriber.awaitSubscriptionInterruptible();
    }

    /**
     * Block until {@link Subscriber#onSubscribe(Cancellable)}.
     * @param timeout The amount of time to wait.
     * @param unit The units of {@code timeout}.
     * @return The {@link Cancellable} from {@link #onSubscribe(Cancellable)}.
     * @throws InterruptedException if the calling thread is interrupted while waiting for signals.
     * @throws TimeoutException if a timeout occurs while waiting.
     */
    public Cancellable awaitSubscriptionInterruptible(long timeout, TimeUnit unit)
            throws InterruptedException, TimeoutException {
        return publisherSubscriber.awaitSubscriptionInterruptible(timeout, unit);
    }

    /**
     * Block until a terminal signal is received, throws if {@link #onComplete()} and returns normally if
     * {@link #onError(Throwable)}.
     *
     * @return the exception received by {@link #onError(Throwable)}.
     */
    public Throwable awaitOnError() {
        return publisherSubscriber.awaitOnError();
    }

    /**
     * Block until a terminal signal is received, throws if {@link #onComplete()} and returns normally if
     * {@link #onError(Throwable)}.
     * @return the exception received by {@link #onError(Throwable)}.
     * @throws InterruptedException if the calling thread is interrupted while waiting for signals.
     */
    public Throwable takeOnErrorInterruptible() throws InterruptedException {
        return publisherSubscriber.takeOnErrorInterruptible();
    }

    /**
     * Block until a terminal signal is received, throws if {@link #onComplete()} and returns normally if
     * {@link #onError(Throwable)}.
     *
     * @param timeout The amount of time to wait.
     * @param unit The units of {@code timeout}.
     * @return the exception received by {@link #onError(Throwable)}.
     * @throws InterruptedException if the calling thread is interrupted while waiting for signals.
     * @throws TimeoutException if a timeout occurs while waiting.
     */
    public Throwable takeOnErrorInterruptible(long timeout, TimeUnit unit)
            throws InterruptedException, TimeoutException {
        return publisherSubscriber.takeOnErrorInterruptible(timeout, unit);
    }

    /**
     * Block until a terminal signal is received, throws if {@link #onError(Throwable)} and returns normally if
     * {@link #onComplete()}.
     */
    public void awaitOnComplete() {
        publisherSubscriber.awaitOnComplete();
    }

    /**
     * Block until a terminal signal is received, throws if {@link #onError(Throwable)} and returns normally if
     * {@link #onComplete()}.
     * @throws InterruptedException if the calling thread is interrupted while waiting for signals.
     */
    public void takeOnCompleteInterruptible() throws InterruptedException {
        publisherSubscriber.takeOnCompleteInterruptible();
    }

    /**
     * Block until a terminal signal is received, throws if {@link #onError(Throwable)} and returns normally if
     * {@link #onComplete()}.
     * @param timeout The amount of time to wait.
     * @param unit The units of {@code timeout}.
     * @throws InterruptedException if the calling thread is interrupted while waiting for signals.
     * @throws TimeoutException if a timeout occurs while waiting.
     */
    public void takeOnCompleteInterruptible(long timeout, TimeUnit unit)
            throws InterruptedException, TimeoutException {
        publisherSubscriber.takeOnCompleteInterruptible(timeout, unit);
    }

    /**
     * Block for a terminal event.
     *
     * @param timeout The duration of time to wait.
     * @param unit The unit of time to apply to the duration.
     * @return {@code null} if a the timeout expires before a terminal event is received. A non-{@code null}
     * {@link Supplier} that returns {@code null} if {@link #onComplete()}, or the {@link Throwable} from
     * {@link #onError(Throwable)}.
     */
    @Nullable
    public Supplier<Throwable> pollTerminal(long timeout, TimeUnit unit) {
        return publisherSubscriber.pollTerminal(timeout, unit);
    }

    /**
     * Block for a terminal event.
     *
     * @param timeout The duration of time to wait.
     * @param unit The unit of time to apply to the duration.
     * @return {@code null} if a the timeout expires before a terminal event is received. A non-{@code null}
     * {@link Supplier} that returns {@code null} if {@link #onComplete()}, or the {@link Throwable} from
     * {@link #onError(Throwable)}.
     * @throws InterruptedException if the calling thread is interrupted while waiting for signals.
     */
    @Nullable
    public Supplier<Throwable> pollTerminalInterruptible(long timeout, TimeUnit unit) throws InterruptedException {
        return publisherSubscriber.pollTerminalInterruptible(timeout, unit);
    }
}
