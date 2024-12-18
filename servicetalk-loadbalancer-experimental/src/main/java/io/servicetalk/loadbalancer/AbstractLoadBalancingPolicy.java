/*
 * Copyright © 2024 Apple Inc. and the ServiceTalk project authors
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

abstract class AbstractLoadBalancingPolicy<ResolvedAddress, C extends LoadBalancedConnection>
        implements LoadBalancingPolicy<ResolvedAddress, C> {
    /**
     * The default fail-open policy to use for {@link HostSelector} implementations.
     */
    static final boolean DEFAULT_FAIL_OPEN_POLICY = false;

    /**
     * Construct a {@link HostSelector}.
     * @param hosts          the set of {@link Host}s to select from.
     * @param lbDescription a description of the associated {@link io.servicetalk.client.api.LoadBalancer},
     *                      useful for debugging purposes.
     * @return a {@link HostSelector}
     */
    abstract HostSelector<ResolvedAddress, C> buildSelector(
            List<Host<ResolvedAddress, C>> hosts, String lbDescription);

    /**
     * The name of the load balancing policy.
     * @return the name of the load balancing policy
     */
    abstract String name();

    @Override
    public abstract String toString();
}
