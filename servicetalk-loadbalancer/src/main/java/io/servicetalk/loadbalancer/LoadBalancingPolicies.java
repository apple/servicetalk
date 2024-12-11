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
 * A collections of factories for constructing a {@link LoadBalancingPolicy}.
 */
public final class LoadBalancingPolicies {

    private LoadBalancingPolicies() {
        // no instances.
    }

    /**
     * Get the recommended default {@link LoadBalancingPolicy}.
     * @return the recommended default {@link LoadBalancingPolicy}.
     */
    public static <ResolvedAddress, C extends LoadBalancedConnection>
    LoadBalancingPolicy<ResolvedAddress, C> defaultPolicy() {
        return LoadBalancingPolicies.roundRobin().build();
    }

    /**
     * Builder for the round-robin {@link LoadBalancingPolicy}.
     * Round-robin load balancing is a strategy that maximizes fairness of the request distribution. This comes at the
     * cost of being unable to bias toward better performing hosts and can only leverage the course grained
     * healthy/unhealthy status of a host.
     * @return a builder for the round-robin {@link LoadBalancingPolicy}.
     */
    public static RoundRobinLoadBalancingPolicyBuilder roundRobin() {
        return new RoundRobinLoadBalancingPolicyBuilder();
    }

    /**
     * Builder for the power of two choices (P2C) {@link LoadBalancingPolicy}.
     * Power of Two Choices (P2C) leverages both course grained healthy/unhealthy status of a host plus additional
     * fine-grained scoring to prioritize hosts that are both healthy and better performing. See the
     * {@link P2CLoadBalancingPolicy} for more details.
     * @return a builder for the power of two choices (P2C) {@link LoadBalancingPolicy}.
     */
    public static P2CLoadBalancingPolicyBuilder p2c() {
        return new P2CLoadBalancingPolicyBuilder();
    }
}
