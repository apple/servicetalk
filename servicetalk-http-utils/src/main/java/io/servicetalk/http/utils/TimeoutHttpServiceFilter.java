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

import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpExecutionStrategyInfluencer;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.http.api.StreamingHttpServiceFilter;
import io.servicetalk.http.api.StreamingHttpServiceFilterFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.TimeoutException;
import javax.annotation.Nullable;

import static io.servicetalk.http.utils.TimeoutHttpRequesterFilter.simpleDurationTimeout;

/**
 * A {@link StreamingHttpServiceFilter} that adds support for request/response timeouts.
 *
 * <p>The order with which this filter is applied may be highly significant. For example, appending it before a retry
 * filter would have different results than applying it after the retry filter; timeout would apply for all retries vs
 * timeout per retry.
 */
public final class TimeoutHttpServiceFilter
        implements StreamingHttpServiceFilterFactory, HttpExecutionStrategyInfluencer {

    /**
     * Establishes the timeout for a given request
     */
    private final TimeoutFromRequest timeoutForRequest;
    /**
     * If true then timeout is for full request/response transaction otherwise only the response metadata must complete
     * before the timeout.
     */
    private final boolean fullRequestResponse;
    @Nullable
    private final Executor timeoutExecutor;

    /**
     * Construct a new instance.
     *
     * @param duration the timeout {@link Duration}
     */
    public TimeoutHttpServiceFilter(Duration duration) {
        this(simpleDurationTimeout(duration));
    }

    /**
     * Construct a new instance.
     *
     * @param duration the timeout {@link Duration}
     * @param timeoutExecutor the {@link Executor} to use for managing the timer notifications
     */
    public TimeoutHttpServiceFilter(Duration duration,
                                    Executor timeoutExecutor) {
        this(simpleDurationTimeout(duration), timeoutExecutor);
    }

    /**
     * Construct a new instance.
     *
     * @param timeoutForRequest function for extracting timeout from request which may also determine the timeout using
     * other sources. If no timeout is to be applied then the function should return null.
     */
    public TimeoutHttpServiceFilter(TimeoutFromRequest timeoutForRequest) {
        this(timeoutForRequest, true);
    }

    /**
     * Construct a new instance.
     *
     * @param timeoutForRequest function for extracting timeout from request which may also determine the timeout using
     * other sources. If no timeout is to be applied then the function should return null.
     * @param timeoutExecutor the {@link Executor} to use for managing the timer notifications
     */
    public TimeoutHttpServiceFilter(TimeoutFromRequest timeoutForRequest,
                                    Executor timeoutExecutor) {
        this(timeoutForRequest, true, timeoutExecutor);
    }

    /**
     * Construct a new instance.
     *
     * @param timeoutForRequest function for extracting timeout from request which may also determine the timeout using
     * other sources. If no timeout is to be applied then the function should return null.
     * @param fullRequestResponse if {@code true} then timeout is for full request/response transaction otherwise only
     * the response metadata must complete before the timeout.
     */
    public TimeoutHttpServiceFilter(TimeoutFromRequest timeoutForRequest,
                                    boolean fullRequestResponse) {
        this.timeoutForRequest = Objects.requireNonNull(timeoutForRequest, "timeoutForRequest");
        this.fullRequestResponse = fullRequestResponse;
        this.timeoutExecutor = null;
    }

    /**
     * Construct a new instance.
     *
     * @param timeoutForRequest function for extracting timeout from request which may also determine the timeout using
     * other sources. If no timeout is to be applied then the function should return null.
     * @param fullRequestResponse if {@code true} then timeout is for full request/response transaction otherwise only
     * the response metadata must complete before the timeout.
     * @param timeoutExecutor the {@link Executor} to use for managing the timer notifications
     */
    public TimeoutHttpServiceFilter(TimeoutFromRequest timeoutForRequest,
                                    boolean fullRequestResponse,
                                    Executor timeoutExecutor) {
        this.timeoutForRequest = Objects.requireNonNull(timeoutForRequest, "timeoutForRequest");
        this.fullRequestResponse = fullRequestResponse;
        this.timeoutExecutor = Objects.requireNonNull(timeoutExecutor, "timeoutExecutor");
    }

    @Override
    public StreamingHttpServiceFilter create(final StreamingHttpService service) {
        return new StreamingHttpServiceFilter(service) {
            @Override
            public Single<StreamingHttpResponse> handle(final HttpServiceContext ctx,
                                                        final StreamingHttpRequest request,
                                                        final StreamingHttpResponseFactory responseFactory) {
                return TimeoutHttpServiceFilter.this.handle(delegate(), ctx, request, responseFactory);
            }
        };
    }

    @Override
    public HttpExecutionStrategy influenceStrategy(final HttpExecutionStrategy strategy) {
        return timeoutForRequest.influenceStrategy(strategy);
    }

    private Single<StreamingHttpResponse> handle(final StreamingHttpService delegate,
                                                 final HttpServiceContext ctx,
                                                 final StreamingHttpRequest request,
                                                 final StreamingHttpResponseFactory responseFactory) {
        return Single.defer(() -> {
            Duration timeout = timeoutForRequest.apply(request);
            Single<StreamingHttpResponse> response;
            if (null != timeout && Duration.ZERO.compareTo(timeout) >= 0) {
                response = Single.failed(new TimeoutException("negative timeout of " + timeout.toMillis() + "ms"));
            } else {
                response = delegate.handle(ctx, request, responseFactory);

                if (null != timeout) {
                    Single<StreamingHttpResponse> timeoutResponse = timeoutExecutor == null ?
                            response.timeout(timeout) : response.timeout(timeout, timeoutExecutor);

                    if (fullRequestResponse) {
                        Instant deadline = Instant.now().plus(timeout);
                        response = timeoutResponse.map(resp -> resp.transformMessageBody(body ->
                                Publisher.defer(() -> {
                                    Duration remaining = Duration.between(Instant.now(), deadline);
                                    return (Duration.ZERO.compareTo(remaining) <= 0 ?
                                            Publisher.failed(
                                                    new TimeoutException("timeout after " + timeout.toMillis() + "ms"))
                                            : (null == timeoutExecutor ?
                                            body.timeoutTerminal(remaining)
                                            : body.timeoutTerminal(remaining, timeoutExecutor))
                                    ).subscribeShareContext();
                                })));
                    } else {
                        response = timeoutResponse;
                    }
                }
            }

            return response.subscribeShareContext();
        });
    }
}
