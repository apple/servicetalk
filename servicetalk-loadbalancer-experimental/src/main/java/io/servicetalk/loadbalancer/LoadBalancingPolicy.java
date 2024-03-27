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
 * Base class defining the selector mechanism used for load balancing.
 * <p>
 * The load balancing policy defines how selection happens over the set of available endpoints. Common examples
 * include Power of Two Choices (P2C), RoundRobin, random, heap, etc. Configurable implementations can be found in
 * {@link LoadBalancingPolicies}.
 * @param <ResolvedAddress> the concrete type of the resolved address.
 * @param <C> the concrete type of the {@link LoadBalancedConnection}.
 * @see LoadBalancingPolicies for definitions of usable policies
 */
public abstract class LoadBalancingPolicy<ResolvedAddress, C extends LoadBalancedConnection> {

    LoadBalancingPolicy() {
        // package private to limit implementations.
    }

    /**
     * The name of the load balancing policy.
     * @return the name of the load balancing policy
     */
    public abstract String name();

    @Override
    public abstract String toString();

    // package private to limit visibility of HostSelector
    abstract HostSelector<ResolvedAddress, C> buildSelector(
            List<Host<ResolvedAddress, C>> hosts, String targetResource);
}
