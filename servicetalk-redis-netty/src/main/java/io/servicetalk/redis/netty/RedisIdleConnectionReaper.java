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

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.internal.SequentialCancellable;
import io.servicetalk.redis.api.RedisConnection;
import io.servicetalk.redis.api.RedisData;
import io.servicetalk.redis.api.RedisExecutionStrategy;
import io.servicetalk.redis.api.RedisRequest;
import io.servicetalk.transport.api.ConnectionContext;
import io.servicetalk.transport.api.ExecutionContext;
import io.servicetalk.transport.api.IoExecutor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import static io.servicetalk.transport.netty.internal.NettyIoExecutors.toNettyIoExecutor;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

/**
 * A {@link UnaryOperator} that decorates {@link RedisConnection} instances in order to close them if they become idle.
 * <p>
 * A {@link RedisConnection} is considered idle only if:
 * <ul>
 * <li>It has no active request. Consequently, subscribed connections are not considered idle because {@code SUBSCRIBE} requests don't complete.</li>
 * <li>No request has started or finished within the provided timeout.</li>
 * </ul>
 * Note that the timeouts are enforced on a best effort basis, i.e. if the {@link IoExecutor} used to schedule checks is overwhelmed,
 * the interval between checks may increase.
 */
final class RedisIdleConnectionReaper implements UnaryOperator<RedisConnection> {
    private static final Logger LOGGER = LoggerFactory.getLogger(RedisIdleConnectionReaper.class);
    private static final Duration MINIMUM_IDLE_TIMEOUT = Duration.ofSeconds(1);

    private final long idleTimeoutNanos;

    /**
     * Creates a new instance.
     *
     * @param idleTimeout the {@link Duration} after which a connection is considered idle.
     */
    RedisIdleConnectionReaper(final Duration idleTimeout) {
        if (idleTimeout.compareTo(MINIMUM_IDLE_TIMEOUT) < 0) {
            throw new IllegalArgumentException("idleTimeout: " + idleTimeout + " (expected >=" + MINIMUM_IDLE_TIMEOUT + ')');
        }
        idleTimeoutNanos = idleTimeout.toNanos();

        LOGGER.debug("Initialized with idle timeout: {}", idleTimeout);
    }

    @Override
    public RedisConnection apply(final RedisConnection redisConnection) {
        Completable timer = toNettyIoExecutor(
                redisConnection.connectionContext().executionContext().ioExecutor())
                        .asExecutor().timer(idleTimeoutNanos, NANOSECONDS);
        return new IdleAwareRedisConnection(redisConnection, timer::subscribe);
    }

    private static final class IdleAwareRedisConnection extends RedisConnection {
        private static final AtomicIntegerFieldUpdater<IdleAwareRedisConnection> activeRequestsCountUpdater =
                AtomicIntegerFieldUpdater.newUpdater(IdleAwareRedisConnection.class, "activeRequestsCount");

        private static final AtomicIntegerFieldUpdater<IdleAwareRedisConnection> inUseUpdater =
                AtomicIntegerFieldUpdater.newUpdater(IdleAwareRedisConnection.class, "inUse");

        private final RedisConnection delegate;
        private final Consumer<Completable.Subscriber> idleCheckScheduler;
        private final SequentialCancellable timerCancellable;
        private final Completable.Subscriber timerCompletableSubscriber;

        @SuppressWarnings("unused")
        private volatile int activeRequestsCount;
        @SuppressWarnings("unused")
        private volatile int inUse;

        IdleAwareRedisConnection(final RedisConnection delegate,
                                 final Consumer<Completable.Subscriber> idleCheckScheduler) {

            this.delegate = requireNonNull(delegate);
            this.idleCheckScheduler = requireNonNull(idleCheckScheduler);

            timerCancellable = new SequentialCancellable();
            timerCompletableSubscriber = new Completable.Subscriber() {
                @Override
                public void onSubscribe(final Cancellable cancellable) {
                    timerCancellable.setNextCancellable(cancellable);
                }

                @Override
                public void onComplete() {
                    onIdleCheckTimerComplete();
                }

                @Override
                public void onError(final Throwable t) {
                    if (!(t instanceof CancellationException)) {
                        LOGGER.error("Unexpected timer error: stopping idle reaper", t);
                    }
                }
            };

            delegate.onClose()
                    .doBeforeComplete(timerCancellable::cancel)
                    .subscribe();

            scheduleNextIdleCheck();
        }

        @Override
        public <T> Publisher<T> settingStream(SettingKey<T> settingKey) {
            return delegate.settingStream(settingKey);
        }

        @Override
        public ConnectionContext connectionContext() {
            return delegate.connectionContext();
        }

        @Override
        public Completable onClose() {
            return delegate.onClose();
        }

        @Override
        public Completable closeAsync() {
            return delegate.closeAsync();
        }

        @Override
        public Completable closeAsyncGracefully() {
            return delegate.closeAsyncGracefully();
        }

        @Override
        public Publisher<RedisData> request(final RedisExecutionStrategy strategy, final RedisRequest request) {
            return delegate.request(strategy, request)
                    .doBeforeSubscribe(__ -> onRequestStarted())
                    .doBeforeFinally(this::onRequestFinished);
        }

        @Override
        public ExecutionContext executionContext() {
            return delegate.executionContext();
        }

        @Override
        public <R> Single<R> request(final RedisExecutionStrategy strategy, final RedisRequest request,
                                     final Class<R> responseType) {
            return delegate.request(strategy, request, responseType)
                    .doBeforeSubscribe(__ -> onRequestStarted())
                    .doBeforeFinally(this::onRequestFinished);
        }

        private void onRequestStarted() {
            activeRequestsCountUpdater.incrementAndGet(this);
            inUse = 1;
        }

        private void onRequestFinished() {
            activeRequestsCountUpdater.decrementAndGet(this);
            inUse = 1;
        }

        private void onIdleCheckTimerComplete() {
            if (activeRequestsCount > 0 || inUseUpdater.getAndSet(this, 0) == 1) {
                scheduleNextIdleCheck();
            } else {
                LOGGER.info("Closing idle connection: {}", delegate);
                delegate.closeAsync().subscribe();
            }
        }

        private void scheduleNextIdleCheck() {
            idleCheckScheduler.accept(timerCompletableSubscriber);
        }

        @Override
        public String toString() {
            return IdleAwareRedisConnection.class.getSimpleName() + "(" + delegate + ")";
        }
    }
}
