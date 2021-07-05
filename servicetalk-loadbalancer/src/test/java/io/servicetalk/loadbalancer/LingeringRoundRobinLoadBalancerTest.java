/*
 * Copyright © 2021 Apple Inc. and the ServiceTalk project authors
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

import org.junit.Test;

import java.util.function.Predicate;

import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class LingeringRoundRobinLoadBalancerTest extends RoundRobinLoadBalancerTest {

    @Test
    public void hostDownDoesntCloseConnection() throws Exception {
        sendServiceDiscoveryEvents(upEvent("address-1"));
        TestLoadBalancedConnection conn = lb.selectConnection(any()).toFuture().get();
        sendServiceDiscoveryEvents(downEvent("address-1"));
        verify(conn, times(0)).closeAsyncGracefully();
        verify(conn, times(0)).closeAsync();

        // But connection is cleaned when LB is closed
        lb.closeAsync().toFuture().get();
        verify(conn, times(1)).closeAsync();
        assertAddresses(lb.activeAddresses(), EMPTY_ARRAY);
    }

    @Test
    public void closedConnectionRemovesExpiredHost() throws Exception {
        sendServiceDiscoveryEvents(upEvent("address-1"));
        final Predicate<TestLoadBalancedConnection> connectionFilter = alwaysNewConnectionFilter();

        TestLoadBalancedConnection conn1 = lb.selectConnection(connectionFilter).toFuture().get();
        TestLoadBalancedConnection conn2 = lb.selectConnection(connectionFilter).toFuture().get();
        assertConnectionCount(lb.activeAddresses(), connectionsCount("address-1", 2));

        sendServiceDiscoveryEvents(downEvent("address-1"));
        assertConnectionCount(lb.activeAddresses(), connectionsCount("address-1", 2));

        conn1.closeAsync().toFuture().get();
        assertConnectionCount(lb.activeAddresses(), connectionsCount("address-1", 1));

        conn2.closeAsync().toFuture().get();
        assertAddresses(lb.activeAddresses(), EMPTY_ARRAY);
    }

    @Test
    public void closedConnectionDoesntRemoveActiveHost() throws Exception {
        sendServiceDiscoveryEvents(upEvent("address-1"));
        final Predicate<TestLoadBalancedConnection> connectionFilter = alwaysNewConnectionFilter();

        TestLoadBalancedConnection conn1 = lb.selectConnection(connectionFilter).toFuture().get();
        TestLoadBalancedConnection conn2 = lb.selectConnection(connectionFilter).toFuture().get();
        assertConnectionCount(lb.activeAddresses(), connectionsCount("address-1", 2));
        conn1.closeAsync().toFuture().get();
        assertConnectionCount(lb.activeAddresses(), connectionsCount("address-1", 1));
        conn2.closeAsync().toFuture().get();
        assertConnectionCount(lb.activeAddresses(), connectionsCount("address-1", 0));
    }

    @Test
    public void handleDiscoveryEventsForConnectedHosts() throws Exception {
        assertAddresses(lb.activeAddresses(), EMPTY_ARRAY);
        assertThat(lb.activeAddresses(), is(empty()));

        final Predicate<TestLoadBalancedConnection> connectionFilter = alwaysNewConnectionFilter();

        sendServiceDiscoveryEvents(upEvent("address-1"));
        assertAddresses(lb.activeAddresses(), "address-1");
        // For an added host, connection needs to be initiated, otherwise the host is free to be deleted
        lb.selectConnection(connectionFilter).toFuture().get();

        sendServiceDiscoveryEvents(downEvent("address-1"));
        assertAddresses(lb.activeAddresses(), "address-1");

        sendServiceDiscoveryEvents(upEvent("address-2"));
        lb.selectConnection(connectionFilter).toFuture().get();

        assertAddresses(lb.activeAddresses(), "address-1", "address-2");

        sendServiceDiscoveryEvents(downEvent("address-3"));
        assertAddresses(lb.activeAddresses(), "address-1", "address-2");

        sendServiceDiscoveryEvents(upEvent("address-1"));
        // At this moment in time, duplicates are allowed and the down event removes just the first address
        assertAddresses(lb.activeAddresses(), "address-1", "address-1", "address-2");

        sendServiceDiscoveryEvents(downEvent("address-1"));
        // Because the first address has an open connection, both addresses stay in the collection
        assertAddresses(lb.activeAddresses(), "address-1", "address-1", "address-2");

        sendServiceDiscoveryEvents(downEvent("address-2"));
        // This host has a connection open, so it stays.
        assertAddresses(lb.activeAddresses(), "address-1", "address-1", "address-2");

        // Let's make sure that an SD failure doesn't compromise LB's internal state
        sendServiceDiscoveryEvents(upEvent("address-1"));
        assertAddresses(lb.activeAddresses(), "address-1", "address-1", "address-1", "address-2");

        serviceDiscoveryPublisher.onError(DELIBERATE_EXCEPTION);
        assertAddresses(lb.activeAddresses(), "address-1", "address-1", "address-1", "address-2");
    }

    @Test
    public void handleDiscoveryEventsForNotConnectedHosts() {
        assertAddresses(lb.activeAddresses(), EMPTY_ARRAY);
        assertThat(lb.activeAddresses(), is(empty()));

        sendServiceDiscoveryEvents(upEvent("address-1"));
        assertAddresses(lb.activeAddresses(), "address-1");

        sendServiceDiscoveryEvents(downEvent("address-1"));
        assertAddresses(lb.activeAddresses(), EMPTY_ARRAY);

        sendServiceDiscoveryEvents(upEvent("address-2"));
        assertAddresses(lb.activeAddresses(), "address-2");

        sendServiceDiscoveryEvents(downEvent("address-3"));
        assertAddresses(lb.activeAddresses(), "address-2");

        sendServiceDiscoveryEvents(upEvent("address-1"));
        assertAddresses(lb.activeAddresses(), "address-1", "address-2");

        sendServiceDiscoveryEvents(downEvent("address-1"));
        assertAddresses(lb.activeAddresses(), "address-2");

        sendServiceDiscoveryEvents(downEvent("address-2"));
        assertAddresses(lb.activeAddresses(), EMPTY_ARRAY);

        // Let's make sure that an SD failure doesn't compromise LB's internal state
        sendServiceDiscoveryEvents(upEvent("address-1"));
        assertAddresses(lb.activeAddresses(), "address-1");

        serviceDiscoveryPublisher.onError(DELIBERATE_EXCEPTION);
        assertAddresses(lb.activeAddresses(), "address-1");
    }

    @Override
    protected RoundRobinLoadBalancer<String, TestLoadBalancedConnection> defaultLb() {
        return newTestLoadBalancer(false);
    }

    @Override
    protected RoundRobinLoadBalancer<String, TestLoadBalancedConnection> defaultLb(
            RoundRobinLoadBalancerTest.DelegatingConnectionFactory connectionFactory) {
        return newTestLoadBalancer(serviceDiscoveryPublisher, connectionFactory, false);
    }

    private Predicate<TestLoadBalancedConnection> alwaysNewConnectionFilter() {
        return cnx -> lb.activeAddresses().stream().noneMatch(addr -> addr.getValue().stream().anyMatch(cnx::equals));
    }
}
