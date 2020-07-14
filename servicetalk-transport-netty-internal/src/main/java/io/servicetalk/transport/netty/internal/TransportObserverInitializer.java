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
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;

import static io.servicetalk.transport.netty.internal.TransportObserverUtils.connectionObserver;

/**
 * A {@link ChannelInitializer} that registers a {@link ConnectionObserver} for all channels.
 */
public final class TransportObserverInitializer implements ChannelInitializer {

    public static final ChannelInitializer TRANSPORT_OBSERVER_INITIALIZER = new TransportObserverInitializer();

    private TransportObserverInitializer() {
        // Singleton
    }

    @Override
    public void init(final Channel channel) {
        final ConnectionObserver observer = connectionObserver(channel);
        if (observer != null) {
            channel.pipeline().addLast(new TransportObserverChannelHandler(observer));
        }
    }

    private static final class TransportObserverChannelHandler extends ChannelDuplexHandler {
        private final ConnectionObserver observer;

        TransportObserverChannelHandler(final ConnectionObserver observer) {
            this.observer = observer;
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
            observer.onDataRead(size);
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
            observer.onDataWrite(size);
        }

        @Override
        public void flush(final ChannelHandlerContext ctx) {
            observer.onFlush();
            ctx.flush();
        }
    }
}
