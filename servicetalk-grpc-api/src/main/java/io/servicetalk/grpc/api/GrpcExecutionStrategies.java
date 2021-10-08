/*
 * Copyright © 2019 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.grpc.api;

import io.servicetalk.http.api.HttpExecutionStrategies;
import io.servicetalk.http.api.HttpExecutionStrategy;

/**
 * A factory to create different {@link GrpcExecutionStrategy}.
 */
public final class GrpcExecutionStrategies {

    private static final GrpcExecutionStrategy NEVER_OFFLOAD_STRATEGY =
            new DefaultGrpcExecutionStrategy(HttpExecutionStrategies.noOffloadsStrategy());

    private static final GrpcExecutionStrategy DEFAULT_GRPC_EXECUTION_STRATEGY =
            new DefaultGrpcExecutionStrategy(HttpExecutionStrategies.defaultStrategy());

    private GrpcExecutionStrategies() {
        // No instances
    }

    /**
     * The default {@link GrpcExecutionStrategy}.
     *
     * @return Default {@link GrpcExecutionStrategy}.
     */
    public static GrpcExecutionStrategy defaultStrategy() {
        return DEFAULT_GRPC_EXECUTION_STRATEGY;
    }

    /**
     * A {@link GrpcExecutionStrategy} that disables all offloads.
     *
     * @return {@link GrpcExecutionStrategy} that disables all offloads.
     */
    public static GrpcExecutionStrategy noOffloadsStrategy() {
        return NEVER_OFFLOAD_STRATEGY;
    }

    /**
     * A {@link GrpcExecutionStrategy} that disables all offloads.
     *
     * @return {@link GrpcExecutionStrategy} that disables all offloads.
     */
    public static Builder customStrategyBuilder() {
        return new Builder();
    }

    /**
     * A builder to build an {@link HttpExecutionStrategy}.
     */
    public static final class Builder {

        private final HttpExecutionStrategies.Builder httpBuilder = HttpExecutionStrategies.customStrategyBuilder();

        /**
         * Enables offloading for receiving of metadata.
         *
         * @return {@code this}.
         */
        public Builder offloadReceiveMetadata() {
            httpBuilder.offloadReceiveMetadata();
            return this;
        }

        /**
         * Enables offloading for receiving of data.
         *
         * @return {@code this}.
         */
        public Builder offloadReceiveData() {
            httpBuilder.offloadReceiveData();
            return this;
        }

        /**
         * Enables offloading for sending.
         *
         * @return {@code this}.
         */
        public Builder offloadSend() {
            httpBuilder.offloadSend();
            return this;
        }

        /**
         * Enable all offloads.
         *
         * @return {@code this}.
         */
        public Builder offloadAll() {
            httpBuilder.offloadAll();
            return this;
        }

        /**
         * Disable all offloads.
         *
         * @return {@code this}.
         */
        public Builder offloadNone() {
            httpBuilder.offloadNone();
            return this;
        }

        /**
         * Builds a new {@link GrpcExecutionStrategy}.
         *
         * @return New {@link GrpcExecutionStrategy}.
         */
        public GrpcExecutionStrategy build() {
            return new DefaultGrpcExecutionStrategy(httpBuilder.build());
        }
    }
}
