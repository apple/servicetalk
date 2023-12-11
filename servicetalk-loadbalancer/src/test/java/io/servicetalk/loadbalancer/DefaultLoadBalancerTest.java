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

import io.servicetalk.client.api.ServiceDiscovererEvent;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.TestPublisher;
import io.servicetalk.context.api.ContextMap;

import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Single.failed;
import static org.junit.jupiter.api.Assertions.assertEquals;

class DefaultLoadBalancerTest extends LoadBalancerTestScaffold {

    private LoadBalancingPolicy<String, TestLoadBalancedConnection> loadBalancingPolicy =
            new P2CLoadBalancingPolicy.Builder().build();

    @Override
    protected boolean eagerConnectionShutdown() {
        return false;
    }

    @Test
    void newHostsRebuildsSelector() throws Exception {
        // necessary because we're making a new lb.
        serviceDiscoveryPublisher.onComplete();

        final TestLoadBalancerPolicy lbPolicy = new TestLoadBalancerPolicy();
        loadBalancingPolicy = lbPolicy;
        lb = newTestLoadBalancer();

        sendServiceDiscoveryEvents(upEvent("address-1"));
        // We should have rebuilt the LB due to a host update.
        assertEquals(1, lbPolicy.rebuilds);
    }

    @Test
    void removingHostsRebuildsSelector() throws Exception {
        // necessary because we're making a new lb.
        serviceDiscoveryPublisher.onComplete();

        final TestLoadBalancerPolicy lbPolicy = new TestLoadBalancerPolicy();
        loadBalancingPolicy = lbPolicy;
        lb = newTestLoadBalancer();

        sendServiceDiscoveryEvents(upEvent("address-1"));
        // We should have rebuilt the LB due to a host update.
        assertEquals(1, lbPolicy.rebuilds);
        // take it back down immediate. No connections means we close in the sd event.
        sendServiceDiscoveryEvents(downEvent("address-1"));
        assertEquals(2, lbPolicy.rebuilds);
    }

    @Test
    void lazyHostExpirationRebuildsSelector() throws Exception {
        // necessary because we're making a new lb.
        serviceDiscoveryPublisher.onComplete();

        final TestLoadBalancerPolicy lbPolicy = new TestLoadBalancerPolicy();
        loadBalancingPolicy = lbPolicy;
        lb = newTestLoadBalancer();

        sendServiceDiscoveryEvents(upEvent("address-1"));
        // We should have rebuilt the LB due to a host update.
        assertEquals(1, lbPolicy.rebuilds);
        // should be an expired but not gone yet because a connection remains.
        TestLoadBalancedConnection cxn = lb.selectConnection(any(), null).toFuture().get();
        sendServiceDiscoveryEvents(downEvent("address-1"));
        assertEquals(1, lbPolicy.rebuilds);
        // Close the connection and we should see a rebuild.
        cxn.closeAsync().subscribe();
        assertEquals(2, lbPolicy.rebuilds);
    }

    @Override
    protected final TestableLoadBalancer<String, TestLoadBalancedConnection> newTestLoadBalancer(
            final TestPublisher<Collection<ServiceDiscovererEvent<String>>> serviceDiscoveryPublisher,
            final LoadBalancerTest.DelegatingConnectionFactory connectionFactory) {
        return (TestableLoadBalancer<String, TestLoadBalancedConnection>)
                baseLoadBalancerBuilder()
                        .loadBalancingPolicy(loadBalancingPolicy)
                        .healthCheckFailedConnectionsThreshold(-1)
                        .backgroundExecutor(testExecutor)
                        .build()
                        .newLoadBalancer(serviceDiscoveryPublisher, connectionFactory, "test-service");
    }

    private LoadBalancerBuilder<String, TestLoadBalancedConnection> baseLoadBalancerBuilder() {
        return LoadBalancers.<String, TestLoadBalancedConnection>builder(getClass().getSimpleName())
                .loadBalancingPolicy(new P2CLoadBalancingPolicy.Builder().build());
    }

    private static class TestLoadBalancerPolicy implements LoadBalancingPolicy<String, TestLoadBalancedConnection> {

        int rebuilds;

        @Override
        public String name() {
            return "test-selector";
        }

        @Override
        public HostSelector<String, TestLoadBalancedConnection> buildSelector(
                List<Host<String, TestLoadBalancedConnection>> hosts, String targetResource) {
            return new TestSelector(hosts);
        }

        private class TestSelector implements HostSelector<String, TestLoadBalancedConnection> {

            private final List<Host<String, TestLoadBalancedConnection>> hosts;

            TestSelector(final List<Host<String, TestLoadBalancedConnection>> hosts) {
                this.hosts = hosts;
            }

            @Override
            public Single<TestLoadBalancedConnection> selectConnection(
                    Predicate<TestLoadBalancedConnection> selector, @Nullable ContextMap context,
                    boolean forceNewConnectionAndReserve) {
                return hosts.isEmpty() ? failed(new IllegalStateException("shouldn't be empty"))
                        : hosts.get(0).newConnection(selector, false, context);
            }

            @Override
            public HostSelector<String, TestLoadBalancedConnection> rebuildWithHosts(
                    List<Host<String, TestLoadBalancedConnection>> hosts) {
                rebuilds++;
                return new TestSelector(hosts);
            }

            @Override
            public boolean isUnHealthy() {
                return false;
            }
        }
    }
}
