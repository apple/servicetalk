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
import io.servicetalk.client.api.ConnectionFactoryFilter;
import io.servicetalk.client.api.DelegatingConnectionFactory;
import io.servicetalk.concurrent.BlockingIterator;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.CompositeCloseable;
import io.servicetalk.concurrent.api.ListenableAsyncCloseable;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.context.api.ContextMap;
import io.servicetalk.http.api.FilterableStreamingHttpConnection;
import io.servicetalk.http.api.HttpPayloadWriter;
import io.servicetalk.http.api.HttpProtocolConfig;
import io.servicetalk.http.api.HttpServerBuilder;
import io.servicetalk.http.api.HttpStreamingSerializer;
import io.servicetalk.http.api.ReservedStreamingHttpConnection;
import io.servicetalk.http.api.SingleAddressHttpClientBuilder;
import io.servicetalk.http.api.StreamingHttpClient;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.netty.H2ProtocolConfig.KeepAlivePolicy;
import io.servicetalk.test.resources.DefaultTestCerts;
import io.servicetalk.transport.api.ClientSslConfigBuilder;
import io.servicetalk.transport.api.ConnectionContext;
import io.servicetalk.transport.api.DelegatingConnectionAcceptor;
import io.servicetalk.transport.api.ExecutionStrategy;
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.api.ServerSslConfigBuilder;
import io.servicetalk.transport.api.TransportObserver;
import io.servicetalk.transport.netty.internal.CloseHandler.CloseEvent;
import io.servicetalk.transport.netty.internal.CloseHandler.CloseEventObservedException;
import io.servicetalk.transport.netty.internal.ExecutionContextExtension;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
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
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nullable;

import static io.servicetalk.buffer.api.Matchers.contentEqualTo;
import static io.servicetalk.concurrent.api.AsyncCloseables.newCompositeCloseable;
import static io.servicetalk.concurrent.api.Completable.completed;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.internal.TestTimeoutConstants.CI;
import static io.servicetalk.http.api.HttpExecutionStrategies.defaultStrategy;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_LENGTH;
import static io.servicetalk.http.api.HttpHeaderValues.ZERO;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.api.HttpSerializers.stringStreamingSerializer;
import static io.servicetalk.http.api.ProxyConfig.forAddress;
import static io.servicetalk.http.netty.CloseUtils.onGracefulClosureStarted;
import static io.servicetalk.http.netty.H2KeepAlivePolicies.disabled;
import static io.servicetalk.http.netty.H2KeepAlivePolicies.whenIdleFor;
import static io.servicetalk.http.netty.HttpClients.forResolvedAddress;
import static io.servicetalk.http.netty.HttpClients.forSingleAddress;
import static io.servicetalk.http.netty.HttpProtocol.HTTP_2;
import static io.servicetalk.http.netty.HttpProtocol.applyFrameLogger;
import static io.servicetalk.http.netty.HttpProtocol.values;
import static io.servicetalk.http.netty.HttpProtocolConfigs.h2;
import static io.servicetalk.http.netty.HttpServers.forAddress;
import static io.servicetalk.logging.api.LogLevel.TRACE;
import static io.servicetalk.test.resources.DefaultTestCerts.serverPemHostname;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.newSocketAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static io.servicetalk.transport.netty.internal.CloseHandler.CloseEvent.CHANNEL_CLOSED_INBOUND;
import static io.servicetalk.transport.netty.internal.CloseHandler.CloseEvent.GRACEFUL_USER_CLOSING;
import static io.servicetalk.transport.netty.internal.CloseUtils.safeClose;
import static io.servicetalk.utils.internal.ThrowableUtils.throwException;
import static java.lang.Integer.parseInt;
import static java.lang.String.valueOf;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.time.Duration.ofMillis;
import static java.util.Arrays.asList;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class GracefulConnectionClosureHandlingTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(GracefulConnectionClosureHandlingTest.class);
    private static final long TIMEOUT_MILLIS = CI ? 1000 : 200;
    private static final Collection<Boolean> TRUE_FALSE = asList(true, false);

    static final HttpStreamingSerializer<String> RAW_STRING_SERIALIZER =
            stringStreamingSerializer(US_ASCII, hdr -> { });

    @RegisterExtension
    static final ExecutionContextExtension SERVER_CTX =
            ExecutionContextExtension.cached("server-io", "server-executor")
                    .setClassLevel(true);
    @RegisterExtension
    static final ExecutionContextExtension CLIENT_CTX =
            ExecutionContextExtension.cached("client-io", "client-executor")
                    .setClassLevel(true);

    private static final String REQUEST_CONTENT = "request_content";
    private static final String RESPONSE_CONTENT = "response_content";

    private HttpProtocol protocol;
    private boolean initiateClosureFromClient;
    private ListenableAsyncCloseable toClose;
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

    private void setUp(HttpProtocol protocol, boolean secure, boolean initiateClosureFromClient,
               boolean useUds, boolean viaProxy) throws Exception {
        setUp(protocol, secure, initiateClosureFromClient, useUds, viaProxy, false);
    }

    private void setUp(HttpProtocol protocol, boolean secure, boolean initiateClosureFromClient,
               boolean useUds, boolean viaProxy, boolean withKeepAlive) throws Exception {
        this.protocol = protocol;
        this.initiateClosureFromClient = initiateClosureFromClient;

        if (useUds) {
            assumeTrue(SERVER_CTX.ioExecutor().isUnixDomainSocketSupported(),
                    "Server's IoExecutor does not support UnixDomainSocket");
            assumeTrue(CLIENT_CTX.ioExecutor().isUnixDomainSocketSupported(),
                    "Client's IoExecutor does not support UnixDomainSocket");
            assumeFalse(viaProxy, "UDS cannot be used via proxy");
        }
        if (viaProxy) {
            assumeTrue(secure, "Proxy tunnel works only with secure connections");
        }

        HttpServerBuilder serverBuilder = forAddress(useUds ? newSocketAddress() : localAddress(0))
                .protocols(protocolConfig(protocol, withKeepAlive))
                .ioExecutor(SERVER_CTX.ioExecutor())
                .executor(SERVER_CTX.executor())
                .executionStrategy(defaultStrategy())
                .enableWireLogging("servicetalk-tests-wire-logger", TRACE, () -> true)
                .appendConnectionAcceptorFilter(original -> new DelegatingConnectionAcceptor(original) {
                    @Override
                    public Completable accept(final ConnectionContext context) {
                        if (!initiateClosureFromClient) {
                            onGracefulClosureStarted(context, onClosing);
                        }
                        context.onClose().whenFinally(serverConnectionClosed::countDown).subscribe();
                        connectionAccepted.countDown();
                        return completed();
                    }
                });

        if (secure) {
            serverBuilder.sslConfig(new ServerSslConfigBuilder(DefaultTestCerts::loadServerPem,
                    DefaultTestCerts::loadServerKey).build());
        }

        HostAndPort proxyAddress = null;
        if (viaProxy) {
            // Dummy proxy helps to emulate old intermediate systems that do not support half-closed TCP connections
            proxyTunnel = new ProxyTunnel();
            proxyAddress = proxyTunnel.startProxy();
        } else {
            proxyTunnel = null;
        }

        serverContext = serverBuilder.listenBlockingStreamingAndAwait((ctx, request, response) -> {
            serverReceivedRequest.countDown();
            response.addHeader(CONTENT_LENGTH, valueOf(RESPONSE_CONTENT.length()));

            serverSendResponse.await();
            try (HttpPayloadWriter<String> writer = response.sendMetaData(RAW_STRING_SERIALIZER)) {
                // Subscribe to the request payload body before response writer closes
                BlockingIterator<Buffer> iterator = request.payloadBody().iterator();
                // Consume request payload body asynchronously:
                ctx.executionContext().executor().submit(() -> {
                    try {
                        int receivedSize = 0;
                        while (iterator.hasNext()) {
                            Buffer chunk = iterator.next();
                            assert chunk != null;
                            receivedSize += chunk.readableBytes();
                        }
                        serverReceivedRequestPayload.add(receivedSize);
                    } catch (Throwable t) {
                        writer.close(t);
                    }
                    return null;
                }).beforeOnError(cause -> {
                    LOGGER.error("failure while reading request", cause);
                    serverReceivedRequestPayload.add(-1);
                }).toFuture();
                serverSendResponsePayload.await();
                writer.write(RESPONSE_CONTENT);
            }
        });
        serverContext.onClose().whenFinally(serverContextClosed::countDown).subscribe();

        SingleAddressHttpClientBuilder<?, ? extends SocketAddress> clientBuilder = viaProxy ?
                forSingleAddress(serverHostAndPort(serverContext)).proxyConfig(forAddress(proxyAddress)) :
                forResolvedAddress(serverContext.listenAddress());

        if (secure) {
            clientBuilder.sslConfig(new ClientSslConfigBuilder(DefaultTestCerts::loadServerCAPem)
                    .peerHost(serverPemHostname()).sniHostname(serverPemHostname()).build());
        }

        client = clientBuilder
                .protocols(protocolConfig(protocol, withKeepAlive))
                .executor(CLIENT_CTX.executor())
                .ioExecutor(CLIENT_CTX.ioExecutor())
                .executionStrategy(defaultStrategy())
                .enableWireLogging("servicetalk-tests-wire-logger", TRACE, Boolean.TRUE::booleanValue)
                .appendConnectionFactoryFilter(ConnectionFactoryFilter.withStrategy(
                        cf -> initiateClosureFromClient ? new OnClosingConnectionFactoryFilter<>(cf, onClosing) : cf,
                        ExecutionStrategy.offloadNone()))
                .buildStreaming();
        connection = client.reserveConnection(client.get("/")).toFuture().get();
        connection.onClose().whenFinally(clientConnectionClosed::countDown).subscribe();
        connectionAccepted.await(); // wait until server accepts connection

        toClose = initiateClosureFromClient ? connection : serverContext;
    }

    private static HttpProtocolConfig protocolConfig(HttpProtocol protocol, boolean withKeepAlive) {
        switch (protocol) {
            case HTTP_1:
                return protocol.config;
            case HTTP_2:
                return applyFrameLogger(h2())
                        .keepAlivePolicy(withKeepAlive ?
                                whenIdleFor(ofMillis(TIMEOUT_MILLIS + 200), ofMillis(TIMEOUT_MILLIS)) : disabled())
                        .build();
            default:
                throw new IllegalArgumentException("Unknown protocol: " + protocol);
        }
    }

    private static Collection<Arguments> data() {
        Collection<Arguments> data = new ArrayList<>();
        for (HttpProtocol protocol : values()) {
            for (boolean secure : TRUE_FALSE) {
                for (boolean initiateClosureFromClient : TRUE_FALSE) {
                    for (boolean useUds : TRUE_FALSE) {
                        for (boolean viaProxy : TRUE_FALSE) {
                            if (viaProxy && (useUds || !secure)) {
                                // UDS cannot be used via proxy
                                // Proxy tunnel works only with secure connections
                                continue;
                            }
                            data.add(Arguments.of(protocol, secure, initiateClosureFromClient, useUds, viaProxy));
                        }
                    }
                }
            }
        }
        return data;
    }

    /**
     * This is an equivalent of {@link #data()} that also adds a flag to enable HTTP/2 {@link KeepAlivePolicy} for
     * all HTTP/2 use-cases.
     */
    private static Collection<Arguments> dataWithKeepAlive() {
        Collection<Arguments> data = new ArrayList<>();
        for (Arguments args : data()) {
            Object[] array = Arrays.copyOf(args.get(), args.get().length + 1);
            array[array.length - 1] = false;
            data.add(Arguments.of(array));
            if (array[0] == HTTP_2) {
                Object[] withKeepAlive = array.clone();
                withKeepAlive[withKeepAlive.length - 1] = true;
                data.add(Arguments.of(withKeepAlive));
            }
        }
        return data;
    }

    @AfterEach
    void tearDown() throws Exception {
        try {
            CompositeCloseable compositeCloseable = newCompositeCloseable();
            if (connection != null) {
                compositeCloseable.append(connection);
            }
            if (client != null) {
                compositeCloseable.append(client);
            }
            if (serverContext != null) {
                compositeCloseable.append(serverContext);
            }
            compositeCloseable.close();
        } finally {
            if (proxyTunnel != null) {
                safeClose(proxyTunnel);
            }
        }
    }

    @ParameterizedTest(name = "{index}: protocol={0} secure={1} initiateClosureFromClient={2} useUds={3} viaProxy={4}")
    @MethodSource("data")
    void closeIdleBeforeExchange(HttpProtocol protocol, boolean secure, boolean initiateClosureFromClient,
                                 boolean useUds, boolean viaProxy) throws Exception {
        setUp(protocol, secure, initiateClosureFromClient, useUds, viaProxy);
        triggerGracefulClosure();
        awaitConnectionClosed();
    }

    @ParameterizedTest(name = "{index}: protocol={0} secure={1} initiateClosureFromClient={2} useUds={3} viaProxy={4}")
    @MethodSource("data")
    void closeIdleAfterExchange(HttpProtocol protocol, boolean secure, boolean initiateClosureFromClient,
                                boolean useUds, boolean viaProxy) throws Exception {
        setUp(protocol, secure, initiateClosureFromClient, useUds, viaProxy);
        serverSendResponse.countDown();
        serverSendResponsePayload.countDown();

        StreamingHttpResponse response = connection.request(newRequest("/first")).toFuture().get();
        assertResponse(response);
        assertResponsePayloadBody(response);

        triggerGracefulClosure();
        awaitConnectionClosed();
    }

    @ParameterizedTest(name = "{index}: protocol={0} secure={1} initiateClosureFromClient={2} useUds={3} viaProxy={4}")
    @MethodSource("data")
    void closeAfterRequestMetaDataSentNoResponseReceived(HttpProtocol protocol,
                                                         boolean secure,
                                                         boolean initiateClosureFromClient,
                                                         boolean useUds,
                                                         boolean viaProxy) throws Exception {
        setUp(protocol, secure, initiateClosureFromClient, useUds, viaProxy);
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

    @ParameterizedTest(name = "{index}: protocol={0} secure={1} initiateClosureFromClient={2} useUds={3} viaProxy={4}")
    @MethodSource("data")
    void closeAfterFullRequestSentNoResponseReceived(HttpProtocol protocol,
                                                     boolean secure,
                                                     boolean initiateClosureFromClient,
                                                     boolean useUds,
                                                     boolean viaProxy) throws Exception {
        setUp(protocol, secure, initiateClosureFromClient, useUds, viaProxy);
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

    @Disabled("Issue 2117")
    @ParameterizedTest(name = "{index}: protocol={0} secure={1} initiateClosureFromClient={2} useUds={3} viaProxy={4}")
    @MethodSource("data")
    void closeAfterRequestMetaDataSentResponseMetaDataReceived(HttpProtocol protocol,
                                                               boolean secure,
                                                               boolean initiateClosureFromClient,
                                                               boolean useUds,
                                                               boolean viaProxy) throws Exception {
        setUp(protocol, secure, initiateClosureFromClient, useUds, viaProxy);
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

    @ParameterizedTest(name = "{index}: protocol={0} secure={1} initiateClosureFromClient={2} useUds={3} viaProxy={4}")
    @MethodSource("data")
    void closeAfterFullRequestSentResponseMetaDataReceived(HttpProtocol protocol,
                                                           boolean secure,
                                                           boolean initiateClosureFromClient,
                                                           boolean useUds,
                                                           boolean viaProxy) throws Exception {
        setUp(protocol, secure, initiateClosureFromClient, useUds, viaProxy);
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

    @ParameterizedTest(name = "{index}: protocol={0} secure={1} initiateClosureFromClient={2} useUds={3} viaProxy={4}")
    @MethodSource("data")
    void closeAfterRequestMetaDataSentFullResponseReceived(HttpProtocol protocol,
                                                           boolean secure,
                                                           boolean initiateClosureFromClient,
                                                           boolean useUds,
                                                           boolean viaProxy) throws Exception {
        setUp(protocol, secure, initiateClosureFromClient, useUds, viaProxy);
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

    @Disabled("Issue 2117")
    @ParameterizedTest(name = "{index}: protocol={0} secure={1} initiateClosureFromClient={2} useUds={3} viaProxy={4}")
    @MethodSource("data")
    void closePipelinedAfterTwoRequestsSentBeforeAnyResponseReceived(HttpProtocol protocol,
                                                                     boolean secure,
                                                                     boolean initiateClosureFromClient,
                                                                     boolean useUds,
                                                                     boolean viaProxy) throws Exception {
        setUp(protocol, secure, initiateClosureFromClient, useUds, viaProxy);
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

    @ParameterizedTest(name =
            "{index}: protocol={0} secure={1} initiateClosureFromClient={2} useUds={3} viaProxy={4} withKeepAlive={5}")
    @MethodSource("dataWithKeepAlive")
    void closeWithIndefiniteStreamsRunning(HttpProtocol protocol,
                                           boolean secure,
                                           boolean initiateClosureFromClient,
                                           boolean useUds,
                                           boolean viaProxy,
                                           boolean withKeepAlive) throws Exception {
        setUp(protocol, secure, initiateClosureFromClient, useUds, viaProxy, withKeepAlive);
        serverSendResponse.countDown();
        CountDownLatch clientSendRequestPayload = new CountDownLatch(1);
        // Never count down serverSendResponsePayload and clientSendRequestPayload to simulate indefinite streams

        StreamingHttpRequest request = newRequest("/first", clientSendRequestPayload);
        StreamingHttpResponse response = connection.request(request).toFuture().get();
        assertResponse(response);

        triggerGracefulClosure();
        assertThrows(TimeoutException.class, () -> toClose.onClose().toFuture().get(TIMEOUT_MILLIS, MILLISECONDS));
        // Enforce non-graceful closure after timeout
        toClose.closeAsync().toFuture().get();

        ExecutionException e = assertThrows(ExecutionException.class,
                () -> response.payloadBody().ignoreElements().toFuture().get());
        assertThat(e.getCause(), is(instanceOf(IOException.class)));
        awaitConnectionClosed();
        assertNextRequestFails();
    }

    @ParameterizedTest(name =
            "{index}: protocol={0} secure={1} initiateClosureFromClient={2} useUds={3} viaProxy={4} withKeepAlive={5}")
    @MethodSource("dataWithKeepAlive")
    void closeWithIndefiniteInboundStreamRunning(HttpProtocol protocol,
                                                 boolean secure,
                                                 boolean initiateClosureFromClient,
                                                 boolean useUds,
                                                 boolean viaProxy,
                                                 boolean withKeepAlive) throws Exception {
        assumeFalse(!secure && initiateClosureFromClient,
                "Server must have either response or connection idle timeout to close its connection");
        setUp(protocol, secure, initiateClosureFromClient, useUds, viaProxy, withKeepAlive);
        serverSendResponse.countDown();
        // Never count down serverSendResponsePayload to simulate an indefinite inbound stream

        StreamingHttpRequest request = newRequest("/first");
        StreamingHttpResponse response = connection.request(request).toFuture().get();
        assertResponse(response);
        assertRequestPayloadBody(request);

        triggerGracefulClosure();
        assertThrows(TimeoutException.class, () -> toClose.onClose().toFuture().get(TIMEOUT_MILLIS, MILLISECONDS));
        // Enforce non-graceful closure after timeout
        toClose.closeAsync().toFuture().get();

        ExecutionException e = assertThrows(ExecutionException.class,
                () -> response.payloadBody().ignoreElements().toFuture().get());
        assertThat(e.getCause(), is(instanceOf(IOException.class)));
        awaitConnectionClosed();
        assertNextRequestFails();
    }

    @ParameterizedTest(name =
            "{index}: protocol={0} secure={1} initiateClosureFromClient={2} useUds={3} viaProxy={4} withKeepAlive={5}")
    @MethodSource("dataWithKeepAlive")
    void closeWithIndefiniteOutboundStreamRunning(HttpProtocol protocol,
                                                  boolean secure,
                                                  boolean initiateClosureFromClient,
                                                  boolean useUds,
                                                  boolean viaProxy,
                                                  boolean withKeepAlive) throws Exception {
        assumeFalse(!secure && !initiateClosureFromClient,
                "Client must have either response or connection idle timeout to close its connection");
        setUp(protocol, secure, initiateClosureFromClient, useUds, viaProxy, withKeepAlive);
        serverSendResponse.countDown();
        serverSendResponsePayload.countDown();
        CountDownLatch clientSendRequestPayload = new CountDownLatch(1);
        // Never count down clientSendRequestPayload to simulate an indefinite outbound stream

        StreamingHttpRequest request = newRequest("/first", clientSendRequestPayload);
        StreamingHttpResponse response = connection.request(request).toFuture().get();
        assertResponse(response);

        triggerGracefulClosure();
        assertThrows(TimeoutException.class, () -> toClose.onClose().toFuture().get(TIMEOUT_MILLIS, MILLISECONDS));
        // Enforce non-graceful closure after timeout
        toClose.closeAsync().toFuture().get();

        ExecutionException e = assertThrows(ExecutionException.class,
                () -> response.payloadBody().ignoreElements().toFuture().get());
        assertThat(e.getCause(), is(instanceOf(IOException.class)));
        awaitConnectionClosed();
        assertNextRequestFails();
    }

    private StreamingHttpRequest newRequest(String path) {
        return connection.post(path)
                .addHeader(CONTENT_LENGTH, valueOf(REQUEST_CONTENT.length()))
                .payloadBody(from(REQUEST_CONTENT), RAW_STRING_SERIALIZER);
    }

    private StreamingHttpRequest newRequest(String path, CountDownLatch payloadBodyLatch) {
        return connection.post(path)
                .addHeader(CONTENT_LENGTH, valueOf(REQUEST_CONTENT.length()))
                .payloadBody(connection.connectionContext().executionContext().executor().submit(() -> {
                    try {
                        payloadBodyLatch.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throwException(e);
                    }
                }).concat(from(REQUEST_CONTENT)), RAW_STRING_SERIALIZER);
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
                                                                       @Nullable final ContextMap context,
                                                                       @Nullable final TransportObserver observer) {
            return delegate().newConnection(address, context, observer).whenOnSuccess(connection ->
                    onGracefulClosureStarted(connection.connectionContext(), onClosing));
        }
    }
}
