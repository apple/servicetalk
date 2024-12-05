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

import static io.servicetalk.utils.internal.NumberUtils.ensurePositive;

/**
 * Configuration of the strategy for selecting connections from a pool to the same endpoint.
 */
// TODO: this may not be valuable to surface right now.
public abstract class ConnectionPoolPolicy {

    static final int DEFAULT_MAX_EFFORT = 5;
    static final int DEFAULT_LINEAR_SEARCH_SPACE = 16;

    private ConnectionPoolPolicy() {
        // only instances are in this class.
    }

    /**
     * A connection selection strategy that prioritizes a configurable "core" pool.
     * <p>
     * This {@link ConnectionPoolPolicy} attempts to emulate the pooling behavior often seen in thread pools.
     * Specifically it allows for the configuration of a "core pool" size which are intended to be long-lived.
     * Iteration starts in the core pool at a random position and then iterates through the entire core pool before
     * moving to an overflow pool. Because iteration of this core pool starts at a random position the core connections
     * will get an even traffic load and, because they are equally selectable, will tend not to be removed due to
     * idleness.
     * <p>
     * If the core pool cannot satisfy the load traffic can spill over to extra connections which are selected in-order.
     * This has the property of minimizing traffic to the latest elements added outside the core pool size, thus let
     * them idle out of the pool once they're no longer necessary.
     *
     * @param corePoolSize the size of the core pool.
     * @param forceCorePool whether to avoid selecting connections from the core pool until it has reached the
     *                      configured core pool size.
     * @return the configured {@link ConnectionPoolPolicy}.
     */
    public static ConnectionPoolPolicy corePool(final int corePoolSize, final boolean forceCorePool) {
        return new CorePoolStrategy(corePoolSize, forceCorePool);
    }

    /**
     * A connection selection strategy that prioritizes connection reuse.
     * <p>
     * This {@link ConnectionPoolPolicy} attempts to minimize the number of connections by attempting to direct
     * traffic to connections in the order they were created in linear order up until a configured quantity. After
     * this linear pool is exhausted the remaining connections will be selected from at random. Prioritizing traffic
     * to the existing connections will let tailing connections be removed due to idleness.
     * @return the configured {@link ConnectionPoolPolicy}.
     */
    public static ConnectionPoolPolicy linearSearch() {
        return linearSearch(DEFAULT_LINEAR_SEARCH_SPACE);
    }

    /**
     * A connection selection strategy that prioritizes connection reuse.
     * <p>
     * This {@link ConnectionPoolPolicy} attempts to minimize the number of connections by attempting to direct
     * traffic to connections in the order they were created in linear order up until a configured quantity. After
     * this linear pool is exhausted the remaining connections will be selected from at random. Prioritizing traffic
     * to the existing connections will let tailing connections be removed due to idleness.
     * @param linearSearchSpace the space to search linearly before resorting to random selection for remaining
     *                          connections.
     * @return the configured {@link ConnectionPoolPolicy}.
     */
    public static ConnectionPoolPolicy linearSearch(int linearSearchSpace) {
        return new LinearSearchStrategy(linearSearchSpace);
    }

    /**
     * A {@link ConnectionPoolPolicy} that attempts to discern between the health of individual connections.
     * If individual connections have health data the P2C strategy can be used to bias traffic toward the best
     * connections. This has the following algorithm:
     * - Randomly select two connections from the 'core pool' (pick-two).
     *   - Try to select the 'best' of the two connections.
     *   - If we fail to select the best connection, try the other connection.
     * - If both connections fail, repeat the pick-two operation for up to maxEffort attempts, begin linear iteration
     *   through the remaining connections searching for an acceptable connection.
     * @param corePoolSize the size of the core pool.
     * @param forceCorePool whether to avoid selecting connections from the core pool until it has reached the
     *                      configured core pool size.
     * @return the configured {@link ConnectionPoolPolicy}.
     */
    public static ConnectionPoolPolicy p2c(int corePoolSize, boolean forceCorePool) {
        return p2c(DEFAULT_MAX_EFFORT, corePoolSize, forceCorePool);
    }

    /**
     * A {@link ConnectionPoolPolicy} that attempts to discern between the health of individual connections.
     * If individual connections have health data the P2C strategy can be used to bias traffic toward the best
     * connections. This has the following algorithm:
     * - Randomly select two connections from the 'core pool' (pick-two).
     *   - Try to select the 'best' of the two connections.
     *   - If we fail to select the best connection, try the other connection.
     * - If both connections fail, repeat the pick-two operation for up to maxEffort attempts, begin linear iteration
     *   through the remaining connections searching for an acceptable connection.
     * @param maxEffort the maximum number of attempts to pick a healthy connection from the core pool.
     * @param corePoolSize the size of the core pool.
     * @param forceCorePool whether to avoid selecting connections from the core pool until it has reached the
     *                      configured core pool size.
     * @return the configured {@link ConnectionPoolPolicy}.
     */
    public static ConnectionPoolPolicy p2c(int maxEffort, int corePoolSize, boolean forceCorePool) {
        return new P2CStrategy(maxEffort, corePoolSize, forceCorePool);
    }

    // instance types
    static final class CorePoolStrategy extends ConnectionPoolPolicy {
        final int corePoolSize;
        final boolean forceCorePool;

        CorePoolStrategy(final int corePoolSize, final boolean forceCorePool) {
            this.corePoolSize = ensurePositive(corePoolSize, "corePoolSize");
            this.forceCorePool = forceCorePool;
        }
    }

    static final class P2CStrategy extends ConnectionPoolPolicy {
        final int maxEffort;
        final int corePoolSize;
        final boolean forceCorePool;

        P2CStrategy(final int maxEffort, final int corePoolSize, final boolean forceCorePool) {
            this.maxEffort = ensurePositive(maxEffort, "maxEffort");
            this.corePoolSize = ensurePositive(corePoolSize, "corePoolSize");
            this.forceCorePool = forceCorePool;
        }
    }

    static final class LinearSearchStrategy extends ConnectionPoolPolicy {
        final int linearSearchSpace;

        LinearSearchStrategy(int linearSearchSpace) {
            this.linearSearchSpace = ensurePositive(linearSearchSpace, "linearSearchSpace");
        }
    }
}
