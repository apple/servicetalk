/*
 * Copyright © 2021 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.http.netty;

import io.servicetalk.concurrent.SingleSource;
import io.servicetalk.transport.netty.internal.ChannelInitializer;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.ssl.SniCompletionEvent;

import java.nio.channels.ClosedChannelException;
import javax.annotation.Nullable;

import static io.servicetalk.transport.netty.internal.ChannelCloseUtils.assignConnectionError;

final class SniCompleteChannelSingle extends ChannelInitSingle<SniCompletionEvent> {
    private final boolean forceRead;

    SniCompleteChannelSingle(final Channel channel, final ChannelInitializer channelInitializer,
                             final boolean forceRead) {
        super(channel, channelInitializer);
        this.forceRead = forceRead;
    }

    @Override
    protected ChannelHandler newChannelHandler(final Subscriber<? super SniCompletionEvent> subscriber) {
        return new SniCompleteChannelHandler(subscriber, forceRead);
    }

    private static final class SniCompleteChannelHandler extends ChannelInboundHandlerAdapter {
        @Nullable
        private Subscriber<? super SniCompletionEvent> subscriber;
        private final boolean forceRead;

        SniCompleteChannelHandler(Subscriber<? super SniCompletionEvent> subscriber,
                                  final boolean forceRead) {
            this.subscriber = subscriber;
            this.forceRead = forceRead;
        }

        @Override
        public void handlerAdded(final ChannelHandlerContext ctx) throws Exception {
            super.handlerAdded(ctx);
            if (forceRead) {
                // Force a read to get the SSL handshake started. We initialize pipeline before
                // SslHandshakeCompletionEvent will complete, therefore, no data will be propagated before we finish
                // initialization.
                ctx.read();
            }
        }

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
            if (evt instanceof SniCompletionEvent && subscriber != null) {
                ctx.pipeline().remove(this);
                Subscriber<? super SniCompletionEvent> subscriberCopy = subscriber;
                subscriber = null;
                subscriberCopy.onSuccess((SniCompletionEvent) evt);
            }
            ctx.fireUserEventTriggered(evt);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            if (subscriber != null) {
                Exception e = new ClosedChannelException();
                assignConnectionError(ctx.channel(), e);
                final SingleSource.Subscriber<? super SniCompletionEvent> subscriberCopy = subscriber;
                subscriber = null;
                subscriberCopy.onError(e);
            } else {
                // Propagate exception in the pipeline if subscriber is already complete
                ctx.fireExceptionCaught(cause);
                ctx.close();
            }
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            if (subscriber != null) {
                Exception e = new ClosedChannelException();
                assignConnectionError(ctx.channel(), e);
                final SingleSource.Subscriber<? super SniCompletionEvent> subscriberCopy = subscriber;
                subscriber = null;
                subscriberCopy.onError(e);
            } else {
                ctx.fireChannelInactive();
            }
        }
    }
}
