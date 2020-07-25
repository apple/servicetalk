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
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.http.api.HttpPayloadWriter;
import io.servicetalk.http.api.HttpServerBuilder;
import io.servicetalk.http.api.ReservedStreamingHttpConnection;
import io.servicetalk.http.api.StreamingHttpClient;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.test.resources.DefaultTestCerts;
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.api.IoExecutor;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.netty.internal.IoThreadFactory;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import java.nio.channels.ClosedChannelException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.AsyncCloseables.newCompositeCloseable;
import static io.servicetalk.concurrent.api.Completable.never;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.http.api.HttpHeaderNames.CONNECTION;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_LENGTH;
import static io.servicetalk.http.api.HttpHeaderValues.CLOSE;
import static io.servicetalk.http.api.HttpHeaderValues.ZERO;
import static io.servicetalk.http.api.HttpRequestMethod.GET;
import static io.servicetalk.http.api.HttpRequestMethod.POST;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.api.HttpSerializationProviders.textSerializer;
import static io.servicetalk.http.api.Matchers.contentEqualTo;
import static io.servicetalk.http.netty.HttpsProxyTest.safeClose;
import static io.servicetalk.transport.netty.NettyIoExecutors.createIoExecutor;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static io.servicetalk.utils.internal.PlatformDependent.throwException;
import static java.lang.String.valueOf;
import static java.util.Arrays.asList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeFalse;

@RunWith(Enclosed.class)
public class ConnectionCloseHeaderHandlingTest {

    private static final Collection<Boolean> TRUE_FALSE = asList(true, false);

    private abstract static class ConnectionSetup {

        @Rule
        public final ServiceTalkTestTimeout timeout = new ServiceTalkTestTimeout();

        @Nullable
        private final ProxyTunnel proxyTunnel;
        private final IoExecutor serverIoExecutor;
        private final ServerContext serverContext;
        private final StreamingHttpClient client;
        protected final ReservedStreamingHttpConnection connection;

        protected final CountDownLatch connectionClosed = new CountDownLatch(1);
        protected final CountDownLatch sendResponse = new CountDownLatch(1);
        protected final CountDownLatch responseReceived = new CountDownLatch(1);
        protected final CountDownLatch requestReceived = new CountDownLatch(1);
        protected final CountDownLatch requestPayloadReceived = new CountDownLatch(1);
        protected final AtomicInteger requestPayloadSize = new AtomicInteger();

        protected ConnectionSetup(boolean viaProxy, boolean awaitRequestPayload) throws Exception {
            serverIoExecutor = createIoExecutor(new IoThreadFactory("server-io-executor"));
            HttpServerBuilder serverBuilder = HttpServers.forAddress(localAddress(0))
                    .ioExecutor(serverIoExecutor);

            HostAndPort proxyAddress = null;
            if (viaProxy) {
                // Dummy proxy helps to emulate old intermediate systems that do not support half-closed TCP connections
                proxyTunnel = new ProxyTunnel();
                proxyAddress = proxyTunnel.startProxy();
                serverBuilder.secure().commit(DefaultTestCerts::loadServerPem, DefaultTestCerts::loadServerKey);
            } else {
                proxyTunnel = null;
            }

            serverContext = serverBuilder
                    .listenBlockingStreamingAndAwait((ctx, request, response) -> {
                        requestReceived.countDown();
                        boolean noResponseContent = request.hasQueryParameter("noResponseContent", "true");
                        String content = noResponseContent ? "" : "server_content";
                        response.addHeader(CONTENT_LENGTH, noResponseContent ? ZERO : valueOf(content.length()))
                                .addHeader(CONNECTION, CLOSE);

                        sendResponse.await();
                        try (HttpPayloadWriter<String> writer = response.sendMetaData(textSerializer())) {
                            // Subscribe to the request payload body before response writer closes
                            BlockingIterator<Buffer> iterator = request.payloadBody().iterator();
                            // Consume request payload body asynchronously:
                            ctx.executionContext().executor().execute(() -> {
                                while (iterator.hasNext()) {
                                    Buffer chunk = iterator.next();
                                    assert chunk != null;
                                    requestPayloadSize.addAndGet(chunk.readableBytes());
                                }
                                requestPayloadReceived.countDown();
                            });
                            if (awaitRequestPayload) {
                                requestPayloadReceived.await();
                            }
                            if (!noResponseContent) {
                                // Defer payload body to see how client-side processes "Connection: close" header
                                responseReceived.await();
                            }
                            writer.write(content);
                        }
                    });

            HostAndPort serverAddress = serverHostAndPort(serverContext);
            client = (viaProxy ? HttpClients.forSingleAddressViaProxy(serverAddress, proxyAddress)
                    .secure().disableHostnameVerification()
                    .trustManager(DefaultTestCerts::loadMutualAuthCaPem)
                    .commit() :
                    HttpClients.forSingleAddress(serverAddress))
                    .buildStreaming();
            connection = client.reserveConnection(client.get("/")).toFuture().get();
            connection.onClose().whenFinally(connectionClosed::countDown).subscribe();
        }

        @After
        public void tearDown() throws Exception {
            try {
                newCompositeCloseable().appendAll(connection, client, serverContext, serverIoExecutor).close();
            } finally {
                if (proxyTunnel != null) {
                    safeClose(proxyTunnel);
                }
            }
        }

        protected void assertClosedChannelException(String path) {
            Exception e = assertThrows(ExecutionException.class,
                    () -> connection.request(connection.get(path).addHeader(CONTENT_LENGTH, ZERO)).toFuture().get());
            assertThat(e.getCause(), instanceOf(ClosedChannelException.class));
        }

        protected static void assertResponse(StreamingHttpResponse response) {
            assertThat(response.status(), is(OK));
            assertThat(response.headers().get(CONNECTION), contentEqualTo(CLOSE));
        }

        protected static void assertResponsePayloadBody(StreamingHttpResponse response) throws Exception {
            int actualContentLength = response.payloadBody().map(Buffer::readableBytes)
                    .collect(AtomicInteger::new, (total, current) -> {
                        total.addAndGet(current);
                        return total;
                    }).toFuture().get().get();
            assertThat(response.headers().get(CONTENT_LENGTH), contentEqualTo(valueOf(actualContentLength)));
        }
    }

    @RunWith(Parameterized.class)
    public static class NonPipelinedRequestsTest extends ConnectionSetup {

        private final CountDownLatch responsePayloadReceived = new CountDownLatch(1);

        private final boolean awaitRequestPayload;
        private final boolean awaitResponsePayload;
        private final boolean requestInitiatesClosure;
        private final boolean noRequestContent;
        private final boolean noResponseContent;

        public NonPipelinedRequestsTest(boolean viaProxy, boolean awaitRequestPayload, boolean awaitResponsePayload,
                                        boolean requestInitiatesClosure,
                                        boolean noRequestContent, boolean noResponseContent) throws Exception {
            super(viaProxy, awaitRequestPayload);
            this.awaitRequestPayload = awaitRequestPayload;
            this.awaitResponsePayload = awaitResponsePayload;
            this.requestInitiatesClosure = requestInitiatesClosure;
            this.noRequestContent = noRequestContent;
            this.noResponseContent = noResponseContent;
        }

        @Parameters(name = "{index}: viaProxy={0}, awaitRequestPayload={1}, awaitResponsePayload={2}, " +
                "requestInitiatesClosure={3}, noRequestContent={4}, noResponseContent={5}")
        public static Collection<Boolean[]> data() {
            Collection<Boolean[]> data = new ArrayList<>();
            for (boolean viaProxy : TRUE_FALSE) {
                for (boolean awaitRequestPayload : TRUE_FALSE) {
                    for (boolean awaitResponsePayload : TRUE_FALSE) {
                        if (awaitRequestPayload && awaitResponsePayload) {
                            // Skip configuration that will cause a deadlock.
                            continue;
                        }
                        for (boolean requestInitiatesClosure : TRUE_FALSE) {
                            for (boolean noRequestContent : TRUE_FALSE) {
                                for (boolean noResponseContent : TRUE_FALSE) {
                                    data.add(new Boolean[] {viaProxy, awaitRequestPayload, awaitResponsePayload,
                                            requestInitiatesClosure, noRequestContent, noResponseContent});
                                }
                            }
                        }
                    }
                }
            }
            return data;
        }

        @Test
        public void testConnectionClosure() throws Exception {
            // FIXME: remove the following assume when the NettyPipelinedConnection bug is fixed:
            assumeFalse("Temporarily skip states that unexpectedly cause deadlock",
                    !awaitRequestPayload && awaitResponsePayload && !noRequestContent);
            String content = "request_content";
            StreamingHttpRequest request = connection.newRequest(noRequestContent ? GET : POST, "/first")
                    .setQueryParameter("noResponseContent", valueOf(noResponseContent))
                    .addHeader(CONTENT_LENGTH, noRequestContent ? ZERO : valueOf(content.length()));
            if (!noRequestContent) {
                request.payloadBody(connection.connectionContext().executionContext().executor().submit(() -> {
                    try {
                        responseReceived.await();
                        if (awaitResponsePayload) {
                            responsePayloadReceived.await();
                        }
                    } catch (InterruptedException e) {
                        throwException(e);
                    }
                }).concat(from(content)), textSerializer());
            }
            if (requestInitiatesClosure) {
                request.addHeader(CONNECTION, CLOSE);
            }

            sendResponse.countDown();
            StreamingHttpResponse response = connection.request(request).toFuture().get();
            assertResponse(response);
            responseReceived.countDown();

            assertResponsePayloadBody(response);
            responsePayloadReceived.countDown();
            requestPayloadReceived.await();
            assertThat(request.headers().get(CONTENT_LENGTH), contentEqualTo(valueOf(requestPayloadSize.get())));

            connectionClosed.await();
            assertClosedChannelException("/second");
        }
    }

    @RunWith(Parameterized.class)
    public static class PipelinedRequestsTest extends ConnectionSetup {

        public PipelinedRequestsTest(boolean viaProxy, boolean awaitRequestPayload) throws Exception {
            super(viaProxy, awaitRequestPayload);
        }

        @Parameters(name = "{index}: viaProxy={0}, awaitRequestPayload={1}")
        public static Collection<Boolean[]> data() {
            return asList(
                    new Boolean[] {false, false},
                    new Boolean[] {false, true},
                    new Boolean[] {true, false},
                    new Boolean[] {true, true}
            );
        }

        @Test
        public void serverCloseTwoPipelinedRequestsSentBeforeFirstResponse() throws Exception {
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
            sendResponse.countDown();
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
        public void serverCloseSecondPipelinedRequestWriteAborted() throws Exception {
            AtomicReference<StreamingHttpResponse> firstResponse = new AtomicReference<>();
            AtomicReference<Throwable> secondRequestError = new AtomicReference<>();
            CountDownLatch secondResponseReceived = new CountDownLatch(1);

            connection.request(connection.get("/first")
                    .addHeader(CONTENT_LENGTH, ZERO)).subscribe(first -> {
                firstResponse.set(first);
                responseReceived.countDown();
            });
            String content = "request_content";
            connection.request(connection.get("/second")
                    .addHeader(CONTENT_LENGTH, valueOf(content.length()))
                    .payloadBody(from(content).concat(never()), textSerializer()))
                    .whenOnError(secondRequestError::set)
                    .whenFinally(secondResponseReceived::countDown)
                    .subscribe(second -> { });
            requestReceived.await();
            sendResponse.countDown();
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

            // Send another request before connection reads payload body of the first request:
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
            // Send another request before connection receives a response for the first request:
            assertClosedChannelException("/second");
            sendResponse.countDown();
            responseReceived.await();

            StreamingHttpResponse response = firstResponse.get();
            assertResponse(response);
            assertResponsePayloadBody(response);
            connectionClosed.await();
        }
    }
}
