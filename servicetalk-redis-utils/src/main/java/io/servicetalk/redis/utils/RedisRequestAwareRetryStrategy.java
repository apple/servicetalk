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
package io.servicetalk.redis.utils;

import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.RetryStrategies;
import io.servicetalk.redis.api.RedisRequest;

/**
 * A strategy to be used for retries which given a retry count, a {@link Throwable} and a {@link RedisRequest} returns
 * a {@link Completable} that terminates successfully when the source has to be retried or terminates with error
 * if the source should not be retried for the passed arguments.
 *
 * @see RetryStrategies
 * @see RetryingRedisClient
 */
@FunctionalInterface
public interface RedisRequestAwareRetryStrategy {
    /**
     * Evaluates this retry strategy on the given arguments.
     *
     * @param i The retry count.
     * @param t The {@link Throwable}.
     * @param r The {@link RedisRequest}
     * @return a {@link Completable}.
     */
    Completable apply(int i, Throwable t, RedisRequest r);
}
