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

import static io.servicetalk.utils.internal.NumberUtils.ensurePositive;
import static java.lang.Math.min;

/**
 * A connection selection strategy that prioritizes a configurable "core" pool.
 * <p>
 * This {@link ConnectionPoolStrategy} attempts to emulate the pooling behavior often seen in thread pools.
 * Specifically it allows for the configuration of a "core pool" size which are intended to be long-lived.
 * Iteration starts in the core pool at a random position and then iterates through the entire core pool before
 * moving to an overflow pool. Because iteration of this core pool starts at a random position the core connections
 * will get an even traffic load and, because they are equally selectable, will tend not to be removed due to idleness.
 * <p>
 * If the core pool cannot satisfy the load traffic can spill over to extra connections which are selected in-order.
 * This has the property of minimizing traffic to the latest elements added outside the core pool size, thus let
 * them idle out of the pool once they're no longer necessary.
 *
 * @param <C> the concrete type of the {@link LoadBalancedConnection}.
 */
final class CorePoolConnectionPoolStrategy<C extends LoadBalancedConnection>
        implements ConnectionPoolStrategy<C> {

    private final int corePoolSize;
    private final boolean forceCorePool;

    private CorePoolConnectionPoolStrategy(final int corePoolSize, final boolean forceCorePool) {
        this.corePoolSize = ensurePositive(corePoolSize, "corePoolSize");
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
        final ThreadLocalRandom rnd = ThreadLocalRandom.current();
        final int randomSearchSpace = min(connectionCount, corePoolSize);
        final int offset = rnd.nextInt(randomSearchSpace);
        for (int i = 0; i < randomSearchSpace; i++) {
            int ii = offset + i;
            if (ii >= randomSearchSpace) {
                ii -= randomSearchSpace;
            }
            final C connection = connections.get(ii);
            if (selector.test(connection)) {
                return connection;
            }
        }
        // Didn't succeed in the core pool. Linear search through the overflow pool (if it exists).
        for (int i = randomSearchSpace; i < connectionCount; i++) {
            final C connection = connections.get(i);
            if (selector.test(connection)) {
                return connection;
            }
        }
        // So sad, we didn't find a healthy connection.
        return null;
    }

    static <C extends LoadBalancedConnection> ConnectionPoolStrategyFactory<C> factory(
            int corePoolSize, boolean forceCorePool) {
        return new CorePoolConnectionPoolStrategyFactory<>(corePoolSize, forceCorePool);
    }

    private static final class CorePoolConnectionPoolStrategyFactory<C extends LoadBalancedConnection>
            implements ConnectionPoolStrategyFactory<C> {

        private final int corePoolSize;
        private final boolean forceCorePool;

        CorePoolConnectionPoolStrategyFactory(int corePoolSize, boolean forceCorePool) {
            this.corePoolSize = ensurePositive(corePoolSize, "corePoolSize");
            this.forceCorePool = forceCorePool;
        }

        @Override
        public ConnectionPoolStrategy<C> buildStrategy(String lbDescription) {
            return new CorePoolConnectionPoolStrategy<>(corePoolSize, forceCorePool);
        }

        @Override
        public String toString() {
            return "CorePoolConnectionPoolStrategyFactory{" +
                    "corePoolSize=" + corePoolSize +
                    ", forceCorePool=" + forceCorePool +
                    '}';
        }
    }
}
