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

import io.servicetalk.client.api.ConnectionRejectedException;
import io.servicetalk.client.api.NoAvailableHostException;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Executors;

import org.junit.Test;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.either;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertNull;
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
        assertAddresses(lb.usedAddresses(), EMPTY_ARRAY);
    }

    @Test
    public void closedConnectionRemovesExpiredHost() throws Exception {
        sendServiceDiscoveryEvents(upEvent("address-1"));
        final Predicate<TestLoadBalancedConnection> connectionFilter = alwaysNewConnectionFilter();

        TestLoadBalancedConnection conn1 = lb.selectConnection(connectionFilter).toFuture().get();
        TestLoadBalancedConnection conn2 = lb.selectConnection(connectionFilter).toFuture().get();
        assertConnectionCount(lb.usedAddresses(), connectionsCount("address-1", 2));

        sendServiceDiscoveryEvents(downEvent("address-1"));
        assertConnectionCount(lb.usedAddresses(), connectionsCount("address-1", 2));

        conn1.closeAsync().toFuture().get();
        assertConnectionCount(lb.usedAddresses(), connectionsCount("address-1", 1));

        conn2.closeAsync().toFuture().get();
        assertAddresses(lb.usedAddresses(), EMPTY_ARRAY);
    }

    // Concurrency test, worth running ~10K times to spot concurrency issues.
    @Test
    public void closureOfLastConnectionDoesntRaceWithNewAvailableEvent() throws Exception {
        Executor executor = Executors.newFixedSizeExecutor(1);
        try {
            sendServiceDiscoveryEvents(upEvent("address-1"));
            TestLoadBalancedConnection conn = lb.selectConnection(alwaysNewConnectionFilter()).toFuture().get();
            sendServiceDiscoveryEvents(downEvent("address-1"));
            assertConnectionCount(lb.usedAddresses(), connectionsCount("address-1", 1));

            Future<Object> f = executor.submit(() -> {
                sendServiceDiscoveryEvents(upEvent("address-1"));
                return null;
            }).toFuture();

            conn.closeAsync().toFuture().get();
            f.get();
            assertConnectionCount(lb.usedAddresses(), connectionsCount("address-1", 0));
        } finally {
            executor.closeAsync().toFuture().get();
        }
    }

    // Concurrency test, worth running >10K times to spot concurrency issues.
    @Test
    public void expiringAHostDoesntRaceWithConnectionAdding() throws Exception {
        Executor executor = Executors.newFixedSizeExecutor(1);
        try {
            sendServiceDiscoveryEvents(upEvent("address-1"));
            assertConnectionCount(lb.usedAddresses(), connectionsCount("address-1", 0));

            AtomicReference<Exception> e = new AtomicReference<>();
            AtomicReference<TestLoadBalancedConnection> connection = new AtomicReference<>();

            Future<Object> f = executor.submit(() -> {
                try {
                     connection.set(lb.selectConnection(alwaysNewConnectionFilter()).toFuture().get());
                } catch (Exception ex) {
                    e.set(ex);
                }
                return null;
            }).toFuture();

            sendServiceDiscoveryEvents(downEvent("address-1"));
            f.get();

            Exception thrown = e.get();
            if (thrown != null) {
                // Connection was not added - SD event arrived before the attempt of adding the connection
                assertThat(thrown, instanceOf(ExecutionException.class));
                // Either the host was already CLOSED and removed from the usedHosts collection:
                assertThat(thrown.getCause(),
                        either(instanceOf(NoAvailableHostException.class))
                                // Or we selected the host and in the meantime it entered the CLOSED state:
                                .or(instanceOf(ConnectionRejectedException.class)));
                assertAddresses(lb.usedAddresses(), EMPTY_ARRAY);
                assertNull(connection.get());
            } else {
                // Connection was added first -> Let's validate the host was properly EXPIRED:
                assertConnectionCount(lb.usedAddresses(), connectionsCount("address-1", 1));

                // Confirm host is expired:
                ExecutionException ex = assertThrows(ExecutionException.class,
                        () -> lb.selectConnection(alwaysNewConnectionFilter()).toFuture().get());
                assertThat(ex.getCause(), instanceOf(NoAvailableHostException.class));

                lb.closeAsyncGracefully().toFuture().get();
                verify(connection.get(), times(1)).closeAsyncGracefully();
            }
        } finally {
            executor.closeAsync().toFuture().get();
        }
    }

    // Concurrency test, worth running >10K times to spot concurrency issues.
    @Test
    public void expiringHostWhileConnectionsClose() throws Exception {
        Executor executor = Executors.newFixedSizeExecutor(1);
        try {
            sendServiceDiscoveryEvents(upEvent("address-1"));
            assertConnectionCount(lb.usedAddresses(), connectionsCount("address-1", 0));

            TestLoadBalancedConnection conn1 = lb.selectConnection(alwaysNewConnectionFilter()).toFuture().get();
            TestLoadBalancedConnection conn2 = lb.selectConnection(alwaysNewConnectionFilter()).toFuture().get();

            Future<Object> f = executor.submit(() -> {
                sendServiceDiscoveryEvents(downEvent("address-1"));
                return null;
            }).toFuture();

            conn1.closeAsync().toFuture().get();
            conn2.closeAsync().toFuture().get();
            f.get();

            assertAddresses(lb.usedAddresses(), EMPTY_ARRAY);
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
        assertConnectionCount(lb.usedAddresses(), connectionsCount("address-1", 2));
        conn1.closeAsync().toFuture().get();
        assertConnectionCount(lb.usedAddresses(), connectionsCount("address-1", 1));
        conn2.closeAsync().toFuture().get();
        assertConnectionCount(lb.usedAddresses(), connectionsCount("address-1", 0));
    }

    @Test
    public void handleDiscoveryEventsForExpiredHostBecomingAvailable() throws Exception {
        assertAddresses(lb.usedAddresses(), EMPTY_ARRAY);

        sendServiceDiscoveryEvents(upEvent("address-1"));
        assertAddresses(lb.usedAddresses(), "address-1");

        lb.selectConnection(any()).toFuture().get();
        assertConnectionCount(lb.usedAddresses(), connectionsCount("address-1", 1));

        sendServiceDiscoveryEvents(downEvent("address-1"));
        assertConnectionCount(lb.usedAddresses(), connectionsCount("address-1", 1));
        assertThat(lb.selectConnection(any()).toFuture().get(), is(notNullValue()));

        // We validate the host is expired by attempting to create a new connection
        final Predicate<TestLoadBalancedConnection> createNewConnection = alwaysNewConnectionFilter();
        Exception e = assertThrows(ExecutionException.class, () ->
                lb.selectConnection(createNewConnection).toFuture().get());
        assertThat(e.getCause(), instanceOf(NoAvailableHostException.class));

        // When the host becomes available again, new connections can be created
        sendServiceDiscoveryEvents(upEvent("address-1"));
        assertAddresses(lb.usedAddresses(), "address-1");
        lb.selectConnection(createNewConnection).toFuture().get();
        assertConnectionCount(lb.usedAddresses(), connectionsCount("address-1", 2));
    }

    @Test
    public void handleDiscoveryEventsForConnectedHosts() throws Exception {
        assertThat(lb.usedAddresses(), is(empty()));

        final Predicate<TestLoadBalancedConnection> connectionFilter = alwaysNewConnectionFilter();

        sendServiceDiscoveryEvents(upEvent("address-1"));
        assertAddresses(lb.usedAddresses(), "address-1");
        // For an added host, connection needs to be initiated, otherwise the host is free to be deleted
        lb.selectConnection(connectionFilter).toFuture().get();

        sendServiceDiscoveryEvents(downEvent("address-1"));
        assertAddresses(lb.usedAddresses(), "address-1");

        sendServiceDiscoveryEvents(upEvent("address-2"));
        TestLoadBalancedConnection address2connection = lb.selectConnection(connectionFilter).toFuture().get();

        assertAddresses(lb.usedAddresses(), "address-1", "address-2");

        sendServiceDiscoveryEvents(downEvent("address-3"));
        assertAddresses(lb.usedAddresses(), "address-1", "address-2");

        // Marking the first host as not expired should not create duplicates
        sendServiceDiscoveryEvents(upEvent("address-1"));
        assertAddresses(lb.usedAddresses(), "address-1", "address-2");

        // When all hosts are active, duplicate event is ignored
        sendServiceDiscoveryEvents(upEvent("address-1"));
        assertAddresses(lb.usedAddresses(), "address-1", "address-2");

        // The second host has a connection open, so it stays as "expired".
        sendServiceDiscoveryEvents(downEvent("address-2"));
        assertAddresses(lb.usedAddresses(), "address-1", "address-2");

        // Closing an expired host's connection should remove the host from the list
        address2connection.closeAsync().toFuture().get();
        assertAddresses(lb.usedAddresses(), "address-1");

        // Let's make sure that an SD failure doesn't compromise LB's internal state
        serviceDiscoveryPublisher.onError(DELIBERATE_EXCEPTION);
        assertAddresses(lb.usedAddresses(), "address-1");
    }

    @Test
    public void handleDiscoveryEventsForNotConnectedHosts() {
        assertThat(lb.usedAddresses(), is(empty()));

        sendServiceDiscoveryEvents(upEvent("address-1"));
        assertAddresses(lb.usedAddresses(), "address-1");

        sendServiceDiscoveryEvents(downEvent("address-1"));
        assertAddresses(lb.usedAddresses(), EMPTY_ARRAY);

        sendServiceDiscoveryEvents(upEvent("address-2"));
        assertAddresses(lb.usedAddresses(), "address-2");

        sendServiceDiscoveryEvents(downEvent("address-3"));
        assertAddresses(lb.usedAddresses(), "address-2");

        sendServiceDiscoveryEvents(upEvent("address-1"));
        assertAddresses(lb.usedAddresses(), "address-2", "address-1");

        sendServiceDiscoveryEvents(downEvent("address-1"));
        assertAddresses(lb.usedAddresses(), "address-2");

        sendServiceDiscoveryEvents(downEvent("address-2"));
        assertAddresses(lb.usedAddresses(), EMPTY_ARRAY);

        // Let's make sure that an SD failure doesn't compromise LB's internal state
        sendServiceDiscoveryEvents(upEvent("address-1"));
        assertAddresses(lb.usedAddresses(), "address-1");

        serviceDiscoveryPublisher.onError(DELIBERATE_EXCEPTION);
        assertAddresses(lb.usedAddresses(), "address-1");
    }

    @Override
    protected boolean eagerConnectionShutdown() {
        return false;
    }
}
