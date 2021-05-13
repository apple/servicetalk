/*
 * Copyright © 2019, 2021 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.PublisherSource;
import io.servicetalk.concurrent.PublisherSource.Subscriber;

import javax.annotation.Nullable;

import static io.servicetalk.concurrent.internal.EmptySubscriptions.EMPTY_SUBSCRIPTION;

final class OnSubscribeIgnoringSubscriberForOffloading<T> implements Subscriber<T> {

    private final Subscriber<? super T> original;

    private OnSubscribeIgnoringSubscriberForOffloading(final Subscriber<? super T> original) {
        this.original = original;
    }

    @Override
    public void onSubscribe(final PublisherSource.Subscription subscription) {
        // Ignore onSubscribe
    }

    @Override
    public void onNext(@Nullable final T t) {
        original.onNext(t);
    }

    @Override
    public void onError(final Throwable t) {
        original.onError(t);
    }

    @Override
    public void onComplete() {
        original.onComplete();
    }

    static <T> Subscriber<? super T> wrapWithDummyOnSubscribe(Subscriber<? super T> original,
               AsyncContextMap contextMap, AsyncContextProvider contextProvider) {
        Subscriber<? super T> toReturn = contextProvider.wrapPublisherSubscriber(
                new OnSubscribeIgnoringSubscriberForOffloading<>(original), contextMap);
        // We have created a wrapped Subscriber but we have sent onSubscribe to the original Subscriber
        // already, so we send an onSubscribe to the wrapped Subscriber which ignores this signal but makes
        // the wrapped does not see spec violation (onError without onSubscribe) for the offloaded
        // subscriber.
        toReturn.onSubscribe(EMPTY_SUBSCRIPTION);
        return toReturn;
    }
}
