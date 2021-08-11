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

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.concurrent.BlockingIterator;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.http.api.HttpPayloadWriter;
import io.servicetalk.http.api.HttpServerBuilder;
import io.servicetalk.http.api.ReservedStreamingHttpConnection;
import io.servicetalk.http.api.StreamingHttpClient;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.oio.api.internal.PayloadWriterUtils;
import io.servicetalk.test.resources.DefaultTestCerts;
import io.servicetalk.transport.api.ClientSslConfigBuilder;
import io.servicetalk.transport.api.ConnectionContext;
import io.servicetalk.transport.api.DelegatingConnectionAcceptor;
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.api.ServerSslConfigBuilder;
import io.servicetalk.transport.netty.internal.ExecutionContextExtension;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.channels.ClosedChannelException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import javax.annotation.Nullable;

import static io.servicetalk.buffer.api.Matchers.contentEqualTo;
import static io.servicetalk.concurrent.api.AsyncCloseables.newCompositeCloseable;
import static io.servicetalk.concurrent.api.Completable.completed;
import static io.servicetalk.concurrent.api.Completable.never;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.http.api.HttpExecutionStrategies.defaultStrategy;
import static io.servicetalk.http.api.HttpHeaderNames.CONNECTION;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_LENGTH;
import static io.servicetalk.http.api.HttpHeaderValues.CLOSE;
import static io.servicetalk.http.api.HttpHeaderValues.ZERO;
import static io.servicetalk.http.api.HttpRequestMethod.GET;
import static io.servicetalk.http.api.HttpRequestMethod.POST;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.api.HttpSerializers.appSerializerUtf8FixLen;
import static io.servicetalk.http.netty.ContentLengthAndTrailersTest.addFixedLengthFramingOverhead;
import static io.servicetalk.http.netty.HttpsProxyTest.safeClose;
import static io.servicetalk.logging.api.LogLevel.TRACE;
import static io.servicetalk.test.resources.DefaultTestCerts.serverPemHostname;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.newSocketAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static io.servicetalk.transport.netty.internal.ExecutionContextExtension.cached;
import static io.servicetalk.utils.internal.PlatformDependent.throwException;
import static java.lang.String.valueOf;
import static java.util.Arrays.asList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

final class ConnectionCloseHeaderHandlingTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConnectionCloseHeaderHandlingTest.class);
    private static final Collection<Boolean> TRUE_FALSE = asList(true, false);
    private static final String SERVER_SHOULD_CLOSE = "serverShouldClose";

    private ConnectionCloseHeaderHandlingTest() {
    }

    @Nested
    abstract class ConnectionSetup {
        @RegisterExtension
        final ExecutionContextExtension serverCtx = cached("server-io", "server-executor");
        @RegisterExtension
        final ExecutionContextExtension clientCtx = cached("client-io", "client-executor");

        @Nullable
        private ProxyTunnel proxyTunnel;
        private ServerContext serverContext;
        private StreamingHttpClient client;
        ReservedStreamingHttpConnection connection;

        private final CountDownLatch clientConnectionClosed = new CountDownLatch(1);
        private final CountDownLatch serverConnectionClosed = new CountDownLatch(1);

        final BlockingQueue<StreamingHttpResponse> responses = new LinkedBlockingDeque<>();

        final CountDownLatch sendResponse = new CountDownLatch(1);
        final CountDownLatch responseReceived = new CountDownLatch(1);
        final CountDownLatch requestReceived = new CountDownLatch(1);
        final CountDownLatch requestPayloadReceived = new CountDownLatch(1);
        final AtomicInteger requestPayloadSize = new AtomicInteger();

        void setUp(boolean useUds, boolean viaProxy, boolean awaitRequestPayload) throws Exception {
            if (useUds) {
                assumeTrue(serverCtx.ioExecutor().isUnixDomainSocketSupported(),
                        "Server's IoExecutor does not support UnixDomainSocket");
                assumeTrue(clientCtx.ioExecutor().isUnixDomainSocketSupported(),
                        "Client's IoExecutor does not support UnixDomainSocket");
                assumeFalse(viaProxy, "UDS cannot be used via proxy");
            }
            HttpServerBuilder serverBuilder = (useUds ?
                    HttpServers.forAddress(newSocketAddress()) :
                    HttpServers.forAddress(localAddress(0)))
                    .ioExecutor(serverCtx.ioExecutor())
                    .executionStrategy(defaultStrategy(serverCtx.executor()))
                    .enableWireLogging("servicetalk-tests-wire-logger", TRACE, () -> true)
                    .appendConnectionAcceptorFilter(original -> new DelegatingConnectionAcceptor(original) {
                        @Override
                        public Completable accept(final ConnectionContext context) {
                            context.onClose().whenFinally(serverConnectionClosed::countDown).subscribe();
                            return completed();
                        }
                    });

            HostAndPort proxyAddress = null;
            if (viaProxy) {
                // Dummy proxy helps to emulate old intermediate systems that do not support half-closed TCP connections
                proxyTunnel = new ProxyTunnel();
                proxyAddress = proxyTunnel.startProxy();
                serverBuilder.sslConfig(new ServerSslConfigBuilder(DefaultTestCerts::loadServerPem,
                        DefaultTestCerts::loadServerKey).build());
            } else {
                proxyTunnel = null;
            }

            serverContext = serverBuilder
                    .listenBlockingStreamingAndAwait((ctx, request, response) -> {
                        requestReceived.countDown();
                        boolean noResponseContent = request.hasQueryParameter("noResponseContent", "true");
                        String content = noResponseContent ? "" : "server_content";
                        response.addHeader(CONTENT_LENGTH, noResponseContent ? ZERO :
                                valueOf(addFixedLengthFramingOverhead(content.length())));

                        // Add the "connection: close" header only when requested:
                        if (request.hasQueryParameter(SERVER_SHOULD_CLOSE)) {
                            response.addHeader(CONNECTION, CLOSE);
                        }

                        sendResponse.await();
                        try (HttpPayloadWriter<String> writer = response.sendMetaData(appSerializerUtf8FixLen())) {
                            // Subscribe to the request payload body before response writer closes
                            BlockingIterator<Buffer> iterator = request.payloadBody().iterator();
                            // Consume request payload body asynchronously:
                            Future<Void> writeFuture = ctx.executionContext().executor().submit(() -> {
                                while (iterator.hasNext()) {
                                    Buffer chunk = iterator.next();
                                    assert chunk != null;
                                    requestPayloadSize.addAndGet(chunk.readableBytes());
                                }
                            }).beforeOnError(cause -> {
                                LOGGER.error("failure while writing response", cause);
                                PayloadWriterUtils.safeClose(writer, cause);
                            })
                            .afterFinally(requestPayloadReceived::countDown)
                            .toFuture();
                            if (awaitRequestPayload) {
                                writeFuture.get();
                            }
                            if (!noResponseContent) {
                                // Defer payload body to see how client-side processes "Connection: close" header
                                boolean done = false;
                                do {
                                    try {
                                        responseReceived.await();
                                        done = true;
                                    } catch (InterruptedException interruptedException) {
                                        // ignored
                                    }
                                } while (!done);
                            }
                            writer.write(content);
                        }
                    });

            client = (viaProxy ? HttpClients.forSingleAddressViaProxy(serverHostAndPort(serverContext), proxyAddress)
                    .sslConfig(new ClientSslConfigBuilder(DefaultTestCerts::loadServerCAPem)
                            .peerHost(serverPemHostname()).build()) :
                    HttpClients.forResolvedAddress(serverContext.listenAddress()))
                    .ioExecutor(clientCtx.ioExecutor())
                    .executionStrategy(defaultStrategy(clientCtx.executor()))
                    .enableWireLogging("servicetalk-tests-wire-logger", TRACE, () -> true)
                    .buildStreaming();
            connection = client.reserveConnection(client.get("/")).toFuture().get();
            connection.onClose().whenFinally(clientConnectionClosed::countDown).subscribe();
        }

        @AfterEach
        void tearDown() throws Exception {
            try {
                newCompositeCloseable().appendAll(connection, client, serverContext).close();
            } finally {
                if (proxyTunnel != null) {
                    safeClose(proxyTunnel);
                }
            }
        }

        void assertClosedChannelException(String path) {
            assertClosedChannelException(sendZeroLengthRequest(path));
        }

        void assertClosedChannelException(Future<StreamingHttpResponse> responseFuture) {
            Exception e = assertThrows(ExecutionException.class, responseFuture::get);
            assertThat(e.getCause(), instanceOf(ClosedChannelException.class));
        }

        Future<StreamingHttpResponse> sendZeroLengthRequest(String path) {
            return connection.request(connection.get(path).addHeader(CONTENT_LENGTH, ZERO)).toFuture();
        }

        void assertResponse(StreamingHttpResponse response) {
            assertThat(response.status(), is(OK));
            assertThat(response.headers().get(CONNECTION), contentEqualTo(CLOSE));
        }

        void assertResponsePayloadBody(StreamingHttpResponse response) throws Exception {
            CharSequence contentLengthHeader = response.headers().get(CONTENT_LENGTH);
            assertThat(contentLengthHeader, is(notNullValue()));
            int actualContentLength = response.payloadBody().map(Buffer::readableBytes)
                    .collect(() -> 0, Integer::sum).toFuture().get();
            assertThat(valueOf(actualContentLength), contentEqualTo(contentLengthHeader));
        }

        void awaitConnectionClosed() throws Exception {
            clientConnectionClosed.await();
            serverConnectionClosed.await();
        }
    }

    @SuppressWarnings("unused")
    static Collection<Arguments> nonPipelinedRequestsTestData() {
        Collection<Arguments> data = new ArrayList<>();
        for (boolean useUds : TRUE_FALSE) {
            for (boolean viaProxy : TRUE_FALSE) {
                if (useUds && viaProxy) {
                    // UDS cannot be used via proxy
                    continue;
                }
                for (boolean awaitRequestPayload : TRUE_FALSE) {
                    for (boolean requestInitiatesClosure : TRUE_FALSE) {
                        for (boolean noRequestContent : TRUE_FALSE) {
                            for (boolean noResponseContent : TRUE_FALSE) {
                                data.add(Arguments.of(useUds, viaProxy, awaitRequestPayload,
                                                      requestInitiatesClosure, noRequestContent, noResponseContent));
                            }
                        }
                    }
                }
            }
        }
        return data;
    }

    @Nested
    class NonPipelinedRequestsTest extends ConnectionSetup {

        private final CountDownLatch responsePayloadReceived = new CountDownLatch(1);

        @ParameterizedTest(name = "{index}: useUds={0}, viaProxy={1}, awaitRequestPayload={2}, " +
                                  "requestInitiatesClosure={3}, noRequestContent={4}, noResponseContent={5}")
        @MethodSource("io.servicetalk.http.netty.ConnectionCloseHeaderHandlingTest#nonPipelinedRequestsTestData")
        void testConnectionClosure(boolean useUds, boolean viaProxy, boolean awaitRequestPayload,
                                   boolean requestInitiatesClosure,
                                   boolean noRequestContent, boolean noResponseContent) throws Exception {
            setUp(useUds, viaProxy, awaitRequestPayload);
            String content = "request_content";
            StreamingHttpRequest request = connection.newRequest(noRequestContent ? GET : POST, "/first")
                    .setQueryParameter("noResponseContent", valueOf(noResponseContent))
                    .addHeader(CONTENT_LENGTH, noRequestContent ? ZERO : valueOf(content.length()));
            if (!noRequestContent) {
                request.payloadBody(connection.connectionContext().executionContext().executor().submit(() -> {
                    try {
                        responseReceived.await();
                    } catch (InterruptedException e) {
                        throwException(e);
                    }
                }).concat(from(content)), appSerializerUtf8FixLen());
            }
            if (requestInitiatesClosure) {
                request.addHeader(CONNECTION, CLOSE);
            } else {
                request.addQueryParameter(SERVER_SHOULD_CLOSE, "true");
            }

            sendResponse.countDown();
            StreamingHttpResponse response = connection.request(request).toFuture().get();
            assertResponse(response);
            responseReceived.countDown();

            assertResponsePayloadBody(response);
            responsePayloadReceived.countDown();
            requestPayloadReceived.await();
            assertThat(request.headers().get(CONTENT_LENGTH), contentEqualTo(valueOf(requestPayloadSize.get())));

            awaitConnectionClosed();
            assertClosedChannelException("/second");
        }
    }

    @SuppressWarnings("unused")
    static Stream<Arguments> pipelinedRequestsTestData() {
        return Stream.of(
            Arguments.of(false, false, false),
            Arguments.of(false, false, true),
            Arguments.of(false, true, false),
            Arguments.of(false, true, true),
            Arguments.of(true, false, false),
            Arguments.of(true, false, true));
    }

    @Nested
    class PipelinedRequestsTest extends ConnectionSetup {

        @ParameterizedTest(name = "{index}: useUds={0}, viaProxy={1}, awaitRequestPayload={2}")
        @MethodSource("io.servicetalk.http.netty.ConnectionCloseHeaderHandlingTest#pipelinedRequestsTestData")
        void serverCloseTwoPipelinedRequestsSentBeforeFirstResponse(boolean useUds, boolean viaProxy,
                                                                    boolean awaitRequestPayload)
                throws Exception {
            setUp(useUds, viaProxy, awaitRequestPayload);
            AtomicReference<Throwable> secondRequestError = new AtomicReference<>();
            CountDownLatch secondResponseReceived = new CountDownLatch(1);

            connection.request(connection.get("/first")
                    .addQueryParameter(SERVER_SHOULD_CLOSE, "true")
                    .addHeader(CONTENT_LENGTH, ZERO)).subscribe(first -> {
                responses.add(first);
                responseReceived.countDown();
            });
            connection.request(connection.get("/second")
                    .addHeader(CONTENT_LENGTH, ZERO))
                    .whenOnError(secondRequestError::set)
                    .whenFinally(secondResponseReceived::countDown)
                    .subscribe(second -> { });
            requestReceived.await();
            sendResponse.countDown();

            StreamingHttpResponse response = responses.take();
            assertResponse(response);
            assertResponsePayloadBody(response);

            awaitConnectionClosed();
            secondResponseReceived.await();
            assertThat(secondRequestError.get(), instanceOf(ClosedChannelException.class));
            assertClosedChannelException("/third");
        }

        @ParameterizedTest(name = "{index}: useUds={0}, viaProxy={1}, awaitRequestPayload={2}")
        @MethodSource("io.servicetalk.http.netty.ConnectionCloseHeaderHandlingTest#pipelinedRequestsTestData")
        void serverCloseSecondPipelinedRequestWriteAborted(boolean useUds, boolean viaProxy,
                                                           boolean awaitRequestPayload) throws Exception {
            setUp(useUds, viaProxy, awaitRequestPayload);
            AtomicReference<Throwable> secondRequestError = new AtomicReference<>();
            CountDownLatch secondResponseReceived = new CountDownLatch(1);

            connection.request(connection.get("/first")
                    .addQueryParameter(SERVER_SHOULD_CLOSE, "true")
                    .addHeader(CONTENT_LENGTH, ZERO)).subscribe(first -> {
                responses.add(first);
                responseReceived.countDown();
            });
            String content = "request_content";
            connection.request(connection.get("/second")
                    .addHeader(CONTENT_LENGTH, valueOf(content.length()))
                    .payloadBody(from(content).concat(never()), appSerializerUtf8FixLen()))
                    .whenOnError(secondRequestError::set)
                    .whenFinally(secondResponseReceived::countDown)
                    .subscribe(second -> { });
            requestReceived.await();
            sendResponse.countDown();

            StreamingHttpResponse response = responses.take();
            assertResponse(response);
            assertResponsePayloadBody(response);

            awaitConnectionClosed();
            secondResponseReceived.await();
            assertThat(secondRequestError.get(), instanceOf(ClosedChannelException.class));
            assertClosedChannelException("/third");
        }

        @ParameterizedTest(name = "{index}: useUds={0}, viaProxy={1}, awaitRequestPayload={2}")
        @MethodSource("io.servicetalk.http.netty.ConnectionCloseHeaderHandlingTest#pipelinedRequestsTestData")
        void serverCloseTwoPipelinedRequestsInSequence(boolean useUds, boolean viaProxy,
                                                       boolean awaitRequestPayload) throws Exception {
            setUp(useUds, viaProxy, awaitRequestPayload);
            sendResponse.countDown();
            StreamingHttpResponse response = connection.request(connection.get("/first")
                    .addQueryParameter(SERVER_SHOULD_CLOSE, "true")
                    .addHeader(CONTENT_LENGTH, ZERO)).toFuture().get();
            assertResponse(response);

            // Send another request before connection reads payload body of the first request:
            Future<StreamingHttpResponse> secondFuture = sendZeroLengthRequest("/second");

            responseReceived.countDown();
            assertResponsePayloadBody(response);
            assertClosedChannelException(secondFuture);
            awaitConnectionClosed();
        }

        @ParameterizedTest(name = "{index}: useUds={0}, viaProxy={1}, awaitRequestPayload={2}")
        @MethodSource("io.servicetalk.http.netty.ConnectionCloseHeaderHandlingTest#pipelinedRequestsTestData")
        void clientCloseTwoPipelinedRequestsSentFirstInitiatesClosure(boolean useUds, boolean viaProxy,
                                                                      boolean awaitRequestPayload)
                throws Exception {
            setUp(useUds, viaProxy, awaitRequestPayload);
            connection.request(connection.get("/first")
                    .addHeader(CONTENT_LENGTH, ZERO)
                    // Request connection closure:
                    .addHeader(CONNECTION, CLOSE)).subscribe(first -> {
                responses.add(first);
                responseReceived.countDown();
            });
            // Send another request before connection receives a response for the first request:
            Future<StreamingHttpResponse> secondFuture = sendZeroLengthRequest("/second");
            sendResponse.countDown();

            StreamingHttpResponse response = responses.take();
            assertResponse(response);
            assertResponsePayloadBody(response);
            assertClosedChannelException(secondFuture);
            awaitConnectionClosed();
        }

        @ParameterizedTest(name = "{index}: useUds={0}, viaProxy={1}, awaitRequestPayload={2}")
        @MethodSource("io.servicetalk.http.netty.ConnectionCloseHeaderHandlingTest#pipelinedRequestsTestData")
        void clientCloseTwoPipelinedRequestsSentSecondInitiatesClosure(boolean useUds, boolean viaProxy,
                                                                       boolean awaitRequestPayload)
                throws Exception {
            setUp(useUds, viaProxy, awaitRequestPayload);
            connection.request(connection.get("/first")
                    .addHeader(CONTENT_LENGTH, ZERO))
                    .subscribe(responses::add);

            connection.request(connection.get("/second")
                    .addHeader(CONTENT_LENGTH, ZERO)
                    // Request connection closure:
                    .addHeader(CONNECTION, CLOSE))
                    .subscribe(responses::add);

            sendResponse.countDown();

            StreamingHttpResponse firstResponse = responses.take();
            responseReceived.countDown();
            assertThat(firstResponse.status(), is(OK));
            assertResponsePayloadBody(firstResponse);

            StreamingHttpResponse secondResponse = responses.take();
            assertResponse(secondResponse);
            assertResponsePayloadBody(secondResponse);

            awaitConnectionClosed();
            assertClosedChannelException("/third");
        }
    }
}
