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
package io.servicetalk.http.netty;

import io.servicetalk.buffer.api.BufferAllocator;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.http.api.DefaultHttpExecutionContext;
import io.servicetalk.http.api.HttpExecutionContext;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.transport.api.ExecutionContext;
import io.servicetalk.transport.api.IoExecutor;
import io.servicetalk.transport.netty.internal.ExecutionContextBuilder;

import static io.servicetalk.http.api.HttpExecutionStrategies.defaultStrategy;

final class HttpExecutionContextBuilder {

    private final ExecutionContextBuilder executionContextBuilder;
    private HttpExecutionStrategy strategy = defaultStrategy();

    HttpExecutionContextBuilder(final HttpExecutionContextBuilder from) {
        executionContextBuilder = new ExecutionContextBuilder(from.executionContextBuilder);
        strategy = from.strategy;
    }

    HttpExecutionContextBuilder() {
        executionContextBuilder = new ExecutionContextBuilder();
        // Make sure we always set a strategy so that ExecutionContextBuilder does not create a strategy which is not
        // compatible with HTTP.
        executionContextBuilder.executionStrategy(defaultStrategy());
    }

    /**
     * Sets the {@link IoExecutor} to use.
     *
     * @param ioExecutor {@link IoExecutor} to use.
     * @return {@code this}.
     */
    public HttpExecutionContextBuilder ioExecutor(IoExecutor ioExecutor) {
        executionContextBuilder.ioExecutor(ioExecutor);
        return this;
    }

    /**
     * Sets the {@link Executor} to use.
     *
     * @param executor {@link Executor} to use.
     * @return {@code this}.
     */
    public HttpExecutionContextBuilder executor(Executor executor) {
        executionContextBuilder.executor(executor);
        return this;
    }

    /**
     * Sets the {@link BufferAllocator} to use.
     *
     * @param allocator {@link BufferAllocator} to use.
     * @return {@code this}.
     */
    public HttpExecutionContextBuilder bufferAllocator(BufferAllocator allocator) {
        executionContextBuilder.bufferAllocator(allocator);
        return this;
    }

    /**
     * Sets the {@link HttpExecutionStrategy} to use.
     *
     * @param strategy {@link HttpExecutionStrategy} to use.
     * @return {@code this}.
     */
    public HttpExecutionContextBuilder executionStrategy(HttpExecutionStrategy strategy) {
        this.strategy = strategy;
        executionContextBuilder.executionStrategy(strategy);
        return this;
    }

    /**
     * Builds a new {@link HttpExecutionContext}.
     *
     * @return {@link HttpExecutionContext}.
     */
    public HttpExecutionContext build() {
        ExecutionContext ctx = executionContextBuilder.build();
        return new DefaultHttpExecutionContext(ctx.bufferAllocator(), ctx.ioExecutor(), ctx.executor(),
                strategy);
    }
}
