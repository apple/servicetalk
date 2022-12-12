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
import io.servicetalk.http.api.StreamingHttpConnectionFilter;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.netty.RetryingHttpRequesterFilter.BackOffPolicy;
import io.servicetalk.http.netty.StreamObserverTest.MulticastTransportEventsStreamingHttpConnectionFilter;
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.netty.internal.ExecutionContextExtension;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http2.DefaultHttp2SettingsFrame;
import io.netty.handler.codec.http2.Http2HeadersFrame;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.handler.codec.http2.Http2StreamChannel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

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
import static io.servicetalk.http.netty.RetryingHttpRequesterFilter.disableAutoRetries;
import static io.servicetalk.http.netty.StreamObserverTest.safeSync;
import static io.servicetalk.transport.api.HostAndPort.of;
import static io.servicetalk.transport.netty.internal.NettyIoExecutors.createIoExecutor;
import static java.lang.Integer.parseInt;
import static java.time.Duration.ofMillis;
import static java.util.Arrays.asList;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Named.named;

class H2ConcurrencyControllerTest {

    private static final int SERVER_MAX_CONCURRENT_STREAMS_VALUE = 1;
    private static final int N_ITERATIONS = 3;
    private static final int STEP = 4;
    private static final int REPEATED_TEST_ITERATIONS = 16;

    @RegisterExtension
    static final ExecutionContextExtension CLIENT_CTX =
        ExecutionContextExtension.cached("client-io", "client-executor")
                .setClassLevel(true);

    private final CountDownLatch[] latches = new CountDownLatch[N_ITERATIONS];
    private final AtomicReference<Channel> serverParentChannel = new AtomicReference<>();
    private final AtomicBoolean alwaysEcho = new AtomicBoolean(false);
    private EventLoopGroup serverEventLoopGroup;
    private Channel serverAcceptorChannel;
    private HostAndPort serverAddress;

    @BeforeEach
    void setUp() throws Exception {
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
        }, parentPipeline -> {
            serverParentChannel.set(parentPipeline.channel());
        }, h2Builder -> {
            h2Builder.initialSettings().maxConcurrentStreams(SERVER_MAX_CONCURRENT_STREAMS_VALUE);
            return h2Builder;
        });

        serverAddress = of((InetSocketAddress) serverAcceptorChannel.localAddress());
    }

    @AfterEach
    void tearDown() throws Exception {
        safeSync(() -> serverAcceptorChannel.close().sync());
        safeSync(() -> serverEventLoopGroup.shutdownGracefully(0, 0, MILLISECONDS).sync());
    }

    @RepeatedTest(REPEATED_TEST_ITERATIONS)
    void noMaxActiveStreamsViolatedErrorAfterCancel() throws Exception {
        try (HttpClient client = newClientBuilder(serverAddress, CLIENT_CTX, HTTP_2)
                .appendClientFilter(disableAutoRetries())   // All exceptions should be propagated
                .appendConnectionFilter(MulticastTransportEventsStreamingHttpConnectionFilter.INSTANCE)
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
                        SERVER_MAX_CONCURRENT_STREAMS_VALUE);
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
                assertThat(exceptions, is(empty()));
            }
        }
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
        alwaysEcho.set(true);   // server should always respond
        try (HttpClient client = newClientBuilder(serverAddress, CLIENT_CTX, HTTP_2)
                .executionStrategy(strategy)
                .appendConnectionFilter(MulticastTransportEventsStreamingHttpConnectionFilter.INSTANCE)
                // Don't allow more than 1 connection:
                .appendConnectionFactoryFilter(LimitingConnectionFactoryFilter.withMax(1))
                // Retry all ConnectionLimitReachedException(s), don't retry RetryableException(s):
                .appendClientFilter(new RetryingHttpRequesterFilter.Builder()
                        .maxTotalRetries(Integer.MAX_VALUE)
                        .retryRetryableExceptions((metaData, e) -> {
                            if (e instanceof ConnectionLimitReachedException) {
                                // Use a limit of 128 to avoid StackOverflow
                                return BackOffPolicy.ofImmediate(128);
                            }
                            return BackOffPolicy.ofNoRetries();
                        })
                        .build())
                .protocols(HTTP_2.config)
                .build()) {

            BlockingQueue<Integer> maxConcurrentStreams = new LinkedBlockingDeque<>();
            try (ReservedHttpConnection connection = client.reserveConnection(client.get("/")).map(conn -> {
                conn.transportEventStream(MAX_CONCURRENCY_NO_OFFLOADING)
                        .forEach(event -> maxConcurrentStreams.add(event.event()));
                return conn;
            }).toFuture().get()) {
                awaitMaxConcurrentStreamsSettingsUpdate(connection, maxConcurrentStreams,
                        SERVER_MAX_CONCURRENT_STREAMS_VALUE);
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
