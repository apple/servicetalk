/*
 * Copyright © 2020 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.transport.api.ConnectionObserver;
import io.servicetalk.transport.api.ConnectionObserver.SecurityHandshakeObserver;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufHolder;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.kqueue.KQueue;

import javax.annotation.Nullable;

import static io.netty.channel.ChannelOption.TCP_FASTOPEN_CONNECT;
import static io.servicetalk.transport.netty.internal.ChannelCloseUtils.channelError;
import static java.util.Objects.requireNonNull;

/**
 * A {@link ChannelInitializer} that registers a {@link ConnectionObserver} for all channels.
 */
public final class ConnectionObserverInitializer implements ChannelInitializer {

    private final ConnectionObserver observer;
    private final boolean secure;
    private final boolean client;

    /**
     * Creates a new instance.
     *
     * @param observer {@link ConnectionObserver} to report network events.
     * @param secure {@code true} if the observed connection is secure
     * @param client {@code true} if this initializer is used on the client-side
     */
    public ConnectionObserverInitializer(final ConnectionObserver observer, final boolean secure,
                                         final boolean client) {
        this.observer = requireNonNull(observer);
        this.secure = secure;
        this.client = client;
    }

    @Override
    public void init(final Channel channel) {
        channel.closeFuture().addListener((ChannelFutureListener) future -> {
            Throwable t = channelError(channel);
            if (t == null) {
                observer.connectionClosed();
            } else {
                observer.connectionClosed(t);
            }
        });
        channel.pipeline().addLast(new ConnectionObserverHandler(observer, secure, isFastOpen(channel)));
    }

    private boolean isFastOpen(final Channel channel) {
        return client && secure && Boolean.TRUE.equals(channel.config().getOption(TCP_FASTOPEN_CONNECT)) &&
                (Epoll.isTcpFastOpenClientSideAvailable() || KQueue.isTcpFastOpenClientSideAvailable());
    }

    static final class ConnectionObserverHandler extends ChannelDuplexHandler {

        private final ConnectionObserver observer;
        private final boolean secure;
        private boolean tcpHandshakeComplete;
        @Nullable
        private SecurityHandshakeObserver handshakeObserver;

        ConnectionObserverHandler(final ConnectionObserver observer, final boolean secure, final boolean fastOpen) {
            this.observer = observer;
            this.secure = secure;
            if (fastOpen) {
                reportSecurityHandshakeStarting();
            }
        }

        @Override
        public void handlerAdded(final ChannelHandlerContext ctx) {
            if (ctx.channel().isActive()) {
                reportTcpHandshakeComplete();
                if (secure) {
                    reportSecurityHandshakeStarting();
                }
            }
        }

        @Override
        public void channelActive(final ChannelHandlerContext ctx) {
            reportTcpHandshakeComplete();
            if (secure) {
                reportSecurityHandshakeStarting();
            }
            ctx.fireChannelActive();
        }

        void reportTcpHandshakeComplete() {
            if (!tcpHandshakeComplete) {
                tcpHandshakeComplete = true;
                observer.onTransportHandshakeComplete();
            }
        }

        void reportSecurityHandshakeStarting() {
            if (handshakeObserver == null) {
                handshakeObserver = observer.onSecurityHandshake();
            }
        }

        @Nullable
        SecurityHandshakeObserver handshakeObserver() {
            return handshakeObserver;
        }

        @Override
        public void channelRead(final ChannelHandlerContext ctx, final Object msg) {
            if (msg instanceof ByteBuf) {
                observer.onDataRead(((ByteBuf) msg).readableBytes());
            } else if (msg instanceof ByteBufHolder) {
                observer.onDataRead(((ByteBufHolder) msg).content().readableBytes());
            }
            ctx.fireChannelRead(msg);
        }

        @Override
        public void write(final ChannelHandlerContext ctx, final Object msg, final ChannelPromise promise) {
            if (msg instanceof ByteBuf) {
                observer.onDataWrite(((ByteBuf) msg).readableBytes());
            } else if (msg instanceof ByteBufHolder) {
                observer.onDataWrite(((ByteBufHolder) msg).content().readableBytes());
            }
            ctx.write(msg, promise);
        }

        @Override
        public void flush(final ChannelHandlerContext ctx) {
            observer.onFlush();
            ctx.flush();
        }
    }
}
