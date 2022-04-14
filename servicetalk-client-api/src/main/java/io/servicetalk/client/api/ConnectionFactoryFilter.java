/*
 * Copyright © 2018, 2021 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.client.api;

import io.servicetalk.concurrent.api.ListenableAsyncCloseable;
import io.servicetalk.transport.api.ConnectExecutionStrategy;
import io.servicetalk.transport.api.ExecutionStrategy;
import io.servicetalk.transport.api.ExecutionStrategyInfluencer;

import static java.util.Objects.requireNonNull;

/**
 * A contract to decorate {@link ConnectionFactory} instances for the purpose of filtering.
 *
 * @param <ResolvedAddress> The type of resolved addresses that can be used for connecting.
 * @param <C> The type of connections created by the {@link ConnectionFactory} decorated by this filter.
 */
@FunctionalInterface
public interface ConnectionFactoryFilter<ResolvedAddress, C extends ListenableAsyncCloseable>
        extends ExecutionStrategyInfluencer<ExecutionStrategy> {

    /**
     * Decorates the passed {@code original} {@link ConnectionFactory} to add the filtering logic.
     *
     * @param original {@link ConnectionFactory} to filter.
     * @return Decorated {@link ConnectionFactory} that contains the filtering logic.
     */
    ConnectionFactory<ResolvedAddress, C> create(ConnectionFactory<ResolvedAddress, C> original);

    /**
     * Returns a function that always returns its input {@link ConnectionFactory}.
     *
     * @param <RA> The type of resolved address that can be used for connecting.
     * @param <C> The type of connections created by the {@link ConnectionFactory} decorated by this filter.
     * @return a function that always returns its input {@link ConnectionFactory}.
     */
    static <RA, C extends ListenableAsyncCloseable> ConnectionFactoryFilter<RA, C> identity() {
        return withStrategy(original -> original, ExecutionStrategy.offloadNone());
    }

    /**
     * Returns a composed function that first applies the {@code before} function to its input, and then applies
     * this function to the result.
     * <p>
     * The order of execution of these filters are in order of append. If 3 filters are added as follows:
     * <pre>
     *     factory.append(filter1).append(filter2).append(filter3)
     * </pre>
     * accepting a connection by a filter wrapped by this filter chain, the order of invocation of these filters will
     * be:
     * <pre>
     *     filter1 ⇒ filter2 ⇒ filter3
     * </pre>
     * @param before the function to apply before this function is applied
     * @return a composed function that first applies the {@code before}
     * function and then applies this function
     */
    default ConnectionFactoryFilter<ResolvedAddress, C> append(ConnectionFactoryFilter<ResolvedAddress, C> before) {
        requireNonNull(before);
        return withStrategy(service -> create(before.create(
                new DeprecatedToNewConnectionFactoryFilter<ResolvedAddress, C>().create(service))),
                this.requiredOffloads().merge(before.requiredOffloads()));
    }

    /**
     * {@inheritDoc}
     *
     * <p>If the returned strategy extends {@link ConnectExecutionStrategy} then the connection creation or accept may
     * be offloaded.
     * <p>If the returned strategy extends {@code HttpExecutionStrategy} then the HTTP execution strategy will be
     * applied to the connections created.</p>
     * <p>A utility class provides the ability to combine connect and HTTP execution strategies,
     * {@code io.servicetalk.http.api.ConnectAndHttpExecutionStrategy}.
     */
    @Override
    default ExecutionStrategy requiredOffloads() {
        // safe default--implementations are expected to override if offloading is required.
        return ConnectExecutionStrategy.offloadAll();
    }

    /**
     * Wraps a connection factory filter to return a specific execution strategy.
     *
     * @param original connection factory filter to be wrapped.
     * @param strategy execution strategy for the wrapped filter
     * @param <RA> The type of resolved addresses that can be used for connecting.
     * @param <C> The type of connections created by the {@link ConnectionFactory} decorated by this filter.
     * @return wrapped {@link ConnectionFactoryFilter}
     */
    static <RA, C extends ListenableAsyncCloseable> ConnectionFactoryFilter<RA, C> withStrategy(
            ConnectionFactoryFilter<RA, C> original, ExecutionStrategy strategy) {
        return new DelegatingConnectionFactoryFilter<RA, C>(original) {
            @Override
            public ExecutionStrategy requiredOffloads() {
                return strategy;
            }
        };
    }
}
