/*
 * Copyright © 2021 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.RetryStrategies;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.FilterableStreamingHttpClient;
import io.servicetalk.http.api.FilterableStreamingHttpConnection;
import io.servicetalk.http.api.HttpExecutionStrategies;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.http.api.StreamingHttpClientFilter;
import io.servicetalk.http.api.StreamingHttpClientFilterFactory;
import io.servicetalk.http.api.StreamingHttpConnectionFilter;
import io.servicetalk.http.api.StreamingHttpConnectionFilterFactory;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequester;
import io.servicetalk.http.api.StreamingHttpResponse;

import java.io.IOException;
import java.util.function.BiPredicate;

import static io.servicetalk.concurrent.api.Completable.failed;

/**
 * A filter to enable retries for HTTP requests.
 *
 * @see RetryStrategies
 * @deprecated A replacement retrying http filter is available through
 * {@code io.servicetalk.http.netty.RetryingHttpRequesterFilter}
 */
@Deprecated
public final class RetryingHttpRequesterFilter implements StreamingHttpClientFilterFactory,
                                                          StreamingHttpConnectionFilterFactory {

    private final ReadOnlyRetryableSettings<HttpRequestMetaData> settings;

    private RetryingHttpRequesterFilter(final ReadOnlyRetryableSettings<HttpRequestMetaData> settings) {
        this.settings = settings;
    }

    private Single<StreamingHttpResponse> request(final StreamingHttpRequester delegate,
                                                  final StreamingHttpRequest request,
                                                  final BiIntFunction<Throwable, Completable> retryStrategy) {
        return delegate.request(request).retryWhen((count, t) -> {
            if (settings.isRetryable(request, t)) {
                return retryStrategy.apply(count, t);
            }
            return failed(t);
        });
    }

    @Override
    public StreamingHttpClientFilter create(final FilterableStreamingHttpClient client) {
        return new StreamingHttpClientFilter(client) {

            private final Executor executor = client.executionContext().executor();
            private final BiIntFunction<Throwable, Completable> retryStrategy =
                    settings.newStrategy(executor);

            @Override
            protected Single<StreamingHttpResponse> request(final StreamingHttpRequester delegate,
                                                            final StreamingHttpRequest request) {
                return RetryingHttpRequesterFilter.this.request(delegate, request, retryStrategy);
            }
        };
    }

    @Override
    public StreamingHttpConnectionFilter create(final FilterableStreamingHttpConnection connection) {
        return new StreamingHttpConnectionFilter(connection) {

            private final Executor executor = connection.executionContext().executor();
            private final BiIntFunction<Throwable, Completable> retryStrategy =
                    settings.newStrategy(executor);

            @Override
            public Single<StreamingHttpResponse> request(final StreamingHttpRequest request) {
                return RetryingHttpRequesterFilter.this.request(delegate(), request, retryStrategy);
            }
       };
    }

    @Override
    public HttpExecutionStrategy requiredOffloads() {
        // No influence since we do not block.
        return HttpExecutionStrategies.offloadNone();
    }

    /**
     * A builder for {@link RetryingHttpRequesterFilter}, which puts an upper bound on retry attempts.
     * To configure the maximum number of retry attempts see {@link #maxRetries(int)}.
     * @deprecated A replacement retrying http filter builder is available through
     * {@code io.servicetalk.http.netty.RetryingHttpRequesterFilterBuilder}
     */
    @Deprecated
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
         * {@link StreamingHttpRequest#payloadBody() payload body} that is
         * <a href="http://reactivex.io/documentation/operators/replay.html">replayable</a>, i.e. multiple subscribes to
         * the payload {@link Publisher} observe the same data. {@link Publisher}s that do not emit any data or which
         * are created from in-memory data are typically replayable.
         *
         * @return a {@link BiPredicate} for {@link #retryFor(BiPredicate)} builder method
         */
        public BiPredicate<HttpRequestMetaData, Throwable> retryForIdempotentRequestsPredicate() {
            return defaultRetryForPredicate().or((meta, throwable) ->
                    throwable instanceof IOException && meta.method().properties().isIdempotent());
        }
    }
}
