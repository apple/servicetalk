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

import io.servicetalk.concurrent.api.AsyncCloseable;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.CompositeCloseable;
import io.servicetalk.concurrent.api.ListenableAsyncCloseable;
import io.servicetalk.transport.api.ExecutionContext;
import io.servicetalk.transport.api.ServerContext;

import io.netty.channel.Channel;

import java.net.SocketAddress;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.AsyncCloseables.newCompositeCloseable;
import static io.servicetalk.concurrent.api.AsyncCloseables.toListenableAsyncCloseable;
import static io.servicetalk.concurrent.api.Executors.immediate;

/**
 * {@link ServerContext} implementation using a netty {@link Channel}.
 */
public final class NettyServerContext implements ServerContext {

    private final Channel listenChannel;
    private final ListenableAsyncCloseable closeable;
    private final ExecutionContext<?> executionContext;

    private NettyServerContext(Channel listenChannel, final ListenableAsyncCloseable closeable,
                               final ExecutionContext<?> executionContext) {
        this.listenChannel = listenChannel;
        this.closeable = closeable;
        this.executionContext = executionContext;
    }

    /**
     * Wrap the passed {@link NettyServerContext}.
     *
     * @param toWrap {@link NettyServerContext} to wrap.
     * @param closeBefore {@link Completable} which needs to be closed first before {@code toWrap} will be closed.
     * @return A new {@link NettyServerContext} instance.
     */
    public static ServerContext wrap(NettyServerContext toWrap, AsyncCloseable closeBefore) {
        return new NettyServerContext(toWrap.listenChannel,
                toListenableAsyncCloseable(newCompositeCloseable().appendAll(closeBefore, toWrap.closeable)),
                toWrap.executionContext);
    }

    /**
     * Wrap the passed {@link NettyServerContext}.
     *
     * @param listenChannel {@link Channel} to wrap.
     * @param channelSetCloseable {@link ChannelSet} to wrap.
     * @param closeBefore {@link Completable} which needs to closed first before {@code listenChannel} will be closed.
     * @param executionContext {@link ExecutionContext} used by this server.
     * @param offloadAsyncClose If true then signals for close {@link Completable} will be offloaded
     * @return A new {@link NettyServerContext} instance.
     */
    public static ServerContext wrap(Channel listenChannel, ListenableAsyncCloseable channelSetCloseable,
                                     @Nullable AsyncCloseable closeBefore, ExecutionContext<?> executionContext,
                                     boolean offloadAsyncClose) {
        final NettyChannelListenableAsyncCloseable channelCloseable =
                new NettyChannelListenableAsyncCloseable(listenChannel,
                        offloadAsyncClose ? executionContext.executor() : immediate());
        final CompositeCloseable closeAsync = closeBefore == null ?
                newCompositeCloseable().appendAll(channelCloseable, channelSetCloseable) :
                newCompositeCloseable().appendAll(closeBefore, channelCloseable, channelSetCloseable);
        return new NettyServerContext(listenChannel, toListenableAsyncCloseable(closeAsync), executionContext);
    }

    @Override
    public SocketAddress listenAddress() {
        return listenChannel.localAddress();
    }

    @Override
    public void acceptConnections(final boolean accept) {
        listenChannel.config().setAutoRead(accept);
    }

    @Override
    public ExecutionContext<?> executionContext() {
        return executionContext;
    }

    @Override
    public Completable closeAsync() {
        return closeable.closeAsync();
    }

    @Override
    public Completable closeAsyncGracefully() {
        return closeable.closeAsyncGracefully();
    }

    @Override
    public Completable onClose() {
        return closeable.onClose();
    }
}
