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
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.transport.api.ExecutionContext;

import static java.util.Objects.requireNonNull;

/**
 * A {@link RedisClient} that delegates all calls to another {@link RedisClient}.
 */
public class RedisClientFilter extends RedisClient {

    private final RedisClient delegate;
    private final RedisExecutionStrategy defaultStrategy;

    /**
     * New instance.
     *
     * @param delegate {@link RedisClient} to delegate.
     */
    public RedisClientFilter(final RedisClient delegate) {
        this.delegate = requireNonNull(delegate);
        defaultStrategy = executionStrategy();
    }

    /**
     * New instance.
     *
     * @param delegate {@link RedisClient} to delegate.
     * @param defaultStrategy Default {@link RedisExecutionStrategy} to use.
     */
    public RedisClientFilter(final RedisClient delegate, final RedisExecutionStrategy defaultStrategy) {
        this.delegate = requireNonNull(delegate);
        this.defaultStrategy = requireNonNull(defaultStrategy);
    }

    @Override
    public final Single<? extends ReservedRedisConnection> reserveConnection(
            final RedisProtocolSupport.Command command) {
        return reserveConnection(defaultStrategy, command);
    }

    @Override
    public Single<? extends ReservedRedisConnection> reserveConnection(final RedisExecutionStrategy strategy,
                                                                       final RedisProtocolSupport.Command command) {
        return delegate.reserveConnection(strategy, command);
    }

    @Override
    public final <R> Single<R> request(final RedisRequest request, final Class<R> responseType) {
        return request(defaultStrategy, request, responseType);
    }

    @Override
    public final Publisher<RedisData> request(final RedisRequest request) {
        return request(defaultStrategy, request);
    }

    @Override
    public Publisher<RedisData> request(final RedisExecutionStrategy strategy, final RedisRequest request) {
        return delegate.request(strategy, request);
    }

    @Override
    public ExecutionContext executionContext() {
        return delegate.executionContext();
    }

    @Override
    public Completable closeAsyncGracefully() {
        return delegate.closeAsyncGracefully();
    }

    @Override
    public Completable onClose() {
        return delegate.onClose();
    }

    @Override
    public Completable closeAsync() {
        return delegate.closeAsync();
    }

    /**
     * The {@link RedisClient} to which all calls are delegated to.
     *
     * @return {@link RedisClient} to which all calls are delegated.
     */
    public final RedisClient delegate() {
        return delegate;
    }
}
