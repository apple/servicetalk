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
package io.servicetalk.concurrent.internal;

import io.servicetalk.concurrent.PublisherSource.Subscriber;
import io.servicetalk.concurrent.PublisherSource.Subscription;

import javax.annotation.Nullable;

import static io.servicetalk.concurrent.internal.SubscriberUtils.checkDuplicateSubscription;

/**
 * A {@link Subscriber} which makes the latest value from {@link #onNext(Object)} available outside the context of the
 * {@link Subscriber}.
 *
 * @param <T> The type of data.
 * @deprecated This class is no longer used by ServiceTalk and will be removed in the future releases. If you depend on
 * it, consider copying into your codebase.
 */
@Deprecated
public final class LatestValueSubscriber<T> implements Subscriber<T> {  // FIXME: 0.43 - remove deprecated class
    @Nullable
    private volatile T latestValue;
    @Nullable
    private Subscription subscription;

    @Override
    public void onSubscribe(Subscription s) {
        if (checkDuplicateSubscription(subscription, s)) {
            subscription = s;
            s.request(1);
        }
    }

    @Override
    public void onNext(T newValue) {
        assert subscription != null : "Subscription can not be null in onNext.";
        latestValue = newValue;
        subscription.request(1);
    }

    @Override
    public void onError(Throwable t) {
    }

    @Override
    public void onComplete() {
    }

    /**
     * Get the last seen value.
     *
     * @param defaultValue The default value if there has been no values seen
     * @return the last seen value
     */
    public T lastSeenValue(T defaultValue) {
        final T current = latestValue;
        return current != null ? current : defaultValue;
    }
}
