/*
 * Copyright © 2026 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.client.api.TransportObserverConnectionFactoryFilter;
import io.servicetalk.concurrent.BlockingIterator;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.BlockingStreamingHttpClient;
import io.servicetalk.http.api.BlockingStreamingHttpResponse;
import io.servicetalk.http.api.FilterableStreamingHttpConnection;
import io.servicetalk.http.api.Http2Exception;
import io.servicetalk.http.api.Http2SettingsBuilder;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.ReservedStreamingHttpConnection;
import io.servicetalk.http.api.SingleAddressHttpClientBuilder;
import io.servicetalk.http.api.StreamingHttpClient;
import io.servicetalk.http.api.StreamingHttpConnectionFilter;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.transport.api.ConnectionInfo;
import io.servicetalk.transport.api.ConnectionObserver;
import io.servicetalk.transport.api.ConnectionObserver.DataObserver;
import io.servicetalk.transport.api.ConnectionObserver.MultiplexedObserver;
import io.servicetalk.transport.api.ConnectionObserver.ReadObserver;
import io.servicetalk.transport.api.ConnectionObserver.StreamObserver;
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.api.TransportObserver;

import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Completable.never;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.concurrent.internal.TestTimeoutConstants.DEFAULT_TIMEOUT_SECONDS;
import static io.servicetalk.http.api.Http2ErrorCode.CANCEL;
import static io.servicetalk.http.api.HttpExecutionStrategies.defaultStrategy;
import static io.servicetalk.http.api.HttpExecutionStrategies.offloadAll;
import static io.servicetalk.http.api.HttpExecutionStrategies.offloadNone;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.netty.HttpProtocolConfigs.h1;
import static io.servicetalk.http.netty.HttpProtocolConfigs.h2;
import static io.servicetalk.http.netty.HttpProtocolConfigs.h2Default;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static java.net.InetAddress.getLoopbackAddress;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.util.Collections.singletonList;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Named.named;

class PartiallyWrittenRequestCancelTest {

    private static final String PARTIAL_BODY = "partial";
    private static final int MAX_CONCURRENT_STREAMS = 2;

    @ParameterizedTest(name = "{displayName} [{index}] {0}")
    @MethodSource("strategies")
    void cancelAfterEarlyH2ResponseWhileBodyIsWritingResetsStream(HttpExecutionStrategy strategy) throws Exception {
        CountDownLatch responseRead = new CountDownLatch(1);
        CountDownLatch streamClosed = new CountDownLatch(1);
        CompletableFuture<Throwable> serverRequestBodyError = new CompletableFuture<>();
        try (ServerContext server = HttpServers.forAddress(localAddress(0)).protocols(h2Default())
                .listenStreamingAndAwait((ctx, request, factory) -> {
                    request.messageBody().ignoreElements().whenOnError(serverRequestBodyError::complete).subscribe();
                    return succeeded(factory.ok());
                });
             StreamingHttpClient client = HttpClients.forSingleAddress(serverHostAndPort(server))
                     .protocols(h2Default())
                     .executionStrategy(strategy)
                     .appendConnectionFactoryFilter(new TransportObserverConnectionFactoryFilter<>(
                             observer(() -> { }, responseRead::countDown, streamClosed::countDown)))
                     .buildStreaming()) {
            StreamingHttpResponse response = client.request(client.post("/")
                    .payloadBody(neverEndingBody(client))).toFuture().get();
            // The transport has read the whole response, so only the write side can reset the stream.
            assertTrue(responseRead.await(DEFAULT_TIMEOUT_SECONDS, SECONDS));

            assertTrue(response.messageBody().ignoreElements().toFuture().cancel(true));
            assertTrue(streamClosed.await(DEFAULT_TIMEOUT_SECONDS, SECONDS));
            Throwable cause = serverRequestBodyError.get();
            assertThat(cause, instanceOf(Http2Exception.class));
            assertThat(((Http2Exception) cause).errorCode(), is(CANCEL));
        }
    }

    @ParameterizedTest(name = "{displayName} [{index}] requestReplaced={0}")
    @ValueSource(booleans = {false, true})
    void cancelAfterEarlyH2ResponseWhileBodyIsWritingFreesStreamSlot(boolean requestReplaced) throws Exception {
        Semaphore responsesRead = new Semaphore(0);
        AtomicInteger connections = new AtomicInteger();
        try (ServerContext server = HttpServers.forAddress(localAddress(0))
                .protocols(h2().initialSettings(new Http2SettingsBuilder()
                        .maxConcurrentStreams(MAX_CONCURRENT_STREAMS).build()).build())
                .listenStreamingAndAwait((ctx, request, factory) -> succeeded(factory.ok()))) {
            SingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> builder =
                    HttpClients.forSingleAddress(serverHostAndPort(server))
                            .protocols(h2Default())
                            .appendConnectionFactoryFilter(new TransportObserverConnectionFactoryFilter<>(
                                    observer(connections::incrementAndGet, responsesRead::release, () -> { })));
            if (requestReplaced) {
                // A new request lacks the original's context, so the client frees its stream slot on cancel, not on
                // stream close, and a stream left open counts only against Netty's limit.
                builder.appendConnectionFilter(ReplaceRequestFilter::new);
            }
            try (StreamingHttpClient client = builder.buildStreaming()) {
                for (int i = 0; i < MAX_CONCURRENT_STREAMS; i++) {
                    StreamingHttpResponse response = client.request(client.post("/")
                            .payloadBody(neverEndingBody(client))).toFuture().get();
                    assertTrue(responsesRead.tryAcquire(DEFAULT_TIMEOUT_SECONDS, SECONDS));
                    assertTrue(response.messageBody().ignoreElements().toFuture().cancel(true));
                }

                StreamingHttpResponse response = client.request(client.get("/")).toFuture().get();
                assertThat(response.status(), is(OK));
                response.messageBody().ignoreElements().toFuture().get();
                assertThat(connections.get(), is(1));
            }
        }
    }

    @Test
    void cancelAfterEarlyH2ResponseOnReservedConnectionResetsOnlyTheStream() throws Exception {
        Semaphore responsesRead = new Semaphore(0);
        CompletableFuture<Throwable> serverRequestBodyError = new CompletableFuture<>();
        try (ServerContext server = HttpServers.forAddress(localAddress(0)).protocols(h2Default())
                .listenStreamingAndAwait((ctx, request, factory) -> {
                    request.messageBody().ignoreElements().whenOnError(serverRequestBodyError::complete).subscribe();
                    return succeeded(factory.ok());
                });
             StreamingHttpClient client = HttpClients.forSingleAddress(serverHostAndPort(server))
                     .protocols(h2Default())
                     .appendConnectionFactoryFilter(new TransportObserverConnectionFactoryFilter<>(
                             observer(() -> { }, responsesRead::release, () -> { })))
                     .buildStreaming()) {
            ReservedStreamingHttpConnection connection =
                    client.reserveConnection(client.get("/")).toFuture().get();
            try {
                StreamingHttpResponse response = connection.request(connection.post("/")
                        .payloadBody(neverEndingBody(client))).toFuture().get();
                assertTrue(responsesRead.tryAcquire(DEFAULT_TIMEOUT_SECONDS, SECONDS));

                assertTrue(response.messageBody().ignoreElements().toFuture().cancel(true));
                Throwable cause = serverRequestBodyError.get();
                assertThat(cause, instanceOf(Http2Exception.class));
                assertThat(((Http2Exception) cause).errorCode(), is(CANCEL));

                StreamingHttpResponse next = connection.request(connection.get("/")).toFuture().get();
                assertThat(next.status(), is(OK));
                next.messageBody().ignoreElements().toFuture().get();
            } finally {
                connection.releaseAsync().toFuture().get();
            }
        }
    }

    @Test
    void closingDrainedH2ResponseIteratorKeepsConnection() throws Exception {
        AtomicInteger connections = new AtomicInteger();
        try (ServerContext server = HttpServers.forAddress(localAddress(0)).protocols(h2Default())
                .listenStreamingAndAwait((ctx, request, factory) -> request.messageBody().ignoreElements()
                        .concat(succeeded(factory.ok().payloadBody(Publisher.from(
                                ctx.executionContext().bufferAllocator().fromAscii(PARTIAL_BODY))))));
             BlockingStreamingHttpClient client = HttpClients.forSingleAddress(serverHostAndPort(server))
                     .protocols(h2Default())
                     .appendConnectionFactoryFilter(new TransportObserverConnectionFactoryFilter<>(
                             observer(connections::incrementAndGet, () -> { }, () -> { })))
                     .buildBlockingStreaming()) {
            for (int i = 0; i < 2; i++) {
                BlockingStreamingHttpResponse response = client.request(client.post("/").payloadBody(
                        singletonList(client.executionContext().bufferAllocator().fromAscii(PARTIAL_BODY))));
                // Closing the iterator always cancels, here after the exchange has already completed.
                try (BlockingIterator<Buffer> iterator = response.payloadBody().iterator()) {
                    while (iterator.hasNext()) {
                        iterator.next();
                    }
                }
            }
            assertThat(connections.get(), is(1));
        }
    }

    @Test
    void cancelWhileH2ResponseIsStreamingResetsOnlyTheStream() throws Exception {
        CountDownLatch serverResponseCancelled = new CountDownLatch(1);
        AtomicInteger connections = new AtomicInteger();
        try (ServerContext server = HttpServers.forAddress(localAddress(0)).protocols(h2Default())
                .listenStreamingAndAwait((ctx, request, factory) -> succeeded("/streaming".equals(request.path()) ?
                        factory.ok().payloadBody(Publisher.from(
                                ctx.executionContext().bufferAllocator().fromAscii(PARTIAL_BODY))
                                .concat(never()).whenCancel(serverResponseCancelled::countDown)) :
                        factory.ok()));
             StreamingHttpClient client = HttpClients.forSingleAddress(serverHostAndPort(server))
                     .protocols(h2Default())
                     .appendConnectionFactoryFilter(new TransportObserverConnectionFactoryFilter<>(
                             observer(connections::incrementAndGet, () -> { }, () -> { })))
                     .buildStreaming()) {
            StreamingHttpResponse response = client.request(client.get("/streaming")).toFuture().get();
            // Cancels after the first chunk.
            response.payloadBody().takeAtMost(1).ignoreElements().toFuture().get();
            assertTrue(serverResponseCancelled.await(DEFAULT_TIMEOUT_SECONDS, SECONDS));

            StreamingHttpResponse next = client.request(client.get("/")).toFuture().get();
            assertThat(next.status(), is(OK));
            next.messageBody().ignoreElements().toFuture().get();
            assertThat(connections.get(), is(1));
        }
    }

    @Test
    void cancelMidH1BodyDoesNotLetQueuedRequestEndTruncatedBody() throws Exception {
        try (RawServer server = new RawServer();
             StreamingHttpClient client = HttpClients.forSingleAddress(serverHostAndPort(server.address()))
                     .protocols(h1().maxPipelinedRequests(3).build())
                     // Subscribes on the caller thread, so C is queued behind B's write before B is cancelled.
                     .executionStrategy(offloadNone())
                     .buildStreaming()) {
            ReservedStreamingHttpConnection connection =
                    client.reserveConnection(client.get("/reserve")).toFuture().get();
            try {
                connection.request(connection.get("/a")).toFuture();
                server.awaitReceived("GET /a");
                Future<StreamingHttpResponse> b = connection.request(connection.post("/b")
                        .payloadBody(neverEndingBody(client))).toFuture();
                server.awaitReceived(PARTIAL_BODY);
                connection.request(connection.get("/c")).toFuture();

                assertTrue(b.cancel(true));
                // Encoding C's headers ends B's chunked body, so the server would take the truncated B as complete.
                String received = server.awaitClosedOrReceived("GET /c");
                assertThat(received, not(containsString("GET /c")));
                assertThat(server.isClosed(), is(true));
            } finally {
                connection.closeAsync().toFuture().get();
            }
        }
    }

    @Test
    void cancelAfterEarlyH1ResponseWhileBodyIsWritingClosesConnection() throws Exception {
        CountDownLatch responseRead = new CountDownLatch(1);
        try (RawServer server = new RawServer();
             StreamingHttpClient client = HttpClients.forSingleAddress(serverHostAndPort(server.address()))
                     .appendConnectionFactoryFilter(new TransportObserverConnectionFactoryFilter<>(
                             observer(() -> { }, responseRead::countDown, () -> { })))
                     .buildStreaming()) {
            ReservedStreamingHttpConnection connection =
                    client.reserveConnection(client.get("/reserve")).toFuture().get();
            try {
                Future<StreamingHttpResponse> responseFuture = connection.request(connection.post("/b")
                        .payloadBody(neverEndingBody(client))).toFuture();
                server.awaitReceived(PARTIAL_BODY);
                server.write("HTTP/1.1 200 OK\r\ncontent-length: 0\r\n\r\n");
                StreamingHttpResponse response = responseFuture.get();
                // The transport has read the whole response, so it ignores the cancel below.
                assertTrue(responseRead.await(DEFAULT_TIMEOUT_SECONDS, SECONDS));

                assertTrue(response.messageBody().ignoreElements().toFuture().cancel(true));
                connection.onClose().toFuture().get();
            } finally {
                connection.closeAsync().toFuture().get();
            }
        }
    }

    private static final class ReplaceRequestFilter extends StreamingHttpConnectionFilter {
        ReplaceRequestFilter(FilterableStreamingHttpConnection delegate) {
            super(delegate);
        }

        @Override
        public Single<StreamingHttpResponse> request(StreamingHttpRequest request) {
            StreamingHttpRequest replacement = delegate().newRequest(request.method(), request.requestTarget())
                    .payloadBody(request.payloadBody());
            replacement.headers().add(request.headers());
            return delegate().request(replacement);
        }
    }

    private static Stream<Named<HttpExecutionStrategy>> strategies() {
        return Stream.of(named("offloadNone", offloadNone()), named("defaultStrategy", defaultStrategy()),
                named("offloadAll", offloadAll()));
    }

    private static Publisher<Buffer> neverEndingBody(StreamingHttpClient client) {
        return Publisher.from(client.executionContext().bufferAllocator().fromAscii(PARTIAL_BODY)).concat(never());
    }

    private static TransportObserver observer(Runnable onConnection, Runnable onReadComplete,
                                              Runnable onStreamClosed) {
        ReadObserver readObserver = new ReadObserver() {
            @Override
            public void readComplete() {
                onReadComplete.run();
            }
        };
        DataObserver dataObserver = new DataObserver() {
            @Override
            public ReadObserver onNewRead() {
                return readObserver;
            }
        };
        StreamObserver streamObserver = new StreamObserver() {
            @Override
            public DataObserver streamEstablished() {
                return dataObserver;
            }

            @Override
            public void streamClosed(Throwable error) {
                onStreamClosed.run();
            }

            @Override
            public void streamClosed() {
                onStreamClosed.run();
            }
        };
        MultiplexedObserver multiplexedObserver = new MultiplexedObserver() {
            @Override
            public StreamObserver onNewStream() {
                return streamObserver;
            }
        };
        ConnectionObserver connectionObserver = new ConnectionObserver() {
            @Override
            public DataObserver connectionEstablished(ConnectionInfo info) {
                onConnection.run();
                return dataObserver;
            }

            @Override
            public MultiplexedObserver multiplexedConnectionEstablished(ConnectionInfo info) {
                onConnection.run();
                return multiplexedObserver;
            }
        };
        return (localAddress, remoteAddress) -> connectionObserver;
    }

    /**
     * Accepts one connection, records every byte it receives, and writes only what the test tells it to.
     */
    private static final class RawServer implements AutoCloseable {
        private final ServerSocket serverSocket;
        private final Thread acceptor;
        private final StringBuilder received = new StringBuilder();
        @Nullable
        private Socket socket;
        private boolean closed;

        RawServer() throws IOException {
            serverSocket = new ServerSocket(0, 50, getLoopbackAddress());
            acceptor = new Thread(this::readConnection, "raw-server");
            acceptor.start();
        }

        SocketAddress address() {
            return serverSocket.getLocalSocketAddress();
        }

        private void readConnection() {
            try (Socket accepted = serverSocket.accept()) {
                synchronized (this) {
                    socket = accepted;
                }
                InputStream in = accepted.getInputStream();
                byte[] buffer = new byte[1024];
                int read;
                while ((read = in.read(buffer)) >= 0) {
                    synchronized (this) {
                        received.append(new String(buffer, 0, read, US_ASCII));
                        notifyAll();
                    }
                }
            } catch (IOException closedOrReset) {
                // Either way the connection is over.
            } finally {
                synchronized (this) {
                    closed = true;
                    notifyAll();
                }
            }
        }

        synchronized void write(String data) throws IOException {
            // Called only once the server has received data, so the connection has been accepted.
            OutputStream out = requireNonNull(socket).getOutputStream();
            out.write(data.getBytes(US_ASCII));
            out.flush();
        }

        synchronized void awaitReceived(String data) throws Exception {
            awaitReceived(data, false);
        }

        synchronized String awaitClosedOrReceived(String data) throws Exception {
            awaitReceived(data, true);
            return received.toString();
        }

        synchronized boolean isClosed() {
            return closed;
        }

        private void awaitReceived(String data, boolean orClosed) throws Exception {
            final long deadline = System.nanoTime() + SECONDS.toNanos(DEFAULT_TIMEOUT_SECONDS);
            while (received.indexOf(data) < 0 && !(orClosed && closed)) {
                final long remainingMillis = (deadline - System.nanoTime()) / 1_000_000;
                if (remainingMillis <= 0) {
                    throw new TimeoutException("Did not receive " + data + "; received so far: " + received);
                }
                wait(remainingMillis);
            }
        }

        @Override
        public void close() throws Exception {
            serverSocket.close();
            synchronized (this) {
                if (socket != null) {
                    socket.close();
                }
            }
            acceptor.join();
        }
    }
}
