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
import io.servicetalk.client.api.LoadBalancer;

import java.util.List;
import java.util.Random;
import javax.annotation.Nullable;

/**
 * A random selection "power of two choices" load balancing policy.
 * <p>
 * This {@link LoadBalancer} selection algorithm is based on work by Michael David Mitzenmacher in The Power of Two
 * Choices in Randomized Load Balancing.
 *  @see <a href="https://www.eecs.harvard.edu/~michaelm/postscripts/tpds2001.pdf">Mitzenmacher (2001) The Power of Two
 *  Choices in Randomized Load Balancing</a>
 * <p>
 * This load balancing policy is characterized by the algorithm:
 * - select two hosts randomly: hosta, and hostb.
 * - if neither host is healthy, repeat selection process until max-effort.
 * - pick the 'best' host of the two options.
 */
final class P2CLoadBalancingPolicy<ResolvedAddress, C extends LoadBalancedConnection>
        implements LoadBalancingPolicy<ResolvedAddress, C> {

    private final int maxEffort;
    @Nullable
    private final Random random;

    private P2CLoadBalancingPolicy(final int maxEffort, @Nullable final Random random) {
        this.maxEffort = maxEffort;
        this.random = random;
    }

    @Override
    public HostSelector<ResolvedAddress, C> buildSelector(List<Host<ResolvedAddress, C>> hosts, String targetResource) {
        return new P2CSelector<>(hosts, targetResource, maxEffort, random);
    }

    @Override
    public String name() {
        return "P2C";
    }

    @Override
    public String toString() {
        return name() + "(maxEffort=" + maxEffort + ')';
    }

    /**
     * A builder for immutable {@link P2CLoadBalancingPolicy} instances.
     */
    public static final class Builder {

        private static final int DEFAULT_MAX_EFFORT = 5;

        private int maxEffort = DEFAULT_MAX_EFFORT;
        @Nullable
        private Random random;

        /**
         * Set the maximum number of attempts that P2C will attempt to select a pair with at least one
         * healthy host.
         * @param maxEffort the maximum number of attempts.
         * @return this {@link Builder}.
         */
        public Builder maxEffort(final int maxEffort) {
            if (maxEffort <= 0) {
                throw new IllegalArgumentException("Invalid maxEffort: " + maxEffort + " (expected > 0)");
            }
            this.maxEffort = maxEffort;
            return this;
        }

        // For testing purposes only.
        Builder random(Random random) {
            this.random = random;
            return this;
        }

        /**
         * Construct an immutable {@link P2CLoadBalancingPolicy}.
         * @param <ResolvedAddress> the type of the resolved address.
         * @param <C> the refined type of the {@LoadBalancedConnection}.
         * @return the concrete {@link P2CLoadBalancingPolicy}.
         */
        public <ResolvedAddress, C extends LoadBalancedConnection> P2CLoadBalancingPolicy<ResolvedAddress, C> build() {
            return new P2CLoadBalancingPolicy<>(maxEffort, random);
        }
    }
}
