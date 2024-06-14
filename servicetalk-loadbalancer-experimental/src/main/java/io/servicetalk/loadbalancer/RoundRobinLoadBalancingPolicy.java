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
 *
 * This load balancing algorithm is the well known policy of selecting hosts sequentially
 * from an ordered set. If a host is considered unhealthy it is skipped the next host
 * is selected until a healthy host is found or the entire host set has been exhausted.
 *
 * @param <ResolvedAddress> the type of the resolved address
 * @param <C> the type of the load balanced connection
 */
final class RoundRobinLoadBalancingPolicy<ResolvedAddress, C extends LoadBalancedConnection>
        extends LoadBalancingPolicy<ResolvedAddress, C> {

    private final boolean failOpen;
    private final boolean ignoreWeights;

    RoundRobinLoadBalancingPolicy(final boolean failOpen, final boolean ignoreWeights) {
        this.failOpen = failOpen;
        this.ignoreWeights = ignoreWeights;
    }

    @Override
    HostSelector<ResolvedAddress, C>
    buildSelector(final List<Host<ResolvedAddress, C>> hosts, final String lbDescription) {
        return new RoundRobinSelector<>(hosts, lbDescription, failOpen, ignoreWeights);
    }

    @Override
    public String name() {
        return "RoundRobin";
    }

    @Override
    public String toString() {
        return name() + "(failOpen=" + failOpen + ")";
    }
}
