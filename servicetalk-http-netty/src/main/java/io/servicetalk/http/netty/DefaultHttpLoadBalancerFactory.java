/*
 * Copyright © 2020-2024 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.client.api.ReservableRequestConcurrencyController;
import io.servicetalk.client.api.ServiceDiscovererEvent;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.context.api.ContextMap;
import io.servicetalk.http.api.FilterableStreamingHttpConnection;
import io.servicetalk.http.api.FilterableStreamingHttpLoadBalancedConnection;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpLoadBalancerFactory;
import io.servicetalk.loadbalancer.RoundRobinLoadBalancers;

import java.util.Collection;
import javax.annotation.Nullable;

/**
 * Default implementation of {@link HttpLoadBalancerFactory}.
 *
 * @param <ResolvedAddress> The type of address after resolution.
 * @deprecated use {@link io.servicetalk.http.api.DefaultHttpLoadBalancerFactory} instead.
 */
@Deprecated // FIXME: 0.43 - remove deprecated class
public final class DefaultHttpLoadBalancerFactory<ResolvedAddress>
        implements HttpLoadBalancerFactory<ResolvedAddress> {

    private DefaultHttpLoadBalancerFactory() {
        // no instances.
    }

    @SuppressWarnings("deprecation")
    @Override
    public <T extends FilterableStreamingHttpLoadBalancedConnection> LoadBalancer<T> newLoadBalancer(
            final String targetResource,
            final Publisher<? extends Collection<? extends ServiceDiscovererEvent<ResolvedAddress>>> eventPublisher,
            final ConnectionFactory<ResolvedAddress, T> connectionFactory) {
        throw new UnsupportedOperationException("No instances.");
    }

    @Override
    public LoadBalancer<FilterableStreamingHttpLoadBalancedConnection> newLoadBalancer(
            final Publisher<? extends Collection<? extends ServiceDiscovererEvent<ResolvedAddress>>> eventPublisher,
            final ConnectionFactory<ResolvedAddress, FilterableStreamingHttpLoadBalancedConnection> connectionFactory,
            final String targetResource) {
        throw new UnsupportedOperationException("No instances.");
    }

    @Override   // FIXME: 0.43 - remove deprecated method
    public FilterableStreamingHttpLoadBalancedConnection toLoadBalancedConnection(
            final FilterableStreamingHttpConnection connection) {
        throw new UnsupportedOperationException("No instances.");
    }

    @Override
    public FilterableStreamingHttpLoadBalancedConnection toLoadBalancedConnection(
            final FilterableStreamingHttpConnection connection,
            final ReservableRequestConcurrencyController concurrencyController,
            @Nullable final ContextMap context) {
        throw new UnsupportedOperationException("No instances.");
    }

    @Override
    public HttpExecutionStrategy requiredOffloads() {
        throw new UnsupportedOperationException("No instances.");
    }

    /**
     * A builder for creating instances of {@link DefaultHttpLoadBalancerFactory}.
     *
     * @param <ResolvedAddress> The type of address after resolution for the {@link HttpLoadBalancerFactory} built by
     * this builder.
     */
    public static final class Builder<ResolvedAddress> {

        private final io.servicetalk.http.api.DefaultHttpLoadBalancerFactory.Builder<ResolvedAddress> delegate;

        private Builder(
                final LoadBalancerFactory<ResolvedAddress, FilterableStreamingHttpLoadBalancedConnection> rawFactory) {
            this.delegate = io.servicetalk.http.api.DefaultHttpLoadBalancerFactory.Builder.from(rawFactory);
        }

        /**
         * Builds a {@link DefaultHttpLoadBalancerFactory} using the properties configured on this builder.
         *
         * @return A {@link DefaultHttpLoadBalancerFactory}.
         */
        public HttpLoadBalancerFactory<ResolvedAddress> build() {
            return delegate.build();
        }

        /**
         * Creates a new {@link Builder} instance using the default {@link LoadBalancer} implementation.
         *
         * @param <ResolvedAddress> The type of address after resolution for the {@link LoadBalancerFactory} built by
         * the returned builder.
         * @return A new {@link Builder}.
         */
        public static <ResolvedAddress> Builder<ResolvedAddress> fromDefaults() {
            return from(RoundRobinLoadBalancers
                    .<ResolvedAddress, FilterableStreamingHttpLoadBalancedConnection>builder(
                            DefaultHttpLoadBalancerFactory.class.getSimpleName()).build());
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
            return new Builder<>(rawFactory);
        }
    }
}
