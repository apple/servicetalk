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

import static java.util.Objects.requireNonNull;

/**
 * A contract to decorate {@link ConnectionFactory} instances for the purpose of filtering.
 *
 * @param <ResolvedAddress> The type of a resolved address that can be used for connecting.
 * @param <C> The type of connections created by the {@link ConnectionFactory} decorated by this filter.
 */
@FunctionalInterface
public interface ConnectionFactoryFilter<ResolvedAddress, C extends ListenableAsyncCloseable> {

    /**
     * Decorates the passed {@code original} {@link ConnectionFactory} to add the filtering logic.
     *
     * @param original {@link ConnectionFactory} to filter.
     * @return Decorated {@link ConnectionFactory} that contains the filtering logic.
     */
    ConnectionFactory<ResolvedAddress, C> create(ConnectionFactory<ResolvedAddress, C> original);

    /**
     * Returns a composed function that first applies the {@code before} function to its input, and then applies
     * this function to the result.
     * <p>
     * The order of execution of these filters are in order of append. If 3 filters are added as follows:
     * <pre>
     *     builder.append(filter1).append(filter2).append(filter3)
     * </pre>
     * Calling {@link ConnectionFactory} wrapped by this filter chain, the order of invocation of these filters will be:
     * <pre>
     *     filter1 =&gt; filter2 =&gt; filter3 =&gt; original connection factory
     * </pre>
     *
     * @deprecated Use {@code appendConnectionFactoryFilter(ConnectionFactoryFilter)} at the builder of a client
     * @param before the function to apply before this function is applied
     * @return a composed function that first applies the {@code before} function and then applies this function.
     */
    @Deprecated
    default ConnectionFactoryFilter<ResolvedAddress, C> append(ConnectionFactoryFilter<ResolvedAddress, C> before) {
        requireNonNull(before);
        return original -> create(before.create(original));
    }

    /**
     * Returns a function that always returns its input {@link ConnectionFactory}.
     *
     * @param <ResolvedAddress> The type of a resolved address that can be used for connecting.
     * @param <C> The type of connections created by the {@link ConnectionFactory} decorated by this filter.
     * @return a function that always returns its input {@link ConnectionFactory}.
     */
    static <ResolvedAddress,
            C extends ListenableAsyncCloseable> ConnectionFactoryFilter<ResolvedAddress, C> identity() {
        return original -> original;
    }
}
