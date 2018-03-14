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

/**
 * A {@link Completable} created from a {@link Publisher}.
 *
 * @param <T> Item type emitted from the original {@link Publisher}.
 */
final class PubToCompletable<T> extends Completable {
    private final Publisher<T> source;

    /**
     * New instance.
     *
     * @param source {@link Publisher} from which this {@link Completable} is created.
     */
    PubToCompletable(Publisher<T> source) {
        this.source = source;
    }

    @Override
    public void handleSubscribe(Subscriber subscriber) {
        source.subscribe(new org.reactivestreams.Subscriber<T>() {
            @Override
            public void onSubscribe(Subscription s) {
                s.request(Long.MAX_VALUE); // Look for the terminal signal
                subscriber.onSubscribe(s::cancel);
            }

            @Override
            public void onNext(T t) {
                // Ignore elements.
            }

            @Override
            public void onError(Throwable t) {
                subscriber.onError(t);
            }

            @Override
            public void onComplete() {
                subscriber.onComplete();
            }
        });
    }
}
