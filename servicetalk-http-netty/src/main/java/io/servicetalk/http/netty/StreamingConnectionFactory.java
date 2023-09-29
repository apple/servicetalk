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
                        new TcpClientChannelInitializer(tcpConfig, connectionObserver, false),
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
            final String newPeerHost;
            final String newSniHostname;
            final String hostnameVerificationAlgorithm;
            if (sniHostname == null) {
                if (peerHost == null) {
                    newPeerHost = toHostAddress(inetAddress);
                    newSniHostname = hostnameVerificationAlgorithm = null;
                } else {
                    newPeerHost = peerHost + '-' + toHostAddress(inetAddress);
                    // We are overriding the peerHost to make it qualified with the resolved address. If sniHostname is
                    // not set and the hostnameVerificationAlgorithm is set, the SSLEngine will take the peerHost value
                    // for validation, which will fail to match now that we have changed the value.
                    if (isValidSniHostname(peerHost)) {
                        newSniHostname = peerHost;
                        hostnameVerificationAlgorithm = sslConfig.hostnameVerificationAlgorithm();
                    } else {
                        newSniHostname = hostnameVerificationAlgorithm = null;
                    }
                }
            } else {
                newPeerHost = sniHostname + '-' + toHostAddress(inetAddress);
                newSniHostname = sniHostname;
                hostnameVerificationAlgorithm = sslConfig.hostnameVerificationAlgorithm();
            }

            return config.withSslConfigPeerHost(newPeerHost, socketAddress.getPort(), newSniHostname,
                    hostnameVerificationAlgorithm);
        }
        return config;
    }

    private static String toHostAddress(final InetAddress address) {
        final String hostAddress = address.getHostAddress();
        // Replace colons with dots to satisfy SNIHostName validation
        return address instanceof Inet6Address ? hostAddress.replace(':', '.') : hostAddress;
    }

    private static boolean isValidSniHostname(String peerHost) {
        try {
            new SNIHostName(peerHost);
            return true;
        } catch (Throwable cause) {
            return false;
        }
    }
}
