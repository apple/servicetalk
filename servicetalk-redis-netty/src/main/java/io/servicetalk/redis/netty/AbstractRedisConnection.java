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
import io.servicetalk.concurrent.api.AsyncCloseable;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.internal.SequentialCancellable;
import io.servicetalk.redis.api.RedisConnection;
import io.servicetalk.redis.api.RedisData;
import io.servicetalk.redis.api.RedisRequest;
import io.servicetalk.transport.api.ExecutionContext;

import org.slf4j.Logger;

import java.nio.channels.ClosedChannelException;
import java.time.Duration;
import java.util.concurrent.CancellationException;
import java.util.function.Supplier;

import static io.servicetalk.concurrent.Cancellable.IGNORE_CANCEL;
import static io.servicetalk.concurrent.api.Publisher.error;
import static io.servicetalk.concurrent.api.Publisher.just;
import static io.servicetalk.concurrent.api.Single.success;
import static io.servicetalk.redis.api.RedisConnection.SettingKey.MAX_CONCURRENCY;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

abstract class AbstractRedisConnection extends RedisConnection {

    private static final Duration MINIMUM_PING_PERIOD = Duration.ofSeconds(1);
    private static final AsyncCloseable NOOP_PINGER = Completable::completed;

    final int maxPendingRequests;
    private final ExecutionContext executionContext;

    private final AsyncCloseable pinger;
    private final Completable closeAsync = new Completable() {
        @Override
        protected void handleSubscribe(Subscriber subscriber) {
            pinger.closeAsync().concatWith(doClose()).subscribe(subscriber);
        }
    };
    private final Executor pingTimerProvider;
    private final Publisher<Integer> maxConcurrencySetting;

    /**
     * New instance.
     *
     * @param pingTimerProvider {@link Executor} to use to schedule pings.
     * @param onClosing {@link Completable} that terminates when the connection is starting to close.
     * @param executionContext The {@link ExecutionContext} used to build this {@link RedisConnection}.
     * @param roConfig for this connection.
     */
    protected AbstractRedisConnection(Executor pingTimerProvider, Completable onClosing,
                                      ExecutionContext executionContext,
                                      ReadOnlyRedisClientConfig roConfig) {
        this.pingTimerProvider = pingTimerProvider;
        this.executionContext = executionContext;
        Duration pingPeriod = roConfig.getPingPeriod();
        final int maxPipelinedRequests = roConfig.getMaxPipelinedRequests();
        if (pingPeriod != null) {
            if (pingPeriod.compareTo(MINIMUM_PING_PERIOD) < 0) {
                throw new IllegalArgumentException("pingPeriod: " + pingPeriod + " (expected >=" + MINIMUM_PING_PERIOD + ')');
            }
            if (maxPipelinedRequests <= 1) {
                throw new IllegalArgumentException("Invalid configuration. When ping is enabled, maxPipelinedRequests MUST be 2 or more. Ping period: "
                        + pingPeriod + ", MaxPipelinedRequests: " + maxPipelinedRequests);
            }
            maxPendingRequests = maxPipelinedRequests - 1; // reserve one request for ping.
            pinger = new Pinger(pingPeriod);
        } else {
            pinger = NOOP_PINGER;
            maxPendingRequests = maxPipelinedRequests;
        }
        maxConcurrencySetting = just(roConfig.getMaxPipelinedRequests())
                .concatWith(onClosing.concatWith(success(0)));
    }

    @Override
    public final Publisher<RedisData> request(final RedisRequest request) {
        // We are writing request content on the connection. control path will be on the EventLoop, so offload to the
        // provided Executor.
        return handleRequest(request.transformContent(c -> c.subscribeOn(executionContext().executor())))
                // Since data will be emitted on the EventLoop, offload the data path to avoid blocking EventLoop
                .publishOn(executionContext.executor());
    }

    abstract Publisher<RedisData> handleRequest(RedisRequest request);

    @Override
    public final ExecutionContext executionContext() {
        return executionContext;
    }

    @SuppressWarnings("unchecked")
    @Override
    public final <T> Publisher<T> settingStream(SettingKey<T> settingKey) {
        if (settingKey == MAX_CONCURRENCY) {
            return (Publisher<T>) maxConcurrencySetting;
        }
        return error(new IllegalArgumentException("Unknown option: " + settingKey));
    }

    @Override
    public final Completable closeAsync() {
        return closeAsync;
    }

    final void startPings() {
        if (pinger instanceof Pinger) {
            ((Pinger) pinger).startPings();
        }
    }

    /**
     * Implement close for this connection.
     *
     * @return {@link Completable} that encapsulates close.
     */
    abstract Completable doClose();

    /**
     * Send a PING to the server.
     *
     * @return {@link Completable} that encapsulates sending a PING request.
     */
    abstract Completable sendPing();

    abstract Logger getLogger();

    private final class Pinger implements AsyncCloseable {

        private final Completable closeAsync;
        private final long pingPeriodNanos;
        private final TimerSubscriber timerSubscriber;

        Pinger(Duration pingPeriod) {
            pingPeriodNanos = pingPeriod.toNanos();
            PingSubscriber pingSubscriber = new PingSubscriber();
            timerSubscriber = new TimerSubscriber(pingSubscriber, pingPeriod,
                    () -> sendPing().subscribe(pingSubscriber),
                    () -> pingTimerProvider.timer(pingPeriodNanos, NANOSECONDS));
            closeAsync = new Completable() {
                @Override
                protected void handleSubscribe(Subscriber subscriber) {
                    timerSubscriber.cancel();
                    pingSubscriber.cancel();
                    subscriber.onSubscribe(IGNORE_CANCEL);
                    subscriber.onComplete();
                }
            };
        }

        void startPings() {
            getLogger().debug("Connection: {} starting PING timer.", AbstractRedisConnection.this);
            pingTimerProvider.timer(pingPeriodNanos, NANOSECONDS).subscribe(timerSubscriber);
        }

        @Override
        public Completable closeAsync() {
            return closeAsync;
        }
    }

    private final class PingSubscriber extends SequentialCancellable implements Completable.Subscriber {

        private volatile boolean inProgress; // volatile for visibility.

        @Override
        public void onSubscribe(Cancellable cancellable) {
            inProgress = true;
            setNextCancellable(cancellable);
        }

        @Override
        public void onComplete() {
            getLogger().debug("Connection: {} received PING response.", AbstractRedisConnection.this);
            inProgress = false;
        }

        @Override
        public void onError(Throwable t) {
            inProgress = false;
            // Ignore failures due to a saturated connection pipeline
            if (!(t instanceof ClosedChannelException || t instanceof PingRejectedException)) {
                getLogger().warn("Connection: {} failed to consume PING response, closing connection.", AbstractRedisConnection.this, t);
                closeAsync().subscribe();
            }
        }

        boolean isPingInProgress() {
            return inProgress;
        }
    }

    private final class TimerSubscriber extends SequentialCancellable implements Completable.Subscriber {

        private final PingSubscriber pingSubscriber;
        private final Duration pingDuration;
        private final Runnable pingSender;
        private final Supplier<Completable> timer;

        TimerSubscriber(PingSubscriber pingSubscriber, Duration pingDuration, Runnable pingSender, Supplier<Completable> timer) {
            this.pingSubscriber = pingSubscriber;
            this.pingDuration = pingDuration;
            this.pingSender = pingSender;
            this.timer = timer;
        }

        @Override
        public void onSubscribe(Cancellable cancellable) {
            setNextCancellable(cancellable);
        }

        @Override
        public void onComplete() {
            if (pingSubscriber.isPingInProgress()) {
                getLogger().warn("Connection: {} ping did not complete within the ping duration: {}. Closing the connection.", AbstractRedisConnection.this, pingDuration);
                closeAsync().subscribe();
            } else {
                getLogger().debug("Connection: {} Sending ping.", AbstractRedisConnection.this);
                pingSender.run();
                timer.get().subscribe(this);
            }
        }

        @Override
        public void onError(Throwable t) {
            if (!(t instanceof CancellationException)) {
                getLogger().error("Connection: {} unexpected timer error, stopping pings.", AbstractRedisConnection.this, t);
            }
        }
    }
}
