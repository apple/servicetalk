/*
 * Copyright © 2018-2023 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.HttpExecutionContext;
import io.servicetalk.tcp.netty.internal.ReadOnlyTcpClientConfig;
import io.servicetalk.tcp.netty.internal.TcpClientChannelInitializer;
import io.servicetalk.tcp.netty.internal.TcpConnector;
import io.servicetalk.transport.api.ClientSslConfig;
import io.servicetalk.transport.api.ConnectionObserver;
import io.servicetalk.transport.api.TransportObserver;
import io.servicetalk.transport.netty.internal.ChannelInitializer;
import io.servicetalk.transport.netty.internal.CloseHandler;
import io.servicetalk.transport.netty.internal.DefaultNettyConnection;
import io.servicetalk.transport.netty.internal.NettyConnection;

import io.netty.channel.Channel;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import javax.net.ssl.SNIHostName;

import static io.servicetalk.buffer.netty.BufferUtils.getByteBufAllocator;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_1;
import static io.servicetalk.http.netty.HeaderUtils.OBJ_EXPECT_CONTINUE;
import static io.servicetalk.http.netty.HttpDebugUtils.showPipeline;
import static io.servicetalk.http.netty.HttpExecutionContextUtils.channelExecutionContext;
import static io.servicetalk.transport.netty.internal.CloseHandler.forPipelinedRequestResponse;
import static io.servicetalk.utils.internal.NetworkUtils.isValidIpV4Address;
import static io.servicetalk.utils.internal.NetworkUtils.isValidIpV6Address;
import static java.util.Objects.requireNonNull;

final class StreamingConnectionFactory {
    private StreamingConnectionFactory() {
        // No instances.
    }

    static <ResolvedAddress> Single<? extends NettyConnection<Object, Object>> buildStreaming(
            final HttpExecutionContext executionContext, final ResolvedAddress resolvedAddress,
            final ReadOnlyHttpClientConfig roConfig, final TransportObserver observer) {
        final ReadOnlyTcpClientConfig tcpConfig = withSslConfigPeerHost(resolvedAddress, roConfig.tcpConfig());
        final H1ProtocolConfig h1Config = roConfig.h1Config();
        assert h1Config != null;
        assert !roConfig.hasProxy() || tcpConfig.sslContext() == null :
                "This factory can be used only for non-proxied connections or for non-secure proxies";
        // We disable auto read so we can handle stuff in the ConnectionFilter before we accept any content.
        return TcpConnector.connect(null, resolvedAddress, tcpConfig, false, executionContext,
                (channel, connectionObserver) -> createConnection(channel, executionContext, h1Config, tcpConfig,
                        new TcpClientChannelInitializer(tcpConfig, connectionObserver, executionContext, false),
                        connectionObserver),
                observer);
    }

    static Single<? extends NettyConnection<Object, Object>> createConnection(final Channel channel,
            final HttpExecutionContext builderExecutionContext, final H1ProtocolConfig h1Config,
            final ReadOnlyTcpClientConfig tcpConfig, final ChannelInitializer initializer,
            final ConnectionObserver connectionObserver) {
        final CloseHandler closeHandler = forPipelinedRequestResponse(true, channel.config());
        return showPipeline(DefaultNettyConnection.initChannel(channel,
                channelExecutionContext(channel, builderExecutionContext), closeHandler,
                tcpConfig.flushStrategy(), tcpConfig.idleTimeoutMs(), tcpConfig.sslConfig(),
                initializer.andThen(new HttpClientChannelInitializer(
                        getByteBufAllocator(builderExecutionContext.bufferAllocator()), h1Config, closeHandler)),
                HTTP_1_1, connectionObserver, true, OBJ_EXPECT_CONTINUE),
                HTTP_1_1, channel);
    }

    static ReadOnlyTcpClientConfig withSslConfigPeerHost(Object resolvedRemoteAddress,
                                                         ReadOnlyTcpClientConfig config) {
        requireNonNull(resolvedRemoteAddress);
        requireNonNull(config);
        ClientSslConfig sslConfig = config.sslConfig();
        if (sslConfig != null && resolvedRemoteAddress instanceof InetSocketAddress) {
            // Get the InetAddress for hostname+IP, port will be appended elsewhere to the SSLSession cache key.
            final InetSocketAddress socketAddress = (InetSocketAddress) resolvedRemoteAddress;
            final InetAddress inetAddress = socketAddress.getAddress();
            final String sniHostname = sslConfig.sniHostname();
            final String peerHost = sslConfig.peerHost();
            final String origHostnameVerificationAlgorithm = sslConfig.hostnameVerificationAlgorithm();
            final String newPeerHost;
            final String newSniHostname;
            final String hostnameVerificationAlgorithm;
            // We want to bundle the resolved IP into peerHost so the SSLSession cache key differentiates per-IP and
            // hits the right cached session when a hostname resolves to multiple IPs. JSSE's endpoint identification
            // would normally validate the cert against peerHost, which would fail once we mangle it. The trick is
            // that JSSE prefers the SNI name (from SSLParameters.serverNames) over peerHost for verification — so
            // when we have a name that's valid as an SNI hostname, we smuggle the real name through SNI and let
            // peerHost carry a cache-friendly value.
            if (sniHostname == null) {
                if (peerHost == null) {
                    newPeerHost = toHostAddress(inetAddress);
                    newSniHostname = null;
                    // No hostname info at all — reset the algorithm to "" so Netty 4.2's "HTTPS" default doesn't
                    // attempt (and fail) verification against the IP literal we just stuffed into peerHost.
                    hostnameVerificationAlgorithm = "";
                } else {
                    // SNIHostName rejects trailing-dot FQDNs; strip so we can still smuggle absolute names via SNI.
                    final String peerHostForSni = stripTrailingDot(peerHost);
                    if (isValidSniHostname(peerHostForSni)) {
                        // Smuggle peerHost through SNI for verification; peerHost is free to be mangled for cache.
                        newPeerHost = toHostAndIpBundle(peerHostForSni, inetAddress);
                        newSniHostname = peerHostForSni;
                        hostnameVerificationAlgorithm = origHostnameVerificationAlgorithm;
                    } else if (origHostnameVerificationAlgorithm != null &&
                            !origHostnameVerificationAlgorithm.isEmpty()) {
                        // Verification required and we can't smuggle the hostname via SNI, so we can't widen
                        // peerHost to make the cache happy.
                        newPeerHost = peerHost;
                        newSniHostname = null;
                        hostnameVerificationAlgorithm = origHostnameVerificationAlgorithm;
                    } else {
                        // No verification requested — mangle freely, nothing to validate against.
                        newPeerHost = toHostAndIpBundle(peerHost, inetAddress);
                        newSniHostname = null;
                        hostnameVerificationAlgorithm = "";
                    }
                }
            } else {
                // SNI is set; verification reads it, peerHost is free to be mangled.
                newPeerHost = toHostAndIpBundle(sniHostname, inetAddress);
                newSniHostname = sniHostname;
                hostnameVerificationAlgorithm = origHostnameVerificationAlgorithm;
            }

            return config.withSslConfigPeerHost(newPeerHost, socketAddress.getPort(), newSniHostname,
                    hostnameVerificationAlgorithm);
        }
        return config;
    }

    /**
     * Bundling the host with address helps to increase probability for SSLSession resumption cache hits when the
     * hostname resolves to multiple IPs.
     */
    static String toHostAndIpBundle(final String hostname, final InetAddress address) {
        if (address.isLoopbackAddress() || isValidIpV4Address(hostname) || isValidIpV6Address(hostname)) {
            // No need to alter the host in this case
            return hostname;
        }
        return hostname + '-' + toHostAddress(address);
    }

    static String toHostAddress(final InetAddress address) {
        final String hostAddress = address.getHostAddress();
        // Replace colons with dots, and percentages with hyphens, to satisfy SNIHostName validation
        return address instanceof Inet6Address ? hostAddress.replace(':', '.').replace('%', '-') : hostAddress;
    }

    static boolean isValidSniHostname(String peerHost) {
        try {
            new SNIHostName(peerHost);
            return true;
        } catch (Throwable cause) {
            return false;
        }
    }

    private static String stripTrailingDot(String hostname) {
        final int len = hostname.length();
        return len > 1 && hostname.charAt(len - 1) == '.' ? hostname.substring(0, len - 1) : hostname;
    }
}
