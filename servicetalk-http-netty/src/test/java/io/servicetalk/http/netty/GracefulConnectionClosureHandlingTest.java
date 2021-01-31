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
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.http.api.FilterableStreamingHttpConnection;
import io.servicetalk.http.api.HttpPayloadWriter;
import io.servicetalk.http.api.HttpServerBuilder;
import io.servicetalk.http.api.ReservedStreamingHttpConnection;
import io.servicetalk.http.api.StreamingHttpClient;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.test.resources.DefaultTestCerts;
import io.servicetalk.transport.api.ConnectionContext;
import io.servicetalk.transport.api.DelegatingConnectionAcceptor;
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.api.TransportObserver;
import io.servicetalk.transport.netty.internal.CloseHandler.CloseEvent;
import io.servicetalk.transport.netty.internal.CloseHandler.CloseEventObservedException;
import io.servicetalk.transport.netty.internal.ExecutionContextRule;
import io.servicetalk.transport.netty.internal.NettyConnectionContext;

import org.junit.After;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.function.ThrowingRunnable;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

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
import static io.servicetalk.http.api.HttpSerializationProviders.textSerializer;
import static io.servicetalk.http.netty.HttpProtocol.HTTP_2;
import static io.servicetalk.http.netty.HttpsProxyTest.safeClose;
import static io.servicetalk.logging.api.LogLevel.TRACE;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.newSocketAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static io.servicetalk.transport.netty.internal.CloseHandler.CloseEvent.CHANNEL_CLOSED_INBOUND;
import static io.servicetalk.transport.netty.internal.CloseHandler.CloseEvent.GRACEFUL_USER_CLOSING;
import static io.servicetalk.transport.netty.internal.ExecutionContextRule.cached;
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
import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

@RunWith(Parameterized.class)
public class GracefulConnectionClosureHandlingTest {

    private static final Collection<Boolean> TRUE_FALSE = asList(true, false);

    @ClassRule
    public static final ExecutionContextRule SERVER_CTX = cached("server-io", "server-executor");
    @ClassRule
    public static final ExecutionContextRule CLIENT_CTX = cached("client-io", "client-executor");

    private static final String REQUEST_CONTENT = "request_content";
    private static final String RESPONSE_CONTENT = "response_content";

    @Rule
    public final ServiceTalkTestTimeout timeout = new ServiceTalkTestTimeout();

    private final HttpProtocol protocol;
    private final boolean initiateClosureFromClient;
    private final AsyncCloseable toClose;
    private final CountDownLatch onClosing = new CountDownLatch(1);

    @Nullable
    private final ProxyTunnel proxyTunnel;
    private final ServerContext serverContext;
    private final StreamingHttpClient client;
    private final ReservedStreamingHttpConnection connection;

    private final CountDownLatch clientConnectionClosed = new CountDownLatch(1);
    private final CountDownLatch serverConnectionClosed = new CountDownLatch(1);
    private final CountDownLatch serverContextClosed = new CountDownLatch(1);

    private final CountDownLatch serverReceivedRequest = new CountDownLatch(1);
    private final BlockingQueue<Integer> serverReceivedRequestPayload = new ArrayBlockingQueue<>(2);
    private final CountDownLatch serverSendResponse = new CountDownLatch(1);
    private final CountDownLatch serverSendResponsePayload = new CountDownLatch(1);

    public GracefulConnectionClosureHandlingTest(HttpProtocol protocol, boolean initiateClosureFromClient,
                                                 boolean useUds, boolean viaProxy) throws Exception {
        this.protocol = protocol;
        this.initiateClosureFromClient = initiateClosureFromClient;

        if (useUds) {
            assumeTrue("Server's IoExecutor does not support UnixDomainSocket",
                    SERVER_CTX.ioExecutor().isUnixDomainSocketSupported());
            assumeTrue("Client's IoExecutor does not support UnixDomainSocket",
                    CLIENT_CTX.ioExecutor().isUnixDomainSocketSupported());
            assumeFalse("UDS cannot be used via proxy", viaProxy);
        }
        assumeFalse("Proxy is not supported with HTTP/2", protocol == HTTP_2 && viaProxy);

        HttpServerBuilder serverBuilder = (useUds ?
                HttpServers.forAddress(newSocketAddress()) :
                HttpServers.forAddress(localAddress(0)))
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
                        return completed();
                    }
                });

        HostAndPort proxyAddress = null;
        if (viaProxy) {
            // Dummy proxy helps to emulate old intermediate systems that do not support half-closed TCP connections
            proxyTunnel = new ProxyTunnel();
            proxyAddress = proxyTunnel.startProxy();
            serverBuilder.secure().commit(DefaultTestCerts::loadServerPem, DefaultTestCerts::loadServerKey);
        } else {
            proxyTunnel = null;
        }

        serverContext = serverBuilder.listenBlockingStreamingAndAwait((ctx, request, response) -> {
            serverReceivedRequest.countDown();
            response.addHeader(CONTENT_LENGTH, valueOf(RESPONSE_CONTENT.length()));

            serverSendResponse.await();
            try (HttpPayloadWriter<String> writer = response.sendMetaData(textSerializer())) {
                // Subscribe to the request payload body before response writer closes
                BlockingIterator<Buffer> iterator = request.payloadBody().iterator();
                // Consume request payload body asynchronously:
                ctx.executionContext().executor().execute(() -> {
                    int receivedSize = 0;
                    while (iterator.hasNext()) {
                        Buffer chunk = iterator.next();
                        assert chunk != null;
                        receivedSize += chunk.readableBytes();
                    }
                    serverReceivedRequestPayload.add(receivedSize);
                });
                serverSendResponsePayload.await();
                writer.write(RESPONSE_CONTENT);
            }
        });
        serverContext.onClose().whenFinally(serverContextClosed::countDown).subscribe();

        client = (viaProxy ? HttpClients.forSingleAddressViaProxy(serverHostAndPort(serverContext), proxyAddress)
                .secure().disableHostnameVerification()
                .trustManager(DefaultTestCerts::loadServerCAPem)
                .commit() :
                HttpClients.forResolvedAddress(serverContext.listenAddress()))
                .protocols(protocol.config)
                .ioExecutor(CLIENT_CTX.ioExecutor())
                .executionStrategy(defaultStrategy(CLIENT_CTX.executor()))
                .enableWireLogging("servicetalk-tests-wire-logger", TRACE, () -> true)
                .appendConnectionFactoryFilter(cf -> initiateClosureFromClient ?
                        new OnClosingConnectionFactoryFilter<>(cf, onClosing) : cf)
                .buildStreaming();
        connection = client.reserveConnection(client.get("/")).toFuture().get();
        connection.onClose().whenFinally(clientConnectionClosed::countDown).subscribe();

        toClose = initiateClosureFromClient ? connection : serverContext;
    }

    @Parameters(name = "{index}: protocol={0} initiateClosureFromClient={1} useUds={2} viaProxy={3}")
    public static Collection<Object[]> data() {
        Collection<Object[]> data = new ArrayList<>();
        for (HttpProtocol protocol : HttpProtocol.values()) {
            for (boolean initiateClosureFromClient : TRUE_FALSE) {
                for (boolean useUds : TRUE_FALSE) {
                    for (boolean viaProxy : TRUE_FALSE) {
                        if (viaProxy && (useUds || protocol == HTTP_2)) {
                            // UDS cannot be used via proxy
                            // Proxy is not supported with HTTP/2
                            continue;
                        }
                        data.add(new Object[] {protocol, initiateClosureFromClient, useUds, viaProxy});
                    }
                }
            }
        }
        return data;
    }

    @After
    public void tearDown() throws Exception {
        try {
            newCompositeCloseable().appendAll(connection, client, serverContext).close();
        } finally {
            if (proxyTunnel != null) {
                safeClose(proxyTunnel);
            }
        }
    }

    @Test
    public void closeIdleBeforeExchange() throws Exception {
        triggerGracefulClosure();
        awaitConnectionClosed();
    }

    @Test
    public void closeIdleAfterExchange() throws Exception {
        serverSendResponse.countDown();
        serverSendResponsePayload.countDown();

        StreamingHttpResponse response = connection.request(newRequest("/first")).toFuture().get();
        assertResponse(response);
        assertResponsePayloadBody(response);

        triggerGracefulClosure();
        awaitConnectionClosed();
    }

    @Test
    public void closeAfterRequestMetaDataSentNoResponseReceived() throws Exception {
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

    @Test
    public void closeAfterFullRequestSentNoResponseReceived() throws Exception {
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

    @Test
    public void closeAfterRequestMetaDataSentResponseMetaDataReceived() throws Exception {
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

    @Test
    public void closeAfterFullRequestSentResponseMetaDataReceived() throws Exception {
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

    @Test
    public void closeAfterRequestMetaDataSentFullResponseReceived() throws Exception {
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

    @Test
    public void closePipelinedAfterTwoRequestsSentBeforeAnyResponseReceived() throws Exception {
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
        return connection.asConnection().post(path)
                .addHeader(CONTENT_LENGTH, valueOf(REQUEST_CONTENT.length()))
                .payloadBody(REQUEST_CONTENT, textSerializer())
                .toStreamingRequest();
    }

    private StreamingHttpRequest newRequest(String path, CountDownLatch payloadBodyLatch) {
        return connection.post(path)
                .addHeader(CONTENT_LENGTH, valueOf(REQUEST_CONTENT.length()))
                .payloadBody(connection.connectionContext().executionContext().executor().submit(() -> {
                    try {
                        payloadBodyLatch.await();
                    } catch (InterruptedException e) {
                        throwException(e);
                    }
                }).concat(from(REQUEST_CONTENT)), textSerializer());
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

    private void assertClosedChannelException(ThrowingRunnable runnable, CloseEvent expectedCloseEvent) {
        Exception e = assertThrows(ExecutionException.class, runnable);
        Throwable cause = e.getCause();
        assertThat(cause, instanceOf(ClosedChannelException.class));
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
                    ((NettyConnectionContext) connection.connectionContext()).onClosing()
                            .whenFinally(onClosing::countDown).subscribe());
        }
    }
}
