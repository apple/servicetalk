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
package io.servicetalk.http.api;

import static io.servicetalk.http.api.DefaultStreamingStrategyInfluencer.DEFAULT_STREAMING_STRATEGY_INFLUENCER;

/**
 * An entity that wishes to influence {@link HttpExecutionStrategy} for an HTTP client or server.
 */
@FunctionalInterface
public interface HttpExecutionStrategyInfluencer {

    /**
     * Optionally modify the passed {@link HttpExecutionStrategy} to a new {@link HttpExecutionStrategy} that suits
     * this {@link HttpExecutionStrategyInfluencer}.
     *
     * @param strategy {@link HttpExecutionStrategy} to influence.
     * @return {@link HttpExecutionStrategy} that suits this {@link HttpExecutionStrategyInfluencer}
     */
    HttpExecutionStrategy influenceStrategy(HttpExecutionStrategy strategy);

    /**
     * Returns an {@link HttpExecutionStrategyInfluencer} to be used for the default streaming programming model.
     *
     * @return An {@link HttpExecutionStrategyInfluencer} to be used for the default streaming programming model.
     */
    static HttpExecutionStrategyInfluencer defaultStreamingInfluencer() {
        return DEFAULT_STREAMING_STRATEGY_INFLUENCER;
    }
}
