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
import io.servicetalk.concurrent.api.DefaultThreadFactory;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Executors;
import io.servicetalk.transport.api.DefaultExecutionContext;
import io.servicetalk.transport.api.ExecutionContext;
import io.servicetalk.transport.api.ExecutionStrategy;
import io.servicetalk.transport.api.IoExecutor;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.rules.ExternalResource;

import java.util.function.Supplier;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.concurrent.api.AsyncCloseables.newCompositeCloseable;
import static io.servicetalk.concurrent.api.Executors.newCachedThreadExecutor;
import static io.servicetalk.transport.netty.internal.NettyIoExecutors.createIoExecutor;
import static io.servicetalk.transport.netty.internal.OffloadFromIOExecutionStrategy.OFFLOAD_FROM_IO_STRATEGY;

/**
 * Test helper that creates and disposes an {@link ExecutionContext} for your test case or suite.
 * <p>
 * Can be used with a @{@link Rule} field and a {@code static} field with @{@link ClassRule}.
 */
public final class ExecutionContextRule extends ExternalResource implements ExecutionContext {
    private static final String IO_THREAD_PREFIX = "exec-ctx-rule-io";
    private final Supplier<Executor> executorSupplier;
    private final Supplier<IoExecutor> ioExecutorSupplier;
    private final Supplier<BufferAllocator> allocatorSupplier;
    private final Supplier<ExecutionStrategy> executionStrategySupplier;

    private ExecutionContext ctx;

    public ExecutionContextRule(final Supplier<BufferAllocator> allocatorSupplier,
                                final Supplier<IoExecutor> ioExecutorSupplier,
                                final Supplier<Executor> executorSupplier) {
        this(allocatorSupplier, ioExecutorSupplier, executorSupplier, () -> OFFLOAD_FROM_IO_STRATEGY);
    }

    public ExecutionContextRule(final Supplier<BufferAllocator> allocatorSupplier,
                                final Supplier<IoExecutor> ioExecutorSupplier,
                                final Supplier<Executor> executorSupplier,
                                final Supplier<ExecutionStrategy> executionStrategySupplier) {
        this.executorSupplier = executorSupplier;
        this.ioExecutorSupplier = ioExecutorSupplier;
        this.allocatorSupplier = allocatorSupplier;
        this.executionStrategySupplier = executionStrategySupplier;
    }

    public static ExecutionContextRule immediate() {
        return immediate(new NettyIoThreadFactory(IO_THREAD_PREFIX));
    }

    public static ExecutionContextRule immediate(NettyIoThreadFactory nettyIoThreadFactory) {
        return new ExecutionContextRule(() -> DEFAULT_ALLOCATOR,
                newIoExecutor(nettyIoThreadFactory),
                Executors::immediate
        );
    }

    public static ExecutionContextRule cached() {
        return cached(new NettyIoThreadFactory(IO_THREAD_PREFIX));
    }

    public static ExecutionContextRule cached(NettyIoThreadFactory nettyIoThreadFactory) {
        return new ExecutionContextRule(() -> DEFAULT_ALLOCATOR,
                newIoExecutor(nettyIoThreadFactory),
                Executors::newCachedThreadExecutor
        );
    }

    public static ExecutionContextRule cached(String ioThreadPrefix, String executorThreadPrefix) {
        return new ExecutionContextRule(() -> DEFAULT_ALLOCATOR,
                newIoExecutor(new NettyIoThreadFactory(ioThreadPrefix)),
                () -> newCachedThreadExecutor(new DefaultThreadFactory(executorThreadPrefix)));
    }

    public static ExecutionContextRule fixed(int size) {
        return fixed(size, new NettyIoThreadFactory(IO_THREAD_PREFIX));
    }

    public static ExecutionContextRule fixed(int size, NettyIoThreadFactory nettyIoThreadFactory) {
        return new ExecutionContextRule(() -> DEFAULT_ALLOCATOR, newIoExecutor(nettyIoThreadFactory),
                () -> Executors.newFixedSizeExecutor(size)
        );
    }

    public static ExecutionContextRule single() {
        return fixed(1);
    }

    public static ExecutionContextRule single(NettyIoThreadFactory nettyIoThreadFactory) {
        return fixed(1, nettyIoThreadFactory);
    }

    @Override
    protected void before() {
        ctx = new DefaultExecutionContext(allocatorSupplier.get(), ioExecutorSupplier.get(), executorSupplier.get(),
                executionStrategySupplier.get());
    }

    @Override
    protected void after() {
        try {
            newCompositeCloseable().appendAll(ctx.ioExecutor(), ctx.executor()).close();
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    @Override
    public BufferAllocator bufferAllocator() {
        return ctx.bufferAllocator();
    }

    @Override
    public IoExecutor ioExecutor() {
        return ctx.ioExecutor();
    }

    @Override
    public Executor executor() {
        return ctx.executor();
    }

    @Override
    public ExecutionStrategy executionStrategy() {
        return ctx.executionStrategy();
    }

    private static Supplier<IoExecutor> newIoExecutor(NettyIoThreadFactory threadFactory) {
        return () -> createIoExecutor(threadFactory);
    }
}
