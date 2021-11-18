/*
 * Copyright © 2018-2019 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.context.api.ContextMap;

import static java.util.Objects.requireNonNull;

/**
 * Base class for all operators on a {@link Publisher} that do not process any signal asynchronously.
 *
 * <h2>Caution</h2>
 * This is an optimized path that omits wrapping the {@link Subscriber} for each operator in an execution chain.
 * If this is used in an operator which creates/consumes new asynchronous sources (eg: flatMap), it will make the
 * processing chain (after this operator) susceptible to blocking code running on eventloops. Such operators MUST use
 * {@link AbstractAsynchronousPublisherOperator} instead.
 *
 * @param <T> Type of original {@link Publisher}.
 * @param <R> Type of {@link Publisher} returned by the operator.
 * @see AbstractAsynchronousPublisherOperator
 */
abstract class AbstractSynchronousPublisherOperator<T, R> extends AbstractNoHandleSubscribePublisher<R>
        implements PublisherOperator<T, R> {

    private final Publisher<T> original;

    AbstractSynchronousPublisherOperator(Publisher<T> original, Executor executor) {
        super(executor);
        this.original = requireNonNull(original);
    }

    @Override
    final void handleSubscribe(Subscriber<? super R> subscriber, SignalOffloader signalOffloader,
                               ContextMap contextMap, AsyncContextProvider contextProvider) {
        original.delegateSubscribe(apply(subscriber), signalOffloader, contextMap, contextProvider);
    }
}
