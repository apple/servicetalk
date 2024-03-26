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

/**
 * A marker class for all currently supported load balancing policies.
 * @param <ResolvedAddress> the concrete type of the resolved address.
 * @param <C> the concrete type of the load balanced connection.
 */
abstract class BaseLoadBalancingPolicy<ResolvedAddress, C extends LoadBalancedConnection>
        implements LoadBalancingPolicy {

    /**
     * Construct a {@link HostSelector}.
     * @param hosts          the set of {@link Host}s to select from.
     * @param targetResource the name of the target resource, useful for debugging purposes.
     * @return a {@link HostSelector}
     */
    abstract HostSelector<ResolvedAddress, C> buildSelector(
            List<Host<ResolvedAddress, C>> hosts, String targetResource);

    @Override
    public abstract String toString();
}
