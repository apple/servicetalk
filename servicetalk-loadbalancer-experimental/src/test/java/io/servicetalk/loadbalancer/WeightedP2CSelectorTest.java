package io.servicetalk.loadbalancer;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WeightedP2CSelectorTest {

    @Test
    void aliasTableForEmptyList() {
        WeightedP2CSelector.EntrySelector selector = WeightedP2CSelector.makeAliasTable(makeHosts(0));
        assertThat(selector, isA(WeightedP2CSelector.EqualWeightTable.class));
    }

    @Test
    void aliasTableForEqualWeights() {
        WeightedP2CSelector.EntrySelector selector = WeightedP2CSelector.makeAliasTable(makeHosts(2));
        assertThat(selector, isA(WeightedP2CSelector.EqualWeightTable.class));
    }

    @Test
    void aliasTableForUnequalWeights() {
        List<Host<String, TestLoadBalancedConnection>> hosts = makeHosts(20);
        when(hosts.get(0).weight()).thenReturn(2.0);
        WeightedP2CSelector.EntrySelector selector = WeightedP2CSelector.makeAliasTable(hosts);
        assertThat(selector, isA(WeightedP2CSelector.AliasTable.class));
        int[] counts = new int[hosts.size()];
        Random rand = ThreadLocalRandom.current();
        for (int i = 0; i < 1000; i++) {
            counts[selector.randomEntry(rand)]++;
        }

        assertThat(counts[0], equalTo(counts[1]));
    }

    private static void checkProbabilities(List<Host<String, TestLoadBalancedConnection>> hosts) {
        final int iterations = 10_000;
        WeightedP2CSelector.EntrySelector selector = WeightedP2CSelector.makeAliasTable(hosts);
        int[] counts = new int[hosts.size()];
        Random rand = ThreadLocalRandom.current();
        for (int i = 0; i < iterations; i++) {
            counts[selector.randomEntry(rand)]++;
        }

        double totalProbability = hosts.stream().map(Host::weight).reduce(0d, (a, b) -> a + b);
        Integer[] expected = hosts.stream().map(host -> iterations * (host.weight() / totalProbability))
                .toArray(Integer[]::new);

        // calculate the rough counts we should expect


        assertThat(counts[0], equalTo(counts[1]));
    }

    private static List<Host<String, TestLoadBalancedConnection>> makeHosts(final int size) {
        List<Host<String, TestLoadBalancedConnection>> hosts = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            hosts.add(mockhost());
        }
        return hosts;
    }

    private static Host<String, TestLoadBalancedConnection> mockhost() {
        Host<String, TestLoadBalancedConnection> host = mock(Host.class);
        when(host.weight()).thenReturn(1.0);
        return host;
    }
}
