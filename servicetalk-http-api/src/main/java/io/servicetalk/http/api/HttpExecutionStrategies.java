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
import io.servicetalk.concurrent.api.Executors;
import io.servicetalk.transport.api.ExecutionContext;

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
    static final HttpExecutionStrategy OFFLOAD_RECEIVE_DATA_STRATEGY =
            customStrategyBuilder().offloadReceiveData().mergeStrategy(Merge).build();
    static final HttpExecutionStrategy OFFLOAD_RECEIVE_DATA_AND_SEND_STRATEGY =
            customStrategyBuilder().offloadReceiveData().offloadSend().mergeStrategy(Merge).build();
    static final HttpExecutionStrategy OFFLOAD_ALL_STRATEGY = customStrategyBuilder().offloadAll()
            .mergeStrategy(Merge).build();
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
     * A {@link HttpExecutionStrategy} that disables all offloads on the request-response path.
     *
     * <p>The default offloading executor will still be used inside {@link ExecutionContext} and for all places where
     * it is referenced. To ensure that the default offloading executor is never used configure it with
     * {@link Executors#immediate()} executor explicitly: <pre>
     *     HttpExecutionStrategies.customStrategyBuilder().offloadNone().executor(immediate()).build()
     * </pre>
     *
     * @return {@link HttpExecutionStrategy} that disables all request-response path offloads.
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
     * Effectively, using this method will remove redundant offloading when more than one entities provide their own
     * {@link HttpExecutionStrategy}.
     *
     * @param fallback {@link Executor} used as fallback while invoking the strategies.
     * @param left {@link HttpExecutionStrategy} which is already in effect.
     * @param right {@link HttpExecutionStrategy} which is expected to be used.
     * @return {@link HttpExecutionStrategy} if there are any differences between the two strategies. {@code null} if
     * the two strategies are the same.
     */
    @Nullable
    public static HttpExecutionStrategy difference(final Executor fallback,
                                                   final HttpExecutionStrategy left,
                                                   final HttpExecutionStrategy right) {
        if (left.equals(right) || noOffloads(right)) {
            return null;
        }
        if (noOffloads(left)) {
            return right;
        }
        final Executor rightExecutor = right.executor();
        if (rightExecutor != null && rightExecutor != left.executor() && rightExecutor != fallback) {
            // Since the original offloads were done on a different executor, we need to offload again.
            return right;
        }

        byte effectiveOffloads = 0;
        if (right.isSendOffloaded() && !left.isSendOffloaded()) {
            effectiveOffloads |= OFFLOAD_SEND;
        }
        if (right.isMetadataReceiveOffloaded() && !left.isMetadataReceiveOffloaded()) {
            effectiveOffloads |= OFFLOAD_RECEIVE_META;
        }
        if (right.isDataReceiveOffloaded() && !left.isDataReceiveOffloaded()) {
            effectiveOffloads |= OFFLOAD_RECEIVE_DATA;
        }
        if (effectiveOffloads != 0) {
            return new DefaultHttpExecutionStrategy(effectiveOffloads, right);
        }
        // No extra offloads required
        return null;
    }

    private static boolean noOffloads(final HttpExecutionStrategy es) {
        return !es.isMetadataReceiveOffloaded() && !es.isDataReceiveOffloaded() && !es.isSendOffloaded();
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
         * @deprecated Use a single threaded executor with {@link #executor(Executor)} to ensure affinity.
         *
         * @return {@code this}.
         */
        @Deprecated
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
