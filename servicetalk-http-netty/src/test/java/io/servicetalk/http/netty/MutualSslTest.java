/*
 * Copyright © 2020-2021 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.http.api.BlockingHttpClient;
import io.servicetalk.http.api.HttpResponseStatus;
import io.servicetalk.http.api.HttpServerBuilder;
import io.servicetalk.http.api.SingleAddressHttpClientBuilder;
import io.servicetalk.test.resources.DefaultTestCerts;
import io.servicetalk.transport.api.ClientSslConfigBuilder;
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.api.ServerSslConfigBuilder;
import io.servicetalk.transport.api.SslProvider;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.net.InetSocketAddress;
import java.net.SocketOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import static io.servicetalk.http.netty.TcpFastOpenTest.assumeTcpFastOpen;
import static io.servicetalk.http.netty.TcpFastOpenTest.clientTcpFastOpenOptions;
import static io.servicetalk.http.netty.TcpFastOpenTest.serverTcpFastOpenOptions;
import static io.servicetalk.test.resources.DefaultTestCerts.serverPemHostname;
import static io.servicetalk.transport.api.SslClientAuthMode.REQUIRE;
import static io.servicetalk.transport.api.SslProvider.JDK;
import static io.servicetalk.transport.api.SslProvider.OPENSSL;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyMap;
import static org.junit.jupiter.api.Assertions.assertEquals;

class MutualSslTest {
    private static final SslProvider[] SSL_PROVIDERS = {JDK, OPENSSL};
    @SuppressWarnings("rawtypes")
    private static final List<Map<SocketOption, Object>> SERVER_LISTEN_OPTIONS =
            asList(emptyMap(), serverTcpFastOpenOptions());
    @SuppressWarnings("rawtypes")
    private static final List<Map<SocketOption, Object>> CLIENT_OPTIONS =
            asList(emptyMap(), clientTcpFastOpenOptions());

    @SuppressWarnings({"rawtypes", "unused"})
    private static Collection<Arguments> params() {
        List<Arguments> params = new ArrayList<>();
        for (SslProvider serverSslProvider : SSL_PROVIDERS) {
            for (SslProvider clientSslProvider : SSL_PROVIDERS) {
                for (Map<SocketOption, Object> serverListenOptions : SERVER_LISTEN_OPTIONS) {
                    for (Map<SocketOption, Object> clientOptions : CLIENT_OPTIONS) {
                        params.add(Arguments.of(serverSslProvider, clientSslProvider,
                                serverListenOptions, clientOptions));
                    }
                }
            }
        }
        return params;
    }

    @ParameterizedTest
    @MethodSource("params")
    void mutualSsl(SslProvider serverSslProvider,
                   SslProvider clientSslProvider,
                   @SuppressWarnings("rawtypes") Map<SocketOption, Object> serverListenOptions,
                   @SuppressWarnings("rawtypes") Map<SocketOption, Object> clientOptions) throws Exception {
        assumeTcpFastOpen(clientOptions);

        HttpServerBuilder serverBuilder = HttpServers.forAddress(localAddress(0))
                .sslConfig(new ServerSslConfigBuilder(
                        DefaultTestCerts::loadServerPem, DefaultTestCerts::loadServerKey)
                        .trustManager(DefaultTestCerts::loadClientCAPem)
                        .clientAuthMode(REQUIRE).provider(serverSslProvider).build());
        for (@SuppressWarnings("rawtypes") Entry<SocketOption, Object> entry : serverListenOptions.entrySet()) {
            @SuppressWarnings("unchecked")
            SocketOption<Object> option = entry.getKey();
            serverBuilder.listenSocketOption(option, entry.getValue());
        }
        try (ServerContext serverContext = serverBuilder.listenBlockingAndAwait(
                (ctx, request, responseFactory) -> responseFactory.ok());
             BlockingHttpClient client = newClientBuilder(serverContext, clientOptions)
                     .sslConfig(new ClientSslConfigBuilder(DefaultTestCerts::loadServerCAPem)
                             .provider(clientSslProvider).peerHost(serverPemHostname())
                             .keyManager(DefaultTestCerts::loadClientPem, DefaultTestCerts::loadClientKey).build())
                     .buildBlocking()) {
            assertEquals(HttpResponseStatus.OK, client.request(client.get("/")).status());
        }
    }

    private static SingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> newClientBuilder(
            ServerContext serverContext, @SuppressWarnings("rawtypes") Map<SocketOption, Object> clientOptions) {
        SingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> builder =
                HttpClients.forSingleAddress(serverHostAndPort(serverContext));
        for (@SuppressWarnings("rawtypes") Entry<SocketOption, Object> entry : clientOptions.entrySet()) {
            @SuppressWarnings("unchecked")
            SocketOption<Object> option = entry.getKey();
            builder.socketOption(option, entry.getValue());
        }
        return builder;
    }
}
