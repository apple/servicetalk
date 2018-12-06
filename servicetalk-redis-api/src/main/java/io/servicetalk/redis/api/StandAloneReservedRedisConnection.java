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

import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.transport.api.ConnectionContext;
import io.servicetalk.transport.api.ExecutionContext;

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import static io.servicetalk.concurrent.Cancellable.IGNORE_CANCEL;

final class StandAloneReservedRedisConnection extends RedisClient.ReservedRedisConnection {
    private static final AtomicIntegerFieldUpdater<StandAloneReservedRedisConnection> releasedUpdater =
            AtomicIntegerFieldUpdater.newUpdater(StandAloneReservedRedisConnection.class, "released");
    private final RedisConnection delegate;
    @SuppressWarnings("unused")
    private volatile int released;

    StandAloneReservedRedisConnection(RedisConnection delegate) {
        this.delegate = delegate;
    }

    @Override
    public Completable releaseAsync() {
        return new Completable() {
            @Override
            protected void handleSubscribe(Subscriber subscriber) {
                subscriber.onSubscribe(IGNORE_CANCEL);
                if (releasedUpdater.compareAndSet(StandAloneReservedRedisConnection.this, 0, 1)) {
                    subscriber.onComplete();
                } else {
                    subscriber.onError(new IllegalStateException("Connection " + StandAloneReservedRedisConnection.this + " already released."));
                }
            }
        };
    }

    @Override
    public ConnectionContext connectionContext() {
        return delegate.connectionContext();
    }

    @Override
    public <T> Publisher<T> settingStream(SettingKey<T> settingKey) {
        return delegate.settingStream(settingKey);
    }

    @Override
    public Publisher<RedisData> request(RedisExecutionStrategy strategy, RedisRequest request) {
        return delegate.request(strategy, request);
    }

    @Override
    public ExecutionContext executionContext() {
        return delegate.executionContext();
    }

    @Override
    public Completable onClose() {
        return delegate.onClose();
    }

    @Override
    public Completable closeAsync() {
        return delegate.closeAsync().doBeforeSubscribe(cancellable -> released = 1);
    }

    @Override
    public Completable closeAsyncGracefully() {
        return delegate.closeAsyncGracefully().doBeforeSubscribe(cancellable -> released = 1);
    }

    @Override
    public String toString() {
        return StandAloneReservedRedisConnection.class.getSimpleName() + "(" + delegate + ")";
    }
}
