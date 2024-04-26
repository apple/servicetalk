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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class DefaultHostPriorityStrategyTest {

    private final HostPriorityStrategy<String, TestLoadBalancedConnection> defaultHostPriorityStrategy =
            new DefaultHostPriorityStrategy<>();

    @Test
    void noPriorities() {
        List<EndpointHost<String, TestLoadBalancedConnection>> hosts = makeHosts(4);
        List<? extends Host<String, TestLoadBalancedConnection>> result = defaultHostPriorityStrategy.prioritize(hosts);
        assertThat(result.size(), equalTo(hosts.size()));

        for (int i = 0; i < hosts.size(); i++) {
            assertThat(result.get(i).address(), equalTo(hosts.get(i).address()));
            // It doesn't matter what they are exactly so long as all weights are equal.
            assertThat(result.get(i).weight(), equalTo(result.get(0).weight()));
        }
    }

    @Test
    void noPrioritiesWithWeights() {
        List<EndpointHost<String, TestLoadBalancedConnection>> hosts = makeHosts(4);
        for (int i = 0; i < hosts.size(); i++) {
            when(hosts.get(i).delegate().weight()).thenReturn(i + 1d);
        }
        List<? extends Host<String, TestLoadBalancedConnection>> result = defaultHostPriorityStrategy.prioritize(hosts);
        assertThat(result.size(), equalTo(hosts.size()));

        for (int i = 0; i < hosts.size(); i++) {
            assertThat(result.get(i).address(), equalTo(hosts.get(i).address()));
            // It doesn't matter what they are exactly so long as all weights are equal.
            assertThat(result.get(i).weight(), approxEqual(result.get(0).weight() * (i + 1)));
        }
    }

    @Test
    void twoPrioritiesNoWeights() {
        List<EndpointHost<String, TestLoadBalancedConnection>> hosts = makeHosts(6);
        for (int i = 3; i < 6; i++) {
            hosts.get(i).priority(1);
        }
        List<? extends Host<String, TestLoadBalancedConnection>> result = defaultHostPriorityStrategy.prioritize(hosts);
        assertThat(result.size(), equalTo(3));

        for (int i = 0; i < 3; i++) {
            assertThat(result.get(i).address(), equalTo(hosts.get(i).address()));
            // It doesn't matter what they are exactly so long as all weights are equal.
            assertThat(result.get(i).weight(), equalTo(result.get(0).weight()));
        }
    }

    @Test
    void twoPrioritiesWithWeights() {
        List<EndpointHost<String, TestLoadBalancedConnection>> hosts = makeHosts(6);
        for (int i = 0; i < hosts.size(); i++) {
            when(hosts.get(i).delegate().weight()).thenReturn(i + 1d);
            if (i >= 3) {
                hosts.get(i).priority(1);
            }
        }
        List<? extends Host<String, TestLoadBalancedConnection>> result = defaultHostPriorityStrategy.prioritize(hosts);
        assertThat(result.size(), equalTo(3));

        // We should only have the first three hosts because they were all healthy, so they are the only group.
        for (int i = 0; i < 3; i++) {
            assertThat(result.get(i).address(), equalTo(hosts.get(i).address()));
            // It doesn't matter what they are exactly so long as all weights are equal.
            assertThat(result.get(i).weight(), approxEqual(result.get(0).weight() * (i + 1)));
        }
    }

    private static List<EndpointHost<String, TestLoadBalancedConnection>> makeHosts(int count) {
        String[] addresses = new String[count];
        for (int i = 0; i < count; i++) {
            addresses[i] = "addr-" + i;
        }
        return makeHosts(addresses);
    }

    private static List<EndpointHost<String, TestLoadBalancedConnection>> makeHosts(String... addresses) {
        List<EndpointHost<String, TestLoadBalancedConnection>> results = new ArrayList<>();
        for (String address : addresses) {
            Host<String, TestLoadBalancedConnection> mockHost = mock(Host.class);
            when(mockHost.address()).thenReturn(address);
            when(mockHost.weight()).thenReturn(1.0);
            when(mockHost.isHealthy()).thenReturn(true);
            results.add(new EndpointHost<>(mockHost, 1.0, 0));
        }
        return results;
    }

    private static Matcher<Double> approxEqual(double expected) {
        return closeTo(expected, 0.001);
    }
}
