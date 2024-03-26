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

import static io.servicetalk.utils.internal.NumberUtils.ensurePositive;

public final class LoadBalancingPolicies {

    /**
     * The default fail-open policy to use for {@link HostSelector} implementations.
     */
    static final boolean DEFAULT_FAIL_OPEN_POLICY = false;

    /**
     * Default max-effort value.
     */
    static final int DEFAULT_MAX_EFFORT = 5;

    private LoadBalancingPolicies() {
        // no instances
    }

    /**
     * Power of Two Choices (P2C) load balancing policy.
     * This {@link LoadBalancingPolicy} algorithm is based on work by Michael David Mitzenmacher in The Power of Two
     * Choices in Randomized Load Balancing.
     * <p>
     *  This load balancing policy is characterized by the algorithm:
     *  - select two hosts randomly: hosta, and hostb.
     *  - if neither host is healthy, repeat selection process until max-effort.
     *  - pick the 'best' host of the two options.
     * @return a P2C load balancing policy with fail-open policy of {@value DEFAULT_FAIL_OPEN_POLICY} and a max-effort
     * of {@value DEFAULT_MAX_EFFORT}.
     * @see <a href="https://ieeexplore.ieee.org/document/963420">Mitzenmacher (2001) The Power of Two
     * Choices in Randomized Load Balancing</a>
     */
    public static LoadBalancingPolicy p2c() {
        return p2c(DEFAULT_FAIL_OPEN_POLICY, DEFAULT_MAX_EFFORT);
    }

    /**
     * Power of Two Choices (P2C) load balancing policy.
     * This {@link LoadBalancingPolicy} algorithm is based on work by Michael David Mitzenmacher in The Power of Two
     * Choices in Randomized Load Balancing.
     * <p>
     *  This load balancing policy is characterized by the algorithm:
     *  - select two hosts randomly: hosta, and hostb.
     *  - if neither host is healthy, repeat selection process until max-effort.
     *  - pick the 'best' host of the two options.
     * @param failOpen whether to attempt connection selection even if a health host cannot be found.
     * @return a P2C load balancing policy with the configured fail-open policy and a max effort of
     * {@value DEFAULT_MAX_EFFORT}.
     * @see <a href="https://ieeexplore.ieee.org/document/963420">Mitzenmacher (2001) The Power of Two
     * Choices in Randomized Load Balancing</a>
     */
    public static LoadBalancingPolicy p2c(boolean failOpen) {
        return p2c(failOpen, DEFAULT_MAX_EFFORT);
    }

    /**
     * Power of Two Choices (P2C) load balancing policy.
     * This {@link LoadBalancingPolicy} algorithm is based on work by Michael David Mitzenmacher in The Power of Two
     * Choices in Randomized Load Balancing.
     * <p>
     *  This load balancing policy is characterized by the algorithm:
     *  - select two hosts randomly: hosta, and hostb.
     *  - if neither host is healthy, repeat selection process until max-effort.
     *  - pick the 'best' host of the two options.
     * @param maxEffort the maximum number of attempts to find a healthy host before giving up.
     * @return a P2C load balancing policy with the configured max effort and fail-open policy of
     * {@value DEFAULT_FAIL_OPEN_POLICY}.
     * @see <a href="https://ieeexplore.ieee.org/document/963420">Mitzenmacher (2001) The Power of Two
     * Choices in Randomized Load Balancing</a>
     */
    public static LoadBalancingPolicy p2c(int maxEffort) {
        return p2c(DEFAULT_FAIL_OPEN_POLICY, maxEffort);
    }

    /**
     * Power of Two Choices (P2C) load balancing policy.
     * This {@link LoadBalancingPolicy} algorithm is based on work by Michael David Mitzenmacher in The Power of Two
     * Choices in Randomized Load Balancing.
     * <p>
     *  This load balancing policy is characterized by the algorithm:
     *  - select two hosts randomly: hosta, and hostb.
     *  - if neither host is healthy, repeat selection process until max-effort.
     *  - pick the 'best' host of the two options.
     * @param failOpen whether to attempt connection selection even if a health host cannot be found.
     * @param maxEffort the maximum number of attempts to find a healthy host before giving up.
     * @return a P2C load balancing policy with the configured parameters.
     * @see <a href="https://ieeexplore.ieee.org/document/963420">Mitzenmacher (2001) The Power of Two
     * Choices in Randomized Load Balancing</a>
     */
    public static LoadBalancingPolicy p2c(boolean failOpen, int maxEffort) {
        ensurePositive(maxEffort, "maxEffort");
        return new P2CLoadBalancingPolicy<>(failOpen, maxEffort, null);
    }

    /**
     * Round-Robin load balancing policy.
     * Round-Robin selection consists of iterating around the endpoint set in a circular pattern.
     * @return a round-robin load balancing policy with a fail-open policy of {@value DEFAULT_FAIL_OPEN_POLICY}.
     */
    public static LoadBalancingPolicy roundRobin() {
        return roundRobin(DEFAULT_FAIL_OPEN_POLICY);
    }

    public static LoadBalancingPolicy roundRobin(boolean failOpen) {
        return new RoundRobinLoadBalancingPolicy<>(failOpen);
    }
}
