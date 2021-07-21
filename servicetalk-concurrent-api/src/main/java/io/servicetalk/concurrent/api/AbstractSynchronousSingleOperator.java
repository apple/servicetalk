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
 * Base class for all operators on a {@link Single} that do not process any signal asynchronously.
 *
 * <h2>Caution</h2>
 * This is an optimized path that omits wrapping the {@link Subscriber} for each operator in an execution chain.
 * If this is used in an operator which creates/consumes new asynchronous sources (eg: flatmap), it will make the
 * processing chain (after this operator) susceptible to blocking code running on eventloops. Such operators MUST use
 * {@link AbstractAsynchronousSingleOperator} instead.
 *
 * @param <T> Type of original {@link Single}.
 * @param <R> Type of {@link Single} returned by the operator.
 * @see AbstractAsynchronousSingleOperator
 */
abstract class AbstractSynchronousSingleOperator<T, R> extends AbstractNoHandleSubscribeSingle<R>
        implements SingleOperator<T, R> {

    private final Single<T> original;

    AbstractSynchronousSingleOperator(Single<T> original) {
        this.original = requireNonNull(original);
    }

    @Override
    final void handleSubscribe(Subscriber<? super R> subscriber,
                               AsyncContextMap contextMap, AsyncContextProvider contextProvider) {
        original.delegateSubscribe(apply(subscriber), contextMap, contextProvider);
    }
}
