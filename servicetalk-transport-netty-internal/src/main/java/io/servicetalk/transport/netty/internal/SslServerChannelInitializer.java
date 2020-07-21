/*
 * Copyright © 2018-2019 Apple Inc. and the ServiceTalk project authors
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
import io.netty.util.DomainNameMapping;

import javax.annotation.Nullable;
import javax.net.ssl.SSLEngine;

import static io.servicetalk.transport.netty.internal.CopyByteBufHandlerChannelInitializer.POOLED_ALLOCATOR;
import static io.servicetalk.transport.netty.internal.SslUtils.newHandler;
import static java.util.Objects.requireNonNull;

/**
 * SSL {@link ChannelInitializer} for servers.
 */
public class SslServerChannelInitializer implements ChannelInitializer {

    @Nullable
    private final DomainNameMapping<SslContext> domainNameMapping;
    @Nullable
    private final SslContext sslContext;

    /**
     * New instance.
     * @param sslContext to use for configuring SSL.
     */
    public SslServerChannelInitializer(SslContext sslContext) {
        this.sslContext = requireNonNull(sslContext);
        domainNameMapping = null;
    }

    /**
     * New instance.
     * @param domainNameMapping to use for configuring SSL.
     */
    public SslServerChannelInitializer(DomainNameMapping<SslContext> domainNameMapping) {
        this.domainNameMapping = requireNonNull(domainNameMapping);
        sslContext = null;
    }

    @Override
    public void init(Channel channel) {
        if (sslContext != null) {
            SslHandler sslHandler = newHandler(sslContext, POOLED_ALLOCATOR);
            channel.pipeline().addLast(sslHandler);
        } else {
            assert domainNameMapping != null;
            channel.pipeline().addLast(new SniHandlerWithPooledAllocator(domainNameMapping));
        }
    }

    /**
     * Overrides the {@link ByteBufAllocator} used by {@link SslHandler} when it needs to copy data to direct memory if
     * required by {@link SSLEngine}. {@link SslHandler} releases allocated direct {@link ByteBuf}s after processing.
     */
    private static final class SniHandlerWithPooledAllocator extends SniHandler {

        SniHandlerWithPooledAllocator(final DomainNameMapping<SslContext> domainNameMapping) {
            super(domainNameMapping);
        }

        @Override
        protected SslHandler newSslHandler(final SslContext context, final ByteBufAllocator ignore) {
            return super.newSslHandler(context, POOLED_ALLOCATOR);
        }
    }
}
