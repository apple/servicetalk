/*
 * Copyright © 2019 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.client.api.AbstractRetryingFilterBuilder;
import io.servicetalk.client.api.AbstractRetryingFilterBuilder.ReadOnlyRetryableSettings;
import io.servicetalk.client.api.RetryableException;
import io.servicetalk.concurrent.api.BiIntFunction;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.RetryStrategies;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.redis.api.RedisClient;
import io.servicetalk.redis.api.RedisClientFilter;
import io.servicetalk.redis.api.RedisClientFilterFactory;
import io.servicetalk.redis.api.RedisConnection;
import io.servicetalk.redis.api.RedisConnectionFilter;
import io.servicetalk.redis.api.RedisConnectionFilterFactory;
import io.servicetalk.redis.api.RedisData;
import io.servicetalk.redis.api.RedisExecutionStrategy;
import io.servicetalk.redis.api.RedisProtocolSupport.Command;
import io.servicetalk.redis.api.RedisRequest;
import io.servicetalk.redis.api.RedisRequester;
import io.servicetalk.redis.api.ReservedRedisConnectionFilter;

import java.time.Duration;
import java.util.function.BiPredicate;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Completable.error;
import static io.servicetalk.redis.api.RedisProtocolSupport.CommandFlag.READONLY;

/**
 * A filter to enable retries for {@link RedisRequest}s.
 *
 * @see RetryStrategies
 */
public final class RetryingRedisRequesterFilter implements RedisClientFilterFactory, RedisConnectionFilterFactory {

    private final ReadOnlyRetryableSettings<Command> settings;

    private RetryingRedisRequesterFilter(final ReadOnlyRetryableSettings<Command> settings) {
        this.settings = settings;
    }

    private Publisher<RedisData> request(final RedisRequester delegate,
                                         final RedisExecutionStrategy strategy,
                                         final RedisRequest request,
                                         final BiIntFunction<Throwable, Completable> retryStrategy) {
        return delegate.request(strategy, request).retryWhen((count, t) -> {
            if (settings.isRetryable(request.command(), t)) {
                return retryStrategy.apply(count, t);
            }
            return error(t);
        });
    }

    @Override
    public RedisClientFilter create(final RedisClient client,
                                    final Publisher<Object> subscribeLoadBalancerEvents,
                                    final Publisher<Object> pipelinedLoadBalancerEvents) {
        return new RedisClientFilter(client) {

            private final BiIntFunction<Throwable, Completable> retryStrategy =
                    settings.newStrategy(delegate().executionContext().executor());

            @Override
            public Publisher<RedisData> request(final RedisExecutionStrategy strategy, final RedisRequest request) {
                return RetryingRedisRequesterFilter.this.request(delegate(), strategy, request, retryStrategy);
            }

            @Override
            public Single<? extends RedisClient.ReservedRedisConnection> reserveConnection(
                    final RedisExecutionStrategy strategy,
                    final Command command) {

                return delegate().reserveConnection(strategy, command).retryWhen((count, t) -> {
                    if (settings.isRetryable(command, t)) {
                        return retryStrategy.apply(count, t);
                    }
                    return error(t);
                }).map(r -> new ReservedRedisConnectionFilter(r) {
                    @Override
                    public Publisher<RedisData> request(final RedisExecutionStrategy strategy,
                                                        final RedisRequest request) {
                        return RetryingRedisRequesterFilter.this.request(delegate(), strategy, request, retryStrategy);
                    }
                });
            }
        };
    }

    @Override
    public RedisConnectionFilter create(final RedisConnection connection) {
        return new RedisConnectionFilter(connection) {

            private final BiIntFunction<Throwable, Completable> retryStrategy =
                    settings.newStrategy(delegate().executionContext().executor());

            @Override
            public Publisher<RedisData> request(final RedisExecutionStrategy strategy, final RedisRequest request) {
                return RetryingRedisRequesterFilter.this.request(delegate(), strategy, request, retryStrategy);
            }
        };
    }

    /**
     * A builder for {@link RetryingRedisRequesterFilter}, which will not infinitely retry. To configure the maximum
     * number of retry attempts see {@link #maxRetries(int)}.
     */
    public static final class Builder extends AbstractRetryingFilterBuilder<RetryingRedisRequesterFilter, Command> {

        @Override
        public Builder maxRetries(final int maxRetries) {
            super.maxRetries(maxRetries);
            return this;
        }

        @Override
        public Builder backoff(final Duration delay) {
            super.backoff(delay);
            return this;
        }

        @Override
        public Builder exponentialBackoff(final Duration initialDelay) {
            super.exponentialBackoff(initialDelay);
            return this;
        }

        @Override
        public Builder noBackoff() {
            super.noBackoff();
            return this;
        }

        @Override
        public Builder addJitter() {
            super.addJitter();
            return this;
        }

        @Override
        public Builder noJitter() {
            super.noJitter();
            return this;
        }

        @Override
        public Builder timerExecutor(@Nullable final Executor timerExecutor) {
            super.timerExecutor(timerExecutor);
            return this;
        }

        @Override
        public Builder retryFor(final BiPredicate<Command, Throwable> retryForPredicate) {
            super.retryFor(retryForPredicate);
            return this;
        }

        @Override
        public BiPredicate<Command, Throwable> defaultRetryForPredicate() {
            return (command, throwable) -> throwable instanceof RetryableException || command.hasFlag(READONLY);
        }

        /**
         * Builds a {@link RetryingRedisRequesterFilter}.
         *
         * @return A new {@link RetryingRedisRequesterFilter}
         */
        @Override
        public RetryingRedisRequesterFilter build() {
            return new RetryingRedisRequesterFilter(readOnlySettings());
        }
    }
}
