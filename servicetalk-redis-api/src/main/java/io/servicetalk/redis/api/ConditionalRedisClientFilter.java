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

import io.servicetalk.concurrent.api.Publisher;

import java.util.function.Predicate;

final class ConditionalRedisClientFilter extends RedisClientFilter {
    private final Predicate<RedisRequest> predicate;
    private final RedisClient predicatedClient;

    ConditionalRedisClientFilter(final Predicate<RedisRequest> predicate,
                                 final RedisClient predicatedClient,
                                 final RedisClient client) {
        super(client);
        this.predicate = predicate;
        this.predicatedClient = predicatedClient;
    }

    @Override
    public Publisher<RedisData> request(final RedisExecutionStrategy strategy, final RedisRequest req) {
        return ConditionalRedisConnectionFilter.request(predicate, predicatedClient, delegate(), strategy, req);
    }
}
