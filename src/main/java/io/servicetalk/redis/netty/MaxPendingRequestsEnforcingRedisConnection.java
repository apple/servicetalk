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
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.internal.LatestValueSubscriber;
import io.servicetalk.redis.api.RedisConnection;
import io.servicetalk.redis.api.RedisData;
import io.servicetalk.redis.api.RedisRequest;
import io.servicetalk.transport.api.ConnectionContext;

import org.reactivestreams.Subscriber;

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import static io.servicetalk.concurrent.internal.EmptySubscription.EMPTY_SUBSCRIPTION;
import static io.servicetalk.redis.api.RedisConnection.SettingKey.MAX_CONCURRENCY;
import static java.util.concurrent.atomic.AtomicIntegerFieldUpdater.newUpdater;

/**
 * A {@link RedisConnection} that enforces max allowed pending requests.
 * This is only used for connections built without a {@link LoadBalancer}.
 */
final class MaxPendingRequestsEnforcingRedisConnection extends RedisConnection {

    private static final AtomicIntegerFieldUpdater<MaxPendingRequestsEnforcingRedisConnection> pendingRequestsUpdater =
            newUpdater(MaxPendingRequestsEnforcingRedisConnection.class, "pendingRequests");

    @SuppressWarnings("unused")
    private volatile int pendingRequests;

    private final LatestValueSubscriber<Integer> maxConcurrencyHolder;
    private final RedisConnection connection;
    private final Executor executor;
    private final int defaultMaxPipelinedRequests;

    MaxPendingRequestsEnforcingRedisConnection(RedisConnection connection, Executor executor,
                                               int defaultMaxPipelinedRequests) {
        this.connection = connection;
        this.executor = executor;
        this.defaultMaxPipelinedRequests = defaultMaxPipelinedRequests;
        maxConcurrencyHolder = new LatestValueSubscriber<>();
        connection.getSettingStream(MAX_CONCURRENCY).subscribe(maxConcurrencyHolder);
    }

    @Override
    public Completable closeAsync() {
        return connection.closeAsync();
    }

    @Override
    public Completable onClose() {
        return connection.onClose();
    }

    @Override
    public Publisher<RedisData> request(RedisRequest request) {
        return new Publisher<RedisData>(executor) {
            @Override
            protected void handleSubscribe(Subscriber<? super RedisData> subscriber) {
                final int maxPendingRequests = maxConcurrencyHolder.getLastSeenValue(defaultMaxPipelinedRequests);
                if (!incrementPendingIfNotSaturated(maxPendingRequests)) {
                    subscriber.onSubscribe(EMPTY_SUBSCRIPTION);
                    subscriber.onError(new MaxRequestLimitExceededException(connection, maxPendingRequests));
                    return;
                }
                connection.request(request)
                        .doBeforeFinally(MaxPendingRequestsEnforcingRedisConnection.this::decrementPending)
                        .subscribe(subscriber);
            }
        };
    }

    @Override
    public ConnectionContext getConnectionContext() {
        return connection.getConnectionContext();
    }

    @Override
    public <T> Publisher<T> getSettingStream(SettingKey<T> settingKey) {
        return connection.getSettingStream(settingKey);
    }

    @Override
    public String toString() {
        return MaxPendingRequestsEnforcingRedisConnection.class.getSimpleName() + "(" + connection + ")";
    }

    private void decrementPending() {
        pendingRequestsUpdater.decrementAndGet(this);
    }

    private boolean incrementPendingIfNotSaturated(final int maxPendingRequests) {
        for (;;) {
            final int currPending = pendingRequests;
            if (currPending >= maxPendingRequests) {
                return false;
            }

            if (pendingRequestsUpdater.compareAndSet(this, currPending, currPending + 1)) {
                return true;
            }
        }
    }
}
