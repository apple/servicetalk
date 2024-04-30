/*
 * Copyright © 2024 Apple Inc. and the ServiceTalk project authors
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

import org.hamcrest.Matcher;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.equalTo;

public class DefaultHostPriorityStrategyTest {

    private final HostPriorityStrategy defaultHostPriorityStrategy = new DefaultHostPriorityStrategy();

    @Test
    void noPriorities() {
        List<TestPrioritizedHost> hosts = makeHosts(4);
        List<TestPrioritizedHost> result = defaultHostPriorityStrategy.prioritize(hosts);
        assertThat(result.size(), equalTo(hosts.size()));

        for (int i = 0; i < hosts.size(); i++) {
            assertThat(result.get(i).address(), equalTo(hosts.get(i).address()));
            // It doesn't matter what they are exactly so long as all weights are equal.
            assertThat(result.get(i).loadBalancedWeight(), equalTo(result.get(0).loadBalancedWeight()));
        }
    }

    @Test
    void noPrioritiesWithWeights() {
        List<TestPrioritizedHost> hosts = makeHosts(4);
        for (int i = 0; i < hosts.size(); i++) {
            hosts.get(i).intrinsicWeight(i + 1d);
        }
        List<TestPrioritizedHost> result = defaultHostPriorityStrategy.prioritize(hosts);
        assertThat(result.size(), equalTo(hosts.size()));

        for (int i = 0; i < hosts.size(); i++) {
            assertThat(result.get(i).address(), equalTo(hosts.get(i).address()));
            // It doesn't matter what they are exactly so long as all weights are equal.
            assertThat(result.get(i).loadBalancedWeight(), approxEqual(result.get(0).loadBalancedWeight() * (i + 1)));
        }
    }

    @Test
    void twoPrioritiesNoWeights() {
        List<TestPrioritizedHost> hosts = makeHosts(6);
        for (int i = 3; i < 6; i++) {
            hosts.get(i).priority(1);
        }
        List<TestPrioritizedHost> result = defaultHostPriorityStrategy.prioritize(hosts);
        assertThat(result.size(), equalTo(3));

        for (int i = 0; i < 3; i++) {
            assertThat(result.get(i).address(), equalTo(hosts.get(i).address()));
            // It doesn't matter what they are exactly so long as all weights are equal.
            assertThat(result.get(i).loadBalancedWeight(), equalTo(result.get(0).loadBalancedWeight()));
        }
    }

    @Test
    void twoPrioritiesWithWeights() {
        List<TestPrioritizedHost> hosts = makeHosts(6);
        for (int i = 0; i < hosts.size(); i++) {
            hosts.get(i).intrinsicWeight(i + 1d);
            if (i >= 3) {
                hosts.get(i).priority(1);
            }
        }
        List<TestPrioritizedHost> result = defaultHostPriorityStrategy.prioritize(hosts);
        assertThat(result.size(), equalTo(3));

        // We should only have the first three hosts because they were all healthy, so they are the only group.
        for (int i = 0; i < 3; i++) {
            assertThat(result.get(i).address(), equalTo(hosts.get(i).address()));
            // It doesn't matter what they are exactly so long as all weights are equal.
            assertThat(result.get(i).loadBalancedWeight(), approxEqual(result.get(0).loadBalancedWeight() * (i + 1)));
        }
    }

    @Test
    void priorityGroupsWithoutUnhealthyNodes() {
        List<TestPrioritizedHost> hosts = makeHosts(6);
        for (int i = 3; i < hosts.size(); i++) {
            hosts.get(i).priority(1);
        }
        List<TestPrioritizedHost> result = new DefaultHostPriorityStrategy(100).prioritize(hosts);

        assertThat(result.size(), equalTo(3));

        // We should only have the first three hosts because they were all healthy, so they are the only group.
        for (int i = 0; i < 3; i++) {
            assertThat(result.get(i).address(), equalTo(hosts.get(i).address()));
            // It doesn't matter what they are exactly so long as all weights are equal.
            assertThat(result.get(i).loadBalancedWeight(), approxEqual(result.get(0).loadBalancedWeight()));
        }
    }

    @Test
    void priorityGroupsWithUnhealthyNodes() {
        List<TestPrioritizedHost> hosts = makeHosts(6);
        hosts.get(0).isHealthy(false);
        for (int i = 3; i < hosts.size(); i++) {
            hosts.get(i).priority(1);
        }
        List<TestPrioritizedHost> result = new DefaultHostPriorityStrategy(100).prioritize(hosts);

        assertThat(result.size(), equalTo(6));

        // We should only have the first three hosts because they were all healthy, so they are the only group.
        for (int i = 0; i < 3; i++) {
            assertThat(result.get(i).address(), equalTo(hosts.get(i).address()));
            // It doesn't matter what they are exactly so long as all weights are equal.
            assertThat(result.get(i).loadBalancedWeight(), approxEqual(result.get(0).loadBalancedWeight()));
        }
        for (int i = 3; i < 6; i++) {
            assertThat(result.get(i).address(), equalTo(hosts.get(i).address()));
            // It doesn't matter what they are exactly so long as all weights are equal.
            assertThat(result.get(i).loadBalancedWeight(), approxEqual(result.get(3).loadBalancedWeight()));
        }
        // Now the relative weights between the two groups should be 66 / 34 as the first group will have 66% health
        // and the second, while having 100% healthy, will only be able to pick up the slack.
        assertThat(result.get(0).loadBalancedWeight(), approxEqual(result.get(3).loadBalancedWeight() * 66 / 34));
    }

    @Test
    void priorityGroupsWithUnhealthyNodesTotallingLessThan100Percent() {
        List<TestPrioritizedHost> hosts = makeHosts(6);
        for (int i = 0; i < hosts.size(); i++) {
            if (i >= 3) {
                hosts.get(i).priority(1);
            }
            hosts.get(i).isHealthy(false);
        }
        hosts.get(0).isHealthy(true);
        List<TestPrioritizedHost> result = new DefaultHostPriorityStrategy(100).prioritize(hosts);

        assertThat(result.size(), equalTo(3));

        // We should only have the first three hosts because while they didn't form a full healthy set the P1 group
        // didn't provide _any_ healthy nodes, so no need to spill over.
        for (int i = 0; i < 3; i++) {
            assertThat(result.get(i).address(), equalTo(hosts.get(i).address()));
            // It doesn't matter what they are exactly so long as all weights are equal.
            assertThat(result.get(i).loadBalancedWeight(), approxEqual(result.get(0).loadBalancedWeight()));
        }
    }

    @Test
    void priorityGroupsWithWeightedUnhealthyNodes() {
        List<TestPrioritizedHost> hosts = makeHosts(6);
        hosts.get(0).isHealthy(false);
        for (int i = 0; i < hosts.size(); i++) {
            if (i >= 3) {
                hosts.get(i).intrinsicWeight(i - 3 + 1d);
                hosts.get(i).priority(1);
            } else {
                hosts.get(i).intrinsicWeight(i + 1d);
            }
        }
        List<TestPrioritizedHost> result = new DefaultHostPriorityStrategy(100).prioritize(hosts);

        assertThat(result.size(), equalTo(6));

        // We should only have the first three hosts because they were all healthy, so they are the only group.
        for (int i = 0; i < 3; i++) {
            assertThat(result.get(i).address(), equalTo(hosts.get(i).address()));
            // It doesn't matter what they are exactly so long as all weights are equal.
            assertThat(result.get(i).loadBalancedWeight(), approxEqual(result.get(0).loadBalancedWeight() * (i + 1)));
        }
        for (int i = 3; i < 6; i++) {
            assertThat(result.get(i).address(), equalTo(hosts.get(i).address()));
            // It doesn't matter what they are exactly so long as all weights are equal.
            assertThat(result.get(i).loadBalancedWeight(), approxEqual(result.get(3).loadBalancedWeight() * (i + 1 - 3)));
        }
        // Now the relative weights between the two groups should be 66 / 34 as the first group will have 66% health
        // and the second, while having 100% healthy, will only be able to pick up the slack.
        for (int i = 0; i < 3; i++) {
            assertThat(result.get(i).loadBalancedWeight(), approxEqual(result.get(i + 3).loadBalancedWeight() * 66 / 34));
        }
    }

    private static List<TestPrioritizedHost> makeHosts(int count) {
        String[] addresses = new String[count];
        for (int i = 0; i < count; i++) {
            addresses[i] = "addr-" + i;
        }
        return makeHosts(addresses);
    }

    private static List<TestPrioritizedHost> makeHosts(String... addresses) {
        List<TestPrioritizedHost> results = new ArrayList<>();
        for (String address : addresses) {
            results.add(new TestPrioritizedHost(address));
        }
        return results;
    }

    private static Matcher<Double> approxEqual(double expected) {
        return closeTo(expected, 0.001);
    }

    private static class TestPrioritizedHost implements PrioritizedHost {

        private final String address;

        private boolean isHealthy = true;
        private int priority = 0;
        private double intrinsicWeight = 1;
        private double loadBalancedWeight = 1;

        TestPrioritizedHost(String address) {
            this.address = address;
        }

        String address() {
            return address;
        }

        @Override
        public int priority() {
            return priority;
        }

        void priority(final int priority) {
            this.priority = priority;
        }

        // Set the intrinsic weight of the endpoint. This is the information from service discovery.
        void intrinsicWeight(final double weight) {
            this.intrinsicWeight = weight;
        }

        // Set the weight to use in load balancing. This includes derived weight information such as prioritization
        // and is what the host selectors will use when picking endpoints.
        @Override
        public void loadBalancedWeight(final double weight) {
            this.loadBalancedWeight = weight;
        }

        double loadBalancedWeight() {
            return loadBalancedWeight;
        }

        @Override
        public boolean isHealthy() {
            return isHealthy;
        }

        public void isHealthy(final boolean isHealthy) {
            this.isHealthy = isHealthy;
        }

        @Override
        public double intrinsicWeight() {
            return intrinsicWeight;
        }
    }
}
