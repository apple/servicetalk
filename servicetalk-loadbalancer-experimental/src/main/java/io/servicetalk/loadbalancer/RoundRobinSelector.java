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

import io.servicetalk.client.api.LoadBalancedConnection;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.context.api.ContextMap;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

final class RoundRobinSelector<ResolvedAddress, C extends LoadBalancedConnection>
        extends BaseHostSelector<ResolvedAddress, C> {

    private static final int MAX_WEIGHT = 0xffff;

    private final AtomicInteger index;
    private final Scheduler scheduler;
    private final boolean failOpen;

    RoundRobinSelector(final List<? extends Host<ResolvedAddress, C>> hosts, final String targetResource,
                       final boolean failOpen) {
        this(new AtomicInteger(), hosts, targetResource, failOpen);
    }

    private RoundRobinSelector(final AtomicInteger index, final List<? extends Host<ResolvedAddress, C>> hosts,
                               final String targetResource, final boolean failOpen) {
        super(hosts, targetResource);
        this.index = index;
        this.scheduler = buildScheduler(index, hosts());
        this.failOpen = failOpen;
    }

    @Override
    protected Single<C> selectConnection0(
            final Predicate<C> selector, @Nullable final ContextMap context,
            final boolean forceNewConnectionAndReserve) {
        // try one loop over hosts and if all are expired, give up
        final int cursor = scheduler.nextHost();
        Host<ResolvedAddress, C> failOpenHost = null;
        for (int i = 0; i < hosts().size(); ++i) {
            // for a particular iteration we maintain a local cursor without contention with other requests
            final int localCursor = (cursor + i) % hosts().size();
            final Host<ResolvedAddress, C> host = hosts().get(localCursor);
            if (host.isHealthy()) {
                Single<C> result = selectFromHost(host, selector, forceNewConnectionAndReserve, context);
                if (result != null) {
                    return result;
                }
            }

            // If the host is active we can use it for backup.
            if (failOpen && failOpenHost == null && host.canMakeNewConnections()) {
                failOpenHost = host;
            }
        }
        if (failOpenHost != null) {
            Single<C> result = selectFromHost(failOpenHost, selector, forceNewConnectionAndReserve, context);
            if (result != null) {
                return result;
            }
        }
        // We were unable to find a suitable host.
        return noActiveHostsFailure(hosts());
    }

    @Override
    public HostSelector<ResolvedAddress, C> rebuildWithHosts(@Nonnull List<? extends Host<ResolvedAddress, C>> hosts) {
        return new RoundRobinSelector<>(index, hosts, getTargetResource(), failOpen);
    }

    private static Scheduler buildScheduler(AtomicInteger index, List<? extends Host<?, ?>> hosts) {

        boolean allEqualWeights = true;
        double maxWeight = 0;

        for (Host<?, ?> host : hosts) {
            double hostWeight = host.weight();
            maxWeight = Math.max(maxWeight, hostWeight);
            allEqualWeights = allEqualWeights && approxEqual(hosts.get(0).weight(), hostWeight);
        }

        if (allEqualWeights) {
            return new ConstantScheduler(index, hosts.size());
        } else {
            double scaleFactor = MAX_WEIGHT / maxWeight;
            int[] scaledWeights = new int[hosts.size()];

            for (int i = 0; i < scaledWeights.length; i++) {
                // Using ceil ensures both that our max weighted element is picked on every round and that
                // hosts with weights near zero will never be truly zero.
                scaledWeights[i] = Math.min(MAX_WEIGHT, (int) Math.ceil(hosts.get(i).weight() * scaleFactor));
            }
            return new StrideScheduler(index, scaledWeights);
        }
    }

    private static abstract class Scheduler {
        abstract int nextHost();
    }

    private static final class ConstantScheduler extends Scheduler {

        private final AtomicInteger index;
        private final int hostsSize;

        ConstantScheduler(AtomicInteger index, int hostsSize) {
            this.index = index;
            this.hostsSize = hostsSize;
        }

        @Override
        int nextHost() {
            return (int) (Integer.toUnsignedLong(index.getAndIncrement()) % hostsSize);
        }
    }

    private static final class StrideScheduler extends Scheduler {

        private final AtomicInteger index;
        private final int[] weights;

        StrideScheduler(AtomicInteger index, int[] weights) {
            this.index = index;
            this.weights = weights;
        }

        @Override
        int nextHost() {
            while (true) {
                long counter = Integer.toUnsignedLong(index.getAndIncrement());
                long pass = counter / weights.length;
                int i = (int) counter % weights.length;
                // We add an offset, which could be anything so long as it's constant throughout iteration. We choose
                // an arbitrary multiple of the index that is on the order of the MAX_WEIGHT. This is helpful in the
                // case where weights are [1, .. 1, 5] since the scheduling could otherwise look something like this:
                //  ....
                //  [t, .., t, t]
                //  [f, .., f, t]
                //  [f, .., f, t]
                //  [f, .., f, t]
                //  [f, .., f, t]
                //  [t, .., t, t]
                //  ....
                long offset = MAX_WEIGHT / 2 * i;
                if ((weights[i] * pass + offset) % MAX_WEIGHT >= MAX_WEIGHT - weights[i]) {
                    return i;
                }
            }
        }
    }
}
