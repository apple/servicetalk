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
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.http.api.HttpResponseMetaData;
import io.servicetalk.http.api.StreamingHttpClient;
import io.servicetalk.http.api.StreamingHttpClientAdapter;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;

import java.time.Duration;
import java.util.function.Predicate;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Completable.completed;
import static io.servicetalk.concurrent.api.Completable.error;
import static io.servicetalk.concurrent.api.RetryStrategies.retryWithConstantBackoff;
import static io.servicetalk.concurrent.api.RetryStrategies.retryWithConstantBackoffAndJitter;
import static io.servicetalk.concurrent.api.RetryStrategies.retryWithExponentialBackoff;
import static io.servicetalk.concurrent.api.RetryStrategies.retryWithExponentialBackoffAndJitter;
import static java.util.Objects.requireNonNull;

/**
 * A {@link StreamingHttpClient} to enable retries for requests. Use {@link #asBlockingClient()},
 * {@link #asBlockingStreamingClient()} or {@link #asClient()} to use this filter with other programming models.
 */
public final class RetryingHttpClientFilter extends StreamingHttpClientAdapter {

    private final BiIntFunction<Throwable, Completable> strategy;
    private final Predicate<HttpRequestMetaData> isRetryable;

    private RetryingHttpClientFilter(final StreamingHttpClient delegate,
                                     final BiIntFunction<Throwable, Completable> strategy,
                                     final Predicate<HttpRequestMetaData> isRetryable) {
        super(delegate);
        this.strategy = requireNonNull(strategy);
        this.isRetryable = requireNonNull(isRetryable);
    }

    @Override
    public Single<StreamingHttpResponse> request(final StreamingHttpRequest request) {
        if (isRetryable.test(request)) {
            return delegate().request(request).retryWhen(strategy);
        }
        return delegate().request(request);
    }

    @Override
    public Single<? extends ReservedStreamingHttpConnection> reserveConnection(final StreamingHttpRequest request) {
        if (isRetryable.test(request)) {
            return delegate().reserveConnection(request).retryWhen(strategy);
        }
        return delegate().reserveConnection(request);
    }

    @Override
    public Single<? extends UpgradableStreamingHttpResponse> upgradeConnection(final StreamingHttpRequest request) {
        if (isRetryable.test(request)) {
            return delegate().upgradeConnection(request).retryWhen(strategy);
        }
        return delegate().upgradeConnection(request);
    }

    /**
     * A builder for {@link RetryingHttpClientFilter}.
     */
    public static final class Builder {

        private final StreamingHttpClient delegate;
        private Executor executor;
        private boolean jitter;
        private boolean exponential;
        @Nullable
        private Duration initialDelay;
        private Predicate<Throwable> causeFilter = throwable -> throwable instanceof RetryableException;
        private Predicate<HttpRequestMetaData> retryableFilter =
                meta -> meta.method().getMethodProperties().isIdempotent();

        /**
         * New instance.
         *
         * @param delegate {@link StreamingHttpClient} to wrap with retries.
         */
        public Builder(final StreamingHttpClient delegate) {
            this.delegate = requireNonNull(delegate);
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
         * The resulting {@link RetryingHttpClientFilter} from {@link #build(int)} may not attempt to check for
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
         * @param retryableFilter {@link Predicate} that checks whether a given {@link HttpResponseMetaData request}
         * should be retried.
         * @return {@code this}.
         */
        public Builder retryForRequest(Predicate<HttpRequestMetaData> retryableFilter) {
            this.retryableFilter = retryableFilter;
            return this;
        }

        /**
         * Builds a {@link RetryingHttpClientFilter}.
         *
         * @param retryCount Maximum number of retries.
         * @return A new {@link RetryingHttpClientFilter}.
         */
        public RetryingHttpClientFilter build(int retryCount) {
            Predicate<Throwable> causeFilter = this.causeFilter; // Save reference since accessed lazily.
            final BiIntFunction<Throwable, Completable> strategy;
            if (initialDelay == null) {
                strategy = (count, throwable) -> causeFilter.test(throwable) && count <= retryCount ?
                        completed() : error(throwable);
            } else {
                Duration initialDelay = this.initialDelay;
                if (exponential) {
                    if (jitter) {
                        strategy = retryWithExponentialBackoffAndJitter(retryCount, causeFilter, initialDelay, executor);
                    } else {
                        strategy = retryWithExponentialBackoff(retryCount, causeFilter, initialDelay, executor);
                    }
                } else {
                    if (jitter) {
                        strategy = retryWithConstantBackoffAndJitter(retryCount, causeFilter, initialDelay, executor);
                    } else {
                        strategy = retryWithConstantBackoff(retryCount, causeFilter, initialDelay, executor);
                    }
                }
            }
            return new RetryingHttpClientFilter(delegate, strategy, retryableFilter);
        }
    }
}
