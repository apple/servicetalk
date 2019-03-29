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
import io.servicetalk.http.api.StreamingHttpClientFilter;
import io.servicetalk.http.api.StreamingHttpClientFilterFactory;
import io.servicetalk.http.api.StreamingHttpConnectionFilter;
import io.servicetalk.http.api.StreamingHttpConnectionFilterFactory;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequester;
import io.servicetalk.http.api.StreamingHttpResponse;

import java.time.Duration;
import javax.annotation.Nullable;

import static java.util.Objects.requireNonNull;

/**
 * A filter to enable timeouts for HTTP requests.
 */
public final class TimeoutStreamingHttpRequesterFilter implements StreamingHttpClientFilterFactory,
                                                                  StreamingHttpConnectionFilterFactory {
    private final Duration duration;
    @Nullable
    private final Executor timeoutExecutor;

    /**
     * Creates a new instance.
     *
     * @param duration the timeout {@link Duration}
     */
    public TimeoutStreamingHttpRequesterFilter(final Duration duration) {
        this.duration = duration;
        this.timeoutExecutor = null;
    }

    /**
     * Creates a new instance.
     *
     * @param duration the timeout {@link Duration}
     * @param timeoutExecutor the {@link Executor} to use for managing the timer notifications
     */
    public TimeoutStreamingHttpRequesterFilter(final Duration duration, final Executor timeoutExecutor) {
        this.duration = duration;
        this.timeoutExecutor = requireNonNull(timeoutExecutor);
    }

    private Single<StreamingHttpResponse> request(final StreamingHttpRequester delegate,
                                                  final HttpExecutionStrategy strategy,
                                                  final StreamingHttpRequest request) {
        return timeoutExecutor != null ? delegate.request(strategy, request).idleTimeout(duration, timeoutExecutor) :
                delegate.request(strategy, request).idleTimeout(duration);
    }

    @Override
    public StreamingHttpClientFilter create(
            final FilterableStreamingHttpClient client,
            final Publisher<Object> lbEvents) {
        return new StreamingHttpClientFilter(client) {
            @Override
            protected Single<StreamingHttpResponse> request(final StreamingHttpRequester delegate,
                                                            final HttpExecutionStrategy strategy,
                                                            final StreamingHttpRequest request) {
                return TimeoutStreamingHttpRequesterFilter.this.request(delegate, strategy, request);
            }

            @Override
            public HttpExecutionStrategy computeExecutionStrategy(HttpExecutionStrategy other) {
                // Since this filter does not have any blocking code, we do not need to alter the effective strategy.
                return delegate().computeExecutionStrategy(other);
            }
        };
    }

    @Override
    public StreamingHttpConnectionFilter create(final FilterableStreamingHttpConnection connection) {
        return new StreamingHttpConnectionFilter(connection) {
            @Override
            public Single<StreamingHttpResponse> request(final HttpExecutionStrategy strategy,
                                                            final StreamingHttpRequest request) {
                return TimeoutStreamingHttpRequesterFilter.this.request(delegate(), strategy, request);
            }

            @Override
            public HttpExecutionStrategy computeExecutionStrategy(HttpExecutionStrategy other) {
                // Since this filter does not have any blocking code, we do not need to alter the effective strategy.
                return delegate().computeExecutionStrategy(other);
            }
        };
    }
}
