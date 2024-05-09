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
package io.servicetalk.apple.capacity.limiter.api;

/**
 * Classification of requests.
 * <p>
 * In the context of capacity, classification can be used to allow prioritization of requests under
 * certain conditions. When a system is load-shedding, it can still choose to accommodate important demand.
 * The classification is not a feature supported by all {@link CapacityLimiter}s but rather the
 * {@link CapacityLimiter} of preference needs to support it. For {@link CapacityLimiter}s that classification
 * is not supported, if a classification is provided it will be discarded.
 * <p>
 * It's not the purpose of this interface to define characteristics or expectations of the currently available or
 * future supported classifications. This is an implementation detail of the respective {@link CapacityLimiter}.
 * <p>
 * Classification is treated as a hint for a {@link CapacityLimiter} and are expected to be strictly respected,
 * they are a best effort approach to communicate user's desire to the {@link CapacityLimiter}.
 */
@FunctionalInterface
public interface Classification {
    /**
     * The priority should be a positive number between 0 and 100 (inclusive), which hints to a {@link CapacityLimiter}
     * the importance of a {@link Classification}.
     * <p>
     * Higher value represents the most important {@link Classification}, while lower value represents less important
     * {@link Classification}.
     *
     * @return A positive value between 0 and 100 (inclusive) that hints importance of a request to a
     * {@link CapacityLimiter}.
     */
    int priority();
}
