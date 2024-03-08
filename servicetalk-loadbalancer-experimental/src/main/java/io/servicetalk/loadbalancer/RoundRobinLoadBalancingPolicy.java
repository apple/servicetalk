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

import java.util.List;

/**
 * A round-robin load balancing policy.
 * This load balancing algorithm is the well known policy of selecting hosts sequentially
 * from an ordered set. If a host is considered unhealthy it is skipped the next host
 * is selected until a healthy host is found or the entire host set has been exhausted.
 * @param <ResolvedAddress> the type of the resolved address
 * @param <C> the type of the load balanced connection
 */
public final class RoundRobinLoadBalancingPolicy<ResolvedAddress, C extends LoadBalancedConnection>
        implements LoadBalancingPolicy<ResolvedAddress, C> {

    private final boolean failOpen;

    private RoundRobinLoadBalancingPolicy(final boolean failOpen) {
        this.failOpen = failOpen;
    }

    @Override
    public HostSelector<ResolvedAddress, C>
    buildSelector(final List<Host<ResolvedAddress, C>> hosts, final String targetResource) {
        return new RoundRobinSelector<>(hosts, targetResource, failOpen);
    }

    @Override
    public String name() {
        return "RoundRobin";
    }

    /**
     * A builder for immutable {@link RoundRobinLoadBalancingPolicy} instances.
     */
    public static final class Builder {

        private boolean failOpen;

        /**
         * Set whether the host selector should attempt to use an unhealthy {@link Host} as a last resort.
         * @param failOpen whether the host selector should attempt to use an unhealthy {@link Host} as a last resort.
         * @return this {@link P2CLoadBalancingPolicy.Builder}.
         */
        public RoundRobinLoadBalancingPolicy.Builder failOpen(final boolean failOpen) {
            this.failOpen = failOpen;
            return this;
        }

        /**
         * Construct the immutable {@link RoundRobinLoadBalancingPolicy}.
         * @param <ResolvedAddress> the type of the resolved address.
         * @param <C> the refined type of the {@link LoadBalancedConnection}.
         * @return the concrete {@link RoundRobinLoadBalancingPolicy}.
         */
        public <ResolvedAddress, C extends LoadBalancedConnection> RoundRobinLoadBalancingPolicy<ResolvedAddress, C>
        build() {
            return new RoundRobinLoadBalancingPolicy<>(failOpen);
        }
    }
}
