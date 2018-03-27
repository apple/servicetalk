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

import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.transport.api.ServerContext;

import io.netty.channel.Channel;

import java.net.SocketAddress;

/**
 * {@link ServerContext} implementation using a netty {@link Channel}.
 */
public final class NettyServerContext implements ServerContext {

    private final Channel channel;
    private final Completable close;
    private final Completable onClose;

    private NettyServerContext(Channel channel, Completable close, Completable onClose) {
        this.channel = channel;
        this.close = close;
        this.onClose = onClose;
    }

    /**
     * Wrap the passed {@link NettyServerContext}.
     *
     * @param toWrap {@link NettyServerContext} to wrap.
     * @param closeTrigger {@link Completable} which needs to be closed first before {@code toWrap} will be closed.
     * @return A new {@link NettyServerContext} instance.
     */
    public static ServerContext wrap(NettyServerContext toWrap, Completable closeTrigger) {
        return wrap(toWrap.channel, closeTrigger);
    }

    /**
     * Wrap the passed {@link NettyServerContext}.
     *
     * @param channel {@link Channel} to wrap.
     * @param closeTrigger {@link Completable} which needs to closed first before {@code channel} will be closed.
     * @return A new {@link NettyServerContext} instance.
     */
    public static ServerContext wrap(Channel channel, Completable closeTrigger) {
        NettyChannelListenableAsyncCloseable closeable = new NettyChannelListenableAsyncCloseable(channel);
        return new NettyServerContext(channel, closeTrigger.andThen(closeable.closeAsync()), closeable.onClose());
    }

    @Override
    public SocketAddress getListenAddress() {
        return channel.localAddress();
    }

    @Override
    public Completable closeAsync() {
        return close;
    }

    @Override
    public Completable onClose() {
        return onClose;
    }
}
