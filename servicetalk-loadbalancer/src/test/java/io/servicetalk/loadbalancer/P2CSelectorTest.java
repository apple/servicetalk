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
import io.servicetalk.client.api.NoAvailableHostException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import javax.annotation.Nullable;

import static io.servicetalk.loadbalancer.SelectorTestHelpers.PREDICATE;
import static io.servicetalk.loadbalancer.SelectorTestHelpers.generateHosts;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.either;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.isA;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class P2CSelectorTest {

    private static final int ITERATIONS = 10_000;

    private boolean failOpen;
    private int maxEffort;
    @Nullable
    private P2CSelector<String, TestLoadBalancedConnection> selector;

    @BeforeEach
    void setup() {
        // set the default values before each test.
        selector = null;
        failOpen = false;
        maxEffort = 5;
    }

    void init(List<Host<String, TestLoadBalancedConnection>> hosts) {
        // Seed the random so that we don't have flaky test when we randomly get a bad roll.
        selector = new P2CSelector<>(hosts, "testResource",
                false, maxEffort, failOpen, new Random(1L));
    }

    @Test
    void equalWeightDistribution() throws Exception {
        List<Host<String, TestLoadBalancedConnection>> hosts = SelectorTestHelpers.generateHosts(5);
        init(hosts);
        checkProbabilities(hosts);
    }

    @Test
    void unequalWeightDistribution() throws Exception {
        List<Host<String, TestLoadBalancedConnection>> hosts = SelectorTestHelpers.generateHosts(5);
        when(hosts.get(0).weight()).thenReturn(2.0);
        when(hosts.get(1).weight()).thenReturn(0.5);
        init(hosts);
        checkProbabilities(hosts);
    }

    @Test
    void unequalWeightsButWeightsDisabled() throws Exception {
        List<Host<String, TestLoadBalancedConnection>> hosts = SelectorTestHelpers.generateHosts(2);
        when(hosts.get(0).weight()).thenReturn(2.0);
        when(hosts.get(1).weight()).thenReturn(0.5);
        selector = new P2CSelector<>(hosts, "testResource", true, maxEffort, failOpen, new Random(0L));
        int[] counts = runIterations(hosts);

        double expected = Arrays.stream(counts).reduce(0, (a, b) -> a + b) / (double) hosts.size();
        for (int i = 0; i < hosts.size(); i++) {
            assertThat((double) counts[i], closeTo(expected, expected * 0.05));
        }
    }

    @Test
    void negativeWeightsTurnIntoUnweightedSelection() throws Exception {
        List<Host<String, TestLoadBalancedConnection>> hosts = SelectorTestHelpers.generateHosts(2);
        when(hosts.get(0).weight()).thenReturn(-1.0);
        init(hosts);
        int[] counts = runIterations(hosts);

        double expected = Arrays.stream(counts).reduce(0, (a, b) -> a + b) / (double) hosts.size();
        for (int i = 0; i < hosts.size(); i++) {
            assertThat((double) counts[i], closeTo(expected, expected * 0.05));
        }
    }

    @Test
    void singleHealthyHost() throws Exception {
        init(SelectorTestHelpers.generateHosts("addr-1"));
        TestLoadBalancedConnection connection = selector.selectConnection(
                PREDICATE, null, false).toFuture().get();
        assertThat(connection.address(), equalTo("addr-1"));
    }

    @ParameterizedTest(name = "{displayName} [{index}]: failOpen={0}, equalWeights={1}")
    @CsvSource({"true, true,", "true, false", "false, true", "false, false"})
    void emptyHostSet(boolean failOpen, boolean equalWeights) {
        List<Host<String, TestLoadBalancedConnection>> hosts = generateHosts(equalWeights);
        this.failOpen = failOpen;
        init(hosts);
        ExecutionException ex = assertThrows(ExecutionException.class,
            () -> selector.selectConnection(PREDICATE, null, false).toFuture().get());
        assertThat(ex.getCause(), instanceOf(NoAvailableHostException.class));
    }

    @ParameterizedTest(name = "{displayName} [{index}]: failOpen={0}, equalWeights={1}")
    @CsvSource({"true, true,", "true, false", "false, true", "false, false"})
    void singleUnhealthyHost(boolean failOpen, boolean equalWeights) throws Exception {
        List<Host<String, TestLoadBalancedConnection>> hosts = generateHosts(equalWeights, "addr-1");
        when(hosts.get(0).isHealthy()).thenReturn(false);
        this.failOpen = failOpen;
        init(hosts);
        if (failOpen) {
            TestLoadBalancedConnection connection = selector.selectConnection(
                    PREDICATE, null, false).toFuture().get();
            assertThat(connection.address(), equalTo("addr-1"));
        } else {
            Exception e = assertThrows(ExecutionException.class, () -> selector.selectConnection(
                    PREDICATE, null, false).toFuture().get());
            assertThat(e.getCause(), isA(NoActiveHostException.class));
        }
    }

    @ParameterizedTest(name = "{displayName} [{index}]: failOpen={0}, equalWeights={1}")
    @CsvSource({"true, true,", "true, false", "false, true", "false, false"})
    void singleInactiveAndUnhealthyHostWithConnection(boolean failOpen, boolean equalWeights) throws Exception {
        List<Host<String, TestLoadBalancedConnection>> hosts = generateHosts(equalWeights, "addr-1");
        when(hosts.get(0).isHealthy()).thenReturn(false);
        when(hosts.get(0).canMakeNewConnections()).thenReturn(false);
        this.failOpen = failOpen;
        init(hosts);
        if (failOpen) {
            TestLoadBalancedConnection connection = selector.selectConnection(
                    PREDICATE, null, false).toFuture().get();
            assertThat(connection.address(), equalTo("addr-1"));
        } else {
            Exception e = assertThrows(ExecutionException.class, () -> selector.selectConnection(
                    PREDICATE, null, false).toFuture().get());
            assertThat(e.getCause(), isA(NoActiveHostException.class));
        }
    }

    @ParameterizedTest(name = "{displayName} [{index}]: failOpen={0}, equalWeights={1}")
    @CsvSource({"true, true,", "true, false", "false, true", "false, false"})
    void singleInactiveAndUnhealthyHostWithoutConnection(boolean failOpen, boolean equalWeights) throws Exception {
        List<Host<String, TestLoadBalancedConnection>> hosts = generateHosts(equalWeights, "addr-1");
        when(hosts.get(0).isHealthy()).thenReturn(false);
        when(hosts.get(0).canMakeNewConnections()).thenReturn(false);
        when(hosts.get(0).pickConnection(PREDICATE, null)).thenReturn(null);
        this.failOpen = failOpen;
        init(hosts);
        // We should never get a connection because we don't have one and an inactive host cant make one.
        Exception e = assertThrows(ExecutionException.class, () -> selector.selectConnection(
                PREDICATE, null, false).toFuture().get());
        assertThat(e.getCause(), isA(NoActiveHostException.class));
    }

    @ParameterizedTest(name = "{displayName} [{index}]: failOpen={0}, equalWeights={1}")
    @CsvSource({"true, true,", "true, false", "false, true", "false, false"})
    void twoHealthyActiveHosts(boolean failOpen, boolean equalWeights) throws Exception {
        List<Host<String, TestLoadBalancedConnection>> hosts = generateHosts(equalWeights, "addr-1", "addr-2");
        this.failOpen = failOpen;
        init(hosts);
        TestLoadBalancedConnection connection = selector.selectConnection(
                PREDICATE, null, true).toFuture().get();
        assertThat(connection.address(), either(equalTo("addr-1")).or(equalTo("addr-2")));
    }

    @ParameterizedTest(name = "{displayName} [{index}]: failOpen={0}, equalWeights={1}")
    @CsvSource({"true, true,", "true, false", "false, true", "false, false"})
    void twoHealthyInactiveHostsWithConnections(boolean failOpen, boolean equalWeights) throws Exception {
        List<Host<String, TestLoadBalancedConnection>> hosts = generateHosts(equalWeights, "addr-1", "addr-2");
        for (Host<String, TestLoadBalancedConnection> host : hosts) {
            when(host.canMakeNewConnections()).thenReturn(false);
        }
        this.failOpen = failOpen;
        init(hosts);
        TestLoadBalancedConnection connection = selector.selectConnection(
                PREDICATE, null, false).toFuture().get();
        assertThat(connection.address(), either(equalTo("addr-1")).or(equalTo("addr-2")));
    }

    @ParameterizedTest(name = "{displayName} [{index}]: failOpen={0}, equalWeights={1}")
    @CsvSource({"true, true,", "true, false", "false, true", "false, false"})
    void twoHealthyInactiveHostsWithoutConnections(boolean failOpen, boolean equalWeights) throws Exception {
        List<Host<String, TestLoadBalancedConnection>> hosts = generateHosts(equalWeights, "addr-1", "addr-2");
        for (Host<String, TestLoadBalancedConnection> host : hosts) {
            when(host.canMakeNewConnections()).thenReturn(false);
            when(host.pickConnection(PREDICATE, null)).thenReturn(null);
        }
        this.failOpen = failOpen;
        init(hosts);
        Exception e = assertThrows(ExecutionException.class, () -> selector.selectConnection(
                PREDICATE, null, false).toFuture().get());
        assertThat(e.getCause(), isA(NoActiveHostException.class));
    }

    @ParameterizedTest(name = "{displayName} [{index}]: failOpen={0}, equalWeights={1}")
    @CsvSource({"true, true,", "true, false", "false, true", "false, false"})
    void twoUnHealthyActiveHosts(boolean failOpen, boolean equalWeights) throws Exception {
        List<Host<String, TestLoadBalancedConnection>> hosts = generateHosts(equalWeights, "addr-1", "addr-2");
        for (Host<String, TestLoadBalancedConnection> host : hosts) {
            when(host.isHealthy()).thenReturn(false);
        }
        this.failOpen = failOpen;
        init(hosts);
        if (failOpen) {
            TestLoadBalancedConnection connection = selector.selectConnection(
                    PREDICATE, null, false).toFuture().get();
            assertThat(connection.address(), either(equalTo("addr-1")).or(equalTo("addr-2")));
        } else {
            Exception e = assertThrows(ExecutionException.class, () -> selector.selectConnection(
                    PREDICATE, null, false).toFuture().get());
            assertThat(e.getCause(), isA(NoActiveHostException.class));
        }
    }

    @ParameterizedTest(name = "{displayName} [{index}]: failOpen={0}, equalWeights={1}")
    @CsvSource({"true, true,", "true, false", "false, true", "false, false"})
    void twoUnHealthyInactiveHosts(boolean failOpen, boolean equalWeights) {
        List<Host<String, TestLoadBalancedConnection>> hosts = generateHosts(equalWeights, "addr-1", "addr-2");
        for (Host<String, TestLoadBalancedConnection> host : hosts) {
            when(host.isHealthy()).thenReturn(false);
            when(host.canMakeNewConnections()).thenReturn(false);
        }
        this.failOpen = failOpen;
        init(hosts);
        Exception e = assertThrows(ExecutionException.class, () -> selector.selectConnection(
                PREDICATE, null, false).toFuture().get());
        assertThat(e.getCause(), isA(NoActiveHostException.class));
    }

    @ParameterizedTest(name = "{displayName} [{index}]: failOpen={0}, equalWeights={1}")
    @CsvSource({"true, true,", "true, false", "false, true", "false, false"})
    void doesntBiasTowardHostsWithConnections(boolean failOpen, boolean equalWeights) throws Exception {
        List<Host<String, TestLoadBalancedConnection>> hosts = generateHosts(equalWeights, "addr-1", "addr-2");
        // we setup the first host to always be preferred by score, but it also doesn't have any connections.
        when(hosts.get(0).pickConnection(any(), any())).thenReturn(null);
        when(hosts.get(0).score()).thenReturn(10);
        this.failOpen = failOpen;
        init(hosts);
        TestLoadBalancedConnection connection = selector.selectConnection(
                PREDICATE, null, false).toFuture().get();
        assertThat(connection.address(), equalTo("addr-1"));
        // verify that we made a new connection to addr-1.
        verify(hosts.get(0)).newConnection(any(), anyBoolean(), any());
    }

    @Test
    void selectsExistingConnectionsFromNonPreferredHost() throws Exception {
        List<Host<String, TestLoadBalancedConnection>> hosts = SelectorTestHelpers.generateHosts("addr-1", "addr-2");
        // we setup the first host to always be preferred by score, but it also doesn't have any connections
        // and is unhealthy.
        when(hosts.get(0).pickConnection(any(), any())).thenReturn(null);
        when(hosts.get(0).isHealthy()).thenReturn(false);
        when(hosts.get(0).score()).thenReturn(10);
        init(hosts);
        TestLoadBalancedConnection connection = selector.selectConnection(
                PREDICATE, null, false).toFuture().get();
        assertThat(connection.address(), equalTo("addr-2"));
        // Verify that we selected an existing connection.
        verify(hosts.get(1), never()).newConnection(any(), anyBoolean(), any());
    }

    @Test
    void biasesTowardsHealthyHostWhenMakingConnections() throws Exception {
        List<Host<String, TestLoadBalancedConnection>> hosts = SelectorTestHelpers.generateHosts("addr-1", "addr-2");
        when(hosts.get(0).isHealthy()).thenReturn(false);
        init(hosts);
        TestLoadBalancedConnection connection = selector.selectConnection(
                PREDICATE, null, false).toFuture().get();
        assertThat(connection.address(), equalTo("addr-2"));
    }

    @ParameterizedTest(name = "{displayName} [{index}]: forceNewConnection={0}, equalWeights={1}")
    @CsvSource({"true, true,", "true, false", "false, true", "false, false"})
    void biasesTowardTheHighestWeightHost(boolean forceNewConnection, boolean equalWeights) throws Exception {
        List<Host<String, TestLoadBalancedConnection>> hosts = generateHosts(equalWeights, "addr-1", "addr-2");
        // Host 0 has the highest score, so it should always get the new connection.
        when(hosts.get(0).score()).thenReturn(10);
        init(hosts);
        TestLoadBalancedConnection connection = selector.selectConnection(
                PREDICATE, null, forceNewConnection).toFuture().get();
        assertThat(connection.address(), equalTo("addr-1"));
    }

    @ParameterizedTest(name = "{displayName} [{index}]: unhealthy={0}, equalWeights={1}")
    @CsvSource({"true, true,", "true, false", "false, true", "false, false"})
    void singleInactiveHostFailOpen(boolean unhealthy, boolean equalWeights) {
        List<Host<String, TestLoadBalancedConnection>> hosts = generateHosts(equalWeights, "addr-1");
        when(hosts.get(0).isHealthy()).thenReturn(unhealthy);
        when(hosts.get(0).canMakeNewConnections()).thenReturn(false);
        when(hosts.get(0).pickConnection(PREDICATE, null)).thenReturn(null);
        failOpen = true;
        init(hosts);
        Exception e = assertThrows(ExecutionException.class, () -> selector.selectConnection(
                PREDICATE, null, false).toFuture().get());
        assertThat(e.getCause(), isA(NoActiveHostException.class));
    }

    private void checkProbabilities(List<Host<String, TestLoadBalancedConnection>> hosts) throws Exception {
        int[] counts = runIterations(hosts);

        double totalProbability = hosts.stream().map(Host::weight).reduce(0d, (a, b) -> a + b);
        Integer[] expected = hosts.stream()
                .map(host -> (int) (ITERATIONS * (host.weight() / totalProbability)))
                .toArray(Integer[]::new);

        // calculate the rough counts we should expect
        for (int i = 0; i < hosts.size(); i++) {
            double c = counts[i];
            double e = expected[i];
            double acceptableError = 0.05 * e; // 5% error
            assertThat(c, closeTo(e, acceptableError));
        }
    }

    private int[] runIterations(List<Host<String, TestLoadBalancedConnection>> hosts) throws Exception {
        HostSelector<String, TestLoadBalancedConnection> toRun = selector.rebuildWithHosts(hosts);
        int[] results = new int[hosts.size()];
        Map<TestLoadBalancedConnection, Integer> hostMap = new HashMap<>();

        // setup our hosts with a connection
        for (int i = 0; i < hosts.size(); i++) {
            Host<String, TestLoadBalancedConnection> host = hosts.get(i);
            TestLoadBalancedConnection cxn = TestLoadBalancedConnection.mockConnection(host.address());
            when(host.pickConnection(any(), any())).thenReturn(cxn);
            hostMap.put(cxn, i);
        }

        for (int i = 0; i < ITERATIONS; i++) {
            results[hostMap.get(toRun.selectConnection(ignored -> true, null, false).toFuture().get())]++;
        }
        return results;
    }
}
