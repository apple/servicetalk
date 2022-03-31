/*
 * Copyright © 2019, 2021-2022 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.client.api.ConnectionFactory;
import io.servicetalk.client.api.ConnectionFactoryFilter;
import io.servicetalk.client.api.DelegatingConnectionFactory;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.context.api.ContextMap;
import io.servicetalk.http.api.BlockingHttpClient;
import io.servicetalk.http.api.FilterableStreamingHttpConnection;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.test.resources.DefaultTestCerts;
import io.servicetalk.transport.api.ClientSslConfigBuilder;
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.api.IoExecutor;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.api.ServerSslConfigBuilder;
import io.servicetalk.transport.api.TransportObserver;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.http.api.HttpContextKeys.HTTP_TARGET_ADDRESS_BEHIND_PROXY;
import static io.servicetalk.http.api.HttpHeaderNames.HOST;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.api.HttpSerializers.textSerializerUtf8;
import static io.servicetalk.test.resources.DefaultTestCerts.serverPemHostname;
import static io.servicetalk.transport.netty.NettyIoExecutors.createIoExecutor;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HttpsProxyTest {

    private final ProxyTunnel proxyTunnel = new ProxyTunnel();
    private final AtomicReference<Object> targetAddress = new AtomicReference<>();

    @Nullable
    private HostAndPort proxyAddress;
    @Nullable
    private IoExecutor serverIoExecutor;
    @Nullable
    private ServerContext serverContext;
    @Nullable
    private HostAndPort serverAddress;
    @Nullable
    private BlockingHttpClient client;

    @BeforeEach
    void setUp() throws Exception {
        proxyAddress = proxyTunnel.startProxy();
        startServer();
        createClient();
    }

    @AfterEach
    void tearDown() throws Exception {
        try {
            safeClose(client);
            safeClose(serverContext);
            safeClose(proxyTunnel);
        } finally {
            if (serverIoExecutor != null) {
                serverIoExecutor.closeAsync().toFuture().get();
            }
        }
    }

    static void safeClose(@Nullable AutoCloseable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    void startServer() throws Exception {
        serverContext = HttpServers.forAddress(localAddress(0))
                .ioExecutor(serverIoExecutor = createIoExecutor("server-io-executor"))
                .sslConfig(new ServerSslConfigBuilder(DefaultTestCerts::loadServerPem,
                        DefaultTestCerts::loadServerKey).build())
                .listenAndAwait((ctx, request, responseFactory) -> succeeded(responseFactory.ok()
                        .payloadBody("host: " + request.headers().get(HOST), textSerializerUtf8())));
        serverAddress = serverHostAndPort(serverContext);
    }

    void createClient() {
        assert serverAddress != null && proxyAddress != null;
        client = HttpClients
                .forSingleAddress(serverAddress)
                .proxyAddress(proxyAddress)
                .sslConfig(new ClientSslConfigBuilder(DefaultTestCerts::loadServerCAPem)
                        .peerHost(serverPemHostname()).build())
                .appendConnectionFactoryFilter(new TargetAddressCheckConnectionFactoryFilter(targetAddress, true))
                .buildBlocking();
    }

    @Test
    void testRequest() throws Exception {
        assert client != null;
        final HttpResponse httpResponse = client.request(client.get("/path"));
        assertThat(httpResponse.status(), is(OK));
        assertThat(proxyTunnel.connectCount(), is(1));
        assertThat(httpResponse.payloadBody().toString(US_ASCII), is("host: " + serverAddress));
        assertThat(targetAddress.get(), is(equalTo(serverAddress.toString())));
    }

    @Test
    void testBadProxyResponse() {
        proxyTunnel.badResponseProxy();
        assert client != null;
        assertThrows(ProxyResponseException.class, () -> client.request(client.get("/path")));
        assertThat(targetAddress.get(), is(equalTo(serverAddress.toString())));
    }

    static final class TargetAddressCheckConnectionFactoryFilter
            implements ConnectionFactoryFilter<InetSocketAddress, FilterableStreamingHttpConnection> {

        private final AtomicReference<Object> targetAddress;
        private final boolean secure;

        TargetAddressCheckConnectionFactoryFilter(AtomicReference<Object> targetAddress, boolean secure) {
            this.targetAddress = targetAddress;
            this.secure = secure;
        }

        @Override
        public ConnectionFactory<InetSocketAddress, FilterableStreamingHttpConnection> create(
                ConnectionFactory<InetSocketAddress, FilterableStreamingHttpConnection> original) {
            return new DelegatingConnectionFactory<InetSocketAddress, FilterableStreamingHttpConnection>(original) {
                @Override
                public Single<FilterableStreamingHttpConnection> newConnection(InetSocketAddress address,
                        @Nullable ContextMap context, @Nullable TransportObserver observer) {
                    assert context != null;
                    targetAddress.set(context.get(HTTP_TARGET_ADDRESS_BEHIND_PROXY));
                    return delegate().newConnection(address, context, observer)
                            .whenOnSuccess(c -> assertThat(c.connectionContext().sslConfig() != null, is(secure)));
                }
            };
        }
    }
}
