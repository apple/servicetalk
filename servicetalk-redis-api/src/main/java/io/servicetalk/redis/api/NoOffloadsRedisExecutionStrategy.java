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

import static io.servicetalk.concurrent.api.Executors.immediate;

final class NoOffloadsRedisExecutionStrategy implements RedisExecutionStrategy {

    static final RedisExecutionStrategy NO_OFFLOADS = new NoOffloadsRedisExecutionStrategy();

    private NoOffloadsRedisExecutionStrategy() {
        // Singleton
    }

    @Override
    public Publisher<RedisData> invokeClient(final Executor fallback, final RedisRequest request,
                                             final Function<RedisRequest, Publisher<RedisData>> client) {
        return client.apply(request.transformContent(p -> p.subscribeOnOverride(immediate())))
                .publishOnOverride(immediate());
    }

    @Override
    public <T> Single<T> offloadSend(final Executor fallback, final Single<T> original) {
        return original.subscribeOnOverride(immediate());
    }

    @Override
    public <T> Single<T> offloadReceive(final Executor fallback, final Single<T> original) {
        return original.publishOnOverride(immediate());
    }

    @Override
    public <T> Publisher<T> offloadSend(final Executor fallback, final Publisher<T> original) {
        return original.subscribeOnOverride(immediate());
    }

    @Override
    public <T> Publisher<T> offloadReceive(final Executor fallback, final Publisher<T> original) {
        return original.publishOnOverride(immediate());
    }

    @Override
    public Executor executor() {
        // We do not return immediate() here as it is an implementation detail to use immediate() and not necessarily
        // required to be used otherwise. Returning immediate() from here may lead to a user inadvertently using that
        // Executor for any custom tasks.
        return null;
    }
}
