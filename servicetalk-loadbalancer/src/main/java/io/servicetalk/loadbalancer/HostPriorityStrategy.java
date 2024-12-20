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

@FunctionalInterface
interface HostPriorityStrategy {

    /**
     * Adjust the host set to account for priority.
     * @param hosts the set of hosts to prioritize.
     * @return the collection of hosts that should be used and have had their weights adjusted.
     * @param <T> the refined type of the {@link PrioritizedHost}.
     */
    <T extends PrioritizedHost> List<T> prioritize(List<T> hosts);
}
