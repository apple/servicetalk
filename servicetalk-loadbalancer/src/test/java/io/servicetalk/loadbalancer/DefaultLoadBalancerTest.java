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

import io.servicetalk.client.api.RequestTracker;
import io.servicetalk.client.api.ServiceDiscovererEvent;
import io.servicetalk.concurrent.PublisherSource;
import io.servicetalk.concurrent.api.Processors;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.SourceAdapters;
import io.servicetalk.concurrent.api.TestPublisher;
import io.servicetalk.context.api.ContextMap;
import io.servicetalk.loadbalancer.LoadBalancerObserver.HostObserver;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Single.failed;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultLoadBalancerTest extends LoadBalancerTestScaffold {

    private LoadBalancingPolicy<String, TestLoadBalancedConnection> loadBalancingPolicy =
            LoadBalancingPolicies.p2c().build();

    private Subsetter.SubsetterFactory subsetterFactory = new RandomSubsetter.RandomSubsetterFactory(Integer.MAX_VALUE);

    private Function<String, HostPriorityStrategy> hostPriorityStrategyFactory = DefaultHostPriorityStrategy::new;

    @Nullable
    private Supplier<OutlierDetector<String, TestLoadBalancedConnection>> outlierDetectorFactory;

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
    void setsHostOnHealthIndicator() throws Exception {
        // necessary because we're making a new lb.
        serviceDiscoveryPublisher.onComplete();
        final TestOutlierDetectorFactory factory = new TestOutlierDetectorFactory();
        outlierDetectorFactory = factory;
        lb = newTestLoadBalancer();

        TestOutlierDetector outlierDetector = factory.currentOutlierDetector.get();
        sendServiceDiscoveryEvents(upEvent("address-1"));
        TestHealthIndicator indicator = outlierDetector.getIndicators().stream()
                .filter(i -> "address-1".equals(i.address)).findFirst().get();
        assertNotNull(indicator.host);
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
        final TestOutlierDetectorFactory factory = new TestOutlierDetectorFactory();
        outlierDetectorFactory = factory;
        lb = newTestLoadBalancer();

        TestOutlierDetector outlierDetector = factory.currentOutlierDetector.get();
        assertNotNull(outlierDetector);
        assertThat(outlierDetector.getIndicators(), empty());
        sendServiceDiscoveryEvents(upEvent("address-1"));
        assertThat(outlierDetector.getIndicators(), hasSize(1));
        sendServiceDiscoveryEvents(upEvent("address-2"));
        assertThat(outlierDetector.getIndicators(), hasSize(2));
        // now for the removals.
        sendServiceDiscoveryEvents(downEvent("address-1"));
        assertThat(outlierDetector.getIndicators(), hasSize(1));
        sendServiceDiscoveryEvents(downEvent("address-2"));
        assertThat(outlierDetector.getIndicators(), empty());
    }

    @Test
    void hostsConsiderHealthIndicatorEjectionStatus() throws Exception {
        serviceDiscoveryPublisher.onComplete();
        final TestOutlierDetectorFactory factory = new TestOutlierDetectorFactory();
        outlierDetectorFactory = factory;
        lb = newTestLoadBalancer();

        TestOutlierDetector outlierDetector = factory.currentOutlierDetector.get();
        sendServiceDiscoveryEvents(upEvent("address-1"));
        sendServiceDiscoveryEvents(upEvent("address-2"));
        TestHealthIndicator indicator = outlierDetector.getIndicators().stream()
                .filter(i -> "address-1".equals(i.address)).findFirst().get();
        indicator.isHealthy = false;
        // Now we should always bias toward address-2.
        TestLoadBalancedConnection connection = lb.selectConnection(any(), null).toFuture().get();
        for (int i = 0; i < 5; i++) {
            assertThat(connection.address(), equalTo("address-2"));
        }
    }

    @Test
    void changesToPriorityOrWeightTriggerRebuilds() throws Exception {
        final AtomicReference<Double> value = new AtomicReference<>();
        serviceDiscoveryPublisher.onComplete();
        hostPriorityStrategyFactory = (ignored) -> new HostPriorityStrategy() {
            @Override
            public <T extends PrioritizedHost> List<T> prioritize(List<T> hosts) {
                assert hosts.size() == 1;
                T host = hosts.get(0);
                value.set(host.weight());
                // We want to adjust the weight here so that if the `loadBalancingWeight()` fails to be
                // reset then the test will fail.
                host.weight(0.5 * host.weight());
                return hosts;
            }
        };
        lb = newTestLoadBalancer();
        DefaultLoadBalancer<String, TestLoadBalancedConnection> refinedLb =
                (DefaultLoadBalancer<String, TestLoadBalancedConnection>) lb;

        // use a simple address. Should have a weight of 1.0;
        sendServiceDiscoveryEvents(upEvent("address-1"));
        List<? extends DefaultLoadBalancer.PrioritizedHostImpl<?, ?>> curentHosts = refinedLb.hosts();
        assertThat(curentHosts, hasSize(1));
        assertThat(curentHosts.get(0).priority(), equalTo(0));
        assertThat(curentHosts.get(0).weight(), equalTo(0.5));
        assertThat(value.getAndSet(null), equalTo(1.0));

        // send a new event with a different priority group. This should trigger another build.
        sendServiceDiscoveryEvents(richEvent(upEvent("address-1"), 1.0, 1));
        curentHosts = refinedLb.hosts();
        assertThat(curentHosts, hasSize(1));
        assertThat(curentHosts.get(0).priority(), equalTo(1));
        assertThat(curentHosts.get(0).weight(), equalTo(0.5));
        assertThat(value.getAndSet(null), equalTo(1.0));

        // send a new event with a different weight. This should trigger yet another build.
        sendServiceDiscoveryEvents(richEvent(upEvent("address-1"), 2.0, 1));
        curentHosts = refinedLb.hosts();
        assertThat(curentHosts, hasSize(1));
        assertThat(curentHosts.get(0).priority(), equalTo(1));
        assertThat(curentHosts.get(0).weight(), equalTo(1.0));
        assertThat(value.getAndSet(null), equalTo(2.0));
    }

    @Test
    void outlierDetectorIsClosedOnShutdown() throws Exception {
        serviceDiscoveryPublisher.onComplete();
        final TestOutlierDetectorFactory factory = new TestOutlierDetectorFactory();
        outlierDetectorFactory = factory;
        lb = newTestLoadBalancer();
        lb.closeAsync().toFuture().get();
        assertTrue(factory.currentOutlierDetector.get().cancelled);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, Integer.MAX_VALUE})
    void subsetting(final int subsetSize) throws Exception {
        serviceDiscoveryPublisher.onComplete();
        this.subsetterFactory = new RandomSubsetter.RandomSubsetterFactory(subsetSize);
        // rr so we can test that each endpoint gets used deterministically.
        this.loadBalancingPolicy = LoadBalancingPolicies.roundRobin().build();
        lb = newTestLoadBalancer();
        for (int i = 1; i <= 4; i++) {
            sendServiceDiscoveryEvents(upEvent("address-" + i));
        }

        assertThat(selectConnections(8), hasSize(Math.min(4, subsetSize)));
    }

    @Test
    void subsettingWithUnhealthyHosts() throws Exception {
        serviceDiscoveryPublisher.onComplete();
        final TestOutlierDetectorFactory factory = new TestOutlierDetectorFactory();
        outlierDetectorFactory = factory;
        this.subsetterFactory = new RandomSubsetter.RandomSubsetterFactory(2);
        // rr so we can test that each endpoint gets used deterministically.
        this.loadBalancingPolicy = LoadBalancingPolicies.roundRobin().build();
        lb = newTestLoadBalancer();
        for (int i = 1; i <= 4; i++) {
            sendServiceDiscoveryEvents(upEvent("address-" + i));
        }

        // find out which of our two addresses are in the subset.
        Set<String> selectedAddresses1 = selectConnections(4);
        assertThat(selectedAddresses1, hasSize(2));

        // Make both unhealthy.
        for (TestHealthIndicator i : factory.currentOutlierDetector.get().indicatorSet) {
            if (selectedAddresses1.contains(i.host.address())) {
                i.isHealthy = false;
            }
        }

        // Trigger a rebuild of the subset and make sure that we're now using the other two hosts.
        factory.currentOutlierDetector.get().healthStatusChanged.onNext(null);
        Set<String> selectedAddresses2 = selectConnections(4);
        assertThat(selectedAddresses2, hasSize(2));
        for (String addr2 : selectedAddresses2) {
            assertThat(selectedAddresses1, not(contains(addr2)));
        }

        // Recover the unhealthy endpoints. Based on the current implementation, they will again be
        // selectable until we rebuild.
        for (TestHealthIndicator i : factory.currentOutlierDetector.get().indicatorSet) {
            i.isHealthy = true;
        }

        Set<String> selectedAddresses3 = selectConnections(4);
        assertThat(selectedAddresses3, hasSize(4));

        // Rebuild and we should now eject the trailing endpoings once more and get back to our normal state.
        factory.currentOutlierDetector.get().healthStatusChanged.onNext(null);
        Set<String> selectedAddresses4 = selectConnections(4);
        assertThat(selectedAddresses4, equalTo(selectedAddresses1));
    }

    private Set<String> selectConnections(final int iterations) throws Exception {
        Set<String> result = new HashSet<>();
        for (int i = 0; i < iterations; i++) {
            TestLoadBalancedConnection cxn = lb.selectConnection(any(), null).toFuture().get();
            result.add(cxn.address());
        }
        return result;
    }

    @Override
    TestableLoadBalancer<String, TestLoadBalancedConnection> newTestLoadBalancer(
            TestPublisher<Collection<ServiceDiscovererEvent<String>>> serviceDiscoveryPublisher,
            TestConnectionFactory connectionFactory) {
        Function<String, OutlierDetector<String, TestLoadBalancedConnection>> factory = outlierDetectorFactory == null ?
                    (description) -> new NoopOutlierDetector<>(OutlierDetectorConfig.DEFAULT_CONFIG, testExecutor)
                 : (description) -> outlierDetectorFactory.get();
        return new DefaultLoadBalancer<>(
                getClass().getSimpleName(),
                "test-service",
                serviceDiscoveryPublisher,
                hostPriorityStrategyFactory,
                loadBalancingPolicy,
                subsetterFactory,
                ConnectionSelectorPolicies.linearSearch(),
                connectionFactory,
                NoopLoadBalancerObserver.factory(),
                null,
                factory);
    }

    private RichServiceDiscovererEvent<String> richEvent(
            ServiceDiscovererEvent<String> parent, double weight, int priority) {
        return new RichServiceDiscovererEvent<>(parent.address(), parent.status(), weight, priority);
    }

    private static class TestHealthIndicator implements HealthIndicator<String, TestLoadBalancedConnection> {

        private final Set<TestHealthIndicator> indicatorSet;
        private Host<String, TestLoadBalancedConnection> host;
        final String address;
        volatile boolean isHealthy = true;

        TestHealthIndicator(final Set<TestHealthIndicator> indicatorSet, final String address) {
            this.indicatorSet = indicatorSet;
            this.address = address;
        }

        @Override
        public void setHost(final Host<String, TestLoadBalancedConnection> host) {
            this.host = host;
        }

        @Override
        public int score() {
            return 0;
        }

        @Override
        public long beforeRequestStart() {
            return 0;
        }

        @Override
        public long beforeConnectStart() {
            return 0;
        }

        @Override
        public void onConnectSuccess(long beforeConnectStart) {
        }

        @Override
        public void onConnectError(long beforeConnectStart, ConnectTracker.ErrorClass errorClass) {
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
        public void onRequestSuccess(long beforeStartTime) {
        }

        @Override
        public void onRequestError(long beforeStartTime, RequestTracker.ErrorClass errorClass) {
        }
    }

    private static class TestOutlierDetectorFactory implements
            Supplier<OutlierDetector<String, TestLoadBalancedConnection>> {

        final AtomicReference<TestOutlierDetector> currentOutlierDetector = new AtomicReference<>();

        @Override
        public OutlierDetector<String, TestLoadBalancedConnection> get() {
            assert currentOutlierDetector.get() == null;
            TestOutlierDetector result = new TestOutlierDetector();
            currentOutlierDetector.set(result);
            return result;
        }
    }

    private static class TestOutlierDetector implements OutlierDetector<String, TestLoadBalancedConnection> {

        private final Set<TestHealthIndicator> indicatorSet = new HashSet<>();

        final PublisherSource.Processor<Void, Void> healthStatusChanged = Processors.newPublisherProcessor();
        volatile boolean cancelled;

        @Override
        public void cancel() {
            cancelled = true;
        }

        @Override
        public HealthIndicator<String, TestLoadBalancedConnection> newHealthIndicator(
                String address, HostObserver hostObserver) {
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

        @Override
        public Publisher<Void> healthStatusChanged() {
            return SourceAdapters.fromSource(healthStatusChanged);
        }
    }

    private static class TestLoadBalancerPolicy extends LoadBalancingPolicy<String, TestLoadBalancedConnection> {

        int rebuilds;

        @Override
        String name() {
            return "TestPolicy";
        }

        @Override
        public String toString() {
            return name() + "()";
        }

        @Override
        HostSelector<String, TestLoadBalancedConnection> buildSelector(
                List<Host<String, TestLoadBalancedConnection>> hosts, String lbDescription) {
            return new TestSelector(hosts);
        }

        private class TestSelector implements HostSelector<String, TestLoadBalancedConnection> {

            private final List<? extends Host<String, TestLoadBalancedConnection>> hosts;

            TestSelector(final List<? extends Host<String, TestLoadBalancedConnection>> hosts) {
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
                    List<? extends Host<String, TestLoadBalancedConnection>> hosts) {
                rebuilds++;
                return new TestSelector(hosts);
            }

            @Override
            public boolean isHealthy() {
                return true;
            }

            @Override
            public Collection<? extends LoadBalancerObserver.Host> hosts() {
                return hosts;
            }
        }
    }
}
