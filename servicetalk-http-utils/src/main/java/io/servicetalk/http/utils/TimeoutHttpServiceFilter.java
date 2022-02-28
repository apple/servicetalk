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

import io.servicetalk.concurrent.TimeSource;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.HttpExecutionContext;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.http.api.StreamingHttpServiceFilter;
import io.servicetalk.http.api.StreamingHttpServiceFilterFactory;

import java.time.Duration;
import java.util.function.BiFunction;

/**
 * A filter to enable timeouts for HTTP requests on the server-side.
 *
 * <p>The timeout applies either the response metadata (headers) completion or the complete reception of the response
 * payload body and optional trailers.
 *
 * <p>If no executor is specified at construction an executor from {@link HttpExecutionContext} associated with the
 * client or connection will be used. If the {@link HttpExecutionContext#executionStrategy()} specifies an
 * {@link HttpExecutionStrategy} with offloads then {@link HttpExecutionContext#executor()} will be used and if no
 * offloads are specified then {@link HttpExecutionContext#ioExecutor()} will be used.
 *
 * <p>The order with which this filter is applied may be highly significant. For example, appending it before a retry
 * filter would have different results than applying it after the retry filter; timeout would apply for all retries vs
 * timeout per retry.
 */
public final class TimeoutHttpServiceFilter extends AbstractTimeoutHttpFilter
        implements StreamingHttpServiceFilterFactory {

    /**
     * Creates a new instance which requires only that the response metadata be received before the timeout.
     *
     * @param duration the timeout {@link Duration}
     */
    public TimeoutHttpServiceFilter(Duration duration) {
        super(new FixedDuration(duration), false);
    }

    /**
     * Creates a new instance which requires only that the response metadata be received before the timeout.
     *
     * @param duration the timeout {@link Duration}
     * @param timeoutExecutor the {@link Executor} to use for managing the timer notifications
     */
    public TimeoutHttpServiceFilter(Duration duration, Executor timeoutExecutor) {
        super(new FixedDuration(duration), false, timeoutExecutor);
    }

    /**
     * Creates a new instance.
     *
     * @param duration the timeout {@link Duration}
     * @param fullRequestResponse if {@code true} then timeout is for full request/response transaction otherwise only
     * the response metadata must arrive before the timeout
     */
    public TimeoutHttpServiceFilter(Duration duration, boolean fullRequestResponse) {
        super(new FixedDuration(duration), fullRequestResponse);
    }

    /**
     * Creates a new instance.
     *
     * @param duration the timeout {@link Duration}
     * @param fullRequestResponse if {@code true} then timeout is for full request/response transaction otherwise only
     * the response metadata must arrive before the timeout
     * @param timeoutExecutor the {@link Executor} to use for managing the timer notifications
     */
    public TimeoutHttpServiceFilter(Duration duration, boolean fullRequestResponse, Executor timeoutExecutor) {
        super(new FixedDuration(duration), fullRequestResponse, timeoutExecutor);
    }

    /**
     * Creates a new instance.
     *
     * @param timeoutForRequest function for extracting timeout from request which may also determine the timeout using
     * other sources. If no timeout is to be applied then the function should return {@code null}
     * @param fullRequestResponse if {@code true} then timeout is for full request/response transaction otherwise only
     * the response metadata must arrive before the timeout
     * @deprecated Use {@link TimeoutHttpServiceFilter#TimeoutHttpServiceFilter(BiFunction, boolean)}.
     */
    @Deprecated
    public TimeoutHttpServiceFilter(TimeoutFromRequest timeoutForRequest, boolean fullRequestResponse) {
        super(timeoutForRequest, fullRequestResponse);
    }

    /**
     * Creates a new instance.
     *
     * @param timeoutForRequest function for extracting timeout from request which may also determine the timeout using
     * other sources. If no timeout is to be applied then the function should return {@code null}
     * @param fullRequestResponse if {@code true} then timeout is for full request/response transaction otherwise only
     * the response metadata must arrive before the timeout
     */
    public TimeoutHttpServiceFilter(BiFunction<HttpRequestMetaData, TimeSource, Duration> timeoutForRequest,
                                    boolean fullRequestResponse) {
        super(timeoutForRequest, fullRequestResponse);
    }

    /**
     * Creates a new instance.
     *
     * @param timeoutForRequest function for extracting timeout from request which may also determine the timeout using
     * other sources. If no timeout is to be applied then the function should return {@code null}
     * @param fullRequestResponse if {@code true} then timeout is for full request/response transaction otherwise only
     * the response metadata must arrive before the timeout
     * @param timeoutExecutor the {@link Executor} to use for managing the timer notifications
     * @deprecated Use {@link TimeoutHttpServiceFilter#TimeoutHttpServiceFilter(BiFunction, boolean, Executor)}.
     */
    @Deprecated
    public TimeoutHttpServiceFilter(TimeoutFromRequest timeoutForRequest,
                                    boolean fullRequestResponse,
                                    Executor timeoutExecutor) {
        super(timeoutForRequest, fullRequestResponse, timeoutExecutor);
    }

    /**
     * Creates a new instance.
     *
     * @param timeoutForRequest function for extracting timeout from request which may also determine the timeout using
     * other sources. If no timeout is to be applied then the function should return {@code null}
     * @param fullRequestResponse if {@code true} then timeout is for full request/response transaction otherwise only
     * the response metadata must arrive before the timeout
     * @param timeoutExecutor the {@link Executor} to use for managing the timer notifications
     */
    public TimeoutHttpServiceFilter(BiFunction<HttpRequestMetaData, TimeSource, Duration> timeoutForRequest,
                                    boolean fullRequestResponse,
                                    Executor timeoutExecutor) {
        super(timeoutForRequest, fullRequestResponse, timeoutExecutor);
    }

    @Override
    public StreamingHttpServiceFilter create(final StreamingHttpService service) {
        return new StreamingHttpServiceFilter(service) {
            @Override
            public Single<StreamingHttpResponse> handle(final HttpServiceContext ctx,
                                                        final StreamingHttpRequest request,
                                                        final StreamingHttpResponseFactory responseFactory) {
                HttpExecutionContext executionContext = ctx.executionContext();
                return TimeoutHttpServiceFilter.this.withTimeout(request,
                        r -> delegate().handle(ctx, r, responseFactory),
                        executionContext.executionStrategy().hasOffloads() ?
                                executionContext.executor() : executionContext.ioExecutor());
            }
        };
    }
}
