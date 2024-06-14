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

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;
import javax.annotation.Nullable;

import static io.servicetalk.utils.internal.NumberUtils.ensureNonNegative;
import static java.lang.Math.min;

/**
 * A connection selection strategy that prioritizes connection reuse.
 * <p>
 * This {@link ConnectionPoolStrategy} attempts to minimize the number of connections by attempting to direct
 * traffic to connections in the order they were created in linear order up until a configured quantity. After
 * this linear pool is exhausted the remaining connections will be selected from at random. Prioritizing traffic
 * to the existing connections will let tailing connections be removed due to idleness.
 *
 * @param <C> the concrete type of the {@link LoadBalancedConnection}.
 */
final class LinearSearchConnectionPoolStrategy<C extends LoadBalancedConnection> implements ConnectionPoolStrategy<C> {

    /**
     * With a relatively small number of connections we can minimize connection creation under moderate concurrency by
     * exhausting the full search space without sacrificing too much latency caused by the cost of a CAS operation per
     * selection attempt.
     */
    private static final int MIN_RANDOM_SEARCH_SPACE = 64;

    /**
     * For larger search spaces, due to the cost of a CAS operation per selection attempt we see diminishing returns for
     * trying to locate an available connection when most connections are in use. This increases tail latencies, thus
     * after some number of failed attempts it appears to be more beneficial to open a new connection instead.
     * <p>
     * The current heuristics were chosen based on a set of benchmarks under various circumstances, low connection
     * counts, larger connection counts, low connection churn, high connection churn.
     */
    private static final float RANDOM_SEARCH_FACTOR = 0.75f;

    private final int linearSearchSpace;

    private LinearSearchConnectionPoolStrategy(final int linearSearchSpace) {
        this.linearSearchSpace = ensureNonNegative(linearSearchSpace, "linearSearchSpace");
    }

    @Nullable
    @Override
    public C select(List<C> connections, Predicate<C> selector) {
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

    static <C extends LoadBalancedConnection> ConnectionPoolStrategyFactory<C> factory(final int linearSearchSpace) {
        ensureNonNegative(linearSearchSpace, "linearSearchSpace");
        return (lbDescription) -> new LinearSearchConnectionPoolStrategy<>(linearSearchSpace);
    }
}
