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
import javax.annotation.Nullable;

/**
 * A {@link StreamingHttpServiceFilter} that adds support for request/response timeouts.
 *
 * <p>The order with which this filter is applied may be highly significant. For example, appending it before a retry
 * filter would have different results than applying it after the retry filter; timeout would apply for all retries vs
 * timeout per retry.
 */
public final class TimeoutHttpServiceFilter
        implements StreamingHttpServiceFilterFactory, HttpExecutionStrategyInfluencer {

    private final TimeoutHttpRequesterFilter.TimeoutFromRequest timeoutForRequest;

    @Nullable
    private final Executor timeoutExecutor;

    public TimeoutHttpServiceFilter(TimeoutHttpRequesterFilter.TimeoutFromRequest timeoutForRequest) {
        this.timeoutForRequest = timeoutForRequest;
        this.timeoutExecutor = null;
    }

    public TimeoutHttpServiceFilter(TimeoutHttpRequesterFilter.TimeoutFromRequest timeoutForRequest,
                                    Executor timeoutExecutor) {
        this.timeoutForRequest = timeoutForRequest;
        this.timeoutExecutor = timeoutExecutor;
    }

    @Override
    public StreamingHttpServiceFilter create(final StreamingHttpService service) {
        return new StreamingHttpServiceFilter(service) {
            @Override
            public Single<StreamingHttpResponse> handle(final HttpServiceContext ctx,
                                                        final StreamingHttpRequest request,
                                                        final StreamingHttpResponseFactory responseFactory) {

                return Single.defer(() -> {
                    Duration timeout = timeoutForRequest.apply(request);

                    Single<StreamingHttpResponse> response = delegate().handle(ctx, request, responseFactory);
                    if (null != timeout) {
                        Instant beganAt = Instant.now();
                        response = (null != timeoutExecutor ?
                                response.timeout(timeout, timeoutExecutor) : response.timeout(timeout))
                                .map(resp -> resp.transformMessageBody(body -> Publisher.defer(() -> {
                                    Duration remaining = timeout.minus(Duration.between(beganAt, Instant.now()));
                                    return (null != timeoutExecutor ?
                                            body.timeoutTerminal(remaining, timeoutExecutor)
                                            : body.timeoutTerminal(remaining))
                                            .subscribeShareContext();
                                })));
                    }
                    return response.subscribeShareContext();
                });
            }
        };
    }

    @Override
    public HttpExecutionStrategy influenceStrategy(final HttpExecutionStrategy strategy) {
        // No influence - no blocking
        return strategy;
    }
}
