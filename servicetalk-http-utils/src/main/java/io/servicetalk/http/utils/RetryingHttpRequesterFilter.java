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
package io.servicetalk.http.utils;

import io.servicetalk.client.api.AbstractRetryingFilterBuilder;
import io.servicetalk.client.api.AbstractRetryingFilterBuilder.ReadOnlyRetryableSettings;
import io.servicetalk.client.api.RetryableException;
import io.servicetalk.concurrent.api.BiIntFunction;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.RetryStrategies;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.HttpClientFilterFactory;
import io.servicetalk.http.api.HttpConnectionFilterFactory;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.StreamingHttpClient;
import io.servicetalk.http.api.StreamingHttpClientFilter;
import io.servicetalk.http.api.StreamingHttpConnection;
import io.servicetalk.http.api.StreamingHttpConnectionFilter;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequester;
import io.servicetalk.http.api.StreamingHttpResponse;

import java.io.IOException;
import java.util.function.BiPredicate;

import static io.servicetalk.concurrent.api.Completable.error;

/**
 * A filter to enable retries for HTTP requests.
 *
 * @see RetryStrategies
 */
public final class RetryingHttpRequesterFilter implements HttpClientFilterFactory, HttpConnectionFilterFactory {

    private final ReadOnlyRetryableSettings<StreamingHttpRequest> settings;

    private RetryingHttpRequesterFilter(final ReadOnlyRetryableSettings<StreamingHttpRequest> settings) {
        this.settings = settings;
    }

    private Single<StreamingHttpResponse> request(final StreamingHttpRequester delegate,
                                                  final HttpExecutionStrategy strategy,
                                                  final StreamingHttpRequest request,
                                                  final BiIntFunction<Throwable, Completable> retryStrategy) {
        return delegate.request(strategy, request).retryWhen((count, t) -> {
            if (settings.isRetryable(request, t)) {
                return retryStrategy.apply(count, t);
            }
            return error(t);
        });
    }

    @Override
    public StreamingHttpClientFilter create(final StreamingHttpClient client, final Publisher<Object> lbEvents) {
        return new StreamingHttpClientFilter(client) {

            private final BiIntFunction<Throwable, Completable> retryStrategy =
                    settings.newStrategy(client.executionContext().executor());

            @Override
            protected Single<StreamingHttpResponse> request(final StreamingHttpRequester delegate,
                                                            final HttpExecutionStrategy strategy,
                                                            final StreamingHttpRequest request) {
                return RetryingHttpRequesterFilter.this.request(delegate, strategy, request, retryStrategy);
            }
        };
    }

    @Override
    public StreamingHttpConnectionFilter create(final StreamingHttpConnection connection) {
        return new StreamingHttpConnectionFilter(connection) {

            private final BiIntFunction<Throwable, Completable> retryStrategy =
                    settings.newStrategy(connection.executionContext().executor());

            @Override
            public Single<StreamingHttpResponse> request(final HttpExecutionStrategy strategy,
                                                         final StreamingHttpRequest request) {
                return RetryingHttpRequesterFilter.this.request(delegate(), strategy, request, retryStrategy);
            }
        };
    }

    /**
     * A builder for {@link RetryingHttpRequesterFilter}, which puts an upper bound on retry attempts.
     * To configure the maximum number of retry attempts see {@link #maxRetries(int)}.
     */
    public static final class Builder
            extends AbstractRetryingFilterBuilder<Builder, RetryingHttpRequesterFilter, StreamingHttpRequest> {

        private boolean retryIdempotent;

        /**
         * Configures the {@link #defaultRetryForPredicate()} to retry
         * <a href="https://tools.ietf.org/html/rfc7231#section-4.2.2">idempotent</a> requests if {@link IOException}
         * occurred.
         * <p>
         * <b>Note 1:</b> Use this setting only for requests without payload body, or when you know that the payload
         * body of your request is repeatable.
         * <p>
         * <b>Note 2:</b> In case an alternative retry-for predicate was set via {@link #retryFor(BiPredicate)}, this
         * method has no effect.
         *
         * @return {@code this}
         * @see #doNotRetryIdempotent()
         */
        public Builder retryIdempotent() {
            retryIdempotent = true;
            return this;
        }

        /**
         * Disables retries for <a href="https://tools.ietf.org/html/rfc7231#section-4.2.2">idempotent</a> requests
         * configured via {@link #retryIdempotent()}.
         *
         * @return {@code this}
         * @see #retryIdempotent()
         */
        public Builder doNotRetryIdempotent() {
            retryIdempotent = false;
            return this;
        }

        @Override
        protected RetryingHttpRequesterFilter build(
                final ReadOnlyRetryableSettings<StreamingHttpRequest> readOnlySettings) {
            return new RetryingHttpRequesterFilter(readOnlySettings);
        }

        @Override
        public BiPredicate<StreamingHttpRequest, Throwable> defaultRetryForPredicate() {
            final boolean retryIdempotentSaved = retryIdempotent;
            return (request, throwable) -> {
                if (throwable instanceof RetryableException) {
                    return true;
                }
                return retryIdempotentSaved && throwable instanceof IOException
                        && request.method().methodProperties().idempotent();
            };
        }
    }
}
