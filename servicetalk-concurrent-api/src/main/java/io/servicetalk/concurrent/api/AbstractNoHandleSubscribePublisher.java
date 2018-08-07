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

import io.servicetalk.concurrent.internal.SignalOffloader;

import org.reactivestreams.Subscriber;

import static io.servicetalk.concurrent.internal.EmptySubscription.EMPTY_SUBSCRIPTION;

/**
 * A {@link Publisher} that does not expect to receive a call to {@link #handleSubscribe(Subscriber)} since it overrides
 * {@link #handleSubscribe(Subscriber, SignalOffloader)}.
 *
 * @param <T> Type of items emitted.
 */
abstract class AbstractNoHandleSubscribePublisher<T> extends Publisher<T> {

    AbstractNoHandleSubscribePublisher() {
    }

    AbstractNoHandleSubscribePublisher(Executor executor) {
        super(executor);
    }

    @Override
    protected final void handleSubscribe(Subscriber<? super T> subscriber) {
        subscriber.onSubscribe(EMPTY_SUBSCRIPTION);
        subscriber.onError(new UnsupportedOperationException("Subscribe with no executor is not supported for " + getClass()));
    }
}
