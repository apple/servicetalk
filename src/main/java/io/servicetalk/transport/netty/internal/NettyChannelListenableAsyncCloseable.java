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
import io.servicetalk.concurrent.api.ListenableAsyncCloseable;

import io.netty.channel.Channel;

import static java.util.Objects.requireNonNull;

/**
 * Implements {@link ListenableAsyncCloseable} using a netty {@link Channel}.
 */
final class NettyChannelListenableAsyncCloseable implements ListenableAsyncCloseable {

    private final Channel channel;
    private final CloseState state = new CloseState();

    /**
     * New instance.
     *
     * @param channel to use.
     */
    NettyChannelListenableAsyncCloseable(Channel channel) {
        this.channel = requireNonNull(channel);
    }

    @Override
    public Completable closeAsync() {
        return new Completable() {
            @Override
            protected void handleSubscribe(final Subscriber subscriber) {
                onClose().subscribe(subscriber);
                if (state.tryCloseAsync()) {
                    channel.close();
                }
            }
        };
    }

    @Override
    public Completable closeAsyncGracefully() {
        return new Completable() {
            @Override
            protected void handleSubscribe(final Subscriber subscriber) {
                if (!state.tryCloseAsyncGracefully()) {
                    onClose().subscribe(subscriber);
                    return;
                }

                final NettyConnectionHolder holder = channel.pipeline().get(NettyConnectionHolder.class);
                NettyConnection connection = holder == null ? null : holder.getConnection();
                if (connection != null) {
                    connection.closeAsyncGracefully().subscribe(subscriber);
                } else {
                    onClose().subscribe(subscriber);
                    channel.close();
                }
            }
        };
    }

    @Override
    public Completable onClose() {
        return new NettyFutureCompletable(channel::closeFuture);
    }
}
