/*
 * Copyright © 2018 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.transport.api;

/**
 * An execution strategy for all transports.
 *
 * <p>Implementations should not override the default {@link Object#equals(Object)} and {@link Object#hashCode()} method
 * implementations. Default instance equality and hash-code behavior should be consistent across all instances.
 */
@FunctionalInterface
public interface ExecutionStrategy {

    /**
     * Returns {@code true} if the instance has offloading for any operation.
     *
     * @return {@code true} if the instance has offloading for any operation.
     */
    boolean hasOffloads();

    /**
     * Returns an {@link ExecutionStrategy} that requires no offloading and is compatible with all other offloading
     * strategies.
     *
     * @return an {@link ExecutionStrategy} that requires no offloading.
     */
    static ExecutionStrategy anyStrategy() {
        return SpecialExecutionStrategy.NO_OFFLOADS;
    }

    /**
     * Returns an {@link ExecutionStrategy} that requires offloading for all actions.
     *
     * @return an {@link ExecutionStrategy} that requires offloading.
     */
    static ExecutionStrategy offloadAll() {
        return SpecialExecutionStrategy.OFFLOAD_ALL;
    }

    /**
     * Combines this execution strategy with another execution strategy.
     *
     * @param other The other execution strategy to combine.
     * @return The combined execution strategy.
     */
    default ExecutionStrategy merge(ExecutionStrategy other) {
        return hasOffloads() ?
                other.hasOffloads() ? ExecutionStrategy.offloadAll() : this :
                other.hasOffloads() ? other : ExecutionStrategy.anyStrategy();
    }
}
