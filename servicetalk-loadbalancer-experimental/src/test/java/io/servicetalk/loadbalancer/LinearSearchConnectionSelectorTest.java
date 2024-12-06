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

import static io.servicetalk.loadbalancer.ConnectionSelectorHelpers.makeConnections;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class LinearSearchConnectionSelectorTest {

    ConnectionSelector<TestLoadBalancedConnection> strategy(int linearSearchSpace) {
        return LinearSearchConnectionSelector.<TestLoadBalancedConnection>factory(linearSearchSpace)
                .buildStrategy("resource");
    }

    @Test
    void selectsHosts() {
        // Ensure we can successfully select hosts for most sized pools
        for (int i = 1; i < 10; i++) {
            List<TestLoadBalancedConnection> connections = makeConnections(i);
            ConnectionSelector<TestLoadBalancedConnection> strategy = strategy(5);
            assertEquals(connections.get(0), strategy.select(connections, c -> true));
        }
    }

    @Test
    void picksHostsLinearlyUpToLinearSearchSpace() {
        List<TestLoadBalancedConnection> connections = makeConnections(10);
        ConnectionSelector<TestLoadBalancedConnection> strategy = strategy(10);
        Set<TestLoadBalancedConnection> selected = new HashSet<>();
        for (int i = 0; i < 10; i++) {
            TestLoadBalancedConnection cxn = strategy.select(connections, c -> !selected.contains(c));
            assertEquals(connections.get(i), cxn);
            selected.add(cxn);
        }
    }

    @Test
    void canRandomlySearchAfterLinearSpace() {
        List<TestLoadBalancedConnection> connections = makeConnections(5);
        ConnectionSelector<TestLoadBalancedConnection> strategy = strategy(1);
        for (int i = 0; i < 100; i++) {
            assertNotNull(strategy.select(connections, c -> c != connections.get(0)));
        }
    }
}
