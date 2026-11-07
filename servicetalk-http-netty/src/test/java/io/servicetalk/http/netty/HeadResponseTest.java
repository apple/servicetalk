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
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpProtocolConfig;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.HttpResponseFactory;
import io.servicetalk.http.api.StatelessTrailersTransformer;
import io.servicetalk.http.api.StreamingHttpClient;
import io.servicetalk.http.api.StreamingHttpConnection;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
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
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Function;
import java.util.stream.Stream;
import javax.annotation.Nullable;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.concurrent.internal.TestTimeoutConstants.DEFAULT_TIMEOUT_SECONDS;
import static io.servicetalk.test.resources.TestUtils.assertNoAsyncErrors;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static io.servicetalk.transport.netty.internal.BuilderUtils.socketChannel;
import static io.servicetalk.transport.netty.internal.CloseUtils.safeSync;
import static io.servicetalk.transport.netty.internal.NettyIoExecutors.createIoExecutor;
import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.startsWith;

/**
 * A {@code HEAD} response must carry no payload body and no trailers (RFC 7231, section 4.3.2), whatever the handler
 * produces. Raw clients are used because ServiceTalk's own client knows that and may hide stray bytes, while strict
 * clients (curl, nghttp2) tear the connection down on them.
 */
class HeadResponseTest {

    private static final String BODY = "abcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyz";
    private static final int BODY_BYTES = BODY.length();
    private static final String TRAILER_NAME = "x-trailer";
    private static final String TRAILER_VALUE = "tv";
    private static final String OK_STATUS_LINE = "HTTP/1.1 200 OK\r\n";
    private static final String END_OF_HEADERS = "\r\n\r\n";
    private static final String HEAD_REQUEST = "HEAD / HTTP/1.1\r\nHost: localhost\r\n\r\n";
    private static final String GET_REQUEST_AND_CLOSE =
            "GET / HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n";
    private static final String CHUNKED = "transfer-encoding: chunked";
    private static final String CONTENT_LENGTH_HEADER = "content-length: " + BODY_BYTES;
    private static final int MAX_HEADERS_BYTES = 8192;

    @FunctionalInterface
    private interface ServerStarter {
        ServerContext start(HttpProtocolConfig protocol) throws Exception;
    }

    private static final class HeadCase {
        final ServerStarter server;
        @Nullable
        final String handlerContentLength;
        /** Whether the handler sets {@code transfer-encoding: chunked}, which only exists in HTTP/1.1. */
        final boolean handlerChunked;
        final int getBodyBytes;
        final boolean getHasTrailer;

        HeadCase(final ServerStarter server, @Nullable final String handlerContentLength,
                 final boolean handlerChunked, final int getBodyBytes, final boolean getHasTrailer) {
            this.server = server;
            this.handlerContentLength = handlerContentLength;
            this.handlerChunked = handlerChunked;
            this.getBodyBytes = getBodyBytes;
            this.getHasTrailer = getHasTrailer;
        }
    }

    private static final List<Named<HeadCase>> HEAD_CASES = Arrays.asList(
            headCase("streaming body+trailer", streaming(rf ->
                    withTrailer(rf.ok().payloadBody(Publisher.from(body())))), BODY_BYTES, true),
            chunkedHeadCase("streaming body+trailer, handler-set chunked", streaming(rf ->
                    withTrailer(rf.ok().payloadBody(Publisher.from(body())).addHeader("transfer-encoding", "chunked"))),
                    BODY_BYTES, true),
            chunkedHeadCase("streaming empty payload, handler-set chunked", streaming(rf ->
                    rf.ok().addHeader("transfer-encoding", "chunked")), 0, false),
            contentLengthHeadCase("streaming body, handler-set content-length", streaming(rf ->
                    rf.ok().payloadBody(Publisher.from(body()))
                            .addHeader("content-length", String.valueOf(BODY_BYTES))), BODY_BYTES, false),
            headCase("streaming 3 buffers", streaming(rf ->
                    rf.ok().payloadBody(Publisher.from(body(), body(), body()))), 3 * BODY_BYTES, false),
            headCase("streaming one empty buffer", streaming(rf ->
                    rf.ok().payloadBody(Publisher.from(DEFAULT_ALLOCATOR.newBuffer(0)))), 0, false),
            headCase("streaming zero items", streaming(rf ->
                    rf.ok().payloadBody(Publisher.<Buffer>empty())), 0, false),
            headCase("streaming no payload at all", streaming(StreamingHttpResponseFactory::ok), 0, false),
            headCase("streaming trailers only", streaming(rf -> withTrailer(rf.ok())), 0, true),
            chunkedHeadCase("streaming trailers only, handler-set chunked", streaming(rf ->
                    withTrailer(rf.ok().addHeader("transfer-encoding", "chunked"))), 0, true),
            headCase("aggregated body", aggregated(rf -> rf.ok().payloadBody(body())), BODY_BYTES, false),
            chunkedHeadCase("aggregated body, handler-set chunked", aggregated(rf ->
                    rf.ok().payloadBody(body()).addHeader("transfer-encoding", "chunked")), BODY_BYTES, false),
            headCase("aggregated no payload at all", aggregated(HttpResponseFactory::ok), 0, false),
            headCase("blocking body", blocking(rf -> rf.ok().payloadBody(body())), BODY_BYTES, false));

    @SuppressWarnings("unused")
    private static Stream<Arguments> headCases() {
        final List<Arguments> arguments = new ArrayList<>();
        for (HttpProtocol protocol : HttpProtocol.values()) {
            for (Named<HeadCase> headCase : HEAD_CASES) {
                if (protocol == HttpProtocol.HTTP_1 || !headCase.getPayload().handlerChunked) {
                    arguments.add(Arguments.of(protocol, headCase));
                }
            }
        }
        return arguments.stream();
    }

    @ParameterizedTest(name = "{displayName} [{index}] protocol={0} {1}")
    @MethodSource("headCases")
    void headResponseHasNoPayloadBodyAndNoTrailers(HttpProtocol protocol, HeadCase headCase) throws Exception {
        try (ServerContext serverContext = headCase.server.start(protocol.config)) {
            switch (protocol) {
                case HTTP_1:
                    assertHttp1(serverContext, headCase);
                    break;
                case HTTP_2:
                    assertHttp2(serverContext, headCase);
                    break;
                default:
                    throw new IllegalArgumentException("No raw client for " + protocol);
            }
        }
    }

    @ParameterizedTest(name = "{displayName} [{index}] protocol={0}")
    @EnumSource(HttpProtocol.class)
    void reservedConnectionHeadThenGetHasNoLeftoverState(HttpProtocol protocol) throws Exception {
        try (ServerContext serverContext = HttpServers.forAddress(localAddress(0)).protocols(protocol.config)
                .listenStreamingAndAwait((ctx, request, responseFactory) -> Single.succeeded(
                        withTrailer(responseFactory.ok().payloadBody(Publisher.from(body())))));
             StreamingHttpClient client = HttpClients.forSingleAddress(serverHostAndPort(serverContext))
                     .protocols(protocol.config).buildStreaming();
             StreamingHttpConnection connection = client.reserveConnection(client.get("/")).toFuture().get()) {
            HttpResponse head = connection.request(connection.head("/")).toFuture().get().toResponse()
                    .toFuture().get();
            assertThat(head.payloadBody().readableBytes(), is(0));
            assertThat(head.trailers().isEmpty(), is(true));

            HttpResponse get = connection.request(connection.get("/")).toFuture().get().toResponse()
                    .toFuture().get();
            assertThat(get.payloadBody().toString(ISO_8859_1), is(BODY));
            assertThat(get.trailers().get(TRAILER_NAME).toString(), is(TRAILER_VALUE));
        }
    }

    private static void assertHttp1(ServerContext serverContext, HeadCase headCase) throws IOException {
        try (Socket socket = new Socket()) {
            socket.connect(serverContext.listenAddress());
            // Only a safety net: the reads below end at the headers or at EOF and never wait for silence.
            socket.setSoTimeout((int) SECONDS.toMillis(DEFAULT_TIMEOUT_SECONDS / 2));
            InputStream in = new BufferedInputStream(socket.getInputStream());

            socket.getOutputStream().write(HEAD_REQUEST.getBytes(ISO_8859_1));
            String headHeaders = readUntilEndOfHeaders(in);
            // The server closes the connection after the second response, so reading to EOF is deterministic.
            socket.getOutputStream().write(GET_REQUEST_AND_CLOSE.getBytes(ISO_8859_1));
            String afterHead = readToEof(in);

            assertThat("Unexpected HEAD response status", headHeaders, startsWith(OK_STATUS_LINE));
            assertThat("HEAD response must carry only the framing headers set by the handler",
                    framingHeaders(headHeaders), is(expectedHttp1FramingHeaders(headCase)));
            assertThat("Bytes were written after the HEAD response headers (e.g. a payload body or a last-chunk)",
                    afterHead, startsWith(OK_STATUS_LINE));
            assertThat("GET response on the same connection has an unexpected body: leftover state from HEAD?",
                    countOccurrences(afterHead, BODY), is(headCase.getBodyBytes / BODY_BYTES));
            assertThat("GET response on the same connection has unexpected trailers: leftover state from HEAD?",
                    afterHead.contains(TRAILER_NAME + ": " + TRAILER_VALUE), is(headCase.getHasTrailer));
        }
    }

    private static void assertHttp2(ServerContext serverContext, HeadCase headCase) throws Exception {
        try (RawH2Client client = new RawH2Client(serverContext.listenAddress())) {
            List<Frame> head = client.request("HEAD");
            // Same connection: leftover state from the HEAD stream would break or alter the GET response.
            List<Frame> get = client.request("GET");

            String headDescription = "HEAD response frames: " + head;
            Frame headers = head.get(0);
            assertThat(headDescription, headers.kind, is(Kind.HEADERS));
            assertThat(headDescription, String.valueOf(headers.headers().status()), is("200"));
            assertThat(headDescription, toStringOrNull(headers.headers().get("content-length")),
                    is(headCase.handlerContentLength));
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

    private static List<String> expectedHttp1FramingHeaders(HeadCase headCase) {
        if (headCase.handlerChunked) {
            return Collections.singletonList(CHUNKED);
        }
        return headCase.handlerContentLength == null ? Collections.<String>emptyList() :
                Collections.singletonList(CONTENT_LENGTH_HEADER);
    }

    private static Named<HeadCase> headCase(String name, ServerStarter server, int getBodyBytes,
                                            boolean getHasTrailer) {
        return Named.of(name, new HeadCase(server, null, false, getBodyBytes, getHasTrailer));
    }

    private static Named<HeadCase> chunkedHeadCase(String name, ServerStarter server, int getBodyBytes,
                                                   boolean getHasTrailer) {
        return Named.of(name, new HeadCase(server, null, true, getBodyBytes, getHasTrailer));
    }

    private static Named<HeadCase> contentLengthHeadCase(String name, ServerStarter server, int getBodyBytes,
                                                         boolean getHasTrailer) {
        return Named.of(name, new HeadCase(server, String.valueOf(BODY_BYTES), false, getBodyBytes,
                getHasTrailer));
    }

    private static Buffer body() {
        return DEFAULT_ALLOCATOR.fromAscii(BODY);
    }

    private static StreamingHttpResponse withTrailer(StreamingHttpResponse response) {
        return response.transform(new StatelessTrailersTransformer<Buffer>() {
            @Override
            protected HttpHeaders payloadComplete(final HttpHeaders trailers) {
                return trailers.add(TRAILER_NAME, TRAILER_VALUE);
            }
        });
    }

    private static ServerStarter streaming(Function<StreamingHttpResponseFactory, StreamingHttpResponse> handler) {
        return protocol -> HttpServers.forAddress(localAddress(0)).protocols(protocol)
                .listenStreamingAndAwait((ctx, request, responseFactory) ->
                        Single.succeeded(handler.apply(responseFactory)));
    }

    private static ServerStarter aggregated(Function<HttpResponseFactory, HttpResponse> handler) {
        return protocol -> HttpServers.forAddress(localAddress(0)).protocols(protocol)
                .listenAndAwait((ctx, request, responseFactory) -> Single.succeeded(handler.apply(responseFactory)));
    }

    private static ServerStarter blocking(Function<HttpResponseFactory, HttpResponse> handler) {
        return protocol -> HttpServers.forAddress(localAddress(0)).protocols(protocol)
                .listenBlockingAndAwait((ctx, request, responseFactory) -> handler.apply(responseFactory));
    }

    /** Reads byte by byte so that nothing after the headers is consumed. */
    private static String readUntilEndOfHeaders(InputStream in) throws IOException {
        ByteArrayOutputStream headers = new ByteArrayOutputStream();
        String text = "";
        while (!text.endsWith(END_OF_HEADERS) && headers.size() < MAX_HEADERS_BYTES) {
            int b = in.read();
            if (b < 0) {
                break;
            }
            headers.write(b);
            text = new String(headers.toByteArray(), ISO_8859_1);
        }
        assertThat("End of the HEAD response headers not found", text, endsWith(END_OF_HEADERS));
        return text;
    }

    private static String readToEof(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int read;
        while ((read = in.read(buffer)) >= 0) {
            out.write(buffer, 0, read);
        }
        return new String(out.toByteArray(), ISO_8859_1);
    }

    private static List<String> framingHeaders(String headers) {
        List<String> framing = new ArrayList<>();
        for (String line : headers.split("\r\n")) {
            String lower = line.toLowerCase(Locale.ROOT);
            if (lower.startsWith("content-length:") || lower.startsWith("transfer-encoding:")) {
                framing.add(lower);
            }
        }
        return framing;
    }

    private static int countOccurrences(String text, String sub) {
        int count = 0;
        for (int from = text.indexOf(sub); from >= 0; from = text.indexOf(sub, from + sub.length())) {
            ++count;
        }
        return count;
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
