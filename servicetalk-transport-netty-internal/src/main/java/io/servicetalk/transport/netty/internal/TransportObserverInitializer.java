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
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;

import static io.servicetalk.transport.netty.internal.TransportObserverUtils.assignConnectionObserver;
import static io.servicetalk.transport.netty.internal.TransportObserverUtils.safeReport;
import static java.util.Objects.requireNonNull;

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
    public TransportObserverInitializer(final TransportObserver transportObserver,
                                        final boolean secure) {
        this.transportObserver = requireNonNull(transportObserver);
        this.secure = secure;
    }

    @Override
    public void init(final Channel channel) {
        final ConnectionObserver observer = safeReport(transportObserver::onNewConnection, transportObserver,
                "new connection");
        if (observer == null) {
            return;
        }
        assignConnectionObserver(channel, observer);
        final ChannelPipeline pipeline = channel.pipeline();
        pipeline.addLast(new TransportObserverHandler(observer, secure));
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
                reportDataRead(((ByteBuf) msg).readableBytes());
            } else if (msg instanceof ByteBufHolder) {
                reportDataRead(((ByteBufHolder) msg).content().readableBytes());
            }
            ctx.fireChannelRead(msg);
        }

        private void reportDataRead(final int size) {
            safeReport(() -> observer.onDataRead(size), observer, "data read");
        }

        @Override
        public void write(final ChannelHandlerContext ctx, final Object msg, final ChannelPromise promise) {
            if (msg instanceof ByteBuf) {
                reportDataWritten(((ByteBuf) msg).readableBytes());
            } else if (msg instanceof ByteBufHolder) {
                reportDataWritten(((ByteBufHolder) msg).content().readableBytes());
            }
            ctx.write(msg, promise);
        }

        private void reportDataWritten(final int size) {
            safeReport(() -> observer.onDataWrite(size), observer, "data write");
        }

        @Override
        public void flush(final ChannelHandlerContext ctx) {
            safeReport(observer::onFlush, observer, "flush");
            ctx.flush();
        }
    }
}
