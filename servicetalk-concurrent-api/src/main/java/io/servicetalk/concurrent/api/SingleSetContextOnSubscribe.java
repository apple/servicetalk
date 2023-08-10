/*
 * Copyright © 2023 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.context.api.ContextMap;

import static java.util.Objects.requireNonNull;

final class SingleSetContextOnSubscribe<T> extends AbstractNoHandleSubscribeSingle<T> {
    private final Single<T> original;
    private final ContextMap context;

    SingleSetContextOnSubscribe(Single<T> original, ContextMap context) {
        this.original = original;
        this.context = requireNonNull(context);
    }

    @Override
    ContextMap contextForSubscribe(AsyncContextProvider provider) {
        return context;
    }

    @Override
    void handleSubscribe(final Subscriber<? super T> singleSubscriber,
                         final ContextMap contextMap, final AsyncContextProvider contextProvider) {
        // This operator currently only targets the subscribe method. Given this limitation if we try to change the
        // ContextMap now it is possible that operators downstream in the subscribe call stack may have modified
        // the ContextMap and we don't want to discard those changes by using a different ContextMap.
        original.handleSubscribe(singleSubscriber, contextMap, contextProvider);
    }
}
