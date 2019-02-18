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

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.internal.DelayedCancellable;

import javax.annotation.Nullable;

final class SingleToCompletionStage<T> extends ExecutorCompletionStage<T>
        implements io.servicetalk.concurrent.Single.Subscriber<T> {
    private final DelayedCancellable cancellable;

    private SingleToCompletionStage(io.servicetalk.concurrent.Executor executor) {
        this(executor, new DelayedCancellable());
    }

    private SingleToCompletionStage(io.servicetalk.concurrent.Executor executor,
                                    DelayedCancellable cancellable) {
        super(executor);
        this.cancellable = cancellable;
    }

    static <X> SingleToCompletionStage<X> createAndSubscribe(Single<X> original,
                                                             io.servicetalk.concurrent.Executor executor,
                                                             AsyncContextProvider provider,
                                                             AsyncContextMap contextMap) {
        SingleToCompletionStage<X> stage = new SingleToCompletionStage<>(executor);
        original.subscribeWithContext(stage, contextMap, provider);
        return stage;
    }

    @Override
    void doCancel() {
        cancellable.cancel();
    }

    @Override
    <U> ExecutorCompletionStage<U> newDependentStage(io.servicetalk.concurrent.Executor executor) {
        return new SingleToCompletionStage<>(executor, cancellable);
    }

    @Override
    public void onSubscribe(final Cancellable cancellable) {
        this.cancellable.delayedCancellable(cancellable);
    }

    @Override
    public void onSuccess(@Nullable final T result) {
        completeNoExec(result);
    }

    @Override
    public void onError(final Throwable t) {
        completeExceptionallyNoExec(t);
    }
}
