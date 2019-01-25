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
package io.servicetalk.redis.api;

import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.redis.api.RedisClient.ReservedRedisConnection;
import io.servicetalk.transport.api.ConnectionContext;
import io.servicetalk.transport.api.ExecutionContext;

import static java.util.Objects.requireNonNull;

/**
 * A {@link ReservedRedisConnection} that delegates all methods to a different {@link RedisConnection}.
 */
public abstract class ReservedRedisConnectionFilter extends ReservedRedisConnection {

    private final ReservedRedisConnection delegate;
    private final RedisExecutionStrategy defaultStrategy;

    /**
     * Create a new instance.
     *
     * @param delegate The {@link ReservedRedisConnection} to delegate all calls to
     */
    protected ReservedRedisConnectionFilter(final ReservedRedisConnection delegate) {
        this.delegate = requireNonNull(delegate);
        this.defaultStrategy = executionStrategy();
    }

    /**
     * Create a new instance.
     *
     * @param delegate The {@link ReservedRedisConnection} to delegate all calls to
     * @param defaultStrategy Default {@link RedisExecutionStrategy} to use.
     */
    protected ReservedRedisConnectionFilter(final ReservedRedisConnection delegate,
                                            final RedisExecutionStrategy defaultStrategy) {
        this.delegate = requireNonNull(delegate);
        this.defaultStrategy = requireNonNull(defaultStrategy);
    }

    /**
     * Get the {@link ReservedRedisConnection} that this class delegates to.
     *
     * @return the {@link ReservedRedisConnection} that this class delegates to
     */
    protected final ReservedRedisConnection delegate() {
        return delegate;
    }

    @Override
    public Completable releaseAsync() {
        return delegate.releaseAsync();
    }

    @Override
    public ConnectionContext connectionContext() {
        return delegate.connectionContext();
    }

    @Override
    public <T> Publisher<T> settingStream(final SettingKey<T> settingKey) {
        return delegate.settingStream(settingKey);
    }

    @Override
    public final Publisher<RedisData> request(final RedisRequest request) {
        return request(defaultStrategy, request);
    }

    @Override
    public final <R> Single<R> request(final RedisRequest request, final Class<R> responseType) {
        return request(defaultStrategy, request, responseType);
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
    public Completable onClose() {
        return delegate.onClose();
    }

    @Override
    public Completable closeAsync() {
        return delegate.closeAsync();
    }

    @Override
    public Completable closeAsyncGracefully() {
        return delegate.closeAsyncGracefully();
    }

    @Override
    public String toString() {
        return getClass().getName() + '(' + delegate + ')';
    }
}
