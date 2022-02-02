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

import io.servicetalk.concurrent.Executor;
import io.servicetalk.concurrent.TimeSource;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.HttpExecutionStrategies;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpExecutionStrategyInfluencer;
import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;

import java.time.Duration;
import java.util.concurrent.TimeoutException;
import java.util.function.BiFunction;
import java.util.function.Function;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Publisher.defer;
import static io.servicetalk.utils.internal.DurationUtils.ensurePositive;
import static java.time.Duration.ofNanos;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

abstract class AbstractTimeoutHttpFilter implements HttpExecutionStrategyInfluencer {
    /**
     * Establishes the timeout for a given request.
     */
    private final BiFunction<HttpRequestMetaData, TimeSource, Duration> timeoutForRequest;

    /**
     * If {@code true} then timeout is for full request/response transaction otherwise only the response metadata must
     * complete before the timeout.
     */
    private final boolean fullRequestResponse;

    /**
     * Optional executor that will be used for scheduling and execution of timeout actions. If unspecified, the
     */
    @Nullable
    private final Executor timeoutExecutor;

    AbstractTimeoutHttpFilter(final TimeoutFromRequest timeoutForRequest, final boolean fullRequestResponse) {
        requireNonNull(timeoutForRequest, "timeoutForRequest");
        this.timeoutForRequest = (request, timeSource) -> timeoutForRequest.apply(request);
        this.fullRequestResponse = fullRequestResponse;
        this.timeoutExecutor = null;
    }

    AbstractTimeoutHttpFilter(final BiFunction<HttpRequestMetaData, TimeSource, Duration> timeoutForRequest,
                              final boolean fullRequestResponse) {
        this.timeoutForRequest = requireNonNull(timeoutForRequest, "timeoutForRequest");
        this.fullRequestResponse = fullRequestResponse;
        this.timeoutExecutor = null;
    }

    AbstractTimeoutHttpFilter(final TimeoutFromRequest timeoutForRequest, final boolean fullRequestResponse,
                              final Executor timeoutExecutor) {
        requireNonNull(timeoutForRequest, "timeoutForRequest");
        this.timeoutForRequest = (request, timeSource) -> timeoutForRequest.apply(request);
        this.fullRequestResponse = fullRequestResponse;
        this.timeoutExecutor = requireNonNull(timeoutExecutor, "timeoutExecutor");
    }

    AbstractTimeoutHttpFilter(final BiFunction<HttpRequestMetaData, TimeSource, Duration> timeoutForRequest,
                              final boolean fullRequestResponse,
                              final Executor timeoutExecutor) {
        this.timeoutForRequest = requireNonNull(timeoutForRequest, "timeoutForRequest");
        this.fullRequestResponse = fullRequestResponse;
        this.timeoutExecutor = requireNonNull(timeoutExecutor, "timeoutExecutor");
    }

    @Override
    public final HttpExecutionStrategy requiredOffloads() {
        return HttpExecutionStrategies.offloadNone();
    }

    /**
     * Returns the response single for the provided request which will be completed within the specified timeout or
     * generate an error with a timeout exception. The timer begins when the {@link Single} is subscribed. The timeout
     * action, if it occurs, will execute on the filter's chosen timeout executor, or if none is specified, the executor
     * from the request or connection context or, if neither are specified, the global executor.
     *
     * @param request The request requiring a response.
     * @param responseFunction Function which generates the response.
     * @param contextExecutor Executor from the request/connection context to be used for the timeout terminal signal if
     * no specific timeout executor is defined for filter.
     * @return response single
     */
    final Single<StreamingHttpResponse> withTimeout(final StreamingHttpRequest request,
            final Function<StreamingHttpRequest, Single<StreamingHttpResponse>> responseFunction,
            final Executor contextExecutor) {

        final Executor useForTimeout = null != this.timeoutExecutor ? this.timeoutExecutor : contextExecutor;

        return Single.defer(() -> {
            final Duration timeout = timeoutForRequest.apply(request, useForTimeout);
            Single<StreamingHttpResponse> response = responseFunction.apply(request);
            if (null != timeout) {
                final Single<StreamingHttpResponse> timeoutResponse = response.timeout(timeout, useForTimeout);

                if (fullRequestResponse) {
                    final long deadline = useForTimeout.currentTime(NANOSECONDS) + timeout.toNanos();
                    response = timeoutResponse.map(resp -> resp.transformMessageBody(body -> defer(() -> {
                        final Duration remaining = ofNanos(deadline - useForTimeout.currentTime(NANOSECONDS));
                        return (body.timeoutTerminal(remaining, useForTimeout))
                                .onErrorMap(TimeoutException.class, t ->
                                        new MappedTimeoutException("message body timeout after " + timeout.toMillis() +
                                                "ms", t))
                                .shareContextOnSubscribe();
                    })));
                } else {
                    response = timeoutResponse;
                }
            }

            return response.shareContextOnSubscribe();
        });
    }

    private static final class MappedTimeoutException extends TimeoutException {
        private static final long serialVersionUID = -8230476062001221272L;

        MappedTimeoutException(String message, Throwable cause) {
            super(message);
            initCause(cause);
        }

        @Override
        public Throwable fillInStackTrace() {
            // This is a wrapping exception class that always has an original cause and does not require stack trace.
            return this;
        }
    }

    /**
     * {@link BiFunction}&lt;{@link HttpRequestMetaData}, {@link TimeSource}, {@link Duration}&gt;
     * implementation which returns the provided default duration as the timeout duration to
     * be used for any request.
     */
    static final class FixedDuration implements BiFunction<HttpRequestMetaData, TimeSource, Duration> {

        private final Duration duration;

        FixedDuration(final Duration duration) {
            this.duration = ensurePositive(duration, "duration");
        }

        @Override
        public Duration apply(final HttpRequestMetaData request, final TimeSource timeSource) {
            return duration;
        }
    }
}
