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
package io.servicetalk.http.utils;

import io.servicetalk.concurrent.api.AsyncContext;
import io.servicetalk.concurrent.api.AsyncContextMap;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.FilterableStreamingHttpClient;
import io.servicetalk.http.api.FilterableStreamingHttpConnection;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpExecutionStrategyInfluencer;
import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.http.api.StreamingHttpClientFilter;
import io.servicetalk.http.api.StreamingHttpClientFilterFactory;
import io.servicetalk.http.api.StreamingHttpConnectionFilter;
import io.servicetalk.http.api.StreamingHttpConnectionFilterFactory;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequester;
import io.servicetalk.http.api.StreamingHttpResponse;

import java.time.Duration;
import java.time.Instant;
import java.util.function.Function;
import javax.annotation.Nullable;

/**
 * A filter to enable timeouts for HTTP requests. The timeout applies either the response metadata (headers) completion
 * or the complete reception of the response payload body and optional trailers.
 *
 * <p>The order with which this filter is applied may be highly significant. For example, appending it before a retry
 * filter would have different results than applying it after the retry filter; timeout would apply for all retries vs
 * timeout per retry.
 */
public final class TimeoutHttpRequesterFilter implements StreamingHttpClientFilterFactory,
                                                         StreamingHttpConnectionFilterFactory,
                                                         HttpExecutionStrategyInfluencer {
    /**
     * HTTP timeout is stored in context as a deadline so that when propagated to a new client request the remaining
     * time available for that request can be calculated.
     */
    private static final AsyncContextMap.Key<Instant> HTTP_DEADLINE_KEY =
            AsyncContextMap.Key.newKey("http-timeout-deadline");

    /**
     * Establishes the timeout for a given request
     */
    private final TimeoutFromRequest timeoutForRequest;
    private final boolean fullRequestResponse;
    @Nullable
    private final Executor timeoutExecutor;
    /**
     * Creates a new instance which requires only that the response metadata be received before the timeout.
     *
     * @param duration the timeout {@link Duration}
     */
    public TimeoutHttpRequesterFilter(final Duration duration) {
        this(useDefaultTimeout(duration), false);
    }

    /**
     * Creates a new instance which requires only that the response metadata be received before the timeout.
     *
     * @param duration the timeout {@link Duration}
     * @param timeoutExecutor the {@link Executor} to use for managing the timer notifications
     */
    public TimeoutHttpRequesterFilter(final Duration duration, final Executor timeoutExecutor) {
        this(useDefaultTimeout(duration), false, timeoutExecutor);
    }

    /**
     * Creates a new instance.
     *
     * @param timeoutForRequest function for extracting timeout from request which may also determine the timeout using
     * other sources. If no timeout is to be applied then the function should return null.
     * @param fullRequestResponse if true then timeout is for full request/response transaction otherwise only the
     * response metadata must arrive before the timeout.
     */
    public TimeoutHttpRequesterFilter(final TimeoutFromRequest timeoutForRequest, final boolean fullRequestResponse) {
        this.timeoutForRequest = timeoutForRequest;
        this.fullRequestResponse = fullRequestResponse;
        this.timeoutExecutor = null;
    }

    /**
     * Creates a new instance.
     *
     * @param timeoutForRequest function for extracting timeout from request which may also determine the timeout using
     * other sources. If no timeout is to be applied then the function should return null.
     * @param fullRequestResponse if {@code true} then timeout is for full request/response transaction otherwise only
     * the response metadata must arrive before the timeout.
     * @param timeoutExecutor the {@link Executor} to use for managing the timer notifications
     */
    public TimeoutHttpRequesterFilter(final TimeoutFromRequest timeoutForRequest,
                                      final boolean fullRequestResponse,
                                      final Executor timeoutExecutor) {
        this.timeoutForRequest = timeoutForRequest;
        this.fullRequestResponse = fullRequestResponse;
        this.timeoutExecutor = timeoutExecutor;
    }

    private Single<StreamingHttpResponse> request(final StreamingHttpRequester delegate,
                                                  final HttpExecutionStrategy strategy,
                                                  final StreamingHttpRequest request) {
        return Single.defer(() -> {
            Single<StreamingHttpResponse> response = delegate.request(strategy, request);

            Duration timeout = timeoutForRequest.apply(request);
            if (null == timeout) {
                // no timeout
                return response;
            }

            Single<StreamingHttpResponse> timeoutResponse = timeoutExecutor != null ?
                    response.timeout(timeout, timeoutExecutor) : response.timeout(timeout);

            return fullRequestResponse ?
                    Single.defer(() -> {
                        Instant beganAt = Instant.now();
                        return timeoutResponse.map(resp -> resp.transformMessageBody(body -> Publisher.defer(() -> {
                            Duration remaining = timeout.minus(Duration.between(beganAt, Instant.now()));
                            return (null != timeoutExecutor ?
                                    body.timeoutTerminal(remaining, timeoutExecutor) : body.timeoutTerminal(remaining))
                                    .subscribeShareContext();
                        }))).subscribeShareContext();
                    })
                    : timeoutResponse;
        });
    }

    @Override
    public StreamingHttpClientFilter create(final FilterableStreamingHttpClient client) {
        return new StreamingHttpClientFilter(client) {
            @Override
            protected Single<StreamingHttpResponse> request(final StreamingHttpRequester delegate,
                                                            final HttpExecutionStrategy strategy,
                                                            final StreamingHttpRequest request) {
                return TimeoutHttpRequesterFilter.this.request(delegate, strategy, request);
            }
       };
    }

    @Override
    public StreamingHttpConnectionFilter create(final FilterableStreamingHttpConnection connection) {
        return new StreamingHttpConnectionFilter(connection) {
            @Override
            public Single<StreamingHttpResponse> request(final HttpExecutionStrategy strategy,
                                                            final StreamingHttpRequest request) {
                return TimeoutHttpRequesterFilter.this.request(delegate(), strategy, request);
            }
        };
    }

    @Override
    public HttpExecutionStrategy influenceStrategy(final HttpExecutionStrategy strategy) {
        // No influence since we do not block.
        return strategy;
    }

    @FunctionalInterface
    public interface TimeoutFromRequest extends Function<HttpRequestMetaData, Duration> {

        /**
         * Determine timeout duration, if present, from a request and/or apply default timeout durations.
         *
         * @param request the current request
         * @return The timeout or null for no timeout
         */
        @Override
        @Nullable
        Duration apply(HttpRequestMetaData request);
    }

    /**
     * Returns a function which uses {@link #useContextDeadlineOrDefault(Duration) useContextDeadlineOrDefault()} with
     * the provided default duration to determine the timeout duration to be used for a provided request.
     *
     * @param duration default timeout duration or null for no timeout
     * @return a function to produce a timeout based on the context deadline or specified default
     */
    public static TimeoutFromRequest useDefaultTimeout(@Nullable Duration duration) {
        // the request is not considered
        return (req) -> useContextDeadlineOrDefault(duration);
    }

    /**
     * Returns timeout duration calculated based on the remaining time until the deadline in the context or, if absent,
     * the provided default. The timeout, if any, will be added to the context as a deadline for additional client
     * requests initiated within this context. The contents of the request are not used by this implementation.
     *
     * <p><strong>Note:</strong>This implementation assumes that the {@link AsyncContext} has not been
     * {@link AsyncContext#disable() disabled}, but will always use the default duration if it has been disabled.
     *
     * @param defaultDuration default timeout duration or null for no timeout
     * @return a timeout based on the context deadline or specified default (which may be null)
     */
    public static @Nullable Duration useContextDeadlineOrDefault(@Nullable Duration defaultDuration) {
        Instant deadline = AsyncContext.get(HTTP_DEADLINE_KEY);

        if (null != deadline) {
            return Duration.between(Instant.now(), deadline);
        } else if (null != defaultDuration) {
            // Convert it to a deadline so that if reused for a new client request the timeout will reflect actual
            // remaining time.
            try {
                AsyncContext.put(HTTP_DEADLINE_KEY, Instant.now().plus(defaultDuration));
            } catch (UnsupportedOperationException ignored) {
                // ignored
            }
        }

        return defaultDuration;
    }
}
