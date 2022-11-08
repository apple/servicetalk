/*
 * Copyright © 2022 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.internal.DuplicateSubscribeException;

import java.util.concurrent.atomic.AtomicReference;

import static io.servicetalk.concurrent.internal.SubscriberUtils.deliverErrorFromSource;
import static java.util.Objects.requireNonNull;

/**
 * Wraps {@link TestPublisher} in a way that allows for sequential resubscribes.
 * @param <T> The type of {@link TestPublisher}.
 */
public class TestResubscribePublisher<T> extends Publisher<T> {
    private final AtomicReference<SubscriberState<T>> state = new AtomicReference<>();

    @Override
    protected void handleSubscribe(final PublisherSource.Subscriber<? super T> subscriber) {
        SubscriberState<T> newState = new SubscriberState<>(state, subscriber);
        SubscriberState<T> currState = state.get();
        if (state.compareAndSet(null, newState)) {
            newState.publisher.subscribe(subscriber);
        } else {
            deliverErrorFromSource(subscriber, new DuplicateSubscribeException(currState.subscriber, subscriber));
        }
    }

    /**
     * Get the current {@link TestPublisher}.
     * @return the current {@link TestPublisher}.
     */
    public TestPublisher<T> publisher() {
        return state.get().publisher;
    }

    /**
     * Get the current {@link TestSubscription}.
     * @return the current {@link TestSubscription}.
     */
    public TestSubscription subscription() {
        return state.get().subscription;
    }

    private static final class SubscriberState<T> {
        private final PublisherSource.Subscriber<? super T> subscriber;
        private final TestPublisher<T> publisher;
        private final TestSubscription subscription;

        SubscriberState(AtomicReference<SubscriberState<T>> state,
                        PublisherSource.Subscriber<? super T> subscriber) {
            this.subscriber = requireNonNull(subscriber);
            this.subscription = new TestSubscription();
            this.publisher = new TestPublisher.Builder<T>().disableAutoOnSubscribe().build(subscriber1 -> {
                subscriber1.onSubscribe(new PublisherSource.Subscription() {
                    @Override
                    public void request(final long n) {
                        subscription.request(n);
                    }

                    @Override
                    public void cancel() {
                        try {
                            subscription.cancel();
                        } finally {
                            state.compareAndSet(SubscriberState.this, null);
                        }
                    }
                });
                return subscriber1;
            });
        }
    }
}
