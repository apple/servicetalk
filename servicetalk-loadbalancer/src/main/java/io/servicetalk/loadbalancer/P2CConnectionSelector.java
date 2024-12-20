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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;
import javax.annotation.Nullable;

import static io.servicetalk.utils.internal.NumberUtils.ensureNonNegative;
import static io.servicetalk.utils.internal.NumberUtils.ensurePositive;
import static java.lang.Math.min;
import static java.util.Objects.requireNonNull;

/**
 * A {@link ConnectionSelector} that attempts to discern between the health of individual connections.
 * If individual connections have health data the P2C strategy can be used to bias traffic toward the best
 * connections. This has the following algorithm:
 * - Randomly select two connections from the 'core pool' (pick-two).
 *   - Try to select the 'best' of the two connections.
 *   - If we fail to select the best connection, try the other connection.
 * - If both connections fail, repeat the pick-two operation for up to maxEffort attempts, begin linear iteration
 *   through the remaining connections searching for an acceptable connection.
 *
 * @param <C> the type of the load balanced connection.
 */
final class P2CConnectionSelector<C extends LoadBalancedConnection> implements ConnectionSelector<C> {

    private static final Logger LOGGER = LoggerFactory.getLogger(P2CConnectionSelector.class);

    private final String lbDescription;
    private final int maxEffort;
    private final int corePoolSize;
    private final boolean forceCorePool;

    private P2CConnectionSelector(final String lbDescription, final int maxEffort, final int corePoolSize,
                                  final boolean forceCorePool) {
        this.lbDescription = requireNonNull(lbDescription, "lbDescription");
        this.maxEffort = ensureNonNegative(maxEffort, "maxEffort");
        this.corePoolSize = ensureNonNegative(corePoolSize, "corePoolSize");
        this.forceCorePool = forceCorePool;
    }

    @Nullable
    @Override
    public C select(List<C> connections, Predicate<C> selector) {
        final int connectionCount = connections.size();
        if (forceCorePool && connectionCount < corePoolSize) {
            // return null so the Host will create a new connection and thus populate the connection pool.
            return null;
        }

        final int randomSearchSpace = min(connectionCount, corePoolSize);
        if (randomSearchSpace == 1) {
            // Only one option in the core pool, so try it.
            final C connection = connections.get(0);
            if (selector.test(connection)) {
                return connection;
            }
        } else if (randomSearchSpace > 1) {
            // attempt p2c.
            final C connection = p2CLoop(randomSearchSpace, connections, selector);
            if (connection != null) {
                return connection;
            }
        }

        // Didn't find a connection in the core pool. Linear search through the overflow pool (if it exists).
        for (int i = randomSearchSpace; i < connectionCount; i++) {
            final C connection = connections.get(i);
            if (selector.test(connection)) {
                return connection;
            }
        }
        // So sad, we didn't find any acceptable connection.
        return null;
    }

    @Nullable
    private C p2CLoop(int randomSearchSpace, List<C> connections, Predicate<C> selector) {
        // attempt p2c.
        final ThreadLocalRandom rnd = ThreadLocalRandom.current();
        final boolean singleIteration = randomSearchSpace == 2;
        // If there are only two connections we only need to try once to have tried them all.
        final int attempts = singleIteration ? 1 : maxEffort;
        for (int i = 0; i < attempts; i++) {
            C candidate = p2cPick(rnd, randomSearchSpace, connections, selector);
            if (candidate != null) {
                return candidate;
            }
        }
        if (!singleIteration && LOGGER.isDebugEnabled()) {
            LOGGER.debug("{}: max effort ({}) exhausted while searching through {} candidates.",
                    lbDescription, maxEffort, randomSearchSpace);
        }
        return null;
    }

    @Nullable
    private C p2cPick(ThreadLocalRandom rnd, int randomSearchSpace, List<C> connections, Predicate<C> selector) {
        int i1 = rnd.nextInt(randomSearchSpace);
        int i2 = rnd.nextInt(randomSearchSpace - 1);
        if (i2 >= i1) {
            i2++;
        }
        C c1 = connections.get(i1);
        C c2 = connections.get(i2);
        // ensure that c1 has the highest score so that we can simply try c1 then c2.
        if (c2.score() > c1.score()) {
            C tmp = c1;
            c1 = c2;
            c2 = tmp;
        }

        if (selector.test(c1)) {
            return c1;
        }
        if (selector.test(c2)) {
            return c2;
        }
        // Neither connection was acceptable.
        return null;
    }

    static <C extends LoadBalancedConnection> ConnectionSelectorPolicy<C> factory(
            final int maxEffort, final int corePoolSize, final boolean forceCorePool) {
        return new P2CConnectionSelectorFactory<>(maxEffort, corePoolSize, forceCorePool);
    }

    private static final class P2CConnectionSelectorFactory<C extends LoadBalancedConnection>
            extends ConnectionSelectorPolicy<C> {

        private final int maxEffort;
        private final int corePoolSize;
        private final boolean forceCorePool;

        P2CConnectionSelectorFactory(
                final int maxEffort, final int corePoolSize, final boolean forceCorePool) {
            this.maxEffort = ensurePositive(maxEffort, " maxEffort");
            this.corePoolSize = ensureNonNegative(corePoolSize, "corePoolSize");
            this.forceCorePool = forceCorePool;
        }

        @Override
        public ConnectionSelector<C> buildConnectionSelector(String lbDescription) {
            return new P2CConnectionSelector<>(lbDescription, maxEffort, corePoolSize, forceCorePool);
        }

        @Override
        public String toString() {
            return P2CConnectionSelectorFactory.class.getSimpleName() + "{" +
                    "maxEffort=" + maxEffort +
                    ", corePoolSize=" + corePoolSize +
                    ", forceCorePool=" + forceCorePool +
                    '}';
        }
    }
}
