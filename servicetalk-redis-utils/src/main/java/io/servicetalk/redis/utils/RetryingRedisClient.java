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
package io.servicetalk.redis.utils;

import io.servicetalk.client.api.RetryableException;
import io.servicetalk.concurrent.api.BiIntFunction;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.RetryStrategies;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.redis.api.RedisClient;
import io.servicetalk.redis.api.RedisData;
import io.servicetalk.redis.api.RedisExecutionStrategy;
import io.servicetalk.redis.api.RedisProtocolSupport.Command;
import io.servicetalk.redis.api.RedisRequest;

import java.time.Duration;
import java.util.function.Predicate;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Completable.completed;
import static io.servicetalk.concurrent.api.Completable.error;
import static io.servicetalk.concurrent.api.RetryStrategies.retryWithConstantBackoff;
import static io.servicetalk.concurrent.api.RetryStrategies.retryWithExponentialBackoff;
import static io.servicetalk.concurrent.api.RetryStrategies.retryWithExponentialBackoffAndJitter;
import static io.servicetalk.redis.api.RedisProtocolSupport.CommandFlag.READONLY;
import static java.util.Objects.requireNonNull;

/**
 * A {@link RedisClient} wrapper that applies a retry strategy.
 *
 * @see RedisRequestAwareRetryStrategy
 * @see RetryStrategies
 */
public final class RetryingRedisClient extends DelegatingRedisClient {

    private final BiIntFunction<Throwable, Completable> strategy;
    private final Predicate<Command> isRetryable;

    private RetryingRedisClient(final RedisClient delegate, final BiIntFunction<Throwable, Completable> strategy,
                                final Predicate<Command> isRetryable) {
        super(delegate);
        this.strategy = requireNonNull(strategy);
        this.isRetryable = requireNonNull(isRetryable);
    }

    @Override
    public Publisher<RedisData> request(final RedisExecutionStrategy executionStrategy, final RedisRequest request) {
        if (!isRetryable.test(request.command())) {
            return super.request(executionStrategy, request);
        }
        return super.request(executionStrategy, request).retryWhen(strategy);
    }

    @Override
    public <R> Single<R> request(final RedisExecutionStrategy executionStrategy, final RedisRequest request,
                                 final Class<R> responseType) {
        if (!isRetryable.test(request.command())) {
            return super.request(executionStrategy, request, responseType);
        }
        return super.request(executionStrategy, request, responseType).retryWhen(strategy);
    }

    @Override
    public Single<? extends ReservedRedisConnection> reserveConnection(RedisExecutionStrategy executionStrategy,
                                                                       Command command) {
        if (!isRetryable.test(command)) {
            return super.reserveConnection(executionStrategy, command);
        }
        return super.reserveConnection(executionStrategy, command).retryWhen(strategy);
    }

    /**
     * Create a new {@link Builder} to build a new {@link RetryingRedisClient} for the passed {@link RedisClient}.
     *
     * @param delegate {@link RetryingRedisClient} to which retries have to be applied.
     * @return A new {@link Builder} to build this filter.
     */
    public static Builder newBuilder(RedisClient delegate) {
        return new Builder(delegate);
    }

    /**
     * A builder for {@link RetryingRedisClient}.
     */
    public static final class Builder {

        private final RedisClient delegate;
        private Executor executor;
        private boolean jitter;
        private boolean exponential;
        @Nullable
        private Duration initialDelay;
        private Predicate<Throwable> causeFilter = throwable -> throwable instanceof RetryableException;
        private Predicate<Command> retryableFilter = meta -> meta.hasFlag(READONLY);

        Builder(final RedisClient delegate) {
            this.delegate = delegate;
            executor = delegate.executionContext().executor();
        }

        /**
         * Adds a delay of {@code delay} between retries.
         *
         * @param delay Delay {@link Duration} for the retries.
         * @return {@code this}.
         */
        public Builder backoff(Duration delay) {
            this.initialDelay = delay;
            return this;
        }

        /**
         * Adds a delay between retries. For first retry, the delay is {@code initialDelay} which is increased
         * exponentially for subsequent retries.
         * <p>
         * The resulting {@link RetryingRedisClient} from {@link #build(int)} may not attempt to check for
         * overflow if the retry count is high enough that an exponential delay causes {@link Long} overflow.
         *
         * @param initialDelay Delay {@link Duration} for the first retry and increased exponentially with each retry.
         * @return {@code this}.
         */
        public Builder exponentialBackoff(Duration initialDelay) {
            this.initialDelay = initialDelay;
            this.exponential = true;
            return this;
        }

        /**
         * Uses the passed {@link Executor} for scheduling timers if {@link #backoff(Duration)} or
         * {@link #exponentialBackoff(Duration)} is used.
         *
         * @param executor {@link Executor} for scheduling timers.
         * @return {@code this}.
         */
        public Builder timerExecutor(Executor executor) {
            this.executor = executor;
            return this;
        }

        /**
         * When {@link #exponentialBackoff(Duration)} is used, adding jitter will randomize the delays between the
         * retries.
         *
         * @return {@code this}
         */
        public Builder addJitter() {
            this.jitter = true;
            return this;
        }

        /**
         * Overrides the default criterion for determining which errors should be retried.
         *
         * @param causeFilter {@link Predicate} that checks whether a given {@link Throwable cause} of the failure
         * should be retried.
         * @return {@code this}.
         */
        public Builder retryForCause(Predicate<Throwable> causeFilter) {
            this.causeFilter = causeFilter;
            return this;
        }

        /**
         * Overrides the default criterion for determining which requests should be retried.
         *
         * @param retryableFilter {@link Predicate} that checks whether a given {@link Command command} should be
         * retried.
         * @return {@code this}.
         */
        public Builder retryForRequest(Predicate<Command> retryableFilter) {
            this.retryableFilter = retryableFilter;
            return this;
        }

        /**
         * Builds a {@link RetryingRedisClient}.
         *
         * @param retryCount Maximum number of retries.
         * @return A new {@link RetryingRedisClient}.
         */
        public RetryingRedisClient build(int retryCount) {
            Predicate<Throwable> causeFilter = this.causeFilter; // Copy since accessed lazily.
            final BiIntFunction<Throwable, Completable> strategy;
            if (initialDelay == null) {
                strategy = (count, throwable) -> causeFilter.test(throwable) && count <= retryCount ?
                        completed() : error(throwable);
            } else {
                Duration initialDelay = this.initialDelay;
                if (exponential) {
                    if (jitter) {
                        strategy = retryWithExponentialBackoffAndJitter(retryCount, causeFilter, initialDelay,
                                executor);
                    } else {
                        strategy = retryWithExponentialBackoff(retryCount, causeFilter, initialDelay, executor);
                    }
                } else {
                    strategy = retryWithConstantBackoff(retryCount, causeFilter, initialDelay, executor);
                }
            }
            return new RetryingRedisClient(delegate, strategy, retryableFilter);
        }
    }
}
