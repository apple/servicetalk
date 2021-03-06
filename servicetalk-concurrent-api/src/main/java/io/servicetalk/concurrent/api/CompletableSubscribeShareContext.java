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

final class CompletableSubscribeShareContext extends AbstractNoHandleSubscribeCompletable {
    private final Completable original;

    CompletableSubscribeShareContext(final Completable original) {
        this.original = original;
    }

    @Override
    protected AsyncContextMap contextForSubscribe(AsyncContextProvider provider) {
        return provider.contextMap();
    }

    @Override
    void handleSubscribe(final Subscriber subscriber,
                         final AsyncContextMap contextMap, final AsyncContextProvider contextProvider) {
        // This operator currently only targets the subscribe method. Given this limitation if we try to change the
        // AsyncContextMap now it is possible that operators downstream in the subscribe call stack may have modified
        // the AsyncContextMap and we don't want to discard those changes by using a different AsyncContextMap.
        original.handleSubscribe(subscriber, contextMap, contextProvider);
    }
}
