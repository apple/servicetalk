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

/**
 * Handles for determining the weight of a host based on it's underlying weight and priority.
 */
interface PrioritizedHost {

    /**
     * The current priority of the host.
     * @return the current priority of the host.
     */
    int priority();

    /**
     * Whether the host is considered healthy or not.
     * @return whether the host is considered healthy or not.
     */
    boolean isHealthy();

    /**
     * The weight of the host to use for load balancing.
     * @return the weight of the host to use for load balancing.
     */
    double loadBalancingWeight();

    /**
     * Set the weight of the host to use during load balancing.
     * @param weight the weight of the host to use during load balancing.
     */
    void loadBalancingWeight(double weight);
}
