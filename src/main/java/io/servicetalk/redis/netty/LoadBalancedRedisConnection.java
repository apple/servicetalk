/**
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

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.internal.FlowControlUtil;
import io.servicetalk.concurrent.internal.LatestValueSubscriber;
import io.servicetalk.redis.api.RedisClient;
import io.servicetalk.redis.api.RedisConnection;
import io.servicetalk.redis.api.RedisData;
import io.servicetalk.redis.api.RedisRequest;
import io.servicetalk.transport.api.ConnectionContext;

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import static io.servicetalk.concurrent.Cancellable.IGNORE_CANCEL;
import static io.servicetalk.redis.api.RedisConnection.SettingKey.MAX_CONCURRENCY;
import static io.servicetalk.redis.netty.RedisUtils.isSubscribeModeCommand;
import static java.util.concurrent.atomic.AtomicIntegerFieldUpdater.newUpdater;

final class LoadBalancedRedisConnection extends RedisClient.ReservedRedisConnection {

    private static final AtomicIntegerFieldUpdater<LoadBalancedRedisConnection> pendingRequestsUpdater =
            newUpdater(LoadBalancedRedisConnection.class, "pendingRequests");

    private static final int STATE_QUIT = -2;
    private static final int STATE_RESERVED = -1;
    private static final int STATE_IDLE = 0;

    /*
     * Following semantics:
     * STATE_RESERVED if this is reserved.
     * STATE_QUIT if quit command issued.
     * STATE_IDLE if connection is not used.
     * pending request count if none of the above states.
     */
    @SuppressWarnings("unused")
    private volatile int pendingRequests;

    private volatile boolean subscribedConnection;

    private final RedisConnection delegate;
    private final LatestValueSubscriber<Integer> maxConcurrencyHolder;

    LoadBalancedRedisConnection(RedisConnection delegate) {
        this.delegate = delegate;
        maxConcurrencyHolder = new LatestValueSubscriber<>();
        delegate.getSettingStream(MAX_CONCURRENCY).subscribe(maxConcurrencyHolder);
        delegate.onClose().subscribe(new Completable.Subscriber() {
            @Override
            public void onSubscribe(Cancellable cancellable) {
                // No op
            }

            @Override
            public void onComplete() {
                pendingRequests = STATE_QUIT;
            }

            @Override
            public void onError(Throwable ignored) {
                pendingRequests = STATE_QUIT;
            }
        });
    }

    boolean tryReserve() {
        return pendingRequestsUpdater.compareAndSet(this, STATE_IDLE, STATE_RESERVED);
    }

    @Override
    public <T> Publisher<T> getSettingStream(SettingKey<T> settingKey) {
        return delegate.getSettingStream(settingKey);
    }

    boolean reserveForRequest() {
        final int lastSeenValue = maxConcurrencyHolder.getLastSeenValue(0);
        for (;;) {
            final int currentPending = pendingRequests;
            if (currentPending < STATE_IDLE || currentPending >= lastSeenValue) {
                return false;
            }
            if (pendingRequestsUpdater.compareAndSet(this, currentPending, currentPending + 1)) {
                return true;
            }
        }
    }

    @Override
    public Publisher<RedisData> request(RedisRequest request) {
        return delegate.request(request)
                .doBeforeSubscribe(subscription -> {
                    if (isSubscribeModeCommand(request.getCommand()) && !subscribedConnection) {
                        subscribedConnection = true; // Don't release if seen a subscribe request to disallow reuse post release.
                    }
                })
                .doBeforeFinally(() -> pendingRequestsUpdater.accumulateAndGet(this, -1, FlowControlUtil::addWithOverflowProtectionIfPositive));
    }

    @Override
    public ConnectionContext getConnectionContext() {
        return delegate.getConnectionContext();
    }

    @Override
    public Completable onClose() {
        return delegate.onClose();
    }

    @Override
    public Completable closeAsync() {
        return delegate.closeAsync().doBeforeSubscribe(cancellable -> pendingRequests = STATE_QUIT);
    }

    @Override
    public Completable release() {
        return new Completable() {
            @Override
            protected void handleSubscribe(Subscriber subscriber) {
                subscriber.onSubscribe(IGNORE_CANCEL);
                // Ownership is maintained by the caller.
                // We do not reuse connections which are used for subscribe mode as after all subscriptions are unsubscribed,
                // we close the connection.
                if (pendingRequestsUpdater.compareAndSet(LoadBalancedRedisConnection.this, STATE_RESERVED, subscribedConnection ? STATE_QUIT : STATE_IDLE)) {
                    subscriber.onComplete();
                } else {
                    subscriber.onError(new IllegalStateException("Connection " + LoadBalancedRedisConnection.this + (pendingRequests == STATE_QUIT ? " is closed." : " was not reserved.")));
                }
            }
        };
    }

    @Override
    public String toString() {
        return LoadBalancedRedisConnection.class.getSimpleName() + "(" + delegate + ")";
    }
}
