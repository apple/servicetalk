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

import io.servicetalk.concurrent.api.AsyncCloseable;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.CompositeCloseable;
import io.servicetalk.concurrent.api.ListenableAsyncCloseable;
import io.servicetalk.transport.api.ServerContext;

import io.netty.channel.Channel;

import java.net.SocketAddress;

import static io.servicetalk.concurrent.api.AsyncCloseables.newCompositeCloseable;
import static io.servicetalk.concurrent.api.AsyncCloseables.toListenableAsyncCloseable;

/**
 * {@link ServerContext} implementation using a netty {@link Channel}.
 */
public final class NettyServerContext implements ServerContext {

    private final Channel listenChannel;
    private final ListenableAsyncCloseable closeable;

    private NettyServerContext(Channel listenChannel, final ListenableAsyncCloseable closeable) {
        this.listenChannel = listenChannel;
        this.closeable = closeable;
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
                toListenableAsyncCloseable(newCompositeCloseable().appendAll(closeBefore, toWrap.closeable)));
    }

    /**
     * Wrap the passed {@link NettyServerContext}.
     *
     * @param listenChannel {@link Channel} to wrap.
     * @param channelSetCloseable {@link ChannelSet} to wrap.
     * @param closeBefore {@link Completable} which needs to closed first before {@code listenChannel} will be closed.
     * @return A new {@link NettyServerContext} instance.
     */
    public static ServerContext wrap(Channel listenChannel, ListenableAsyncCloseable channelSetCloseable,
                                     AsyncCloseable closeBefore) {
        final CompositeCloseable closeAsync = newCompositeCloseable().appendAll(
                closeBefore, new NettyChannelListenableAsyncCloseable(listenChannel), channelSetCloseable);
        return new NettyServerContext(listenChannel, toListenableAsyncCloseable(closeAsync));
    }

    @Override
    public SocketAddress getListenAddress() {
        return listenChannel.localAddress();
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
