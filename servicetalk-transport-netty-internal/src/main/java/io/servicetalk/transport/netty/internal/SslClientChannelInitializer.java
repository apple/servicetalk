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

import javax.annotation.Nullable;
import javax.net.ssl.SSLParameters;

import static io.servicetalk.transport.netty.internal.CopyByteBufHandlerChannelInitializer.POOLED_ALLOCATOR;
import static io.servicetalk.transport.netty.internal.SslUtils.newHandler;
import static java.util.Objects.requireNonNull;

/**
 * SSL {@link ChannelInitializer} for clients.
 */
public class SslClientChannelInitializer implements ChannelInitializer {
    private final String peerHost;
    private final int peerPort;
    @Nullable
    private final String hostnameVerificationAlgorithm;
    @Nullable
    private final String sniHostname;
    private final SslContext sslContext;
    private final boolean deferSslHandler;

    /**
     * New instance.
     * @param sslContext to use for configuring SSL.
     * @param peerHost the non-authoritative name of the peer, will be used for host name verification (if enabled).
     * @param peerPort the non-authoritative port of the peer.
     * @param hostnameVerificationAlgorithm see {@link SSLParameters#setEndpointIdentificationAlgorithm(String)}.
     * If this is {@code null} or empty then you will be vulnerable to a MITM attack.
     * @param sniHostname enable the <a href="https://tools.ietf.org/html/rfc6066#section-3">SNI</a> TLS extension with
     * this value as the {@code host_name}.
     * @param deferSslHandler {@code true} to wrap the {@link SslHandler} in a {@link DeferSslHandler}.
     */
    public SslClientChannelInitializer(SslContext sslContext, String peerHost, int peerPort,
                                       @Nullable String hostnameVerificationAlgorithm, @Nullable String sniHostname,
                                       final boolean deferSslHandler) {
        this.sslContext = requireNonNull(sslContext);
        this.peerHost = requireNonNull(peerHost);
        this.peerPort = peerPort;
        this.hostnameVerificationAlgorithm = hostnameVerificationAlgorithm;
        this.sniHostname = sniHostname;
        this.deferSslHandler = deferSslHandler;
    }

    @Override
    public void init(Channel channel) {
        final SslHandler sslHandler = newHandler(sslContext, POOLED_ALLOCATOR, peerHost, peerPort,
                hostnameVerificationAlgorithm, sniHostname);
        channel.pipeline().addLast(deferSslHandler ? new DeferSslHandler(channel, sslHandler) : sslHandler);
    }
}
