/*
 * Copyright © 2019-2023 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.client.api.TransportObserverConnectionFactoryFilter;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.context.api.ContextMap;
import io.servicetalk.http.api.BlockingHttpClient;
import io.servicetalk.http.api.FilterableStreamingHttpConnection;
import io.servicetalk.http.api.HttpConnectionContext;
import io.servicetalk.http.api.HttpProtocolVersion;
import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.ProxyConnectException;
import io.servicetalk.http.api.ProxyConnectResponseException;
import io.servicetalk.http.api.ReservedBlockingHttpConnection;
import io.servicetalk.test.resources.DefaultTestCerts;
import io.servicetalk.transport.api.ClientSslConfigBuilder;
import io.servicetalk.transport.api.ConnectionObserver;
import io.servicetalk.transport.api.ConnectionObserver.ProxyConnectObserver;
import io.servicetalk.transport.api.ConnectionObserver.SecurityHandshakeObserver;
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.api.RetryableException;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.api.ServerSslConfigBuilder;
import io.servicetalk.transport.api.TransportObserver;
import io.servicetalk.transport.netty.internal.ExecutionContextExtension;
import io.servicetalk.transport.netty.internal.NoopTransportObserver.NoopDataObserver;
import io.servicetalk.transport.netty.internal.NoopTransportObserver.NoopMultiplexedObserver;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.channels.ClosedChannelException;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nullable;
import javax.net.ssl.SSLHandshakeException;

import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.http.api.HttpContextKeys.HTTP_TARGET_ADDRESS_BEHIND_PROXY;
import static io.servicetalk.http.api.HttpHeaderNames.CONNECTION;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_LENGTH;
import static io.servicetalk.http.api.HttpHeaderNames.HOST;
import static io.servicetalk.http.api.HttpHeaderValues.CLOSE;
import static io.servicetalk.http.api.HttpResponseStatus.BAD_REQUEST;
import static io.servicetalk.http.api.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.api.HttpResponseStatus.PROXY_AUTHENTICATION_REQUIRED;
import static io.servicetalk.http.api.HttpSerializers.textSerializerUtf8;
import static io.servicetalk.http.netty.HttpProtocol.toConfigs;
import static io.servicetalk.test.resources.DefaultTestCerts.serverPemHostname;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class HttpsProxyTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(HttpsProxyTest.class);
    private static final String AUTH_TOKEN = "aGVsbG86d29ybGQ=";

    @RegisterExtension
    static final ExecutionContextExtension SERVER_CTX =
            ExecutionContextExtension.cached("server-io", "server-executor")
                    .setClassLevel(true);
    @RegisterExtension
    static final ExecutionContextExtension CLIENT_CTX =
            ExecutionContextExtension.cached("client-io", "client-executor")
                    .setClassLevel(true);

    private final ProxyTunnel proxyTunnel = new ProxyTunnel();
    private final AtomicReference<Object> targetAddress = new AtomicReference<>();

    private TransportObserver transportObserver;
    private ConnectionObserver connectionObserver;
    private ProxyConnectObserver proxyConnectObserver;
    private SecurityHandshakeObserver securityHandshakeObserver;
    private InOrder order;

    @Nullable
    private HostAndPort proxyAddress;
    @Nullable
    private ServerContext serverContext;
    @Nullable
    private HostAndPort serverAddress;
    @Nullable
    private BlockingHttpClient client;

    void setUp(List<HttpProtocol> protocols) throws Exception {
        setUp(protocols, false);
    }

    void setUp(List<HttpProtocol> protocols, boolean failHandshake) throws Exception {
        initMocks();
        proxyAddress = proxyTunnel.startProxy();
        startServer(protocols);
        createClient(protocols, failHandshake);
    }

    @AfterEach
    void tearDown() throws Exception {
        safeClose(client);
        safeClose(serverContext);
        safeClose(proxyTunnel);
    }

    static void safeClose(@Nullable AutoCloseable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (Exception e) {
                LOGGER.error("Unexpected exception while closing", e);
            }
        }
    }

    private void initMocks() {
        transportObserver = mock(TransportObserver.class, "transportObserver");
        connectionObserver = mock(ConnectionObserver.class, "connectionObserver");
        proxyConnectObserver = mock(ProxyConnectObserver.class, "proxyConnectObserver");
        securityHandshakeObserver = mock(SecurityHandshakeObserver.class, "securityHandshakeObserver");
        when(transportObserver.onNewConnection(any(), any())).thenReturn(connectionObserver);
        when(connectionObserver.onProxyConnect(any())).thenReturn(proxyConnectObserver);
        lenient().when(connectionObserver.onSecurityHandshake()).thenReturn(securityHandshakeObserver);
        lenient().when(connectionObserver.connectionEstablished(any())).thenReturn(NoopDataObserver.INSTANCE);
        lenient().when(connectionObserver.multiplexedConnectionEstablished(any()))
                .thenReturn(NoopMultiplexedObserver.INSTANCE);
        order = inOrder(transportObserver, connectionObserver, proxyConnectObserver, securityHandshakeObserver);
    }

    private void startServer(List<HttpProtocol> protocols) throws Exception {
        serverContext = BuilderUtils.newServerBuilder(SERVER_CTX)
                .sslConfig(new ServerSslConfigBuilder(DefaultTestCerts::loadServerPem,
                        DefaultTestCerts::loadServerKey).build())
                .protocols(toConfigs(protocols))
                .listenAndAwait((ctx, request, responseFactory) -> succeeded(responseFactory.ok()
                        .payloadBody("host: " + request.headers().get(HOST), textSerializerUtf8())));
        serverAddress = serverHostAndPort(serverContext);
    }

    private void createClient(List<HttpProtocol> protocols, boolean failHandshake) {
        assert serverContext != null && proxyAddress != null;
        client = BuilderUtils.newClientBuilder(serverContext, CLIENT_CTX)
                .proxyAddress(proxyAddress)
                .sslConfig(new ClientSslConfigBuilder(DefaultTestCerts::loadServerCAPem)
                        .peerHost(failHandshake ? "unknown" : serverPemHostname()).build())
                .protocols(toConfigs(protocols))
                .appendConnectionFactoryFilter(new TransportObserverConnectionFactoryFilter<>(transportObserver))
                .appendConnectionFactoryFilter(new TargetAddressCheckConnectionFactoryFilter(targetAddress, true))
                .buildBlocking();
    }

    @ParameterizedTest(name = "{displayName} [{index}] protocols={0}")
    @MethodSource("io.servicetalk.http.netty.HttpProtocol#allCombinations")
    void testClientRequest(List<HttpProtocol> protocols) throws Exception {
        setUp(protocols);
        assert client != null;
        assertResponse(client.request(client.get("/path")), protocols.get(0).version);
    }

    @ParameterizedTest(name = "{displayName} [{index}] protocols={0}")
    @MethodSource("io.servicetalk.http.netty.HttpProtocol#allCombinations")
    void testConnectionRequest(List<HttpProtocol> protocols) throws Exception {
        setUp(protocols);
        assert client != null;
        assert proxyAddress != null;
        HttpProtocolVersion expectedVersion = protocols.get(0).version;
        try (ReservedBlockingHttpConnection connection = client.reserveConnection(client.get("/"))) {
            HttpConnectionContext ctx = connection.connectionContext();
            assertThat(ctx.protocol(), is(expectedVersion));
            assertThat(ctx.sslConfig(), is(notNullValue()));
            assertThat(ctx.sslSession(), is(notNullValue()));
            assertThat(serverHostAndPort(ctx.remoteAddress()).port(), is(proxyAddress.port()));

            HttpRequest request = connection.get("/path");
            assertThat(request.version(), is(expectedVersion));
            assertResponse(connection.request(request), expectedVersion);
        }
        order.verify(connectionObserver).connectionClosed();
    }

    private void assertResponse(HttpResponse httpResponse, HttpProtocolVersion expectedVersion) {
        assertThat(httpResponse.status(), is(OK));
        assertThat(httpResponse.version(), is(expectedVersion));
        assertThat(proxyTunnel.connectCount(), is(1));
        assertThat(httpResponse.payloadBody().toString(US_ASCII), is("host: " + serverAddress));
        assertTargetAddress();

        verifyProxyConnectComplete();
        order.verify(connectionObserver).onSecurityHandshake();
        order.verify(securityHandshakeObserver).handshakeComplete(any());
        if (expectedVersion.major() > 1) {
            order.verify(connectionObserver).multiplexedConnectionEstablished(any());
        } else {
            order.verify(connectionObserver).connectionEstablished(any());
        }
        verifyNoMoreInteractions(transportObserver, proxyConnectObserver, securityHandshakeObserver);
    }

    @ParameterizedTest(name = "{displayName} [{index}] protocols={0}")
    @MethodSource("io.servicetalk.http.netty.HttpProtocol#allCombinations")
    void testProxyAuthRequired(List<HttpProtocol> protocols) throws Exception {
        setUp(protocols);
        proxyTunnel.basicAuthToken(AUTH_TOKEN);
        assert client != null;
        ProxyConnectResponseException e = assertThrows(ProxyConnectResponseException.class,
                () -> client.request(client.get("/path")));
        assertThat(e, is(not(instanceOf(RetryableException.class))));
        assertThat(e.response().status(), is(PROXY_AUTHENTICATION_REQUIRED));
        assertTargetAddress();
        verifyProxyConnectFailed(e);
    }

    @ParameterizedTest(name = "{displayName} [{index}] protocols={0}")
    @MethodSource("io.servicetalk.http.netty.HttpProtocol#allCombinations")
    void testBadProxyResponse(List<HttpProtocol> protocols) throws Exception {
        setUp(protocols);
        proxyTunnel.badResponseProxy();
        assert client != null;
        ProxyConnectResponseException e = assertThrows(ProxyConnectResponseException.class,
                () -> client.request(client.get("/path")));
        assertThat(e, is(instanceOf(RetryableException.class)));
        assertThat(e.response().status(), is(INTERNAL_SERVER_ERROR));
        assertTargetAddress();
        verifyProxyConnectFailed(e);
    }

    @ParameterizedTest(name = "{displayName} [{index}] protocols={0}")
    @MethodSource("io.servicetalk.http.netty.HttpProtocol#allCombinations")
    void testBadRequest(List<HttpProtocol> protocols) throws Exception {
        setUp(protocols);
        proxyTunnel.proxyRequestHandler((socket, host, port, protocol) -> {
            final OutputStream os = socket.getOutputStream();
            os.write((protocol + ' ' + BAD_REQUEST + "\r\n\r\n").getBytes(UTF_8));
            os.flush();
        });
        assert client != null;
        ProxyConnectResponseException e = assertThrows(ProxyConnectResponseException.class,
                () -> client.request(client.get("/path")));
        assertThat(e, is(not(instanceOf(RetryableException.class))));
        assertThat(e.response().status(), is(BAD_REQUEST));
        assertTargetAddress();
        verifyProxyConnectFailed(e);
    }

    @ParameterizedTest(name = "{displayName} [{index}] protocols={0}")
    @MethodSource("io.servicetalk.http.netty.HttpProtocol#allCombinations")
    void testProxyClosesConnection(List<HttpProtocol> protocols) throws Exception {
        setUp(protocols);
        proxyTunnel.proxyRequestHandler((socket, host, port, protocol) -> {
            socket.close();
        });
        assert client != null;
        ProxyConnectException e = assertThrows(ProxyConnectException.class,
                () -> client.request(client.get("/path")));
        assertThat(e, is(instanceOf(RetryableException.class)));
        assertThat(e.getCause(), is(anyOf(nullValue(), instanceOf(ClosedChannelException.class))));
        assertTargetAddress();
        verifyProxyConnectFailed(e);
    }

    @ParameterizedTest(name = "{displayName} [{index}] protocols={0}")
    @MethodSource("io.servicetalk.http.netty.HttpProtocol#allCombinations")
    void testProxyRespondsWithConnectionCloseHeader(List<HttpProtocol> protocols) throws Exception {
        setUp(protocols);
        proxyTunnel.proxyRequestHandler((socket, host, port, protocol) -> {
            final OutputStream os = socket.getOutputStream();
            os.write((protocol + ' ' + OK + "\r\n" +
                    CONNECTION + ": " + CLOSE + "\r\n" +
                    "\r\n").getBytes(UTF_8));
            os.flush();
        });
        assert client != null;
        ProxyConnectException e = assertThrows(ProxyConnectException.class,
                () -> client.request(client.get("/path")));
        assertThat(e, is(instanceOf(RetryableException.class)));
        assertThat(e.getCause(), is(instanceOf(ClosedChannelException.class)));
        assertTargetAddress();
        verifyProxyConnectComplete();
    }

    @ParameterizedTest(name = "{displayName} [{index}] protocols={0}")
    @MethodSource("io.servicetalk.http.netty.HttpProtocol#allCombinations")
    void testProxyRespondsAndClosesConnection(List<HttpProtocol> protocols) throws Exception {
        setUp(protocols);
        proxyTunnel.proxyRequestHandler((socket, host, port, protocol) -> {
            final OutputStream os = socket.getOutputStream();
            os.write((protocol + ' ' + OK + "\r\n\r\n").getBytes(UTF_8));
            os.flush();
            socket.close();
        });
        assert client != null;
        ProxyConnectException e = assertThrows(ProxyConnectException.class,
                () -> client.request(client.get("/path")));
        assertThat(e, is(instanceOf(RetryableException.class)));
        assertThat(e.getCause(), is(instanceOf(IOException.class)));
        assertTargetAddress();
        verifyProxyConnectComplete();
    }

    @ParameterizedTest(name = "{displayName} [{index}] protocols={0}")
    @MethodSource("io.servicetalk.http.netty.HttpProtocol#allCombinations")
    void testProxyRespondsWithPayloadBody(List<HttpProtocol> protocols) throws Exception {
        setUp(protocols);
        String content = "content";
        proxyTunnel.proxyRequestHandler((socket, host, port, protocol) -> {
            final OutputStream os = socket.getOutputStream();
            os.write((protocol + ' ' + OK + "\r\n" +
                    CONTENT_LENGTH + ": " + content.length() + "\r\n" +
                    "\r\n" + content).getBytes(UTF_8));
            os.flush();
        });
        assert client != null;
        ProxyConnectException e = assertThrows(ProxyConnectException.class,
                () -> client.request(client.get("/path")));
        assertThat(e, is(instanceOf(RetryableException.class)));
        assertTargetAddress();
        verifyProxyConnectComplete();
    }

    @ParameterizedTest(name = "{displayName} [{index}] protocols={0}")
    @MethodSource("io.servicetalk.http.netty.HttpProtocol#allCombinations")
    void testHandshakeFailed(List<HttpProtocol> protocols) throws Exception {
        setUp(protocols, true);
        assert client != null;
        SSLHandshakeException e = assertThrows(SSLHandshakeException.class,
                () -> client.request(client.get("/path")));
        assertThat(e, is(not(instanceOf(RetryableException.class))));
        assertTargetAddress();

        verifyProxyConnectComplete();
        order.verify(connectionObserver).onSecurityHandshake();
        order.verify(securityHandshakeObserver).handshakeFailed(e);
    }

    private void assertTargetAddress() {
        assertThat(targetAddress.get(), is(equalTo(serverAddress.toString())));
    }

    private void verifyProxyConnectFailed(Throwable cause) {
        order.verify(transportObserver).onNewConnection(any(), any());
        order.verify(connectionObserver).onTransportHandshakeComplete();
        order.verify(connectionObserver).onProxyConnect(any());
        order.verify(proxyConnectObserver).proxyConnectFailed(cause);
    }

    private void verifyProxyConnectComplete() {
        order.verify(transportObserver).onNewConnection(any(), any());
        order.verify(connectionObserver).onTransportHandshakeComplete();
        order.verify(connectionObserver).onProxyConnect(any());
        order.verify(proxyConnectObserver).proxyConnectComplete(any());
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
