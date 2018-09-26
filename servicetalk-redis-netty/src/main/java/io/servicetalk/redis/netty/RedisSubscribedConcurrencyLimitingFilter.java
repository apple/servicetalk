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

import io.servicetalk.client.internal.RequestConcurrencyController;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.redis.api.RedisConnection;
import io.servicetalk.redis.api.RedisData;
import io.servicetalk.redis.api.RedisRequest;
import io.servicetalk.transport.api.ConnectionContext;
import io.servicetalk.transport.api.ExecutionContext;

import static java.util.Objects.requireNonNull;

final class RedisSubscribedConcurrencyLimitingFilter extends RedisConnection {
    private final RedisConnection next;
    private final RequestConcurrencyController limiter;

    RedisSubscribedConcurrencyLimitingFilter(RedisConnection next) {
        this.next = requireNonNull(next);
        limiter = new RedisSubscribedReservableRequestConcurrencyController();
    }

    @Override
    public Completable closeAsync() {
        return next.closeAsync();
    }

    @Override
    public Completable closeAsyncGracefully() {
        return next.closeAsyncGracefully();
    }

    @Override
    public Completable onClose() {
        return next.onClose();
    }

    @Override
    public Publisher<RedisData> request(RedisRequest request) {
        return limiter.tryRequest() ? next.request(request) :
                Publisher.error(new IllegalStateException("Connection in invalid state for requests: " + this));
    }

    @Override
    public ExecutionContext executionContext() {
        return next.executionContext();
    }

    @Override
    public ConnectionContext connectionContext() {
        return next.connectionContext();
    }

    @Override
    public <T> Publisher<T> settingStream(RedisConnection.SettingKey<T> settingKey) {
        return next.settingStream(settingKey);
    }

    @Override
    public String toString() {
        return RedisSubscribedConcurrencyLimitingFilter.class.getSimpleName() + "(" + next + ")";
    }
}
