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

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RandomSubsetterTest {

    @Test
    void desiredSubsetLargerThanHostList() {
        List<PrioritizedHost> hosts = hosts(10);
        List<PrioritizedHost> results = new RandomSubsetter(Integer.MAX_VALUE).subset(hosts);
        assertThat(results, equalTo(hosts));
    }

    @Test
    void desiredSubsetSmallerThanHostList() {
        List<PrioritizedHost> hosts = hosts(10);
        List<PrioritizedHost> results = new RandomSubsetter(5).subset(hosts);
        assertThat(results.size(), equalTo(5));
    }

    @Test
    void enoughHealthEndpoints() {
        List<PrioritizedHost> hosts = hosts(10);
        when(hosts.get(0).isHealthy()).thenReturn(false);
        List<PrioritizedHost> results = new RandomSubsetter(5).subset(hosts);
        long healthyCount = results.stream().filter(PrioritizedHost::isHealthy).count();
        assertThat(healthyCount, equalTo(5L));
    }

    @Test
    void notEnoughHealthEndpoints() {
        List<PrioritizedHost> hosts = hosts(10);
        for (PrioritizedHost host : hosts) {
            when(host.isHealthy()).thenReturn(false);
        }
        when(hosts.get(0).isHealthy()).thenReturn(false);
        List<PrioritizedHost> results = new RandomSubsetter(5).subset(hosts);
        long healthyCount = results.stream().filter(PrioritizedHost::isHealthy).count();
        assertThat(healthyCount, equalTo(0L));
        assertThat(hosts, equalTo(results));
    }

    private static List<PrioritizedHost> hosts(int count) {
        List<PrioritizedHost> hosts = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            PrioritizedHost host = mock(PrioritizedHost.class);
            when(host.randomSeed()).thenReturn((long) i);
            when(host.isHealthy()).thenReturn(true);
            hosts.add(host);
        }

        return hosts;
    }
}
