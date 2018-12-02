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

import java.util.function.Supplier;

import static io.servicetalk.concurrent.api.PublishAndSubscribeOnSingles.deliverOnSubscribeAndOnError;
import static java.util.Objects.requireNonNull;

/**
 * As returned by {@link Single#defer(Supplier)}.
 *
 * @param <T> Type of result of this {@link Single}.
 */
final class SingleDefer<T> extends AbstractNoHandleSubscribeSingle<T> {

    private final Supplier<? extends Single<T>> singleFactory;
    private final boolean shareContext;

    SingleDefer(Supplier<? extends Single<T>> singleFactory, boolean shareContext) {
        this.singleFactory = requireNonNull(singleFactory);
        this.shareContext = shareContext;
    }

    @Override
    protected void handleSubscribe(Subscriber<? super T> subscriber, SignalOffloader signalOffloader,
                                   AsyncContextMap contextMap, AsyncContextProvider contextProvider) {
        final Single<T> single;
        try {
            single = requireNonNull(singleFactory.get());
        } catch (Throwable cause) {
            deliverOnSubscribeAndOnError(subscriber, signalOffloader, contextMap, contextProvider, cause);
            return;
        }

        // There are technically two sources, this one and the one returned by the factory.
        // Since, we are invoking user code (singleFactory) we need this method to be run using an Executor
        // and also use the configured Executor for subscribing to the Single returned from singleFactory
        if (shareContext) {
            single.subscribeWithContext(subscriber, contextMap, contextProvider);
        } else {
            single.subscribeWithContext(subscriber, contextMap.copy(), contextProvider);
        }
    }
}
