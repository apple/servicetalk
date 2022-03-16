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
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpExecutionStrategyInfluencer;
import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.transport.api.ExecutionStrategyInfluencer;

import java.time.Duration;
import java.util.function.BiFunction;
import java.util.function.Function;
import javax.annotation.Nullable;

/**
 * A function to determine the appropriate timeout to be used for a given {@link HttpRequestMetaData HTTP request}.
 * The result is a {@link Duration} which may be null if no timeout is to be applied. If the function blocks then
 * {@link #requiredOffloads()} should specify the execution strategy as required.
 * @deprecated In areas which require {@link TimeoutFromRequest} use variants that accept
 * {@link java.util.function.BiFunction}&lt;{@link HttpRequestMetaData}, {@link TimeSource}, {@link Duration}&gt;.
 * E.g.:
 * {@link TimeoutHttpRequesterFilter#TimeoutHttpRequesterFilter(BiFunction, boolean)},
 * {@link TimeoutHttpServiceFilter#TimeoutHttpServiceFilter(BiFunction, boolean)} for filters.
 */
@Deprecated // FIXME: 0.43 - remove deprecated interface
public interface TimeoutFromRequest extends Function<HttpRequestMetaData, Duration>,
                                            HttpExecutionStrategyInfluencer,
                                            ExecutionStrategyInfluencer<HttpExecutionStrategy> {

    /**
     * Determine timeout duration, if present, from a request and/or apply default timeout durations.
     *
     * @param request the current request
     * @return The timeout or null for no timeout
     */
    @Override
    @Nullable
    Duration apply(HttpRequestMetaData request);

    /**
     * {@inheritDoc}
     *
     * <p>If it is known that apply() cannot block then override to return strategy as provided.
     */
    @Override
    default HttpExecutionStrategy influenceStrategy(HttpExecutionStrategy strategy) {
        // The effects of apply() are unknown so the default strategy should be used.
        return HttpExecutionStrategyInfluencer.defaultStreamingInfluencer().influenceStrategy(strategy);
    }

    @Override
    default HttpExecutionStrategy requiredOffloads() {
        // The effects of apply() are unknown so the default "safe" strategy should be used.
        return HttpExecutionStrategyInfluencer.super.requiredOffloads();
    }

    /**
     * Create a {@link TimeoutFromRequest} instance.
     *
     * @param function a function for converting request headers to a duration
     * @param requiredStrategy execution strategy required by the function.
     * @return {@link TimeoutFromRequest} instance which applies the provided function and requires the specified
     * strategy.
     * @deprecated In areas which require {@link TimeoutFromRequest} use variants that accept
     * {@link java.util.function.BiFunction}&lt;{@link HttpRequestMetaData}, {@link TimeSource}, {@link Duration}&gt;.
     * E.g.:
     * {@link TimeoutHttpRequesterFilter#TimeoutHttpRequesterFilter(BiFunction, boolean)},
     * {@link TimeoutHttpServiceFilter#TimeoutHttpServiceFilter(BiFunction, boolean)} for filters.
     * Note that passed {@link java.util.function.BiFunction} should never block.
     */
    @Deprecated
    static TimeoutFromRequest toTimeoutFromRequest(final Function<HttpRequestMetaData, Duration> function,
                                                   final HttpExecutionStrategy requiredStrategy) {
        return new TimeoutFromRequest() {
            @Nullable
            @Override
            public Duration apply(final HttpRequestMetaData request) {
                return function.apply(request);
            }

            @Override
            public HttpExecutionStrategy requiredOffloads() {
                return requiredStrategy;
            }
        };
    }
}
