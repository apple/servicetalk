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
import io.servicetalk.transport.api.ConnectionContext;
import io.servicetalk.transport.api.ExecutionContext;

import javax.annotation.Nullable;

import static java.util.Objects.requireNonNull;

/**
 * A {@link RedisConnection} that delegates all calls to another {@link RedisConnection}.
 */
public class RedisConnectionFilter extends RedisConnection {

    private final RedisConnection delegate;
    @Nullable
    private final RedisExecutionStrategy defaultStrategy;

    /**
     * New instance.
     *
     * @param delegate {@link RedisConnection} to delegate.
     */
    public RedisConnectionFilter(final RedisConnection delegate) {
        this.delegate = requireNonNull(delegate);
        defaultStrategy = null;
    }

    /**
     * New instance.
     *
     * @param delegate {@link RedisConnection} to delegate.
     * @param defaultStrategy Default {@link RedisExecutionStrategy} to use.
     */
    public RedisConnectionFilter(final RedisConnection delegate, final RedisExecutionStrategy defaultStrategy) {
        this.delegate = requireNonNull(delegate);
        this.defaultStrategy = requireNonNull(defaultStrategy);
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
        return defaultStrategy == null ? super.request(request) : request(defaultStrategy, request);
    }

    @Override
    public final <R> Single<R> request(final RedisRequest request, final Class<R> responseType) {
        return defaultStrategy == null ? super.request(request, responseType) :
                request(defaultStrategy, request, responseType);
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

    /**
     * The {@link RedisConnection} to which all calls are delegated to.
     *
     * @return {@link RedisConnection} to which all calls are delegated.
     */
    protected final RedisConnection delegate() {
        return delegate;
    }
}
