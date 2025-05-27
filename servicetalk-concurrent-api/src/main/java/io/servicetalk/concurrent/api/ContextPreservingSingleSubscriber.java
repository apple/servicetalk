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
    @Nullable
    final CapturedContext subscriberCapturedContext;
    @Nullable
    final CapturedContext cancellableCapturedContext;
    final SingleSource.Subscriber<T> subscriber;

    ContextPreservingSingleSubscriber(Subscriber<T> subscriber, @Nullable CapturedContext cancellableCapturedContext,
                                      @Nullable CapturedContext subscriberCapturedContext) {
        assert subscriberCapturedContext != null || cancellableCapturedContext != null;
        this.subscriber = requireNonNull(subscriber);
        this.subscriberCapturedContext = subscriberCapturedContext;
        this.cancellableCapturedContext = cancellableCapturedContext;
    }

    void invokeOnSubscribe(Cancellable cancellable) {
        subscriber.onSubscribe(cancellable);
    }

    @Override
    public final void onSubscribe(Cancellable cancellable) {
        if (cancellableCapturedContext != null) {
            cancellable = ContextPreservingCancellable.wrap(cancellable, cancellableCapturedContext);
        }
        if (subscriberCapturedContext == null) {
            invokeOnSubscribe(cancellable);
        } else {
            try (Scope ignored = subscriberCapturedContext.attachContext()) {
                invokeOnSubscribe(cancellable);
            }
        }
    }

    @Override
    public final void onSuccess(@Nullable T result) {
        if (subscriberCapturedContext == null) {
            subscriber.onSuccess(result);
        } else {
            try (Scope ignored = subscriberCapturedContext.attachContext()) {
                subscriber.onSuccess(result);
            }
        }
    }

    @Override
    public final void onError(Throwable t) {
        if (subscriberCapturedContext == null) {
            subscriber.onError(t);
        } else {
            try (Scope ignored = subscriberCapturedContext.attachContext()) {
                subscriber.onError(t);
            }
        }
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + '(' + subscriber + ')';
    }
}
