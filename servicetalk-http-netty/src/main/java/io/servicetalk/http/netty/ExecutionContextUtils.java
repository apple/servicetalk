/*
 * Copyright © 2022 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.api.ListenableAsyncCloseable;
import io.servicetalk.http.api.DelegatingHttpExecutionContext;
import io.servicetalk.http.api.HttpExecutionContext;
import io.servicetalk.transport.api.IoExecutor;

import io.netty.channel.Channel;
import io.netty.channel.EventLoop;
import io.netty.util.concurrent.FastThreadLocal;

import static io.servicetalk.transport.netty.internal.NettyIoExecutors.fromNettyEventLoop;

final class ExecutionContextUtils {

    private static final FastThreadLocal<IoExecutor> CHANNEL_IO_EXECUTOR = new FastThreadLocal<>();

    private ExecutionContextUtils() {
        // No instances
    }

    /**
     * Utility that maps {@link Channel#eventLoop()} into {@link IoExecutor} and caches the result for future mappings
     * to reduce allocations. Because {@link IoExecutor} implements {@link ListenableAsyncCloseable} interface, its
     * allocation cost is relatively high.
     *
     * @param channel {@link Channel} registered for a single {@link EventLoop} thread
     * @param builderExecutionContext {@link HttpExecutionContext} pre-computed by the builder for new connections
     * @return {@link HttpExecutionContext} which has {@link IoExecutor} backed by a single {@link EventLoop} thread
     * associated with the passed {@link Channel}.
     */
    static HttpExecutionContext channelExecutionContext(final Channel channel,
                                                        final HttpExecutionContext builderExecutionContext) {
        final IoExecutor channelIoExecutor = fromChannel(channel,
                builderExecutionContext.ioExecutor().isIoThreadSupported());
        return new DelegatingHttpExecutionContext(builderExecutionContext) {
            @Override
            public IoExecutor ioExecutor() {
                return channelIoExecutor;
            }
        };
    }

    private static IoExecutor fromChannel(final Channel channel, boolean isIoThreadSupported) {
        assert channel.eventLoop().inEventLoop();
        IoExecutor ioExecutor = CHANNEL_IO_EXECUTOR.getIfExists();
        if (ioExecutor != null) {
            return ioExecutor;
        }
        ioExecutor = fromNettyEventLoop(channel.eventLoop(), isIoThreadSupported);
        CHANNEL_IO_EXECUTOR.set(ioExecutor);
        return ioExecutor;
    }

    // for tests only
    static void clearThreadLocal() {
        CHANNEL_IO_EXECUTOR.remove();
    }
}
