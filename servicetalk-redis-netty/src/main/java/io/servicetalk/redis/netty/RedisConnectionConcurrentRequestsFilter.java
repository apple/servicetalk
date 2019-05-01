/*
 * Copyright © 2018-2019 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.client.api.ConnectionClosedException;
import io.servicetalk.client.api.LoadBalancer;
import io.servicetalk.client.api.internal.IgnoreConsumedEvent;
import io.servicetalk.client.api.internal.MaxRequestLimitExceededRejectedSubscribeException;
import io.servicetalk.client.api.internal.RequestConcurrencyController;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.internal.SubscribablePublisher;
import io.servicetalk.concurrent.internal.LatestValueSubscriber;
import io.servicetalk.concurrent.internal.ThrowableUtil;
import io.servicetalk.redis.api.RedisConnection;
import io.servicetalk.redis.api.RedisConnectionFilter;
import io.servicetalk.redis.api.RedisData;
import io.servicetalk.redis.api.RedisExecutionStrategy;
import io.servicetalk.redis.api.RedisRequest;
import io.servicetalk.transport.netty.internal.NettyConnectionContext;

import static io.servicetalk.client.api.internal.RequestConcurrencyControllers.newController;
import static io.servicetalk.client.api.internal.RequestConcurrencyControllers.newSingleController;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.EmptySubscription.EMPTY_SUBSCRIPTION;
import static io.servicetalk.redis.api.RedisConnection.SettingKey.MAX_CONCURRENCY;

/**
 * A {@link RedisConnection} that enforces max allowed pending requests.
 * This is only used for connections built without a {@link LoadBalancer}.
 */
final class RedisConnectionConcurrentRequestsFilter extends RedisConnectionFilter {
    private static final Throwable NONE = ThrowableUtil.unknownStackTrace(new Throwable(),
            RedisConnectionConcurrentRequestsFilter.class, "request()");

    private final RequestConcurrencyController limiter;
    private final LatestValueSubscriber<Throwable> transportError = new LatestValueSubscriber<>();

    RedisConnectionConcurrentRequestsFilter(RedisConnection next,
                                            int defaultMaxPipelinedRequests) {
        super(next);
        if (next.connectionContext() instanceof NettyConnectionContext) {
            toSource(((NettyConnectionContext) next.connectionContext()).transportError()
                    .toPublisher()).subscribe(transportError);
        }

        limiter = defaultMaxPipelinedRequests == 1 ?
                newSingleController(next.settingStream(MAX_CONCURRENCY).map(IgnoreConsumedEvent::new), next.onClose()) :
                newController(next.settingStream(MAX_CONCURRENCY).map(IgnoreConsumedEvent::new), next.onClose(),
                        defaultMaxPipelinedRequests);
    }

    @Override
    public Publisher<RedisData> request(RedisExecutionStrategy strategy, RedisRequest request) {
        return new SubscribablePublisher<RedisData>() {
            @Override
            protected void handleSubscribe(final Subscriber<? super RedisData> subscriber) {
                RequestConcurrencyController.Result result = limiter.tryRequest();
                Throwable reportedError;
                switch (result) {
                    case Accepted:
                        toSource(delegate().request(strategy, request).beforeFinally(limiter::requestFinished))
                                .subscribe(subscriber);
                        return;
                    case RejectedTemporary:
                        reportedError = new MaxRequestLimitExceededRejectedSubscribeException(
                                "Max concurrent requests saturated for: " +
                                        RedisConnectionConcurrentRequestsFilter.this);
                        break;
                    case RejectedPermanently:
                        reportedError = RedisConnectionConcurrentRequestsFilter.this.transportError.lastSeenValue(NONE);
                        if (reportedError == NONE) {
                            reportedError = new ConnectionClosedException(
                                    "Connection Closed: " + RedisConnectionConcurrentRequestsFilter.this);
                        } else {
                            reportedError = new ConnectionClosedException(
                                    "Connection Closed: " + RedisConnectionConcurrentRequestsFilter.this,
                                    reportedError);
                        }
                        break;
                    default:
                        reportedError = new AssertionError("Unexpected result: " + result +
                                " determining concurrency limit for the connection " +
                                RedisConnectionConcurrentRequestsFilter.this);
                        break;
                }
                subscriber.onSubscribe(EMPTY_SUBSCRIPTION);
                subscriber.onError(reportedError);
            }
        };
    }
}
