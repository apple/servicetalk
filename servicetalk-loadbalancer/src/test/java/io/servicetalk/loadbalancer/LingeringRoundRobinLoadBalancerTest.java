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

import io.servicetalk.client.api.NoAvailableHostException;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Executors;

import org.junit.Test;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.Predicate;

import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class LingeringRoundRobinLoadBalancerTest extends RoundRobinLoadBalancerTest {

    @Test
    public void hostDownDoesntCloseConnectionCloseLB() throws Exception {
        hostDownDoesntCloseConnection(false);
    }

    @Test
    public void hostDownDoesntCloseConnectionCloseLBGracefully() throws Exception {
        hostDownDoesntCloseConnection(true);
    }

    private void hostDownDoesntCloseConnection(boolean gracefulClosure) throws Exception {
        sendServiceDiscoveryEvents(upEvent("address-1"));
        TestLoadBalancedConnection conn = lb.selectConnection(any()).toFuture().get();
        sendServiceDiscoveryEvents(downEvent("address-1"));
        verify(conn, never()).closeAsyncGracefully();
        verify(conn, never()).closeAsync();

        // But connection is cleaned when LB is closed
        if (gracefulClosure) {
            lb.closeAsyncGracefully().toFuture().get();
            verify(conn, times(1)).closeAsyncGracefully();
            verify(conn, never()).closeAsync();
        } else {
            lb.closeAsync().toFuture().get();
            verify(conn, times(1)).closeAsync();
            verify(conn, never()).closeAsyncGracefully();
        }
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

    // Concurrency test, worth running ~10K times to spot concurrency issues.
    @Test
    public void closureOfLastConnectionDoesntRaceWithNewAvailableEvent() throws Exception {
        Executor executor = Executors.newFixedSizeExecutor(1);
        try {
            sendServiceDiscoveryEvents(upEvent("address-1"));
            TestLoadBalancedConnection conn = lb.selectConnection(alwaysNewConnectionFilter()).toFuture().get();
            sendServiceDiscoveryEvents(downEvent("address-1"));
            assertConnectionCount(lb.activeAddresses(), connectionsCount("address-1", 1));

            Future<Object> f = executor.submit(() -> {
                sendServiceDiscoveryEvents(upEvent("address-1"));
                return null;
            }).toFuture();

            conn.closeAsync().toFuture().get();
            f.get();
            assertConnectionCount(lb.activeAddresses(), connectionsCount("address-1", 0));
        } finally {
            executor.closeAsync().toFuture().get();
        }
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
    public void handleDiscoveryEventsForExpiredHostBecomingAvailable() throws Exception {
        assertAddresses(lb.activeAddresses(), EMPTY_ARRAY);

        sendServiceDiscoveryEvents(upEvent("address-1"));
        assertAddresses(lb.activeAddresses(), "address-1");

        lb.selectConnection(any()).toFuture().get();
        assertConnectionCount(lb.activeAddresses(), connectionsCount("address-1", 1));

        sendServiceDiscoveryEvents(downEvent("address-1"));
        assertConnectionCount(lb.activeAddresses(), connectionsCount("address-1", 1));
        assertThat(lb.selectConnection(any()).toFuture().get(), is(notNullValue()));

        // We validate the host is expired by attempting to create a new connection
        final Predicate<TestLoadBalancedConnection> createNewConnection = alwaysNewConnectionFilter();
        Exception e = assertThrows(ExecutionException.class, () ->
                lb.selectConnection(createNewConnection).toFuture().get());
        assertThat(e.getCause(), instanceOf(NoAvailableHostException.class));

        // When the host becomes available again, new connections can be created
        sendServiceDiscoveryEvents(upEvent("address-1"));
        assertAddresses(lb.activeAddresses(), "address-1");
        lb.selectConnection(createNewConnection).toFuture().get();
        assertConnectionCount(lb.activeAddresses(), connectionsCount("address-1", 2));
    }

    @Test
    public void handleDiscoveryEventsForConnectedHosts() throws Exception {
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

        // Marking the first host as not expired should not create duplicates
        sendServiceDiscoveryEvents(upEvent("address-1"));
        assertAddresses(lb.activeAddresses(), "address-1", "address-2");

        // When all hosts are active, new event creates a duplicate
        sendServiceDiscoveryEvents(upEvent("address-1"));
        assertAddresses(lb.activeAddresses(), "address-1", "address-2", "address-1");

        // Because the first address (new ones are added at the end) has an open connection it's marked as "expired",
        // but the other one is removed due to 0 connections being open
        sendServiceDiscoveryEvents(downEvent("address-1"));
        assertAddresses(lb.activeAddresses(), "address-1", "address-2");

        // This host has a connection open, so it stays as "expired".
        sendServiceDiscoveryEvents(downEvent("address-2"));
        assertAddresses(lb.activeAddresses(), "address-1", "address-2");

        // Let's make sure that an SD failure doesn't compromise LB's internal state
        serviceDiscoveryPublisher.onError(DELIBERATE_EXCEPTION);
        assertAddresses(lb.activeAddresses(), "address-1", "address-2");
    }

    @Test
    public void handleDiscoveryEventsForNotConnectedHosts() {
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
        assertAddresses(lb.activeAddresses(), "address-2", "address-1");

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
}
