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
 * A default implementation of {@link ExecutionContext}.
 *
 * @param <ES> type of the execution strategy used.
 */
public final class DefaultExecutionContext<ES extends ExecutionStrategy> implements ExecutionContext<ES> {

    private final BufferAllocator bufferAllocator;
    private final IoExecutor ioExecutor;
    private final Executor executor;
    private final ES executionStrategy;

    /**
     * Create a new instance.
     *
     * @param bufferAllocator The {@link BufferAllocator} to use for {@link #bufferAllocator()}.
     * @param ioExecutor The {@link IoExecutor} to use for {@link #ioExecutor()}.
     * @param executor The {@link Executor} to use for {@link #executor()}.
     * @param executionStrategy {@link ExecutionStrategy} to use for {@link #executionStrategy()}.
     */
    public DefaultExecutionContext(final BufferAllocator bufferAllocator,
                                   final IoExecutor ioExecutor,
                                   final Executor executor,
                                   final ES executionStrategy) {
        this.bufferAllocator = requireNonNull(bufferAllocator);
        this.ioExecutor = requireNonNull(ioExecutor);
        this.executor = requireNonNull(executor);
        this.executionStrategy = requireNonNull(executionStrategy);
    }

    @Override
    public BufferAllocator bufferAllocator() {
        return bufferAllocator;
    }

    @Override
    public IoExecutor ioExecutor() {
        return ioExecutor;
    }

    @Override
    public Executor executor() {
        return executor;
    }

    @Override
    public ES executionStrategy() {
        return executionStrategy;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final DefaultExecutionContext<?> that = (DefaultExecutionContext<?>) o;

        return bufferAllocator.equals(that.bufferAllocator) &&
                ioExecutor.equals(that.ioExecutor) &&
                executor.equals(that.executor) &&
                executionStrategy.equals(that.executionStrategy);
    }

    @Override
    public int hashCode() {
        int result = bufferAllocator.hashCode();
        result = 31 * result + ioExecutor.hashCode();
        result = 31 * result + executor.hashCode();
        result = 31 * result + executionStrategy.hashCode();
        return result;
    }
}
