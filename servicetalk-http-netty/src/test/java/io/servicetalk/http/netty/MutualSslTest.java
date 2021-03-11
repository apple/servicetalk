/*
 * Copyright © 2020 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
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

import org.junit.Rule;
import org.junit.experimental.theories.DataPoints;
import org.junit.experimental.theories.FromDataPoints;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;

import java.net.InetSocketAddress;
import java.net.SocketOption;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

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
import static org.junit.Assert.assertEquals;

@RunWith(Theories.class)
public class MutualSslTest {
    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();
    @DataPoints("serverSslProvider")
    public static final SslProvider[] SERVER_PROVIDERS = {JDK, OPENSSL};
    @DataPoints("clientSslProvider")
    public static final SslProvider[] CLIENT_PROVIDERS = {JDK, OPENSSL};
    @DataPoints("serverListenOptions")
    @SuppressWarnings("rawtypes")
    public static final List<Map<SocketOption, Object>> SERVER_LISTEN_OPTIONS =
            asList(emptyMap(), serverTcpFastOpenOptions());
    @DataPoints("clientOptions")
    @SuppressWarnings("rawtypes")
    public static final List<Map<SocketOption, Object>> CLIENT_OPTIONS = asList(emptyMap(), clientTcpFastOpenOptions());

    @Theory
    public void mutualSsl(@FromDataPoints("serverSslProvider") SslProvider serverSslProvider,
                          @FromDataPoints("clientSslProvider") SslProvider clientSslProvider,
                          @SuppressWarnings("rawtypes")
                          @FromDataPoints("serverListenOptions") Map<SocketOption, Object> serverListenOptions,
                          @SuppressWarnings("rawtypes")
                          @FromDataPoints("serverListenOptions") Map<SocketOption, Object> clientOptions)
            throws Exception {
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

    private SingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> newClientBuilder(
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
