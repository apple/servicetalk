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

import org.junit.jupiter.api.Test;

import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class EagerRoundRobinLoadBalancerTest extends RoundRobinLoadBalancerTest {

    @Test
    void duplicateEventsAreIgnored() {
        assertThat(lb.usedAddresses(), is(empty()));

        sendServiceDiscoveryEvents(upEvent("address-1"));
        assertThat(lb.usedAddresses(), hasSize(1));
        sendServiceDiscoveryEvents(upEvent("address-1"));
        assertThat(lb.usedAddresses(), hasSize(1));

        sendServiceDiscoveryEvents(downEvent("address-1"));
        assertThat(lb.usedAddresses(), hasSize(0));
        sendServiceDiscoveryEvents(downEvent("address-1"));
        assertThat(lb.usedAddresses(), hasSize(0));
    }

    @Test
    void handleDiscoveryEvents() {
        assertAddresses(lb.usedAddresses(), EMPTY_ARRAY);

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

        sendServiceDiscoveryEvents(downEvent("address-3"));
        assertAddresses(lb.usedAddresses(), EMPTY_ARRAY);

        // Let's make sure that an SD failure doesn't compromise LB's internal state
        sendServiceDiscoveryEvents(upEvent("address-1"));
        assertAddresses(lb.usedAddresses(), "address-1");

        serviceDiscoveryPublisher.onError(DELIBERATE_EXCEPTION);
        assertAddresses(lb.usedAddresses(), "address-1");
    }

    @Test
    void hostDownGracefullyClosesConnections() throws Exception {
        sendServiceDiscoveryEvents(upEvent("address-1"));
        TestLoadBalancedConnection host1Conn1 = lb.selectConnection(alwaysNewConnectionFilter(), null).toFuture().get();

        sendServiceDiscoveryEvents(upEvent("address-2"));
        TestLoadBalancedConnection host2Conn1 = lb.selectConnection(alwaysNewConnectionFilter(), null).toFuture().get();

        // create another for address-1
        TestLoadBalancedConnection host1Conn2 = lb.selectConnection(alwaysNewConnectionFilter(), null).toFuture().get();

        sendServiceDiscoveryEvents(downEvent("address-1"));
        validateConnectionClosedGracefully(host1Conn1);
        validateConnectionClosedGracefully(host1Conn2);

        // The remaining Host's connections should not be closed
        assertConnectionCount(lb.usedAddresses(), connectionsCount("address-2", 1));
        verify(host2Conn1, never()).closeAsync();
        verify(host2Conn1, never()).closeAsyncGracefully();
    }

    private void validateConnectionClosedGracefully(final TestLoadBalancedConnection connection) throws Exception {
        connection.onClose().toFuture().get();
        verify(connection).closeAsyncGracefully();
        verify(connection, never()).closeAsync();
    }

    @Override
    protected boolean eagerConnectionShutdown() {
        return true;
    }
}
