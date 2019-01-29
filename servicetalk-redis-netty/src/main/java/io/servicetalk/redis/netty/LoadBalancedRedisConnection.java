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
package io.servicetalk.redis.netty;

import io.servicetalk.client.internal.ReservableRequestConcurrencyController;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.redis.api.RedisClient.ReservedRedisConnection;
import io.servicetalk.redis.api.RedisConnection;
import io.servicetalk.redis.api.RedisData;
import io.servicetalk.redis.api.RedisExecutionStrategy;
import io.servicetalk.redis.api.RedisRequest;
import io.servicetalk.transport.api.ConnectionContext;
import io.servicetalk.transport.api.ExecutionContext;

import static java.util.Objects.requireNonNull;

final class LoadBalancedRedisConnection extends ReservedRedisConnection
        implements ReservableRequestConcurrencyController {
    private final RedisConnection delegate;
    private final ReservableRequestConcurrencyController limiter;

    LoadBalancedRedisConnection(RedisConnection delegate,
                                ReservableRequestConcurrencyController limiter) {
        this.delegate = requireNonNull(delegate);
        this.limiter = requireNonNull(limiter);
    }

    @Override
    public <T> Publisher<T> settingStream(SettingKey<T> settingKey) {
        return delegate.settingStream(settingKey);
    }

    @Override
    public Publisher<RedisData> request(RedisExecutionStrategy strategy, RedisRequest request) {
        return delegate.request(strategy, request);
    }

    @Override
    public ExecutionContext executionContext() {
        return delegate.executionContext();
    }

    @Override
    public ConnectionContext connectionContext() {
        return delegate.connectionContext();
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
    public boolean tryReserve() {
        return limiter.tryReserve();
    }

    @Override
    public Result tryRequest() {
        return limiter.tryRequest();
    }

    @Override
    public void requestFinished() {
        limiter.requestFinished();
    }

    @Override
    public Completable releaseAsync() {
        return limiter.releaseAsync();
    }

    @Override
    public String toString() {
        return getClass().getName() + '(' + delegate + ')';
    }
}
