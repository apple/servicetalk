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

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CorePoolConnectionPoolStrategyTest {

    List<TestLoadBalancedConnection> makeConnections(int size) {
        List<TestLoadBalancedConnection> result = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            result.add(TestLoadBalancedConnection.mockConnection("address-" + i));
        }
        return result;
    }

    @Test
    void selectsHosts() {
        List<TestLoadBalancedConnection> connections = makeConnections(1);
        ConnectionPoolStrategy<TestLoadBalancedConnection> strategy =
                new CorePoolConnectionPoolStrategy<>(1, false);
        assertNotNull(strategy.select(connections, c -> true, null));
    }

    @Test
    void prefersCorePool() {
        List<TestLoadBalancedConnection> connections = makeConnections(10);
        ConnectionPoolStrategy<TestLoadBalancedConnection> strategy =
                new CorePoolConnectionPoolStrategy<>(5, false);
        Set<TestLoadBalancedConnection> selected = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            selected.add(strategy.select(connections, c -> true, null));
        }
        // Commonly we should have more than one element in strategy, although we can expect it to contain a single
        // element with a probability of 0.2^99 or ~6e-70.
        assertThat(selected, hasSize(greaterThan(1)));

        // We should not have selected any of the non-core pool connections.
        for (int i = 5; i < 10; i++) {
            assertThat(selected, not(hasItem(connections.get(i))));
        }
    }

    @Test
    void spillsIntoOverflow() {
        List<TestLoadBalancedConnection> connections = makeConnections(6);
        ConnectionPoolStrategy<TestLoadBalancedConnection> strategy =
                new CorePoolConnectionPoolStrategy<>(5, false);
        Set<TestLoadBalancedConnection> corePoolCxns = new HashSet<>();
        for (int i = 0; i < 5; i++) {
            corePoolCxns.add(connections.get(i));
        }
        Predicate<TestLoadBalancedConnection> selector = (TestLoadBalancedConnection c) -> !corePoolCxns.contains(c);
        assertEquals(connections.get(5), strategy.select(connections, selector, null));
    }
}
