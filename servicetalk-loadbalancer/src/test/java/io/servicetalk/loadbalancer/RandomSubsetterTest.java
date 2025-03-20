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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.sameInstance;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RandomSubsetterTest {

    @Test
    void desiredSubsetLargerThanHostList() {
        List<PrioritizedHost> hosts = hosts(10);
        List<PrioritizedHost> results = new RandomSubsetter.RandomSubsetterFactory(Integer.MAX_VALUE)
                .newSubsetter("").subset(hosts);
        assertThat(results, sameInstance(hosts));
    }

    @Test
    void desiredSubsetSmallerThanHostList() {
        List<PrioritizedHost> hosts = hosts(10);
        Set<PrioritizedHost> results = subsetSet(5, hosts);
        // We turn it to a hash-set to ensure each element is unique.
        assertThat(results.size(), equalTo(5));
    }

    @Test
    void enoughHealthEndpoints() {
        List<PrioritizedHost> hosts = hosts(10);
        when(hosts.get(0).isHealthy()).thenReturn(false);
        Set<PrioritizedHost> results = subsetSet(5, hosts);
        long healthyCount = results.stream().filter(PrioritizedHost::isHealthy).count();
        assertThat(healthyCount, equalTo(5L));
    }

    @Test
    void notEnoughHealthEndpoints() {
        List<PrioritizedHost> hosts = hosts(10);
        for (PrioritizedHost host : hosts) {
            when(host.isHealthy()).thenReturn(false);
        }
        List<PrioritizedHost> results = subset(5, hosts);
        long healthyCount = results.stream().filter(PrioritizedHost::isHealthy).count();
        assertThat(healthyCount, equalTo(0L));
        assertThat(hosts, equalTo(results));
    }

    private static Set<PrioritizedHost> subsetSet(int randomSubsetSize, List<PrioritizedHost> hosts) {
        return new HashSet<>(subset(randomSubsetSize, hosts));
    }

    private static List<PrioritizedHost> subset(int randomSubsetSize, List<PrioritizedHost> hosts) {
        return new RandomSubsetter.RandomSubsetterFactory(randomSubsetSize).newSubsetter("").subset(hosts);
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
