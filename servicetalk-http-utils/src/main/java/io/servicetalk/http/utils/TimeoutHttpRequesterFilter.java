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
        this(simpleDurationTimeout(duration), false);
    }

    /**
     * Creates a new instance which requires only that the response metadata be received before the timeout.
     *
     * @param duration the timeout {@link Duration}
     * @param timeoutExecutor the {@link Executor} to use for managing the timer notifications
     */
    public TimeoutHttpRequesterFilter(final Duration duration, final Executor timeoutExecutor) {
        this(simpleDurationTimeout(duration), false, timeoutExecutor);
    }

    /**
     * Creates a new instance.
     *
     * @param duration the timeout {@link Duration}
     * @param fullRequestResponse if true then timeout is for full request/response transaction otherwise only the
     * response metadata must arrive before the timeout.
     */
    public TimeoutHttpRequesterFilter(final Duration duration, final boolean fullRequestResponse) {
        this(simpleDurationTimeout(duration), fullRequestResponse);
    }

    /**
     * Creates a new instance.
     *
     * @param duration the timeout {@link Duration}
     * @param fullRequestResponse if true then timeout is for full request/response transaction otherwise only the
     * response metadata must arrive before the timeout.
     * @param timeoutExecutor the {@link Executor} to use for managing the timer notifications
     */
    public TimeoutHttpRequesterFilter(final Duration duration,
                                      final boolean fullRequestResponse,
                                      final Executor timeoutExecutor) {
        this(simpleDurationTimeout(duration), fullRequestResponse, timeoutExecutor);
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
        return timeoutForRequest.influenceStrategy(strategy);
    }

    /**
     * Returns a function which returns the provided default duration as the timeout duration to be used for any
     * request.
     *
     * @param duration timeout duration or null for no timeout
     * @return a function to produce a timeout using specified duration
     */
    static TimeoutFromRequest simpleDurationTimeout(@Nullable Duration duration) {
        return new TimeoutFromRequest() {
            @Nullable
            @Override
            public Duration apply(final HttpRequestMetaData request) {
                // the request is not considered
                return duration;
            }

            @Override
            public HttpExecutionStrategy influenceStrategy(final HttpExecutionStrategy strategy) {
                // No influence since we do not block.
                return strategy;
            }
        };
    }

    /**
     * A function to determine the appropriate timeout to be used for a given {@link HttpRequestMetaData HTTP request}.
     * The result is a {@link Duration} which may be null if no timeout is to be applied. If the function blocks then
     * {@link #influenceStrategy(HttpExecutionStrategy)} should alter the execution strategy as required.
     */
    public interface TimeoutFromRequest extends Function<HttpRequestMetaData, Duration>,
                                                HttpExecutionStrategyInfluencer {

        /**
         * Determine timeout duration, if present, from a request and/or apply default timeout durations.
         *
         * @param request the current request
         * @return The timeout or null for no timeout
         */
        @Override
        @Nullable
        Duration apply(HttpRequestMetaData request);

        @Override
        HttpExecutionStrategy influenceStrategy(HttpExecutionStrategy strategy);
    }
}
