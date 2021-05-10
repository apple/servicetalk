/*
 * Copyright © 2019-2021 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.client.api.ConnectionFactory;
import io.servicetalk.client.api.DefaultAutoRetryStrategyProvider;
import io.servicetalk.client.api.DelegatingConnectionFactory;
import io.servicetalk.client.api.LoadBalancedConnection;
import io.servicetalk.client.api.LoadBalancer;
import io.servicetalk.client.api.LoadBalancerFactory;
import io.servicetalk.client.api.ServiceDiscovererEvent;
import io.servicetalk.concurrent.api.AsyncCloseables;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.CompositeCloseable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.BlockingHttpClient;
import io.servicetalk.http.api.FilterableStreamingHttpConnection;
import io.servicetalk.http.api.SingleAddressHttpClientBuilder;
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.api.RetryableException;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.api.TransportObserver;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import javax.annotation.Nullable;

import static io.servicetalk.client.api.AutoRetryStrategyProvider.DISABLE_AUTO_RETRIES;
import static io.servicetalk.concurrent.api.Single.defer;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.http.netty.HttpClients.forSingleAddress;
import static io.servicetalk.http.netty.HttpServers.forAddress;
import static io.servicetalk.loadbalancer.RoundRobinLoadBalancer.newRoundRobinFactory;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

class AutoRetryTest {
    private final ServerContext svcCtx;
    private final SingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> clientBuilder;
    private final AtomicInteger lbSelectInvoked;

    @Nullable
    private BlockingHttpClient client;

    AutoRetryTest() throws Exception {
        svcCtx = forAddress(localAddress(0))
                .listenBlockingAndAwait((ctx, request, responseFactory) -> responseFactory.ok());
        clientBuilder = forSingleAddress(serverHostAndPort(svcCtx))
                .loadBalancerFactory(DefaultHttpLoadBalancerFactory.Builder
                        .from(new InspectingLoadBalancerFactory<>()).build())
                .appendConnectionFactoryFilter(ClosingConnectionFactory::new);
        lbSelectInvoked = new AtomicInteger();
    }

    @AfterEach
    void tearDown() throws Exception {
        CompositeCloseable closeable = AsyncCloseables.newCompositeCloseable();
        if (client != null) {
            closeable.append(client.asClient());
        }
        closeable.append(svcCtx);
        closeable.close();
    }

    @Test
    void disableAutoRetry() {
        client = clientBuilder
                .autoRetryStrategy(DISABLE_AUTO_RETRIES)
                .buildBlocking();
        Exception e = assertThrows(Exception.class, () -> client.request(client.get("/")));
        assertThat(e, instanceOf(RetryableException.class));
    }

    @Test
    void updateMaxRetry() {
        client = clientBuilder
                .autoRetryStrategy(new DefaultAutoRetryStrategyProvider.Builder().maxRetries(1).build())
                .buildBlocking();
        try {
            client.request(client.get("/"));
            fail("Request is expected to fail.");
        } catch (Exception e) {
            assertThat("Unexpected exception.", e, instanceOf(RetryableException.class));
            assertThat("Unexpected calls to select.", lbSelectInvoked.get(), is(2));
        }
    }

    private final class InspectingLoadBalancerFactory<C extends LoadBalancedConnection>
            implements LoadBalancerFactory<InetSocketAddress, C> {

        private final LoadBalancerFactory<InetSocketAddress, C> rr =
                newRoundRobinFactory();

        @Override
        public <T extends C> LoadBalancer<T> newLoadBalancer(
                final Publisher<? extends ServiceDiscovererEvent<InetSocketAddress>> eventPublisher,
                final ConnectionFactory<InetSocketAddress, T> connectionFactory) {
            return new InspectingLoadBalancer<>(rr.newLoadBalancer(eventPublisher, connectionFactory));
        }
    }

    private final class InspectingLoadBalancer<C extends LoadBalancedConnection> implements LoadBalancer<C> {
        private final LoadBalancer<C> delegate;

        private InspectingLoadBalancer(final LoadBalancer<C> delegate) {
            this.delegate = delegate;
        }

        @Override
        public Single<C> selectConnection(final Predicate<C> selector) {
            return defer(() -> {
                lbSelectInvoked.incrementAndGet();
                return delegate.selectConnection(selector);
            });
        }

        @Override
        public Publisher<Object> eventStream() {
            return delegate.eventStream();
        }

        @Override
        public Completable onClose() {
            return delegate.onClose();
        }

        @Override
        public Completable closeAsync() {
            return delegate.closeAsync();
        }

        @Override
        public Completable closeAsyncGracefully() {
            return delegate.closeAsyncGracefully();
        }
    }

    private static final class ClosingConnectionFactory
            extends DelegatingConnectionFactory<InetSocketAddress, FilterableStreamingHttpConnection> {
        ClosingConnectionFactory(
                final ConnectionFactory<InetSocketAddress, FilterableStreamingHttpConnection> original) {
            super(original);
        }

        @Override
        public Single<FilterableStreamingHttpConnection> newConnection(final InetSocketAddress inetSocketAddress,
                                                                       @Nullable final TransportObserver observer) {
            return delegate().newConnection(inetSocketAddress, observer)
                    .flatMap(c -> c.closeAsync().concat(succeeded(c)));
        }
    }
}
