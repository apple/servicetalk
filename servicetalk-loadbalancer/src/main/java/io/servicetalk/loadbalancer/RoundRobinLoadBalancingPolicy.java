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

final class RoundRobinLoadBalancingPolicy implements LoadBalancingPolicy {

    private RoundRobinLoadBalancingPolicy() {
    }

    @Override
    public <ResolvedAddress, C extends LoadBalancedConnection> HostSelector<ResolvedAddress, C>
    buildSelector(final String targetResource) {
        return new RoundRobinSelector<>(targetResource);
    }

    @Override
    public String loadBalancerName() {
        return "RoundRobin";
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private Builder() {
        }

        public RoundRobinLoadBalancingPolicy build() {
            return new RoundRobinLoadBalancingPolicy();
        }
    }
}
