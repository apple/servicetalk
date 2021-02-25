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
package io.servicetalk.transport.netty.internal;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.handler.ssl.SniHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.Mapping;

import javax.net.ssl.SSLEngine;

import static io.servicetalk.transport.netty.internal.CopyByteBufHandlerChannelInitializer.POOLED_ALLOCATOR;
import static java.util.Objects.requireNonNull;

/**
 * SNI {@link ChannelInitializer} for servers.
 */
public final class SniServerChannelInitializer implements ChannelInitializer {
    private final Mapping<String, SslContext> sniMapping;

    /**
     * Create a new instance.
     * @param sniMapping to use for SNI configuration.
     */
    public SniServerChannelInitializer(final Mapping<String, SslContext> sniMapping) {
        this.sniMapping = requireNonNull(sniMapping);
    }

    @Override
    public void init(final Channel channel) {
        channel.pipeline().addLast(new SniHandlerWithPooledAllocator(sniMapping));
    }

    /**
     * Overrides the {@link ByteBufAllocator} used by {@link SslHandler} when it needs to copy data to direct memory if
     * required by {@link SSLEngine}. {@link SslHandler} releases allocated direct {@link ByteBuf}s after processing.
     */
    private static final class SniHandlerWithPooledAllocator extends SniHandler {
        SniHandlerWithPooledAllocator(final Mapping<String, SslContext> mapping) {
            super(mapping);
        }

        @Override
        protected SslHandler newSslHandler(final SslContext context, final ByteBufAllocator ignore) {
            return super.newSslHandler(context, POOLED_ALLOCATOR);
        }
    }
}
