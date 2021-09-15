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

import io.servicetalk.client.api.ConnectionFactory;
import io.servicetalk.client.api.LoadBalancer;
import io.servicetalk.client.api.LoadBalancerFactory;
import io.servicetalk.client.api.ServiceDiscovererEvent;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.FilterableStreamingHttpConnection;
import io.servicetalk.http.api.FilterableStreamingHttpLoadBalancedConnection;
import io.servicetalk.http.api.HttpConnectionContext;
import io.servicetalk.http.api.HttpEventKey;
import io.servicetalk.http.api.HttpExecutionContext;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpExecutionStrategyInfluencer;
import io.servicetalk.http.api.HttpLoadBalancerFactory;
import io.servicetalk.http.api.HttpRequestMethod;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.loadbalancer.RoundRobinLoadBalancer;
import io.servicetalk.loadbalancer.RoundRobinLoadBalancerFactory;

import static io.servicetalk.http.api.HttpExecutionStrategyInfluencer.defaultStreamingInfluencer;
import static java.lang.Integer.MAX_VALUE;
import static java.util.Objects.requireNonNull;

/**
 * Default implementation of {@link HttpLoadBalancerFactory}.
 *
 * @param <ResolvedAddress> The type of address after resolution.
 */
public final class DefaultHttpLoadBalancerFactory<ResolvedAddress>
        implements HttpLoadBalancerFactory<ResolvedAddress>, HttpExecutionStrategyInfluencer {
    private final LoadBalancerFactory<ResolvedAddress, FilterableStreamingHttpLoadBalancedConnection> rawFactory;
    private final HttpExecutionStrategyInfluencer strategyInfluencer;

    DefaultHttpLoadBalancerFactory(
            final LoadBalancerFactory<ResolvedAddress, FilterableStreamingHttpLoadBalancedConnection> rawFactory,
            final HttpExecutionStrategyInfluencer strategyInfluencer) {
        this.rawFactory = rawFactory;
        this.strategyInfluencer = strategyInfluencer;
    }

    @Override
    public <T extends FilterableStreamingHttpLoadBalancedConnection> LoadBalancer<T> newLoadBalancer(
            final Publisher<? extends ServiceDiscovererEvent<ResolvedAddress>> eventPublisher,
            final ConnectionFactory<ResolvedAddress, T> cf) {
        return rawFactory.newLoadBalancer(eventPublisher, cf);
    }

    @Override
    public <T extends FilterableStreamingHttpLoadBalancedConnection> LoadBalancer<T> newLoadBalancer(
            final String targetResource,
            final Publisher<? extends ServiceDiscovererEvent<ResolvedAddress>> eventPublisher,
            final ConnectionFactory<ResolvedAddress, T> connectionFactory) {
        return rawFactory.newLoadBalancer(targetResource, eventPublisher, connectionFactory);
    }

    @Override
    public FilterableStreamingHttpLoadBalancedConnection toLoadBalancedConnection(
            final FilterableStreamingHttpConnection connection) {
        return new DefaultFilterableStreamingHttpLoadBalancedConnection(connection);
    }

    @Override
    public HttpExecutionStrategy influenceStrategy(final HttpExecutionStrategy strategy) {
        return strategyInfluencer.influenceStrategy(strategy);
    }

    /**
     * A builder for creating instances of {@link DefaultHttpLoadBalancerFactory}.
     *
     * @param <ResolvedAddress> The type of address after resolution for the {@link HttpLoadBalancerFactory} built by
     * this builder.
     */
    public static final class Builder<ResolvedAddress> {
        private final LoadBalancerFactory<ResolvedAddress, FilterableStreamingHttpLoadBalancedConnection> rawFactory;
        private final HttpExecutionStrategyInfluencer strategyInfluencer;

        private Builder(
                final LoadBalancerFactory<ResolvedAddress, FilterableStreamingHttpLoadBalancedConnection> rawFactory,
                final HttpExecutionStrategyInfluencer strategyInfluencer) {
            this.rawFactory = rawFactory;
            this.strategyInfluencer = strategyInfluencer;
        }

        /**
         * Builds a {@link DefaultHttpLoadBalancerFactory} using the properties configured on this builder.
         *
         * @return A {@link DefaultHttpLoadBalancerFactory}.
         */
        public DefaultHttpLoadBalancerFactory<ResolvedAddress> build() {
            return new DefaultHttpLoadBalancerFactory<>(rawFactory, strategyInfluencer);
        }

        /**
         * Creates a new {@link Builder} instance using the default {@link LoadBalancer} implementation.
         *
         * @param <ResolvedAddress> The type of address after resolution for the {@link LoadBalancerFactory} built by
         * the returned builder.
         * @return A new {@link Builder}.
         */
        public static <ResolvedAddress> Builder<ResolvedAddress> fromDefaults() {
            return from(new RoundRobinLoadBalancerFactory
                    .Builder<ResolvedAddress, FilterableStreamingHttpLoadBalancedConnection>().build());
        }

        /**
         * Creates a new {@link Builder} using the passed {@link LoadBalancerFactory}.
         *
         * @param rawFactory {@link LoadBalancerFactory} to use for creating a {@link HttpLoadBalancerFactory} from the
         * returned {@link Builder}.
         * @param <ResolvedAddress> The type of address after resolution for a {@link HttpLoadBalancerFactory} created
         * by the returned {@link Builder}.
         * @return A new {@link Builder}.
         */
        @SuppressWarnings("deprecation")
        public static <ResolvedAddress> Builder<ResolvedAddress> from(
                final LoadBalancerFactory<ResolvedAddress, FilterableStreamingHttpLoadBalancedConnection> rawFactory) {
            final HttpExecutionStrategyInfluencer strategyInfluencer;
            if (rawFactory instanceof HttpExecutionStrategyInfluencer) {
                strategyInfluencer = (HttpExecutionStrategyInfluencer) rawFactory;
            } else if (rawFactory instanceof RoundRobinLoadBalancerFactory
                    || rawFactory instanceof RoundRobinLoadBalancer.RoundRobinLoadBalancerFactory) {
                strategyInfluencer = strategy -> strategy; // RoundRobinLoadBalancer is non-blocking.
            } else {
                // user provided load balancer assumed to be blocking unless it implements
                // HttpExecutionStrategyInfluencer
                strategyInfluencer = defaultStreamingInfluencer();
            }
            return new Builder<>(rawFactory, strategyInfluencer);
        }
    }

    private static final class DefaultFilterableStreamingHttpLoadBalancedConnection
            implements FilterableStreamingHttpLoadBalancedConnection {

        private final FilterableStreamingHttpConnection delegate;

        DefaultFilterableStreamingHttpLoadBalancedConnection(final FilterableStreamingHttpConnection delegate) {
            this.delegate = requireNonNull(delegate);
        }

        @Override
        public int score() {
            return MAX_VALUE;
        }

        @Override
        public HttpConnectionContext connectionContext() {
            return delegate.connectionContext();
        }

        @Override
        public <T> Publisher<? extends T> transportEventStream(final HttpEventKey<T> eventKey) {
            return delegate.transportEventStream(eventKey);
        }

        @Override
        public Single<StreamingHttpResponse> request(final HttpExecutionStrategy strategy,
                                                     final StreamingHttpRequest request) {
            return delegate.request(strategy, request);
        }

        @Override
        public HttpExecutionContext executionContext() {
            return delegate.executionContext();
        }

        @Override
        public StreamingHttpResponseFactory httpResponseFactory() {
            return delegate.httpResponseFactory();
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

        @Override
        public StreamingHttpRequest newRequest(final HttpRequestMethod method, final String requestTarget) {
            return delegate.newRequest(method, requestTarget);
        }
    }
}
