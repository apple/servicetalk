/*
 * Copyright © 2021 Apple Inc. and the ServiceTalk project authors
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

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.function.Supplier;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.concurrent.api.AsyncCloseables.newCompositeCloseable;
import static io.servicetalk.concurrent.api.Executors.newCachedThreadExecutor;
import static io.servicetalk.transport.netty.internal.NettyIoExecutors.createIoExecutor;

/**
 * An {@link Extension} that creates and disposes an {@link ExecutionContext} for your test case or suite.
 * <p>
 * Can be used with a {@link RegisterExtension} field. If used with a class static field then use
 * {@link #setClassLevel(boolean) setClassLevel(true)} to create contained {@link ExecutionContext} instance only once
 * for all tests in class.
 *
 */
public final class ExecutionContextExtension implements AfterEachCallback, BeforeEachCallback,
                                                        AfterAllCallback, BeforeAllCallback,
                                                        ExecutionContext<ExecutionStrategy> {

    private static final String IO_THREAD_PREFIX = "exec-ctx-ext-io";
    private static final String EXEC_THREAD_PREFIX = "exec-ctx-ext-exec";
    private final Supplier<Executor> executorSupplier;
    private final Supplier<IoExecutor> ioExecutorSupplier;
    private final Supplier<BufferAllocator> allocatorSupplier;
    private final Supplier<ExecutionStrategy> executionStrategySupplier;

    private ExecutionContext<ExecutionStrategy> ctx;
    private boolean classLevel;

    public ExecutionContextExtension(final Supplier<BufferAllocator> allocatorSupplier,
                                     final Supplier<IoExecutor> ioExecutorSupplier,
                                     final Supplier<Executor> executorSupplier) {
        this(allocatorSupplier, ioExecutorSupplier, executorSupplier, ExecutionStrategy::offloadAll);
    }

    private ExecutionContextExtension(final Supplier<BufferAllocator> allocatorSupplier,
                                      final Supplier<IoExecutor> ioExecutorSupplier,
                                      final Supplier<Executor> executorSupplier,
                                      final Supplier<ExecutionStrategy> executionStrategySupplier) {
        this.executorSupplier = executorSupplier;
        this.ioExecutorSupplier = ioExecutorSupplier;
        this.allocatorSupplier = allocatorSupplier;
        this.executionStrategySupplier = executionStrategySupplier;
    }

    public static ExecutionContextExtension immediate() {
        return immediate(new NettyIoThreadFactory(IO_THREAD_PREFIX));
    }

    private static ExecutionContextExtension immediate(NettyIoThreadFactory nettyIoThreadFactory) {
        return new ExecutionContextExtension(() -> DEFAULT_ALLOCATOR, newIoExecutor(nettyIoThreadFactory),
                Executors::immediate);
    }

    public static ExecutionContextExtension cached() {
        return cached(new NettyIoThreadFactory(IO_THREAD_PREFIX));
    }

    public static ExecutionContextExtension cached(NettyIoThreadFactory nettyIoThreadFactory) {
        return new ExecutionContextExtension(() -> DEFAULT_ALLOCATOR, newIoExecutor(nettyIoThreadFactory),
                () -> Executors.newCachedThreadExecutor(new DefaultThreadFactory(EXEC_THREAD_PREFIX))
        );
    }

    public static ExecutionContextExtension cached(String ioThreadPrefix, String executorThreadPrefix) {
        return new ExecutionContextExtension(() -> DEFAULT_ALLOCATOR,
                () -> createIoExecutor(ioThreadPrefix),
                () -> newCachedThreadExecutor(new DefaultThreadFactory(executorThreadPrefix)));
    }

    private static ExecutionContextExtension fixed(int size) {
        return fixed(size, new NettyIoThreadFactory(IO_THREAD_PREFIX));
    }

    private static ExecutionContextExtension fixed(int size, NettyIoThreadFactory nettyIoThreadFactory) {
        return new ExecutionContextExtension(() -> DEFAULT_ALLOCATOR, newIoExecutor(nettyIoThreadFactory),
                () -> Executors.newFixedSizeExecutor(size, new DefaultThreadFactory(EXEC_THREAD_PREFIX))
        );
    }

    public static ExecutionContextExtension single() {
        return fixed(1);
    }

    public static ExecutionContextExtension single(NettyIoThreadFactory nettyIoThreadFactory) {
        return fixed(1, nettyIoThreadFactory);
    }

    /**
     * Set to true if the extension is being shared among all tests in a class.
     *
     * @param classLevel true if extension is shared between tests within test class otherwise false to create a new
     * instance for each test.
     * @return this
     */
    public ExecutionContextExtension setClassLevel(final boolean classLevel) {
        this.classLevel = classLevel;
        return this;
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

    @Override
    public void afterEach(ExtensionContext context) {
        if (!classLevel) {
            closeAll();
        }
    }

    private void closeAll() {
        try {
            newCompositeCloseable().appendAll(ctx.ioExecutor(), ctx.executor()).close();
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        if (!classLevel) {
            createContext();
        }
    }

    private void createContext() {
        ctx = new DefaultExecutionContext<>(allocatorSupplier.get(), ioExecutorSupplier.get(), executorSupplier.get(),
                executionStrategySupplier.get());
    }

    @Override
    public void afterAll(final ExtensionContext context) {
        if (classLevel) {
            closeAll();
        }
    }

    @Override
    public void beforeAll(final ExtensionContext context) {
        if (classLevel) {
            createContext();
        }
    }
}
