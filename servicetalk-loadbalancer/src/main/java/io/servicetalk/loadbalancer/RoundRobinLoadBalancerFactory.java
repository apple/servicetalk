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
package io.servicetalk.loadbalancer;

import io.servicetalk.client.api.ConnectionFactory;
import io.servicetalk.client.api.LoadBalancedConnection;
import io.servicetalk.client.api.LoadBalancer;
import io.servicetalk.client.api.LoadBalancerFactory;
import io.servicetalk.client.api.ServiceDiscovererEvent;
import io.servicetalk.concurrent.api.Publisher;

/**
 * {@link LoadBalancerFactory} for {@link RoundRobinLoadBalancer}.
 *
 * @param <ResolvedAddress> The resolved address type.
 * @param <C> The type of connection.
 */
public final class RoundRobinLoadBalancerFactory<ResolvedAddress, C extends LoadBalancedConnection>
        implements LoadBalancerFactory<ResolvedAddress, C> {

    private final RoundRobinLoadBalancerFactory.Builder<ResolvedAddress, C> builder;

    private RoundRobinLoadBalancerFactory(RoundRobinLoadBalancerFactory.Builder<ResolvedAddress, C> builder) {
        this.builder = builder;
    }

    @Override
    public <T extends C> LoadBalancer<T> newLoadBalancer(
            final Publisher<? extends ServiceDiscovererEvent<ResolvedAddress>> eventPublisher,
            final ConnectionFactory<ResolvedAddress, T> connectionFactory) {
        return new RoundRobinLoadBalancer<>(eventPublisher, connectionFactory, builder.eagerConnectionShutdown);
    }

    /**
     * Builder for {@link RoundRobinLoadBalancerFactory}.
     * @param <ResolvedAddress> The resolved address type.
     * @param <C> The type of connection.
     */
    public static final class Builder<ResolvedAddress, C extends LoadBalancedConnection> {
        private boolean eagerConnectionShutdown;

        /**
         * Creates a new instance with default settings.
         */
        public Builder() {
        }

        /**
         * Configures the {@link RoundRobinLoadBalancerFactory} to produce {@link RoundRobinLoadBalancer} with
         * a setting driving eagerness of connection shutdown. By default, the {@link RoundRobinLoadBalancer} does
         * not close connections of {@link ServiceDiscovererEvent#isAvailable() unavailable} hosts. When called with
         * {@code true} as argument, the connections will be removed gracefully on such event.
         * @param eagerConnectionShutdown when true, connections will be shut down upon receiving
         * {@link ServiceDiscovererEvent#isAvailable() unavailable} events for a particular host.
         * @return {@code this}.
         */
        public RoundRobinLoadBalancerFactory.Builder<ResolvedAddress, C> eagerConnectionShutdown(
                boolean eagerConnectionShutdown) {
            this.eagerConnectionShutdown = eagerConnectionShutdown;
            return this;
        }

        /**
         * Builds the {@link RoundRobinLoadBalancerFactory} configured by this builder.
         * @return a new instance of {@link RoundRobinLoadBalancerFactory} with settings from this builder.
         */
        public RoundRobinLoadBalancerFactory<ResolvedAddress, C> build() {
            return new RoundRobinLoadBalancerFactory<>(this);
        }
    }
}
