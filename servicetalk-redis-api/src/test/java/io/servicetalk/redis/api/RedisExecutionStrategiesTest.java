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

import org.junit.Test;

import static io.servicetalk.redis.api.DefaultRedisExecutionStrategy.OFFLOAD_RECEIVE;
import static io.servicetalk.redis.api.DefaultRedisExecutionStrategy.OFFLOAD_SEND;
import static io.servicetalk.redis.api.RedisExecutionStrategies.defaultStrategy;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

public class RedisExecutionStrategiesTest {

    @Test
    public void defaultShouldOffloadAll() {
        DefaultRedisExecutionStrategy strategy = (DefaultRedisExecutionStrategy) defaultStrategy();
        assertThat("send not offloaded by default.", strategy.offloaded(OFFLOAD_SEND), is(true));
        assertThat("receive not offloaded by default.", strategy.offloaded(OFFLOAD_RECEIVE), is(true));
    }
}
