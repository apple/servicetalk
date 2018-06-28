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
import io.servicetalk.concurrent.api.CompletableProcessor;
import io.servicetalk.concurrent.api.CompositeCloseable;
import io.servicetalk.concurrent.api.ListenableAsyncCloseable;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelId;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static io.servicetalk.concurrent.api.AsyncCloseables.newCompositeCloseable;

/**
 * Manages a set of {@link Channel}s to provide a mechanism for closing all of them.
 * <p>
 * Channels are removed from the set when they are closed.
 */
public final class ChannelSet implements ListenableAsyncCloseable {

    private final ChannelFutureListener remover = new ChannelFutureListener() {
        @Override
        public void operationComplete(final ChannelFuture future) {
            final Channel channel = future.channel();
            final boolean wasRemoved = channelMap.remove(channel.id()) != null;
            if (wasRemoved && state.isClosing() && channelMap.isEmpty()) {
                onClose.onComplete();
            }
        }
    };

    private final ConcurrentMap<ChannelId, Channel> channelMap = new ConcurrentHashMap<>();
    private final CompletableProcessor onClose = new CompletableProcessor();
    private final CloseState state = new CloseState();

    /**
     * Add a {@link Channel} to this {@link ChannelSet}, if it is not already present. {@link Channel#id()} is used to
     * check uniqueness.
     *
     * @param channel The {@link Channel} to add.
     * @return {@code true} if the channel was added successfully, {@code false} otherwise.
     */
    public boolean addIfAbsent(final Channel channel) {
        final boolean added = channelMap.putIfAbsent(channel.id(), channel) == null;

        if (state.isClosing()) {
            if (added) {
                channelMap.remove(channel.id(), channel);
                channel.close();
            }
        } else if (added) {
            channel.closeFuture().addListener(remover);
        }

        return added;
    }

    @Override
    public Completable closeAsync() {
        return new Completable() {
            @Override
            protected void handleSubscribe(final Subscriber subscriber) {
                onClose.subscribe(subscriber);
                if (!state.tryCloseAsync()) {
                    return;
                }

                if (channelMap.isEmpty()) {
                    onClose.onComplete();
                    return;
                }

                for (final Channel channel : channelMap.values()) {
                    // We don't try to catch exceptions here because we're only invoking Netty or ServiceTalk code, no
                    // user-provided code.
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
                    onClose.subscribe(subscriber);
                    return;
                }

                if (channelMap.isEmpty()) {
                    onClose.subscribe(subscriber);
                    onClose.onComplete();
                    return;
                }

                CompositeCloseable closeable = newCompositeCloseable().concat(() -> onClose);

                for (final Channel channel : channelMap.values()) {
                    final NettyConnectionHolder holder = channel.pipeline().get(NettyConnectionHolder.class);
                    NettyConnection connection = holder == null ? null : holder.getConnection();
                    if (connection != null) {
                        closeable = closeable.merge(connection);
                    } else {
                        channel.close();
                    }
                }

                closeable.closeAsyncGracefully().subscribe(subscriber);
            }
        };
    }

    @Override
    public Completable onClose() {
        return onClose;
    }
}
