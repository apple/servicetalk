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

import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;

import java.util.function.Function;
import javax.annotation.Nullable;

/**
 * Default implementation for {@link RedisExecutionStrategy}.
 */
final class DefaultRedisExecutionStrategy implements RedisExecutionStrategy {

    static final byte OFFLOAD_RECEIVE = 2;
    static final byte OFFLOAD_SEND = 4;
    @Nullable
    private final Executor executor;
    private final byte offloads;

    DefaultRedisExecutionStrategy(@Nullable final Executor executor, final byte offloads) {
        this.executor = executor;
        this.offloads = offloads;
    }

    @Nullable
    @Override
    public Executor executor() {
        return executor;
    }

    @Override
    public <T> Single<T> offloadSend(final Executor fallback, final Single<T> original) {
        return offloaded(OFFLOAD_SEND) ? original.subscribeOn(executor(fallback)) : original;
    }

    @Override
    public <T> Single<T> offloadReceive(final Executor fallback, final Single<T> original) {
        return offloaded(OFFLOAD_SEND) ? original.publishOn(executor(fallback)) : original;
    }

    @Override
    public <T> Publisher<T> offloadSend(final Executor fallback, final Publisher<T> original) {
        return offloaded(OFFLOAD_SEND) ? original.subscribeOn(executor(fallback)) : original;
    }

    @Override
    public <T> Publisher<T> offloadReceive(final Executor fallback, final Publisher<T> original) {
        return offloaded(OFFLOAD_SEND) ? original.publishOn(executor(fallback)) : original;
    }

    @Override
    public Publisher<RedisData> offloadClient(final Executor fallback, RedisRequest request,
                                              final Function<RedisRequest, Publisher<RedisData>> client) {
        Executor e = this.executor == null ? fallback : this.executor;
        request = offloaded(OFFLOAD_SEND) ? request.transformContent(c -> c.subscribeOn(e)) : request;
        return offloaded(OFFLOAD_RECEIVE) ? client.apply(request).publishOn(e) : client.apply(request);
    }

    private Executor executor(final Executor fallback) {
        return executor == null ? fallback : executor;
    }

    private boolean offloaded(byte flag) {
        return (offloads & flag) == flag;
    }
}
