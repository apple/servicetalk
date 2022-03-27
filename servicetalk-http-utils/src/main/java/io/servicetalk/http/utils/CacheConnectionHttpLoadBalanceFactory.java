/*
 * Copyright © 2022 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.http.utils;

import io.servicetalk.client.api.ConnectionFactory;
import io.servicetalk.client.api.ConsumableEvent;
import io.servicetalk.client.api.LoadBalancer;
import io.servicetalk.client.api.LoadBalancerFactory;
import io.servicetalk.client.api.ServiceDiscovererEvent;
import io.servicetalk.concurrent.PublisherSource;
import io.servicetalk.concurrent.PublisherSource.Subscriber;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.context.api.ContextMap;
import io.servicetalk.http.api.DelegatingFilterableStreamingHttpConnection;
import io.servicetalk.http.api.FilterableStreamingHttpLoadBalancedConnection;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpLoadBalancerFactory;
import io.servicetalk.transport.api.TransportObserver;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.ToIntFunction;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.http.api.HttpEventKey.MAX_CONCURRENCY;
import static java.util.Objects.requireNonNull;

/**
 * A {@link HttpLoadBalancerFactory} that will cache successive connection creation attempts and return the same
 * {@link Single} instead of creating a new connection each time. This is useful when a spike of connections occurs
 * instead of creating a new connection for each request, if the underlying protocol version supports concurrency
 * (pipelining, multiplexing) a single connection creation attempt can be used before the connection is actually
 * established, which will reduce the overall number of connectiosn required.
 * @param <ResolvableAddress> The resolved address type.
 */
public final class CacheConnectionHttpLoadBalanceFactory<ResolvableAddress>
        implements HttpLoadBalancerFactory<ResolvableAddress> {
    private final ToIntFunction<ResolvableAddress> maxConcurrencyFunc;
    private final HttpLoadBalancerFactory<ResolvableAddress> delegate;

    /**
     * Create a new instance.
     *
     * @param delegate The {@link LoadBalancerFactory} to delegate to.
     * @param maxConcurrencyFunc The default number of maximum concurrency requests per each address.
     */
    public CacheConnectionHttpLoadBalanceFactory(final HttpLoadBalancerFactory<ResolvableAddress> delegate,
                                                 final ToIntFunction<ResolvableAddress> maxConcurrencyFunc) {
        this.maxConcurrencyFunc = requireNonNull(maxConcurrencyFunc);
        this.delegate = requireNonNull(delegate);
    }

    @Override
    public <T extends FilterableStreamingHttpLoadBalancedConnection> LoadBalancer<T> newLoadBalancer(
            final String targetResource,
            final Publisher<? extends Collection<? extends ServiceDiscovererEvent<ResolvableAddress>>> eventPublisher,
            final ConnectionFactory<ResolvableAddress, T> connectionFactory) {
        throw new UnsupportedOperationException("Use newLoadBalancerTyped instead.");
    }

    @Override
    public LoadBalancer<FilterableStreamingHttpLoadBalancedConnection> newLoadBalancerTyped(
        final String targetResource,
        final Publisher<? extends Collection<? extends ServiceDiscovererEvent<ResolvableAddress>>> eventPublisher,
        final ConnectionFactory<ResolvableAddress, FilterableStreamingHttpLoadBalancedConnection> connectionFactory) {
        final HttpCacheConnectionFactory<ResolvableAddress> cacheFactory =
                new HttpCacheConnectionFactory<>(connectionFactory, maxConcurrencyFunc);
        return delegate.newLoadBalancerTyped(targetResource, eventPublisher,
                new CacheConnectionFactory<>(cacheFactory, r -> {
                    ConcurrencyRefCnt v = cacheFactory.maxConcurrentMap.get(r);
                    return v == null ? maxConcurrencyFunc.applyAsInt(r) : v.concurrency;
                }));
    }

    @Override
    public HttpExecutionStrategy requiredOffloads() {
        return delegate.requiredOffloads();
    }

    private static final class HttpCacheConnectionFactory<RA>
            implements ConnectionFactory<RA, FilterableStreamingHttpLoadBalancedConnection> {
        private final Map<RA, ConcurrencyRefCnt> maxConcurrentMap = new ConcurrentHashMap<>();
        private final ToIntFunction<RA> maxConcurrencyFunc;
        private final ConnectionFactory<RA, FilterableStreamingHttpLoadBalancedConnection> delegate;

        private HttpCacheConnectionFactory(
                final ConnectionFactory<RA, FilterableStreamingHttpLoadBalancedConnection> delegate,
                final ToIntFunction<RA> maxConcurrencyFunc) {
            this.delegate = requireNonNull(delegate);
            this.maxConcurrencyFunc = requireNonNull(maxConcurrencyFunc);
        }

        @Override
        public Single<FilterableStreamingHttpLoadBalancedConnection> newConnection(
                final RA resolvedAddress, @Nullable final ContextMap context,
                @Nullable final TransportObserver observer) {
            return delegate.newConnection(resolvedAddress, context, observer)
                    .map(connection -> new MaxConcurrencyFilterableStreamingHttpConnection<>(connection,
                            maxConcurrentMap, resolvedAddress, maxConcurrencyFunc));
        }

        @Override
        public Completable closeAsync() {
            return delegate.closeAsync();
        }

        @Override
        public Completable closeAsyncGracefully() {
            return delegate.closeAsyncGracefully();
        }

        @Override
        public Completable onClose() {
            return delegate.onClose();
        }
    }

    private static final class MaxConcurrencyFilterableStreamingHttpConnection<RA>
            extends DelegatingFilterableStreamingHttpConnection {
        MaxConcurrencyFilterableStreamingHttpConnection(final FilterableStreamingHttpLoadBalancedConnection delegate,
                                                        final Map<RA, ConcurrencyRefCnt> maxConcurrentMap,
                                                        final RA resolvedAddress,
                                                        final ToIntFunction<RA> concurrencyEstimator) {
            super(delegate);
            // For each new connection we increment the reference count. Even if the estimate is <=1 we still track
            // the connection because we could learn the actual concurrency after connections are established and
            // the protocol is negotiated. For example if the connection supports h1 and h2, and we negotiate h2 we
            // will likely find out the connection can handle more concurrency.
            maxConcurrentMap.compute(resolvedAddress, (ra, refCnt) -> refCnt == null ?
                    new ConcurrencyRefCnt(concurrencyEstimator.applyAsInt(ra), 1) :
                    new ConcurrencyRefCnt(refCnt.concurrency, refCnt.refCnt + 1));
            toSource(delegate.<ConsumableEvent<Integer>>transportEventStream(MAX_CONCURRENCY))
                    .subscribe(new Subscriber<ConsumableEvent<Integer>>() {
                        @Override
                        public void onSubscribe(final PublisherSource.Subscription subscription) {
                            subscription.request(Long.MAX_VALUE);
                        }

                        @Override
                        public void onNext(@Nullable final ConsumableEvent<Integer> integerConsumableEvent) {
                            if (integerConsumableEvent != null) {
                                final int concurrency = integerConsumableEvent.event();
                                // Connections may set their max concurrency to 0 before shutting down.
                                // Map cleanup is done in terminal method.
                                if (concurrency > 0) {
                                    maxConcurrentMap.computeIfPresent(resolvedAddress,
                                            (ra, refCnt) -> new ConcurrencyRefCnt(concurrency, refCnt.refCnt));
                                }
                            }
                        }

                        @Override
                        public void onError(final Throwable t) {
                            decrementRefCnt();
                        }

                        @Override
                        public void onComplete() {
                            decrementRefCnt();
                        }

                        private void decrementRefCnt() {
                            // When the connection is closed we decrement the reference count.
                            maxConcurrentMap.computeIfPresent(resolvedAddress, (ra, refCnt) ->
                                    refCnt.refCnt <= 1 ? null :
                                            new ConcurrencyRefCnt(refCnt.concurrency, refCnt.refCnt - 1));
                        }
                    });
        }
    }

    private static final class ConcurrencyRefCnt {
        final int concurrency;
        final int refCnt;

        private ConcurrencyRefCnt(final int concurrency, final int refCnt) {
            this.concurrency = concurrency;
            this.refCnt = refCnt;
        }
    }
}
