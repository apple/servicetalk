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
import io.servicetalk.client.api.MaxRequestLimitExceededException;
import io.servicetalk.client.internal.RequestConcurrencyController;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.redis.api.RedisConnection;
import io.servicetalk.redis.api.RedisData;
import io.servicetalk.redis.api.RedisRequest;
import io.servicetalk.transport.api.ConnectionContext;
import io.servicetalk.transport.api.ExecutionContext;

import org.reactivestreams.Subscriber;

import static io.servicetalk.client.internal.RequestConcurrencyControllers.newController;
import static io.servicetalk.client.internal.RequestConcurrencyControllers.newSingleController;
import static io.servicetalk.concurrent.internal.EmptySubscription.EMPTY_SUBSCRIPTION;
import static io.servicetalk.redis.api.RedisConnection.SettingKey.MAX_CONCURRENCY;
import static java.util.Objects.requireNonNull;

/**
 * A {@link RedisConnection} that enforces max allowed pending requests.
 * This is only used for connections built without a {@link LoadBalancer}.
 */
final class RedisConnectionConcurrentRequestsFilter extends RedisConnection {
    private final RedisConnection next;
    private final RequestConcurrencyController limiter;

    RedisConnectionConcurrentRequestsFilter(RedisConnection next,
                                            int defaultMaxPipelinedRequests) {
        this.next = requireNonNull(next);
        limiter = defaultMaxPipelinedRequests == 1 ?
                newSingleController(next.getSettingStream(MAX_CONCURRENCY), next.onClose()) :
                newController(next.getSettingStream(MAX_CONCURRENCY), next.onClose(), defaultMaxPipelinedRequests);
    }

    @Override
    public Completable closeAsync() {
        return next.closeAsync();
    }

    @Override
    public Completable closeAsyncGracefully() {
        return next.closeAsyncGracefully();
    }

    @Override
    public Completable onClose() {
        return next.onClose();
    }

    @Override
    public Publisher<RedisData> request(RedisRequest request) {
        return new Publisher<RedisData>() {
            @Override
            protected void handleSubscribe(final Subscriber<? super RedisData> subscriber) {
                if (limiter.tryRequest()) {
                    next.request(request).doBeforeFinally(limiter::requestFinished).subscribe(subscriber);
                } else {
                    subscriber.onSubscribe(EMPTY_SUBSCRIPTION);
                    subscriber.onError(new MaxRequestLimitExceededException("Max concurrent requests saturated for: " +
                            RedisConnectionConcurrentRequestsFilter.this));
                }
            }
        };
    }

    @Override
    public ExecutionContext getExecutionContext() {
        return next.getExecutionContext();
    }

    @Override
    public ConnectionContext getConnectionContext() {
        return next.getConnectionContext();
    }

    @Override
    public <T> Publisher<T> getSettingStream(SettingKey<T> settingKey) {
        return next.getSettingStream(settingKey);
    }

    @Override
    public String toString() {
        return RedisConnectionConcurrentRequestsFilter.class.getSimpleName() + "(" + next + ")";
    }
}
