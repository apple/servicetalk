/*
 * Copyright © 2018 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.transport.api;

import io.servicetalk.buffer.api.BufferAllocator;
import io.servicetalk.concurrent.api.Executor;

import static java.util.Objects.requireNonNull;

/**
 * An {@link ExecutionContext} implementation that delegates all calls to a provided {@link ExecutionContext}. Any of
 * the methods can be overridden by implementations to change the behavior.
 *
 * @param <ES> type of the execution strategy used.
 */
public class DelegatingExecutionContext<ES extends ExecutionStrategy> implements ExecutionContext<ES> {

    private final ExecutionContext<? extends ES> delegate;

    /**
     * New instance.
     *
     * @param delegate {@link ExecutionContext} to delegate all calls.
     */
    public DelegatingExecutionContext(final ExecutionContext<? extends ES> delegate) {
        this.delegate = requireNonNull(delegate);
    }

    /**
     * Get the {@link ExecutionContext} that this class delegates to.
     *
     * @return the {@link ExecutionContext} that this class delegates to.
     */
    protected final ExecutionContext<? extends ES> delegate() {
        return delegate;
    }

    @Override
    public BufferAllocator bufferAllocator() {
        return delegate.bufferAllocator();
    }

    @Override
    public IoExecutor ioExecutor() {
        return delegate.ioExecutor();
    }

    @Override
    public Executor executor() {
        return delegate.executor();
    }

    @Override
    public ES executionStrategy() {
        return delegate.executionStrategy();
    }
}
