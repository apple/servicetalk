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

import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpExecutionStrategyInfluencer;
import io.servicetalk.http.api.HttpRequestMetaData;

import java.time.Duration;
import java.util.function.Function;
import javax.annotation.Nullable;

/**
 * A function to determine the appropriate timeout to be used for a given {@link HttpRequestMetaData HTTP request}.
 * The result is a {@link Duration} which may be null if no timeout is to be applied. If the function blocks then
 * {@link #influenceStrategy(HttpExecutionStrategy)} should alter the execution strategy as required.
 */
public interface TimeoutFromRequest extends Function<HttpRequestMetaData, Duration>,
                                            HttpExecutionStrategyInfluencer {

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

    /**
     * Returns a function which returns the provided default duration as the timeout duration to be used for any
     * request.
     *
     * @param duration timeout duration or null for no timeout
     * @return a function to produce a timeout using specified duration
     */
    static TimeoutFromRequest simpleDurationTimeout(@Nullable Duration duration) {
        return new TimeoutFromRequest() {
            @Nullable
            @Override
            public Duration apply(final HttpRequestMetaData request) {
                // the request is not considered
                return duration;
            }

            @Override
            public HttpExecutionStrategy influenceStrategy(final HttpExecutionStrategy strategy) {
                // No influence since we do not block.
                return strategy;
            }
        };
    }
}
