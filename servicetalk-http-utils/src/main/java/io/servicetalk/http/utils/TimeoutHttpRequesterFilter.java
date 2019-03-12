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
import io.servicetalk.http.api.HttpClientFilterFactory;
import io.servicetalk.http.api.HttpConnectionFilterFactory;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.StreamingHttpClientFilter;
import io.servicetalk.http.api.StreamingHttpConnectionFilter;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequestFunction;
import io.servicetalk.http.api.StreamingHttpResponse;

import java.time.Duration;
import javax.annotation.Nullable;

import static java.util.Objects.requireNonNull;

/**
 * A filter to enable timeouts for HTTP requests.
 */
public final class TimeoutHttpRequesterFilter implements HttpClientFilterFactory, HttpConnectionFilterFactory {
    private final Duration duration;
    @Nullable
    private final Executor timeoutExecutor;

    private TimeoutHttpRequesterFilter(final Duration duration, @Nullable final Executor timeoutExecutor) {
        this.duration = duration;
        this.timeoutExecutor = timeoutExecutor;
    }

    private Single<StreamingHttpResponse> request(final StreamingHttpRequestFunction delegate,
                                                  final HttpExecutionStrategy strategy,
                                                  final StreamingHttpRequest request) {
        return timeoutExecutor != null ? delegate.request(strategy, request).timeout(duration, timeoutExecutor) :
                delegate.request(strategy, request).timeout(duration);
    }

    @Override
    public StreamingHttpClientFilter create(final StreamingHttpClientFilter client, final Publisher<Object> lbEvents) {
        return new StreamingHttpClientFilter(client) {
            @Override
            protected Single<StreamingHttpResponse> request(final StreamingHttpRequestFunction delegate,
                                                            final HttpExecutionStrategy strategy,
                                                            final StreamingHttpRequest request) {
                return TimeoutHttpRequesterFilter.this.request(delegate, strategy, request);
            }

            @Override
            protected HttpExecutionStrategy mergeForEffectiveStrategy(final HttpExecutionStrategy mergeWith) {
                // Since this filter does not have any blocking code, we do not need to alter the effective strategy.
                return mergeWith;
            }
        };
    }

    @Override
    public StreamingHttpConnectionFilter create(final StreamingHttpConnectionFilter connection) {
        return new StreamingHttpConnectionFilter(connection) {
            @Override
            protected Single<StreamingHttpResponse> request(final StreamingHttpConnectionFilter delegate,
                                                            final HttpExecutionStrategy strategy,
                                                            final StreamingHttpRequest request) {
                return TimeoutHttpRequesterFilter.this.request(delegate, strategy, request);
            }

            @Override
            protected HttpExecutionStrategy mergeForEffectiveStrategy(final HttpExecutionStrategy mergeWith) {
                // Since this filter does not have any blocking code, we do not need to alter the effective strategy.
                return mergeWith;
            }
        };
    }

    /**
     * A builder for {@link TimeoutHttpRequesterFilter}.
     */
    public static final class Builder {
        @Nullable
        private Executor timeoutExecutor;

        /**
         * Specifies the {@link Executor} to use for managing the timer notifications.
         *
         * @param timeoutExecutor the {@link Executor} to use for managing the timer notifications.
         * @return {@code this}
         */
        public Builder timeoutExecutor(final Executor timeoutExecutor) {
            this.timeoutExecutor = requireNonNull(timeoutExecutor);
            return this;
        }

        /**
         * Creates a new {@link TimeoutHttpRequesterFilter} which adds the provided {@link Duration} as the timeout
         * for requests.
         *
         * @param duration the timeout {@link Duration}
         * @return a new {@link TimeoutHttpRequesterFilter}
         */
        public TimeoutHttpRequesterFilter buildWithTimeout(Duration duration) {
            return new TimeoutHttpRequesterFilter(requireNonNull(duration), timeoutExecutor);
        }
    }
}
