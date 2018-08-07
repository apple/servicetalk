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

import io.servicetalk.concurrent.api.BiIntFunction;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.RetryStrategies;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.redis.api.RedisClient;
import io.servicetalk.redis.api.RedisData;
import io.servicetalk.redis.api.RedisRequest;

import static java.util.Objects.requireNonNull;

/**
 * A {@link RedisClient} wrapper that applies a retry strategy to its {@link RetryingRedisClient#request(RedisRequest)}
 * {@link RetryingRedisClient#request(RedisRequest, Class)} and {@link RetryingRedisClient#reserveConnection(RedisRequest)} methods.
 *
 * @see RedisRequestAwareRetryStrategy
 * @see RetryStrategies
 */
public final class RetryingRedisClient extends DelegatingRedisClient {

    private final RedisRequestAwareRetryStrategy retryStrategy;

    /**
     * Creates a new instance.
     *
     * @param delegate      the {@link RedisClient} to delegate to.
     * @param retryStrategy the {@link RedisRequest}-unaware retry strategy to use.
     */
    public RetryingRedisClient(final RedisClient delegate, final BiIntFunction<Throwable, Completable> retryStrategy) {
        this(delegate, asRedisRequestAwareRetryStrategy(retryStrategy));
    }

    /**
     * Creates a new instance.
     *
     * @param delegate      the {@link RedisClient} to delegate to.
     * @param retryStrategy the {@link RedisRequest}-aware retry strategy to use.
     */
    public RetryingRedisClient(final RedisClient delegate, final RedisRequestAwareRetryStrategy retryStrategy) {
        super(delegate);
        this.retryStrategy = requireNonNull(retryStrategy);
    }

    private static RedisRequestAwareRetryStrategy asRedisRequestAwareRetryStrategy(final BiIntFunction<Throwable, Completable> retryStrategy) {
        requireNonNull(retryStrategy);
        return (i, t, r) -> retryStrategy.apply(i, t);
    }

    @Override
    public Publisher<RedisData> request(final RedisRequest request) {
        return super.request(request).retryWhen((i, t) -> retryStrategy.apply(i, t, request));
    }

    @Override
    public <R> Single<R> request(final RedisRequest request, final Class<R> responseType) {
        return super.request(request, responseType).retryWhen((i, t) -> retryStrategy.apply(i, t, request));
    }

    @Override
    public Single<? extends ReservedRedisConnection> reserveConnection(final RedisRequest request) {
        return super.reserveConnection(request).retryWhen((i, t) -> retryStrategy.apply(i, t, request));
    }
}
