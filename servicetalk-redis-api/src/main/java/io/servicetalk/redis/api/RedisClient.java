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
import io.servicetalk.redis.api.RedisProtocolSupport.Command;

/**
 * Provides a means to issue requests against redis service. The implementation is free to maintain a collection of
 * {@link RedisConnection} instances and distribute calls to {@link #request(RedisRequest)} amongst this collection.
 */
public abstract class RedisClient extends RedisRequester {

    /**
     * Reserve a {@link RedisConnection} for exclusive use. Caller is responsible for invoking
     * {@link ReservedRedisConnection#releaseAsync()} after done using the return value.
     *
     * @param command A command representing how the returned {@link ReservedRedisConnection} will be used. It is
     * possible that this {@link RedisClient} will return different types of {@link ReservedRedisConnection} depending
     * on usage. For example {@link Command#SUBSCRIBE} and {@link Command#MONITOR} may be treated differently than other
     * request/response based commands.
     * @return a {@link ReservedRedisConnection}.
     */
    public abstract Single<? extends ReservedRedisConnection> reserveConnection(Command command);

    /**
     * A {@link RedisConnection} that is reserved for exclusive use until {@link #releaseAsync() released}.
     */
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
