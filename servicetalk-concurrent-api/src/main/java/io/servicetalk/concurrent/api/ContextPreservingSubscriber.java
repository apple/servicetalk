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

import javax.annotation.Nullable;

import static java.util.Objects.requireNonNull;

class ContextPreservingSubscriber<T> implements Subscriber<T> {
    @Nullable
    final CapturedContext subscriptionCapturedContext;
    @Nullable
    final CapturedContext subscriberCapturedContext;
    final Subscriber<T> subscriber;

    ContextPreservingSubscriber(Subscriber<T> subscriber, @Nullable CapturedContext subscriptionCapturedContext,
                                @Nullable CapturedContext subscriberCapturedContext) {
        assert subscriptionCapturedContext != null || subscriberCapturedContext != null;
        this.subscriber = requireNonNull(subscriber);
        this.subscriptionCapturedContext = subscriptionCapturedContext;
        this.subscriberCapturedContext = subscriberCapturedContext;
    }

    @Override
    public final void onSubscribe(Subscription s) {
        if (subscriptionCapturedContext != null) {
            s = ContextPreservingSubscription.wrap(s, subscriptionCapturedContext);
        }
        if (subscriberCapturedContext == null) {
            subscriber.onSubscribe(s);
        } else {
            try (Scope ignored = subscriberCapturedContext.attachContext()) {
                subscriber.onSubscribe(s);
            }
        }
    }

    @Override
    public final void onNext(@Nullable T t) {
        if (subscriberCapturedContext == null) {
            subscriber.onNext(t);
        } else {
            try (Scope ignored = subscriberCapturedContext.attachContext()) {
                subscriber.onNext(t);
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
    public final void onComplete() {
        if (subscriberCapturedContext == null) {
            subscriber.onComplete();
        } else {
            try (Scope ignored = subscriberCapturedContext.attachContext()) {
                subscriber.onComplete();
            }
        }
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + '(' + subscriber + ')';
    }
}
