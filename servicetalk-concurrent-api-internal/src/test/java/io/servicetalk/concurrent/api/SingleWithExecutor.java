/*
 * Copyright © 2019 Apple Inc. and the ServiceTalk project authors
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
 * A {@link Single} that could be created with an {@link Executor}.
 *
 * @param <T> Type of items emitted.
 */
public final class SingleWithExecutor<T> extends AbstractSynchronousSingleOperator<T, T> {

    private Executor executor;
    /**
     * New instance.
     * @param executor {@link Executor} for this {@link Single}.
     * @param delegate {@link Single} to use.
     */
    public SingleWithExecutor(final Executor executor, Single<T> delegate) {
        super(delegate);
        this.executor = executor;
    }

    @Override
    Executor executor() {
        return executor;
    }

    @Override
    public Subscriber<? super T> apply(final Subscriber<? super T> subscriber) {
        return subscriber;
    }
}
