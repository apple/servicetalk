/*
 * Copyright © 2020-2023 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.transport.api.ConnectionInfo;
import io.servicetalk.transport.api.ConnectionObserver;
import io.servicetalk.transport.api.ConnectionObserver.SecurityHandshakeObserver;
import io.servicetalk.transport.api.ExecutionContext;
import io.servicetalk.transport.api.SslConfig;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufHolder;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.kqueue.KQueue;

import java.net.SocketAddress;
import java.net.SocketOption;
import java.util.function.Function;
import javax.annotation.Nullable;
import javax.net.ssl.SSLSession;

import static io.netty.channel.ChannelOption.TCP_FASTOPEN_CONNECT;
import static io.servicetalk.transport.netty.internal.ChannelCloseUtils.channelError;
import static io.servicetalk.transport.netty.internal.SocketOptionUtils.getOption;
import static java.util.Objects.requireNonNull;

/**
 * A {@link ChannelInitializer} that registers a {@link ConnectionObserver} for all channels.
 */
public final class ConnectionObserverInitializer implements ChannelInitializer {

    private final ConnectionObserver observer;
    private final Function<Channel, ConnectionInfo> connectionInfoFactory;
    private final boolean client;
    @Nullable
    private final SslConfig sslConfig;

    /**
     * Creates a new instance.
     *
     * @param observer {@link ConnectionObserver} to report network events.
     * @param handshakeOnActive {@code true} if the observed connection is secure
     * @param client {@code true} if this initializer is used on the client-side
     * @deprecated Use {@link #ConnectionObserverInitializer(ConnectionObserver, Function, boolean, SslConfig)}
     * instead
     */
    @Deprecated // FIXME: 0.43 - remove deprecated ctor
    public ConnectionObserverInitializer(final ConnectionObserver observer,
                                         final boolean handshakeOnActive,
                                         final boolean client) {
        this(observer, PartialConnectionInfo::new, handshakeOnActive, client);
    }

    /**
     * Creates a new instance.
     *
     * @param observer {@link ConnectionObserver} to report network events
     * @param connectionInfoFactory {@link Function} that creates {@link ConnectionInfo} from the provided
     * {@link Channel} to report {@link ConnectionObserver#onTransportHandshakeComplete(ConnectionInfo)}
     * @param ignored ignored parameter.
     * @param client {@code true} if this initializer is used on the client-side
     * @deprecated Use {@link #ConnectionObserverInitializer(ConnectionObserver, Function, boolean, SslConfig)}
     * instead
     */
    @Deprecated // FIXME: 0.43 - remove deprecated ctor
    public ConnectionObserverInitializer(final ConnectionObserver observer,
                                         final Function<Channel, ConnectionInfo> connectionInfoFactory,
                                         final boolean ignored,
                                         final boolean client) {
        this(observer, connectionInfoFactory, client, null);
    }

    /**
     * Creates a new instance.
     *
     * @param observer {@link ConnectionObserver} to report network events
     * @param connectionInfoFactory {@link Function} that creates {@link ConnectionInfo} from the provided
     * {@link Channel} to report {@link ConnectionObserver#onTransportHandshakeComplete(ConnectionInfo)}
     * @param client {@code true} if this initializer is used on the client-side
     * @param sslConfig the {@link SslConfig} to supply to the observer on handshake.
     */
    public ConnectionObserverInitializer(final ConnectionObserver observer,
                                         final Function<Channel, ConnectionInfo> connectionInfoFactory,
                                         final boolean client,
                                         @Nullable final SslConfig sslConfig) {
        this.observer = requireNonNull(observer);
        this.connectionInfoFactory = requireNonNull(connectionInfoFactory);
        this.client = client;
        this.sslConfig = sslConfig;
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
        channel.pipeline().addLast(new ConnectionObserverHandler(observer, connectionInfoFactory,
                sslConfig != null, isFastOpen(channel), sslConfig));
    }

    private boolean isFastOpen(final Channel channel) {
        return client && sslConfig != null && Boolean.TRUE.equals(channel.config().getOption(TCP_FASTOPEN_CONNECT)) &&
                (Epoll.isTcpFastOpenClientSideAvailable() || KQueue.isTcpFastOpenClientSideAvailable());
    }

    static final class ConnectionObserverHandler extends ChannelDuplexHandler {

        private final ConnectionObserver observer;
        private final Function<Channel, ConnectionInfo> connectionInfoFactory;
        private final boolean handshakeOnActive;
        private boolean tcpHandshakeComplete;
        @Nullable
        private SecurityHandshakeObserver handshakeObserver;
        @Nullable
        private final SslConfig sslConfig;

        ConnectionObserverHandler(final ConnectionObserver observer,
                                  final Function<Channel, ConnectionInfo> connectionInfoFactory,
                                  final boolean handshakeOnActive,
                                  final boolean fastOpen,
                                  @Nullable final SslConfig sslConfig) {
            this.observer = observer;
            this.connectionInfoFactory = connectionInfoFactory;
            this.handshakeOnActive = handshakeOnActive;
            this.sslConfig = sslConfig;
            if (fastOpen) {
                reportSecurityHandshakeStarting(sslConfig);
            }
        }

        @Override
        public void handlerAdded(final ChannelHandlerContext ctx) {
            final Channel channel = ctx.channel();
            if (channel.isActive()) {
                reportTcpHandshakeComplete(channel);
                if (handshakeOnActive) {
                    reportSecurityHandshakeStarting(sslConfig);
                }
            }
        }

        @Override
        public void channelActive(final ChannelHandlerContext ctx) {
            reportTcpHandshakeComplete(ctx.channel());
            if (handshakeOnActive) {
                reportSecurityHandshakeStarting(sslConfig);
            }
            ctx.fireChannelActive();
        }

        private void reportTcpHandshakeComplete(final Channel channel) {
            if (!tcpHandshakeComplete) {
                tcpHandshakeComplete = true;
                observer.onTransportHandshakeComplete(connectionInfoFactory.apply(channel));
            }
        }

        void reportSecurityHandshakeStarting(@Nullable final SslConfig sslConfig) {
            assert sslConfig != null;
            if (handshakeObserver == null) {
                handshakeObserver = observer.onSecurityHandshake(sslConfig);
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

        @Override
        public void channelWritabilityChanged(final ChannelHandlerContext ctx) {
            observer.connectionWritabilityChanged(ctx.channel().isWritable());
            ctx.fireChannelWritabilityChanged();
        }
    }

    /**
     * Implementation of {@link ConnectionInfo} that will be used only if users use our deprecated internal API.
     * It's not used for regular users or if users of internal API migrate to recommended constructors.
     */
    // FIXME: 0.43 - remove this class after deprecated public constructors removed
    private static final class PartialConnectionInfo implements ConnectionInfo {

        private static final Protocol TCP_PROTOCOL = () -> "TCP";

        private final Channel channel;

        PartialConnectionInfo(final Channel channel) {
            this.channel = channel;
        }

        @Override
        public SocketAddress localAddress() {
            return channel.localAddress();
        }

        @Override
        public SocketAddress remoteAddress() {
            return channel.remoteAddress();
        }

        @Override
        @SuppressWarnings("DataFlowIssue")
        public ExecutionContext<?> executionContext() {
            return null;
        }

        @Nullable
        @Override
        public SslConfig sslConfig() {
            return null;
        }

        @Nullable
        @Override
        public SSLSession sslSession() {
            return null;
        }

        @Nullable
        @Override
        public <T> T socketOption(final SocketOption<T> option) {
            return getOption(option, channel.config(), 0L);
        }

        @Override
        public Protocol protocol() {
            return TCP_PROTOCOL;
        }

        @Override
        public String toString() {
            return channel.toString();
        }
    }
}
