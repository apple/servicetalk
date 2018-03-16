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
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandler;
import io.servicetalk.transport.api.ConnectionContext;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

import javax.annotation.Nullable;

import static io.servicetalk.transport.netty.internal.SslUtils.newHandler;
import static java.util.Objects.requireNonNull;

/**
 * SSL {@link ChannelInitializer} for clients.
 */
public class SslClientChannelInitializer extends AbstractSslChannelInitializer {

    @Nullable
    private final String hostnameVerificationAlgorithm;
    private final SslContext sslContext;

    /**
     * New instance.
     * @param sslContext to use for configuring SSL.
     * @param hostnameVerificationAlgorithm hostname verification algorithm.
     */
    public SslClientChannelInitializer(SslContext sslContext, @Nullable String hostnameVerificationAlgorithm) {
        this.sslContext = requireNonNull(sslContext);
        this.hostnameVerificationAlgorithm = hostnameVerificationAlgorithm;
    }

    @Nullable
    @Override
    protected SslHandler addNettySslHandler(Channel channel, ConnectionContext context) {
        SocketAddress remote = channel.remoteAddress();
        if (remote instanceof InetSocketAddress) {
            // Add ssl handler into the pipeline
            //noinspection ConstantConditions
            SslHandler sslHandler = newHandler(sslContext, channel.alloc(), (InetSocketAddress) remote, hostnameVerificationAlgorithm);
            channel.pipeline().addFirst(sslHandler);
            return sslHandler;
        }
        return null;
    }
}
