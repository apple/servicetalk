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
package io.servicetalk.transport.netty.internal;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.ssl.SslHandler;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Similar to {@link io.netty.handler.ssl.OptionalSslHandler}, but also adds support for SNI and optimizes it for
 * ServiceTalk usage patterns.
 */
public final class OptionalSslHandler extends io.netty.handler.codec.ByteToMessageDecoder {

    /**
     * The length of the ssl record header (in bytes)
     */
    private static final int SSL_RECORD_HEADER_LENGTH = 5;

    private final Supplier<ChannelHandler> sslHandler;

    OptionalSslHandler(final Supplier<ChannelHandler> sslHandler) {
        this.sslHandler = Objects.requireNonNull(sslHandler, "sslHandler");
    }

    @Override
    protected void decode(final ChannelHandlerContext context, final ByteBuf in, final List<Object> out) {
        if (in.readableBytes() < SSL_RECORD_HEADER_LENGTH) {
            return;
        }
        if (SslHandler.isEncrypted(in)) {
            context.pipeline().replace(this, null, sslHandler.get());
        } else {
            context.pipeline().remove(this);
            context.pipeline().fireUserEventTriggered(OptionalSslHandlerRemovedEvent.INSTANCE);
        }
    }

    public static final class OptionalSslHandlerRemovedEvent {
        static final OptionalSslHandlerRemovedEvent INSTANCE = new OptionalSslHandlerRemovedEvent();
        private OptionalSslHandlerRemovedEvent() {
            // singleton
        }
    }
}
