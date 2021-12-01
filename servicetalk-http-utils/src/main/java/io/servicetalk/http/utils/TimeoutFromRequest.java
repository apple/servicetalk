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
import io.servicetalk.http.api.HttpExecutionStrategies;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.transport.api.ExecutionStrategyInfluencer;

import java.time.Duration;
import java.util.function.BiFunction;
import java.util.function.Function;
import javax.annotation.Nullable;

import static java.lang.System.nanoTime;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

/**
 * A function to determine the appropriate timeout to be used for a given {@link HttpRequestMetaData HTTP request}.
 * The result is a {@link Duration} which may be null if no timeout is to be applied. If the function blocks then
 * {@link #requiredOffloads()} } should alter the execution strategy as required.
 */
public interface TimeoutFromRequest extends
            Function<HttpRequestMetaData, Duration>, ExecutionStrategyInfluencer<HttpExecutionStrategy> {

    /**
     * Determine timeout duration, if present, from a request and/or apply default timeout durations.
     *
     * @param request the current request
     * @return The timeout or null for no timeout
     * @deprecated Use {@link #apply(HttpRequestMetaData, TimeSource)}. This method will be removed in a future release.
     */
    @Override
    @Deprecated
    @Nullable
    default Duration apply(HttpRequestMetaData request) {
        return apply(request, (unit) -> unit.convert(nanoTime(), NANOSECONDS));
    }

    /**
     * Determine timeout duration, if present, from a request and/or apply default timeout durations.
     *
     * @param request the current request
     * @param timeSource {@link TimeSource} for calculating the timeout
     * @return The timeout or null for no timeout
     */
    @Nullable
    Duration apply(HttpRequestMetaData request, TimeSource timeSource);

    /**
     * {@inheritDoc}
     *
     * <p>If it is known that apply() cannot block then override to return strategy as provided.
     */
    @Override
    default HttpExecutionStrategy requiredOffloads() {
        // The effects of apply() are unknown so the default "safe" strategy should be used.
        return HttpExecutionStrategies.offloadAll();
    }

    /**
     * Create a {@link TimeoutFromRequest} instance.
     *
     * @param function a function for converting request headers to a duration
     * @param requiredStrategy execution strategy required by the function.
     * @return {@link TimeoutFromRequest} instance which applies the provided function and requires the specified
     * strategy.
     */
    static TimeoutFromRequest toTimeoutFromRequest(final BiFunction<HttpRequestMetaData, TimeSource, Duration> function,
                                                   final HttpExecutionStrategy requiredStrategy) {
        return new TimeoutFromRequest() {
            @Nullable
            @Override
            public Duration apply(final HttpRequestMetaData request, final TimeSource timeSource) {
                return function.apply(request, timeSource);
            }

            @Override
            public HttpExecutionStrategy requiredOffloads() {
                return requiredStrategy;
            }
        };
    }
}
