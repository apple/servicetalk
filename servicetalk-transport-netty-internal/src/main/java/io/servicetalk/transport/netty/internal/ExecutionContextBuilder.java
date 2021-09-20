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
package io.servicetalk.transport.netty.internal;

import io.servicetalk.buffer.api.BufferAllocator;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.transport.api.DefaultExecutionContext;
import io.servicetalk.transport.api.ExecutionContext;
import io.servicetalk.transport.api.ExecutionStrategy;
import io.servicetalk.transport.api.IoExecutor;

import javax.annotation.Nullable;

import static io.servicetalk.transport.netty.internal.GlobalExecutionContext.globalExecutionContext;
import static java.util.Objects.requireNonNull;

/**
 * A builder of {@link ExecutionContext}. If any of the components of {@link ExecutionContext} is not provided, then
 * the corresponding component from {@link GlobalExecutionContext} will be chosen. If none of the components are
 * provided then {@link GlobalExecutionContext#globalExecutionContext()} will be returned.
 */
public final class ExecutionContextBuilder {

    @Nullable
    private IoExecutor ioExecutor;
    @Nullable
    private Executor executor;
    @Nullable
    private BufferAllocator allocator;
    @Nullable
    private ExecutionStrategy strategy;

    /**
     * New instance.
     */
    public ExecutionContextBuilder() {
    }

    /**
     * Copy constructor.
     *
     * @param other existing {@link ExecutionContextBuilder} to copy the config from.
     */
    public ExecutionContextBuilder(ExecutionContextBuilder other) {
        ioExecutor = other.ioExecutor;
        executor = other.executor;
        allocator = other.allocator;
        strategy = other.strategy;
    }

    /**
     * Sets the {@link IoExecutor} to use.
     *
     * @param ioExecutor {@link IoExecutor} to use.
     * @return {@code this}.
     */
    public ExecutionContextBuilder ioExecutor(IoExecutor ioExecutor) {
        this.ioExecutor = requireNonNull(ioExecutor);
        return this;
    }

    /**
     * Sets the {@link Executor} to use.
     *
     * @param executor {@link Executor} to use.
     * @return {@code this}.
     */
    public ExecutionContextBuilder executor(Executor executor) {
        this.executor = requireNonNull(executor);
        return this;
    }

    /**
     * Sets the {@link BufferAllocator} to use.
     *
     * @param allocator {@link BufferAllocator} to use.
     * @return {@code this}.
     */
    public ExecutionContextBuilder bufferAllocator(BufferAllocator allocator) {
        this.allocator = requireNonNull(allocator);
        return this;
    }

    /**
     * Sets the {@link ExecutionStrategy} to use.
     *
     * @param strategy {@link ExecutionStrategy} to use.
     * @return {@code this}.
     */
    public ExecutionContextBuilder executionStrategy(ExecutionStrategy strategy) {
        this.strategy = requireNonNull(strategy);
        return this;
    }

    /**
     * Builds a new {@link ExecutionContext} or return {@link GlobalExecutionContext#globalExecutionContext()} if none
     * of the components are set in this builder.
     *
     * @return {@link ExecutionContext}.
     */
    public ExecutionContext build() {
        // Do not refer to globalExecutionContext() unless someone builds an ExecutionContext with defaults.
        // This is to make sure we do not eagerly initialize the resources used by the globalExecutionContext()
        if (ioExecutor == null && executor == null && allocator == null && strategy == null) {
            return globalExecutionContext();
        }
        return new DefaultExecutionContext(allocator == null ? globalExecutionContext().bufferAllocator() : allocator,
                ioExecutor == null ? globalExecutionContext().ioExecutor() : ioExecutor,
                executor == null ? globalExecutionContext().executor() : executor,
                strategy == null ? globalExecutionContext().executionStrategy() : strategy);
    }
}
