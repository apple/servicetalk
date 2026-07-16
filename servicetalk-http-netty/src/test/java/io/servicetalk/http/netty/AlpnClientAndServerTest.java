/*
 * Copyright © 2019, 2021, 2023 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.http.api.BlockingHttpClient;
import io.servicetalk.http.api.HttpProtocolConfig;
import io.servicetalk.http.api.HttpProtocolVersion;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.ReservedBlockingHttpConnection;
import io.servicetalk.http.api.StreamingHttpClient;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.test.resources.DefaultTestCerts;
import io.servicetalk.transport.api.ClientSslConfigBuilder;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.api.ServerSslConfigBuilder;
import io.servicetalk.transport.netty.internal.ExecutionContextExtension;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.channels.ClosedChannelException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.stream.Stream;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.api.VerificationTestUtils.assertThrows;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.api.HttpSerializers.textSerializerUtf8;
import static io.servicetalk.http.netty.AlpnIds.HTTP_1_1;
import static io.servicetalk.http.netty.AlpnIds.HTTP_2;
import static io.servicetalk.http.netty.BuilderUtils.newClientBuilder;
import static io.servicetalk.http.netty.BuilderUtils.newServerBuilder;
import static io.servicetalk.test.resources.DefaultTestCerts.serverPemHostname;
import static io.servicetalk.transport.api.SslProvider.OPENSSL;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class AlpnClientAndServerTest {

    @RegisterExtension
    static final ExecutionContextExtension SERVER_CTX =
            ExecutionContextExtension.cached("server-io", "server-executor")
                    .setClassLevel(true);
    @RegisterExtension
    static final ExecutionContextExtension CLIENT_CTX =
            ExecutionContextExtension.cached("client-io", "client-executor")
                    .setClassLevel(true);

    private static final String PAYLOAD_BODY = "Hello World!";

    private ServerContext serverContext;
    private BlockingHttpClient client;
    @Nullable
    private HttpProtocolVersion expectedProtocol;
    private BlockingQueue<HttpServiceContext> serviceContext;
    private BlockingQueue<HttpProtocolVersion> requestVersion;
    private BlockingQueue<Integer> requestPayloadSize;
    private void setUp(List<String> serverSideProtocols,
                       List<String> clientSideProtocols,
                       @Nullable HttpProtocolVersion expectedProtocol,
                       boolean acceptInsecureConnections) throws Exception {
        serverContext = startServer(serverSideProtocols, acceptInsecureConnections);
        client = startClient(serverContext, clientSideProtocols);
        this.expectedProtocol = expectedProtocol;
    }

    @BeforeEach
    void beforeEach() {
        serviceContext = new LinkedBlockingDeque<>();
        requestVersion = new LinkedBlockingDeque<>();
        requestPayloadSize = new LinkedBlockingDeque<>();
    }

    @SuppressWarnings("unused")
    private static Stream<Arguments> arguments() {
        final List<Arguments> arguments = new ArrayList<>();
        for (boolean acceptInsecureConnections : asList(true, false)) {
            arguments.addAll(Arrays.asList(
                    Arguments.of(asList(HTTP_2, HTTP_1_1), asList(HTTP_2, HTTP_1_1),
                            HttpProtocolVersion.HTTP_2_0, null, null, acceptInsecureConnections),
                    Arguments.of(asList(HTTP_2, HTTP_1_1), asList(HTTP_1_1, HTTP_2),
                            HttpProtocolVersion.HTTP_2_0, null, null, acceptInsecureConnections),
                    Arguments.of(asList(HTTP_2, HTTP_1_1), singletonList(HTTP_2),
                            HttpProtocolVersion.HTTP_2_0, null, null, acceptInsecureConnections),
                    Arguments.of(asList(HTTP_2, HTTP_1_1), singletonList(HTTP_1_1),
                            HttpProtocolVersion.HTTP_1_1, null, null, acceptInsecureConnections),

                    Arguments.of(asList(HTTP_1_1, HTTP_2), asList(HTTP_2, HTTP_1_1),
                            HttpProtocolVersion.HTTP_1_1, null, null, acceptInsecureConnections),
                    Arguments.of(asList(HTTP_1_1, HTTP_2), asList(HTTP_1_1, HTTP_2),
                            HttpProtocolVersion.HTTP_1_1, null, null, acceptInsecureConnections),
                    Arguments.of(asList(HTTP_1_1, HTTP_2), singletonList(HTTP_2),
                            HttpProtocolVersion.HTTP_2_0, null, null, acceptInsecureConnections),
                    Arguments.of(asList(HTTP_1_1, HTTP_2), singletonList(HTTP_1_1),
                            HttpProtocolVersion.HTTP_1_1, null, null, acceptInsecureConnections),

                    Arguments.of(singletonList(HTTP_2), asList(HTTP_2, HTTP_1_1),
                            HttpProtocolVersion.HTTP_2_0, null, null, acceptInsecureConnections),
                    Arguments.of(singletonList(HTTP_2), asList(HTTP_1_1, HTTP_2),
                            HttpProtocolVersion.HTTP_2_0, null, null, acceptInsecureConnections),
                    Arguments.of(singletonList(HTTP_2), singletonList(HTTP_2),
                            HttpProtocolVersion.HTTP_2_0, null, null, acceptInsecureConnections),
                    Arguments.of(singletonList(HTTP_2), singletonList(HTTP_1_1),
                            null, ClosedChannelException.class, null, acceptInsecureConnections),

                    Arguments.of(singletonList(HTTP_1_1), asList(HTTP_2, HTTP_1_1),
                            HttpProtocolVersion.HTTP_1_1, null, null, acceptInsecureConnections),
                    Arguments.of(singletonList(HTTP_1_1), asList(HTTP_1_1, HTTP_2),
                            HttpProtocolVersion.HTTP_1_1, null, null, acceptInsecureConnections),
                    Arguments.of(singletonList(HTTP_1_1), singletonList(HTTP_2),
                            null, ClosedChannelException.class, null, acceptInsecureConnections),
                    Arguments.of(singletonList(HTTP_1_1), singletonList(HTTP_1_1),
                            HttpProtocolVersion.HTTP_1_1, null, null, acceptInsecureConnections)
            ));
        }
        return arguments.stream();
    }

    private ServerContext startServer(List<String> supportedProtocols, boolean acceptInsecureConnections)
            throws Exception {
        return newServerBuilder(SERVER_CTX)
                .protocols(toProtocolConfigs(supportedProtocols))
                .sslConfig(new ServerSslConfigBuilder(DefaultTestCerts::loadServerPem,
                        DefaultTestCerts::loadServerKey).provider(OPENSSL).build(), acceptInsecureConnections)
                .listenBlocking((ctx, request, responseFactory) -> {
                    serviceContext.put(ctx);
                    requestVersion.put(request.version());
                    requestPayloadSize.put(request.payloadBody().readableBytes());
                    return responseFactory.ok().payloadBody(PAYLOAD_BODY, textSerializerUtf8());
                })
                .toFuture().get();
    }

    private static BlockingHttpClient startClient(ServerContext serverContext, List<String> supportedProtocols) {
        return newClientBuilder(serverContext, CLIENT_CTX)
                .protocols(toProtocolConfigs(supportedProtocols))
                .sslConfig(new ClientSslConfigBuilder(DefaultTestCerts::loadServerCAPem)
                        .peerHost(serverPemHostname()).provider(OPENSSL).build())
                .buildBlocking();
    }

    private static HttpProtocolConfig[] toProtocolConfigs(List<String> supportedProtocols) {
        return supportedProtocols.stream()
                .map(id -> {
                    switch (id) {
                        case HTTP_1_1:
                            return HttpProtocol.HTTP_1.config;
                        case HTTP_2:
                            return HttpProtocol.HTTP_2.config;
                        default:
                            throw new IllegalArgumentException("Unsupported protocol: " + id);
                    }
                }).toArray(HttpProtocolConfig[]::new);
    }

    @AfterEach
    void tearDown() throws Exception {
        try {
            client.close();
        } finally {
            serverContext.close();
        }
    }

    @ParameterizedTest(name = "serverSideProtocols={0}, clientSideProtocols={1}," +
            " expectedProtocol={2}, expectedExceptionType={3}, optionalExceptionWrapperType={4}" +
            " acceptInsecureConnections={5}")
    @MethodSource("arguments")
    void testAlpnConnection(List<String> serverSideProtocols,
                            List<String> clientSideProtocols,
                            @Nullable HttpProtocolVersion expectedProtocol,
                            @Nullable Class<? extends Throwable> expectedExceptionType,
                            @Nullable Class<? extends Throwable> optionalExceptionWrapperType,
                            boolean acceptInsecureConnections) throws Exception {
        setUp(serverSideProtocols, clientSideProtocols, expectedProtocol, acceptInsecureConnections);
        if (expectedExceptionType != null) {
            assertThrows(expectedExceptionType, optionalExceptionWrapperType, () -> client.request(client.get("/")));
            return;
        }

        try (ReservedBlockingHttpConnection connection = client.reserveConnection(client.get("/"))) {
            assertThat(connection.connectionContext().protocol(), is(expectedProtocol));
            assertThat(connection.connectionContext().sslConfig(), is(notNullValue()));
            assertThat(connection.connectionContext().sslSession(), is(notNullValue()));

            assertResponseAndServiceContext(connection.request(client.get("/")));
            // When using ALPN the request factory selection is deferred until after the connection is established, so
            // the protocol on the requests may be "http/1.x" but we may actually be speaking http/2. In either case
            // keep-alive should be enabled, the connection shouldn't be closed, and subsequent requests should succeed.
            assertResponseAndServiceContext(connection.request(client.get("/")));
        }
    }

    @ParameterizedTest(name = "serverSideProtocols={0}, clientSideProtocols={1}," +
            " expectedProtocol={2}, expectedExceptionType={3}, optionalExceptionWrapperType={4}," +
            " acceptInsecureConnections={5}")
    @MethodSource("arguments")
    void testAlpnClient(List<String> serverSideProtocols,
                        List<String> clientSideProtocols,
                        @Nullable HttpProtocolVersion expectedProtocol,
                        @Nullable Class<? extends Throwable> expectedExceptionType,
                        @Nullable Class<? extends Throwable> optionalExceptionWrapperType,
                        boolean acceptInsecureConnections) throws Exception {
        setUp(serverSideProtocols, clientSideProtocols, expectedProtocol, acceptInsecureConnections);
        if (expectedExceptionType != null) {
            assertThrows(expectedExceptionType, optionalExceptionWrapperType, () -> client.request(client.get("/")));
        } else {
            assertResponseAndServiceContext(client.request(client.get("/")));
        }
    }

    @ParameterizedTest(name = "serverSideProtocols={0}, clientSideProtocols={1}," +
            " expectedProtocol={2}, expectedExceptionType={3}, optionalExceptionWrapperType={4}," +
            " acceptInsecureConnections={5}")
    @MethodSource("arguments")
    void testAlpnClientStreamingRequestPayload(List<String> serverSideProtocols,
                                               List<String> clientSideProtocols,
                                               @Nullable HttpProtocolVersion expectedProtocol,
                                               @Nullable Class<? extends Throwable> expectedExceptionType,
                                               @Nullable Class<? extends Throwable> optionalExceptionWrapperType,
                                               boolean acceptInsecureConnections) throws Exception {
        setUp(serverSideProtocols, clientSideProtocols, expectedProtocol, acceptInsecureConnections);
        // Protocol-incompatible combinations are exercised by the negotiation tests above.
        assumeTrue(expectedExceptionType == null);

        final StreamingHttpClient streamingClient = client.asStreamingClient();
        final Buffer chunk1 = streamingClient.executionContext().bufferAllocator().fromAscii("Hello");
        final Buffer chunk2 = streamingClient.executionContext().bufferAllocator().fromAscii(" World!");
        final int expectedBytes = chunk1.readableBytes() + chunk2.readableBytes();

        // An unknown-length body must be framed as Transfer-Encoding: chunked on an h1 connection; verify that still
        // happens when the client prefers h2 but ALPN falls back to h1.
        final StreamingHttpRequest request = streamingClient.post("/").payloadBody(from(chunk1, chunk2));
        final StreamingHttpResponse response = streamingClient.request(request).toFuture().get();
        assertThat(response.status(), is(OK));
        assertThat(response.version(), is(expectedProtocol));
        response.payloadBody().ignoreElements().toFuture().get();

        // Assert the negotiated protocol so a fallback row can't silently degrade into an all-h2 exchange that
        // never exercises h1 chunked framing.
        assertThat(requestVersion.take(), is(expectedProtocol));
        assertThat(requestPayloadSize.take(), is(expectedBytes));
    }

    private void assertResponseAndServiceContext(HttpResponse response) throws Exception {
        assertThat(response.version(), is(expectedProtocol));
        assertThat(response.status(), is(OK));
        assertThat(response.payloadBody(textSerializerUtf8()), is(PAYLOAD_BODY));

        HttpServiceContext serviceCtx = serviceContext.take();
        assertThat(serviceCtx.protocol(), is(expectedProtocol));
        assertThat(serviceCtx.sslSession(), is(notNullValue()));
        assertThat(serviceCtx.sslConfig(), is(notNullValue()));
        assertThat(requestVersion.take(), is(expectedProtocol));

        assertThat(serviceContext, is(empty()));
        assertThat(requestVersion, is(empty()));
    }
}
