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
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.http.api.HttpResponseFactory;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.netty.HeadResponseTestUtils.ServerStarter;
import io.servicetalk.transport.api.ServerContext;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.DefaultHttp2HeadersFrame;
import io.netty.handler.codec.http2.Http2DataFrame;
import io.netty.handler.codec.http2.Http2FrameCodecBuilder;
import io.netty.handler.codec.http2.Http2GoAwayFrame;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2HeadersFrame;
import io.netty.handler.codec.http2.Http2MultiplexHandler;
import io.netty.handler.codec.http2.Http2ResetFrame;
import io.netty.handler.codec.http2.Http2StreamChannel;
import io.netty.handler.codec.http2.Http2StreamChannelBootstrap;
import io.netty.util.ReferenceCountUtil;
import org.hamcrest.Matcher;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Stream;
import javax.annotation.Nullable;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.concurrent.internal.TestTimeoutConstants.DEFAULT_TIMEOUT_SECONDS;
import static io.servicetalk.http.netty.HeadResponseTestUtils.BODY;
import static io.servicetalk.http.netty.HeadResponseTestUtils.TRAILER_NAME;
import static io.servicetalk.http.netty.HeadResponseTestUtils.TRAILER_VALUE;
import static io.servicetalk.http.netty.HeadResponseTestUtils.aggregated;
import static io.servicetalk.http.netty.HeadResponseTestUtils.assertHeadThenGetOnReservedConnection;
import static io.servicetalk.http.netty.HeadResponseTestUtils.blocking;
import static io.servicetalk.http.netty.HeadResponseTestUtils.body;
import static io.servicetalk.http.netty.HeadResponseTestUtils.streaming;
import static io.servicetalk.http.netty.HeadResponseTestUtils.withTrailer;
import static io.servicetalk.http.netty.HttpProtocolConfigs.h2Default;
import static io.servicetalk.test.resources.TestUtils.assertNoAsyncErrors;
import static io.servicetalk.transport.netty.internal.BuilderUtils.socketChannel;
import static io.servicetalk.transport.netty.internal.CloseUtils.safeSync;
import static io.servicetalk.transport.netty.internal.NettyIoExecutors.createIoExecutor;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

/**
 * End-to-end check, with a raw Netty HTTP/2 client, of the frames an HTTP/2 server writes for a {@code HEAD} response.
 * <p>
 * A raw client is used on purpose: ServiceTalk's own client knows that a {@code HEAD} response has no payload and may
 * hide stray {@code DATA} or trailers {@code HEADERS} frames, while strict clients (e.g. curl/nghttp2) answer such
 * frames with {@code GOAWAY} and tear the connection down. It is the HTTP/2 counterpart of
 * {@link Http1HeadResponseTest}.
 */
class Http2HeadResponseTest {

    private static final int BODY_BYTES = BODY.length();

    private static final class HeadCase {
        final ServerStarter server;
        /** Expected {@code content-length} of the HEAD response: only a handler-set one must be present. */
        final Matcher<String> headContentLength;
        /** Number of payload body bytes the handler produces, which the {@code GET} on the same connection gets. */
        final int getBodyBytes;
        /** Whether the handler sets a trailer, which the {@code GET} on the same connection gets. */
        final boolean getHasTrailer;

        HeadCase(final ServerStarter server, final Matcher<String> headContentLength, final int getBodyBytes,
                 final boolean getHasTrailer) {
            this.server = server;
            this.headContentLength = headContentLength;
            this.getBodyBytes = getBodyBytes;
            this.getHasTrailer = getHasTrailer;
        }
    }

    static Stream<Arguments> headCases() {
        final Matcher<String> noContentLength = nullValue(String.class);
        final Matcher<String> contentLength52 = is("52");
        return Stream.of(
                headCase("streaming body+trailer, no framing header", streaming(h2Default(), rf ->
                                withTrailer(rf.ok().payloadBody(Publisher.from(body())))),
                        noContentLength, BODY_BYTES, true),
                headCase("streaming body, handler-set content-length", streaming(h2Default(), rf ->
                                rf.ok().payloadBody(Publisher.from(body())).addHeader("content-length", "52")),
                        contentLength52, BODY_BYTES, false),
                headCase("streaming 3 buffers", streaming(h2Default(), rf ->
                                rf.ok().payloadBody(Publisher.from(body(), body(), body()))),
                        noContentLength, 3 * BODY_BYTES, false),
                headCase("streaming one empty buffer", streaming(h2Default(), rf ->
                                rf.ok().payloadBody(Publisher.from(DEFAULT_ALLOCATOR.newBuffer(0)))),
                        noContentLength, 0, false),
                headCase("streaming zero items", streaming(h2Default(), rf ->
                                rf.ok().payloadBody(Publisher.<Buffer>empty())),
                        noContentLength, 0, false),
                headCase("streaming no payload at all", streaming(h2Default(), StreamingHttpResponseFactory::ok),
                        noContentLength, 0, false),
                headCase("aggregated body", aggregated(h2Default(), rf -> rf.ok().payloadBody(body())),
                        noContentLength, BODY_BYTES, false),
                headCase("aggregated no payload at all", aggregated(h2Default(), HttpResponseFactory::ok),
                        noContentLength, 0, false),
                headCase("blocking body", blocking(h2Default(), rf -> rf.ok().payloadBody(body())),
                        noContentLength, BODY_BYTES, false),
                headCase("streaming trailers only", streaming(h2Default(), rf -> withTrailer(rf.ok())),
                        noContentLength, 0, true));
    }

    @ParameterizedTest(name = "{displayName} [{index}] {0}")
    @MethodSource("headCases")
    void rawH2HeadResponseHasNoPayloadAndNoTrailers(HeadCase headCase) throws Exception {
        try (ServerContext serverContext = headCase.server.start();
             RawH2Client client = new RawH2Client(serverContext.listenAddress())) {
            List<Frame> head = client.request("HEAD");
            // Same connection: leftover state from the HEAD stream would break or alter the GET response.
            List<Frame> get = client.request("GET");

            String headDescription = "HEAD response frames: " + head;
            Frame headers = head.get(0);
            assertThat(headDescription, headers.kind, is(Kind.HEADERS));
            assertThat(headDescription, String.valueOf(headers.headers().status()), is("200"));
            assertThat(headDescription, toStringOrNull(headers.headers().get("content-length")),
                    headCase.headContentLength);
            assertThat("HEAD response leaked payload body bytes in DATA frames: " + head, dataBytes(head), is(0));
            assertThat("HEAD response leaked trailers in an additional HEADERS frame: " + head,
                    count(head, Kind.HEADERS), is(1));
            assertThat(headDescription, count(head, Kind.RESET), is(0));
            assertThat("HEAD response stream was not ended by the last frame: " + head,
                    head.get(head.size() - 1).endStream, is(true));

            String getDescription = "GET response frames on the same connection: " + get;
            assertThat(getDescription, get.get(0).kind, is(Kind.HEADERS));
            assertThat(getDescription, String.valueOf(get.get(0).headers().status()), is("200"));
            assertThat(getDescription, dataBytes(get), is(headCase.getBodyBytes));
            assertThat(getDescription, count(get, Kind.HEADERS), is(headCase.getHasTrailer ? 2 : 1));
            if (headCase.getHasTrailer) {
                assertThat(getDescription, toStringOrNull(get.get(get.size() - 1).headers().get(TRAILER_NAME)),
                        is(TRAILER_VALUE));
            }
            assertThat(getDescription, count(get, Kind.RESET), is(0));
            assertThat(getDescription, get.get(get.size() - 1).endStream, is(true));

            assertThat("Connection is not usable after the HEAD response", client.isActive(), is(true));
            assertThat("GOAWAY received after the HEAD response", client.goAways(), is(empty()));
            client.assertNoErrors();
        }
    }

    @ParameterizedTest(name = "{displayName} [{index}] client={0}")
    @EnumSource(HttpTestExecutionStrategy.class)
    void reservedConnectionHeadThenGetHasNoLeftoverState(HttpTestExecutionStrategy strategy) throws Exception {
        assertHeadThenGetOnReservedConnection(h2Default(), strategy);
    }

    private static Arguments headCase(String name, ServerStarter server, Matcher<String> headContentLength,
                                      int getBodyBytes, boolean getHasTrailer) {
        return Arguments.of(Named.of(name, new HeadCase(server, headContentLength, getBodyBytes, getHasTrailer)));
    }

    @Nullable
    private static String toStringOrNull(@Nullable CharSequence value) {
        return value == null ? null : value.toString();
    }

    private static int dataBytes(List<Frame> frames) {
        int sum = 0;
        for (Frame frame : frames) {
            sum += frame.dataBytes;
        }
        return sum;
    }

    private static int count(List<Frame> frames, Kind kind) {
        int count = 0;
        for (Frame frame : frames) {
            if (frame.kind == kind) {
                ++count;
            }
        }
        return count;
    }

    private enum Kind {
        HEADERS, DATA, RESET
    }

    /** Immutable summary of an inbound HTTP/2 stream frame, safe to keep after the Netty frame is released. */
    private static final class Frame {
        final Kind kind;
        @Nullable
        private final Http2Headers headers;
        final int dataBytes;
        final boolean endStream;

        private Frame(final Kind kind, @Nullable final Http2Headers headers, final int dataBytes,
                      final boolean endStream) {
            this.kind = kind;
            this.headers = headers;
            this.dataBytes = dataBytes;
            this.endStream = endStream;
        }

        Http2Headers headers() {
            assertThat("Not a HEADERS frame: " + this, headers, is(notNullValue()));
            return headers;
        }

        @Override
        public String toString() {
            return kind + "(" + (headers != null ? headers : "bytes=" + dataBytes) + ", endStream=" + endStream + ")";
        }
    }

    /** Collects the frames of a single stream, releasing the Netty frames after summarizing them. */
    private static final class StreamFrameCollector extends ChannelInboundHandlerAdapter {
        private final BlockingQueue<Frame> frames;
        private final Queue<Throwable> errors;

        StreamFrameCollector(final BlockingQueue<Frame> frames, final Queue<Throwable> errors) {
            this.frames = frames;
            this.errors = errors;
        }

        @Override
        public void channelRead(final ChannelHandlerContext ctx, final Object msg) {
            try {
                if (msg instanceof Http2HeadersFrame) {
                    Http2HeadersFrame frame = (Http2HeadersFrame) msg;
                    frames.add(new Frame(Kind.HEADERS, frame.headers(), 0, frame.isEndStream()));
                } else if (msg instanceof Http2DataFrame) {
                    Http2DataFrame frame = (Http2DataFrame) msg;
                    frames.add(new Frame(Kind.DATA, null, frame.content().readableBytes(), frame.isEndStream()));
                } else if (msg instanceof Http2ResetFrame) {
                    frames.add(new Frame(Kind.RESET, null, 0, false));
                } else {
                    errors.add(new IllegalStateException("Unexpected stream frame: " + msg));
                }
            } finally {
                ReferenceCountUtil.release(msg);
            }
        }

        @Override
        public void exceptionCaught(final ChannelHandlerContext ctx, final Throwable cause) {
            errors.add(cause);
        }
    }

    /** Minimal cleartext prior-knowledge HTTP/2 client that exposes the raw frames of every response. */
    private static final class RawH2Client implements AutoCloseable {
        private final EventLoopGroup group = createIoExecutor(1, "h2-raw-client-io").eventLoopGroup();
        private final Queue<Throwable> errors = new LinkedBlockingQueue<>();
        private final Queue<String> goAways = new LinkedBlockingQueue<>();
        private final Channel parent;

        RawH2Client(final SocketAddress address) throws Exception {
            try {
                ChannelFuture connect = new Bootstrap().group(group)
                        .channel(socketChannel(group, InetSocketAddress.class))
                        .handler(new ChannelInitializer<Channel>() {
                            @Override
                            protected void initChannel(final Channel ch) {
                                ch.pipeline().addLast(Http2FrameCodecBuilder.forClient().build(),
                                        new Http2MultiplexHandler(new ChannelInboundHandlerAdapter()),
                                        new ParentCollector());
                            }
                        }).connect(address);
                connect.get(DEFAULT_TIMEOUT_SECONDS, SECONDS);
                parent = connect.channel();
            } catch (Throwable t) {
                safeSync(group.shutdownGracefully(0, 0, MILLISECONDS));
                throw t;
            }
        }

        /** Sends a request without a payload on a new stream and returns all frames up to the end of the response. */
        List<Frame> request(String method) throws Exception {
            BlockingQueue<Frame> frames = new LinkedBlockingQueue<>();
            Http2StreamChannel stream = new Http2StreamChannelBootstrap(parent)
                    .handler(new StreamFrameCollector(frames, errors))
                    .open().get(DEFAULT_TIMEOUT_SECONDS, SECONDS);
            stream.writeAndFlush(new DefaultHttp2HeadersFrame(new DefaultHttp2Headers().method(method).path("/")
                    .scheme("http").authority("localhost"), true)).get(DEFAULT_TIMEOUT_SECONDS, SECONDS);
            List<Frame> response = new ArrayList<>();
            Frame frame;
            do {
                frame = frames.poll(DEFAULT_TIMEOUT_SECONDS, SECONDS);
                assertThat("Timed out waiting for the end of the " + method + " response, frames so far: " + response,
                        frame, is(notNullValue()));
                response.add(frame);
            } while (!frame.endStream && frame.kind != Kind.RESET);
            return response;
        }

        boolean isActive() {
            return parent.isActive();
        }

        List<String> goAways() {
            return new ArrayList<>(goAways);
        }

        void assertNoErrors() {
            assertNoAsyncErrors(errors);
        }

        @Override
        public void close() throws Exception {
            try {
                safeSync(parent.close());
            } finally {
                safeSync(group.shutdownGracefully(0, 0, MILLISECONDS));
            }
        }

        private final class ParentCollector extends ChannelInboundHandlerAdapter {
            @Override
            public void channelRead(final ChannelHandlerContext ctx, final Object msg) {
                try {
                    if (msg instanceof Http2GoAwayFrame) {
                        goAways.add(msg.toString());
                    }
                } finally {
                    ReferenceCountUtil.release(msg);
                }
            }

            @Override
            public void exceptionCaught(final ChannelHandlerContext ctx, final Throwable cause) {
                errors.add(cause);
            }
        }
    }
}
