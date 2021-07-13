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

import java.util.function.Predicate;

/**
 * {@link LoadBalancerFactory} that creates {@link LoadBalancer} instances which use a round robin strategy
 * for selecting addresses. The created instances have the following behaviour:
 * <ul>
 * <li>Round robining is done at address level.</li>
 * <li>Connections are created lazily, without any concurrency control on their creation.
 * This can lead to over-provisioning connections when dealing with a requests surge.</li>
 * <li>Existing connections are reused unless a selector passed to {@link LoadBalancer#selectConnection(Predicate)}
 * suggests otherwise. This can lead to situations where connections will be used to their maximum capacity
 * (for example in the context of pipelining) before new connections are created.</li>
 * <li>Closed connections are automatically pruned.</li>
 * <li>By default, connections to addresses marked as {@link ServiceDiscovererEvent#isAvailable() unavailable}
 * are used for requests, but no new connections are created for them. In case the address' connections are busy,
 * another host is tried. If all hosts are busy, selection fails with a
 * {@link io.servicetalk.client.api.ConnectionRejectedException}. If {@link #eagerConnectionShutdown} is set to true,
 * connections are immediately closed for an {@link ServiceDiscovererEvent#isAvailable() unavailable} address.</li>
 * </ul>
 *
 * @param <ResolvedAddress> The resolved address type.
 * @param <C> The type of connection.
 */
public final class RoundRobinLoadBalancerFactory<ResolvedAddress, C extends LoadBalancedConnection>
        implements LoadBalancerFactory<ResolvedAddress, C> {

    private final boolean eagerConnectionShutdown;

    private RoundRobinLoadBalancerFactory(boolean eagerConnectionShutdown) {
        this.eagerConnectionShutdown = eagerConnectionShutdown;
    }

    @Override
    public <T extends C> LoadBalancer<T> newLoadBalancer(
            final Publisher<? extends ServiceDiscovererEvent<ResolvedAddress>> eventPublisher,
            final ConnectionFactory<ResolvedAddress, T> connectionFactory) {
        return new RoundRobinLoadBalancer<>(eventPublisher, connectionFactory, eagerConnectionShutdown);
    }

    /**
     * Builder for {@link RoundRobinLoadBalancerFactory}.
     *
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
         * Configures the {@link RoundRobinLoadBalancerFactory} to produce a {@link LoadBalancer} with
         * a setting driving eagerness of connection shutdown. By default, the created {@link LoadBalancer} does
         * not close connections when a host become {@link ServiceDiscovererEvent#isAvailable() unavailable}.
         * When configured with {@code true} as an argument, the connections will be closed gracefully on such event.
         *
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
         *
         * @return a new instance of {@link RoundRobinLoadBalancerFactory} with settings from this builder.
         */
        public RoundRobinLoadBalancerFactory<ResolvedAddress, C> build() {
            return new RoundRobinLoadBalancerFactory<>(eagerConnectionShutdown);
        }
    }
}
