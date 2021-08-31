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

import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.http.api.HttpExecutionStrategies;
import io.servicetalk.http.api.HttpExecutionStrategy;

/**
 * A factory to create different {@link GrpcExecutionStrategy}.
 */
public final class GrpcExecutionStrategies {

    private static final DefaultGrpcExecutionStrategy NO_OFFLOADS =
            new DefaultGrpcExecutionStrategy(HttpExecutionStrategies.noOffloadsStrategy());

    private GrpcExecutionStrategies() {
        // No instances
    }

    /**
     * The default {@link GrpcExecutionStrategy}.
     *
     * @return Default {@link GrpcExecutionStrategy}.
     */
    public static GrpcExecutionStrategy defaultStrategy() {
        return Builder.DEFAULT;
    }

    /**
     * The default {@link GrpcExecutionStrategy} using the passed {@link Executor}.
     *
     * @param executor {@link Executor} to use.
     * @return Default {@link GrpcExecutionStrategy}.
     */
    public static GrpcExecutionStrategy defaultStrategy(final Executor executor) {
        return new DefaultGrpcExecutionStrategy(HttpExecutionStrategies.defaultStrategy(executor));
    }

    /**
     * A {@link GrpcExecutionStrategy} that disables all offloads.
     *
     * @return {@link GrpcExecutionStrategy} that disables all offloads.
     */
    public static GrpcExecutionStrategy noOffloadsStrategy() {
        return NO_OFFLOADS;
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

        static final GrpcExecutionStrategy DEFAULT =
                new DefaultGrpcExecutionStrategy(HttpExecutionStrategies.defaultStrategy());

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
         * Specify an {@link Executor} to use.
         *
         * @param executor {@link Executor} to use.
         * @return {@code this}.
         */
        public Builder executor(Executor executor) {
            httpBuilder.executor(executor);
            return this;
        }

        /**
         * Enable thread affinity while offloading. When enabled, offloading implementation will favor using a
         * single thread per subscribe of a source.
         * @return {@code this}.
         * @deprecated Use a single threaded executor with {@link #executor(Executor)} to ensure affinity.
         */
        @Deprecated
        public Builder offloadWithThreadAffinity() {
            httpBuilder.offloadWithThreadAffinity();
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
