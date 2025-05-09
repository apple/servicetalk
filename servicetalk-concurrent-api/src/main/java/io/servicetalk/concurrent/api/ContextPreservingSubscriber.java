/*
 * Copyright © 2018-2019, 2021 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.PublisherSource.Subscriber;
import io.servicetalk.concurrent.PublisherSource.Subscription;
import io.servicetalk.context.api.ContextMap;

import static java.util.Objects.requireNonNull;

class ContextPreservingSubscriber<T> implements Subscriber<T> {
    // TODO: remove after 0.42.55
    private final ContextMap saved;
    final CapturedContext capturedContext;
    final Subscriber<T> subscriber;

    ContextPreservingSubscriber(Subscriber<T> subscriber, CapturedContext capturedContext) {
        this.subscriber = requireNonNull(subscriber);
        this.capturedContext = requireNonNull(capturedContext);
        this.saved = capturedContext.captured();
    }

    void invokeOnSubscribe(Subscription s) {
        subscriber.onSubscribe(s);
    }

    @Override
    public final void onSubscribe(Subscription s) {
        try (Scope ignored = capturedContext.attachContext()) {
            invokeOnSubscribe(s);
        }
    }

    @Override
    public final void onNext(T t) {
        try (Scope ignored = capturedContext.attachContext()) {
            subscriber.onNext(t);
        }
    }

    @Override
    public final void onError(Throwable t) {
        try (Scope ignored = capturedContext.attachContext()) {
            subscriber.onError(t);
        }
    }

    @Override
    public final void onComplete() {
        try (Scope ignored = capturedContext.attachContext()) {
            subscriber.onComplete();
        }
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + '(' + subscriber + ')';
    }
}
