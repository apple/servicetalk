/*
 * Copyright © 2021 Apple Inc. and the ServiceTalk project authors
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
 * An execution strategy for creating or accepting connections.
 */
public interface ConnectExecutionStrategy extends ExecutionStrategy {

    /**
     * Returns {@code true} if the instance has offloading for any operation.
     *
     * @return {@code true} if the instance has offloading for any operation.
     */
    @Override
    default boolean hasOffloads() {
        return isConnectOffloaded();
    }

    /**
     * Returns true if connection creation or accept requires offloading.
     *
     * @return true if connection creation or accept requires offloading
     */
    boolean isConnectOffloaded();

    /**
     * Combines this execution strategy with another execution strategy.
     *
     * @param other The other execution strategy to combine. This is converted to a {@link ConnectExecutionStrategy}
     * using {@link #from(ExecutionStrategy)}.
     * @return The combined execution strategy.
     */
    @Override
    default ConnectExecutionStrategy merge(ExecutionStrategy other) {
        ConnectExecutionStrategy asCES = from(other);
        return hasOffloads() ?
                asCES.hasOffloads() ? ConnectExecutionStrategy.offloadAll() : this :
                asCES.hasOffloads() ? asCES : ConnectExecutionStrategy.offloadNone();
    }

    /**
     * Returns an {@link ConnectExecutionStrategy} that requires no offloading.
     *
     * @return an {@link ConnectExecutionStrategy} that requires no offloading.
     */
    static ConnectExecutionStrategy offloadNone() {
        return DefaultConnectExecutionStrategy.CONNECT_NOT_OFFLOADED;
    }

    /**
     * Returns an {@link ConnectExecutionStrategy} that requires offloading for all actions.
     *
     * @return an {@link ConnectExecutionStrategy} that requires offloading.
     */
    static ConnectExecutionStrategy offloadAll() {
        return DefaultConnectExecutionStrategy.CONNECT_OFFLOADED;
    }

    /**
     * Converts the provided execution strategy to a {@link ConnectExecutionStrategy}. If the provided strategy is
     * already {@link ConnectExecutionStrategy} it is returned unchanged. For other strategies, if the strategy
     * {@link ExecutionStrategy#hasOffloads()} then {@link ConnectExecutionStrategy#offloadAll()} is returned otherwise
     * {@link ConnectExecutionStrategy#offloadNone()} is returned.
     *
     * @param executionStrategy The {@link ExecutionStrategy} to convert
     * @return converted {@link ConnectExecutionStrategy}.
     */
    static ConnectExecutionStrategy from(ExecutionStrategy executionStrategy) {
        return executionStrategy instanceof ConnectExecutionStrategy ?
                (ConnectExecutionStrategy) executionStrategy :
                    executionStrategy.hasOffloads() ?
                        ConnectExecutionStrategy.offloadAll() :
                        ConnectExecutionStrategy.offloadNone();
    }
}
