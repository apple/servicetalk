/*
 * Copyright © 2021-2022 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.client.api.ConnectionLimitReachedException;
import io.servicetalk.client.api.LimitingConnectionFactoryFilter;
import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.SingleSource;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.HttpClient;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.ReservedHttpConnection;
import io.servicetalk.http.api.SingleAddressHttpClientBuilder;
import io.servicetalk.http.api.StreamingHttpConnectionFilter;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.netty.RetryingHttpRequesterFilter.BackOffPolicy;
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.netty.internal.ExecutionContextExtension;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http2.DefaultHttp2SettingsFrame;
import io.netty.handler.codec.http2.Http2FrameLogger;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2HeadersFrame;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.handler.codec.http2.Http2StreamChannel;
import io.netty.handler.logging.LogLevel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

import static io.netty.handler.codec.http2.Http2CodecUtil.MAX_UNSIGNED_INT;
import static io.servicetalk.concurrent.api.internal.BlockingUtils.blockingInvocation;
import static io.servicetalk.http.api.HttpExecutionStrategies.customStrategyBuilder;
import static io.servicetalk.http.api.HttpExecutionStrategies.defaultStrategy;
import static io.servicetalk.http.api.HttpExecutionStrategies.offloadAll;
import static io.servicetalk.http.api.HttpExecutionStrategies.offloadNone;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.netty.AbstractStreamingHttpConnection.MAX_CONCURRENCY_NO_OFFLOADING;
import static io.servicetalk.http.netty.BuilderUtils.newClientBuilder;
import static io.servicetalk.http.netty.H2PriorKnowledgeFeatureParityTest.EchoHttp2Handler;
import static io.servicetalk.http.netty.H2PriorKnowledgeFeatureParityTest.bindH2Server;
import static io.servicetalk.http.netty.HttpProtocol.HTTP_2;
import static io.servicetalk.transport.api.HostAndPort.of;
import static io.servicetalk.transport.netty.internal.CloseUtils.safeSync;
import static io.servicetalk.transport.netty.internal.NettyIoExecutors.createIoExecutor;
import static java.lang.Integer.parseInt;
import static java.time.Duration.ofMillis;
import static java.util.Arrays.asList;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Named.named;

class H2ConcurrencyControllerTest {

    private static final int N_ITERATIONS = 3;
    private static final int STEP = 4;
    private static final int REPEATED_TEST_ITERATIONS = 16;
    // REFUSED_STREAM error code per RFC 7540 section 7.
    private static final long REFUSED_STREAM_CODE = 7L;

    @RegisterExtension
    static final ExecutionContextExtension CLIENT_CTX =
        ExecutionContextExtension.cached("client-io", "client-executor")
                .setClassLevel(true);

    private final CountDownLatch[] latches = new CountDownLatch[N_ITERATIONS];
    private final AtomicReference<Channel> serverParentChannel = new AtomicReference<>();
    private final AtomicBoolean alwaysEcho = new AtomicBoolean(false);
    private final RecordingFrameLogger serverFrameLog = new RecordingFrameLogger();
    @Nullable
    private EventLoopGroup serverEventLoopGroup;
    @Nullable
    private Channel serverAcceptorChannel;
    @Nullable
    private HostAndPort serverAddress;

    private void setUp(long maxConcurrentStreams) throws Exception {
        serverEventLoopGroup = createIoExecutor(1, "server-io").eventLoopGroup();
        for (int i = 0; i < N_ITERATIONS; i++) {
            latches[i] = new CountDownLatch(1);
        }
        AtomicBoolean secondAndMore = new AtomicBoolean();
        serverAcceptorChannel = bindH2Server(serverEventLoopGroup, new ChannelInitializer<Http2StreamChannel>() {
            @Override
            protected void initChannel(Http2StreamChannel ch) {
                // Respond only for the first request which is used to propagate MAX_CONCURRENT_STREAMS_VALUE
                if (alwaysEcho.get() || secondAndMore.compareAndSet(false, true)) {
                    ch.pipeline().addLast(new EchoHttp2Handler());
                } else {
                    // Do not respond to any subsequent requests, only release the associated latch to notify the client
                    // that server received the request.
                    ch.pipeline().addLast(new SimpleChannelInboundHandler<Http2HeadersFrame>() {
                        @Override
                        protected void channelRead0(final ChannelHandlerContext ctx, final Http2HeadersFrame msg) {
                            String path = msg.headers().path().toString();
                            int i = parseInt(path.substring(1));
                            latches[i].countDown();
                        }
                    });
                }
            }
        },
        parentPipeline -> serverParentChannel.set(parentPipeline.channel()),
        h2Builder -> {
            h2Builder.initialSettings().maxConcurrentStreams(maxConcurrentStreams);
            h2Builder.frameLogger(serverFrameLog);
            return h2Builder;
        });

        serverAddress = of((InetSocketAddress) serverAcceptorChannel.localAddress());
    }

    @AfterEach
    void tearDown() throws Exception {
        if (serverAcceptorChannel != null) {
            safeSync(serverAcceptorChannel.close());
        }
        if (serverEventLoopGroup != null) {
            safeSync(serverEventLoopGroup.shutdownGracefully(0, 0, MILLISECONDS));
        }
    }

    @RepeatedTest(3 * 1024)
    void noMaxActiveStreamsViolatedErrorAfterCancel() throws Exception { // the flaky test.
        int serverMaxConcurrentStreams = 1;
        setUp(serverMaxConcurrentStreams);
        assert serverAddress != null;
        try (HttpClient client = configureSingleConnection(newClientBuilder(serverAddress, CLIENT_CTX, HTTP_2),
                // The deferred cancel holds the only slot ~100ms, so wait for it rather than spin.
                BackOffPolicy.ofConstantBackoffFullJitter(ofMillis(10), 128))
                .appendConnectionFilter(connection -> new StreamingHttpConnectionFilter(connection) {
                    @Override
                    public Single<StreamingHttpResponse> request(StreamingHttpRequest request) {
                        return delegate().request(request)
                                .liftSync(subscriber -> new SingleSource.Subscriber<StreamingHttpResponse>() {
                                    @Override
                                    public void onSubscribe(final Cancellable cancellable) {
                                        // Defer the cancel() signal to let the test thread start a new request
                                        subscriber.onSubscribe(() -> CLIENT_CTX.executor()
                                                .schedule(cancellable::cancel, ofMillis(100)));
                                    }

                                    @Override
                                    public void onSuccess(@Nullable final StreamingHttpResponse result) {
                                        subscriber.onSuccess(result);
                                    }

                                    @Override
                                    public void onError(final Throwable t) {
                                        subscriber.onError(t);
                                    }
                                });
                    }
                })
                .protocols(HTTP_2.config)
                .build()) {

            BlockingQueue<Integer> maxConcurrentStreams = new LinkedBlockingDeque<>();
            try (ReservedHttpConnection connection = client.reserveConnection(client.get("/")).map(conn -> {
                conn.transportEventStream(MAX_CONCURRENCY_NO_OFFLOADING)
                        .forEach(event -> maxConcurrentStreams.add(event.event()));
                return conn;
            }).toFuture().get()) {
                awaitMaxConcurrentStreamsSettingsUpdate(connection, maxConcurrentStreams,
                        serverMaxConcurrentStreams);
                connection.releaseAsync().toFuture().get();

                BlockingQueue<Throwable> exceptions = new LinkedBlockingDeque<>();
                for (int i = 0; i < N_ITERATIONS; i++) {
                    final int idx = i;
                    Cancellable cancellable = client.request(client.get("/" + i))
                        .whenOnError(exceptions::add)
                        .afterFinally(() -> latches[idx].countDown())
                        .subscribe(__ -> { /* response is not expected */ });
                    latches[i].await();
                    cancellable.cancel();
                }
                if (!exceptions.isEmpty()) {
                    throw new AssertionError(diagnoseRefusal(exceptions, serverFrameLog.events));
                }
            }
        }
    }

    private static String diagnoseRefusal(BlockingQueue<Throwable> exceptions, Queue<FrameEvent> events) {
        StringBuilder sb = new StringBuilder("Unexpected exceptions: ").append(exceptions);
        List<FrameEvent> trace = new ArrayList<>(events);

        // Group by connection so we can tell a single-connection reorder apart from multiple connections.
        Map<String, List<FrameEvent>> byConn = new LinkedHashMap<>();
        for (FrameEvent e : trace) {
            byConn.computeIfAbsent(e.connId, k -> new ArrayList<>()).add(e);
        }
        sb.append("\nServer observed ").append(byConn.size())
                .append(" connection(s). Per-connection frame trace (codec processing order):");
        for (Map.Entry<String, List<FrameEvent>> entry : byConn.entrySet()) {
            sb.append("\n  connection ").append(entry.getKey()).append(':');
            for (FrameEvent e : entry.getValue()) {
                sb.append("\n    ").append(e);
            }
        }

        // For each refused stream, analyze the connection it happened on.
        for (FrameEvent refused : trace) {
            if (refused.direction == Http2FrameLogger.Direction.OUTBOUND && "RST_STREAM".equals(refused.type)
                    && refused.errorCode == REFUSED_STREAM_CODE) {
                sb.append("\nVERDICT for refused streamId=").append(refused.streamId)
                        .append(" on connection ").append(refused.connId).append(':');
                Set<Integer> active = activeClientStreamsBeforeRefusal(byConn.get(refused.connId), refused.streamId);
                if (active.isEmpty()) {
                    sb.append("\n  No other client stream was active on this connection when HEADERS(")
                            .append(refused.streamId).append(") arrived => server refused a lone/in-order stream "
                                    + "(would indicate a Netty server-side bug).");
                } else {
                    sb.append("\n  Client streams still active on this connection when HEADERS(")
                            .append(refused.streamId).append(") arrived: ").append(active)
                            .append("\n  => the client had ").append(active.size() + 1)
                            .append(" concurrent streams open against maxConcurrentStreams=1 (over-subscription); "
                                    + "REFUSED_STREAM is the server enforcing its advertised limit, not a reorder bug.");
                }
            }
        }
        return sb.toString();
    }

    /**
     * Walks a single connection's frames up to (but not including) the inbound HEADERS for {@code refusedId} and
     * returns the set of client-initiated (odd) streams that were still active at that point. A stream is active
     * once its HEADERS is seen and until it is reset or fully closed (both peers signalled END_STREAM).
     */
    private static Set<Integer> activeClientStreamsBeforeRefusal(List<FrameEvent> conn, int refusedId) {
        Map<Integer, StreamState> states = new LinkedHashMap<>();
        for (FrameEvent e : conn) {
            if (e.direction == Http2FrameLogger.Direction.INBOUND && "HEADERS".equals(e.type)
                    && e.streamId == refusedId) {
                break; // stop just before the refused stream is created
            }
            if (e.streamId == 0 || (e.streamId & 1) == 0) {
                continue; // connection-level or server-initiated frames
            }
            StreamState st = states.computeIfAbsent(e.streamId, k -> new StreamState());
            boolean inbound = e.direction == Http2FrameLogger.Direction.INBOUND;
            if ("HEADERS".equals(e.type)) {
                st.started = true;
                st.clientEnd |= inbound && e.endStream;
                st.serverEnd |= !inbound && e.endStream;
            } else if ("DATA".equals(e.type)) {
                st.clientEnd |= inbound && e.endStream;
                st.serverEnd |= !inbound && e.endStream;
            } else if ("RST_STREAM".equals(e.type)) {
                st.reset = true;
            }
        }
        Set<Integer> active = new LinkedHashSet<>();
        for (Map.Entry<Integer, StreamState> en : states.entrySet()) {
            StreamState st = en.getValue();
            if (st.started && !st.reset && !(st.clientEnd && st.serverEnd)) {
                active.add(en.getKey());
            }
        }
        return active;
    }

    private static final class StreamState {
        boolean started;
        boolean clientEnd;
        boolean serverEnd;
        boolean reset;
    }

    private static final class FrameEvent {
        final String connId;
        final Http2FrameLogger.Direction direction;
        final String type;
        final int streamId;
        final long errorCode;   // -1 when not applicable
        final boolean endStream;
        @Nullable
        final String detail;    // e.g. SETTINGS contents

        FrameEvent(String connId, Http2FrameLogger.Direction direction, String type, int streamId, long errorCode,
                   boolean endStream, @Nullable String detail) {
            this.connId = connId;
            this.direction = direction;
            this.type = type;
            this.streamId = streamId;
            this.errorCode = errorCode;
            this.endStream = endStream;
            this.detail = detail;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder(direction.toString()).append(' ').append(type);
            if (streamId != 0) {
                sb.append(" streamId=").append(streamId);
            }
            if (errorCode >= 0) {
                sb.append(" errorCode=").append(errorCode);
            }
            if (endStream) {
                sb.append(" END_STREAM");
            }
            if (detail != null) {
                sb.append(' ').append(detail);
            }
            return sb.toString();
        }
    }

    /**
     * Records HEADERS / DATA / RST_STREAM / SETTINGS frames per connection as observed by the server's codec. The
     * frame logger is invoked by the frame reader/writer wrappers in codec processing order, so INBOUND entries
     * reflect the order frames were decoded off the wire (including a HEADERS frame that is subsequently refused).
     */
    private static final class RecordingFrameLogger extends Http2FrameLogger {
        final Queue<FrameEvent> events = new ConcurrentLinkedQueue<>();

        RecordingFrameLogger() {
            super(LogLevel.DEBUG, "H2-SERVER");
        }

        private static String connId(ChannelHandlerContext ctx) {
            return ctx.channel().id().asShortText();
        }

        @Override
        public void logHeaders(Direction direction, ChannelHandlerContext ctx, int streamId, Http2Headers headers,
                               int padding, boolean endStream) {
            events.add(new FrameEvent(connId(ctx), direction, "HEADERS", streamId, -1, endStream, null));
            super.logHeaders(direction, ctx, streamId, headers, padding, endStream);
        }

        @Override
        public void logHeaders(Direction direction, ChannelHandlerContext ctx, int streamId, Http2Headers headers,
                               int streamDependency, short weight, boolean exclusive, int padding, boolean endStream) {
            events.add(new FrameEvent(connId(ctx), direction, "HEADERS", streamId, -1, endStream, null));
            super.logHeaders(direction, ctx, streamId, headers, streamDependency, weight, exclusive, padding,
                    endStream);
        }

        @Override
        public void logData(Direction direction, ChannelHandlerContext ctx, int streamId, ByteBuf data, int padding,
                            boolean endStream) {
            events.add(new FrameEvent(connId(ctx), direction, "DATA", streamId, -1, endStream, null));
            super.logData(direction, ctx, streamId, data, padding, endStream);
        }

        @Override
        public void logRstStream(Direction direction, ChannelHandlerContext ctx, int streamId, long errorCode) {
            events.add(new FrameEvent(connId(ctx), direction, "RST_STREAM", streamId, errorCode, false, null));
            super.logRstStream(direction, ctx, streamId, errorCode);
        }

        @Override
        public void logSettings(Direction direction, ChannelHandlerContext ctx, Http2Settings settings) {
            events.add(new FrameEvent(connId(ctx), direction, "SETTINGS", 0, -1, false,
                    "maxConcurrentStreams=" + settings.maxConcurrentStreams()));
            super.logSettings(direction, ctx, settings);
        }

        @Override
        public void logSettingsAck(Direction direction, ChannelHandlerContext ctx) {
            events.add(new FrameEvent(connId(ctx), direction, "SETTINGS_ACK", 0, -1, false, null));
            super.logSettingsAck(direction, ctx);
        }
    }

    /**
     * Pins the client to a single connection so the concurrency controller (not a second, not-yet-settings-synced
     * connection) is what's under test. A request that can't get the in-use connection's only slot fails with
     * {@link ConnectionLimitReachedException} and is retried with {@code limitReachedBackoff}; real stream errors are
     * never retried, so a genuine "max concurrent streams violated" still reaches the assertion.
     */
    private static SingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> configureSingleConnection(
            SingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> builder, BackOffPolicy limitReachedBackoff) {
        return builder
                .appendConnectionFactoryFilter(LimitingConnectionFactoryFilter.withMax(1))
                .appendClientFilter(new RetryingHttpRequesterFilter.Builder()
                        .maxTotalRetries(Integer.MAX_VALUE)
                        .retryRetryableExceptions((metaData, e) -> e instanceof ConnectionLimitReachedException
                                ? limitReachedBackoff : BackOffPolicy.ofNoRetries())
                        .build());
    }

    private static List<Arguments> params() {
        List<Arguments> params = new ArrayList<>();
        for (Named<HttpExecutionStrategy> strategy : asList(
                named("defaultStrategy", defaultStrategy()),
                named("offloadNone", offloadNone()),
                named("offloadAll", offloadAll()),
                named("offloadEvents", customStrategyBuilder().offloadEvent().build()))) {
            // Use index to repeat the test. RepeatedTest can not be used simultaneously with ParameterizedTest.
            for (int i = 1; i <= REPEATED_TEST_ITERATIONS; ++i) {
                params.add(Arguments.of(strategy, i));
            }
        }
        return params;
    }

    @ParameterizedTest(name = "{displayName} [{index}] strategy={0}, repetition {1} of " + REPEATED_TEST_ITERATIONS)
    @MethodSource("params")
    void noMaxActiveStreamsViolatedErrorWhenLimitIncreases(HttpExecutionStrategy strategy,
                @SuppressWarnings("unused") int repetition) throws Exception {
        noMaxActiveStreamsViolatedErrorWhenLimitChanges(true, strategy);
    }

    @ParameterizedTest(name = "{displayName} [{index}] strategy={0}, repetition {1} of " + REPEATED_TEST_ITERATIONS)
    @MethodSource("params")
    void noMaxActiveStreamsViolatedErrorWhenLimitDecreases(HttpExecutionStrategy strategy,
                @SuppressWarnings("unused") int repetition) throws Exception {
        noMaxActiveStreamsViolatedErrorWhenLimitChanges(false, strategy);
    }

    private void noMaxActiveStreamsViolatedErrorWhenLimitChanges(boolean increase,
                                                                 HttpExecutionStrategy strategy) throws Exception {
        int serverMaxConcurrentStreams = 1;
        setUp(serverMaxConcurrentStreams);
        alwaysEcho.set(true);   // server should always respond
        assert serverAddress != null;
        try (HttpClient client = configureSingleConnection(newClientBuilder(serverAddress, CLIENT_CTX, HTTP_2)
                .executionStrategy(strategy),
                // Spin to densely probe the limit-change race; echoed streams free the slot near-instantly.
                BackOffPolicy.ofImmediate(128))
                .protocols(HTTP_2.config)
                .build()) {

            BlockingQueue<Integer> maxConcurrentStreams = new LinkedBlockingDeque<>();
            try (ReservedHttpConnection connection = client.reserveConnection(client.get("/")).map(conn -> {
                conn.transportEventStream(MAX_CONCURRENCY_NO_OFFLOADING)
                        .forEach(event -> maxConcurrentStreams.add(event.event()));
                return conn;
            }).toFuture().get()) {
                awaitMaxConcurrentStreamsSettingsUpdate(connection, maxConcurrentStreams,
                        serverMaxConcurrentStreams);
                connection.releaseAsync().toFuture().get();

                final Channel serverParentChannel = this.serverParentChannel.get();
                assertThat(serverParentChannel, is(notNullValue()));

                int newConcurrency = increase ? 0 : ((N_ITERATIONS + 1) * STEP);
                if (!increase) {
                    serverParentChannel.writeAndFlush(
                            new DefaultHttp2SettingsFrame(new Http2Settings().maxConcurrentStreams(newConcurrency)));
                    awaitMaxConcurrentStreamsSettingsUpdate(maxConcurrentStreams, newConcurrency);
                }
                final int nRequests = 16;
                for (int i = 0; i < N_ITERATIONS; i++) {
                    // Run multiple parallel requests every time the limit changes:
                    List<Single<HttpResponse>> asyncResponses = new ArrayList<>(nRequests);
                    for (int j = 0; j < nRequests; j++) {
                        asyncResponses.add(client.request(client.get("/" + j)));
                    }
                    newConcurrency += increase ? STEP : -STEP;
                    serverParentChannel.writeAndFlush(
                            new DefaultHttp2SettingsFrame(new Http2Settings().maxConcurrentStreams(newConcurrency)));
                    List<Object> responses = asyncResponses.parallelStream().map(s -> {
                        try {
                            return blockingInvocation(s);
                        } catch (Exception e) {
                            return e;
                        }
                    }).collect(Collectors.toList());
                    for (Object maybeResponse : responses) {
                        // We expect requests to either complete or fail with ConnectionLimitReachedException
                        if (maybeResponse instanceof HttpResponse) {
                            assertThat(((HttpResponse) maybeResponse).status(), is(OK));
                        } else {
                            assertThat(maybeResponse, is(instanceOf(ConnectionLimitReachedException.class)));
                        }
                    }
                }
            }
        }
    }

    @Test
    void maxActiveStreamsOutsideIntRange() throws Exception {
        setUp(MAX_UNSIGNED_INT);
        assert serverAddress != null;
        assertThat(MAX_UNSIGNED_INT, is(greaterThan((long) Integer.MAX_VALUE)));
        try (HttpClient client = newClientBuilder(serverAddress, CLIENT_CTX, HTTP_2)
                .protocols(HTTP_2.config)
                .build()) {

            BlockingQueue<Integer> maxConcurrentStreams = new LinkedBlockingDeque<>();
            try (ReservedHttpConnection connection = client.reserveConnection(client.get("/")).map(conn -> {
                conn.transportEventStream(MAX_CONCURRENCY_NO_OFFLOADING)
                        .forEach(event -> maxConcurrentStreams.add(event.event()));
                return conn;
            }).toFuture().get()) {
                // The value is expected to be adjusted to avoid int overflow
                awaitMaxConcurrentStreamsSettingsUpdate(connection, maxConcurrentStreams, Integer.MAX_VALUE);
            }
        }
    }

    private static void awaitMaxConcurrentStreamsSettingsUpdate(ReservedHttpConnection connection,
                BlockingQueue<Integer> maxConcurrentStreams, int expectedValue) throws Exception {
        // In some cases preface and initial settings won't be sent until after we make the first request.
        HttpResponse response = connection.request(connection.get("/")).toFuture().get();
        assertThat(response.status(), is(OK));
        awaitMaxConcurrentStreamsSettingsUpdate(maxConcurrentStreams, expectedValue);
    }

    private static void awaitMaxConcurrentStreamsSettingsUpdate(BlockingQueue<Integer> maxConcurrentStreams,
                                                                int expectedValue) throws Exception {
        int value;
        do {
            value = maxConcurrentStreams.take();
        } while (value != expectedValue);
    }
}
