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
import io.servicetalk.client.api.ConnectionFactory;
import io.servicetalk.client.api.DelegatingConnectionFactory;
import io.servicetalk.concurrent.BlockingIterator;
import io.servicetalk.concurrent.api.AsyncCloseable;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.FilterableStreamingHttpConnection;
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
import io.servicetalk.transport.api.TransportObserver;
import io.servicetalk.transport.netty.internal.CloseHandler.CloseEvent;
import io.servicetalk.transport.netty.internal.CloseHandler.CloseEventObservedException;
import io.servicetalk.transport.netty.internal.ExecutionContextExtension;
import io.servicetalk.transport.netty.internal.NettyConnectionContext;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.ClosedChannelException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nullable;

import static io.servicetalk.buffer.api.Matchers.contentEqualTo;
import static io.servicetalk.concurrent.api.AsyncCloseables.newCompositeCloseable;
import static io.servicetalk.concurrent.api.Completable.completed;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.http.api.HttpExecutionStrategies.defaultStrategy;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_LENGTH;
import static io.servicetalk.http.api.HttpHeaderValues.ZERO;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.api.HttpSerializers.appSerializerUtf8FixLen;
import static io.servicetalk.http.netty.ContentLengthAndTrailersTest.addFixedLengthFramingOverhead;
import static io.servicetalk.http.netty.HttpClients.forResolvedAddress;
import static io.servicetalk.http.netty.HttpClients.forSingleAddressViaProxy;
import static io.servicetalk.http.netty.HttpProtocol.HTTP_2;
import static io.servicetalk.http.netty.HttpProtocol.values;
import static io.servicetalk.http.netty.HttpServers.forAddress;
import static io.servicetalk.http.netty.HttpsProxyTest.safeClose;
import static io.servicetalk.logging.api.LogLevel.TRACE;
import static io.servicetalk.test.resources.DefaultTestCerts.serverPemHostname;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.newSocketAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static io.servicetalk.transport.netty.internal.CloseHandler.CloseEvent.CHANNEL_CLOSED_INBOUND;
import static io.servicetalk.transport.netty.internal.CloseHandler.CloseEvent.GRACEFUL_USER_CLOSING;
import static io.servicetalk.utils.internal.PlatformDependent.throwException;
import static java.lang.Integer.parseInt;
import static java.lang.String.valueOf;
import static java.util.Arrays.asList;
import static java.util.Objects.requireNonNull;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GracefulConnectionClosureHandlingTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(GracefulConnectionClosureHandlingTest.class);
    private static final Collection<Boolean> TRUE_FALSE = asList(true, false);

    @RegisterExtension
    static final ExecutionContextExtension SERVER_CTX =
            ExecutionContextExtension.cached("server-io", "server-executor");
    @RegisterExtension
    static final ExecutionContextExtension CLIENT_CTX =
            ExecutionContextExtension.cached("client-io", "client-executor");

    private static final String REQUEST_CONTENT = "request_content";
    private static final String RESPONSE_CONTENT = "response_content";

    private HttpProtocol protocol;
    private boolean initiateClosureFromClient;
    private AsyncCloseable toClose;
    private final CountDownLatch onClosing = new CountDownLatch(1);
    private final CountDownLatch connectionAccepted = new CountDownLatch(1);

    @Nullable
    private ProxyTunnel proxyTunnel;
    private ServerContext serverContext;
    private StreamingHttpClient client;
    private ReservedStreamingHttpConnection connection;

    private final CountDownLatch clientConnectionClosed = new CountDownLatch(1);
    private final CountDownLatch serverConnectionClosed = new CountDownLatch(1);
    private final CountDownLatch serverContextClosed = new CountDownLatch(1);

    private final CountDownLatch serverReceivedRequest = new CountDownLatch(1);
    private final BlockingQueue<Integer> serverReceivedRequestPayload = new ArrayBlockingQueue<>(2);
    private final CountDownLatch serverSendResponse = new CountDownLatch(1);
    private final CountDownLatch serverSendResponsePayload = new CountDownLatch(1);

    void setUp(HttpProtocol protocol, boolean initiateClosureFromClient,
               boolean useUds, boolean viaProxy) throws Exception {
        this.protocol = protocol;
        this.initiateClosureFromClient = initiateClosureFromClient;

        if (useUds) {
            Assumptions.assumeTrue(
                    SERVER_CTX.ioExecutor().isUnixDomainSocketSupported(),
                    "Server's IoExecutor does not support UnixDomainSocket");
            Assumptions.assumeTrue(
                    CLIENT_CTX.ioExecutor().isUnixDomainSocketSupported(),
                    "Client's IoExecutor does not support UnixDomainSocket");
            Assumptions.assumeFalse(viaProxy, "UDS cannot be used via proxy");
        }
        Assumptions.assumeFalse(protocol == HTTP_2 && viaProxy, "Proxy is not supported with HTTP/2");

        HttpServerBuilder serverBuilder = (useUds ?
                forAddress(newSocketAddress()) :
                forAddress(localAddress(0)))
                .protocols(protocol.config)
                .ioExecutor(SERVER_CTX.ioExecutor())
                .executionStrategy(defaultStrategy(SERVER_CTX.executor()))
                .enableWireLogging("servicetalk-tests-wire-logger", TRACE, () -> true)
                .appendConnectionAcceptorFilter(original -> new DelegatingConnectionAcceptor(original) {
                    @Override
                    public Completable accept(final ConnectionContext context) {
                        if (!initiateClosureFromClient) {
                            ((NettyConnectionContext) context).onClosing()
                                    .whenFinally(onClosing::countDown).subscribe();
                        }
                        context.onClose().whenFinally(serverConnectionClosed::countDown).subscribe();
                        connectionAccepted.countDown();
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

        serverContext = serverBuilder.listenBlockingStreamingAndAwait((ctx, request, response) -> {
            serverReceivedRequest.countDown();
            response.addHeader(CONTENT_LENGTH, valueOf(addFixedLengthFramingOverhead(RESPONSE_CONTENT.length())));

            serverSendResponse.await();
            try (HttpPayloadWriter<String> writer = response.sendMetaData(appSerializerUtf8FixLen())) {
                // Subscribe to the request payload body before response writer closes
                BlockingIterator<Buffer> iterator = request.payloadBody().iterator();
                // Consume request payload body asynchronously:
                ctx.executionContext().executor().submit(() -> {
                    int receivedSize = 0;
                    while (iterator.hasNext()) {
                        Buffer chunk = iterator.next();
                        assert chunk != null;
                        receivedSize += chunk.readableBytes();
                    }
                    serverReceivedRequestPayload.add(receivedSize);
                }).beforeOnError(cause -> {
                    LOGGER.error("failure while writing response", cause);
                    serverReceivedRequestPayload.add(-1);
                    PayloadWriterUtils.safeClose(writer, cause);
                }).toFuture();
                serverSendResponsePayload.await();
                writer.write(RESPONSE_CONTENT);
            }
        });
        serverContext.onClose().whenFinally(serverContextClosed::countDown).subscribe();

        client = (viaProxy ? forSingleAddressViaProxy(serverHostAndPort(serverContext), proxyAddress)
                .sslConfig(new ClientSslConfigBuilder(DefaultTestCerts::loadServerCAPem)
                        .peerHost(serverPemHostname()).build()) :
                forResolvedAddress(serverContext.listenAddress()))
                .protocols(protocol.config)
                .ioExecutor(CLIENT_CTX.ioExecutor())
                .executionStrategy(defaultStrategy(CLIENT_CTX.executor()))
                .enableWireLogging("servicetalk-tests-wire-logger", TRACE, () -> true)
                .appendConnectionFactoryFilter(cf -> initiateClosureFromClient ?
                        new OnClosingConnectionFactoryFilter<>(cf, onClosing) : cf)
                .buildStreaming();
        connection = client.reserveConnection(client.get("/")).toFuture().get();
        connection.onClose().whenFinally(clientConnectionClosed::countDown).subscribe();
        connectionAccepted.await(); // wait until server accepts connection

        toClose = initiateClosureFromClient ? connection : serverContext;
    }

    @SuppressWarnings("unused")
    private static Collection<Arguments> data() {
        Collection<Arguments> data = new ArrayList<>();
        for (HttpProtocol protocol : values()) {
            for (boolean initiateClosureFromClient : TRUE_FALSE) {
                for (boolean useUds : TRUE_FALSE) {
                    for (boolean viaProxy : TRUE_FALSE) {
                        if (viaProxy && (useUds || protocol == HTTP_2)) {
                            // UDS cannot be used via proxy
                            // Proxy is not supported with HTTP/2
                            continue;
                        }
                        data.add(Arguments.of(protocol, initiateClosureFromClient, useUds, viaProxy));
                    }
                }
            }
        }
        return data;
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

    @ParameterizedTest(name = "{index}: protocol={0} initiateClosureFromClient={1} useUds={2} viaProxy={3}")
    @MethodSource("data")
    void closeIdleBeforeExchange(HttpProtocol protocol, boolean initiateClosureFromClient,
                                 boolean useUds, boolean viaProxy) throws Exception {
        setUp(protocol, initiateClosureFromClient, useUds, viaProxy);
        triggerGracefulClosure();
        awaitConnectionClosed();
    }

    @ParameterizedTest(name = "{index}: protocol={0} initiateClosureFromClient={1} useUds={2} viaProxy={3}")
    @MethodSource("data")
    void closeIdleAfterExchange(HttpProtocol protocol, boolean initiateClosureFromClient,
                                boolean useUds, boolean viaProxy) throws Exception {
        setUp(protocol, initiateClosureFromClient, useUds, viaProxy);
        serverSendResponse.countDown();
        serverSendResponsePayload.countDown();

        StreamingHttpResponse response = connection.request(newRequest("/first")).toFuture().get();
        assertResponse(response);
        assertResponsePayloadBody(response);

        triggerGracefulClosure();
        awaitConnectionClosed();
    }

    @ParameterizedTest(name = "{index}: protocol={0} initiateClosureFromClient={1} useUds={2} viaProxy={3}")
    @MethodSource("data")
    void closeAfterRequestMetaDataSentNoResponseReceived(HttpProtocol protocol,
                                                         boolean initiateClosureFromClient,
                                                         boolean useUds,
                                                         boolean viaProxy) throws Exception {
        setUp(protocol, initiateClosureFromClient, useUds, viaProxy);
        CountDownLatch clientSendRequestPayload = new CountDownLatch(1);
        StreamingHttpRequest request = newRequest("/first", clientSendRequestPayload);
        Future<StreamingHttpResponse> responseFuture = connection.request(request).toFuture();
        serverReceivedRequest.await();

        triggerGracefulClosure();

        serverSendResponse.countDown();
        StreamingHttpResponse response = responseFuture.get();
        assertResponse(response);

        clientSendRequestPayload.countDown();
        serverSendResponsePayload.countDown();
        assertRequestPayloadBody(request);
        assertResponsePayloadBody(response);

        awaitConnectionClosed();
        assertNextRequestFails();
    }

    @ParameterizedTest(name = "{index}: protocol={0} initiateClosureFromClient={1} useUds={2} viaProxy={3}")
    @MethodSource("data")
    void closeAfterFullRequestSentNoResponseReceived(HttpProtocol protocol, boolean initiateClosureFromClient,
                                                     boolean useUds, boolean viaProxy) throws Exception {
        setUp(protocol, initiateClosureFromClient, useUds, viaProxy);
        StreamingHttpRequest request = newRequest("/first");
        Future<StreamingHttpResponse> responseFuture = connection.request(request).toFuture();
        serverReceivedRequest.await();

        triggerGracefulClosure();

        serverSendResponse.countDown();
        StreamingHttpResponse response = responseFuture.get();
        assertResponse(response);

        assertRequestPayloadBody(request);
        serverSendResponsePayload.countDown();
        assertResponsePayloadBody(response);

        awaitConnectionClosed();
        assertNextRequestFails();
    }

    @ParameterizedTest(name = "{index}: protocol={0} initiateClosureFromClient={1} useUds={2} viaProxy={3}")
    @MethodSource("data")
    void closeAfterRequestMetaDataSentResponseMetaDataReceived(HttpProtocol protocol,
                                                               boolean initiateClosureFromClient,
                                                               boolean useUds,
                                                               boolean viaProxy) throws Exception {
        setUp(protocol, initiateClosureFromClient, useUds, viaProxy);
        CountDownLatch clientSendRequestPayload = new CountDownLatch(1);
        StreamingHttpRequest request = newRequest("/first", clientSendRequestPayload);
        Future<StreamingHttpResponse> responseFuture = connection.request(request).toFuture();

        serverSendResponse.countDown();
        StreamingHttpResponse response = responseFuture.get();
        assertResponse(response);

        triggerGracefulClosure();

        clientSendRequestPayload.countDown();
        serverSendResponsePayload.countDown();
        assertRequestPayloadBody(request);
        assertResponsePayloadBody(response);

        awaitConnectionClosed();
        assertNextRequestFails();
    }

    @ParameterizedTest(name = "{index}: protocol={0} initiateClosureFromClient={1} useUds={2} viaProxy={3}")
    @MethodSource("data")
    void closeAfterFullRequestSentResponseMetaDataReceived(HttpProtocol protocol,
                                                           boolean initiateClosureFromClient,
                                                           boolean useUds,
                                                           boolean viaProxy) throws Exception {
        setUp(protocol, initiateClosureFromClient, useUds, viaProxy);
        StreamingHttpRequest request = newRequest("/first");
        Future<StreamingHttpResponse> responseFuture = connection.request(request).toFuture();

        serverSendResponse.countDown();
        StreamingHttpResponse response = responseFuture.get();
        assertResponse(response);

        triggerGracefulClosure();

        serverSendResponsePayload.countDown();
        assertRequestPayloadBody(request);
        assertResponsePayloadBody(response);

        awaitConnectionClosed();
        assertNextRequestFails();
    }

    @ParameterizedTest(name = "{index}: protocol={0} initiateClosureFromClient={1} useUds={2} viaProxy={3}")
    @MethodSource("data")
    void closeAfterRequestMetaDataSentFullResponseReceived(HttpProtocol protocol,
                                                           boolean initiateClosureFromClient,
                                                           boolean useUds,
                                                           boolean viaProxy) throws Exception {
        setUp(protocol, initiateClosureFromClient, useUds, viaProxy);
        CountDownLatch clientSendRequestPayload = new CountDownLatch(1);
        StreamingHttpRequest request = newRequest("/first", clientSendRequestPayload);
        Future<StreamingHttpResponse> responseFuture = connection.request(request).toFuture();

        serverSendResponse.countDown();
        StreamingHttpResponse response = responseFuture.get();
        assertResponse(response);

        serverSendResponsePayload.countDown();

        CharSequence responseContentLengthHeader = response.headers().get(CONTENT_LENGTH);
        assertThat(responseContentLengthHeader, is(notNullValue()));
        AtomicInteger responseContentLength = new AtomicInteger(parseInt(responseContentLengthHeader.toString()));
        CountDownLatch responsePayloadReceived = new CountDownLatch(1);
        CountDownLatch responsePayloadComplete = new CountDownLatch(1);
        response.payloadBody().whenOnNext(chunk -> {
            if (responseContentLength.addAndGet(-chunk.readableBytes()) == 0) {
                responsePayloadReceived.countDown();
            }
        }).ignoreElements().subscribe(responsePayloadComplete::countDown);
        responsePayloadReceived.await();

        triggerGracefulClosure();

        clientSendRequestPayload.countDown();
        responsePayloadComplete.await();
        assertRequestPayloadBody(request);

        awaitConnectionClosed();
        assertNextRequestFails();
    }

    @ParameterizedTest(name = "{index}: protocol={0} initiateClosureFromClient={1} useUds={2} viaProxy={3}")
    @MethodSource("data")
    void closePipelinedAfterTwoRequestsSentBeforeAnyResponseReceived(HttpProtocol protocol,
                                                                     boolean initiateClosureFromClient,
                                                                     boolean useUds,
                                                                     boolean viaProxy) throws Exception {
        setUp(protocol, initiateClosureFromClient, useUds, viaProxy);
        StreamingHttpRequest firstRequest = newRequest("/first");
        Future<StreamingHttpResponse> firstResponseFuture = connection.request(firstRequest).toFuture();
        serverReceivedRequest.await();

        CountDownLatch secondRequestSent = new CountDownLatch(1);
        StreamingHttpRequest secondRequest = newRequest("/second")
                .transformPayloadBody(payload -> payload.whenOnComplete(secondRequestSent::countDown));
        Future<StreamingHttpResponse> secondResponseFuture = connection.request(secondRequest).toFuture();
        secondRequestSent.await();

        triggerGracefulClosure();

        serverSendResponse.countDown();
        serverSendResponsePayload.countDown();

        StreamingHttpResponse firstResponse = firstResponseFuture.get();
        assertResponse(firstResponse);
        assertResponsePayloadBody(firstResponse);
        assertRequestPayloadBody(firstRequest);

        if (initiateClosureFromClient) {
            StreamingHttpResponse secondResponse = secondResponseFuture.get();
            assertResponse(secondResponse);
            assertResponsePayloadBody(secondResponse);
            assertRequestPayloadBody(secondRequest);
        } else {
            // In case of server graceful closure the second response may complete successfully if the second request
            // reached the server before the closure was triggered, or may fail if it's not.
            StreamingHttpResponse secondResponse = null;
            try {
                secondResponse = secondResponseFuture.get();
            } catch (ExecutionException e) {
                if (proxyTunnel != null) {
                    assertThat(e.getCause(), anyOf(instanceOf(ClosedChannelException.class),
                            instanceOf(IOException.class)));
                } else {
                    assertClosedChannelException(secondResponseFuture::get, CHANNEL_CLOSED_INBOUND);
                }
            }
            if (secondResponse != null) {
                assertResponse(secondResponse);
                assertResponsePayloadBody(secondResponse);
                assertRequestPayloadBody(secondRequest);
            }
        }

        awaitConnectionClosed();
        assertNextRequestFails();
    }

    private StreamingHttpRequest newRequest(String path) {
        return connection.post(path)
                .addHeader(CONTENT_LENGTH, valueOf(addFixedLengthFramingOverhead(REQUEST_CONTENT.length())))
                .payloadBody(from(REQUEST_CONTENT), appSerializerUtf8FixLen());
    }

    private StreamingHttpRequest newRequest(String path, CountDownLatch payloadBodyLatch) {
        return connection.post(path)
                .addHeader(CONTENT_LENGTH, valueOf(addFixedLengthFramingOverhead(REQUEST_CONTENT.length())))
                .payloadBody(connection.connectionContext().executionContext().executor().submit(() -> {
                    try {
                        payloadBodyLatch.await();
                    } catch (InterruptedException e) {
                        throwException(e);
                    }
                }).concat(from(REQUEST_CONTENT)), appSerializerUtf8FixLen());
    }

    private static void assertResponse(StreamingHttpResponse response) {
        assertThat(response.status(), is(OK));
    }

    private static void assertResponsePayloadBody(StreamingHttpResponse response) throws Exception {
        CharSequence contentLengthHeader = response.headers().get(CONTENT_LENGTH);
        assertThat(contentLengthHeader, is(notNullValue()));
        int actualContentLength = response.payloadBody().map(Buffer::readableBytes)
                .collect(() -> 0, Integer::sum).toFuture().get();
        assertThat(valueOf(actualContentLength), contentEqualTo(contentLengthHeader));
    }

    private void assertRequestPayloadBody(StreamingHttpRequest request) throws Exception {
        int actualContentLength = serverReceivedRequestPayload.take();
        CharSequence contentLengthHeader = request.headers().get(CONTENT_LENGTH);
        assertThat(contentLengthHeader, is(notNullValue()));
        assertThat(valueOf(actualContentLength), contentEqualTo(contentLengthHeader));
    }

    private void triggerGracefulClosure() throws Exception {
        toClose.closeAsyncGracefully().subscribe();
        onClosing.await();
    }

    private void awaitConnectionClosed() throws Exception {
        clientConnectionClosed.await();
        serverConnectionClosed.await();
        if (!initiateClosureFromClient) {
            serverContextClosed.await();
        }
    }

    private void assertNextRequestFails() {
        assertClosedChannelException(
                () -> connection.request(connection.get("/next").addHeader(CONTENT_LENGTH, ZERO)).toFuture().get(),
                initiateClosureFromClient ? GRACEFUL_USER_CLOSING : CHANNEL_CLOSED_INBOUND);
    }

    private void assertClosedChannelException(Executable runnable, CloseEvent expectedCloseEvent) {
        Exception e = assertThrows(ExecutionException.class, runnable);
        Throwable cause = e.getCause();
        assertThat(cause, anyOf(instanceOf(ClosedChannelException.class), instanceOf(IOException.class)));
        if (protocol == HTTP_2) {
            // HTTP/2 does not enhance ClosedChannelException with CloseEvent
            return;
        }
        while (cause != null && !(cause instanceof CloseEventObservedException)) {
            cause = cause.getCause();
        }
        assertThat("Exception is not enhanced with CloseEvent", cause, is(notNullValue()));
        assertThat(((CloseEventObservedException) cause).event(), is(expectedCloseEvent));
    }

    private static class OnClosingConnectionFactoryFilter<ResolvedAddress extends SocketAddress>
            extends DelegatingConnectionFactory<ResolvedAddress, FilterableStreamingHttpConnection> {

        private final CountDownLatch onClosing;

        OnClosingConnectionFactoryFilter(ConnectionFactory<ResolvedAddress, FilterableStreamingHttpConnection> cf,
                                         CountDownLatch onClosing) {
            super(cf);
            this.onClosing = requireNonNull(onClosing);
        }

        @Override
        public Single<FilterableStreamingHttpConnection> newConnection(ResolvedAddress address,
                                                                       @Nullable final TransportObserver observer) {
            return delegate().newConnection(address, observer).whenOnSuccess(connection ->
                    ((NettyConnectionContext) connection
                            .connectionContext()).onClosing()
                            .whenFinally(onClosing::countDown)
                            .subscribe());
        }
    }
}
