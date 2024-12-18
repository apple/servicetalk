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

import java.util.Random;
import javax.annotation.Nullable;

import static io.servicetalk.utils.internal.NumberUtils.ensurePositive;

/**
 * A builder for {@link P2CLoadBalancingPolicy} instances.
 *
 * @see LoadBalancingPolicies#p2c()
 */
public final class P2CLoadBalancingPolicyBuilder {

    private static final boolean DEFAULT_IGNORE_WEIGHTS = false;
    private static final int DEFAULT_MAX_EFFORT = 5;
    private static final boolean DEFAULT_FAIL_OPEN_POLICY = AbstractLoadBalancingPolicy.DEFAULT_FAIL_OPEN_POLICY;

    private boolean ignoreWeights = DEFAULT_IGNORE_WEIGHTS;
    private int maxEffort = DEFAULT_MAX_EFFORT;
    private boolean failOpen = DEFAULT_FAIL_OPEN_POLICY;
    @Nullable
    private Random random;

    P2CLoadBalancingPolicyBuilder() {
        // package private
    }

    /**
     * Set the maximum number of attempts that P2C will attempt to select a pair with at least one healthy host.
     * <p>
     * Defaults to {@value DEFAULT_MAX_EFFORT}.
     *
     * @param maxEffort the maximum number of attempts.
     * @return {@code this}
     */
    public P2CLoadBalancingPolicyBuilder maxEffort(final int maxEffort) {
        this.maxEffort = ensurePositive(maxEffort, "maxEffort");
        return this;
    }

    /**
     * Set whether the selector should fail-open in the event no healthy hosts are found.
     * <p>
     * When a load balancing policy is configured to fail-open and is unable to find a healthy host, it will attempt
     * to select or establish a connection from an arbitrary host even if it is unlikely to return a healthy session.
     * <p>
     * Defaults to {@value DEFAULT_FAIL_OPEN_POLICY}.
     *
     * @param failOpen if true, will attempt  to select or establish a connection from an arbitrary host even if it
     *                 is unlikely to return a healthy  session.
     * @return {@code this}
     */
    public P2CLoadBalancingPolicyBuilder failOpen(final boolean failOpen) {
        this.failOpen = failOpen;
        return this;
    }

    /**
     * Set whether the host selector should ignore {@link Host}s weight.
     * <p>
     * Host weight influences the probability it will be selected to serve a request. The host weight can come
     * from many sources including known host capacity, priority groups, and others, so ignoring weight
     * information can lead to other features not working properly and should be used with care.
     * <p>
     * Defaults to {@value DEFAULT_IGNORE_WEIGHTS}.
     *
     * @param ignoreWeights whether the host selector should ignore host weight information.
     * @return {@code this}
     */
    public P2CLoadBalancingPolicyBuilder ignoreWeights(final boolean ignoreWeights) {
        this.ignoreWeights = ignoreWeights;
        return this;
    }

    // For testing purposes only.
    P2CLoadBalancingPolicyBuilder random(final Random random) {
        this.random = random;
        return this;
    }

    /**
     * Construct the {@link P2CLoadBalancingPolicy}.
     *
     * @param <ResolvedAddress> the type of the resolved address.
     * @param <C>               the refined type of the {@link LoadBalancedConnection}.
     * @return the concrete {@link P2CLoadBalancingPolicy}.
     */
    public <ResolvedAddress, C extends LoadBalancedConnection> LoadBalancingPolicy<ResolvedAddress, C> build() {
        return new P2CLoadBalancingPolicy<>(ignoreWeights, maxEffort, failOpen, random);
    }
}
