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
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nullable;

import static io.servicetalk.loadbalancer.SelectorTestHelpers.PREDICATE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.isA;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

class RoundRobinSelectorTest {

    private boolean failOpen;
    private final AtomicInteger index = new AtomicInteger();
    @Nullable
    private HostSelector<String, TestLoadBalancedConnection> selector;

    @BeforeEach
    void setup() {
        // set the default values before each test.
        selector = null;
        failOpen = false;
    }

    void init(List<Host<String, TestLoadBalancedConnection>> hosts) {
        selector = new RoundRobinSelector<>(index, hosts, "testResource", failOpen, false);
    }

    @ParameterizedTest(name = "{displayName} [{index}]: negativeIndex={0}")
    @ValueSource(booleans = {true, false})
    void roundRobining(boolean negativeIndex) throws Exception {
        List<Host<String, TestLoadBalancedConnection>> hosts = SelectorTestHelpers.generateHosts("addr-1", "addr-2");
        index.set(negativeIndex ? -1000 : 0);
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
    void roundRobiningWithUnequalWeights() throws Exception {
        List<Host<String, TestLoadBalancedConnection>> hosts = SelectorTestHelpers.generateHosts(
                "addr-1", "addr-2", "addr-3");
        when(hosts.get(0).weight()).thenReturn(1.0);
        when(hosts.get(1).weight()).thenReturn(2.0);
        when(hosts.get(2).weight()).thenReturn(3.0);
        init(hosts);
        List<String> addresses = new ArrayList<>();
        for (int i = 0; i < 18; i++) {
            TestLoadBalancedConnection connection = selector.selectConnection(
                    PREDICATE, null, true).toFuture().get();
            addresses.add(connection.address());
        }

        assertThat(addresses.stream().filter("addr-1"::equals).count(), equalTo(3L));
        assertThat(addresses.stream().filter("addr-2"::equals).count(), equalTo(6L));
        assertThat(addresses.stream().filter("addr-3"::equals).count(), equalTo(9L));

        // The stream of selections should be should be
        // addr-1, addr-2, addr-3
        // F       T       T
        // F       F       T
        // T       T       T
        // F       T       T <- starting repetition
        // ...
        assertThat(addresses, contains(
                "addr-2", "addr-3", "addr-3",
                "addr-1", "addr-2", "addr-3",
                "addr-2", "addr-3", "addr-3",
                "addr-1", "addr-2", "addr-3",
                "addr-2", "addr-3", "addr-3",
                "addr-1", "addr-2", "addr-3"));
    }

    @Test
    void unequalWeightsWithATrueZeroWeight() throws Exception {
        List<Host<String, TestLoadBalancedConnection>> hosts = SelectorTestHelpers.generateHosts("addr-1", "addr-2");
        when(hosts.get(0).weight()).thenReturn(0.0);
        when(hosts.get(1).weight()).thenReturn(1.0);
        init(hosts);
        int[] counts = new int[2];
        for (int i = 0; i < 0xffff + 1; i++) {
            TestLoadBalancedConnection connection = selector.selectConnection(
                    PREDICATE, null, true).toFuture().get();
            if ("addr-1".equals(connection.address())) {
                counts[0]++;
            } else {
                counts[1]++;
            }
        }

        assertThat(counts[0], equalTo(0));
        assertThat(counts[1], equalTo(0xffff + 1));
    }

    @Test
    void unequalWeightsWithNearWeight() throws Exception {
        List<Host<String, TestLoadBalancedConnection>> hosts = SelectorTestHelpers.generateHosts("addr-1", "addr-2");
        when(hosts.get(0).weight()).thenReturn(1d / (0xffff * 7));
        when(hosts.get(1).weight()).thenReturn(1.0);
        init(hosts);
        int[] counts = new int[2];
        for (int i = 0; i < 0xffff + 1; i++) {
            TestLoadBalancedConnection connection = selector.selectConnection(
                    PREDICATE, null, true).toFuture().get();
            if ("addr-1".equals(connection.address())) {
                counts[0]++;
            } else {
                counts[1]++;
            }
        }

        assertThat(counts[0], equalTo(1));
        assertThat(counts[1], equalTo(0xffff));
    }

    @Test
    void skipUnhealthyHosts() throws Exception {
        List<Host<String, TestLoadBalancedConnection>> hosts = SelectorTestHelpers.generateHosts("addr-1", "addr-2");
        when(hosts.get(0).isHealthy()).thenReturn(false);
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
        List<Host<String, TestLoadBalancedConnection>> hosts = SelectorTestHelpers.generateHosts("addr-1");
        when(hosts.get(0).isHealthy()).thenReturn(false);
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

    @ParameterizedTest(name = "{displayName} [{index}]: unhealthy={0} failOpen={1}")
    @CsvSource({"true,true", "true,false", "false,true", "false,false"})
    void singleInactiveHostWithoutConnections(boolean unhealthy, boolean failOpen) {
        List<Host<String, TestLoadBalancedConnection>> hosts = SelectorTestHelpers.generateHosts("addr-1");
        when(hosts.get(0).canMakeNewConnections()).thenReturn(false);
        when(hosts.get(0).pickConnection(PREDICATE, null)).thenReturn(null);
        this.failOpen = failOpen;
        init(hosts);
        Exception e = assertThrows(ExecutionException.class, () -> selector.selectConnection(
                PREDICATE, null, false).toFuture().get());
        assertThat(e.getCause(), isA(NoActiveHostException.class));
    }

    @ParameterizedTest(name = "{displayName} [{index}]: negativeIndex={0}")
    @ValueSource(booleans = {true, false})
    void equalWeightsDoesNotOverPrioritizeTheNodeAfterAFailingNode(boolean negativeIndex) throws Exception {
        List<Host<String, TestLoadBalancedConnection>> hosts =
                SelectorTestHelpers.generateHosts("addr-1", "addr-2", "addr-3", "addr-4");
        when(hosts.get(0).isHealthy()).thenReturn(false);
        when(hosts.get(1).isHealthy()).thenReturn(false);
        index.set(negativeIndex ? -1000 : 0);
        init(hosts);

        List<String> addresses = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            TestLoadBalancedConnection connection = selector.selectConnection(
                    PREDICATE, null, true).toFuture().get();
            addresses.add(connection.address());
        }
        assertThat(addresses, contains("addr-3", "addr-4", "addr-3", "addr-4"));
    }

    @Test
    void unequalWeightsDoesNotOverPrioritizeTheNodeAfterAFailingNode() throws Exception {
        List<Host<String, TestLoadBalancedConnection>> hosts = SelectorTestHelpers.generateHosts(
                "addr-1", "addr-2", "addr-3", "addr-4");
        when(hosts.get(0).isHealthy()).thenReturn(false);
        when(hosts.get(1).isHealthy()).thenReturn(false);
        when(hosts.get(0).weight()).thenReturn(1.0);
        when(hosts.get(1).weight()).thenReturn(1.1);
        when(hosts.get(2).weight()).thenReturn(1.2);
        when(hosts.get(3).weight()).thenReturn(1.3);
        init(hosts);

        // The stream of 7 selections for healthy elements is
        //  [addr-2, addr-3, addr-4, addr-1, addr-2, addr-3, addr-4]
        // Since 1 and 2 are unhealthy we expect the first 4 picks to be
        //  [   X  , addr-3, addr-4,   X   ,   X   , addr-3, addr-4]
        List<String> addresses = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            TestLoadBalancedConnection connection = selector.selectConnection(
                    PREDICATE, null, true).toFuture().get();
            addresses.add(connection.address());
        }
        assertThat(addresses, contains("addr-3", "addr-4", "addr-3", "addr-4"));
    }
}
