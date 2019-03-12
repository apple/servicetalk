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
import io.servicetalk.concurrent.api.BiIntFunction;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.RetryStrategies;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.HttpClientFilterFactory;
import io.servicetalk.http.api.HttpConnectionFilterFactory;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.http.api.StreamingHttpClientFilter;
import io.servicetalk.http.api.StreamingHttpConnectionFilter;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequestFunction;
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

    private final ReadOnlyRetryableSettings<HttpRequestMetaData> settings;

    private RetryingHttpRequesterFilter(final ReadOnlyRetryableSettings<HttpRequestMetaData> settings) {
        this.settings = settings;
    }

    private Single<StreamingHttpResponse> request(final StreamingHttpRequestFunction delegate,
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
    public StreamingHttpClientFilter create(final StreamingHttpClientFilter client, final Publisher<Object> lbEvents) {
        return new StreamingHttpClientFilter(client) {

            private final BiIntFunction<Throwable, Completable> retryStrategy =
                    settings.newStrategy(client.executionContext().executor());

            @Override
            protected Single<StreamingHttpResponse> request(final StreamingHttpRequestFunction delegate,
                                                            final HttpExecutionStrategy strategy,
                                                            final StreamingHttpRequest request) {
                return RetryingHttpRequesterFilter.this.request(delegate, strategy, request, retryStrategy);
            }

            @Override
            protected HttpExecutionStrategy mergeForEffectiveStrategy(final HttpExecutionStrategy mergeWith) {
                // Since this filter does not have any blocking code, we do not need to alter the effective strategy.
                return mergeWith;
            }
        };
    }

    @Override
    public StreamingHttpConnectionFilter create(final StreamingHttpConnectionFilter connection) {
        return new StreamingHttpConnectionFilter(connection) {

            private final BiIntFunction<Throwable, Completable> retryStrategy =
                    settings.newStrategy(connection.executionContext().executor());

            @Override
            protected Single<StreamingHttpResponse> request(final StreamingHttpConnectionFilter delegate,
                                                            final HttpExecutionStrategy strategy,
                                                            final StreamingHttpRequest request) {
                return RetryingHttpRequesterFilter.this.request(delegate, strategy, request, retryStrategy);
            }

            @Override
            protected HttpExecutionStrategy mergeForEffectiveStrategy(final HttpExecutionStrategy mergeWith) {
                // Since this filter does not have any blocking code, we do not need to alter the effective strategy.
                return mergeWith;
            }
        };
    }

    /**
     * A builder for {@link RetryingHttpRequesterFilter}, which puts an upper bound on retry attempts.
     * To configure the maximum number of retry attempts see {@link #maxRetries(int)}.
     */
    public static final class Builder
            extends AbstractRetryingFilterBuilder<Builder, RetryingHttpRequesterFilter, HttpRequestMetaData> {

        @Override
        protected RetryingHttpRequesterFilter build(
                final ReadOnlyRetryableSettings<HttpRequestMetaData> readOnlySettings) {
            return new RetryingHttpRequesterFilter(readOnlySettings);
        }

        /**
         * Behaves as {@link #defaultRetryForPredicate()}, but also retries
         * <a href="https://tools.ietf.org/html/rfc7231#section-4.2.2">idempotent</a> requests when applicable.
         * <p>
         * <b>Note:</b> This predicate expects that the retried {@link StreamingHttpRequest requests} have a
         * {@link StreamingHttpRequest#payloadBody() payload body} that is replayable, i.e. multiple subscribes to the
         * payload {@link Publisher} emit the same data. {@link Publisher}s that do not emit any data or which are
         * created from in-memory data are typically replayable.
         *
         * @return a {@link BiPredicate} for {@link #retryFor(BiPredicate)} builder method
         */
        public BiPredicate<HttpRequestMetaData, Throwable> retryForIdempotentRequestsPredicate() {
            return defaultRetryForPredicate().or((meta, throwable) ->
                    throwable instanceof IOException && meta.method().properties().isIdempotent());
        }
    }
}
