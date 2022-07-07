/*
 * Copyright © 2019, 2021-2022 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.api.TerminalSignalConsumer;
import io.servicetalk.http.api.FilterableStreamingHttpClient;
import io.servicetalk.http.api.FilterableStreamingHttpConnection;
import io.servicetalk.http.api.HttpExecutionContext;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.http.api.StreamingHttpClientFilter;
import io.servicetalk.http.api.StreamingHttpClientFilterFactory;
import io.servicetalk.http.api.StreamingHttpConnectionFilter;
import io.servicetalk.http.api.StreamingHttpConnectionFilterFactory;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequester;
import io.servicetalk.http.api.StreamingHttpResponse;

import java.time.Duration;
import java.util.function.BiFunction;

/**
 * A filter to enable timeouts for HTTP requests on the client-side.
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
public final class TimeoutHttpRequesterFilter extends AbstractTimeoutHttpFilter
        implements StreamingHttpClientFilterFactory, StreamingHttpConnectionFilterFactory {

    /**
     * Creates a new instance which requires only that the response metadata be received before the timeout.
     *
     * @param duration the timeout {@link Duration}
     */
    public TimeoutHttpRequesterFilter(final Duration duration) {
        this(new FixedDuration(duration), false);
    }

    /**
     * Creates a new instance which requires only that the response metadata be received before the timeout.
     *
     * @param duration the timeout {@link Duration}
     * @param timeoutExecutor the {@link Executor} to use for managing the timer notifications
     */
    public TimeoutHttpRequesterFilter(final Duration duration, final Executor timeoutExecutor) {
        this(new FixedDuration(duration), false, timeoutExecutor);
    }

    /**
     * Creates a new instance.
     *
     * @param duration the timeout {@link Duration}
     * @param fullRequestResponse if {@code true} then timeout is for full request/response transaction otherwise only
     * the response metadata must arrive before the timeout
     */
    public TimeoutHttpRequesterFilter(final Duration duration, final boolean fullRequestResponse) {
        this(new FixedDuration(duration), fullRequestResponse);
    }

    /**
     * Creates a new instance.
     *
     * @param duration the timeout {@link Duration}
     * @param fullRequestResponse if {@code true} then timeout is for full request/response transaction otherwise only
     * the response metadata must arrive before the timeout
     * @param timeoutExecutor the {@link Executor} to use for managing the timer notifications
     */
    public TimeoutHttpRequesterFilter(final Duration duration,
                                      final boolean fullRequestResponse,
                                      final Executor timeoutExecutor) {
        this(new FixedDuration(duration), fullRequestResponse, timeoutExecutor);
    }

    /**
     * Creates a new instance.
     *
     * @param timeoutForRequest function for extracting timeout from request which may also determine the timeout using
     * other sources. If no timeout is to be applied then the function should return {@code null}
     * @param fullRequestResponse if {@code true} then timeout is for full request/response transaction otherwise only
     * the response metadata must arrive before the timeout
     * @deprecated Use {@link TimeoutHttpRequesterFilter#TimeoutHttpRequesterFilter(BiFunction, boolean)}.
     */
    @Deprecated
    public TimeoutHttpRequesterFilter(final TimeoutFromRequest timeoutForRequest, final boolean fullRequestResponse) {
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
    public TimeoutHttpRequesterFilter(final BiFunction<HttpRequestMetaData, TimeSource, Duration> timeoutForRequest,
                                      final boolean fullRequestResponse) {
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
     * @deprecated Use {@link TimeoutHttpRequesterFilter#TimeoutHttpRequesterFilter(BiFunction, boolean, Executor)}.
     */
    @Deprecated
    public TimeoutHttpRequesterFilter(final TimeoutFromRequest timeoutForRequest,
                                      final boolean fullRequestResponse,
                                      final Executor timeoutExecutor) {
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
    public TimeoutHttpRequesterFilter(final BiFunction<HttpRequestMetaData, TimeSource, Duration> timeoutForRequest,
                                      final boolean fullRequestResponse,
                                      final Executor timeoutExecutor) {
        super(timeoutForRequest, fullRequestResponse, timeoutExecutor);
    }

    @Override
    public StreamingHttpClientFilter create(final FilterableStreamingHttpClient client) {
        return new StreamingHttpClientFilter(client) {
            @Override
            protected Single<StreamingHttpResponse> request(final StreamingHttpRequester delegate,
                                                            final StreamingHttpRequest request) {
                return TimeoutHttpRequesterFilter.this.withTimeout(request, delegate::request, executionContext());
            }
        };
    }

    @Override
    public StreamingHttpConnectionFilter create(final FilterableStreamingHttpConnection connection) {
        return new StreamingHttpConnectionFilter(connection) {
            @Override
            public Single<StreamingHttpResponse> request(final StreamingHttpRequest request) {
                final FilterableStreamingHttpConnection delegate = delegate();
                return TimeoutHttpRequesterFilter.this.withTimeout(request,
                        delegate.connectionContext().protocol().major() > 1 ? delegate::request :
                                r -> delegate.request(r).liftSync(new BeforeFinallyHttpOperator(
                                        new TerminalSignalConsumer() {
                                            @Override
                                            public void onComplete() {
                                                // noop
                                            }

                                            @Override
                                            public void onError(final Throwable throwable) {
                                                // noop
                                            }

                                            @Override
                                            public void cancel() {
                                                // In alignment with cancellation processing in
                                                // LoadBalancedStreamingHttpClient, we need to close the HTTP/1.x
                                                // connection when this filter is applied at connection level.
                                                // Otherwise, a connection will be marked as "free" and can be selected
                                                // for another request, racing with closure.
                                                delegate.closeAsync().subscribe();
                                                // Not necessary to do anything for HTTP/2 at this level because
                                                // NettyChannelPublisher#cancel0 will be scheduled on the EventLoop
                                                // prior marking the request as finished. Therefore, any new attempt to
                                                // open a stream on the same h2-connection will see the current stream
                                                // as already cancelled and won't result in "max-concurrent-streams"
                                                // error.
                                            }
                                        })), executionContext());
            }
        };
    }
}
