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
import static io.servicetalk.http.api.HttpExecutionStrategies.Builder.MergeStrategy.Merge;
import static io.servicetalk.http.api.HttpExecutionStrategies.Builder.MergeStrategy.ReturnOther;
import static io.servicetalk.http.api.HttpExecutionStrategies.Builder.MergeStrategy.ReturnSelf;
import static io.servicetalk.http.api.NoOffloadsHttpExecutionStrategy.NO_OFFLOADS_NO_EXECUTOR;
import static java.util.Objects.requireNonNull;

/**
 * A factory to create different {@link HttpExecutionStrategy}.
 */
public final class HttpExecutionStrategies {

    // Package private constants to be used across programming model adapters, should not be made public.
    static final HttpExecutionStrategy OFFLOAD_NONE_STRATEGY = customStrategyBuilder().mergeStrategy(Merge).build();
    static final HttpExecutionStrategy OFFLOAD_RECEIVE_META_STRATEGY =
            customStrategyBuilder().offloadReceiveMetadata().mergeStrategy(Merge).build();
    static final HttpExecutionStrategy OFFLOAD_ALL_STRATEGY = customStrategyBuilder().offloadAll()
            .mergeStrategy(Merge).build();
    static final HttpExecutionStrategy OFFLOAD_RECEIVE_META_AND_SEND_STRATEGY =
            customStrategyBuilder().offloadReceiveMetadata().offloadSend().mergeStrategy(Merge).build();
    static final HttpExecutionStrategy OFFLOAD_SEND_STRATEGY = customStrategyBuilder().offloadSend()
            .mergeStrategy(Merge).build();

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
        return customStrategyBuilder().offloadAll().executor(executor).mergeStrategy(ReturnOther).build();
    }

    /**
     * A {@link HttpExecutionStrategy} that disables all offloads.
     *
     * @return {@link HttpExecutionStrategy} that disables all offloads.
     */
    public static HttpExecutionStrategy noOffloadsStrategy() {
        return NO_OFFLOADS_NO_EXECUTOR;
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

        static final HttpExecutionStrategy DEFAULT = new Builder().offloadAll().mergeStrategy(ReturnOther).build();

        @Nullable
        private Executor executor;
        private byte offloads;
        private boolean threadAffinity;
        @Nullable
        private MergeStrategy mergeStrategy;

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
         * Disable all offloads.
         *
         * @return {@code this}.
         */
        public Builder offloadNone() {
            offloads = 0;
            return this;
        }

        /**
         * Specify an {@link Executor} to use.
         *
         * @param executor {@link Executor} to use.
         * @return {@code this}.
         */
        public Builder executor(Executor executor) {
            this.executor = requireNonNull(executor);
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
         * Specify the {@link MergeStrategy} for the {@link HttpExecutionStrategy} built from this {@link Builder}.
         *
         * @param mergeStrategy {@link MergeStrategy} to use.
         * @return {@code this}.
         */
        // Intentionally package-private, API is not required to be public for the lack of use cases.
        Builder mergeStrategy(MergeStrategy mergeStrategy) {
            this.mergeStrategy = mergeStrategy;
            return this;
        }

        /**
         * Builds a new {@link HttpExecutionStrategy}.
         *
         * @return New {@link HttpExecutionStrategy}.
         */
        public HttpExecutionStrategy build() {
            if (offloads == 0 && mergeStrategy == null) {
                return executor == null ? NO_OFFLOADS_NO_EXECUTOR : noOffloadsStrategyWithExecutor(executor);
            } else {
                if (mergeStrategy == null) {
                    // User provided strategies will always be used without merging. Any custom behavior will be used at
                    // the merged call site.
                    mergeStrategy = ReturnSelf;
                }
                return new DefaultHttpExecutionStrategy(executor, offloads, threadAffinity, mergeStrategy);
            }
        }

        private static HttpExecutionStrategy noOffloadsStrategyWithExecutor(final Executor executor) {
            return new NoOffloadsHttpExecutionStrategy(executor);
        }

        private Builder addOffload(byte flag) {
            offloads |= flag;
            return this;
        }

        /**
         * A strategy for implementing {@link HttpExecutionStrategy#merge(HttpExecutionStrategy)} method.
         */
        enum MergeStrategy {
            ReturnSelf,
            ReturnOther,
            Merge
        }
    }
}
