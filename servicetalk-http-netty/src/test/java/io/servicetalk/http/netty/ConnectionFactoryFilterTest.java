/*
 * Copyright © 2019-2020 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.client.api.ConnectionFactoryFilter;
import io.servicetalk.client.api.DelegatingConnectionFactory;
import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.CompletableSource;
import io.servicetalk.concurrent.api.AsyncCloseables;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.CompositeCloseable;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.BlockingHttpClient;
import io.servicetalk.http.api.DelegatingHttpConnectionContext;
import io.servicetalk.http.api.FilterableStreamingHttpConnection;
import io.servicetalk.http.api.HttpConnectionContext;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.HttpResponseMetaData;
import io.servicetalk.http.api.HttpResponseStatus;
import io.servicetalk.http.api.ReservedBlockingHttpConnection;
import io.servicetalk.http.api.ReservedStreamingHttpConnection;
import io.servicetalk.http.api.SingleAddressHttpClientBuilder;
import io.servicetalk.http.api.StreamingHttpConnectionFilter;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.api.TransportObserver;
import io.servicetalk.transport.netty.internal.FlushStrategy;
import io.servicetalk.transport.netty.internal.NettyConnectionContext;

import io.netty.channel.Channel;
import org.junit.After;
import org.junit.Test;

import java.net.InetSocketAddress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.UnaryOperator;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Processors.newCompletableProcessor;
import static io.servicetalk.concurrent.api.SourceAdapters.fromSource;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

public class ConnectionFactoryFilterTest {

    private final ServerContext serverContext;
    private final SingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> clientBuilder;
    @Nullable
    private BlockingHttpClient client;

    public ConnectionFactoryFilterTest() throws Exception {
        serverContext = HttpServers.forAddress(localAddress(0))
                .listenBlockingAndAwait((ctx, request, responseFactory) -> responseFactory.ok());
        clientBuilder = HttpClients.forSingleAddress(serverHostAndPort(serverContext));
    }

    @After
    public void tearDown() throws Exception {
        CompositeCloseable closeable = AsyncCloseables.newCompositeCloseable();
        if (client != null) {
            closeable.append(client.asClient());
        }
        closeable.append(serverContext);
        closeable.close();
    }

    @Test
    public void reserveConnection() throws Exception {
        AtomicInteger activeConnections = new AtomicInteger();
        CountDownLatch doneLatch = new CountDownLatch(1);
        client = clientBuilder.appendConnectionFactoryFilter(
                newConnectionFactoryFilter(connectionCounter(activeConnections, doneLatch)))
                .buildBlocking();
        ReservedBlockingHttpConnection c = client.reserveConnection(client.get("/"));
        assertThat("Unexpected active connections.", activeConnections.get(), is(1));
        c.close();
        doneLatch.await();
        assertThat("Unexpected active connections.", activeConnections.get(), is(0));
    }

    @Test
    public void countConnections() throws Exception {
        AtomicInteger activeConnections = new AtomicInteger();
        client = clientBuilder.appendConnectionFactoryFilter(
                newConnectionFactoryFilter(connectionCounter(activeConnections, null)))
                .buildBlocking();
        sendRequest(client);
        assertThat("Unexpected active connections.", activeConnections.get(), is(1));
    }

    @Test
    public void wrapConnection() throws Exception {
        client = clientBuilder.appendConnectionFactoryFilter(
                newConnectionFactoryFilter(AddResponseHeaderConnectionFilter::new)).buildBlocking();
        HttpResponse response = sendRequest(client);
        AddResponseHeaderConnectionFilter.assertResponseHeader(response);
    }

    @Test
    public void onClosingIsDelegated() throws Exception {
        CompletableSource.Processor onClosing = newCompletableProcessor();
        client = clientBuilder.appendConnectionFactoryFilter(
                newConnectionFactoryFilter(delegate ->
                        new NettyConnectionContextReturningConnection(delegate, onClosing)))
                .buildBlocking();

        ReservedStreamingHttpConnection con = client.asStreamingClient()
                .reserveConnection(client.get("/"))
                .toFuture().get();
        NettyConnectionContext ctx = (NettyConnectionContext) con.connectionContext();
        onClosing.onComplete();
        ctx.onClosing().toFuture().get();
    }

    private static HttpResponse sendRequest(BlockingHttpClient client) throws Exception {
        HttpResponse response = client.request(client.get("/"));
        assertThat("Unexpected response.", response.status(), equalTo(HttpResponseStatus.OK));
        return response;
    }

    @Nonnull
    private static UnaryOperator<FilterableStreamingHttpConnection> connectionCounter(
            final AtomicInteger activeConnections, @Nullable final CountDownLatch doneLatch) {
        return connection -> {
            activeConnections.incrementAndGet();
            connection.onClose().beforeFinally(() -> {
                activeConnections.decrementAndGet();
                if (doneLatch != null) {
                    doneLatch.countDown();
                }
            }).subscribe();
            return connection;
        };
    }

    private static
    ConnectionFactoryFilter<InetSocketAddress, FilterableStreamingHttpConnection> newConnectionFactoryFilter(
            UnaryOperator<FilterableStreamingHttpConnection> filter) {
        return original ->
                new DelegatingConnectionFactory<InetSocketAddress, FilterableStreamingHttpConnection>(original) {
                    @Override
                    public Single<FilterableStreamingHttpConnection> newConnection(
                            final InetSocketAddress inetSocketAddress, @Nullable final TransportObserver observer) {
                        return delegate().newConnection(inetSocketAddress, observer).map(filter);
                    }
                };
    }

    private static class AddResponseHeaderConnectionFilter extends StreamingHttpConnectionFilter {
        private static final String HEADER_FOR_FACTORY = "TouchedByFactory";

        AddResponseHeaderConnectionFilter(final FilterableStreamingHttpConnection c) {
            super(c);
        }

        @Override
        public Single<StreamingHttpResponse> request(final HttpExecutionStrategy strategy,
                                                     final StreamingHttpRequest request) {
            return delegate().request(strategy, request)
                    .map(resp -> resp.addHeader(HEADER_FOR_FACTORY, "true"));
        }

        static void assertResponseHeader(HttpResponseMetaData metaData) {
            assertThat("Expected header not found.", metaData.headers().contains(HEADER_FOR_FACTORY), is(true));
        }
    }

    private static final class NettyConnectionContextReturningConnection extends StreamingHttpConnectionFilter {
        private final HttpConnectionContext ctx;

        NettyConnectionContextReturningConnection(final FilterableStreamingHttpConnection delegate,
                                                  final CompletableSource.Processor onClosing) {
            super(delegate);
            ctx = new DelegatingNettyConnectionContext(delegate.connectionContext(), onClosing);
        }

        @Override
        public HttpConnectionContext connectionContext() {
            return ctx;
        }
   }

    private static final class DelegatingNettyConnectionContext extends DelegatingHttpConnectionContext
            implements NettyConnectionContext {

        private final NettyConnectionContext delegate;
        private final CompletableSource.Processor onClosing;

        DelegatingNettyConnectionContext(final HttpConnectionContext delegate,
                                         final CompletableSource.Processor onClosing) {
            super(delegate);
            this.delegate = (NettyConnectionContext) delegate;
            this.onClosing = onClosing;
        }

        @Override
        public Channel nettyChannel() {
            return delegate.nettyChannel();
        }

        @Override
        public Cancellable updateFlushStrategy(final FlushStrategyProvider strategyProvider) {
            return delegate.updateFlushStrategy(strategyProvider);
        }

        @Override
        public FlushStrategy defaultFlushStrategy() {
            return delegate.defaultFlushStrategy();
        }

        @Override
        public Single<Throwable> transportError() {
            return delegate.transportError();
        }

        @Override
        public Completable onClosing() {
            return fromSource(onClosing);
        }
    }
}
