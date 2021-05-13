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

import static java.util.Objects.requireNonNull;

/**
 * Base class for operators on a {@link Single} that process signals asynchronously hence in order to guarantee safe
 * downstream invocations require to wrap their {@link Subscriber}s.
 * Operators that process signals synchronously can use {@link AbstractSynchronousSingleOperator} to avoid wrapping
 * their {@link Subscriber}s and hence reduce object allocation.
 *
 * @param <T> Type of original {@link Single}.
 * @param <R> Type of {@link Single} returned by the operator.
 *
 * @see AbstractSynchronousSingleOperator
 */
abstract class AbstractAsynchronousSingleOperator<T, R> extends AbstractNoHandleSubscribeSingle<R>
        implements SingleOperator<T, R> {

    protected final Single<T> original;

    AbstractAsynchronousSingleOperator(Single<T> original) {
        this.original = requireNonNull(original);
    }

    @Override
    void handleSubscribe(Subscriber<? super R> subscriber,
                         AsyncContextMap contextMap, AsyncContextProvider contextProvider) {
        // The AsyncContext needs to be preserved when ever we interact with the original Subscriber, so we wrap it here
        // with the original contextMap. Otherwise some other context may leak into this subscriber chain from the other
        // side of the asynchronous boundary.
        final Subscriber<? super R> operatorSubscriber =
                contextProvider.wrapSingleSubscriberAndCancellable(subscriber, contextMap);
        final Subscriber<? super T> upstreamSubscriber = apply(operatorSubscriber);
        original.delegateSubscribe(upstreamSubscriber, contextMap, contextProvider);
    }
}
