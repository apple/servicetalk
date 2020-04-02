/*
 * Copyright © 2020 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.http.api.FilterableStreamingHttpConnection;
import io.servicetalk.http.api.FilterableStreamingHttpLoadBalancedConnection;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpExecutionStrategyInfluencer;
import io.servicetalk.http.api.HttpLoadBalancerFactory;

import static io.servicetalk.loadbalancer.RoundRobinLoadBalancer.newRoundRobinFactory;

/**
 * Default implementation of {@link HttpLoadBalancerFactory}.
 *
 * @param <ResolvedAddress> The type of address after resolution.
 */
public final class DefaultHttpLoadBalancerFactory<ResolvedAddress>
        implements HttpLoadBalancerFactory<ResolvedAddress>, HttpExecutionStrategyInfluencer {
    private final Config<ResolvedAddress> config;

    private DefaultHttpLoadBalancerFactory(final Config<ResolvedAddress> config) {
        this.config = config;
    }

    @Override
    public LoadBalancer<? extends FilterableStreamingHttpLoadBalancedConnection> newLoadBalancer(
            final Publisher<? extends ServiceDiscovererEvent<ResolvedAddress>> eventPublisher,
            final ConnectionFactory<ResolvedAddress, ? extends FilterableStreamingHttpLoadBalancedConnection> cf) {
        return config.rawFactory.newLoadBalancer(eventPublisher, cf);
    }

    @Override
    public FilterableStreamingHttpLoadBalancedConnection toLoadBalancedConnection(
            final FilterableStreamingHttpConnection connection) {
        return new AbstractFilterableStreamingHttpLoadBalancedConnection(connection) {
            @Override
            public float score() {
                return 1;
            }
        };
    }

    @Override
    public HttpExecutionStrategy influenceStrategy(final HttpExecutionStrategy strategy) {
        // Load balancers are assumed to be non-blocking.
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

        private Builder(
                final LoadBalancerFactory<ResolvedAddress, FilterableStreamingHttpLoadBalancedConnection> rawFactory) {
            this.rawFactory = rawFactory;
        }

        /**
         * Builds a {@link DefaultHttpLoadBalancerFactory} using the properties configured on this builder.
         *
         * @return A {@link DefaultHttpLoadBalancerFactory}.
         */
        public DefaultHttpLoadBalancerFactory<ResolvedAddress> build() {
            return new DefaultHttpLoadBalancerFactory<>(new Config<>(rawFactory));
        }

        /**
         * Creates a new {@link Builder} instance using the default {@link LoadBalancer} implementation.
         *
         * @param <ResolvedAddress> The type of address after resolution for the {@link LoadBalancerFactory} built by
         * the returned builder.
         * @return A new {@link Builder}.
         */
        public static <ResolvedAddress> Builder<ResolvedAddress> fromDefaults() {
            return from(newRoundRobinFactory());
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
        public static <ResolvedAddress> Builder<ResolvedAddress> from(
                final LoadBalancerFactory<ResolvedAddress, FilterableStreamingHttpLoadBalancedConnection> rawFactory) {
            return new Builder<>(rawFactory);
        }
    }

    private static final class Config<Addr> {
        private final LoadBalancerFactory<Addr, FilterableStreamingHttpLoadBalancedConnection> rawFactory;

        Config(final LoadBalancerFactory<Addr, FilterableStreamingHttpLoadBalancedConnection> rawFactory) {
            this.rawFactory = rawFactory;
        }
    }
}
