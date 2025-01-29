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

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.CompletableSource.Subscriber;
import io.servicetalk.context.api.ContextMap;

import static java.util.Objects.requireNonNull;

class ContextPreservingCompletableSubscriber implements Subscriber {
    final ContextMap saved;
    final Subscriber subscriber;

    ContextPreservingCompletableSubscriber(Subscriber subscriber, ContextMap current) {
        this.subscriber = requireNonNull(subscriber);
        this.saved = requireNonNull(current);
    }

    void invokeOnSubscribe(Cancellable cancellable) {
        subscriber.onSubscribe(cancellable);
    }

    @Override
    public final void onSubscribe(final Cancellable cancellable) {
        AsyncContextProvider provider = AsyncContext.provider();
        ContextMap prev = provider.attachContext(saved);
        try {
            invokeOnSubscribe(cancellable);
        } finally {
            provider.detachContext(saved, prev);
        }
    }

    @Override
    public final void onComplete() {
        AsyncContextProvider provider = AsyncContext.provider();
        ContextMap prev = provider.attachContext(saved);
        try {
            subscriber.onComplete();
        } finally {
            provider.detachContext(saved, prev);
        }
    }

    @Override
    public final void onError(Throwable t) {
        AsyncContextProvider provider = AsyncContext.provider();
        ContextMap prev = provider.attachContext(saved);
        try {
            subscriber.onError(t);
        } finally {
            provider.detachContext(saved, prev);
        }
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + '(' + subscriber + ')';
    }
}
