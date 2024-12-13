/*
 * Copyright © 2023 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.client.api.LoadBalancedConnection;

/**
 * Provider for {@link RoundRobinLoadBalancerBuilder} that can be registered using {@link java.util.ServiceLoader}.
 */
public interface RoundRobinLoadBalancerBuilderProvider {

    /**
     * Returns a {@link RoundRobinLoadBalancerBuilder} based on the pre-initialized
     * {@link RoundRobinLoadBalancerBuilder}.
     * <p>
     * This method may return the pre-initialized {@code builder} as-is, or apply custom builder settings before
     * returning it, or wrap it ({@link DelegatingRoundRobinLoadBalancerBuilder} may be helpful).
     *
     * @param id a (unique) identifier used to identify the underlying {@link RoundRobinLoadBalancer}.
     * @param builder pre-initialized {@link RoundRobinLoadBalancerBuilder}.
     * @return a {@link RoundRobinLoadBalancerBuilder} based on the pre-initialized
     * {@link RoundRobinLoadBalancerBuilder}.
     * @param <ResolvedAddress> The resolved address type.
     * @param <C> The type of connection.
     */
    <ResolvedAddress, C extends LoadBalancedConnection> RoundRobinLoadBalancerBuilder<ResolvedAddress, C>
    newBuilder(String id, RoundRobinLoadBalancerBuilder<ResolvedAddress, C> builder);
}
