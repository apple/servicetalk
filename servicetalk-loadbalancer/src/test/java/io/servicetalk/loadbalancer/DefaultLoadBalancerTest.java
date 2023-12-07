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
import io.servicetalk.concurrent.api.TestPublisher;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

import java.util.Collection;
import java.util.List;
import javax.annotation.Nonnull;

import static io.servicetalk.concurrent.api.Single.succeeded;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultLoadBalancerTest extends LoadBalancerTestScaffold {

    private LoadBalancingPolicy<String, TestLoadBalancedConnection> loadBalancingPolicy =
            new P2CLoadBalancingPolicy.Builder().build();

    @Override
    protected boolean eagerConnectionShutdown() {
        return false;
    }

    @Test
    void hostUpdatesRebuildsSelector() {
        // necessary because we're making a new lb.
        serviceDiscoveryPublisher.onComplete();

        final TestLoadBalancerPolicy lbPolicy = new TestLoadBalancerPolicy();
        loadBalancingPolicy = lbPolicy;
        lb = newTestLoadBalancer();

        sendServiceDiscoveryEvents(upEvent("address-1"));
        sendServiceDiscoveryEvents(downEvent("address-1"));
        verify(lbPolicy.hostSelector, times(2)).rebuildWithHosts(ArgumentMatchers.anyList());
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

        HostSelector<String, TestLoadBalancedConnection> hostSelector;

        TestLoadBalancerPolicy() {
            TestLoadBalancedConnection connection = TestLoadBalancedConnection.mockConnection("address");
            hostSelector = mock(HostSelector.class);
            when(hostSelector.selectConnection(ArgumentMatchers.any(), ArgumentMatchers.any(),
                    ArgumentMatchers.anyBoolean()))
                    .thenReturn(succeeded(connection));
            when(hostSelector.rebuildWithHosts(ArgumentMatchers.anyList()))
                    .thenReturn(hostSelector);
        }

        @Override
        public String name() {
            return "test-selector";
        }

        @Override
        public HostSelector<String, TestLoadBalancedConnection> buildSelector(
                @Nonnull List<Host<String, TestLoadBalancedConnection>> hosts, String targetResource) {
            return hostSelector;
        }
    }
}
