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

import io.servicetalk.concurrent.api.Executor;

import javax.annotation.Nullable;

import static io.servicetalk.http.api.DefaultHttpExecutionStrategy.OFFLOAD_RECEIVE_DATA;
import static io.servicetalk.http.api.DefaultHttpExecutionStrategy.OFFLOAD_RECEIVE_META;
import static io.servicetalk.http.api.DefaultHttpExecutionStrategy.OFFLOAD_SEND;
import static io.servicetalk.http.api.NoOffloadsHttpExecutionStrategy.NO_OFFLOADS;

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
        return Builder.DEFAULT;
    }

    /**
     * The default {@link HttpExecutionStrategy} using the passed {@link Executor}.
     *
     * @param executor {@link Executor} to use.
     * @return Default {@link HttpExecutionStrategy}.
     */
    public static HttpExecutionStrategy defaultStrategy(Executor executor) {
        return customStrategyBuilder().offloadAll().executor(executor).build();
    }

    /**
     * A {@link HttpExecutionStrategy} that disables all offloads.
     *
     * @return {@link HttpExecutionStrategy} that disables all offloads.
     */
    public static HttpExecutionStrategy noOffloadsStrategy() {
        return NO_OFFLOADS;
    }

    /**
     * A {@link HttpExecutionStrategy} that disables all offloads.
     *
     * @return {@link HttpExecutionStrategy} that disables all offloads.
     */
    public static Builder customStrategyBuilder() {
        return new Builder();
    }

    /**
     * A builder to build an {@link HttpExecutionStrategy}.
     */
    public static final class Builder {

        static final HttpExecutionStrategy DEFAULT = new Builder().offloadAll().build();

        @Nullable
        private Executor executor;
        private byte offloads;
        private boolean threadAffinity;

        private Builder() {
        }

        /**
         * Enables offloading for receiving of metadata.
         *
         * @return {@code this}.
         */
        public Builder offloadReceiveMetadata() {
            return addOffload(OFFLOAD_RECEIVE_META);
        }

        /**
         * Enables offloading for receiving of data.
         *
         * @return {@code this}.
         */
        public Builder offloadReceiveData() {
            return addOffload(OFFLOAD_RECEIVE_DATA);
        }

        /**
         * Enables offloading for sending.
         *
         * @return {@code this}.
         */
        public Builder offloadSend() {
            return addOffload(OFFLOAD_SEND);
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
         * Specify an {@link Executor} to use.
         *
         * @param executor {@link Executor} to use.
         * @return {@code this}.
         */
        public Builder executor(Executor executor) {
            this.executor = executor;
            return this;
        }

        /**
         * Enable thread affinity while offloading. When enabled, offloading implementation will favor using a
         * single thread per subscribe of a source.
         *
         * @return {@code this}.
         */
        public Builder offloadWithThreadAffinity() {
            threadAffinity = true;
            return this;
        }

        /**
         * Builds a new {@link HttpExecutionStrategy}.
         *
         * @return New {@link HttpExecutionStrategy}.
         */
        public HttpExecutionStrategy build() {
            return offloads == 0 ? NO_OFFLOADS : new DefaultHttpExecutionStrategy(executor, offloads, threadAffinity);
        }

        private Builder addOffload(byte flag) {
            offloads |= flag;
            return this;
        }
    }
}
