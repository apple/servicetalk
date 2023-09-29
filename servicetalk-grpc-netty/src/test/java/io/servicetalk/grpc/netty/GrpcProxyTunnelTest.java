/*
 * Copyright © 2023 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.grpc.netty;

import io.servicetalk.concurrent.api.Single;
import io.servicetalk.context.api.ContextMap.Key;
import io.servicetalk.grpc.api.DefaultGrpcClientMetadata;
import io.servicetalk.grpc.api.GrpcClientMetadata;
import io.servicetalk.grpc.api.GrpcStatusException;
import io.servicetalk.http.api.StreamingHttpConnectionFilter;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.netty.ProxyResponseException;
import io.servicetalk.http.netty.ProxyTunnel;
import io.servicetalk.test.resources.DefaultTestCerts;
import io.servicetalk.transport.api.ClientSslConfigBuilder;
import io.servicetalk.transport.api.ConnectionInfo;
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.api.ServerSslConfigBuilder;

import io.grpc.examples.helloworld.Greeter;
import io.grpc.examples.helloworld.Greeter.BlockingGreeterClient;
import io.grpc.examples.helloworld.HelloReply;
import io.grpc.examples.helloworld.HelloRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;

import static io.servicetalk.context.api.ContextMap.Key.newKey;
import static io.servicetalk.grpc.api.GrpcStatusCode.UNKNOWN;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_2_0;
import static io.servicetalk.http.api.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static io.servicetalk.http.api.HttpResponseStatus.PROXY_AUTHENTICATION_REQUIRED;
import static io.servicetalk.test.resources.DefaultTestCerts.serverPemHostname;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GrpcProxyTunnelTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(GrpcProxyTunnelTest.class);
    private static final String AUTH_TOKEN = "aGVsbG86d29ybGQ=";
    private static final String GREETING_PREFIX = "Hello ";
    private static final Key<ConnectionInfo> CONNECTION_INFO_KEY =
            newKey("CONNECTION_INFO_KEY", ConnectionInfo.class);

    private final ProxyTunnel proxyTunnel;
    private final HostAndPort proxyAddress;
    private final ServerContext serverContext;
    private final BlockingGreeterClient client;

    GrpcProxyTunnelTest() throws Exception {
        proxyTunnel = new ProxyTunnel();
        proxyAddress = proxyTunnel.startProxy();
        serverContext = GrpcServers.forAddress(localAddress(0))
                .initializeHttp(httpBuilder -> httpBuilder
                        .sslConfig(new ServerSslConfigBuilder(DefaultTestCerts::loadServerPem,
                                DefaultTestCerts::loadServerKey).build()))
                .listenAndAwait((Greeter.BlockingGreeterService) (ctx, request) ->
                        HelloReply.newBuilder().setMessage(GREETING_PREFIX + request.getName()).build());
        client = GrpcClients.forAddress(serverHostAndPort(serverContext))
                .initializeHttp(httpBuilder -> httpBuilder.proxyAddress(proxyAddress)
                        .sslConfig(new ClientSslConfigBuilder(DefaultTestCerts::loadServerCAPem)
                                .peerHost(serverPemHostname()).build())
                        .appendConnectionFilter(connection -> new StreamingHttpConnectionFilter(connection) {
                            @Override
                            public Single<StreamingHttpResponse> request(final StreamingHttpRequest request) {
                                return delegate().request(request)
                                        .whenOnSuccess(response -> response.context()
                                                .put(CONNECTION_INFO_KEY, connection.connectionContext()));
                            }
                        }))
                .buildBlocking(new Greeter.ClientFactory());
    }

    @AfterEach
    void tearDown() throws Exception {
        safeClose(client);
        safeClose(serverContext);
        safeClose(proxyTunnel);
    }

    private static void safeClose(AutoCloseable closeable) {
        try {
            closeable.close();
        } catch (Exception e) {
            LOGGER.error("Unexpected exception while closing", e);
        }
    }

    @Test
    void testRequest() throws Exception {
        String name = "foo";
        GrpcClientMetadata metaData = new DefaultGrpcClientMetadata();
        HelloReply reply = client.sayHello(metaData, HelloRequest.newBuilder().setName(name).build());
        assertThat(reply.getMessage(), is(GREETING_PREFIX + name));
        ConnectionInfo connectionInfo = metaData.responseContext().get(CONNECTION_INFO_KEY);
        assertThat(connectionInfo, is(notNullValue()));
        assertThat(connectionInfo.protocol(), is(HTTP_2_0));
        assertThat(connectionInfo.sslConfig(), is(notNullValue()));
        assertThat(connectionInfo.sslSession(), is(notNullValue()));
        assertThat(((InetSocketAddress) connectionInfo.remoteAddress()).getPort(), is(proxyAddress.port()));
        assertThat(proxyTunnel.connectCount(), is(1));
    }

    @Test
    void testProxyAuthRequired() throws Exception {
        proxyTunnel.basicAuthToken(AUTH_TOKEN);
        GrpcStatusException e = assertThrows(GrpcStatusException.class,
                () -> client.sayHello(HelloRequest.newBuilder().setName("foo").build()));
        assertThat(e.status().code(), is(UNKNOWN));
        Throwable cause = e.getCause();
        assertThat(cause, is(instanceOf(ProxyResponseException.class)));
        assertThat(((ProxyResponseException) cause).status(), is(PROXY_AUTHENTICATION_REQUIRED));
    }

    @Test
    void testBadProxyResponse() throws Exception {
        proxyTunnel.badResponseProxy();
        GrpcStatusException e = assertThrows(GrpcStatusException.class,
                () -> client.sayHello(HelloRequest.newBuilder().setName("foo").build()));
        assertThat(e.status().code(), is(UNKNOWN));
        Throwable cause = e.getCause();
        assertThat(cause, is(instanceOf(ProxyResponseException.class)));
        assertThat(((ProxyResponseException) cause).status(), is(INTERNAL_SERVER_ERROR));
    }
}
