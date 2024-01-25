/*
 * Copyright © 2024 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.loadbalancer;

import io.servicetalk.client.api.LoadBalancedConnection;
import io.servicetalk.context.api.ContextMap;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;
import javax.annotation.Nullable;

import static io.servicetalk.utils.internal.NumberUtils.ensureNonNegative;
import static java.lang.Math.min;

interface ConnectionPoolStrategy<C extends LoadBalancedConnection> {

    /**
     * With a relatively small number of connections we can minimize connection creation under moderate concurrency by
     * exhausting the full search space without sacrificing too much latency caused by the cost of a CAS operation per
     * selection attempt.
     */
    int MIN_RANDOM_SEARCH_SPACE = 64;

    /**
     * For larger search spaces, due to the cost of a CAS operation per selection attempt we see diminishing returns for
     * trying to locate an available connection when most connections are in use. This increases tail latencies, thus
     * after some number of failed attempts it appears to be more beneficial to open a new connection instead.
     * <p>
     * The current heuristics were chosen based on a set of benchmarks under various circumstances, low connection
     * counts, larger connection counts, low connection churn, high connection churn.
     */
    float RANDOM_SEARCH_FACTOR = 0.75f;

    /**
     * Select a connection from the ordered list of connections.
     * @param connections the list of connections to pick from.
     * @param selector a predicate used to test a connection.
     * @param context optional {@link ContextMap} that can be used to pass information.
     * @return the selected connection, or {@code null} if no existing connection was selected.
     */
    @Nullable
    C select(List<C> connections, Predicate<C> selector, @Nullable ContextMap context);

    /**
     * A connection selection strategy that prioritizes a configurable "core" pool size and then will
     * spill over to an overflow pool.
     * <p>
     * This {@link ConnectionPoolStrategy} attempts to emulate the pooling behavior often seen in thread pools.
     * Specifically it allows for the configuration of a "core pool" size which are intended to be long-lived.
     * Members of this core pool are selected at random that so they will get an even traffic load and, because they
     * are equally selectable, will tend not to be removed due to idleness.
     * <p>
     * If the core pool cannot satisfy the load traffic can spill over to extra connections which are selected in-order.
     * This has the property of minimizing traffic to the latest elements added outside the core pool size, thus let
     * them tend to idle out of the pool once they're no longer of use.
     * @param <C> the concrete type of the {@link LoadBalancedConnection}.
     */
    final class CorePoolStrategy<C extends LoadBalancedConnection> implements ConnectionPoolStrategy<C> {

        private final int corePoolSize;
        private final boolean forceCorePool;

        CorePoolStrategy(final int corePoolSize, final boolean forceCorePool) {
            this.corePoolSize = ensureNonNegative(corePoolSize, "corePoolSize");
            this.forceCorePool = forceCorePool;
        }

        @Nullable
        @Override
        public C select(List<C> connections, Predicate<C> selector, @Nullable final ContextMap context) {
            final int connectionCount = connections.size();
            if (forceCorePool && connectionCount < corePoolSize) {
                // return null so the Host will create a new connection and thus populate the connection pool.
                return null;
            }
            final int randomAttempts = (int) (connectionCount * RANDOM_SEARCH_FACTOR);
            final ThreadLocalRandom rnd = ThreadLocalRandom.current();
            final int randomSearchSpace = min(connectionCount, corePoolSize);
            for (int i = 0; i < randomAttempts; ++i) {
                final C connection = connections.get(rnd.nextInt(randomSearchSpace));
                if (selector.test(connection)) {
                    return connection;
                }
            }
            // Linear search through the overflow pool (if it exists).
            for (int i = randomSearchSpace; i < connectionCount; i++) {
                final C connection = connections.get(i);
                if (selector.test(connection)) {
                    return connection;
                }
            }
            // So sad, we didn't find a healthy connection.
            return null;
        }
    }

    /**
     * A connection selection strategy that prioritizes connection reuse.
     * <p>
     * This {@link ConnectionPoolStrategy} attempts to minimize the number of connections by attempting to direct
     * traffic to connections in the order they were created in linear order up until a configured quantity. After
     * this linear pool is exhausted the remaining connections will be selected from at random. Prioritizing traffic
     * to the existing connections will let tailing connections be removed due to idleness.
     * @param <C> the concrete type of the {@link LoadBalancedConnection}.
     */
    final class LinearSearchFirst<C extends LoadBalancedConnection> implements ConnectionPoolStrategy<C> {

        private final int linearSearchSpace;

        LinearSearchFirst(final int linearSearchSpace) {
            this.linearSearchSpace = ensureNonNegative(linearSearchSpace, "linearSearchSpace");
        }

        @Nullable
        @Override
        public C select(List<C> connections, Predicate<C> selector, @Nullable final ContextMap context) {
            // Exhaust the linear search space first:
            final int linearAttempts = min(connections.size(), linearSearchSpace);
            for (int j = 0; j < linearAttempts; ++j) {
                final C connection = connections.get(j);
                if (selector.test(connection)) {
                    return connection;
                }
            }
            // Try other connections randomly:
            if (connections.size() > linearAttempts) {
                final int diff = connections.size() - linearAttempts;
                // With small enough search space, attempt number of times equal to number of remaining connections.
                // Back off after exploring most of the search space, it gives diminishing returns.
                final int randomAttempts = diff < MIN_RANDOM_SEARCH_SPACE ? diff :
                        (int) (diff * RANDOM_SEARCH_FACTOR);
                final ThreadLocalRandom rnd = ThreadLocalRandom.current();
                for (int j = 0; j < randomAttempts; ++j) {
                    final C connection = connections.get(rnd.nextInt(linearAttempts, connections.size()));
                    if (selector.test(connection)) {
                        return connection;
                    }
                }
            }
            // So sad, we didn't find a healthy connection.
            return null;
        }
    }
}
