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
import io.servicetalk.concurrent.api.TestPublisher;
import io.servicetalk.http.api.Http2ErrorCode;
import io.servicetalk.http.api.Http2Exception;
import io.servicetalk.http.api.SingleAddressHttpClientBuilder;
import io.servicetalk.http.api.StreamingHttpClient;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.api.ServerContext;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.DefaultHttp2HeadersFrame;
import io.netty.handler.codec.http2.DefaultHttp2ResetFrame;
import io.netty.handler.codec.http2.Http2HeadersFrame;
import io.netty.handler.codec.http2.Http2ResetFrame;
import io.netty.handler.codec.http2.Http2StreamFrame;
import io.netty.util.ReferenceCountUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Supplier;
import javax.annotation.Nullable;

import static io.netty.handler.codec.http2.Http2Error.CANCEL;
import static io.netty.handler.codec.http2.Http2Error.NO_ERROR;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.concurrent.internal.TestTimeoutConstants.DEFAULT_TIMEOUT_SECONDS;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_LENGTH;
import static io.servicetalk.http.api.HttpHeaderNames.EXPECT;
import static io.servicetalk.http.api.HttpHeaderNames.TRANSFER_ENCODING;
import static io.servicetalk.http.api.HttpHeaderValues.CHUNKED;
import static io.servicetalk.http.api.HttpHeaderValues.CONTINUE;
import static io.servicetalk.http.api.HttpResponseStatus.EXPECTATION_FAILED;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.netty.HttpProtocolConfigs.h2Default;
import static io.servicetalk.http.netty.RawH2Peers.awaitFinalResponse;
import static io.servicetalk.http.netty.RawH2Peers.requestHeaders;
import static io.servicetalk.http.netty.RawH2Peers.streamClosedObserver;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An early final response to an {@code Expect: 100-continue} request leaves the request side of the stream unfinished,
 * because the client never sends the body. Each peer must still end the stream, or it holds a
 * {@code SETTINGS_MAX_CONCURRENT_STREAMS} slot until the connection closes.
 */
class H2ExpectContinueRejectionTest {

    private static final String DECLARED_LENGTH = "10";
    private static final String REJECTION_BODY = "rejected";

    private final RawH2Peers peers = new RawH2Peers();

    @AfterEach
    void closePeers() throws Exception {
        peers.close();
    }

    // A smoke test: either peer's reset alone closes both streams here, so the tests with a raw peer below are the ones
    // that isolate the client fix and the server fix.
    @Test
    void earlyRejectionClosesStreamOnClientAndServer() throws Exception {
        CountDownLatch clientStreamClosed = new CountDownLatch(1);
        CountDownLatch serverStreamClosed = new CountDownLatch(1);
        try (ServerContext server = HttpServers.forAddress(localAddress(0))
                .protocols(h2Default())
                .transportObserver(streamClosedObserver(serverStreamClosed))
                // The service rejects without subscribing to the request payload body.
                .listenStreamingAndAwait((ctx, request, factory) -> succeeded(factory.expectationFailed()));
             StreamingHttpClient client = h2Client(server.listenAddress())
                     .appendConnectionFactoryFilter(new TransportObserverConnectionFactoryFilter<>(
                             streamClosedObserver(clientStreamClosed)))
                     .buildStreaming()) {
            // The body never emits, like a client that has nothing to send once the server rejected the request.
            TestPublisher<Buffer> payload = new TestPublisher.Builder<Buffer>().singleSubscriber().build();
            StreamingHttpResponse response = client.request(client.post("/")
                    .setHeader(EXPECT, CONTINUE)
                    .setHeader(CONTENT_LENGTH, DECLARED_LENGTH)
                    .payloadBody(payload)).toFuture().get();
            assertThat(response.status(), is(EXPECTATION_FAILED));
            response.messageBody().ignoreElements().toFuture().get();

            assertTrue(clientStreamClosed.await(DEFAULT_TIMEOUT_SECONDS, SECONDS));
            assertTrue(serverStreamClosed.await(DEFAULT_TIMEOUT_SECONDS, SECONDS));
            assertThat(payload.isSubscribed(), is(false));
        }
    }

    @Test
    void earlyRejectionOfRequestThatNeverCompletesResetsStreamWithNoError() throws Exception {
        try (ServerContext server = HttpServers.forAddress(localAddress(0))
                .protocols(h2Default())
                .listenStreamingAndAwait((ctx, request, factory) -> succeeded(factory.expectationFailed()))) {
            // A peer that sends the headers and then neither the body nor a reset: only the server can end the stream.
            BlockingQueue<Http2StreamFrame> frames = new LinkedBlockingQueue<>();
            peers.openStream(peers.connect(server.listenAddress()), frames).writeAndFlush(
                    new DefaultHttp2HeadersFrame(requestHeaders("POST", "/")
                            .add("expect", "100-continue").add("content-length", DECLARED_LENGTH), false)).sync();

            List<Http2StreamFrame> received = new ArrayList<>();
            Http2ResetFrame reset = awaitResetFrame(frames, received);
            assertThat("No RST_STREAM after frames " + received, reset, is(notNullValue()));
            assertThat(reset.errorCode(), is(NO_ERROR.code()));
        }
    }

    @ParameterizedTest(name = "{displayName} [{index}] {0}")
    @EnumSource(NoResetCase.class)
    void serverDoesNotResetStreamWhen(NoResetCase noResetCase) throws Exception {
        try (ServerContext server = HttpServers.forAddress(localAddress(0))
                .protocols(h2Default())
                .listenStreamingAndAwait((ctx, request, factory) -> {
                    switch (request.requestTarget()) {
                        case "/ok":
                            return succeeded(factory.ok());
                        case "/continue":
                            // Reading the request body makes the server write 100 (Continue) first.
                            request.messageBody().ignoreElements().subscribe();
                            return succeeded(factory.expectationFailed());
                        default:
                            return succeeded(factory.expectationFailed());
                    }
                })) {
            Channel connection = peers.connect(server.listenAddress());
            BlockingQueue<Http2StreamFrame> frames = new LinkedBlockingQueue<>();
            peers.openStream(connection, frames).writeAndFlush(new DefaultHttp2HeadersFrame(
                    noResetCase.withExpect ? requestHeaders("POST", noResetCase.path).add("expect", "100-continue") :
                            requestHeaders("POST", noResetCase.path), noResetCase.endStream)).sync();
            List<Http2StreamFrame> received = new ArrayList<>();
            awaitFinalResponse(frames, received);

            // Frames of one connection arrive in the order the server wrote them, so a reset of the first stream
            // that the server was going to send would be ahead of the response to this second request.
            BlockingQueue<Http2StreamFrame> fenceFrames = new LinkedBlockingQueue<>();
            peers.openStream(connection, fenceFrames).writeAndFlush(
                    new DefaultHttp2HeadersFrame(requestHeaders("GET", "/ok"), true)).sync();
            awaitFinalResponse(fenceFrames, new ArrayList<>());

            frames.drainTo(received);
            assertThat("Unexpected RST_STREAM among " + received,
                    received.stream().anyMatch(f -> f instanceof Http2ResetFrame), is(false));
            assertThat("Precondition: 100 (Continue) before the final response in " + received,
                    received.stream().anyMatch(H2ExpectContinueRejectionTest::isContinueResponse),
                    is(noResetCase == NoResetCase.CONTINUE_ALREADY_SENT));
        }
    }

    @Test
    void clientDoesNotResetStreamWhenRequestAlreadyEnded() throws Exception {
        BlockingQueue<Http2ResetFrame> resets = new LinkedBlockingQueue<>();
        Channel server = peers.bindServer(respondWith("417", resets));
        try (StreamingHttpClient client = h2Client(server.localAddress()).buildStreaming()) {
            // No body: the request ends with its HEADERS frame, so there is nothing left to reset.
            StreamingHttpResponse response = client.request(client.post("/").setHeader(EXPECT, CONTINUE))
                    .toFuture().get();
            assertThat(response.status(), is(EXPECTATION_FAILED));
            response.messageBody().ignoreElements().toFuture().get();

            assertNoResetAfterFenceRequest(client, resets);
        }
    }

    @Test
    void clientDoesNotResetStreamWhenFinalResponseIsSuccessful() throws Exception {
        BlockingQueue<Http2ResetFrame> resets = new LinkedBlockingQueue<>();
        Channel server = peers.bindServer(respondWith("200", resets));
        try (StreamingHttpClient client = h2Client(server.localAddress()).buildStreaming()) {
            // A 2xx tells the client to send the body, so the request ends by itself and nothing needs a reset.
            StreamingHttpResponse response = client.request(client.post("/")
                    .setHeader(EXPECT, CONTINUE)
                    .setHeader(TRANSFER_ENCODING, CHUNKED)
                    .payloadBody(from(client.executionContext().bufferAllocator().fromAscii("body"))))
                    .toFuture().get();
            assertThat(response.status(), is(OK));
            response.messageBody().ignoreElements().toFuture().get();

            assertNoResetAfterFenceRequest(client, resets);
        }
    }

    @Test
    void rejectionBodyReadAfterServerResetsStreamIsDelivered() throws Exception {
        CountDownLatch serverStreamClosed = new CountDownLatch(1);
        try (ServerContext server = HttpServers.forAddress(localAddress(0))
                .protocols(h2Default())
                .transportObserver(streamClosedObserver(serverStreamClosed))
                .listenStreamingAndAwait((ctx, request, factory) -> succeeded(factory.expectationFailed()
                        .payloadBody(from(ctx.executionContext().bufferAllocator().fromAscii(REJECTION_BODY)))));
             StreamingHttpClient client = h2Client(server.listenAddress()).buildStreaming()) {
            StreamingHttpResponse response = client.request(client.post("/")
                    .setHeader(EXPECT, CONTINUE)
                    .setHeader(CONTENT_LENGTH, DECLARED_LENGTH)
                    .payloadBody(new TestPublisher.Builder<Buffer>().singleSubscriber().build())).toFuture().get();
            assertThat(response.status(), is(EXPECTATION_FAILED));
            // The server resets the stream after its complete response, while the client has yet to read the body.
            assertTrue(serverStreamClosed.await(DEFAULT_TIMEOUT_SECONDS, SECONDS));

            assertThat(response.toResponse().toFuture().get().payloadBody().toString(US_ASCII),
                    is(REJECTION_BODY));
        }
    }

    @ParameterizedTest(name = "{displayName} [{index}] withContentLength={0}")
    @ValueSource(booleans = {true, false})
    void clientResetsStreamWithCancelAfterRejectionResponseEnds(boolean withContentLength) throws Exception {
        BlockingQueue<Http2ResetFrame> resets = new LinkedBlockingQueue<>();
        // A peer that rejects and then never resets: only the client can end the stream.
        Channel server = peers.bindServer(respondWith("417", resets));
        try (StreamingHttpClient client = h2Client(server.localAddress()).buildStreaming()) {
            TestPublisher<Buffer> payload = new TestPublisher.Builder<Buffer>().singleSubscriber().build();
            StreamingHttpResponse response = client.request(client.post("/")
                    .setHeader(EXPECT, CONTINUE)
                    // A declared Content-Length forbids ending the request early with END_STREAM (RFC 9113 8.1.1).
                    .setHeader(withContentLength ? CONTENT_LENGTH : TRANSFER_ENCODING,
                            withContentLength ? DECLARED_LENGTH : CHUNKED)
                    .payloadBody(payload)).toFuture().get();
            assertThat(response.status(), is(EXPECTATION_FAILED));
            response.messageBody().ignoreElements().toFuture().get();

            Http2ResetFrame reset = resets.poll(DEFAULT_TIMEOUT_SECONDS, SECONDS);
            assertThat("No RST_STREAM from the client", reset, is(notNullValue()));
            assertThat(reset.errorCode(), is(CANCEL.code()));
            assertThat(payload.isSubscribed(), is(false));
        }
    }

    @Test
    void clientFailsResponseThatNoErrorResetCutShort() throws Exception {
        // A peer that starts a response and then resets the stream with NO_ERROR before ending it.
        Channel server = peers.bindServer(() -> new ChannelInboundHandlerAdapter() {
            @Override
            public void channelRead(final ChannelHandlerContext ctx, final Object msg) {
                try {
                    if (msg instanceof Http2HeadersFrame) {
                        ctx.writeAndFlush(new DefaultHttp2HeadersFrame(new DefaultHttp2Headers(false).status("200"),
                                false)).addListener(f -> ctx.writeAndFlush(new DefaultHttp2ResetFrame(NO_ERROR)));
                    }
                } finally {
                    ReferenceCountUtil.release(msg);
                }
            }
        });
        try (StreamingHttpClient client = h2Client(server.localAddress()).buildStreaming()) {
            // The client holds back NO_ERROR until the stream closes, then must fail a message that is incomplete.
            ExecutionException e = assertThrows(ExecutionException.class, () -> client.request(client.get("/"))
                    .flatMapPublisher(StreamingHttpResponse::messageBody).ignoreElements().toFuture().get());
            assertThat(e.getCause(), instanceOf(Http2Exception.class));
            assertThat(((Http2Exception) e.getCause()).errorCode(), is(Http2ErrorCode.NO_ERROR));
        }
    }

    private enum NoResetCase {
        WITHOUT_EXPECT("/", false, false),
        CONTINUE_ALREADY_SENT("/continue", true, false),
        SUCCESSFUL_RESPONSE("/ok", true, false),
        REQUEST_ALREADY_ENDED("/", true, true);

        final String path;
        final boolean withExpect;
        final boolean endStream;

        NoResetCase(final String path, final boolean withExpect, final boolean endStream) {
            this.path = path;
            this.withExpect = withExpect;
            this.endStream = endStream;
        }
    }

    private static SingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> h2Client(
            final SocketAddress address) {
        return HttpClients.forSingleAddress(serverHostAndPort(address))
                .protocols(h2Default())
                // Keep the client waiting for 100 (Continue), however slow this machine is.
                .expectContinueTimeout(Duration.ofSeconds(DEFAULT_TIMEOUT_SECONDS));
    }

    private static void assertNoResetAfterFenceRequest(final StreamingHttpClient client,
                                                       final BlockingQueue<Http2ResetFrame> resets) throws Exception {
        // Both requests share the connection, so a reset that the client was going to send for the first stream is
        // already at the server when it answers this one.
        client.request(client.get("/fence")).toFuture().get().messageBody().ignoreElements().toFuture().get();
        assertThat("Unexpected RST_STREAM from the client", resets, is(empty()));
    }

    /**
     * Answers each request with {@code status} and END_STREAM, except {@code /fence} which gets a 200, and records the
     * resets it receives.
     */
    private static Supplier<ChannelHandler> respondWith(final String status,
                                                        final BlockingQueue<Http2ResetFrame> resets) {
        return () -> new ChannelInboundHandlerAdapter() {
            @Override
            public void channelRead(final ChannelHandlerContext ctx, final Object msg) {
                try {
                    if (msg instanceof Http2HeadersFrame) {
                        final CharSequence path = ((Http2HeadersFrame) msg).headers().path();
                        ctx.writeAndFlush(new DefaultHttp2HeadersFrame(new DefaultHttp2Headers(false)
                                .status("/fence".contentEquals(path == null ? "" : path) ? "200" : status)
                                .add("content-length", "0"), true));
                    }
                } finally {
                    ReferenceCountUtil.release(msg);
                }
            }

            @Override
            public void userEventTriggered(final ChannelHandlerContext ctx, final Object evt) {
                if (evt instanceof Http2ResetFrame) {
                    resets.add((Http2ResetFrame) evt);
                }
                ctx.fireUserEventTriggered(evt);
            }
        };
    }

    private static boolean isContinueResponse(final Http2StreamFrame frame) {
        return frame instanceof Http2HeadersFrame &&
                "100".contentEquals(((Http2HeadersFrame) frame).headers().status());
    }

    /**
     * Collects frames into {@code received} until a reset arrives. Returns {@code null} if none arrives in time, which
     * is bounded below the test timeout so the failure can report what did arrive.
     */
    @Nullable
    private static Http2ResetFrame awaitResetFrame(final BlockingQueue<Http2StreamFrame> frames,
                                                   final List<Http2StreamFrame> received) throws InterruptedException {
        for (;;) {
            Http2StreamFrame frame = frames.poll(DEFAULT_TIMEOUT_SECONDS / 2, SECONDS);
            if (frame == null) {
                return null;
            }
            received.add(frame);
            if (frame instanceof Http2ResetFrame) {
                return (Http2ResetFrame) frame;
            }
        }
    }
}
