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

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import static io.servicetalk.loadbalancer.ConnectionSelectorHelpers.makeConnections;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

class P2CConnectionPoolSelectorTest {

    private static ConnectionSelector<TestLoadBalancedConnection> strategy() {
        return P2CConnectionPoolSelector.<TestLoadBalancedConnection>factory(5, 5, false)
                .buildStrategy("resource");
    }

    @Test
    void selectsHosts() {
        // Ensure we can successfully select hosts for most sized pools
        for (int i = 1; i < 10; i++) {
            List<TestLoadBalancedConnection> connections = makeConnections(i);
            ConnectionSelector<TestLoadBalancedConnection> strategy = strategy();
            assertNotNull(strategy.select(connections, c -> true));
        }
    }

    @Test
    void prefersCorePool() {
        List<TestLoadBalancedConnection> connections = makeConnections(10);
        ConnectionSelector<TestLoadBalancedConnection> strategy = strategy();
        Set<TestLoadBalancedConnection> selected = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            selected.add(strategy.select(connections, c -> true));
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
        ConnectionSelector<TestLoadBalancedConnection> strategy = strategy();
        Set<TestLoadBalancedConnection> corePoolCxns = new HashSet<>();
        for (int i = 0; i < 5; i++) {
            corePoolCxns.add(connections.get(i));
        }
        Predicate<TestLoadBalancedConnection> selector = (TestLoadBalancedConnection c) -> !corePoolCxns.contains(c);
        assertEquals(connections.get(5), strategy.select(connections, selector));
    }

    @Test
    void prefersHigherScoringHosts() {
        List<TestLoadBalancedConnection> connections = makeConnections(2);
        ConnectionSelector<TestLoadBalancedConnection> strategy = strategy();
        // We should always get connection at index 1 becuase it has the higher score.
        when(connections.get(0).score()).thenReturn(0);
        when(connections.get(1).score()).thenReturn(1);

        for (int i = 0; i < 10; i++) {
            assertEquals(connections.get(1), strategy.select(connections, ctx -> true));
        }
    }

    @Test
    void willSelectLowerScoringConnectionIfHigherScoredConnectionCantBeSelected() {
        List<TestLoadBalancedConnection> connections = makeConnections(2);
        ConnectionSelector<TestLoadBalancedConnection> strategy = strategy();
        // We should always get connection at index 1 becuase it has the higher score.
        when(connections.get(0).score()).thenReturn(0);
        when(connections.get(1).score()).thenReturn(1);

        for (int i = 0; i < 10; i++) {
            assertEquals(connections.get(0), strategy.select(connections, ctx -> ctx == connections.get(0)));
        }
    }
}
