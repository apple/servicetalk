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
package io.servicetalk.http.utils;

import io.servicetalk.client.api.RetryableException;
import io.servicetalk.concurrent.api.BiIntFunction;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.HttpClientFilterFactory;
import io.servicetalk.http.api.HttpConnectionFilterFactory;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.http.api.HttpResponseMetaData;
import io.servicetalk.http.api.StreamingHttpClient;
import io.servicetalk.http.api.StreamingHttpClient.ReservedStreamingHttpConnection;
import io.servicetalk.http.api.StreamingHttpClientFilter;
import io.servicetalk.http.api.StreamingHttpConnection;
import io.servicetalk.http.api.StreamingHttpConnectionFilter;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequester;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.utils.RetryingHttpRequestFilter.Builder.ReadOnlyRetryableSettings;

import java.time.Duration;
import java.util.function.Predicate;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Completable.completed;
import static io.servicetalk.concurrent.api.Completable.error;
import static io.servicetalk.concurrent.api.RetryStrategies.retryWithConstantBackoff;
import static io.servicetalk.concurrent.api.RetryStrategies.retryWithConstantBackoffAndJitter;
import static io.servicetalk.concurrent.api.RetryStrategies.retryWithExponentialBackoff;
import static io.servicetalk.concurrent.api.RetryStrategies.retryWithExponentialBackoffAndJitter;

/**
 * A filter to enable retries for HTTP requests.
 */
public final class RetryingHttpRequestFilter implements HttpClientFilterFactory, HttpConnectionFilterFactory {

    private final ReadOnlyRetryableSettings settings;

    RetryingHttpRequestFilter(final ReadOnlyRetryableSettings settings) {
        this.settings = settings;
    }

    private Single<StreamingHttpResponse> request(final BiIntFunction<Throwable, Completable> retryStrategy,
                                                  final StreamingHttpRequester delegate,
                                                  final HttpExecutionStrategy executionStrategy,
                                                  final StreamingHttpRequest request) {
        if (settings.isRetryable(request)) {
            return delegate.request(executionStrategy, request).retryWhen(retryStrategy);
        }
        return delegate.request(executionStrategy, request);
    }

    private Single<? extends ReservedStreamingHttpConnection> reserve(
            final BiIntFunction<Throwable, Completable> retryStrategy,
            final StreamingHttpClient delegate,
            final HttpExecutionStrategy executionStrategy,
            final StreamingHttpRequest request) {
        if (settings.isRetryable(request)) {
            return delegate.reserveConnection(executionStrategy, request).retryWhen(retryStrategy);
        }
        return delegate.reserveConnection(executionStrategy, request);
    }

    @Override
    public StreamingHttpClientFilter create(final StreamingHttpClient client, final Publisher<Object> lbEvents) {
        return new StreamingHttpClientFilter(client) {

            final BiIntFunction<Throwable, Completable> retryStrategy =
                    settings.newStrategy(client.executionContext().executor());

            @Override
            protected Single<StreamingHttpResponse> request(final StreamingHttpRequester delegate,
                                                            final HttpExecutionStrategy executionStrategy,
                                                            final StreamingHttpRequest request) {
                return RetryingHttpRequestFilter.this.request(retryStrategy, delegate, executionStrategy, request);
            }

            @Override
            protected Single<? extends ReservedStreamingHttpConnection> reserve(
                    final StreamingHttpClient delegate,
                    final HttpExecutionStrategy executionStrategy,
                    final StreamingHttpRequest request) {
                return RetryingHttpRequestFilter.this.reserve(retryStrategy, delegate, executionStrategy, request);
            }
        };
    }

    @Override
    public StreamingHttpConnectionFilter create(final StreamingHttpConnection connection) {

        final BiIntFunction<Throwable, Completable> retryStrategy =
                settings.newStrategy(connection.executionContext().executor());

        return new StreamingHttpConnectionFilter(connection) {
            @Override
            public Single<StreamingHttpResponse> request(final HttpExecutionStrategy executionStrategy,
                                                         final StreamingHttpRequest request) {
                return RetryingHttpRequestFilter.this.request(retryStrategy, delegate(), executionStrategy, request);
            }
        };
    }

    /**
     * A builder for {@link RetryingHttpRequestFilter}.
     */
    public static final class Builder {

        @Nullable
        private Executor executor;
        private int retryCount = 1;
        private boolean jitter;
        private boolean exponential;
        @Nullable
        private Duration initialDelay;
        private Predicate<Throwable> causeFilter = throwable -> throwable instanceof RetryableException;
        private Predicate<HttpRequestMetaData> retryablePredicate =
                meta -> meta.method().methodProperties().idempotent();

        /**
         * New instance.
         * <p>
         * By default this builder will not infinitely retry. To configure the number of retry attempts see
         * {@link #retryCount(int)}.
         */
        public Builder() {
        }

        /**
         * Set the number of retry operations before giving up.
         *
         * @param retryCount the number of retry operations before giving up.
         * @return {@code this}.
         */
        public Builder retryCount(int retryCount) {
            if (retryCount <= 0) {
                throw new IllegalArgumentException("retryCount: " + retryCount + " (expected: >0)");
            }
            this.retryCount = retryCount;
            return this;
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
         * The resulting {@link RetryingHttpRequestFilter} from {@link #build()} may not attempt to check for
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
         * When {@link #exponentialBackoff(Duration)} or {@link #backoff(Duration)} is used, adding jitter will
         * randomize the delays between the retries.
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
         * @param retryablePredicate {@link Predicate} that checks whether a given {@link HttpResponseMetaData request}
         * should be retried.
         * @return {@code this}.
         */
        public Builder retryForRequest(Predicate<HttpRequestMetaData> retryablePredicate) {
            this.retryablePredicate = retryablePredicate;
            return this;
        }

        /**
         * Builds a {@link RetryingHttpRequestFilter}.
         *
         * @return A new {@link RetryingHttpRequestFilter}.
         */
        public RetryingHttpRequestFilter build() {
            return new RetryingHttpRequestFilter(new ReadOnlyRetryableSettings(executor, retryCount, jitter,
                    exponential, initialDelay, causeFilter, retryablePredicate));
        }

        static final class ReadOnlyRetryableSettings {

            @Nullable
            private final Executor executor;
            private final int retryCount;
            private final boolean jitter;
            private final boolean exponential;
            @Nullable
            private final Duration initialDelay;
            private final Predicate<Throwable> causeFilter;
            private final Predicate<HttpRequestMetaData> retryablePredicate;

            ReadOnlyRetryableSettings(@Nullable final Executor executor,
                                      int retryCount,
                                      final boolean jitter,
                                      final boolean exponential,
                                      @Nullable final Duration initialDelay,
                                      final Predicate<Throwable> causeFilter,
                                      final Predicate<HttpRequestMetaData> retryablePredicate) {
                this.executor = executor;
                this.retryCount = retryCount;
                this.jitter = jitter;
                this.exponential = exponential;
                this.initialDelay = initialDelay;
                this.causeFilter = causeFilter;
                this.retryablePredicate = retryablePredicate;
            }

            boolean isRetryable(HttpRequestMetaData meta) {
                return retryablePredicate.test(meta);
            }

            BiIntFunction<Throwable, Completable> newStrategy(Executor requesterExecutor) {
                if (initialDelay == null) {
                    return (count, throwable) ->
                            causeFilter.test(throwable) && count <= retryCount ? completed() : error(throwable);
                } else {
                    Executor effectiveExecutor = executor == null ? requesterExecutor : executor;
                    if (exponential) {
                        if (jitter) {
                            return retryWithExponentialBackoffAndJitter(
                                    retryCount, causeFilter, initialDelay, effectiveExecutor);
                        } else {
                            return retryWithExponentialBackoff(
                                    retryCount, causeFilter, initialDelay, effectiveExecutor);
                        }
                    } else {
                        if (jitter) {
                            return retryWithConstantBackoffAndJitter(
                                    retryCount, causeFilter, initialDelay, effectiveExecutor);
                        } else {
                            return retryWithConstantBackoff(retryCount, causeFilter, initialDelay, effectiveExecutor);
                        }
                    }
                }
            }
        }
    }
}
