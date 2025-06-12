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

import javax.annotation.Nullable;

import static java.util.Objects.requireNonNull;

final class ContextPreservingCompletableSubscriber implements Subscriber {
    final Subscriber subscriber;
    @Nullable
    final CapturedContext cancellableCapturedContext;
    @Nullable
    final CapturedContext subscriberCapturedContext;

    ContextPreservingCompletableSubscriber(Subscriber subscriber,
                                           @Nullable CapturedContext cancellableCapturedContext,
                                           @Nullable CapturedContext subscriberCapturedContext) {
        assert cancellableCapturedContext != null || subscriberCapturedContext != null;
        this.subscriber = requireNonNull(subscriber);
        this.cancellableCapturedContext = cancellableCapturedContext;
        this.subscriberCapturedContext = subscriberCapturedContext;
    }

    @Override
    public void onSubscribe(Cancellable cancellable) {
        if (cancellableCapturedContext != null) {
            cancellable = ContextPreservingCancellable.wrap(cancellable, cancellableCapturedContext);
        }
        if (subscriberCapturedContext != null) {
            try (Scope ignored = subscriberCapturedContext.attachContext()) {
                subscriber.onSubscribe(cancellable);
            }
        } else {
            subscriber.onSubscribe(cancellable);
        }
    }

    @Override
    public void onComplete() {
        if (subscriberCapturedContext != null) {
            try (Scope ignored = subscriberCapturedContext.attachContext()) {
                subscriber.onComplete();
            }
        } else {
            subscriber.onComplete();
        }
    }

    @Override
    public void onError(Throwable t) {
        if (subscriberCapturedContext != null) {
            try (Scope ignored = subscriberCapturedContext.attachContext()) {
                subscriber.onError(t);
            }
        } else {
            subscriber.onError(t);
        }
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + '(' + subscriber + ')';
    }
}
