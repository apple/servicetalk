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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufHolder;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;

import static io.servicetalk.transport.netty.internal.ChannelCloseUtils.channelError;
import static java.util.Objects.requireNonNull;

/**
 * A {@link ChannelInitializer} that registers a {@link ConnectionObserver} for all channels.
 */
public final class TransportObserverInitializer implements ChannelInitializer {

    private final ObservabilityProvider provider;
    private final boolean secure;

    /**
     * Creates a new instance.
     *
     * @param provider {@link ObservabilityProvider} that helps to provide observability features
     * @param secure {@code true} if the observed connection is secure
     */
    public TransportObserverInitializer(final ObservabilityProvider provider, final boolean secure) {
        this.provider = requireNonNull(provider);
        this.secure = secure;
    }

    @Override
    public void init(final Channel channel) {
        final ConnectionObserver observer = provider.onNewConnection();
        channel.closeFuture().addListener((ChannelFutureListener) future -> {
            Throwable t = channelError(channel);
            if (t == null) {
                observer.connectionClosed();
            } else {
                observer.connectionClosed(t);
            }
        });
        channel.pipeline().addLast(new TransportObserverHandler(observer, provider, secure));
    }

    private static final class TransportObserverHandler extends ChannelDuplexHandler {
        private final ConnectionObserver observer;
        private final ObservabilityProvider provider;
        private final boolean secure;
        private boolean handshakeStartNotified;

        TransportObserverHandler(final ConnectionObserver observer, final ObservabilityProvider provider,
                                 final boolean secure) {
            this.observer = observer;
            this.provider = provider;
            this.secure = secure;
        }

        @Override
        public void handlerAdded(final ChannelHandlerContext ctx) {
            if (secure && ctx.channel().isActive()) {
                reportSecurityHandshakeStarting();
            }
        }

        @Override
        public void channelActive(final ChannelHandlerContext ctx) {
            if (secure) {
                reportSecurityHandshakeStarting();
            }
            ctx.fireChannelActive();
        }

        void reportSecurityHandshakeStarting() {
            if (!handshakeStartNotified) {
                handshakeStartNotified = true;
                // Use provider instead of ConnectionObserver to let other layers access SecurityHandshakeObserver
                provider.onSecurityHandshake();
            }
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
