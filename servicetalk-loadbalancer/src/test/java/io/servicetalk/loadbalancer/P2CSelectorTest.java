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
import io.servicetalk.concurrent.api.Single;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.function.Predicate;
import javax.annotation.Nullable;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.either;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.isA;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class P2CSelectorTest {

    private static final Predicate<TestLoadBalancedConnection> PREDICATE = (ignored) -> true;
    private P2CSelector<String, TestLoadBalancedConnection> selector;

    @BeforeEach
    void init() {
        init(5, null);
    }

    void init(int maxEffort, @Nullable Random random) {
        selector = new P2CSelector<>("testResource", maxEffort, random);
    }

    private Host mockHost(String addr, TestLoadBalancedConnection connection) {
        Host<String, TestLoadBalancedConnection> host = mock(Host.class);
        when(host.address()).thenReturn(addr);
        when(host.isUnhealthy()).thenReturn(true);
        when(host.isActiveAndHealthy()).thenReturn(true);
        when(host.pickConnection(any(), any())).thenReturn(connection);
        when(host.newConnection(any(), anyBoolean(), any())).thenReturn(Single.succeeded(connection));
        return host;
    }

    private List<Host<String, TestLoadBalancedConnection>> connections(String... addresses) {
        final List<Host<String, TestLoadBalancedConnection>> results = new ArrayList<>(addresses.length);
        for (String addr : addresses) {
            results.add(mockHost(addr, TestLoadBalancedConnection.mockConnection(addr)));
        }
        return results;
    }

    @ParameterizedTest(name = "{displayName} [{index}]: forceNewConnection={0}")
    @ValueSource(booleans = {false, true})
    void singleHost(boolean forceNewConnection) throws Exception {
        List<Host<String, TestLoadBalancedConnection>> hosts = connections("addr-1");
        TestLoadBalancedConnection connection = selector.selectConnection(
                hosts, PREDICATE, null, forceNewConnection).toFuture().get();
        assertThat(connection.address(), equalTo("addr-1"));
    }

    @ParameterizedTest(name = "{displayName} [{index}]: forceNewConnection={0}")
    @ValueSource(booleans = {false, true})
    void singleUnhealthyHostWithConnection(boolean forceNewConnection) throws Exception {
        List<Host<String, TestLoadBalancedConnection>> hosts = connections("addr-1");
        when(hosts.get(0).isActiveAndHealthy()).thenReturn(false);
        if (forceNewConnection) {
            Exception e = assertThrows(ExecutionException.class, () -> selector.selectConnection(
                    hosts, PREDICATE, null, forceNewConnection).toFuture().get());
            assertThat(e.getCause(), isA(NoActiveHostException.class));
        } else {
            TestLoadBalancedConnection connection = selector.selectConnection(
                    hosts, PREDICATE, null, forceNewConnection).toFuture().get();
            assertThat(connection.address(), equalTo("addr-1"));
        }
    }

    @ParameterizedTest(name = "{displayName} [{index}]: forceNewConnection={0}")
    @ValueSource(booleans = {false, true})
    void singleUnhealthyHostWithoutConnection(boolean forceNewConnection) {
        List<Host<String, TestLoadBalancedConnection>> hosts = connections("addr-1");
        when(hosts.get(0).isActiveAndHealthy()).thenReturn(false);
        when(hosts.get(0).pickConnection(any(), any())).thenReturn(null);
        Exception e = assertThrows(ExecutionException.class, () -> selector.selectConnection(
                hosts, PREDICATE, null, forceNewConnection).toFuture().get());
        assertThat(e.getCause(), isA(NoActiveHostException.class));
    }

    @ParameterizedTest(name = "{displayName} [{index}]: forceNewConnection={0}")
    @ValueSource(booleans = {false, true})
    void twoHealthyHosts(boolean forceNewConnection) throws Exception {
        List<Host<String, TestLoadBalancedConnection>> hosts = connections("addr-1", "addr-2");
        TestLoadBalancedConnection connection = selector.selectConnection(
                hosts, PREDICATE, null, forceNewConnection).toFuture().get();
        assertThat(connection.address(), either(equalTo("addr-1")).or(equalTo("addr-2")));
    }

    @Test
    void twoUnHealthyHostsWithConnections() throws Exception {
        List<Host<String, TestLoadBalancedConnection>> hosts = connections("addr-1", "addr-2");
        for (Host<String, TestLoadBalancedConnection> host : hosts) {
            when(host.isActiveAndHealthy()).thenReturn(false);
        }
        TestLoadBalancedConnection connection = selector.selectConnection(
                hosts, PREDICATE, null, false).toFuture().get();
        assertThat(connection.address(), either(equalTo("addr-1")).or(equalTo("addr-2")));
    }

    @Test
    void twoUnHealthyHostsWithoutConnections() {
        List<Host<String, TestLoadBalancedConnection>> hosts = connections("addr-1", "addr-2");
        for (Host<String, TestLoadBalancedConnection> host : hosts) {
            when(host.isActiveAndHealthy()).thenReturn(false);
            when(host.pickConnection(any(), any())).thenReturn(null);
        }
        Exception e = assertThrows(ExecutionException.class, () -> selector.selectConnection(
                hosts, PREDICATE, null, false).toFuture().get());
        assertThat(e.getCause(), isA(NoActiveHostException.class));
    }

    @Test
    void doesntBiasTowardHostsWithConnections() throws Exception {
        List<Host<String, TestLoadBalancedConnection>> hosts = connections("addr-1", "addr-2");
        // we setup the first host to always be preferred by score, but it also doesn't have any connections.
        when(hosts.get(0).pickConnection(any(), any())).thenReturn(null);
        when(hosts.get(0).score()).thenReturn(10);
        TestLoadBalancedConnection connection = selector.selectConnection(
                hosts, PREDICATE, null, false).toFuture().get();
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
        when(hosts.get(0).isActiveAndHealthy()).thenReturn(false);
        when(hosts.get(0).score()).thenReturn(10);
        TestLoadBalancedConnection connection = selector.selectConnection(
                hosts, PREDICATE, null, false).toFuture().get();
        assertThat(connection.address(), equalTo("addr-2"));
        // Verify that we selected an existing connection.
        verify(hosts.get(1), never()).newConnection(any(), anyBoolean(), any());
    }

    @Test
    void biasesTowardsActiveAndHealthyHostWhenMakingConnections() throws Exception {
        List<Host<String, TestLoadBalancedConnection>> hosts = connections("addr-1", "addr-2");
        when(hosts.get(0).isActiveAndHealthy()).thenReturn(false);
        TestLoadBalancedConnection connection = selector.selectConnection(
                hosts, PREDICATE, null, true).toFuture().get();
        assertThat(connection.address(), equalTo("addr-2"));
    }

    @ParameterizedTest(name = "{displayName} [{index}]: forceNewConnection={0}")
    @ValueSource(booleans = {false, true})
    void biasesTowardTheHighestWeightHost(boolean forceNewConnection) throws Exception {
        List<Host<String, TestLoadBalancedConnection>> hosts = connections("addr-1", "addr-2");
        // Host 0 has the highest score, so it should always get the new connection.
        when(hosts.get(0).score()).thenReturn(10);
        TestLoadBalancedConnection connection = selector.selectConnection(
                hosts, PREDICATE, null, forceNewConnection).toFuture().get();
        assertThat(connection.address(), equalTo("addr-1"));
    }
}
