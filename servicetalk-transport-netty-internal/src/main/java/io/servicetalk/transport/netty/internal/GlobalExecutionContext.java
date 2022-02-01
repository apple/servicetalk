/*
 * Copyright © 2018, 2021 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Executors;
import io.servicetalk.transport.api.DefaultExecutionContext;
import io.servicetalk.transport.api.ExecutionContext;
import io.servicetalk.transport.api.ExecutionStrategy;
import io.servicetalk.transport.api.IoExecutor;

import io.netty.channel.EventLoopGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.transport.api.ExecutionStrategy.offloadAll;
import static io.servicetalk.transport.netty.internal.NettyIoExecutors.createIoExecutor;

/**
 * ServiceTalk's shared {@link ExecutionContext} with reasonable defaults for APIs when a user doesn't provide one.
 * <p>
 * A lazily initialized singleton {@link ExecutionContext}, the lifecycle of this instance shouldn't need to be managed
 * by the user. Don't attempt to close the executors.
 */
public final class GlobalExecutionContext {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExecutionContext.class);

    private GlobalExecutionContext() {
        // No instances
    }

    /**
     * Get the {@link GlobalExecutionContext}.
     *
     * @return the singleton instance
     */
    public static ExecutionContext<ExecutionStrategy> globalExecutionContext() {
        return GlobalExecutionContextInitializer.INSTANCE;
    }

    private static final class GlobalExecutionContextInitializer {

        static final ExecutionContext<ExecutionStrategy> INSTANCE;

        static {
            final IoExecutor ioExecutor = new GlobalIoExecutor(createIoExecutor(GlobalIoExecutor.NAME_PREFIX));
            final Executor executor = Executors.global();
            INSTANCE = new DefaultExecutionContext<>(DEFAULT_ALLOCATOR, ioExecutor, executor, offloadAll());
            LOGGER.debug("Initialized GlobalExecutionContext");
        }

        private GlobalExecutionContextInitializer() {
            // No instances
        }
    }

    private static final class GlobalIoExecutor implements EventLoopAwareNettyIoExecutor {

        private static final Logger LOGGER = LoggerFactory.getLogger(GlobalIoExecutor.class);

        static final String NAME_PREFIX = "servicetalk-global-io-executor";

        private final EventLoopAwareNettyIoExecutor delegate;

        GlobalIoExecutor(final EventLoopAwareNettyIoExecutor delegate) {
            this.delegate = delegate;
        }

        @Override
        public Completable closeAsync() {
            return delegate.closeAsync()
                    .beforeOnSubscribe(__ -> log(LOGGER, NAME_PREFIX, "closeAsync()"));
        }

        @Override
        public Completable closeAsyncGracefully() {
            return delegate.closeAsyncGracefully()
                    .beforeOnSubscribe(__ -> log(LOGGER, NAME_PREFIX, "closeAsyncGracefully()"));
        }

        @Override
        public Completable onClose() {
            return delegate.onClose();
        }

        @Override
        public boolean isUnixDomainSocketSupported() {
            return delegate.isUnixDomainSocketSupported();
        }

        @Override
        public boolean isFileDescriptorSocketAddressSupported() {
            return delegate.isFileDescriptorSocketAddressSupported();
        }

        @Override
        public boolean isIoThreadSupported() {
            return delegate.isIoThreadSupported();
        }

        @Override
        public boolean isCurrentThreadEventLoop() {
            return delegate.isCurrentThreadEventLoop();
        }

        @Override
        public EventLoopGroup eventLoopGroup() {
            return delegate.eventLoopGroup();
        }

        @Override
        public EventLoopAwareNettyIoExecutor next() {
            return delegate.next();
        }

        @Override
        @Deprecated
        public Executor asExecutor() {
            return delegate.asExecutor();
        }

        @Override
        public Cancellable execute(final Runnable task) throws RejectedExecutionException {
            return delegate.execute(task);
        }

        @Override
        public Cancellable schedule(final Runnable task, final long delay, final TimeUnit unit)
                throws RejectedExecutionException {
            return delegate.schedule(task, delay, unit);
        }
    }

    private static void log(final Logger logger, final String name, final String methodName) {
        logger.debug("Closure of \"{}\" was initiated using {} method. Closing the global instance before " +
                "closing all resources that use it may result in unexpected behavior.", name, methodName);
    }
}
