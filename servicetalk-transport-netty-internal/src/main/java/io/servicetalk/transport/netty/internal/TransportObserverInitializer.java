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
import io.servicetalk.transport.api.TransportObserver;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufHolder;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;

import static io.servicetalk.transport.netty.internal.TransportObserverUtils.assignConnectionObserver;

/**
 * A {@link ChannelInitializer} that registers a {@link ConnectionObserver} for all channels.
 */
public final class TransportObserverInitializer implements ChannelInitializer {

    private final TransportObserver transportObserver;
    private final boolean secure;

    /**
     * Creates a new instance.
     *
     * @param transportObserver {@link TransportObserver} to initialize for the channel
     * @param secure {@code true} if the observed connection is secure
     */
    public TransportObserverInitializer(final TransportObserver transportObserver, final boolean secure) {
        this.transportObserver = new CatchAllTransportObserver(transportObserver);
        this.secure = secure;
    }

    @Override
    public void init(final Channel channel) {
        final ConnectionObserver observer = transportObserver.onNewConnection();
        assignConnectionObserver(channel, observer);
        channel.pipeline().addLast(new TransportObserverHandler(observer, secure));
    }

    private static final class TransportObserverHandler extends ChannelDuplexHandler {
        private final ConnectionObserver observer;
        private final boolean secure;
        private boolean handshakeStartNotified;

        TransportObserverHandler(final ConnectionObserver observer, final boolean secure) {
            this.observer = observer;
            this.secure = secure;
        }

        @Override
        public void handlerAdded(final ChannelHandlerContext ctx) {
            if (secure && ctx.channel().isActive()) {
                reportSecurityHandshakeStarting(ctx.channel());
            }
        }

        @Override
        public void channelActive(final ChannelHandlerContext ctx) {
            if (secure) {
                reportSecurityHandshakeStarting(ctx.channel());
            }
            ctx.fireChannelActive();
        }

        void reportSecurityHandshakeStarting(final Channel channel) {
            if (!handshakeStartNotified) {
                handshakeStartNotified = true;
                TransportObserverUtils.reportSecurityHandshakeStarting(channel);
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
