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
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.redis.api.RedisConnection;
import io.servicetalk.redis.api.RedisConnectionFilter;
import io.servicetalk.redis.api.RedisData;
import io.servicetalk.redis.api.RedisExecutionStrategy;
import io.servicetalk.redis.api.RedisRequest;

import static io.servicetalk.client.internal.RequestConcurrencyController.Result.Accepted;

final class RedisSubscribedConcurrencyLimitingFilter extends RedisConnectionFilter {
    private final RequestConcurrencyController limiter;

    RedisSubscribedConcurrencyLimitingFilter(RedisConnection next) {
        super(next);
        limiter = new RedisSubscribedReservableRequestConcurrencyController();
    }

    @Override
    public Publisher<RedisData> request(RedisExecutionStrategy strategy, RedisRequest request) {
        RequestConcurrencyController.Result result = limiter.tryRequest();
        return result == Accepted ? delegate().request(strategy, request) :
                Publisher.error(new IllegalStateException("Connection in invalid state for requests: " + result));
    }
}
