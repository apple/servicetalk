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
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpProtocolVersion;
import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.ProxyConfigBuilder;
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
import io.servicetalk.transport.api.SslConfig;
import io.servicetalk.transport.api.TransportObserver;
import io.servicetalk.transport.netty.internal.ExecutionContextExtension;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.channels.ClosedChannelException;
import java.security.KeyStore;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;

import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.http.api.HttpContextKeys.HTTP_TARGET_ADDRESS_BEHIND_PROXY;
import static io.servicetalk.http.api.HttpHeaderNames.CONNECTION;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_LENGTH;
import static io.servicetalk.http.api.HttpHeaderNames.HOST;
import static io.servicetalk.http.api.HttpHeaderNames.PROXY_AUTHORIZATION;
import static io.servicetalk.http.api.HttpHeaderValues.CLOSE;
import static io.servicetalk.http.api.HttpResponseStatus.BAD_REQUEST;
import static io.servicetalk.http.api.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.api.HttpResponseStatus.PROXY_AUTHENTICATION_REQUIRED;
import static io.servicetalk.http.api.HttpSerializers.textSerializerUtf8;
import static io.servicetalk.http.netty.HttpProtocol.toConfigs;
import static io.servicetalk.test.resources.DefaultTestCerts.serverPemHostname;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static io.servicetalk.transport.netty.internal.CloseUtils.safeClose;
import static java.net.InetAddress.getLoopbackAddress;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class HttpsProxyTest {

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
    private SecurityHandshakeObserver proxySecurityHandshakeObserver;
    private InOrder order;

    @Nullable
    private HostAndPort proxyAddress;
    @Nullable
    private ServerContext serverContext;
    @Nullable
    private HostAndPort serverAddress;
    @Nullable
    private BlockingHttpClient client;

    private static final char[] KEYSTORE_PASSWORD = "changeit".toCharArray();

    void setUp(List<HttpProtocol> protocols, boolean proxyTls) throws Exception {
        setUp(protocols, false, proxyTls);
    }

    void setUp(List<HttpProtocol> protocols, boolean failHandshake, boolean proxyTls) throws Exception {
        setUp(protocols, failHandshake, __ -> { /* noop */ }, proxyTls);
    }

    void setUp(List<HttpProtocol> protocols, boolean failHandshake,
               Consumer<HttpHeaders> connectRequestHeadersInitializer, boolean proxyTls) throws Exception {
        setUp(protocols, failHandshake, false, connectRequestHeadersInitializer, proxyTls);
    }

    void setUp(List<HttpProtocol> protocols, boolean failHandshake, boolean failProxyHandshake,
               Consumer<HttpHeaders> connectRequestHeadersInitializer, boolean proxyTls) throws Exception {
        initMocks();
        if (proxyTls) {
            proxyTunnel.sslContext(buildProxySslContext());
        }
        proxyAddress = proxyTunnel.startProxy();
        startServer(protocols);
        createClient(protocols, failHandshake, failProxyHandshake, connectRequestHeadersInitializer, proxyTls);
    }

    /**
     * Cross-product of {@link HttpProtocol#allCombinations()} with the proxy-TLS axis (plaintext vs TLS).
     * Used by every parameterized test in this class to exercise the existing CONNECT test surface under both
     * the legacy plaintext-proxy path and the new TLS-terminating proxy path.
     */
    static Stream<Arguments> protocolsAndProxyTls() {
        return HttpProtocol.allCombinations().stream()
                .flatMap(p -> Stream.of(Arguments.of(p, false), Arguments.of(p, true)));
    }

    private static SSLContext buildProxySslContext() throws Exception {
        // Reuse the standard test server cert for the proxy listener too. Two independent TLS sessions are still
        // produced — different handshakes, separate SSLSession instances — even when the leaf cert is shared.
        final KeyStore ks = KeyStore.getInstance("PKCS12");
        try (InputStream is = DefaultTestCerts.loadServerP12()) {
            ks.load(is, KEYSTORE_PASSWORD);
        }
        final KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, KEYSTORE_PASSWORD);
        final SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(kmf.getKeyManagers(), null, null);
        return ctx;
    }

    @AfterEach
    void tearDown() throws Exception {
        safeClose(client);
        safeClose(serverContext);
        safeClose(proxyTunnel);
    }

    private void initMocks() {
        transportObserver = mock(TransportObserver.class, "transportObserver");
        connectionObserver = mock(ConnectionObserver.class, "connectionObserver");
        proxyConnectObserver = mock(ProxyConnectObserver.class, "proxyConnectObserver");
        securityHandshakeObserver = mock(SecurityHandshakeObserver.class, "securityHandshakeObserver");
        proxySecurityHandshakeObserver =
                mock(SecurityHandshakeObserver.class, "proxySecurityHandshakeObserver");
        when(transportObserver.onNewConnection(any(), any())).thenReturn(connectionObserver);
        when(connectionObserver.onProxyConnect(any())).thenReturn(proxyConnectObserver);
        lenient().when(connectionObserver.onSecurityHandshake(any(SslConfig.class)))
                .thenReturn(securityHandshakeObserver);
        lenient().when(connectionObserver.onProxySecurityHandshake(any(SslConfig.class)))
                .thenReturn(proxySecurityHandshakeObserver);
        order = inOrder(transportObserver, connectionObserver, proxyConnectObserver,
                securityHandshakeObserver, proxySecurityHandshakeObserver);
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

    private void createClient(List<HttpProtocol> protocols, boolean failHandshake, boolean failProxyHandshake,
                              Consumer<HttpHeaders> connectRequestHeadersInitializer, boolean proxyTls) {
        assert serverContext != null && proxyAddress != null;
        final ProxyConfigBuilder<HostAndPort> proxyBuilder = new ProxyConfigBuilder<>(proxyAddress)
                .connectRequestHeadersInitializer(connectRequestHeadersInitializer);
        if (proxyTls) {
            // No explicit peerHost — let inference fill it in from the proxy address.  We test the explicit case via
            // testProxySslConfigExplicitPeerHostOverridesInference.
            // failProxyHandshake omits the test CA from the proxy trust store so the proxy cert is rejected.
            final ClientSslConfigBuilder proxySslConfig = failProxyHandshake ?
                    new ClientSslConfigBuilder() :
                    new ClientSslConfigBuilder(DefaultTestCerts::loadServerCAPem);
            proxyBuilder.sslConfig(proxySslConfig.build());
        }
        client = BuilderUtils.newClientBuilder(serverContext, CLIENT_CTX)
                .proxyConfig(proxyBuilder.build())
                .sslConfig(new ClientSslConfigBuilder(DefaultTestCerts::loadServerCAPem)
                        .peerHost(failHandshake ? "unknown" : serverPemHostname()).build())
                .protocols(toConfigs(protocols))
                .appendConnectionFactoryFilter(new TransportObserverConnectionFactoryFilter<>(transportObserver))
                .appendConnectionFactoryFilter(new TargetAddressCheckConnectionFactoryFilter(targetAddress, true))
                .buildBlocking();
    }

    @ParameterizedTest(name = "{displayName} [{index}] protocols={0} proxyTls={1}")
    @MethodSource("protocolsAndProxyTls")
    void testClientRequest(List<HttpProtocol> protocols, boolean proxyTls) throws Exception {
        setUp(protocols, proxyTls);
        assert client != null;
        assertResponse(client.request(client.get("/path")), protocols.get(0).version, proxyTls);
    }

    @ParameterizedTest(name = "{displayName} [{index}] protocols={0} proxyTls={1}")
    @MethodSource("protocolsAndProxyTls")
    void testConnectionRequest(List<HttpProtocol> protocols, boolean proxyTls) throws Exception {
        setUp(protocols, proxyTls);
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
            assertResponse(connection.request(request), expectedVersion, proxyTls);
        }
        order.verify(connectionObserver).connectionClosed();
    }

    private void assertResponse(HttpResponse httpResponse, HttpProtocolVersion expectedVersion, boolean proxyTls) {
        assertThat(httpResponse.status(), is(OK));
        assertThat(httpResponse.version(), is(expectedVersion));
        assertThat(proxyTunnel.connectCount(), is(1));
        assertThat(httpResponse.payloadBody().toString(US_ASCII), is("host: " + serverAddress));
        assertTargetAddress();

        verifyProxyConnectComplete(proxyTls);
        order.verify(connectionObserver).onSecurityHandshake(any(SslConfig.class));
        order.verify(securityHandshakeObserver).handshakeComplete(any());
        if (expectedVersion.major() > 1) {
            order.verify(connectionObserver).multiplexedConnectionEstablished(any());
        } else {
            order.verify(connectionObserver).connectionEstablished(any());
        }
        verifyNoMoreInteractions(transportObserver, proxyConnectObserver, securityHandshakeObserver);
    }

    @ParameterizedTest(name = "{displayName} [{index}] protocols={0} proxyTls={1}")
    @MethodSource("protocolsAndProxyTls")
    void testProxyAuthRequired(List<HttpProtocol> protocols, boolean proxyTls) throws Exception {
        setUp(protocols, proxyTls);
        proxyTunnel.basicAuthToken(AUTH_TOKEN);
        assert client != null;
        ProxyConnectResponseException e = assertThrows(ProxyConnectResponseException.class,
                () -> client.request(client.get("/path")));
        assertThat(e, is(not(instanceOf(RetryableException.class))));
        assertThat(e.response().status(), is(PROXY_AUTHENTICATION_REQUIRED));
        assertTargetAddress();
        verifyProxyConnectFailed(e, proxyTls);
    }

    @ParameterizedTest(name = "{displayName} [{index}] protocols={0} proxyTls={1}")
    @MethodSource("protocolsAndProxyTls")
    void testProxyAuthRequiredWithAuthInfo(List<HttpProtocol> protocols, boolean proxyTls) throws Exception {
        setUp(protocols, false, headers -> headers.set(PROXY_AUTHORIZATION, "basic " + AUTH_TOKEN), proxyTls);
        proxyTunnel.basicAuthToken(AUTH_TOKEN);
        assert client != null;
        assertResponse(client.request(client.get("/path")), protocols.get(0).version, proxyTls);
    }

    @Test
    void testConnectRequestHeadersReachProxyNotOrigin() throws Exception {
        // A header set via connectRequestHeadersInitializer must reach the proxy on the CONNECT request and
        // must NOT be forwarded to the origin. Structurally enforced by ServiceTalk (CONNECT and origin requests
        // carry separate HttpHeaders instances) but worth a guard test. One-off — the property is generic across
        // protocols and proxy-TLS modes so a single representative configuration is sufficient.
        final String forwardedFor = "X-Proxy-Forwarded-For";
        final String forwardedForValue = "edge-td-sre:testapp";
        initMocks();
        proxyAddress = proxyTunnel.startProxy();
        // Custom origin server that echoes whether it received the proxy-only header.
        serverContext = BuilderUtils.newServerBuilder(SERVER_CTX, HttpProtocol.HTTP_1)
                .sslConfig(new ServerSslConfigBuilder(DefaultTestCerts::loadServerPem,
                        DefaultTestCerts::loadServerKey).build())
                .listenAndAwait((ctx, request, responseFactory) -> succeeded(responseFactory.ok()
                        .payloadBody("forwardedFor=" + request.headers().get(forwardedFor),
                                textSerializerUtf8())));
        serverAddress = serverHostAndPort(serverContext);
        client = BuilderUtils.newClientBuilder(serverContext, CLIENT_CTX, HttpProtocol.HTTP_1)
                .proxyConfig(new ProxyConfigBuilder<>(proxyAddress)
                        .connectRequestHeadersInitializer(headers -> headers.set(forwardedFor, forwardedForValue))
                        .build())
                .sslConfig(new ClientSslConfigBuilder(DefaultTestCerts::loadServerCAPem)
                        .peerHost(serverPemHostname()).build())
                .buildBlocking();

        final HttpResponse response = client.request(client.get("/path"));
        assertThat(response.status(), is(OK));
        // Proxy received the header on the CONNECT request.
        assertThat(proxyTunnel.lastConnectHeaders(), is(notNullValue()));
        assertThat(proxyTunnel.lastConnectHeaders().get(forwardedFor), is(forwardedForValue));
        // Origin did NOT receive the header — the response payload echoes the value the origin saw.
        assertThat(response.payloadBody().toString(US_ASCII), is("forwardedFor=null"));
    }

    @ParameterizedTest(name = "{displayName} [{index}] setCorrectPeerHost={0}")
    @ValueSource(booleans = {true, false})
    void testProxySslConfigExplicitPeerHostOverridesInference(boolean setCorrectPeerHost) throws Exception {
        // Configure the proxy address with a hostname the cert would NOT validate (the loopback IP). We test
        // both setting the peerHost explicitly to make sure it can work and failing to set it to ensure that
        // by default we properly validate the certificates.
        initMocks();
        proxyTunnel.sslContext(buildProxySslContext());
        final HostAndPort bound = proxyTunnel.startProxy();
        final HostAndPort proxyAtIp = HostAndPort.of(getLoopbackAddress().getHostAddress(), bound.port());

        startServer(Collections.singletonList(HttpProtocol.HTTP_1));
        ClientSslConfigBuilder clientSslConfigBuilder = new ClientSslConfigBuilder(DefaultTestCerts::loadServerCAPem);
        if (setCorrectPeerHost) {
            clientSslConfigBuilder.peerHost(serverPemHostname());
        }

        // NB: deliberately call .sslConfig(...) BEFORE .proxyConfig(...) here. Most tests in this file use the
        // opposite order; this one proves the two builder methods don't have an order dependency on each other.
        client = BuilderUtils.newClientBuilder(serverContext, CLIENT_CTX, HttpProtocol.HTTP_1)
                .sslConfig(clientSslConfigBuilder.build())
                .proxyConfig(new ProxyConfigBuilder<>(proxyAtIp)
                        .sslConfig(new ClientSslConfigBuilder(DefaultTestCerts::loadServerCAPem)
                                .peerHost(serverPemHostname())
                                .build())
                        .build())
                .buildBlocking();

        if (setCorrectPeerHost) {
            assertThat(client.request(client.get("/path")).status(), is(OK));
        } else {
            assertThrows(SSLHandshakeException.class, () -> client.request(client.get("/path")));
        }
    }

    @ParameterizedTest(name = "{displayName} [{index}] protocols={0}")
    @MethodSource("io.servicetalk.http.netty.HttpProtocol#allCombinations")
    void testProxyTlsHandshakeFails(List<HttpProtocol> protocols) throws Exception {
        // The proxy uses TLS, but the client's proxy trust store omits the test CA, so the proxy certificate is
        // rejected during the proxy TLS handshake. Because the CONNECT request is only sent inside the established
        // tunnel, the handshake failure must abort the attempt before any CONNECT is initiated.
        initMocks();
        proxyTunnel.sslContext(buildProxySslContext());
        proxyAddress = proxyTunnel.startProxy();
        startServer(protocols);
        client = BuilderUtils.newClientBuilder(serverContext, CLIENT_CTX)
                .proxyConfig(new ProxyConfigBuilder<>(proxyAddress)
                        // Default (system) trust store does not trust the test CA -> proxy cert is rejected.
                        .sslConfig(new ClientSslConfigBuilder().build())
                        .build())
                .sslConfig(new ClientSslConfigBuilder(DefaultTestCerts::loadServerCAPem)
                        .peerHost(serverPemHostname()).build())
                .protocols(toConfigs(protocols))
                .appendConnectionFactoryFilter(new TransportObserverConnectionFactoryFilter<>(transportObserver))
                .buildBlocking();

        // Non-retryable SSL failure surfaced as-is (no LB retry, no transport-level wrapping).
        assertThrows(SSLException.class, () -> client.request(client.get("/path")));
        assertThat(proxyTunnel.connectCount(), is(0));

        // The proxy security handshake is reported and fails. atLeastOnce: the LB may re-resolve and retry.
        verify(connectionObserver, atLeastOnce()).onTransportHandshakeComplete(any());
        verify(connectionObserver, atLeastOnce()).onProxySecurityHandshake(any(SslConfig.class));
        verify(proxySecurityHandshakeObserver, atLeastOnce()).handshakeFailed(any(SSLException.class));
        verify(connectionObserver, atLeastOnce()).connectionClosed(any(Throwable.class));

        // The CONNECT exchange never starts, so neither the proxy-connect nor the application handshake fire.
        verify(connectionObserver, never()).onProxyConnect(any());
        verify(proxyConnectObserver, never()).proxyConnectComplete(any());
        verify(proxyConnectObserver, never()).proxyConnectFailed(any());
        verify(proxySecurityHandshakeObserver, never()).handshakeComplete(any());
        verify(connectionObserver, never()).onSecurityHandshake(any(SslConfig.class));
        verify(securityHandshakeObserver, never()).handshakeComplete(any());
        verify(securityHandshakeObserver, never()).handshakeFailed(any());
    }

    @ParameterizedTest(name = "{displayName} [{index}] protocols={0} proxyTls={1}")
    @MethodSource("protocolsAndProxyTls")
    void testHeadersInitializerThrows(List<HttpProtocol> protocols, boolean proxyTls) throws Exception {
        setUp(protocols, false, headers -> {
            throw DELIBERATE_EXCEPTION;
        }, proxyTls);
        proxyTunnel.basicAuthToken(AUTH_TOKEN);
        assert client != null;
        ProxyConnectException e = assertThrows(ProxyConnectException.class,
                () -> client.request(client.get("/path")));
        assertThat(e, is(not(instanceOf(RetryableException.class))));
        assertTargetAddress();
        order.verify(transportObserver).onNewConnection(any(), any());
        order.verify(connectionObserver).onTransportHandshakeComplete(any());
        verifyNoMoreInteractions(transportObserver, proxyConnectObserver, securityHandshakeObserver);
    }

    @ParameterizedTest(name = "{displayName} [{index}] protocols={0} proxyTls={1}")
    @MethodSource("protocolsAndProxyTls")
    void testBadProxyResponse(List<HttpProtocol> protocols, boolean proxyTls) throws Exception {
        setUp(protocols, proxyTls);
        proxyTunnel.badResponseProxy();
        assert client != null;
        ProxyConnectResponseException e = assertThrows(ProxyConnectResponseException.class,
                () -> client.request(client.get("/path")));
        assertThat(e, is(instanceOf(RetryableException.class)));
        assertThat(e.response().status(), is(INTERNAL_SERVER_ERROR));
        assertTargetAddress();
        verifyProxyConnectFailed(e, proxyTls);
    }

    @ParameterizedTest(name = "{displayName} [{index}] protocols={0} proxyTls={1}")
    @MethodSource("protocolsAndProxyTls")
    void testBadRequest(List<HttpProtocol> protocols, boolean proxyTls) throws Exception {
        setUp(protocols, proxyTls);
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
        verifyProxyConnectFailed(e, proxyTls);
    }

    @ParameterizedTest(name = "{displayName} [{index}] protocols={0} proxyTls={1}")
    @MethodSource("protocolsAndProxyTls")
    void testProxyClosesConnection(List<HttpProtocol> protocols, boolean proxyTls) throws Exception {
        setUp(protocols, proxyTls);
        proxyTunnel.proxyRequestHandler((socket, host, port, protocol) -> {
            socket.close();
        });
        assert client != null;
        ProxyConnectException e = assertThrows(ProxyConnectException.class,
                () -> client.request(client.get("/path")));
        assertThat(e, is(instanceOf(RetryableException.class)));
        assertThat(e.getCause(), is(nullValue()));
        assertTargetAddress();
        verifyProxyConnectFailed(e, proxyTls);
    }

    @ParameterizedTest(name = "{displayName} [{index}] protocols={0} proxyTls={1}")
    @MethodSource("protocolsAndProxyTls")
    void testProxyRespondsWithConnectionCloseHeader(List<HttpProtocol> protocols, boolean proxyTls) throws Exception {
        setUp(protocols, proxyTls);
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
        verifyProxyConnectComplete(proxyTls);
    }

    @ParameterizedTest(name = "{displayName} [{index}] protocols={0} proxyTls={1}")
    @MethodSource("protocolsAndProxyTls")
    void testProxyRespondsAndClosesConnection(List<HttpProtocol> protocols, boolean proxyTls) throws Exception {
        setUp(protocols, proxyTls);
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
        verifyProxyConnectComplete(proxyTls);
    }

    @ParameterizedTest(name = "{displayName} [{index}] protocols={0} proxyTls={1}")
    @MethodSource("protocolsAndProxyTls")
    void testProxyRespondsWithPayloadBody(List<HttpProtocol> protocols, boolean proxyTls) throws Exception {
        setUp(protocols, proxyTls);
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
        verifyProxyConnectComplete(proxyTls);
    }

    @ParameterizedTest(name = "{displayName} [{index}] protocols={0} proxyTls={1}")
    @MethodSource("protocolsAndProxyTls")
    void testHandshakeFailed(List<HttpProtocol> protocols, boolean proxyTls) throws Exception {
        setUp(protocols, true, proxyTls);
        assert client != null;
        SSLHandshakeException e = assertThrows(SSLHandshakeException.class,
                () -> client.request(client.get("/path")));
        assertThat(e, is(not(instanceOf(RetryableException.class))));
        assertTargetAddress();

        verifyProxyConnectComplete(proxyTls);
        order.verify(connectionObserver).onSecurityHandshake(any(SslConfig.class));
        order.verify(securityHandshakeObserver).handshakeFailed(e);
    }

    private void assertTargetAddress() {
        assertThat(targetAddress.get(), is(equalTo(serverAddress.toString())));
    }

    private void verifyProxyConnectFailed(Throwable cause, boolean proxyTls) {
        order.verify(transportObserver).onNewConnection(any(), any());
        order.verify(connectionObserver).onTransportHandshakeComplete(any());
        if (proxyTls) {
            order.verify(connectionObserver).onProxySecurityHandshake(any(SslConfig.class));
            order.verify(proxySecurityHandshakeObserver).handshakeComplete(any());
        }
        order.verify(connectionObserver).onProxyConnect(any());
        order.verify(proxyConnectObserver).proxyConnectFailed(cause);
    }

    private void verifyProxyConnectComplete(boolean proxyTls) {
        order.verify(transportObserver).onNewConnection(any(), any());
        order.verify(connectionObserver).onTransportHandshakeComplete(any());
        if (proxyTls) {
            order.verify(connectionObserver).onProxySecurityHandshake(any(SslConfig.class));
            order.verify(proxySecurityHandshakeObserver).handshakeComplete(any());
        }
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
