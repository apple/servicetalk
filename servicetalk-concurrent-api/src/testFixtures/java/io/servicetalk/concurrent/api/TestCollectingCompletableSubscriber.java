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
package io.servicetalk.concurrent.api;

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.CompletableSource.Subscriber;
import io.servicetalk.concurrent.PublisherSource;

import java.util.concurrent.TimeUnit;

/**
 * A {@link Subscriber} that enqueues signals and provides blocking methods to consume them.
 */
public final class TestCollectingCompletableSubscriber implements Subscriber {
    private final TestCollectingPublisherSubscriber<Void> publisherSubscriber =
            new TestCollectingPublisherSubscriber<>();
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
     * @throws InterruptedException if an interrupt occurs while blocking for waiting for
     * {@link #onSubscribe(Cancellable)}.
     */
    public Cancellable awaitSubscription() throws InterruptedException {
        return publisherSubscriber.awaitSubscription();
    }

    /**
     * Block until a terminal signal is received, throws if {@link #onComplete()} and returns normally if
     * {@link #onError(Throwable)}.
     *
     * @return the exception received by {@link #onError(Throwable)}.
     * @throws InterruptedException If an interrupt occurs while blocking for the terminal event.
     */
    public Throwable awaitOnError() throws InterruptedException {
        return publisherSubscriber.awaitOnError();
    }

    /**
     * Block until a terminal signal is received, throws if {@link #onError(Throwable)} and returns normally if
     * {@link #onComplete()}.
     *
     * @throws InterruptedException If an interrupt occurs while blocking for the terminal event.
     */
    public void awaitOnComplete() throws InterruptedException {
        publisherSubscriber.awaitOnComplete();
    }

    /**
     * Block for a terminal event.
     *
     * @param timeout The duration of time to wait.
     * @param unit The unit of time to apply to the duration.
     * @return {@code true} if a terminal event has been received before the timeout duration.
     * @throws InterruptedException If an interrupt occurs while blocking for the terminal event.
     */
    public boolean pollTerminal(long timeout, TimeUnit unit) throws InterruptedException {
        return publisherSubscriber.pollTerminal(timeout, unit);
    }
}
