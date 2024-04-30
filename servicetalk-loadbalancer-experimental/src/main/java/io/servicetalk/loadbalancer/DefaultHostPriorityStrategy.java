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

import static io.servicetalk.utils.internal.NumberUtils.ensurePositive;

final class DefaultHostPriorityStrategy implements HostPriorityStrategy {

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
        //  The behavior could be structured more as a tree, but it's not obvious how to feed such a tree into the load
        //  balancer.
        List<Group> groups = new ArrayList<>();
        // First consolidate our hosts into their respective priority groups.
        for (T host : hosts) {
            if (host.priority() < 0) {
                LOGGER.warn("Found illegal priority: {}. Dropping priority grouping data.", host.priority());
                return hosts;
            }
            Group group = getGroup(groups, host.priority());
            if (host.isHealthy()) {
                group.healthyCount++;
            }
            group.hosts.add(host);
        }

        // Compute the health percentage for each group.
        int totalHealthPercentage = 0;
        for (Group group : groups) {
            group.healthPercentage = Math.min(100, overProvisionPercentage * group.healthyCount / group.hosts.size());
            totalHealthPercentage = Math.min(100, totalHealthPercentage + group.healthPercentage);
        }
        if (totalHealthPercentage == 0) {
            // nothing is considered healthy so everything is considered healthy.
            return hosts;
        }

        // We require that we have a continuous priority set. We could relax this if we wanted by using a tree map to
        // traverse in order. However, I think it's also a requirement of other xDS compatible implementations.
        List<T> weightedResults = new ArrayList<>();
        int remainingProbability = 100;
        for (int i = 0; i < groups.size() && remainingProbability > 0; i++) {
            Group group = groups.get(i);
            if (group.hosts.isEmpty()) {
                // we don't have a continuous priority group. Warn and rebuild without priorities.
                LOGGER.warn("Non-continuous priority groups: {} total groups but missing group {}. " +
                        "Dropping priority grouping data.", groups.size(), i);
                return hosts;
            }

            // We need to compute the weight for the group.
            final int groupProbability = Math.min(remainingProbability,
                    group.healthPercentage * 100 / totalHealthPercentage);
            if (groupProbability == 0) {
                // TODO: this means all hosts for this group are unhealthy. This may be worth some logging.
            } else {
                remainingProbability -= groupProbability;
                group.normalize(groupProbability, weightedResults);
            }
        }
        // What to do if we don't have any healthy nodes at all?
        if (remainingProbability > 0) {
            // TODO: this is an awkward situation. Should we panic and just discard priority information?
        }
        return weightedResults;
    }

    private Group getGroup(List<Group> groups, int priority) {
        while (groups.size() < priority + 1) {
            groups.add(new Group());
        }
        return groups.get(priority);
    }

    private static class Group<H extends PrioritizedHost> {
        final List<H> hosts = new ArrayList<>();
        int healthyCount;

        int healthPercentage;

        private void normalize(int groupProbability, List<H> results) {
            // Add all the members of the group after we recompute their weights. To recompute the weights we're going
            // to normalize against their group probability.
            double groupTotalWeight = totalWeight(hosts);
            if (groupTotalWeight == 0) {
                double weight = ((double) groupProbability) / hosts.size();
                for (H host : hosts) {
                    host.loadBalancedWeight(weight);
                    results.add(host);
                }
            } else {
                double scalingFactor = groupProbability / groupTotalWeight;
                for (H host : hosts) {
                    double hostWeight = host.intrinsicWeight() * scalingFactor;
                    host.loadBalancedWeight(hostWeight);
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
            sum += host.intrinsicWeight();
        }
        return sum;
    }
}
