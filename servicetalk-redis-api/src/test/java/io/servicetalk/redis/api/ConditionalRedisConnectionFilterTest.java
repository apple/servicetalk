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

import org.mockito.Mock;

public class ConditionalRedisConnectionFilterTest extends AbstractConditionalRedisRequesterFilterTest {
    @Mock
    private RedisConnection predicatedConnection;

    @Mock
    private RedisConnection connection;

    @Override
    protected RedisRequester predicatedRequester() {
        return predicatedConnection;
    }

    @Override
    protected RedisRequester requester() {
        return connection;
    }

    @Override
    protected RedisRequester filter() {
        return new ConditionalRedisConnectionFilter(TEST_REQ_PREDICATE, predicatedConnection, connection);
    }
}
