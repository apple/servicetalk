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

import io.servicetalk.client.api.ConnectionFactory;
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
import io.servicetalk.context.api.ContextMap;
import io.servicetalk.http.api.BlockingHttpClient;
import io.servicetalk.http.api.FilterableStreamingHttpConnection;
import io.servicetalk.http.api.SingleAddressHttpClientBuilder;
import io.servicetalk.http.api.StreamingHttpConnectionFilter;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.loadbalancer.RoundRobinLoadBalancerFactory;
import io.servicetalk.transport.api.ExecutionStrategy;
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.api.RetryableException;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.api.TransportObserver;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Single.defer;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.http.netty.HttpClients.forResolvedAddress;
import static io.servicetalk.http.netty.HttpClients.forSingleAddress;
import static io.servicetalk.http.netty.HttpServers.forAddress;
import static io.servicetalk.http.netty.RetryingHttpRequesterFilter.BackOffPolicy.ofImmediate;
import static io.servicetalk.http.netty.RetryingHttpRequesterFilter.BackOffPolicy.ofNoRetries;
import static io.servicetalk.http.netty.RetryingHttpRequesterFilter.Builder;
import static io.servicetalk.http.netty.RetryingHttpRequesterFilter.HttpResponseException;
import static io.servicetalk.http.netty.RetryingHttpRequesterFilter.disableAutoRetries;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RetryingHttpRequesterFilterTest {

    private static final String RETRYABLE_HEADER = "RETRYABLE";

    private final ServerContext svcCtx;
    private final SingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> normalClientBuilder;
    private final SingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> failingConnClientBuilder;
    private final AtomicInteger lbSelectInvoked;

    @Nullable
    private BlockingHttpClient normalClient;

    @Nullable
    private BlockingHttpClient failingClient;

    RetryingHttpRequesterFilterTest() throws Exception {
        svcCtx = forAddress(localAddress(0))
                .listenBlockingAndAwait((ctx, request, responseFactory) -> responseFactory.ok()
                        .addHeader(RETRYABLE_HEADER, "yes"));
        failingConnClientBuilder = forSingleAddress(serverHostAndPort(svcCtx))
                .loadBalancerFactory(DefaultHttpLoadBalancerFactory.Builder
                        .from(new InspectingLoadBalancerFactory<>()).build())
                .appendConnectionFactoryFilter(ClosingConnectionFactory::new);
        normalClientBuilder = forSingleAddress(serverHostAndPort(svcCtx))
                .loadBalancerFactory(DefaultHttpLoadBalancerFactory.Builder
                        .from(new InspectingLoadBalancerFactory<>()).build());
        lbSelectInvoked = new AtomicInteger();
    }

    @AfterEach
    void tearDown() throws Exception {
        CompositeCloseable closeable = AsyncCloseables.newCompositeCloseable();
        if (normalClient != null) {
            closeable.append(normalClient.asClient());
        }
        if (failingClient != null) {
            closeable.append(failingClient.asClient());
        }
        closeable.append(svcCtx);
        closeable.close();
    }

    @Test
    void disableAutoRetry() {
        failingClient = failingConnClientBuilder
                .appendClientFilter(disableAutoRetries())
                .buildBlocking();
        Exception e = assertThrows(Exception.class, () -> failingClient.request(failingClient.get("/")));
        assertThat(e, instanceOf(RetryableException.class));
        assertThat("Unexpected calls to select.", lbSelectInvoked.get(), is(1));
    }

    @Test
    void maxTotalRetries() {
        failingClient = failingConnClientBuilder
                .appendClientFilter(new Builder().maxTotalRetries(1).build())
                .buildBlocking();
        Exception e = assertThrows(Exception.class, () -> failingClient.request(failingClient.get("/")));
        assertThat("Unexpected exception.", e, instanceOf(RetryableException.class));
        assertThat("Unexpected calls to select.", lbSelectInvoked.get(), is(2));
    }

    @Test
    void requestRetryingPredicate() {
        failingClient = failingConnClientBuilder
                .appendClientFilter(new Builder()
                        .retryRetryableExceptions((requestMetaData, e) -> ofNoRetries())
                        .retryOther((requestMetaData, throwable) ->
                                requestMetaData.requestTarget().equals("/retry") ? ofImmediate() :
                                        ofNoRetries()).build())
                .buildBlocking();
        assertRequestRetryingPred(failingClient);
    }

    @Test
    void requestRetryingPredicateWithConditionalAppend() {
        failingClient = failingConnClientBuilder
                .appendClientFilter((__) -> true, new Builder()
                        .retryRetryableExceptions((requestMetaData, e) -> ofNoRetries())
                        .retryOther((requestMetaData, throwable) ->
                                requestMetaData.requestTarget().equals("/retry") ? ofImmediate() :
                                        ofNoRetries()).build())
                .buildBlocking();
        assertRequestRetryingPred(failingClient);
    }

    private void assertRequestRetryingPred(final BlockingHttpClient client) {
        Exception e = assertThrows(Exception.class, () -> client.request(client.get("/")));
        assertThat("Unexpected exception.", e, instanceOf(RetryableException.class));
        // Account for LB readiness
        assertThat("Unexpected calls to select.", (double) lbSelectInvoked.get(), closeTo(1.0, 1.0));

        e = assertThrows(Exception.class, () -> client.request(client.get("/retry")));
        assertThat("Unexpected exception.", e, instanceOf(RetryableException.class));
        // 1 Run + 3 Retries + 1 residual count from previous request + account for LB readiness
        assertThat("Unexpected calls to select.", (double) lbSelectInvoked.get(), closeTo(5.0, 1.0));
    }

    @Test
    void testResponseMapper() {
        AtomicInteger newConnectionCreated = new AtomicInteger();
        AtomicInteger responseDrained = new AtomicInteger();
        final int maxTotalRetries = 4;
        normalClient = normalClientBuilder
                .appendClientFilter(new Builder()
                        .maxTotalRetries(maxTotalRetries)
                        .responseMapper(metaData -> metaData.headers().contains(RETRYABLE_HEADER) ?
                                    new HttpResponseException("Retryable header", metaData) : null)
                        // Disable request retrying
                        .retryRetryableExceptions((requestMetaData, e) -> ofNoRetries())
                        // Retry only responses marked so
                        .retryResponses((requestMetaData, throwable) -> ofImmediate(maxTotalRetries - 1))
                        .build())
                .appendConnectionFilter(c -> {
                    newConnectionCreated.incrementAndGet();
                    return new StreamingHttpConnectionFilter(c) {
                        @Override
                        public Single<StreamingHttpResponse> request(final StreamingHttpRequest request) {
                            return delegate().request(request)
                                    .map(response -> response.transformPayloadBody(payload -> payload
                                            .whenFinally(responseDrained::incrementAndGet)));
                        }
                    };
                })
                .buildBlocking();
        HttpResponseException e = assertThrows(HttpResponseException.class,
                () -> normalClient.request(normalClient.get("/")));
        assertThat("Unexpected exception.", e, instanceOf(HttpResponseException.class));
        // The load balancer is allowed to be not ready one time, which is counted against total retry attempts but not
        // against actual requests being issued.
        assertThat("Unexpected calls to select.", lbSelectInvoked.get(), allOf(greaterThanOrEqualTo(maxTotalRetries),
                lessThanOrEqualTo(maxTotalRetries + 1)));
        assertThat("Response payload body was not drained on every mapping", responseDrained.get(),
                is(maxTotalRetries));
        assertThat("Unexpected number of connections was created", newConnectionCreated.get(), is(1));
    }

    @Test()
    void singleInstanceFilter() {
        Assertions.assertThrows(IllegalStateException.class, () -> forResolvedAddress(localAddress(8888))
                .appendClientFilter(new Builder().build())
                .appendClientFilter(new Builder().build())
                .build());
    }

    private final class InspectingLoadBalancerFactory<C extends LoadBalancedConnection>
            implements LoadBalancerFactory<InetSocketAddress, C> {

        private final LoadBalancerFactory<InetSocketAddress, C> rr =
                new RoundRobinLoadBalancerFactory.Builder<InetSocketAddress, C>().build();

        @SuppressWarnings("deprecation")
        @Override
        public <T extends C> LoadBalancer<T> newLoadBalancer(
                final String targetResource,
                final Publisher<? extends Collection<? extends ServiceDiscovererEvent<InetSocketAddress>>>
                        eventPublisher,
                final ConnectionFactory<InetSocketAddress, T> connectionFactory) {
            return new InspectingLoadBalancer<>(rr.newLoadBalancer(targetResource, eventPublisher, connectionFactory));
        }

        @Override
        public LoadBalancer<C> newLoadBalancerTyped(
                final String targetResource,
                final Publisher<? extends Collection<? extends ServiceDiscovererEvent<InetSocketAddress>>>
                        eventPublisher,
                final ConnectionFactory<InetSocketAddress, C> connectionFactory) {
            return new InspectingLoadBalancer<>(
                    rr.newLoadBalancerTyped(targetResource, eventPublisher, connectionFactory));
        }

        @Override
        public ExecutionStrategy requiredOffloads() {
            return ExecutionStrategy.offloadNone();
        }
    }

    private final class InspectingLoadBalancer<C extends LoadBalancedConnection> implements LoadBalancer<C> {
        private final LoadBalancer<C> delegate;

        private InspectingLoadBalancer(final LoadBalancer<C> delegate) {
            this.delegate = delegate;
        }

        @Override
        public Single<C> selectConnection(final Predicate<C> selector, @Nullable ContextMap context) {
            return defer(() -> {
                lbSelectInvoked.incrementAndGet();
                return delegate.selectConnection(selector, context);
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
        public Completable onClosing() {
            return delegate.onClosing();
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
                                                                       @Nullable final ContextMap context,
                                                                       @Nullable final TransportObserver observer) {
            return delegate().newConnection(inetSocketAddress, context, observer)
                    .flatMap(c -> c.closeAsync().concat(succeeded(c)));
        }
    }
}
