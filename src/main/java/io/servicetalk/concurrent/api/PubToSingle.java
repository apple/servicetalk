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

import org.reactivestreams.Subscription;

import java.util.NoSuchElementException;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.internal.SubscriberUtils.checkDuplicateSubscription;

/**
 * A single created from a {@link Publisher}.
 *
 * @param <T> Type of items emitted by this {@link Single}.
 */
final class PubToSingle<T> extends Single<T> {
    private final Publisher<T> source;

    /**
     * New instance.
     *
     * @param source {@link Publisher} for this {@link Single}.
     */
    PubToSingle(Publisher<T> source) {
        this.source = source;
    }

    @Override
    protected void handleSubscribe(Subscriber<? super T> subscriber) {
        source.subscribe(new org.reactivestreams.Subscriber<T>() {
            @Nullable
            private Subscription subscription;
            private boolean done;

            @Override
            public void onSubscribe(Subscription s) {
                if (checkDuplicateSubscription(subscription, s)) {
                    subscription = s;
                    subscriber.onSubscribe(s::cancel);
                    s.request(1);
                }
            }

            @Override
            public void onNext(T t) {
                if (!done) {
                    assert subscription != null : "Subscription can not be null.";
                    subscription.cancel();
                    done = true;
                    subscriber.onSuccess(t);
                }
            }

            @Override
            public void onError(Throwable t) {
                if (!done) {
                    done = true;
                    subscriber.onError(t);
                }
            }

            @Override
            public void onComplete() {
                if (!done) {
                    done = true;
                    subscriber.onError(new NoSuchElementException());
                }
            }
        });
    }
}
