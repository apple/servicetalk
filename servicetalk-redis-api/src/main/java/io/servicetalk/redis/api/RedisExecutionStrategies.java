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
package io.servicetalk.redis.api;

import io.servicetalk.concurrent.api.Executor;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import static io.servicetalk.redis.api.DefaultRedisExecutionStrategy.OFFLOAD_RECEIVE;
import static io.servicetalk.redis.api.DefaultRedisExecutionStrategy.OFFLOAD_SEND;
import static io.servicetalk.redis.api.NoOffloadsRedisExecutionStrategy.NO_OFFLOADS;

/**
 * A factory to create different {@link RedisExecutionStrategy}.
 */
public final class RedisExecutionStrategies {

    private RedisExecutionStrategies() {
        // No instances.
    }

    /**
     * The default {@link RedisExecutionStrategy}.
     *
     * @return Default {@link RedisExecutionStrategy}.
     */
    public static RedisExecutionStrategy defaultStrategy() {
        return Builder.DEFAULT;
    }

    /**
     * The default {@link RedisExecutionStrategy} using the passed {@link Executor}.
     *
     * @param executor {@link Executor} to use.
     * @return Default {@link RedisExecutionStrategy}.
     */
    public static RedisExecutionStrategy defaultStrategy(Executor executor) {
        return customStrategyBuilder().executor(executor).build();
    }

    /**
     * A {@link RedisExecutionStrategy} that disables all offloads.
     *
     * @return {@link RedisExecutionStrategy} that disables all offloads.
     */
    public static RedisExecutionStrategy noOffloadsStrategy() {
        return NO_OFFLOADS;
    }

    /**
     * A {@link RedisExecutionStrategy} that disables all offloads.
     *
     * @return {@link RedisExecutionStrategy} that disables all offloads.
     */
    public static Builder customStrategyBuilder() {
        return new Builder();
    }

    /**
     * A builder to build an {@link RedisExecutionStrategy}.
     */
    public static final class Builder {

        static final RedisExecutionStrategy DEFAULT = new Builder().offloadAll().build();

        @Nullable
        private Executor executor;
        private byte offloads;

        private Builder() {
        }

        /**
         * Enables offloading for receiving.
         *
         * @return {@code this}.
         */
        public Builder offloadReceive() {
            return addOffload(OFFLOAD_RECEIVE);
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
            return offloadReceive().offloadSend();
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
         * Builds a new {@link RedisExecutionStrategy}.
         *
         * @return New {@link RedisExecutionStrategy}.
         */
        public RedisExecutionStrategy build() {
            return offloads == 0 ? NO_OFFLOADS : new DefaultRedisExecutionStrategy(executor, offloads);
        }

        @Nonnull
        private Builder addOffload(byte flag) {
            offloads |= flag;
            return this;
        }
    }
}
