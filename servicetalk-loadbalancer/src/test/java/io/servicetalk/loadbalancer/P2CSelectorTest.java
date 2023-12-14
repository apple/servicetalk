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

import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutionException;

import static io.servicetalk.loadbalancer.SelectorTestHelpers.PREDICATE;
import static io.servicetalk.loadbalancer.SelectorTestHelpers.connections;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.either;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.isA;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class P2CSelectorTest {

    private boolean failOpen;
    private int maxEffort;
    private Random random;
    private HostSelector<String, TestLoadBalancedConnection> selector;

    @BeforeEach
    void setup() {
        // set the default values before each test.
        selector = null;
        failOpen = false;
        maxEffort = 5;
        random = null;
    }

    void init(List<Host<String, TestLoadBalancedConnection>> hosts) {
        selector = new P2CSelector<>(hosts, "testResource", maxEffort, failOpen, random);
    }

    @Test
    void singleHealthyHost() throws Exception {
        init(connections("addr-1"));
        TestLoadBalancedConnection connection = selector.selectConnection(
                PREDICATE, null, false).toFuture().get();
        assertThat(connection.address(), equalTo("addr-1"));
    }

    @ParameterizedTest(name = "{displayName} [{index}]: failOpen={0}")
    @ValueSource(booleans = {false, true})
    void singleUnhealthyHost(boolean failOpen) throws Exception {
        List<Host<String, TestLoadBalancedConnection>> hosts = connections("addr-1");
        when(hosts.get(0).isUnhealthy(anyBoolean())).thenReturn(true);
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

    @ParameterizedTest(name = "{displayName} [{index}]: failOpen={0}")
    @ValueSource(booleans = {false, true})
    void singleInactiveAndUnhealthyHostWithConnection(boolean failOpen) throws Exception {
        List<Host<String, TestLoadBalancedConnection>> hosts = connections("addr-1");
        when(hosts.get(0).isUnhealthy(anyBoolean())).thenReturn(true);
        when(hosts.get(0).isActive()).thenReturn(false);
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

    @ParameterizedTest(name = "{displayName} [{index}]: failOpen={0}")
    @ValueSource(booleans = {false, true})
    void singleInactiveAndUnhealthyHostWithoutConnection(boolean failOpen) throws Exception {
        List<Host<String, TestLoadBalancedConnection>> hosts = connections("addr-1");
        when(hosts.get(0).isUnhealthy(anyBoolean())).thenReturn(true);
        when(hosts.get(0).pickConnection(PREDICATE, null)).thenReturn(null);
        when(hosts.get(0).isActive()).thenReturn(false);
        this.failOpen = failOpen;
        init(hosts);
        // We should never get a connection because we don't have one and an inactive host cant make one.
        Exception e = assertThrows(ExecutionException.class, () -> selector.selectConnection(
                PREDICATE, null, false).toFuture().get());
        assertThat(e.getCause(), isA(NoActiveHostException.class));
    }

    @Test
    void twoHealthyActiveHosts() throws Exception {
        List<Host<String, TestLoadBalancedConnection>> hosts = connections("addr-1", "addr-2");
        init(hosts);
        TestLoadBalancedConnection connection = selector.selectConnection(
                PREDICATE, null, true).toFuture().get();
        assertThat(connection.address(), either(equalTo("addr-1")).or(equalTo("addr-2")));
    }

    @ParameterizedTest(name = "{displayName} [{index}]: failOpen={0}")
    @ValueSource(booleans = {false, true})
    void twoHealthyInactiveHostsWithConnections(boolean failOpen) throws Exception {
        List<Host<String, TestLoadBalancedConnection>> hosts = connections("addr-1", "addr-2");
        for (Host<String, TestLoadBalancedConnection> host : hosts) {
            when(host.isActive()).thenReturn(false);
        }
        this.failOpen = failOpen;
        init(hosts);
        TestLoadBalancedConnection connection = selector.selectConnection(
                PREDICATE, null, false).toFuture().get();
        assertThat(connection.address(), either(equalTo("addr-1")).or(equalTo("addr-2")));
    }

    @ParameterizedTest(name = "{displayName} [{index}]: failOpen={0}")
    @ValueSource(booleans = {false, true})
    void twoHealthyInactiveHostsWithoutConnections(boolean failOpen) throws Exception {
        List<Host<String, TestLoadBalancedConnection>> hosts = connections("addr-1", "addr-2");
        for (Host<String, TestLoadBalancedConnection> host : hosts) {
            when(host.isActive()).thenReturn(false);
            when(host.pickConnection(PREDICATE, null)).thenReturn(null);
        }
        this.failOpen = failOpen;
        init(hosts);
        Exception e = assertThrows(ExecutionException.class, () -> selector.selectConnection(
                PREDICATE, null, false).toFuture().get());
        assertThat(e.getCause(), isA(NoActiveHostException.class));
    }

    @ParameterizedTest(name = "{displayName} [{index}]: failOpen={0}")
    @ValueSource(booleans = {false, true})
    void twoUnHealthyActiveHostsWithConnections(boolean failOpen) throws Exception {
        List<Host<String, TestLoadBalancedConnection>> hosts = connections("addr-1", "addr-2");
        for (Host<String, TestLoadBalancedConnection> host : hosts) {
            when(host.isUnhealthy(anyBoolean())).thenReturn(true);
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

    @ParameterizedTest(name = "{displayName} [{index}]: failOpen={0}")
    @ValueSource(booleans = {false, true})
    void twoUnHealthyActiveHostsWithoutConnections(boolean failOpen) throws Exception {
        List<Host<String, TestLoadBalancedConnection>> hosts = connections("addr-1", "addr-2");
        for (Host<String, TestLoadBalancedConnection> host : hosts) {
            when(host.isUnhealthy(anyBoolean())).thenReturn(true);
            when(host.pickConnection(PREDICATE, null)).thenReturn(null);
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

    @ParameterizedTest(name = "{displayName} [{index}]: failOpen={0}")
    @ValueSource(booleans = {false, true})
    void twoUnHealthyInactiveHosts(boolean failOpen) throws Exception {
        List<Host<String, TestLoadBalancedConnection>> hosts = connections("addr-1", "addr-2");
        for (Host<String, TestLoadBalancedConnection> host : hosts) {
            when(host.isUnhealthy(anyBoolean())).thenReturn(true);
            when(host.isActive()).thenReturn(false);
        }
        this.failOpen = failOpen;
        init(hosts);
        Exception e = assertThrows(ExecutionException.class, () -> selector.selectConnection(
                PREDICATE, null, false).toFuture().get());
        assertThat(e.getCause(), isA(NoActiveHostException.class));
    }

    @Test
    void doesntBiasTowardHostsWithConnections() throws Exception {
        List<Host<String, TestLoadBalancedConnection>> hosts = connections("addr-1", "addr-2");
        // we setup the first host to always be preferred by score, but it also doesn't have any connections.
        when(hosts.get(0).pickConnection(any(), any())).thenReturn(null);
        when(hosts.get(0).score()).thenReturn(10);
        init(hosts);
        TestLoadBalancedConnection connection = selector.selectConnection(
                PREDICATE, null, false).toFuture().get();
        assertThat(connection.address(), equalTo("addr-1"));
        // verify that we made a new connection to addr-1.
        verify(hosts.get(0)).newConnection(any(), anyBoolean(), any());
    }

    @Test
    void selectsExistingConnectionsFromNonPreferredHost() throws Exception {
        List<Host<String, TestLoadBalancedConnection>> hosts = connections("addr-1", "addr-2");
        // we setup the first host to always be preferred by score, but it also doesn't have any connections
        // and is unhealthy.
        when(hosts.get(0).pickConnection(any(), any())).thenReturn(null);
        when(hosts.get(0).isUnhealthy(anyBoolean())).thenReturn(true);
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
        List<Host<String, TestLoadBalancedConnection>> hosts = connections("addr-1", "addr-2");
        when(hosts.get(0).isUnhealthy(anyBoolean())).thenReturn(true);
        init(hosts);
        TestLoadBalancedConnection connection = selector.selectConnection(
                PREDICATE, null, false).toFuture().get();
        assertThat(connection.address(), equalTo("addr-2"));
    }

    @ParameterizedTest(name = "{displayName} [{index}]: forceNewConnection={0}")
    @ValueSource(booleans = {false, true})
    void biasesTowardTheHighestWeightHost(boolean forceNewConnection) throws Exception {
        List<Host<String, TestLoadBalancedConnection>> hosts = connections("addr-1", "addr-2");
        // Host 0 has the highest score, so it should always get the new connection.
        when(hosts.get(0).score()).thenReturn(10);
        init(hosts);
        TestLoadBalancedConnection connection = selector.selectConnection(
                PREDICATE, null, forceNewConnection).toFuture().get();
        assertThat(connection.address(), equalTo("addr-1"));
    }

    @ParameterizedTest(name = "{displayName} [{index}]: unhealthy={0}")
    @ValueSource(booleans = {false, true})
    void singleInactiveHostFailOpen(boolean unhealthy) {
        List<Host<String, TestLoadBalancedConnection>> hosts = connections("addr-1");
        when(hosts.get(0).isUnhealthy(anyBoolean())).thenReturn(unhealthy);
        when(hosts.get(0).pickConnection(PREDICATE, null)).thenReturn(null);
        when(hosts.get(0).isActive()).thenReturn(false);
        failOpen = true;
        init(hosts);
        Exception e = assertThrows(ExecutionException.class, () -> selector.selectConnection(
                PREDICATE, null, false).toFuture().get());
        assertThat(e.getCause(), isA(NoActiveHostException.class));
    }
}
