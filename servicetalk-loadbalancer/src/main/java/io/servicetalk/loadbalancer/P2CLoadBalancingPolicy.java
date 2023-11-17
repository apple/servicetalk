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

import java.util.Random;
import javax.annotation.Nullable;

final class P2CLoadBalancingPolicy implements LoadBalancingPolicy {

    private final int maxEffort;
    @Nullable
    private final Random random;

    private P2CLoadBalancingPolicy(final int maxEffort, @Nullable final Random random) {
        this.maxEffort = maxEffort;
        this.random = random;
    }

    @Override
    public <ResolvedAddress, C extends LoadBalancedConnection> HostSelector<ResolvedAddress, C>
    buildSelector(String targetResource) {
        return new P2CSelector<>(targetResource, maxEffort, random);
    }

    @Override
    public String loadBalancerName() {
        return "P2C";
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private static final int DEFAULT_MAX_EFFORT = 5;

        private int maxEffort = DEFAULT_MAX_EFFORT;
        @Nullable
        private Random random;

        private Builder() {
        }

        public Builder maxEffort(final int maxEffort) {
            if (maxEffort <= 0) {
                throw new IllegalArgumentException("Illegal maxEffort: " + maxEffort +
                        ". maxEffort must be a positive value");
            }
            this.maxEffort = maxEffort;
            return this;
        }

        // For testing purposes only.
        Builder random(Random random) {
            this.random = random;
            return this;
        }

        P2CLoadBalancingPolicy build() {
            return new P2CLoadBalancingPolicy(maxEffort, random);
        }
    }
}
