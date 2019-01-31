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

import io.servicetalk.client.api.LoadBalancer;
import io.servicetalk.client.internal.MaxRequestLimitExceededRejectedSubscribeException;
import io.servicetalk.client.internal.RequestConcurrencyController;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.redis.api.RedisConnection;
import io.servicetalk.redis.api.RedisConnectionFilter;
import io.servicetalk.redis.api.RedisData;
import io.servicetalk.redis.api.RedisExecutionStrategy;
import io.servicetalk.redis.api.RedisRequest;

import org.reactivestreams.Subscriber;

import static io.servicetalk.client.internal.RequestConcurrencyControllers.newController;
import static io.servicetalk.client.internal.RequestConcurrencyControllers.newSingleController;
import static io.servicetalk.concurrent.internal.EmptySubscription.EMPTY_SUBSCRIPTION;
import static io.servicetalk.redis.api.RedisConnection.SettingKey.MAX_CONCURRENCY;

/**
 * A {@link RedisConnection} that enforces max allowed pending requests.
 * This is only used for connections built without a {@link LoadBalancer}.
 */
final class RedisConnectionConcurrentRequestsFilter extends RedisConnectionFilter {
    private final RequestConcurrencyController limiter;

    RedisConnectionConcurrentRequestsFilter(RedisConnection next,
                                            int defaultMaxPipelinedRequests) {
        super(next);
        limiter = defaultMaxPipelinedRequests == 1 ?
                newSingleController(next.settingStream(MAX_CONCURRENCY), next.onClose()) :
                newController(next.settingStream(MAX_CONCURRENCY), next.onClose(), defaultMaxPipelinedRequests);
    }

    @Override
    public Publisher<RedisData> request(RedisExecutionStrategy strategy, RedisRequest request) {
        return new Publisher<RedisData>() {
            @Override
            protected void handleSubscribe(final Subscriber<? super RedisData> subscriber) {
                if (limiter.tryRequest()) {
                    delegate().request(strategy, request).doBeforeFinally(limiter::requestFinished)
                            .subscribe(subscriber);
                } else {
                    subscriber.onSubscribe(EMPTY_SUBSCRIPTION);
                    subscriber.onError(new MaxRequestLimitExceededRejectedSubscribeException(
                            "Max concurrent requests saturated for: " + RedisConnectionConcurrentRequestsFilter.this));
                }
            }
        };
    }
}
