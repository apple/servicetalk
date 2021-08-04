/*
 * Copyright © 2020-2021 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.api.AmbSingles.AmbSubscriber;
import io.servicetalk.concurrent.api.AmbSingles.State;

import static io.servicetalk.concurrent.internal.SubscriberUtils.handleExceptionFromOnSubscribe;
import static java.util.Objects.requireNonNull;

final class SingleAmbWith<T> extends AbstractNoHandleSubscribeSingle<T> {
    private final Single<T> original;
    private final Single<T> ambWith;

    SingleAmbWith(final Single<T> original, final Single<T> ambWith) {
        this.original = requireNonNull(original);
        this.ambWith = requireNonNull(ambWith);
    }

    @Override
    void handleSubscribe(final Subscriber<? super T> subscriber,
                         final AsyncContextMap contextMap, final AsyncContextProvider contextProvider) {
        State<T> state = new State<>(subscriber);
        try {
            subscriber.onSubscribe(state);
        } catch (Throwable t) {
            handleExceptionFromOnSubscribe(subscriber, t);
            return;
        }

        try {
            AmbSubscriber<T> originalSubscriber = new AmbSubscriber<>(state);
            AmbSubscriber<T> ambWithSubscriber = new AmbSubscriber<>(state);
            state.delayedCancellable(CompositeCancellable.create(originalSubscriber, ambWithSubscriber));
            original.delegateSubscribe(originalSubscriber, contextMap, contextProvider);
            ambWith.subscribeInternal(
                    // If the other Single delivers the result, we should restore the context.
                    contextProvider.wrapSingleSubscriber(ambWithSubscriber, contextMap));
        } catch (Throwable t) {
            state.tryError(t);
        }
    }
}
