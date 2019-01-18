/*
 * Copyright © 2018-2019 Apple Inc. and the ServiceTalk project authors
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

import static io.servicetalk.concurrent.api.DefaultAsyncContextProvider.INSTANCE;
import static java.util.Objects.requireNonNull;

final class ContextPreservingCompletableSubscriber implements Completable.Subscriber {
    private final AsyncContextMap saved;
    private final Completable.Subscriber subscriber;

    ContextPreservingCompletableSubscriber(Completable.Subscriber subscriber, AsyncContextMap current) {
        // Wrapping is used internally and the wrapped subscriber would not escape to user code,
        // so we don't have to unwrap it.
        this.subscriber = requireNonNull(subscriber);
        this.saved = requireNonNull(current);
    }

    @Override
    public void onSubscribe(Cancellable cancellable) {
        AsyncContextMap prev = INSTANCE.contextMap();
        try {
            INSTANCE.contextMap(saved);
            subscriber.onSubscribe(cancellable);
        } finally {
            INSTANCE.contextMap(prev);
        }
    }

    @Override
    public void onComplete() {
        AsyncContextMap prev = INSTANCE.contextMap();
        try {
            INSTANCE.contextMap(saved);
            subscriber.onComplete();
        } finally {
            INSTANCE.contextMap(prev);
        }
    }

    @Override
    public void onError(Throwable t) {
        AsyncContextMap prev = INSTANCE.contextMap();
        try {
            INSTANCE.contextMap(saved);
            subscriber.onError(t);
        } finally {
            INSTANCE.contextMap(prev);
        }
    }
}
