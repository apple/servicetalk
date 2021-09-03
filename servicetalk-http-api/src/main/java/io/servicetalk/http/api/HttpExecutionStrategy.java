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
package io.servicetalk.http.api;

import io.servicetalk.transport.api.ExecutionStrategy;

/**
 * An execution strategy for HTTP client and servers.
 */
public interface HttpExecutionStrategy extends ExecutionStrategy {

    /**
     * Returns {@code true} if any offloading is enabled for this {@link HttpExecutionStrategy}.
     *
     * @return {@code true} if any offloading is enabled for this {@link HttpExecutionStrategy}.
     */
    default boolean hasOffloads() {
        return isSendOffloaded() || isMetadataReceiveOffloaded() || isDataReceiveOffloaded();
    }

    /**
     * Returns {@code true} if metadata receive offloading is enabled for this {@link HttpExecutionStrategy}.
     *
     * @return {@code true} if metadata receive offloading is enabled for this {@link HttpExecutionStrategy}.
     */
    boolean isMetadataReceiveOffloaded();

    /**
     * Returns {@code true} if data receive offloading is enabled for this {@link HttpExecutionStrategy}.
     *
     * @return {@code true} if data receive offloading is enabled for this {@link HttpExecutionStrategy}.
     */
    boolean isDataReceiveOffloaded();

    /**
     * Returns {@code true} if send offloading is enabled for this {@link HttpExecutionStrategy}.
     *
     * @return {@code true} if send offloading is enabled for this {@link HttpExecutionStrategy}.
     */
    boolean isSendOffloaded();

    /**
     * Merges the passed {@link HttpExecutionStrategy} with {@code this} {@link HttpExecutionStrategy} and return the
     * merged result.
     *
     * @param other {@link HttpExecutionStrategy} to merge with {@code this}.
     * @return Merged {@link HttpExecutionStrategy}.
     */
    HttpExecutionStrategy merge(HttpExecutionStrategy other);
}
