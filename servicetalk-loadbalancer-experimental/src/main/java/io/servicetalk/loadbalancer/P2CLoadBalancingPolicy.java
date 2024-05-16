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
 * <p>
 * This load balancing policy is characterized by the algorithm:
 * - select two hosts randomly: hosta, and hostb.
 * - if neither host is healthy, repeat selection process until max-effort.
 * - pick the 'best' host of the two options.
 * @param <ResolvedAddress> the type of the resolved address.
 * @param <C> the type of the load balanced connection.
 * @see <a href="https://www.eecs.harvard.edu/~michaelm/postscripts/tpds2001.pdf">Mitzenmacher (2001) The Power of Two
 *    Choices in Randomized Load Balancing</a>
 */
final class P2CLoadBalancingPolicy<ResolvedAddress, C extends LoadBalancedConnection>
        extends LoadBalancingPolicy<ResolvedAddress, C> {

    private final boolean ignoreWeights;
    private final int maxEffort;
    private final boolean failOpen;
    @Nullable
    private final Random random;

    P2CLoadBalancingPolicy(final boolean ignoreWeights, final int maxEffort,
                                   final boolean failOpen, @Nullable final Random random) {
        this.ignoreWeights = ignoreWeights;
        this.maxEffort = maxEffort;
        this.failOpen = failOpen;
        this.random = random;
    }

    @Override
    HostSelector<ResolvedAddress, C> buildSelector(
            List<Host<ResolvedAddress, C>> hosts, String targetResource) {
        return new P2CSelector<>(hosts, targetResource, ignoreWeights, maxEffort, failOpen, random);
    }

    @Override
    public String name() {
        return "P2C";
    }

    @Override
    public String toString() {
        return name() + "(failOpen=" + failOpen + ", maxEffort=" + maxEffort + ')';
    }
}
