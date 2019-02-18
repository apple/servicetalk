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
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.ListenableAsyncCloseable;

import io.netty.channel.Channel;

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import static io.servicetalk.concurrent.Cancellable.IGNORE_CANCEL;
import static io.servicetalk.transport.netty.internal.CloseStates.CLOSING;
import static io.servicetalk.transport.netty.internal.CloseStates.GRACEFULLY_CLOSING;
import static io.servicetalk.transport.netty.internal.CloseStates.OPEN;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.atomic.AtomicIntegerFieldUpdater.newUpdater;

/**
 * Implements {@link ListenableAsyncCloseable} using a netty {@link Channel}.
 */
final class NettyChannelListenableAsyncCloseable implements ListenableAsyncCloseable {
    private static final AtomicIntegerFieldUpdater<NettyChannelListenableAsyncCloseable> stateUpdater =
            newUpdater(NettyChannelListenableAsyncCloseable.class, "state");
    private final Channel channel;
    @SuppressWarnings("unused")
    private volatile int state;
    private final Completable onClose;

    /**
     * New instance.
     *
     * @param channel to use.
     * @param offloadingExecutor {@link Executor} used to offload any signals to any asynchronous created by this
     * {@link NettyChannelListenableAsyncCloseable} which could interact with the EventLoop.
     */
    NettyChannelListenableAsyncCloseable(Channel channel, Executor offloadingExecutor) {
        this.channel = requireNonNull(channel);
        onClose = new Completable() {
            @Override
            protected void handleSubscribe(final Subscriber subscriber) {
                subscriber.onSubscribe(IGNORE_CANCEL);
                NettyFutureCompletable.connectToSubscriber(subscriber, channel.closeFuture());
            }
        }
        // Since onClose termination will come from EventLoop, offload those signals to avoid blocking EventLoop
        .publishOn(offloadingExecutor);
    }

    @Override
    public Completable closeAsync() {
        return new Completable() {
            @Override
            protected void handleSubscribe(final Subscriber subscriber) {
                onClose().subscribe(subscriber);
                if (stateUpdater.getAndSet(NettyChannelListenableAsyncCloseable.this, CLOSING) != CLOSING) {
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
                if (!stateUpdater.compareAndSet(NettyChannelListenableAsyncCloseable.this, OPEN, GRACEFULLY_CLOSING)) {
                    onClose().subscribe(subscriber);
                    return;
                }

                final ConnectionHolderChannelHandler<?, ?> holder =
                        channel.pipeline().get(ConnectionHolderChannelHandler.class);
                NettyConnection<?, ?> connection = holder == null ? null : holder.connection();
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
        return onClose;
    }
}
