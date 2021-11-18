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
 * Base class for all operators on a {@link Completable} that do not process any signal asynchronously.
 *
 * <h2>Caution</h2>
 * This is an optimized path that omits wrapping the {@link Subscriber} for each operator in an execution chain.
 * If this is used in an operator which creates/consumes new asynchronous sources (eg: flatmap), it will make the
 * processing chain (after this operator) susceptible to blocking code running on eventloops. Such operators MUST use
 * {@link AbstractAsynchronousCompletableOperator} instead.
 *
 * @see AbstractAsynchronousCompletableOperator
 */
abstract class AbstractSynchronousCompletableOperator extends AbstractNoHandleSubscribeCompletable
        implements CompletableOperator {

    private final Completable original;

    AbstractSynchronousCompletableOperator(Completable original, Executor executor) {
        super(executor);
        this.original = requireNonNull(original);
    }

    @Override
    final void handleSubscribe(Subscriber subscriber, SignalOffloader signalOffloader,
                               ContextMap contextMap, AsyncContextProvider contextProvider) {
        original.delegateSubscribe(apply(subscriber), signalOffloader, contextMap, contextProvider);
    }
}
