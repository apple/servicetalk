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
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.http.api.HttpPayloadWriter;
import io.servicetalk.http.api.ReservedStreamingHttpConnection;
import io.servicetalk.http.api.StreamingHttpClient;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.transport.api.IoExecutor;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.netty.internal.IoThreadFactory;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;

import java.nio.channels.ClosedChannelException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static io.servicetalk.concurrent.api.AsyncCloseables.newCompositeCloseable;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.http.api.HttpHeaderNames.CONNECTION;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_LENGTH;
import static io.servicetalk.http.api.HttpHeaderValues.CLOSE;
import static io.servicetalk.http.api.HttpHeaderValues.ZERO;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.api.HttpSerializationProviders.textSerializer;
import static io.servicetalk.http.api.Matchers.contentEqualTo;
import static io.servicetalk.transport.netty.NettyIoExecutors.createIoExecutor;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static io.servicetalk.utils.internal.PlatformDependent.throwException;
import static java.lang.String.valueOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThrows;

public class ConnectionCloseHeaderHandlingTest {

    @Rule
    public final ServiceTalkTestTimeout timeout = new ServiceTalkTestTimeout();

    private final IoExecutor serverIoExecutor;
    private final ServerContext serverContext;
    private final StreamingHttpClient client;
    private final ReservedStreamingHttpConnection connection;

    private final CountDownLatch sendResponse = new CountDownLatch(1);
    private final CountDownLatch responseReceived = new CountDownLatch(1);
    private final CountDownLatch requestReceived = new CountDownLatch(1);
    private final CountDownLatch connectionClosed = new CountDownLatch(1);
    private final AtomicInteger requestPayloadSize = new AtomicInteger();

    public ConnectionCloseHeaderHandlingTest() throws Exception {
        serverIoExecutor = createIoExecutor(new IoThreadFactory("server-io-executor"));
        serverContext = HttpServers.forAddress(localAddress(0))
                .ioExecutor(serverIoExecutor)
                .listenBlockingStreamingAndAwait((ctx, request, response) -> {
                    requestReceived.countDown();
                    String content = "server_content";
                    response.addHeader(CONTENT_LENGTH, valueOf(content.length()))
                            .addHeader(CONNECTION, CLOSE);

                    sendResponse.await();
                    try (HttpPayloadWriter<String> writer = response.sendMetaData(textSerializer())) {
                        // Defer payload body to see how client processes "Connection: close" header
                        request.payloadBody().forEach(chunk -> requestPayloadSize.addAndGet(chunk.readableBytes()));
                        responseReceived.await();
                        writer.write(content);
                    }
                });

        client = HttpClients.forSingleAddress(serverHostAndPort(serverContext))
                .buildStreaming();
        connection = client.reserveConnection(client.get("/")).toFuture().get();
        connection.onClose().whenFinally(connectionClosed::countDown).subscribe();
    }

    @After
    public void tearDown() throws Exception {
        newCompositeCloseable().appendAll(client, serverContext, serverIoExecutor).close();
    }

    @Test
    public void serverCloseNoRequestPayloadBody() throws Exception {
        sendRequestAndAssertResponse(connection.get("/first")
                .addHeader(CONTENT_LENGTH, ZERO));
    }

    @Test
    public void serverCloseRequestWithPayloadBody() throws Exception {
        String content = "request_content";
        sendRequestAndAssertResponse(connection.post("/first")
                .addHeader(CONTENT_LENGTH, valueOf(content.length()))
                .payloadBody(client.executionContext().executor().submit(() -> {
                    try {
                        responseReceived.await();
                    } catch (InterruptedException e) {
                        throwException(e);
                    }
                }).concat(from(content)), textSerializer()));
    }

    private void sendRequestAndAssertResponse(StreamingHttpRequest request) throws Exception {
        sendResponse.countDown();
        StreamingHttpResponse response = connection.request(request).toFuture().get();
        assertResponse(response);
        responseReceived.countDown();

        assertResponsePayloadBody(response);
        assertThat(request.headers().get(CONTENT_LENGTH), contentEqualTo(valueOf(requestPayloadSize.get())));

        connectionClosed.await();
        assertClosedChannelException("/second");
    }

    @Test
    public void serverCloseTwoPipelinedRequestsSentBeforeFirstResponse() throws Exception {
        sendResponse.countDown();
        AtomicReference<StreamingHttpResponse> firstResponse = new AtomicReference<>();
        AtomicReference<Throwable> secondRequestError = new AtomicReference<>();
        CountDownLatch secondResponseReceived = new CountDownLatch(1);

        connection.request(connection.get("/first")
                .addHeader(CONTENT_LENGTH, ZERO)).subscribe(first -> {
            firstResponse.set(first);
            responseReceived.countDown();
        });
        connection.request(connection.get("/second")
                .addHeader(CONTENT_LENGTH, ZERO))
                .whenOnError(secondRequestError::set)
                .whenFinally(secondResponseReceived::countDown)
                .subscribe(second -> { });
        requestReceived.await();
        responseReceived.await();

        StreamingHttpResponse response = firstResponse.get();
        assertResponse(response);
        assertResponsePayloadBody(response);

        connectionClosed.await();
        secondResponseReceived.await();
        assertThat(secondRequestError.get(), instanceOf(ClosedChannelException.class));
        assertClosedChannelException("/third");
    }

    @Test
    public void serverCloseTwoPipelinedRequestsInSequence() throws Exception {
        sendResponse.countDown();
        StreamingHttpResponse response = connection.request(connection.get("/first")
                .addHeader(CONTENT_LENGTH, ZERO)).toFuture().get();
        assertResponse(response);

        // Send another request before client reads payload body of the first request:
        assertClosedChannelException("/second");

        responseReceived.countDown();
        assertResponsePayloadBody(response);
        connectionClosed.await();
    }

    @Test
    public void clientCloseTwoPipelinedRequestsSentBeforeFirstResponse() throws Exception {
        AtomicReference<StreamingHttpResponse> firstResponse = new AtomicReference<>();

        connection.request(connection.get("/first")
                .addHeader(CONTENT_LENGTH, ZERO)
                // Request connection closure:
                .addHeader(CONNECTION, CLOSE)).subscribe(first -> {
            firstResponse.set(first);
            responseReceived.countDown();
        });
        // Send another request before client receives a response for the first request:
        assertClosedChannelException("/second");
        sendResponse.countDown();
        responseReceived.await();

        StreamingHttpResponse response = firstResponse.get();
        assertResponse(response);
        assertResponsePayloadBody(response);
        connectionClosed.await();
    }

    private static void assertResponse(StreamingHttpResponse response) {
        assertThat(response.status(), is(OK));
        assertThat(response.headers().get(CONNECTION), contentEqualTo(CLOSE));
    }

    private static void assertResponsePayloadBody(StreamingHttpResponse response) throws Exception {
        int actualContentLength = response.payloadBody().map(Buffer::readableBytes)
                .collect(AtomicInteger::new, (total, current) -> {
                    total.addAndGet(current);
                    return total;
                }).toFuture().get().get();
        assertThat(response.headers().get(CONTENT_LENGTH), contentEqualTo(valueOf(actualContentLength)));
    }

    private void assertClosedChannelException(String path) {
        Exception e = assertThrows(ExecutionException.class,
                () -> connection.request(connection.get(path).addHeader(CONTENT_LENGTH, ZERO)).toFuture().get());
        assertThat(e.getCause(), instanceOf(ClosedChannelException.class));
    }
}
