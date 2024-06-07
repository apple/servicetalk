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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

import static io.servicetalk.utils.internal.NumberUtils.ensurePositive;

final class DefaultHostPriorityStrategy implements HostPriorityStrategy {

    static final HostPriorityStrategy INSTANCE = new DefaultHostPriorityStrategy();

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultHostPriorityStrategy.class);
    private static final int DEFAULT_OVER_PROVISION_FACTOR = 140;


    private final int overProvisionPercentage;

    DefaultHostPriorityStrategy() {
        this(DEFAULT_OVER_PROVISION_FACTOR);
    }

    // exposed for testing
    DefaultHostPriorityStrategy(final int overProvisionPercentage) {
        this.overProvisionPercentage = ensurePositive(overProvisionPercentage, "overProvisionPercentage");
    }

    @Override
    public <T extends PrioritizedHost> List<T> prioritize(List<T> hosts) {
        // no need to compute priorities if there are no hosts.
        return hosts.isEmpty() ? hosts : rebuildWithPriorities(hosts);
    }

    private <T extends PrioritizedHost> List<T> rebuildWithPriorities(final List<T> hosts) {
        assert !hosts.isEmpty();

        // TODO: this precludes having an expected amount of traffic favor local zones and the rest routed to
        //  remote zones intentionally even if all hosts are well.
        //  https://www.envoyproxy.io/docs/envoy/latest/configuration/upstream/cluster_manager/cluster_runtime
        //      #zone-aware-load-balancing
        // Consolidate our hosts into their respective priority groups. Since we're going to use a map we must use
        // and ordered map (in this case a TreeMap) so that we can iterate in order of group priority.
        TreeMap<Integer, Group> groups = new TreeMap<>();
        for (T host : hosts) {
            if (host.priority() < 0) {
                LOGGER.warn("Found illegal priority: {}. Dropping priority grouping data.", host.priority());
                return hosts;
            }
            Group group = groups.computeIfAbsent(host.priority(), i -> new Group());
            if (host.isHealthy()) {
                group.healthyCount++;
            }
            group.hosts.add(host);
        }

        // If there is only a single group we don't need to adjust weights.
        if (groups.size() == 1) {
            return hosts;
        }

        // Compute the health percentage for each group.
        int totalHealthPercentage = 0;
        for (Group group : groups.values()) {
            group.healthPercentage = Math.min(100, overProvisionPercentage * group.healthyCount / group.hosts.size());
            totalHealthPercentage = Math.min(100, totalHealthPercentage + group.healthPercentage);
        }
        if (totalHealthPercentage == 0) {
            // nothing is considered healthy so everything is considered healthy.
            return hosts;
        }

        List<T> weightedResults = new ArrayList<>();
        int remainingProbability = 100;
        for (Group group : groups.values()) {
            assert !group.hosts.isEmpty();
            final int groupProbability = Math.min(remainingProbability,
                    group.healthPercentage * 100 / totalHealthPercentage);
            if (groupProbability > 0) {
                remainingProbability -= groupProbability;
                group.addToResults(groupProbability, weightedResults);
            }
            if (remainingProbability == 0) {
                break;
            }
        }
        if (weightedResults.isEmpty()) {
            // This is awkward situation can happen if we don't have any healthy groups.
            // In that case let's panic and return an un-prioritized set of hosts.
            LOGGER.warn("No healthy priority groups found. Returning the un-prioritized set.");
            return hosts;
        }
        return weightedResults;
    }

    private static class Group<H extends PrioritizedHost> {
        final List<H> hosts = new ArrayList<>();
        int healthyCount;
        int healthPercentage;

        private void addToResults(int groupProbability, List<H> results) {
            // Add all the members of the group after we recompute their weights. To recompute the weights we're going
            // to normalize against their group probability.
            double groupTotalWeight = totalWeight(hosts);
            if (groupTotalWeight == 0) {
                double weight = ((double) groupProbability) / hosts.size();
                for (H host : hosts) {
                    host.loadBalancingWeight(weight);
                    results.add(host);
                }
            } else {
                double scalingFactor = groupProbability / groupTotalWeight;
                for (H host : hosts) {
                    double hostWeight = host.loadBalancingWeight() * scalingFactor;
                    host.loadBalancingWeight(hostWeight);
                    if (hostWeight > 0) {
                        results.add(host);
                    }
                }
            }
        }
    }

    private static double totalWeight(Iterable<? extends PrioritizedHost> hosts) {
        double sum = 0;
        for (PrioritizedHost host : hosts) {
            sum += host.loadBalancingWeight();
        }
        return sum;
    }
}
