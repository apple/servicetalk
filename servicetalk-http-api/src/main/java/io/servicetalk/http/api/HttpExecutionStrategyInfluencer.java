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

import java.lang.reflect.Method;

import static io.servicetalk.http.api.DefaultStreamingStrategyInfluencer.DEFAULT_STREAMING_STRATEGY_INFLUENCER;
import static io.servicetalk.http.api.HttpExecutionStrategies.defaultStrategy;
import static io.servicetalk.http.api.HttpExecutionStrategies.offloadAll;
import static io.servicetalk.http.api.HttpExecutionStrategies.offloadNone;

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
     * @deprecated Implement {@link #requiredOffloads()} instead.
     */
    @Deprecated
    default HttpExecutionStrategy influenceStrategy(HttpExecutionStrategy strategy) {
        boolean defaultRequiredOffloads;
        try {
            // avoid infinite recursion of default requiredOffloads calling this default
            Method requiredOffloads = this.getClass().getMethod("requiredOffloads");
            defaultRequiredOffloads = requiredOffloads.getDeclaringClass() == HttpExecutionStrategyInfluencer.class &&
                    requiredOffloads.isDefault();
        } catch (NoSuchMethodException impossible) {
            defaultRequiredOffloads = true;
        }
        return defaultRequiredOffloads ? offloadAll() : requiredOffloads().merge(strategy);
    }

    /**
     * {@inheritDoc}
     *
     * <p>The provided default implementation requests offloading of all operations. Implementations that require no
     * offloading should be careful to return {@link HttpExecutionStrategies#offloadNone()} rather than
     * {@link HttpExecutionStrategies#offloadNever()}.
     */
    @Override
    default HttpExecutionStrategy requiredOffloads() {
        // safe default--implementations are expected to override
        HttpExecutionStrategy result = influenceStrategy(defaultStrategy());
        return defaultStrategy() == result ? offloadNone() : result;
    }

    /**
     * Returns an {@link HttpExecutionStrategyInfluencer} to be used for the default streaming programming model.
     *
     * @return An {@link HttpExecutionStrategyInfluencer} to be used for the default streaming programming model.
     * @deprecated This method is not useful anymore and will be removed in future releases.
     */
    @Deprecated
    static HttpExecutionStrategyInfluencer defaultStreamingInfluencer() {
        return DEFAULT_STREAMING_STRATEGY_INFLUENCER;
    }
}
