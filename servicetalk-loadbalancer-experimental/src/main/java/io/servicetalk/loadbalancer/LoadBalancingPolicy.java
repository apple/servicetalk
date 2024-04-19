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
 * Definition of the selector mechanism used for load balancing.
 * @param <ResolvedAddress> the type of the resolved address
 * @param <C> the type of the load balanced connection
 */
// TODO: make abstract class and private.
public interface LoadBalancingPolicy<ResolvedAddress, C extends LoadBalancedConnection> {

    /**
     * The name of the load balancing policy.
     * @return the name of the load balancing policy
     */
    String name();

    /**
     * Construct a {@link HostSelector}.
     * @param hosts          the set of {@link Host}s to select from.
     * @param targetResource the name of the target resource, useful for debugging purposes.
     * @return a {@link HostSelector}
     */
    HostSelector<ResolvedAddress, C> buildSelector(
            List<Host<ResolvedAddress, C>> hosts, String targetResource);
}
