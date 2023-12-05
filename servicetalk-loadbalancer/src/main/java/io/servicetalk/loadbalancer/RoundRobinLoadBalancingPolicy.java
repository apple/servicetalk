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
 * A round-robin load balancing policy.
 *
 * This load balancing algorithm is the well known policy of selecting hosts sequentially
 * from an ordered set. If a host is considered unhealthy it is skipped the next host
 * is selected until a healthy host is found or the entire host set has been exhausted.
 */
final class RoundRobinLoadBalancingPolicy implements LoadBalancingPolicy {

    /**
     * The default P2C load balancing policy.
     */
    public static final RoundRobinLoadBalancingPolicy DEFAULT_POLICY = new RoundRobinLoadBalancingPolicy();

    private RoundRobinLoadBalancingPolicy() {
    }

    @Override
    public <ResolvedAddress, C extends LoadBalancedConnection> HostSelector<ResolvedAddress, C>
    buildSelector(final String targetResource) {
        return new RoundRobinSelector<>(targetResource);
    }

    @Override
    public String name() {
        return "RoundRobin";
    }

    /**
     * A builder for immutable {@link RoundRobinLoadBalancingPolicy} instances.
     */
    public static final class Builder {

        /**
         * Construct the immutable {@link RoundRobinLoadBalancingPolicy}.
         * @return the concrete {@link RoundRobinLoadBalancingPolicy}.
         */
        public RoundRobinLoadBalancingPolicy build() {
            // Right now there aren't any configurations for round-robin.
            return DEFAULT_POLICY;
        }
    }
}
