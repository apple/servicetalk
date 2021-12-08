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

import static io.servicetalk.http.api.HttpExecutionStrategies.HttpOffload.OFFLOAD_CLOSE;
import static io.servicetalk.http.api.HttpExecutionStrategies.HttpOffload.OFFLOAD_EVENT;
import static io.servicetalk.http.api.HttpExecutionStrategies.HttpOffload.OFFLOAD_RECEIVE_DATA;
import static io.servicetalk.http.api.HttpExecutionStrategies.HttpOffload.OFFLOAD_RECEIVE_META;
import static io.servicetalk.http.api.HttpExecutionStrategies.HttpOffload.OFFLOAD_SEND;

/**
 * An execution strategy for HTTP client and servers.
 *
 * @see HttpExecutionStrategyInfluencer
 */
public interface HttpExecutionStrategy extends ExecutionStrategy {

    @Override
    default boolean hasOffloads() {
        return ExecutionStrategy.super.hasOffloads() || isEventOffloaded() || isRequestResponseOffloaded();
    }

    /**
     * Returns {@code true} if any portion of request/response path is offloaded for this {@link HttpExecutionStrategy}.
     *
     * @return {@code true} if any portion of request/response path is offloaded for this {@link HttpExecutionStrategy}.
     */
    default boolean isRequestResponseOffloaded() {
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
     * Returns {@code true} if event offloading is enabled for this {@link HttpExecutionStrategy}.
     *
     * @return {@code true} if event offloading is enabled for this {@link HttpExecutionStrategy}.
     */
    boolean isEventOffloaded();

    /**
     * Merges the passed {@link HttpExecutionStrategy} with {@code this} {@link HttpExecutionStrategy} and return the
     * merged result.
     *
     * @param other {@link HttpExecutionStrategy} to merge with {@code this}.
     * @return Merged {@link HttpExecutionStrategy}.
     */
    HttpExecutionStrategy merge(HttpExecutionStrategy other);

    /**
     * Returns an execution strategy which contains only the additional offloads present in other that were not present
     * in this execution strategy.
     *
     * @param other An execution strategy
     * @return An execution strategy which contains only the additional offloads present in other that were not present
     * in this execution strategy.
     */
    default HttpExecutionStrategy missing(HttpExecutionStrategy other) {
        if (this.equals(other) || !other.hasOffloads()) {
            return DefaultHttpExecutionStrategy.OFFLOAD_NONE_STRATEGY;
        }

        byte effectiveOffloads = 0;

        if (other.isSendOffloaded() && !this.isSendOffloaded()) {
            effectiveOffloads |= OFFLOAD_SEND.mask();
        }
        if (other.isMetadataReceiveOffloaded() && !this.isMetadataReceiveOffloaded()) {
            effectiveOffloads |= OFFLOAD_RECEIVE_META.mask();
        }
        if (other.isDataReceiveOffloaded() && !this.isDataReceiveOffloaded()) {
            effectiveOffloads |= OFFLOAD_RECEIVE_DATA.mask();
        }
        if (other.isEventOffloaded() && !this.isEventOffloaded()) {
            effectiveOffloads |= OFFLOAD_EVENT.mask();
        }
        if (other.isCloseOffloaded() && !this.isCloseOffloaded()) {
            effectiveOffloads |= OFFLOAD_CLOSE.mask();
        }

        return DefaultHttpExecutionStrategy.fromMask(effectiveOffloads);
    }

    /**
     * Convert from any {@link ExecutionStrategy} to the most appropriate compatible safe {@link HttpExecutionStrategy}.
     *
     * @param strategy The strategy to convert
     * @return The provided execution strategy converted to compatible safe {@link HttpExecutionStrategy}.
     */
    static HttpExecutionStrategy from(ExecutionStrategy strategy) {
        return (strategy instanceof HttpExecutionStrategy) ?
                (HttpExecutionStrategy) strategy :
                strategy.hasOffloads() ? HttpExecutionStrategies.offloadAll() : HttpExecutionStrategies.offloadNone();
    }
}
