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
import io.servicetalk.concurrent.PublisherSource;
import io.servicetalk.concurrent.SingleSource.Subscriber;

import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;

/**
 * A {@link Subscriber} that enqueues signals and provides blocking methods to consume them.
 * @param <T> The type of data in {@link #onSuccess(Object)}.
 */
public final class TestSingleSubscriber<T> implements Subscriber<T> {
    // 1 -> demand is implicit
    private final TestPublisherSubscriber<T> publisherSubscriber = new TestPublisherSubscriber<>(1);

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
    public void onSuccess(@Nullable final T result) {
        try {
            publisherSubscriber.onNext(result);
        } finally {
            publisherSubscriber.onComplete();
        }
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
     * Block until a terminal signal is received, throws if {@link #onSuccess(Object)} and returns normally if
     * {@link #onError(Throwable)}.
     *
     * @return the exception received by {@link #onError(Throwable)}.
     */
    public Throwable awaitOnError() {
        return publisherSubscriber.awaitOnError();
    }

    /**
     * Block until a terminal signal is received, throws if {@link #onError(Throwable)} and returns normally if
     * {@link #onSuccess(Object)}.
     *
     * @return The value delivered to {@link #onSuccess(Object)}.
     */
    @Nullable
    public T awaitOnSuccess() {
        final T next = publisherSubscriber.takeOnNext();
        publisherSubscriber.awaitOnComplete();
        return next;
    }

    /**
     * Block for a terminal event.
     *
     * @param timeout The duration of time to wait.
     * @param unit The unit of time to apply to the duration.
     * @return {@code true} if a terminal event has been received before the timeout duration.
     */
    public boolean pollTerminal(long timeout, TimeUnit unit) {
        return publisherSubscriber.pollTerminal(timeout, unit);
    }
}
