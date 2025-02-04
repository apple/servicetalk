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
import io.servicetalk.concurrent.SingleSource;
import io.servicetalk.concurrent.SingleSource.Subscriber;

import javax.annotation.Nullable;

import static java.util.Objects.requireNonNull;

class ContextPreservingSingleSubscriber<T> implements Subscriber<T> {
    final CapturedContext saved;
    final SingleSource.Subscriber<T> subscriber;

    ContextPreservingSingleSubscriber(Subscriber<T> subscriber, CapturedContext current) {
        this.subscriber = requireNonNull(subscriber);
        this.saved = requireNonNull(current);
    }

    void invokeOnSubscribe(Cancellable cancellable) {
        subscriber.onSubscribe(cancellable);
    }

    @Override
    public final void onSubscribe(Cancellable cancellable) {
        try (Scope ignored = saved.restoreContext()) {
            invokeOnSubscribe(cancellable);
        }
    }

    @Override
    public final void onSuccess(@Nullable T result) {
        try (Scope ignored = saved.restoreContext()) {
            subscriber.onSuccess(result);
        }
    }

    @Override
    public final void onError(Throwable t) {
        try (Scope ignored = saved.restoreContext()) {
            subscriber.onError(t);
        }
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + '(' + subscriber + ')';
    }
}
