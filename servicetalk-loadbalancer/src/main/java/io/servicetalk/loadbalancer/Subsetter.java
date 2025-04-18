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

import java.util.List;

/**
 * An abstraction for picking a subset of hosts to use for load balancing purposes.
 */
interface Subsetter {

    /**
     * Subset the provided host list into the list of hosts that will be used for load balancing.
     * @param hosts the eligible list of hosts.
     * @return the list of hosts that should be used for load balancing.
     * @param <T> the type of the {@link PrioritizedHost}
     */
    <T extends PrioritizedHost> List<T> subset(List<T> hosts);

    /**
     * Build {@link Subsetter} instances using the provided load balancer description.
     */
    interface SubsetterFactory {

        /**
         * Build {@link Subsetter} instances using the provided load balancer description.
         * @param lbDescription the string based description of the load balancer, useful for observability.
         * @return the constructed {@link Subsetter}.
         */
        Subsetter newSubsetter(String lbDescription);
    }
}
