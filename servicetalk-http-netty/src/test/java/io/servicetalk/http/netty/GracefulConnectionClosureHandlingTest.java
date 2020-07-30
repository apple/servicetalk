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

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.concurrent.BlockingIterator;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
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
import io.servicetalk.transport.netty.internal.ExecutionContextRule;

import org.junit.After;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import java.nio.channels.ClosedChannelException;
import java.util.Collection;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.AsyncCloseables.newCompositeCloseable;
import static io.servicetalk.concurrent.api.Completable.completed;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.http.api.HttpExecutionStrategies.defaultStrategy;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_LENGTH;
import static io.servicetalk.http.api.HttpHeaderValues.ZERO;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.api.HttpSerializationProviders.textSerializer;
import static io.servicetalk.http.api.Matchers.contentEqualTo;
import static io.servicetalk.http.netty.HttpsProxyTest.safeClose;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static io.servicetalk.transport.netty.internal.ExecutionContextRule.cached;
import static io.servicetalk.utils.internal.PlatformDependent.throwException;
import static java.lang.Integer.parseInt;
import static java.lang.String.valueOf;
import static java.util.Arrays.asList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertThrows;

@RunWith(Parameterized.class)
public class GracefulConnectionClosureHandlingTest {

    @ClassRule
    public static final ExecutionContextRule SERVER_CTX = cached("server-io", "server-executor");
    @ClassRule
    public static final ExecutionContextRule CLIENT_CTX = cached("client-io", "client-executor");

    private static final String REQUEST_CONTENT = "request_content";
    private static final String RESPONSE_CONTENT = "response_content";

    @Rule
    public final ServiceTalkTestTimeout timeout = new ServiceTalkTestTimeout();

    @Nullable
    private final ProxyTunnel proxyTunnel;
    private final ServerContext serverContext;
    private final StreamingHttpClient client;
    private final ReservedStreamingHttpConnection connection;

    private final CountDownLatch clientConnectionClosed = new CountDownLatch(1);
    private final CountDownLatch serverConnectionClosed = new CountDownLatch(1);

    private final CountDownLatch serverReceivedRequest = new CountDownLatch(1);
    private final BlockingQueue<Integer> serverReceivedRequestPayload = new ArrayBlockingQueue<>(2);
    private final CountDownLatch serverSendResponse = new CountDownLatch(1);
    private final CountDownLatch serverSendResponsePayload = new CountDownLatch(1);

    private final BlockingQueue<StreamingHttpResponse> responses = new ArrayBlockingQueue<>(2);

    public GracefulConnectionClosureHandlingTest(boolean viaProxy) throws Exception {
        HttpServerBuilder serverBuilder = HttpServers.forAddress(localAddress(0))
                .ioExecutor(SERVER_CTX.ioExecutor())
                .executionStrategy(defaultStrategy(SERVER_CTX.executor()))
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

        HostAndPort serverAddress = serverHostAndPort(serverContext);
        client = (viaProxy ? HttpClients.forSingleAddressViaProxy(serverAddress, proxyAddress)
                .secure().disableHostnameVerification()
                .trustManager(DefaultTestCerts::loadMutualAuthCaPem)
                .commit() :
                HttpClients.forSingleAddress(serverAddress))
                .ioExecutor(CLIENT_CTX.ioExecutor())
                .executionStrategy(defaultStrategy(CLIENT_CTX.executor()))
                .buildStreaming();
        connection = client.reserveConnection(client.get("/")).toFuture().get();
        connection.onClose().whenFinally(clientConnectionClosed::countDown).subscribe();
    }

    @Parameters(name = "viaProxy={0}")
    public static Collection<Boolean> data() {
        return asList(false, true);
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
        connection.closeGracefully();
        awaitConnectionClosed();
    }

    @Test
    public void closeIdleAfterExchange() throws Exception {
        serverSendResponse.countDown();
        serverSendResponsePayload.countDown();

        connection.request(newRequest("/first")).subscribe(responses::add);
        StreamingHttpResponse response = responses.take();
        assertResponse(response);
        assertResponsePayloadBody(response);

        connection.closeGracefully();
        awaitConnectionClosed();
    }

    @Test
    public void closeAfterRequestMetaDataSentNoResponseReceived() throws Exception {
        CountDownLatch clientSendRequestPayload = new CountDownLatch(1);
        StreamingHttpRequest request = newRequest("/first", clientSendRequestPayload);
        connection.request(request).subscribe(responses::add);
        serverReceivedRequest.await();

        connection.closeAsyncGracefully().subscribe();

        serverSendResponse.countDown();
        StreamingHttpResponse response = responses.take();
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
        connection.request(request).subscribe(responses::add);
        serverReceivedRequest.await();

        connection.closeAsyncGracefully().subscribe();

        serverSendResponse.countDown();
        StreamingHttpResponse response = responses.take();
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
        connection.request(request).subscribe(responses::add);

        serverSendResponse.countDown();
        StreamingHttpResponse response = responses.take();
        assertResponse(response);

        connection.closeAsyncGracefully().subscribe();

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
        connection.request(request).subscribe(responses::add);

        serverSendResponse.countDown();
        StreamingHttpResponse response = responses.take();
        assertResponse(response);

        connection.closeAsyncGracefully().subscribe();

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
        connection.request(request).subscribe(responses::add);

        serverSendResponse.countDown();
        StreamingHttpResponse response = responses.take();
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

        connection.closeAsyncGracefully().subscribe();

        clientSendRequestPayload.countDown();
        responsePayloadComplete.await();
        assertRequestPayloadBody(request);

        awaitConnectionClosed();
        assertNextRequestFails();
    }

    @Test
    public void closePipelinedAfterTwoRequestsSentBeforeAnyResponseReceived() throws Exception {
        StreamingHttpRequest zeroRequest = newRequest("/zero");
        connection.request(zeroRequest).subscribe(responses::add);
        serverReceivedRequest.await();

        CountDownLatch firstRequestSent = new CountDownLatch(1);
        StreamingHttpRequest firstRequest = newRequest("/first")
                .transformPayloadBody(payload -> payload.whenOnComplete(firstRequestSent::countDown));
        connection.request(firstRequest).subscribe(responses::add);
        firstRequestSent.await();

        connection.closeAsyncGracefully().subscribe();

        serverSendResponse.countDown();
        serverSendResponsePayload.countDown();

        StreamingHttpResponse zeroResponse = responses.take();
        assertResponse(zeroResponse);
        assertResponsePayloadBody(zeroResponse);
        assertRequestPayloadBody(zeroRequest);

        StreamingHttpResponse firstResponse = responses.take();
        assertResponse(firstResponse);
        assertResponsePayloadBody(firstResponse);
        assertRequestPayloadBody(firstRequest);

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

    private void awaitConnectionClosed() throws Exception {
        clientConnectionClosed.await();
        serverConnectionClosed.await();
    }

    private void assertNextRequestFails() {
        Exception e = assertThrows(ExecutionException.class,
                () -> connection.request(connection.get("/next").addHeader(CONTENT_LENGTH, ZERO)).toFuture().get());
        assertThat(e.getCause(), instanceOf(ClosedChannelException.class));
    }
}
