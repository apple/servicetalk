/*
 * Copyright © 2024 Apple Inc. and the ServiceTalk project authors
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

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.ssl.SslHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import javax.annotation.Nullable;

final class OptionalSslChannelSingle extends ChannelInitSingle<Boolean> {

    OptionalSslChannelSingle(final Channel channel) {
        super(channel, NoopChannelInitializer.INSTANCE);
    }

    @Override
    protected ChannelHandler newChannelHandler(final Subscriber<? super Boolean> subscriber) {
        return new OptionalSslHandler(subscriber);
    }

    private static final class OptionalSslHandler extends ByteToMessageDecoder {

        private static final Logger LOGGER = LoggerFactory.getLogger(OptionalSslHandler.class);

        /**
         * the length of the ssl record header (in bytes)
         */
        private static final int SSL_RECORD_HEADER_LENGTH = 5;

        @Nullable
        SingleSource.Subscriber<? super Boolean> subscriber;

        OptionalSslHandler(final SingleSource.Subscriber<? super Boolean> subscriber) {
            this.subscriber = subscriber;
        }

        @Override
        public void handlerAdded(final ChannelHandlerContext ctx) throws Exception {
            if (ctx.channel().isActive()) {
                ctx.read(); // we need to force a read to detect SSL yes/no
            }
            super.handlerAdded(ctx);
        }

        @Override
        public void channelActive(final ChannelHandlerContext ctx) throws Exception {
            ctx.read(); // we need to force a read to detect SSL yes/no
            ctx.fireChannelActive();
        }

        @Override
        protected void decode(final ChannelHandlerContext ctx, final ByteBuf in, final List<Object> out) {
            if (in.readableBytes() < SSL_RECORD_HEADER_LENGTH || subscriber == null) {
                return;
            }
            boolean isEncrypted = SslHandler.isEncrypted(in);
            LOGGER.debug("{} Detected TLS for this connection: {}", ctx.channel(), isEncrypted);
            final SingleSource.Subscriber<? super Boolean> subscriberCopy = subscriber;
            subscriber = null;
            subscriberCopy.onSuccess(isEncrypted);
            ctx.pipeline().remove(this);
        }
    }
}
