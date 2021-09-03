/*
 * Copyright © 2018, 2021 Apple Inc. and the ServiceTalk project authors
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

import java.util.EnumSet;
import javax.annotation.Nullable;

import static io.servicetalk.http.api.HttpExecutionStrategies.HttpOffload.OFFLOAD_RECEIVE_DATA;
import static io.servicetalk.http.api.HttpExecutionStrategies.HttpOffload.OFFLOAD_RECEIVE_META;
import static io.servicetalk.http.api.HttpExecutionStrategies.HttpOffload.OFFLOAD_SEND;
import static io.servicetalk.http.api.SpecialHttpExecutionStrategy.DEFAULT_HTTP_EXECUTION_STRATEGY;
import static io.servicetalk.http.api.SpecialHttpExecutionStrategy.OFFLOAD_NEVER_STRATEGY;

/**
 * A factory to create different {@link HttpExecutionStrategy}.
 */
public final class HttpExecutionStrategies {

    private HttpExecutionStrategies() {
        // No instances.
    }

    /**
     * The default {@link HttpExecutionStrategy}.
     *
     * @return Default {@link HttpExecutionStrategy}.
     */
    public static HttpExecutionStrategy defaultStrategy() {
        return DEFAULT_HTTP_EXECUTION_STRATEGY;
    }

    /**
     * A {@link HttpExecutionStrategy} that disables all offloads on the request-response path.
     *
     * @return {@link HttpExecutionStrategy} that disables all request-response path offloads.
     */
    public static HttpExecutionStrategy noOffloadsStrategy() {
        return OFFLOAD_NEVER_STRATEGY;
    }

    /**
     * A Builder for creating custom {@link HttpExecutionStrategy}.
     *
     * @return a builder for custom {@link HttpExecutionStrategy}.
     */
    public static Builder customStrategyBuilder() {
        return new Builder();
    }

    /**
     * Find the difference between two strategies and provide a resulting strategy if there are differences in between
     * the strategies. The resulting strategy can then be used to offload the difference between two strategies. This
     * is typically used if there is a call hierarchy and each entity in the hierarchy defines its own
     * {@link HttpExecutionStrategy}. This method is useful to reduce duplicating work across these entities if the
     * caller has already offloaded all the paths required to be offloaded by the callee.
     * <pre>
     *     Entities:         Entity 1      ⇒      Entity 2      ⇒      Entity 3
     *                                  (calls)              (calls)
     *     Strategies:     No offloads          Offload Send        Offload Send + Meta
     * </pre>
     * In the above call hierarchy, if {@code Entity 1} uses this method to find the {@link HttpExecutionStrategy} to
     * use for invoking {@code Entity 2}, the resulting {@link HttpExecutionStrategy} will only offload sending to the
     * transport. However, if {@code Entity 2} uses this method to find the {@link HttpExecutionStrategy} to
     * use for invoking {@code Entity 3}, the resulting {@link HttpExecutionStrategy} will only offload receiving of
     * metadata.
     * <p>
     * Effectively, using this method will remove redundant offloading when more than one entity each provide their own
     * {@link HttpExecutionStrategy}.
     *
     * @param left {@link HttpExecutionStrategy} which is already in effect.
     * @param right {@link HttpExecutionStrategy} which is expected to be used.
     * @return {@link HttpExecutionStrategy} if there are any differences between the two strategies. {@code null} if
     * the two strategies are the same.
     */
    @Nullable
    public static HttpExecutionStrategy difference(final HttpExecutionStrategy left,
                                                   final HttpExecutionStrategy right) {
        if (left.equals(right) || !right.hasOffloads()) {
            return null;
        }
        if (!left.hasOffloads()) {
            return right;
        }

        byte effectiveOffloads = 0;
        if (right.isSendOffloaded() && !left.isSendOffloaded()) {
            effectiveOffloads |= OFFLOAD_SEND.mask();
        }
        if (right.isMetadataReceiveOffloaded() && !left.isMetadataReceiveOffloaded()) {
            effectiveOffloads |= OFFLOAD_RECEIVE_META.mask();
        }
        if (right.isDataReceiveOffloaded() && !left.isDataReceiveOffloaded()) {
            effectiveOffloads |= OFFLOAD_RECEIVE_DATA.mask();
        }

        if (0 == effectiveOffloads) {
            // No extra offloads required
            return null;
        }

        return DefaultHttpExecutionStrategy.fromMask(effectiveOffloads);
    }

    /**
     * A builder to build an {@link HttpExecutionStrategy}.
     */
    public static final class Builder {

        private byte offloads;

        private Builder() {
        }

        /**
         * Enables offloading for receiving of metadata.
         *
         * @return {@code this}.
         */
        public Builder offloadReceiveMetadata() {
            return offload(OFFLOAD_RECEIVE_META);
        }

        /**
         * Enables offloading for receiving of data.
         *
         * @return {@code this}.
         */
        public Builder offloadReceiveData() {
            return offload(OFFLOAD_RECEIVE_DATA);
        }

        /**
         * Enables offloading for sending.
         *
         * @return {@code this}.
         */
        public Builder offloadSend() {
            return offload(OFFLOAD_SEND);
        }

        /**
         * Enable all offloads.
         *
         * @return {@code this}.
         */
        public Builder offloadAll() {
            return offloadReceiveMetadata().offloadReceiveData().offloadSend();
        }

        /**
         * Enable a specific offload.
         *
         * @param offload The offload to enable.
         * @return {@code this}.
         */
        private Builder offload(HttpExecutionStrategies.HttpOffload offload) {
            offloads |= offload.mask();
            return this;
        }

        /**
         * Disable all offloads.
         *
         * @return {@code this}.
         */
        public Builder offloadNone() {
            offloads = 0;
            return this;
        }

        /**
         * Builds a new {@link HttpExecutionStrategy}.
         *
         * @return New {@link HttpExecutionStrategy}.
         */
        public HttpExecutionStrategy build() {
            return DefaultHttpExecutionStrategy.fromMask(offloads);
        }
    }

    /**
     * The HTTP offload points available.
     */
    enum HttpOffload {
        OFFLOAD_RECEIVE_META,
        OFFLOAD_RECEIVE_DATA,
        OFFLOAD_SEND;

        byte mask() {
            return (byte) (1 << ordinal());
        }

        static byte toMask(EnumSet<HttpOffload> offloads) {
            return (byte) offloads.stream().mapToInt(HttpOffload::mask).reduce(0, Integer::sum);
        }
    }
}
