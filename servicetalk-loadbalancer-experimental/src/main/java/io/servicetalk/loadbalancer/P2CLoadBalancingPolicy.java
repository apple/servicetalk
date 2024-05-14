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

import static io.servicetalk.utils.internal.NumberUtils.ensurePositive;

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
 *  *  Choices in Randomized Load Balancing</a>
 */
@Deprecated // FIXME: 0.42.45 - make package private
public final class P2CLoadBalancingPolicy<ResolvedAddress, C extends LoadBalancedConnection>
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

    /**
     * A builder for immutable {@link P2CLoadBalancingPolicy} instances.
     */
    @Deprecated // FIXME: 0.42.45 - remove builder.
    public static final class Builder {

        private static final boolean DEFAULT_IGNORE_WEIGHTS = false;
        private static final int DEFAULT_MAX_EFFORT = 5;

        private boolean ignoreWeights = DEFAULT_IGNORE_WEIGHTS;
        private int maxEffort = DEFAULT_MAX_EFFORT;
        private boolean failOpen = DEFAULT_FAIL_OPEN_POLICY;
        @Nullable
        private Random random;

        /**
         * Set the maximum number of attempts that P2C will attempt to select a pair with at least one
         * healthy host.
         * Defaults to {@value DEFAULT_MAX_EFFORT}.
         * @param maxEffort the maximum number of attempts.
         * @return {@code this}
         */
        public Builder maxEffort(final int maxEffort) {
            this.maxEffort = ensurePositive(maxEffort, "maxEffort");
            return this;
        }

        /**
         * Set whether the selector should fail-open in the event no healthy hosts are found.
         * When a load balancing policy is configured to fail-open and is unable to find a healthy host, it will attempt
         * to select or establish a connection from an arbitrary host even if it is unlikely to return a healthy
         * session.
         * Defaults to {@value DEFAULT_FAIL_OPEN_POLICY}.
         * @param failOpen if true, will attempt  to select or establish a connection from an arbitrary host even if it
         *                 is unlikely to return a healthy  session.
         * @return {@code this}
         */
        public Builder failOpen(final boolean failOpen) {
            this.failOpen = failOpen;
            return this;
        }

        /**
         * Set whether the host selector should ignore {@link Host}s weight.
         * Host weight influences the probability it will be selected to serve a request. The host weight can come
         * from many sources including known host capacity, priority groups, and others, so ignoring weight
         * information can lead to other features not working properly and should be used with care.
         * Defaults to {@value DEFAULT_IGNORE_WEIGHTS}.
         *
         * @param ignoreWeights whether the host selector should ignore host weight information.
         * @return {@code this}
         */
        public Builder ignoreWeights(final boolean ignoreWeights) {
            this.ignoreWeights = ignoreWeights;
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
         * @param <C> the refined type of the {@link LoadBalancedConnection}.
         * @return the concrete {@link P2CLoadBalancingPolicy}.
         */
        public <ResolvedAddress, C extends LoadBalancedConnection> P2CLoadBalancingPolicy<ResolvedAddress, C> build() {
            return new P2CLoadBalancingPolicy<>(ignoreWeights, maxEffort, failOpen, random);
        }
    }
}
