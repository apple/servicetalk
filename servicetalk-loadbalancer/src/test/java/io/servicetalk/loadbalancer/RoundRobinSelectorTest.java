/*
 * Copyright © 2023 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.client.api.NoActiveHostException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static io.servicetalk.loadbalancer.SelectorTestHelpers.PREDICATE;
import static io.servicetalk.loadbalancer.SelectorTestHelpers.connections;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.isA;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.when;

class RoundRobinSelectorTest {

    private boolean failOpen;
    private HostSelector<String, TestLoadBalancedConnection> selector;

    @BeforeEach
    void setup() {
        // set the default values before each test.
        selector = null;
        failOpen = false;
    }

    void init(List<Host<String, TestLoadBalancedConnection>> hosts) {
        selector = new RoundRobinSelector<>(hosts, "testResource", failOpen);
    }

    @Test
    void roundRobining() throws Exception {
        List<Host<String, TestLoadBalancedConnection>> hosts = connections("addr-1", "addr-2");
        init(hosts);
        List<String> addresses = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            TestLoadBalancedConnection connection = selector.selectConnection(
                    PREDICATE, null, true).toFuture().get();
            addresses.add(connection.address());
        }
        assertThat(addresses, contains("addr-1", "addr-2", "addr-1", "addr-2", "addr-1"));
    }

    @Test
    void skipUnhealthyHosts() throws Exception {
        List<Host<String, TestLoadBalancedConnection>> hosts = connections("addr-1", "addr-2");
        when(hosts.get(0).isUnhealthy(anyBoolean())).thenReturn(true);
        init(hosts);
        List<String> addresses = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            TestLoadBalancedConnection connection = selector.selectConnection(
                    PREDICATE, null, true).toFuture().get();
            addresses.add(connection.address());
        }
        assertThat(addresses, contains("addr-2", "addr-2", "addr-2", "addr-2", "addr-2"));
    }

    @ParameterizedTest(name = "{displayName} [{index}]: failOpen={0}")
    @ValueSource(booleans = {false, true})
    void noHealthyHosts(boolean failOpen) throws Exception {
        List<Host<String, TestLoadBalancedConnection>> hosts = connections("addr-1");
        when(hosts.get(0).isUnhealthy(anyBoolean())).thenReturn(true);
        this.failOpen = failOpen;
        init(hosts);
        if (failOpen) {
            List<String> addresses = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                TestLoadBalancedConnection connection = selector.selectConnection(
                        PREDICATE, null, true).toFuture().get();
                addresses.add(connection.address());
            }
            assertThat(addresses, contains("addr-1", "addr-1", "addr-1"));
        } else {
            Exception e = assertThrows(ExecutionException.class, () -> selector.selectConnection(
                    PREDICATE, null, false).toFuture().get());
            assertThat(e.getCause(), isA(NoActiveHostException.class));
        }
    }
}
