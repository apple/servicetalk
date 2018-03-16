/*
 * Copyright © 2018 Apple Inc. and the ServiceTalk project authors
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

import io.netty.channel.Channel;
import io.netty.handler.ssl.SniHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.DomainNameMapping;
import io.servicetalk.transport.api.ConnectionContext;

import javax.annotation.Nullable;

/**
 * SSL {@link ChannelInitializer} for servers.
 */
public class SslServerChannelInitializer extends AbstractSslChannelInitializer {

    @Nullable
    private final DomainNameMapping<SslContext> domainNameMapping;
    @Nullable
    private final SslContext sslContext;

    /**
     * New instance.
     * @param sslContext to use for configuring SSL.
     */
    public SslServerChannelInitializer(SslContext sslContext) {
        this.sslContext = sslContext;
        domainNameMapping = null;
    }

    /**
     * New instance.
     * @param domainNameMapping to use for configuring SSL.
     */
    public SslServerChannelInitializer(DomainNameMapping<SslContext> domainNameMapping) {
        this.domainNameMapping = domainNameMapping;
        sslContext = null;
    }

    @Nullable
    @Override
    protected SslHandler addNettySslHandler(Channel channel, ConnectionContext context) {
        if (sslContext != null) {
            SslHandler sslHandler = SslUtils.newHandler(sslContext, channel.alloc());
            channel.pipeline().addLast(sslHandler);
            return sslHandler;
        } else if (domainNameMapping != null) {
            channel.pipeline().addLast(new SniHandler(domainNameMapping));
        }
        return null;
    }
}
