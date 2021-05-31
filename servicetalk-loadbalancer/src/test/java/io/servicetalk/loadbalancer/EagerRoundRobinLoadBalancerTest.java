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

import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class EagerRoundRobinLoadBalancerTest extends RoundRobinLoadBalancerTest {

    @Test
    public void addressIsAddedTwice() {
        assertThat(lb.activeAddresses(), is(empty()));
        sendServiceDiscoveryEvents(upEvent("address-1"));
        assertThat(lb.activeAddresses(), hasSize(1));
        sendServiceDiscoveryEvents(upEvent("address-1"));
        assertThat(lb.activeAddresses(), hasSize(2));

        sendServiceDiscoveryEvents(downEvent("address-1"));
        assertThat(lb.activeAddresses(), hasSize(1));
        sendServiceDiscoveryEvents(downEvent("address-1"));
        assertThat(lb.activeAddresses(), hasSize(0));
    }

    @Test
    public void handleDiscoveryEvents() {
        assertAddresses(lb.activeAddresses(), EMPTY_ARRAY);

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

        sendServiceDiscoveryEvents(downEvent("address-3"));
        assertAddresses(lb.activeAddresses(), EMPTY_ARRAY);

        // Let's make sure that an SD failure doesn't compromise LB's internal state
        sendServiceDiscoveryEvents(upEvent("address-1"));
        assertAddresses(lb.activeAddresses(), "address-1");

        serviceDiscoveryPublisher.onError(DELIBERATE_EXCEPTION);
        assertAddresses(lb.activeAddresses(), "address-1");
    }

    @Test
    public void hostDownGracefulCloseConnection() throws Exception {
        sendServiceDiscoveryEvents(upEvent("address-1"));
        TestLoadBalancedConnection conn = lb.selectConnection(any()).toFuture().get();
        sendServiceDiscoveryEvents(downEvent("address-1"));
        conn.onClose().toFuture().get();
        verify(conn).closeAsyncGracefully();
        verify(conn, times(0)).closeAsync();
    }

    @Override
    protected RoundRobinLoadBalancer<String, TestLoadBalancedConnection> defaultLb() {
        return newTestLoadBalancer(true);
    }

    @Override
    protected RoundRobinLoadBalancer<String, TestLoadBalancedConnection> defaultLb(
            RoundRobinLoadBalancerTest.DelegatingConnectionFactory connectionFactory) {
        return newTestLoadBalancer(serviceDiscoveryPublisher, connectionFactory, true);
    }
}
