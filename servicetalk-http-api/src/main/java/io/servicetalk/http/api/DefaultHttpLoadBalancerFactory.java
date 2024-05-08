/*
 * Copyright © 2020-2021, 2024 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.http.api;

import io.servicetalk.client.api.ConnectionFactory;
import io.servicetalk.client.api.LoadBalancer;
import io.servicetalk.client.api.LoadBalancerFactory;
import io.servicetalk.client.api.ReservableRequestConcurrencyController;
import io.servicetalk.client.api.ScoreSupplier;
import io.servicetalk.client.api.ServiceDiscovererEvent;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.context.api.ContextMap;

import java.util.Collection;
import javax.annotation.Nullable;

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

    /**
     * Creates a new instance with execution strategy adapted from the underlying factory.
     *
     * @param rawFactory {@link LoadBalancerFactory} to use
     */
    public DefaultHttpLoadBalancerFactory(
            final LoadBalancerFactory<ResolvedAddress, FilterableStreamingHttpLoadBalancedConnection> rawFactory) {
        this.rawFactory = rawFactory;
        this.strategy = HttpExecutionStrategy.from(rawFactory.requiredOffloads());
    }

    @SuppressWarnings("deprecation")
    @Override
    public <T extends FilterableStreamingHttpLoadBalancedConnection> LoadBalancer<T> newLoadBalancer(
            final String targetResource,
            final Publisher<? extends Collection<? extends ServiceDiscovererEvent<ResolvedAddress>>> eventPublisher,
            final ConnectionFactory<ResolvedAddress, T> connectionFactory) {
        return rawFactory.newLoadBalancer(targetResource, eventPublisher, connectionFactory);
    }

    @Override
    public LoadBalancer<FilterableStreamingHttpLoadBalancedConnection> newLoadBalancer(
            final Publisher<? extends Collection<? extends ServiceDiscovererEvent<ResolvedAddress>>> eventPublisher,
            final ConnectionFactory<ResolvedAddress, FilterableStreamingHttpLoadBalancedConnection> connectionFactory,
            final String targetResource) {
        return rawFactory.newLoadBalancer(eventPublisher, connectionFactory, targetResource);
    }

    @SuppressWarnings("deprecation")
    @Override   // FIXME: 0.43 - remove deprecated method
    public FilterableStreamingHttpLoadBalancedConnection toLoadBalancedConnection(
            final FilterableStreamingHttpConnection connection) {
        return new DefaultFilterableStreamingHttpLoadBalancedConnection(connection);
    }

    @Override
    public FilterableStreamingHttpLoadBalancedConnection toLoadBalancedConnection(
            final FilterableStreamingHttpConnection connection,
            final ReservableRequestConcurrencyController concurrencyController,
            @Nullable final ContextMap context) {
        return new HttpLoadBalancerFactory.DefaultFilterableStreamingHttpLoadBalancedConnection(connection,
                concurrencyController);
    }

    @Override
    public HttpExecutionStrategy requiredOffloads() {
        return strategy;
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
