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

import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;

import java.util.function.Function;
import javax.annotation.Nullable;

import static java.util.Objects.requireNonNull;

/**
 * An {@link RedisExecutionStrategy} that delegates all method calls to another {@link RedisExecutionStrategy}.
 */
public class RedisExecutionStrategyAdapter implements RedisExecutionStrategy {

    private final RedisExecutionStrategy delegate;

    /**
     * Create a new instance.
     *
     * @param delegate {@link RedisExecutionStrategy} to which all method calls will be delegated.
     */
    public RedisExecutionStrategyAdapter(final RedisExecutionStrategy delegate) {
        this.delegate = requireNonNull(delegate);
    }

    @Override
    public Publisher<RedisData> invokeClient(final Executor fallback, final RedisRequest request,
                                             final Function<RedisRequest, Publisher<RedisData>> client) {
        return delegate.invokeClient(fallback, request, client);
    }

    @Override
    public <T> Single<T> offloadSend(final Executor fallback, final Single<T> original) {
        return delegate.offloadSend(fallback, original);
    }

    @Override
    public <T> Single<T> offloadReceive(final Executor fallback, final Single<T> original) {
        return delegate.offloadReceive(fallback, original);
    }

    @Override
    public <T> Publisher<T> offloadSend(final Executor fallback, final Publisher<T> original) {
        return delegate.offloadSend(fallback, original);
    }

    @Override
    public <T> Publisher<T> offloadReceive(final Executor fallback, final Publisher<T> original) {
        return delegate.offloadReceive(fallback, original);
    }

    @Override
    @Nullable
    public Executor executor() {
        return delegate.executor();
    }
}
