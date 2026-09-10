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
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.BlockingHttpClient;
import io.servicetalk.http.api.BlockingHttpService;
import io.servicetalk.http.api.BlockingStreamingHttpService;
import io.servicetalk.http.api.DisableInterruptOnCancelHttpServiceFilter;
import io.servicetalk.http.api.HttpPayloadWriter;
import io.servicetalk.http.api.HttpServerBuilder;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.api.StreamingHttpServiceFilter;
import io.servicetalk.http.api.StreamingHttpServiceFilterFactory;
import io.servicetalk.http.utils.TimeoutHttpServiceFilter;
import io.servicetalk.transport.api.ServerContext;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.servicetalk.http.api.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * End-to-end coverage of {@link DisableInterruptOnCancelHttpServiceFilter} over a real client and server. The filter
 * decides only whether the handler thread is interrupted; everything else about the exchange is unchanged.
 * <p>
 * Each cancellation trigger needs a model that it actually reaches. A mid-stream client disconnect and a non-graceful
 * server close both cancel a {@link BlockingStreamingHttpService}, because the server is already writing. An
 * aggregated {@link BlockingHttpService} has written nothing yet, so neither transport event reaches it; a
 * server-side timeout is what cancels an aggregated handler in flight.
 */
class BlockingServiceInterruptOnCancelTest {

    private static final String RAW_REQUEST = "GET / HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n";
    private static final Duration TIMEOUT = Duration.ofMillis(50);
    // Latch waits cost nothing when they fire; this bound only applies when something is broken.
    private static final int AWAIT_S = 5;
    private static final int MAX_CHUNKS = 100;
    private static final int CHUNKS_AFTER_FAILURE = 2;
    private static final long CHUNK_GAP_MILLIS = 10;

    @ParameterizedTest(name = "{displayName} [{index}] disableInterrupt={0}")
    @ValueSource(booleans = {false, true})
    void timeoutDuringAggregatedService(boolean disableInterrupt) throws Exception {
        CountDownLatch cancelObserved = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch handlerFinished = new CountDownLatch(1);
        AtomicBoolean interrupted = new AtomicBoolean();

        BlockingHttpService service = (ctx, request, responseFactory) -> {
            try {
                release.await();
            } catch (InterruptedException e) {
                interrupted.set(true);
            }
            if (Thread.interrupted()) {
                interrupted.set(true);
            }
            handlerFinished.countDown();
            return responseFactory.ok();
        };

        HttpServerBuilder builder = HttpServers.forAddress(localAddress(0))
                .appendServiceFilter(new TimeoutHttpServiceFilter(TIMEOUT, true))
                .appendServiceFilter(cancelObserver(cancelObserved));
        if (disableInterrupt) {
            builder.appendServiceFilter(DisableInterruptOnCancelHttpServiceFilter.INSTANCE);
        }
        ServerContext serverContext = builder.listenBlockingAndAwait(service);
        try (BlockingHttpClient client = HttpClients.forSingleAddress(serverHostAndPort(serverContext))
                .buildBlocking()) {
            assertThat("the timeout must fire while the handler is still running",
                    client.request(client.get("/")).status(), is(INTERNAL_SERVER_ERROR));
            assertThat("the timeout must cancel the response, otherwise a missing interrupt is indistinguishable "
                    + "from a late one", cancelObserved.await(AWAIT_S, SECONDS), is(true));

            release.countDown();
            assertThat("the handler must finish even though the exchange is already over",
                    handlerFinished.await(AWAIT_S, SECONDS), is(true));
            assertThat("the filter must decide whether the handler thread is interrupted", interrupted.get(),
                    is(!disableInterrupt));
        } finally {
            release.countDown();
            serverContext.closeAsync().toFuture().get();
        }
    }

    @ParameterizedTest(name = "{displayName} [{index}] disableInterrupt={0}")
    @ValueSource(booleans = {false, true})
    void timeoutBeforeStreamingResponseMetaData(boolean disableInterrupt) throws Exception {
        CountDownLatch cancelObserved = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch handlerFinished = new CountDownLatch(1);
        AtomicBoolean interrupted = new AtomicBoolean();
        AtomicBoolean writeFailed = new AtomicBoolean();

        BlockingStreamingHttpService service = (ctx, request, response) -> {
            try {
                release.await();
            } catch (InterruptedException e) {
                interrupted.set(true);
            }
            if (Thread.interrupted()) {
                interrupted.set(true);
            }
            HttpPayloadWriter<Buffer> writer = response.sendMetaData();
            try {
                writer.write(ctx.executionContext().bufferAllocator().fromAscii("x"));
                writer.close();
            } catch (IOException e) {
                writeFailed.set(true);
            } finally {
                handlerFinished.countDown();
            }
        };

        HttpServerBuilder builder = HttpServers.forAddress(localAddress(0))
                .appendServiceFilter(new TimeoutHttpServiceFilter(TIMEOUT, true))
                .appendServiceFilter(cancelObserver(cancelObserved));
        if (disableInterrupt) {
            builder.appendServiceFilter(DisableInterruptOnCancelHttpServiceFilter.INSTANCE);
        }
        ServerContext serverContext = builder.listenBlockingStreamingAndAwait(service);
        try (BlockingHttpClient client = HttpClients.forSingleAddress(serverHostAndPort(serverContext))
                .buildBlocking()) {
            assertThat("the timeout must fire before the handler sends meta-data",
                    client.request(client.get("/")).status(), is(INTERNAL_SERVER_ERROR));
            assertThat("the timeout must cancel the response, otherwise a missing interrupt is indistinguishable "
                    + "from a late one", cancelObserved.await(AWAIT_S, SECONDS), is(true));

            release.countDown();
            assertThat("the handler must finish even though the exchange is already over",
                    handlerFinished.await(AWAIT_S, SECONDS), is(true));
            assertThat("a cancel delivered before sendMetaData() must still terminate the payload writer, otherwise "
                    + "the handler parks in write() and never releases its offload thread", writeFailed.get(),
                    is(true));
            assertThat("the filter must decide whether the handler thread is interrupted", interrupted.get(),
                    is(!disableInterrupt));
        } finally {
            release.countDown();
            serverContext.closeAsync().toFuture().get();
        }
    }

    @ParameterizedTest(name = "{displayName} [{index}] disableInterrupt={0}")
    @ValueSource(booleans = {false, true})
    void disconnectDuringStreamingResponse(boolean disableInterrupt) throws Exception {
        CountDownLatch firstChunkSent = new CountDownLatch(1);
        CountDownLatch handlerFinished = new CountDownLatch(1);
        AtomicBoolean interrupted = new AtomicBoolean();
        AtomicBoolean writeFailed = new AtomicBoolean();

        HttpServerBuilder builder = HttpServers.forAddress(localAddress(0));
        if (disableInterrupt) {
            builder.appendServiceFilter(DisableInterruptOnCancelHttpServiceFilter.INSTANCE);
        }
        ServerContext serverContext = builder.listenBlockingStreamingAndAwait(
                chunkingService(firstChunkSent, handlerFinished, interrupted, writeFailed));
        try {
            InetSocketAddress serverAddress = (InetSocketAddress) serverContext.listenAddress();
            try (Socket clientSocket = new Socket(serverAddress.getAddress(), serverAddress.getPort())) {
                clientSocket.setSoLinger(true, 0);
                OutputStream out = clientSocket.getOutputStream();
                out.write(RAW_REQUEST.getBytes(US_ASCII));
                out.flush();
                assertThat("the handler never sent its first chunk", firstChunkSent.await(AWAIT_S, SECONDS), is(true));
            }

            assertThat("the handler must finish regardless of the client having already disconnected",
                    handlerFinished.await(AWAIT_S, SECONDS), is(true));
            assertThat("a write against the abandoned connection must fail whatever the filter decides",
                    writeFailed.get(), is(true));
            assertThat("the filter must decide whether the handler thread is interrupted", interrupted.get(),
                    is(!disableInterrupt));
        } finally {
            serverContext.closeAsync().toFuture().get();
        }
    }

    @ParameterizedTest(name = "{displayName} [{index}] disableInterrupt={0}")
    @ValueSource(booleans = {false, true})
    void nonGracefulServerCloseDuringStreamingResponse(boolean disableInterrupt) throws Exception {
        CountDownLatch firstChunkSent = new CountDownLatch(1);
        CountDownLatch handlerFinished = new CountDownLatch(1);
        AtomicBoolean interrupted = new AtomicBoolean();
        AtomicBoolean writeFailed = new AtomicBoolean();

        HttpServerBuilder builder = HttpServers.forAddress(localAddress(0));
        if (disableInterrupt) {
            builder.appendServiceFilter(DisableInterruptOnCancelHttpServiceFilter.INSTANCE);
        }
        ServerContext serverContext = builder.listenBlockingStreamingAndAwait(
                chunkingService(firstChunkSent, handlerFinished, interrupted, writeFailed));
        try {
            InetSocketAddress serverAddress = (InetSocketAddress) serverContext.listenAddress();
            try (Socket clientSocket = new Socket(serverAddress.getAddress(), serverAddress.getPort())) {
                OutputStream out = clientSocket.getOutputStream();
                out.write(RAW_REQUEST.getBytes(US_ASCII));
                out.flush();
                assertThat("the handler never sent its first chunk", firstChunkSent.await(AWAIT_S, SECONDS),
                        is(true));

                // A non-graceful close kills in-flight connections. Do not wait for it here: it cannot complete
                // until the handler below returns.
                serverContext.closeAsync().subscribe();

                assertThat("the handler must finish even though the server is already closing",
                        handlerFinished.await(AWAIT_S, SECONDS), is(true));
                assertThat("a write against the killed connection must fail whatever the filter decides",
                        writeFailed.get(), is(true));
                assertThat("the filter must decide whether the handler thread is interrupted", interrupted.get(),
                        is(!disableInterrupt));
            }
        } finally {
            serverContext.closeAsync().toFuture().get();
        }
    }

    /**
     * Streams a first chunk to prove the response is under way, then alternates unrelated blocking work with further
     * writes until a write fails. The blocking work is deliberately not an I/O call, so an interrupt observed there
     * cannot be confused with the write failing.
     */
    private static BlockingStreamingHttpService chunkingService(final CountDownLatch firstChunkSent,
                                                               final CountDownLatch handlerFinished,
                                                               final AtomicBoolean interrupted,
                                                               final AtomicBoolean writeFailed) {
        return (ctx, request, response) -> {
            HttpPayloadWriter<Buffer> writer = response.sendMetaData();
            try {
                writer.write(ctx.executionContext().bufferAllocator().fromAscii("first"));
                writer.flush();
                firstChunkSent.countDown();
                int chunksAfterFailure = 0;
                for (int i = 0; i < MAX_CHUNKS && chunksAfterFailure < CHUNKS_AFTER_FAILURE; i++) {
                    if (sleepWasInterrupted(CHUNK_GAP_MILLIS) || Thread.interrupted()) {
                        interrupted.set(true);
                    }
                    try {
                        writer.write(ctx.executionContext().bufferAllocator().fromAscii("x"));
                        writer.flush();
                    } catch (IOException e) {
                        writeFailed.set(true);
                    }
                    if (writeFailed.get()) {
                        chunksAfterFailure++;
                    }
                }
                writer.close();
            } catch (IOException e) {
                writeFailed.set(true);
            } finally {
                handlerFinished.countDown();
            }
        };
    }

    private static StreamingHttpServiceFilterFactory cancelObserver(final CountDownLatch cancelObserved) {
        return delegate -> new StreamingHttpServiceFilter(delegate) {
            @Override
            public Single<StreamingHttpResponse> handle(final HttpServiceContext ctx,
                                                        final StreamingHttpRequest request,
                                                        final StreamingHttpResponseFactory responseFactory) {
                return delegate().handle(ctx, request, responseFactory).afterCancel(cancelObserved::countDown);
            }
        };
    }

    private static boolean sleepWasInterrupted(final long millis) {
        try {
            Thread.sleep(millis);
            return false;
        } catch (InterruptedException e) {
            return true;
        }
    }
}
