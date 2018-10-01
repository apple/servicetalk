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
import io.servicetalk.transport.api.ExecutionStrategy;

import java.util.function.Function;

/**
 * An {@link ExecutionStrategy} for redis.
 */
public interface RedisExecutionStrategy extends ExecutionStrategy {

    /**
     * Invokes the passed {@link Function} and applies the necessary offloading of request and response for a client.
     *
     * @param fallback {@link Executor} to use as a fallback if this {@link RedisExecutionStrategy} does not define an
     * {@link Executor}.
     * @param request {@link RedisRequest} for which the offloading is to be applied.
     * @param client A {@link Function} that given a {@link RedisRequest} returns a {@link Publisher} of
     * {@link RedisData}.
     * @return {@link Publisher} which is offloaded as required.
     */
    Publisher<RedisData> offloadClient(Executor fallback, RedisRequest request,
                                       Function<RedisRequest, Publisher<RedisData>> client);
}
