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

import io.servicetalk.transport.api.ExecutionStrategyInfluencer;

import static io.servicetalk.http.api.DefaultStreamingStrategyInfluencer.DEFAULT_STREAMING_STRATEGY_INFLUENCER;

/**
 * An entity that wishes to influence {@link HttpExecutionStrategy} for an HTTP client or server.
 */
public interface HttpExecutionStrategyInfluencer extends ExecutionStrategyInfluencer<HttpExecutionStrategy> {

    /**
     * Optionally modify the passed {@link HttpExecutionStrategy} to a new {@link HttpExecutionStrategy} that suits
     * this {@link HttpExecutionStrategyInfluencer}.
     *
     * @param strategy {@link HttpExecutionStrategy} to influence.
     * @return {@link HttpExecutionStrategy} that suits this {@link HttpExecutionStrategyInfluencer}
     * @deprecated Implement {@link ExecutionStrategyInfluencer} interface and {@link #requiredOffloads()} instead.
     */
    @Deprecated
    default HttpExecutionStrategy influenceStrategy(HttpExecutionStrategy strategy) {
        return requiredOffloads().merge(strategy);
    }

    /**
     * Return the {@link HttpExecutionStrategy} describing offloads required by this instance.
     *
     * @return the {@link HttpExecutionStrategy} describing offloads required by this instance
     */
    @Override
    default HttpExecutionStrategy requiredOffloads() {
        return HttpExecutionStrategies.offloadAll();
    }

    /**
     * Returns an {@link HttpExecutionStrategyInfluencer} to be used for the default streaming programming model.
     *
     * @return An {@link HttpExecutionStrategyInfluencer} to be used for the default streaming programming model.
     */
    static HttpExecutionStrategyInfluencer defaultStreamingInfluencer() {
        return DEFAULT_STREAMING_STRATEGY_INFLUENCER;
    }

    /**
     * Creates an instance of {@link HttpExecutionStrategyInfluencer} that requires the provided strategy.
     *
     * @param requiredStrategy The required strategy of the influencer to be created.
     * @return an instance of {@link HttpExecutionStrategyInfluencer} that requires the provided strategy.
     */
    static HttpExecutionStrategyInfluencer newInfluencer(HttpExecutionStrategy requiredStrategy) {
        return new HttpExecutionStrategyInfluencer() {

            @Override
            public HttpExecutionStrategy requiredOffloads() {
                return requiredStrategy;
            }
        };
    }
}
