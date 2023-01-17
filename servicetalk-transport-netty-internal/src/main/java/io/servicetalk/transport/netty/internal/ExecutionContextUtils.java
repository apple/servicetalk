/*
 * Copyright © 2023 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.api.ListenableAsyncCloseable;
import io.servicetalk.transport.api.DelegatingExecutionContext;
import io.servicetalk.transport.api.ExecutionContext;
import io.servicetalk.transport.api.ExecutionStrategy;
import io.servicetalk.transport.api.IoExecutor;
import io.servicetalk.transport.api.IoThreadFactory;

import io.netty.channel.Channel;
import io.netty.channel.EventLoop;
import io.netty.util.concurrent.FastThreadLocal;

import static io.servicetalk.transport.netty.internal.NettyIoExecutors.fromNettyEventLoop;

public final class ExecutionContextUtils {
    private static final FastThreadLocal<IoExecutor> CHANNEL_IO_EXECUTOR = new FastThreadLocal<>();

    private ExecutionContextUtils() {
        // No instances
    }

    /**
     * Creates an {@link IoExecutor} around the Channel {@link EventLoop}.
     * <p>
     * This method must only be called from inside the {@link EventLoop}, since for performance reasons it will
     * cache the {@link IoExecutor} in a thread local and reuse it if present.
     *
     * @param channel the netty channel to pick the event loop from.
     * @param isIoThreadSupported if threads used by the {@link IoExecutor} are marked with
     * {@link IoThreadFactory.IoThread} interface.
     * @return The (potentially cached) {@link IoExecutor} wrapped around the Channel {@link EventLoop}.
     */
    public static IoExecutor fromChannel(final Channel channel, boolean isIoThreadSupported) {
        assert channel.eventLoop().inEventLoop();
        IoExecutor ioExecutor = CHANNEL_IO_EXECUTOR.getIfExists();
        if (ioExecutor != null) {
            return ioExecutor;
        }
        ioExecutor = fromNettyEventLoop(channel.eventLoop(), isIoThreadSupported);
        CHANNEL_IO_EXECUTOR.set(ioExecutor);
        return ioExecutor;
    }

    /**
     * Utility that maps {@link Channel#eventLoop()} into {@link IoExecutor} and caches the result for future mappings
     * to reduce allocations. Because {@link IoExecutor} implements {@link ListenableAsyncCloseable} interface, its
     * allocation cost is relatively high.
     *
     * @param channel {@link Channel} registered for a single {@link EventLoop} thread
     * @param builderExecutionContext {@link ExecutionContext} pre-computed by the builder for new connections
     * @param <ES> the execution strategy used inside the execution context.
     * @return {@link ExecutionContext} which has {@link IoExecutor} backed by a single {@link EventLoop} thread
     * associated with the passed {@link Channel}.
     */
    public static <ES extends ExecutionStrategy> ExecutionContext<ES> channelExecutionContext(
            final Channel channel,
            final ExecutionContext<ES> builderExecutionContext) {
        final IoExecutor channelIoExecutor = fromChannel(channel,
                builderExecutionContext.ioExecutor().isIoThreadSupported());
        return new DelegatingExecutionContext<ES>(builderExecutionContext) {
            @Override
            public IoExecutor ioExecutor() {
                return channelIoExecutor;
            }
        };
    }

    // for tests only
    public static void clearThreadLocal() {
        CHANNEL_IO_EXECUTOR.remove();
    }
}
