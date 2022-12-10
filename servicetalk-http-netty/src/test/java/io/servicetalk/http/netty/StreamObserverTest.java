/*
 * Copyright © 2020-2021 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.client.api.ConsumableEvent;
import io.servicetalk.client.api.TransportObserverConnectionFactoryFilter;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.http.api.FilterableStreamingHttpConnection;
import io.servicetalk.http.api.Http2Exception;
import io.servicetalk.http.api.HttpClient;
import io.servicetalk.http.api.HttpConnection;
import io.servicetalk.http.api.HttpEventKey;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.StreamingHttpConnectionFilter;
import io.servicetalk.http.api.StreamingHttpConnectionFilterFactory;
import io.servicetalk.transport.api.ConnectionInfo;
import io.servicetalk.transport.api.ConnectionObserver;
import io.servicetalk.transport.api.ConnectionObserver.DataObserver;
import io.servicetalk.transport.api.ConnectionObserver.MultiplexedObserver;
import io.servicetalk.transport.api.ConnectionObserver.ReadObserver;
import io.servicetalk.transport.api.ConnectionObserver.StreamObserver;
import io.servicetalk.transport.api.ConnectionObserver.WriteObserver;
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.api.RetryableException;
import io.servicetalk.transport.api.TransportObserver;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http2.Http2Exception.StreamException;
import io.netty.handler.codec.http2.Http2StreamChannel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.channels.ClosedChannelException;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;

import static io.servicetalk.http.api.HttpExecutionStrategies.offloadNone;
import static io.servicetalk.http.netty.AbstractStreamingHttpConnection.MAX_CONCURRENCY_NO_OFFLOADING;
import static io.servicetalk.http.netty.H2PriorKnowledgeFeatureParityTest.bindH2Server;
import static io.servicetalk.http.netty.HttpProtocolConfigs.h2;
import static io.servicetalk.http.netty.HttpTransportObserverTest.await;
import static io.servicetalk.http.netty.HttpsProxyTest.safeClose;
import static io.servicetalk.logging.api.LogLevel.TRACE;
import static io.servicetalk.transport.netty.internal.NettyIoExecutors.createIoExecutor;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class StreamObserverTest {

    private final TransportObserver clientTransportObserver;
    private final ConnectionObserver clientConnectionObserver;
    private final MultiplexedObserver clientMultiplexedObserver;
    private final StreamObserver clientStreamObserver;
    private final DataObserver clientDataObserver;
    private final ReadObserver clientReadObserver;
    private final WriteObserver clientWriteObserver;

    private final EventLoopGroup serverEventLoopGroup;
    private final Channel serverAcceptorChannel;
    private final HttpClient client;
    private final CountDownLatch requestReceived = new CountDownLatch(1);

    StreamObserverTest() throws Exception {
        clientTransportObserver = mock(TransportObserver.class, "clientTransportObserver");
        clientConnectionObserver = mock(ConnectionObserver.class, "clientConnectionObserver");
        clientMultiplexedObserver = mock(MultiplexedObserver.class, "clientMultiplexedObserver");
        clientStreamObserver = mock(StreamObserver.class, "clientStreamObserver");
        clientDataObserver = mock(DataObserver.class, "clientDataObserver");
        clientReadObserver = mock(ReadObserver.class, "clientReadObserver");
        clientWriteObserver = mock(WriteObserver.class, "clientWriteObserver");
        when(clientTransportObserver.onNewConnection(any(), any())).thenReturn(clientConnectionObserver);
        when(clientConnectionObserver.multiplexedConnectionEstablished(any(ConnectionInfo.class)))
                .thenReturn(clientMultiplexedObserver);
        when(clientMultiplexedObserver.onNewStream()).thenReturn(clientStreamObserver);
        when(clientStreamObserver.streamEstablished()).thenReturn(clientDataObserver);
        when(clientDataObserver.onNewRead()).thenReturn(clientReadObserver);
        when(clientDataObserver.onNewWrite()).thenReturn(clientWriteObserver);

        serverEventLoopGroup = createIoExecutor(2, "server-io").eventLoopGroup();
        serverAcceptorChannel = bindH2Server(serverEventLoopGroup, new ChannelInitializer<Http2StreamChannel>() {
            @Override
            protected void initChannel(final Http2StreamChannel ch) {
                ch.pipeline().addLast(new SimpleChannelInboundHandler<Object>() {
                    @Override
                    protected void channelRead0(final ChannelHandlerContext ctx, final Object msg) {
                        requestReceived.countDown();
                    }
                });
            }
        }, parentPipeline -> { }, h2Builder -> {
            h2Builder.initialSettings().maxConcurrentStreams(1L);
            return h2Builder;
        });
        client = HttpClients.forSingleAddress(HostAndPort.of((InetSocketAddress) serverAcceptorChannel.localAddress()))
                .protocols(h2().enableFrameLogging("servicetalk-tests-h2-frame-logger", TRACE, () -> true).build())
                .appendConnectionFilter(MulticastTransportEventsStreamingHttpConnectionFilter.INSTANCE)
                .appendConnectionFactoryFilter(new TransportObserverConnectionFactoryFilter<>(clientTransportObserver))
                .build();
    }

    @AfterEach
    void teardown() throws Exception {
        safeSync(() -> serverAcceptorChannel.close().sync());
        safeSync(() -> serverEventLoopGroup.shutdownGracefully(0, 0, MILLISECONDS).sync());
        safeClose(client);
    }

    static void safeSync(Callable<Object> callable) throws Exception {
        try {
            callable.call();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw e;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Disabled("https://github.com/apple/servicetalk/issues/1264")
    @Test
    void maxActiveStreamsViolationError() throws Exception {
        CountDownLatch maxConcurrentStreamsValueSetToOne = new CountDownLatch(1);
        try (HttpConnection connection = client.reserveConnection(client.get("/")).map(conn -> {
            conn.transportEventStream(MAX_CONCURRENCY_NO_OFFLOADING).forEach(event -> {
                if (event.event() == 1) {
                    maxConcurrentStreamsValueSetToOne.countDown();
                }
            });
            return conn;
        }).toFuture().get()) {
            verify(clientTransportObserver).onNewConnection(any(), any());
            verify(clientConnectionObserver).multiplexedConnectionEstablished(any(ConnectionInfo.class));

            connection.request(connection.get("/first")).subscribe(__ -> { /* no response expected */ });
            requestReceived.await();
            maxConcurrentStreamsValueSetToOne.await();

            ExecutionException e = assertThrows(ExecutionException.class,
                    () -> connection.request(connection.get("/second")).toFuture().get());
            assertThat(e.getCause(), instanceOf(Http2Exception.class));
            assertThat(e.getCause(), instanceOf(RetryableException.class));
            assertThat(e.getCause().getCause(), instanceOf(StreamException.class));

            verify(clientMultiplexedObserver, times(2)).onNewStream();
            verify(clientStreamObserver, times(2)).streamEstablished();
            verify(clientDataObserver, times(2)).onNewRead();
            verify(clientDataObserver, times(2)).onNewWrite();
            verify(clientReadObserver).readFailed(any(ClosedChannelException.class));
            verify(clientWriteObserver).writeFailed(e.getCause());
            verify(clientStreamObserver, await()).streamClosed(e.getCause());
        }
        verify(clientStreamObserver, await()).streamClosed();
        verify(clientConnectionObserver).connectionClosed();

        verifyNoMoreInteractions(clientTransportObserver, clientMultiplexedObserver, clientStreamObserver,
                clientDataObserver);
    }

    /**
     * Filter that allows tests to subscribe to
     * {@link FilterableStreamingHttpConnection#transportEventStream(HttpEventKey)} without loosing already delivered
     * events.
     */
    static final class MulticastTransportEventsStreamingHttpConnectionFilter
            implements StreamingHttpConnectionFilterFactory {

        static final StreamingHttpConnectionFilterFactory INSTANCE =
                new MulticastTransportEventsStreamingHttpConnectionFilter();

        private MulticastTransportEventsStreamingHttpConnectionFilter() {
            // Singleton.
        }

        @Override
        public StreamingHttpConnectionFilter create(FilterableStreamingHttpConnection connection) {
            Publisher<? extends ConsumableEvent<Integer>> maxConcurrency = connection
                    .transportEventStream(MAX_CONCURRENCY_NO_OFFLOADING).multicast(2);
            return new StreamingHttpConnectionFilter(connection) {
                @Override
                @SuppressWarnings("unchecked")
                public <T> Publisher<? extends T> transportEventStream(final HttpEventKey<T> eventKey) {
                    return eventKey == MAX_CONCURRENCY_NO_OFFLOADING ? (Publisher<? extends T>) maxConcurrency :
                            delegate().transportEventStream(eventKey);
                }
            };
        }

        @Override
        public HttpExecutionStrategy requiredOffloads() {
            return offloadNone();
        }
    }
}
