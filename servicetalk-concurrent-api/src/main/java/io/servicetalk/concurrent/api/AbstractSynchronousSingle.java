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

/**
 * Base class for all {@link Single}s that are created with already realized result and does not generate result
 * asynchronously.
 *
 * @param <T> Type of the result of the single.
 */
abstract class AbstractSynchronousSingle<T> extends AbstractNoHandleSubscribeSingle<T> {

    @Override
    final void handleSubscribe(Subscriber<? super T> subscriber,
                               AsyncContextMap contextMap, AsyncContextProvider contextProvider) {
        // We need to wrap the Subscriber to save/restore the AsyncContext on each operation or else the AsyncContext
        // may leak from another thread.
        doSubscribe(contextProvider.wrapSingleSubscriber(subscriber, contextMap));
    }

    /**
     * Handles the subscribe call.
     *
     *  @param subscriber {@link Subscriber} to this {@link Single}.
     */
    abstract void doSubscribe(Subscriber<? super T> subscriber);
}
