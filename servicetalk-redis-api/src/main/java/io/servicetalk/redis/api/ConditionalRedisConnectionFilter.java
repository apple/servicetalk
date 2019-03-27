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

import static io.servicetalk.concurrent.api.Publisher.failed;

final class ConditionalRedisConnectionFilter extends RedisConnectionFilter {
    private final Predicate<RedisRequest> predicate;
    private final RedisConnection predicatedConnection;

    ConditionalRedisConnectionFilter(final Predicate<RedisRequest> predicate,
                                     final RedisConnection predicatedConnection,
                                     final RedisConnection connection) {
        super(connection);
        this.predicate = predicate;
        this.predicatedConnection = predicatedConnection;
    }

    @Override
    public Publisher<RedisData> request(final RedisExecutionStrategy strategy, final RedisRequest req) {
        return request(predicate, predicatedConnection, delegate(), strategy, req);
    }

    static Publisher<RedisData> request(final Predicate<RedisRequest> predicate,
                                        final RedisRequester predicatedRequester,
                                        final RedisRequester requester,
                                        final RedisExecutionStrategy strategy,
                                        final RedisRequest req) {
        boolean b;
        try {
            b = predicate.test(req);
        } catch (Throwable t) {
            return failed(new RuntimeException("Unexpected predicate failure", t));
        }

        if (b) {
            return predicatedRequester.request(strategy, req);
        }

        return requester.request(strategy, req);
    }
}
