/*
 * Copyright © 2022 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.grpc.api;

import io.servicetalk.concurrent.TimeSource;
import io.servicetalk.concurrent.api.AsyncContext;
import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.http.api.StreamingHttpClientFilterFactory;
import io.servicetalk.http.api.StreamingHttpServiceFilterFactory;
import io.servicetalk.http.utils.TimeoutHttpRequesterFilter;
import io.servicetalk.http.utils.TimeoutHttpServiceFilter;

import java.time.Duration;
import java.util.function.BiFunction;
import javax.annotation.Nullable;

import static io.servicetalk.grpc.internal.DeadlineUtils.GRPC_DEADLINE_KEY;
import static io.servicetalk.grpc.internal.DeadlineUtils.readTimeoutHeader;
import static io.servicetalk.utils.internal.DurationUtils.ensurePositive;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

/**
 * Utility filters for gRPC.
 */
public final class GrpcFilters {
    private GrpcFilters() {
    }

    /**
     * Create a {@link StreamingHttpClientFilterFactory} that enforces the
     * <a href="https://github.com/grpc/grpc/blob/master/doc/PROTOCOL-HTTP2.md#requests">Timeout</a>
     * <a href="https://grpc.io/blog/deadlines">deadline propagation</a>.
     * @return {@code this}.
     */
    public static StreamingHttpClientFilterFactory newGrpcDeadlineClientFilterFactory() {
        return new TimeoutHttpRequesterFilter((request, timeSource) -> readTimeoutHeader(request), true);
    }

    /**
     * Create a {@link StreamingHttpClientFilterFactory} that enforces the
     * <a href="https://github.com/grpc/grpc/blob/master/doc/PROTOCOL-HTTP2.md#requests">Timeout</a>
     * <a href="https://grpc.io/blog/deadlines">deadline propagation</a>.
     * @param defaultTimeout The default timeout to apply if not otherwise specified for the request, or {@code null}
     * doesn't apply a timeout if not specified.
     * @return {@code this}.
     */
    public static StreamingHttpServiceFilterFactory newGrpcDeadlineServerFilterFactory(
            @Nullable Duration defaultTimeout) {
        // Use an inner class instead of a lambda to capture the external state (defaultTimeout) for visibility during
        // heap dump analysis.
        return new TimeoutHttpServiceFilter(new GrpcDetermineTimeoutForRequestFunction(defaultTimeout), true);
    }

    private static final class GrpcDetermineTimeoutForRequestFunction
            implements BiFunction<HttpRequestMetaData, TimeSource, Duration> {

        @Nullable
        private final Duration defaultTimeout;

        GrpcDetermineTimeoutForRequestFunction(final @Nullable Duration defaultTimeout) {
            this.defaultTimeout = defaultTimeout == null ? null : ensurePositive(defaultTimeout, "defaultTimeout");
        }

        /**
         * Return the timeout duration extracted from the GRPC timeout HTTP header if present or default timeout.
         *
         * @param request The HTTP request to be used as source of the GRPC timeout header
         * @param timeSource The source of time to calculate the deadline
         * @return The non-negative timeout duration or {@code null} if not identified
         */
        @Nullable
        @Override
        public Duration apply(final HttpRequestMetaData request, final TimeSource timeSource) {
            @Nullable
            Duration requestTimeout = readTimeoutHeader(request);
            @Nullable
            Duration timeout = null != requestTimeout ? requestTimeout : defaultTimeout;

            if (null != timeout) {
                // Store the timeout in the context as a deadline to be used for any client requests created
                // during the context of handling this request.
                Long deadline = timeSource.currentTime(NANOSECONDS) + timeout.toNanos();
                AsyncContext.put(GRPC_DEADLINE_KEY, deadline);
            }

            return timeout;
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() +
                    "{defaultTimeout=" + defaultTimeout +
                    '}';
        }
    }
}
