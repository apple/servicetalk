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

import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Single;

/**
 * Provides a means to issue requests against redis service. The implementation is free to maintain a collection of
 * {@link RedisConnection} instances and distribute calls to {@link #request(RedisRequest)} amongst this collection.
 */
public abstract class RedisClient extends RedisRequester {

    /**
     * Reserve a {@link RedisConnection} for the for handling the provided {@link RedisRequest}
     * but <b>does not execute it</b>!
     *
     * @param request Allows the underlying layers to know what {@link RedisConnection}s are valid to reserve. For example
     *                this may provide some insight into shard or other info.
     * @return a {@link ReservedRedisConnection}.
     */
    public abstract Single<? extends ReservedRedisConnection> reserveConnection(RedisRequest request);

    public abstract static class ReservedRedisConnection extends RedisConnection {
        /**
         * Releases this reserved {@link RedisConnection} to be used for subsequent requests.
         * This method must be idempotent, i.e. calling multiple times must not have side-effects.
         *
         * @return the {@code Completable} that is notified on releaseAsync.
         */
        public abstract Completable releaseAsync();
    }
}
