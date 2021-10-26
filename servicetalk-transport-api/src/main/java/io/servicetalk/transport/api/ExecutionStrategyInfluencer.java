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
 * An entity that wishes to influence the {@link ExecutionStrategy} for a server or client.
 *
 * @param <S> Type of {@link ExecutionStrategy} influenced.
 */
@FunctionalInterface
public interface ExecutionStrategyInfluencer<S extends ExecutionStrategy> {

    /**
     * Return an {@link ExecutionStrategy} that describes the offloads required by the influencer. The provided
     * default implementation requests offloading of all operations.
     *
     * @return the {@link ExecutionStrategy} required by the influencer.
     */
    S requiredOffloads();

    /**
     * Construct an {@link ExecutionStrategyInfluencer} from an {@link ExecutionStrategy} which describes the offloads
     * required by the influencer.
     *
     * @param <S> Type of the execution strategy
     * @param requiredOffloads offloads required
     * @return an {@link ExecutionStrategyInfluencer}
     */
    static <S extends ExecutionStrategy> ExecutionStrategyInfluencer<? extends S> newInfluencer(S requiredOffloads) {
        return () -> requiredOffloads;
    }
}
