/*
 * Copyright © 2021 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.http.netty.StreamObserverTest.MulticastTransportEventsStreamingHttpConnectionFilter;
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.netty.internal.ExecutionContextExtension;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http2.Http2HeadersFrame;
import io.netty.handler.codec.http2.Http2StreamChannel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.net.InetSocketAddress;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.Nullable;

import static io.servicetalk.client.api.AutoRetryStrategyProvider.DISABLE_AUTO_RETRIES;
import static io.servicetalk.http.api.HttpEventKey.MAX_CONCURRENCY;
import static io.servicetalk.http.api.HttpExecutionStrategies.defaultStrategy;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.netty.H2PriorKnowledgeFeatureParityTest.EchoHttp2Handler;
import static io.servicetalk.http.netty.H2PriorKnowledgeFeatureParityTest.bindH2Server;
import static io.servicetalk.http.netty.HttpClients.forResolvedAddress;
import static io.servicetalk.http.netty.HttpProtocolConfigs.h2;
import static io.servicetalk.http.netty.HttpsProxyTest.safeClose;
import static io.servicetalk.http.netty.StreamObserverTest.safeSync;
import static io.servicetalk.logging.api.LogLevel.TRACE;
import static io.servicetalk.transport.api.HostAndPort.of;
import static io.servicetalk.transport.netty.internal.NettyIoExecutors.createIoExecutor;
import static java.lang.Integer.parseInt;
import static java.time.Duration.ofMillis;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;

class H2ConcurrencyControllerTest {

    private static final long MAX_CONCURRENT_STREAMS_VALUE = 1L;
    private static final int N_ITERATIONS = 3;

    @RegisterExtension
    static final ExecutionContextExtension CTX =
        ExecutionContextExtension.cached("client-io", "client-executor");

    private EventLoopGroup serverEventLoopGroup;
    private Channel serverAcceptorChannel;
    private HttpClient client;
    private final CountDownLatch[] latches = new CountDownLatch[N_ITERATIONS];

    @BeforeEach
    void setUp() {
        serverEventLoopGroup = createIoExecutor(1, "server-io").eventLoopGroup();
        for (int i = 0; i < N_ITERATIONS; i++) {
            latches[i] = new CountDownLatch(1);
        }
        AtomicBoolean secondAndMore = new AtomicBoolean();
        serverAcceptorChannel = bindH2Server(serverEventLoopGroup, new ChannelInitializer<Http2StreamChannel>() {
            @Override
            protected void initChannel(Http2StreamChannel ch) {
                // Respond only for the first request which is used to propagate MAX_CONCURRENT_STREAMS_VALUE
                if (secondAndMore.compareAndSet(false, true)) {
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
        }, h2Builder -> {
            h2Builder.initialSettings().maxConcurrentStreams(MAX_CONCURRENT_STREAMS_VALUE);
            return h2Builder;
        });
        final HostAndPort serverAddress = of((InetSocketAddress) serverAcceptorChannel.localAddress());
        client = forResolvedAddress(serverAddress)
            .ioExecutor(CTX.ioExecutor())
            .executionStrategy(defaultStrategy(CTX.executor()))
            .autoRetryStrategy(DISABLE_AUTO_RETRIES)    // All exceptions should be propagated
            .appendConnectionFilter(MulticastTransportEventsStreamingHttpConnectionFilter::new)
            .appendConnectionFilter(connection -> new StreamingHttpConnectionFilter(connection) {
                @Override
                public Single<StreamingHttpResponse> request(HttpExecutionStrategy strategy,
                                                             StreamingHttpRequest request) {
                    return delegate().request(strategy, request)
                        .liftSync(subscriber -> new SingleSource.Subscriber<StreamingHttpResponse>() {
                            @Override
                            public void onSubscribe(final Cancellable cancellable) {
                                // Defer the cancel() signal to let the test thread start a new request
                                subscriber.onSubscribe(() -> CTX.executor()
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
            .protocols(h2().enableFrameLogging("servicetalk-tests-h2-frame-logger", TRACE, () -> true).build())
            .build();
    }

    @AfterEach
    void tearDown() {
        safeSync(() -> serverAcceptorChannel.close().syncUninterruptibly());
        safeSync(() -> serverEventLoopGroup.shutdownGracefully(0, 0, MILLISECONDS).syncUninterruptibly());
        safeClose(client);
    }

    @Test
    void noMaxActiveStreamsViolatedError() throws Exception {
        CountDownLatch maxConcurrencyUpdated = new CountDownLatch(1);
        try (ReservedHttpConnection connection = client.reserveConnection(client.get("/")).map(conn -> {
            conn.transportEventStream(MAX_CONCURRENCY).forEach(event -> {
                if (event.event() == MAX_CONCURRENT_STREAMS_VALUE) {
                    maxConcurrencyUpdated.countDown();
                }
            });
            return conn;
        }).toFuture().get()) {
            awaitMaxConcurrentStreamsSettingsUpdate(connection, maxConcurrencyUpdated);

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

    private static void awaitMaxConcurrentStreamsSettingsUpdate(ReservedHttpConnection connection,
                                                                CountDownLatch latch) throws Exception {
        HttpResponse response = connection.request(connection.get("/")).toFuture().get();
        assertThat(response.status(), is(OK));
        latch.await();
        connection.releaseAsync().toFuture().get();
    }
}
