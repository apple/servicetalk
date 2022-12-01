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
import io.servicetalk.client.api.ScoreSupplier;
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
import io.servicetalk.http.api.HttpLoadBalancerFactory;
import io.servicetalk.http.api.HttpRequestMethod;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.loadbalancer.RoundRobinLoadBalancerFactory;

import java.util.Collection;

import static java.util.Objects.requireNonNull;

/**
 * Default implementation of {@link HttpLoadBalancerFactory}.
 *
 * @param <ResolvedAddress> The type of address after resolution.
 */
public final class DefaultHttpLoadBalancerFactory<ResolvedAddress>
        implements HttpLoadBalancerFactory<ResolvedAddress> {
    private final LoadBalancerFactory<ResolvedAddress, FilterableStreamingHttpLoadBalancedConnection> rawFactory;
    private final HttpExecutionStrategy strategy;

    DefaultHttpLoadBalancerFactory(
            final LoadBalancerFactory<ResolvedAddress, FilterableStreamingHttpLoadBalancedConnection> rawFactory,
            final HttpExecutionStrategy strategy) {
        this.rawFactory = rawFactory;
        this.strategy = strategy;
    }

    @Override
    public <T extends FilterableStreamingHttpLoadBalancedConnection> LoadBalancer<T> newLoadBalancer(
            final String targetResource,
            final Publisher<? extends Collection<? extends ServiceDiscovererEvent<ResolvedAddress>>> eventPublisher,
            final ConnectionFactory<ResolvedAddress, T> connectionFactory) {
        return rawFactory.newLoadBalancer(targetResource, eventPublisher, connectionFactory);
    }

    @Override   // FIXME: 0.43 - remove deprecated method
    public FilterableStreamingHttpLoadBalancedConnection toLoadBalancedConnection(
            final FilterableStreamingHttpConnection connection) {
        return new DefaultFilterableStreamingHttpLoadBalancedConnection(connection);
    }

    @Override
    public HttpExecutionStrategy requiredOffloads() {
        return strategy;
    }

    /**
     * A builder for creating instances of {@link DefaultHttpLoadBalancerFactory}.
     *
     * @param <ResolvedAddress> The type of address after resolution for the {@link HttpLoadBalancerFactory} built by
     * this builder.
     */
    public static final class Builder<ResolvedAddress> {
        private final LoadBalancerFactory<ResolvedAddress, FilterableStreamingHttpLoadBalancedConnection> rawFactory;
        private final HttpExecutionStrategy strategy;

        private Builder(
                final LoadBalancerFactory<ResolvedAddress, FilterableStreamingHttpLoadBalancedConnection> rawFactory,
                final HttpExecutionStrategy strategy) {
            this.rawFactory = rawFactory;
            this.strategy = strategy;
        }

        /**
         * Builds a {@link DefaultHttpLoadBalancerFactory} using the properties configured on this builder.
         *
         * @return A {@link DefaultHttpLoadBalancerFactory}.
         */
        public DefaultHttpLoadBalancerFactory<ResolvedAddress> build() {
            return new DefaultHttpLoadBalancerFactory<>(rawFactory, strategy);
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
            final HttpExecutionStrategy strategy = HttpExecutionStrategy.from(rawFactory.requiredOffloads());
            return new Builder<>(rawFactory, strategy);
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
            throw new UnsupportedOperationException(
                   DefaultFilterableStreamingHttpLoadBalancedConnection.class.getName() +
                           " doesn't support scoring. " + ScoreSupplier.class.getName() +
                           " is only available through " + HttpLoadBalancerFactory.class.getSimpleName() +
                           " implementations that support scoring.");
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
        public Single<StreamingHttpResponse> request(final StreamingHttpRequest request) {
            return delegate.request(request);
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

        @Override
        public StreamingHttpRequest newRequest(final HttpRequestMethod method, final String requestTarget) {
            return delegate.newRequest(method, requestTarget);
        }

        @Override
        public String toString() {
            return delegate.toString();
        }
    }
}
