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
import io.servicetalk.concurrent.api.Executors;
import io.servicetalk.concurrent.internal.DefaultThreadFactory;
import io.servicetalk.transport.api.DefaultExecutionContext;
import io.servicetalk.transport.api.ExecutionContext;
import io.servicetalk.transport.api.IoExecutor;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.rules.ExternalResource;

import java.util.concurrent.ThreadFactory;
import java.util.function.Supplier;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.concurrent.internal.Await.awaitIndefinitely;
import static io.servicetalk.transport.netty.internal.NettyIoExecutors.createIoExecutor;

/**
 * Test helper that creates and disposes an {@link ExecutionContext} for your test case or suite.
 * <p>
 * Can be used with a @{@link Rule} field and a {@code static} field with @{@link ClassRule}.
 */
public final class ExecutionContextRule extends ExternalResource implements ExecutionContext {

    private final Supplier<Executor> executorSupplier;
    private final Supplier<IoExecutor> ioExecutorSupplier;
    private final Supplier<BufferAllocator> allocatorSupplier;

    private ExecutionContext ctx;

    public ExecutionContextRule(final Supplier<BufferAllocator> allocatorSupplier,
                                final Supplier<IoExecutor> ioExecutorSupplier,
                                final Supplier<Executor> executorSupplier) {
        this.executorSupplier = executorSupplier;
        this.ioExecutorSupplier = ioExecutorSupplier;
        this.allocatorSupplier = allocatorSupplier;
    }

    public static ExecutionContextRule immediate() {
        return immediate(new DefaultThreadFactory());
    }

    public static ExecutionContextRule immediate(ThreadFactory ioThreadFactory) {
        return new ExecutionContextRule(() -> DEFAULT_ALLOCATOR, newIoExecutor(ioThreadFactory), Executors::immediate
        );
    }

    public static ExecutionContextRule cached() {
        return cached(new DefaultThreadFactory());
    }

    public static ExecutionContextRule cached(ThreadFactory ioThreadFactory) {
        return new ExecutionContextRule(() -> DEFAULT_ALLOCATOR, newIoExecutor(ioThreadFactory),
                Executors::newCachedThreadExecutor
        );
    }

    public static ExecutionContextRule fixed(int size) {
        return fixed(size, new DefaultThreadFactory());
    }

    public static ExecutionContextRule fixed(int size, ThreadFactory ioThreadFactory) {
        return new ExecutionContextRule(() -> DEFAULT_ALLOCATOR, newIoExecutor(ioThreadFactory),
                () -> Executors.newFixedSizeExecutor(size)
        );
    }

    public static ExecutionContextRule single() {
        return fixed(1);
    }

    public static ExecutionContextRule single(ThreadFactory ioThreadFactory) {
        return fixed(1, ioThreadFactory);
    }

    @Override
    protected void before() {
        ctx = new DefaultExecutionContext(allocatorSupplier.get(), ioExecutorSupplier.get(), executorSupplier.get());
    }

    @Override
    protected void after() {
        try {
            awaitIndefinitely(ctx.getIoExecutor().closeAsync()
                    .mergeDelayError(ctx.getExecutor().closeAsync()));
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    @Override
    public BufferAllocator getBufferAllocator() {
        return ctx.getBufferAllocator();
    }

    @Override
    public IoExecutor getIoExecutor() {
        return ctx.getIoExecutor();
    }

    @Override
    public Executor getExecutor() {
        return ctx.getExecutor();
    }

    private static Supplier<IoExecutor> newIoExecutor(ThreadFactory threadFactory) {
        return () -> createIoExecutor(0, threadFactory);
    }
}
