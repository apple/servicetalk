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
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.redis.api.RedisClient;
import io.servicetalk.redis.api.RedisData;
import io.servicetalk.redis.api.RedisRequest;
import io.servicetalk.transport.api.ExecutionContext;

import static java.util.Objects.requireNonNull;

/**
 * Wraps another {@link RedisClient} and delegate all methods invocations to it.
 */
public abstract class DelegatingRedisClient extends RedisClient {

    private final RedisClient wrapped;

    /**
     * Creates a new instance and delegate all method invocations.
     *
     * @param wrapped the {@link RedisClient} to delegate to.
     */
    protected DelegatingRedisClient(final RedisClient wrapped) {
        this.wrapped = requireNonNull(wrapped);
    }

    @Override
    public Single<? extends ReservedRedisConnection> reserveConnection(final RedisRequest request) {
        return wrapped.reserveConnection(request);
    }

    @Override
    public ExecutionContext getExecutionContext() {
        return wrapped.getExecutionContext();
    }

    @Override
    public Publisher<RedisData> request(final RedisRequest request) {
        return wrapped.request(request);
    }

    @Override
    public <R> Single<R> request(final RedisRequest request, final Class<R> responseType) {
        return wrapped.request(request, responseType);
    }

    @Override
    public Completable onClose() {
        return wrapped.onClose();
    }

    @Override
    public Completable closeAsync() {
        return wrapped.closeAsync();
    }
}
