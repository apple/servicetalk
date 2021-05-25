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

import static io.servicetalk.concurrent.api.Executors.immediate;

/**
 * Base class for operators on a {@link Completable} that process signals asynchronously hence in order to guarantee
 * safe downstream invocations require to wrap their {@link Subscriber}s with offloading.
 * Operators that process signals synchronously can use {@link AbstractSynchronousCompletableOperator} to avoid wrapping
 * their {@link Subscriber}s and hence reduce object allocation.
 *
 * @see AbstractSynchronousCompletableOperator
 */
abstract class AbstractAsynchronousCompletableOperator extends AbstractNoHandleSubscribeCompletable
        implements CompletableOperator {

    protected final Completable original;
    protected final Executor executor;

    AbstractAsynchronousCompletableOperator(Completable original, Executor executor) {
        super(immediate());
        this.original = original;
        this.executor = executor;
    }

    @Override
    final Executor executor() {
        return executor;
    }

    @Override
    public abstract Subscriber apply(Subscriber subscriber);

    @Override
    abstract void handleSubscribe(Subscriber subscriber, SignalOffloader signalOffloader, AsyncContextMap contextMap,
                               AsyncContextProvider contextProvider);
}
