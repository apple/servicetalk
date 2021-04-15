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
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpExecutionStrategyInfluencer;
import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;

import java.time.Duration;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Publisher.defer;
import static io.servicetalk.concurrent.api.Publisher.failed;
import static io.servicetalk.utils.internal.DurationUtils.ensurePositive;
import static io.servicetalk.utils.internal.DurationUtils.isPositive;
import static java.time.Duration.ofNanos;
import static java.util.Objects.requireNonNull;

abstract class AbstractTimeoutHttpFilter implements HttpExecutionStrategyInfluencer {
    /**
     * Establishes the timeout for a given request.
     */
    private final TimeoutFromRequest timeoutForRequest;

    /**
     * If {@code true} then timeout is for full request/response transaction otherwise only the response metadata must
     * complete before the timeout.
     */
    private final boolean fullRequestResponse;

    @Nullable
    private final Executor timeoutExecutor;

    AbstractTimeoutHttpFilter(final TimeoutFromRequest timeoutForRequest, final boolean fullRequestResponse) {
        this.timeoutForRequest = requireNonNull(timeoutForRequest, "timeoutForRequest");
        this.fullRequestResponse = fullRequestResponse;
        this.timeoutExecutor = null;
    }

    AbstractTimeoutHttpFilter(final TimeoutFromRequest timeoutForRequest, final boolean fullRequestResponse,
                              final Executor timeoutExecutor) {
        this.timeoutForRequest = requireNonNull(timeoutForRequest, "timeoutForRequest");
        this.fullRequestResponse = fullRequestResponse;
        this.timeoutExecutor = requireNonNull(timeoutExecutor, "timeoutExecutor");
    }

    @Override
    public final HttpExecutionStrategy influenceStrategy(final HttpExecutionStrategy strategy) {
        return timeoutForRequest.influenceStrategy(strategy);
    }

    final Single<StreamingHttpResponse> withTimeout(final StreamingHttpRequest request,
                final Function<StreamingHttpRequest, Single<StreamingHttpResponse>> responseFunction) {

        return Single.defer(() -> {
            final Duration timeout = timeoutForRequest.apply(request);
            if (null != timeout && !isPositive(timeout)) {
                return Single.failed(new TimeoutException("non-positive timeout of " + timeout.toMillis() + "ms"));
            }

            Single<StreamingHttpResponse> response = responseFunction.apply(request);
            if (null != timeout) {
                final Single<StreamingHttpResponse> timeoutResponse = timeoutExecutor == null ?
                        response.timeout(timeout) : response.timeout(timeout, timeoutExecutor);

                if (fullRequestResponse) {
                    final long deadline = System.nanoTime() + timeout.toNanos();
                    response = timeoutResponse.map(resp -> resp.transformMessageBody(body -> defer(() -> {
                        final Duration remaining = ofNanos(deadline - System.nanoTime());
                        if (isPositive(remaining)) {
                            return (timeoutExecutor == null ?
                                    body.timeoutTerminal(remaining) : body.timeoutTerminal(remaining, timeoutExecutor))
                                    .subscribeShareContext();
                        }
                        return failed(new TimeoutException("timeout after " + timeout.toMillis() + "ms"));
                    })));
                } else {
                    response = timeoutResponse;
                }
            }

            return response.subscribeShareContext();
        });
    }

    /**
     * {@link TimeoutFromRequest} implementation which returns the provided default duration as the timeout duration to
     * be used for any request.
     */
    static final class FixedDuration implements TimeoutFromRequest {

        private final Duration duration;

        FixedDuration(final Duration duration) {
            this.duration = ensurePositive(duration, "duration");
        }

        @Override
        public Duration apply(final HttpRequestMetaData request) {
            return duration;
        }

        @Override
        public HttpExecutionStrategy influenceStrategy(final HttpExecutionStrategy strategy) {
            // No influence since we do not block.
            return strategy;
        }
    }
}
