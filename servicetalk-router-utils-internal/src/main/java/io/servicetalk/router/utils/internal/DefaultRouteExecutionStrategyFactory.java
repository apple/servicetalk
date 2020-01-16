/*
 * Copyright © 2020 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.router.utils.internal;

import io.servicetalk.router.api.RouteExecutionStrategy;
import io.servicetalk.router.api.RouteExecutionStrategyFactory;
import io.servicetalk.transport.api.ExecutionStrategy;

/**
 * Implementation of {@link RouteExecutionStrategyFactory} that always throws {@link IllegalArgumentException}.
 * This should be used as a default strategy factory when users did not provide their own implementation.
 *
 * @param <ES> protocol-specific execution strategy implementation
 */
public final class DefaultRouteExecutionStrategyFactory<ES extends ExecutionStrategy>
        implements RouteExecutionStrategyFactory<ES> {

    private static final RouteExecutionStrategyFactory<ExecutionStrategy> INSTANCE =
            new DefaultRouteExecutionStrategyFactory<>();

    private DefaultRouteExecutionStrategyFactory() {
        // singleton
    }

    @Override
    public ES get(final String id) {
        throw new IllegalArgumentException("Unknown execution strategy id: " + id);
    }

    /**
     * Returns default {@link RouteExecutionStrategyFactory}.
     *
     * @param <ES> protocol-specific execution strategy implementation
     * @return default {@link RouteExecutionStrategyFactory}
     */
    @SuppressWarnings("unchecked")
    public static <ES extends ExecutionStrategy> RouteExecutionStrategyFactory<ES> defaultStrategyFactory() {
        return (RouteExecutionStrategyFactory<ES>) INSTANCE;
    }

    /**
     * Returns an {@link ES} provided by {@link #defaultStrategyFactory()}.
     *
     * @param id of {@link RouteExecutionStrategy}
     * @param <ES> protocol-specific execution strategy implementation
     * @return an {@link ES} provided by {@link #defaultStrategyFactory()}
     */
    public static <ES extends ExecutionStrategy> ES getUsingDefaultStrategyFactory(final String id) {
        final RouteExecutionStrategyFactory<ES> defaultStrategyFactory = defaultStrategyFactory();
        return defaultStrategyFactory.get(id);
    }
}
