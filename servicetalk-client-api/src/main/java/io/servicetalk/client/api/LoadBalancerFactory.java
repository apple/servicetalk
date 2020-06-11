/*
 * Copyright © 2018 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.client.api;

import io.servicetalk.concurrent.api.Publisher;

/**
 * A factory for creating {@link LoadBalancer} instances.
 *
 * @param <ResolvedAddress> The type of address after resolution.
 * @param <C> The type of connection.
 */
@FunctionalInterface
public interface LoadBalancerFactory<ResolvedAddress, C extends LoadBalancedConnection> {

    /**
     * Create a new {@link LoadBalancer}.
     * @param eventPublisher A stream of {@link ServiceDiscovererEvent}s which the {@link LoadBalancer} can use to
     * connect to physical hosts. Typically generated from a {@link ServiceDiscoverer}.
     * @param connectionFactory {@link ConnectionFactory} that the returned {@link LoadBalancer} will use to generate
     * new connections. Returned {@link LoadBalancer} will own the responsibility for this {@link ConnectionFactory}
     * and hence will call {@link ConnectionFactory#closeAsync()} when {@link LoadBalancer#closeAsync()} is called.
     * @param <T> Type of connections created by the passed {@link ConnectionFactory}.
     * @return a new {@link LoadBalancer}.
     */
    <T extends C> LoadBalancer<T> newLoadBalancer(
            Publisher<? extends ServiceDiscovererEvent<ResolvedAddress>> eventPublisher,
            ConnectionFactory<ResolvedAddress, T> connectionFactory);
}
