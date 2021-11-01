/*
 * Copyright © 2018 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.transport.api;

import static java.util.Objects.requireNonNull;

/**
 * A factory of {@link ConnectionAcceptor}.
 */
@FunctionalInterface
public interface ConnectionAcceptorFactory extends ExecutionStrategyInfluencer<ExecutionStrategy> {

    /**
     * Create a {@link ConnectionAcceptor} using the provided {@link ConnectionAcceptor}.
     *
     * @param original {@link ConnectionAcceptor} to filter
     * @return {@link ConnectionAcceptor} using the provided {@link ConnectionAcceptor}
     */
    ConnectionAcceptor create(ConnectionAcceptor original);

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
    default ConnectionAcceptorFactory append(ConnectionAcceptorFactory before) {
        requireNonNull(before);
        return withStrategy(service -> create(before.create(service)),
                this.requiredOffloads().merge(before.requiredOffloads()));
    }

    /**
     * Returns a function that always returns its input {@link ConnectionAcceptor}.
     *
     * @return a function that always returns its input {@link ConnectionAcceptor}.
     */
    static ConnectionAcceptorFactory identity() {
        return original -> original;
    }

    @Override
    default ExecutionStrategy requiredOffloads() {
        // "safe" default -- implementations are expected to override
        return ExecutionStrategy.offloadAll();
    }

    /**
     * Wraps a {@link ConnectionAcceptorFactory} to return a specific execution strategy.
     *
     * @param original factory to be wrapped.
     * @param strategy execution strategy for the wrapped factory
     * @return wrapped {@link ConnectionAcceptorFactory}
     */
    static ConnectionAcceptorFactory withStrategy(ConnectionAcceptorFactory original, ExecutionStrategy strategy) {
        return new ConnectionAcceptorFactory() {

            @Override
            public ConnectionAcceptor create(final ConnectionAcceptor acceptor) {
                return original.create(acceptor);
            }

            @Override
            public ExecutionStrategy requiredOffloads() {
                return strategy;
            }
        };
    }
}
