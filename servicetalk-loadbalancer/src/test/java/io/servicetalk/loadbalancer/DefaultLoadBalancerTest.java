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
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.TestPublisher;
import io.servicetalk.context.api.ContextMap;
import io.servicetalk.loadbalancer.LoadBalancerObserver.HostObserver;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Single.failed;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultLoadBalancerTest extends LoadBalancerTestScaffold {

    private LoadBalancingPolicy<String, TestLoadBalancedConnection> loadBalancingPolicy =
            new P2CLoadBalancingPolicy.Builder().build();
    @Nullable
    private HealthCheckerFactory healthCheckerFactory;

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

    @Test
    void hostHealthIndicatorLifeCycleManagement() {
        serviceDiscoveryPublisher.onComplete();
        final TestHealthCheckerFactory factory = new TestHealthCheckerFactory();
        healthCheckerFactory = factory;
        lb = newTestLoadBalancer();

        TestHealthChecker healthChecker = factory.currentHealthChecker.get();
        assertNotNull(healthChecker);
        assertThat(healthChecker.getIndicators(), empty());
        sendServiceDiscoveryEvents(upEvent("address-1"));
        assertThat(healthChecker.getIndicators(), hasSize(1));
        sendServiceDiscoveryEvents(upEvent("address-2"));
        assertThat(healthChecker.getIndicators(), hasSize(2));
        // now for the removals.
        sendServiceDiscoveryEvents(downEvent("address-1"));
        assertThat(healthChecker.getIndicators(), hasSize(1));
        sendServiceDiscoveryEvents(downEvent("address-2"));
        assertThat(healthChecker.getIndicators(), empty());
    }

    @Test
    void hostsConsiderHealthIndicatorEjectionStatus() throws Exception {
        serviceDiscoveryPublisher.onComplete();
        final TestHealthCheckerFactory factory = new TestHealthCheckerFactory();
        healthCheckerFactory = factory;
        lb = newTestLoadBalancer();

        TestHealthChecker healthChecker = factory.currentHealthChecker.get();
        sendServiceDiscoveryEvents(upEvent("address-1"));
        sendServiceDiscoveryEvents(upEvent("address-2"));
        TestHealthIndicator indicator = healthChecker.getIndicators().stream()
                .filter(i -> "address-1".equals(i.address)).findFirst().get();
        indicator.isHealthy = false;
        // Now we should always bias toward address-2.
        TestLoadBalancedConnection connection = lb.selectConnection(any(), null).toFuture().get();
        for (int i = 0; i < 5; i++) {
            assertThat(connection.address(), equalTo("address-2"));
        }
    }

    @Test
    void healthCheckerIsClosedOnShutdown() throws Exception {
        serviceDiscoveryPublisher.onComplete();
        final TestHealthCheckerFactory factory = new TestHealthCheckerFactory();
        healthCheckerFactory = factory;
        lb = newTestLoadBalancer();
        lb.closeAsync().toFuture().get();
        assertTrue(factory.currentHealthChecker.get().cancelled);
    }

    private LoadBalancerBuilder<String, TestLoadBalancedConnection> baseLoadBalancerBuilder() {
        return LoadBalancers.<String, TestLoadBalancedConnection>builder(getClass().getSimpleName())
                .loadBalancingPolicy(new P2CLoadBalancingPolicy.Builder().build());
    }

    @Override
    protected final TestableLoadBalancer<String, TestLoadBalancedConnection> newTestLoadBalancer(
            final TestPublisher<Collection<ServiceDiscovererEvent<String>>> serviceDiscoveryPublisher,
            final TestConnectionFactory connectionFactory) {
        return (TestableLoadBalancer<String, TestLoadBalancedConnection>)
                baseLoadBalancerBuilder()
                        .loadBalancingPolicy(loadBalancingPolicy)
                        .healthCheckerFactory(healthCheckerFactory)
                        .healthCheckFailedConnectionsThreshold(-1)
                        .backgroundExecutor(testExecutor)
                        .build()
                        .newLoadBalancer(serviceDiscoveryPublisher, connectionFactory, "test-service");
    }

    private static class TestHealthIndicator implements HealthIndicator {

        private final Set<TestHealthIndicator> indicatorSet;
        final String address;
        volatile boolean isHealthy = true;

        TestHealthIndicator(final Set<TestHealthIndicator> indicatorSet, final String address) {
            this.indicatorSet = indicatorSet;
            this.address = address;
        }

        @Override
        public int score() {
            return 0;
        }

        @Override
        public long beforeStart() {
            return 0;
        }

        @Override
        public long currentTimeNanos() {
            return 0;
        }

        @Override
        public void onConnectFailure(long startTimeNanos) {
        }

        @Override
        public void cancel() {
            synchronized (indicatorSet) {
                assert indicatorSet.remove(this);
            }
        }

        @Override
        public boolean isHealthy() {
            return isHealthy;
        }

        @Override
        public void onSuccess(long beforeStartTime) {
        }

        @Override
        public void onError(long beforeStartTime, ErrorClass errorClass) {
        }
    }

    private static class TestHealthCheckerFactory implements HealthCheckerFactory<String> {

        final AtomicReference<TestHealthChecker> currentHealthChecker = new AtomicReference<>();
        @Override
        public HealthChecker newHealthChecker(Executor executor, HostObserver<String> hostObserver) {
            assert currentHealthChecker.get() == null;
            TestHealthChecker result = new TestHealthChecker();
            currentHealthChecker.set(result);
            return result;
        }
    }

    private static class TestHealthChecker implements HealthChecker<String> {

        private final Set<TestHealthIndicator> indicatorSet = new HashSet<>();
        volatile boolean cancelled;

        @Override
        public void cancel() {
            cancelled = true;
        }

        @Override
        public HealthIndicator newHealthIndicator(String address) {
            TestHealthIndicator healthIndicator = new TestHealthIndicator(indicatorSet, address);
            synchronized (indicatorSet) {
                indicatorSet.add(healthIndicator);
            }
            return healthIndicator;
        }

        List<TestHealthIndicator> getIndicators() {
            synchronized (indicatorSet) {
                return new ArrayList<>(indicatorSet);
            }
        }
    }

    private static class TestLoadBalancerPolicy implements LoadBalancingPolicy<String, TestLoadBalancedConnection> {

        int rebuilds;

        @Override
        public String name() {
            return "test-selector";
        }

        @Override
        public <T extends TestLoadBalancedConnection> HostSelector<String, T> buildSelector(
                List<Host<String, T>> hosts, String targetResource) {
            return new TestSelector(hosts);
        }

        private class TestSelector<C extends TestLoadBalancedConnection> implements HostSelector<String, C> {

            private final List<? extends Host<String, C>> hosts;

            TestSelector(final List<? extends Host<String, C>> hosts) {
                this.hosts = hosts;
            }

            @Override
            public Single<C> selectConnection(
                    Predicate<C> selector, @Nullable ContextMap context,
                    boolean forceNewConnectionAndReserve) {
                return hosts.isEmpty() ? failed(new IllegalStateException("shouldn't be empty"))
                        : hosts.get(0).newConnection(selector, false, context);
            }

            @Override
            public HostSelector<String, C> rebuildWithHosts(
                    List<? extends Host<String, C>> hosts) {
                rebuilds++;
                return new TestSelector(hosts);
            }

            @Override
            public boolean isHealthy() {
                return true;
            }

            @Override
            public int hostSetSize() {
                return hosts.size();
            }
        }
    }
}
