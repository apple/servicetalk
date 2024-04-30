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
import io.servicetalk.client.api.LoadBalancer;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.context.api.ContextMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;
import javax.annotation.Nullable;

import static java.lang.Math.abs;

/**
 * This {@link LoadBalancer} selection algorithm is based on work by Michael David Mitzenmacher in The Power of Two
 * Choices in Randomized Load Balancing.
 *
 * @see <a href="https://www.eecs.harvard.edu/~michaelm/postscripts/tpds2001.pdf">Mitzenmacher (2001) The Power of Two
 * Choices in Randomized Load Balancing</a>
 */
final class P2CSelector<ResolvedAddress, C extends LoadBalancedConnection>
        extends BaseHostSelector<ResolvedAddress, C> {

    private static final Logger LOGGER = LoggerFactory.getLogger(P2CSelector.class);

    private static final double ACCEPTABLE_PERCENT_ERROR = 0.01;
    private static final EntrySelector EMPTY_SELECTOR = new EqualWeightEntrySelector(0);

    @Nullable
    private final Random random;
    private final boolean ignoreWeights;
    private final EntrySelector entrySelector;
    private final int maxEffort;
    private final boolean failOpen;

    P2CSelector(List<? extends Host<ResolvedAddress, C>> hosts, final String targetResource,
                       final boolean ignoreWeights, final int maxEffort, final boolean failOpen,
                       @Nullable final Random random) {
        super(hosts, targetResource);
        this.ignoreWeights = ignoreWeights;
        this.entrySelector = ignoreWeights ? new EqualWeightEntrySelector(hosts.size()) : buildAliasTable(hosts);
        this.maxEffort = maxEffort;
        this.failOpen = failOpen;
        this.random = random;
    }

    @Override
    public HostSelector<ResolvedAddress, C> rebuildWithHosts(List<? extends Host<ResolvedAddress, C>> hosts) {
        return new P2CSelector<>(hosts, getTargetResource(), ignoreWeights, maxEffort, failOpen, random);
    }

    @Override
    protected Single<C> selectConnection0(Predicate<C> selector, @Nullable ContextMap context,
                                          boolean forceNewConnectionAndReserve) {
        final int size = hostSetSize();
        switch (size) {
            case 0:
                // We shouldn't get called if the load balancer doesn't have any hosts.
                throw new AssertionError("Selector for " + getTargetResource() +
                        " received an empty host set");
            case 1:
                // There is only a single host, so we don't need to do any of the looping or comparison logic.
                Host<ResolvedAddress, C> host = hosts().get(0);
                // If we're going to fail open we just yo-lo it, otherwise check if it's considered
                // healthy.
                if (failOpen || host.isHealthy()) {
                    Single<C> result = selectFromHost(
                            host, selector, forceNewConnectionAndReserve, context);
                    if (result != null) {
                        return result;
                    }
                }
                return noActiveHostsFailure(hosts());
            default:
                return p2c(hosts(), getRandom(), selector, forceNewConnectionAndReserve, context);
        }
    }

    private Single<C> p2c(List<? extends Host<ResolvedAddress, C>> hosts, Random random,
                          Predicate<C> selector, boolean forceNewConnectionAndReserve,
                          @Nullable ContextMap contextMap) {
        // If there are only two hosts we only try once since there is no chance we'll select different hosts
        // on further iterations.
        Host<ResolvedAddress, C> failOpenHost = null;
        for (int j = hosts.size() == 2 ? 1 : maxEffort; j > 0; j--) {
            // Pick two random indexes that don't collide.
            final int i1 = entrySelector.firstEntry(random);
            final int i2 = entrySelector.secondEntry(random, i1);

            Host<ResolvedAddress, C> t1 = hosts.get(i1);
            Host<ResolvedAddress, C> t2 = hosts.get(i2);
            final boolean t1Healthy = t1.isHealthy();
            final boolean t2Healthy = t2.isHealthy();
            // Priority of selection: health > score > failOpen
            // Only if both hosts are healthy do we consider score.
            if (t1Healthy && t2Healthy) {
                // both are healthy. Select based on score, using t1 if equal.
                if (t1.score() < t2.score()) {
                    Host<ResolvedAddress, C> tmp = t1;
                    t1 = t2;
                    t2 = tmp;
                }
                Single<C> result = selectFromHost(
                        t1, selector, forceNewConnectionAndReserve, contextMap);
                // We didn't get a connection from the first host: maybe it is inactive
                // and we couldn't reserve a connection. Try the second host.
                if (result == null) {
                    result = selectFromHost(t2, selector, forceNewConnectionAndReserve, contextMap);
                }
                // If we have a connection we're good to go. Otherwise fall through for another round.
                // Since we didn't get a connection from either of them there is no reason to think they'll
                // yield a connection if we make them the fallback.
                if (result != null) {
                    return result;
                }
            } else if (t2Healthy) {
                Single<C> result = selectFromHost(t2, selector, forceNewConnectionAndReserve, contextMap);
                if (result != null) {
                    return result;
                }
            } else if (t1Healthy) {
                Single<C> result = selectFromHost(t1, selector, forceNewConnectionAndReserve, contextMap);
                if (result != null) {
                    return result;
                }
            } else if (failOpen && failOpenHost == null) {
                // Both are unhealthy. If one of them can make new connections then it can be a backup.
                if (t1.canMakeNewConnections()) {
                    failOpenHost = t1;
                } else if (t2.canMakeNewConnections()) {
                    failOpenHost = t2;
                }
            }
        }
        // Max effort exhausted. We failed to find a healthy and active host. If we want to fail open and
        // found an active host but it was considered unhealthy, try it anyway.
        if (failOpenHost != null) {
            Single<C> result = selectFromHost(failOpenHost, selector, forceNewConnectionAndReserve, contextMap);
            if (result != null) {
                return result;
            }
        }
        return noActiveHostsFailure(hosts);
    }

    private Random getRandom() {
        return random == null ? ThreadLocalRandom.current() : random;
    }

    private EntrySelector buildAliasTable(List<? extends Host<?, ?>> hosts) {
        if (hosts.isEmpty()) {
            return EMPTY_SELECTOR;
        }

        double[] probs = new double[hosts.size()];
        boolean allSameProbability = true;
        double pTotal = 0;
        for (int i = 0; i < hosts.size(); i++) {
            final double pi = hosts.get(i).loadBalancedWeight();
            if (pi < 0) {
                LOGGER.warn("{}: host at address {} has negative weight ({}). Using unweighted selection.",
                        getTargetResource(), hosts.get(i).address(), pi);
                return new EqualWeightEntrySelector(hosts.size());
            }
            probs[i] = pi;
            pTotal += pi;
            allSameProbability = allSameProbability && approxEqual(pi, probs[0]);
        }

        if (allSameProbability) {
            // no need for a DRV table.
            return new EqualWeightEntrySelector(hosts.size());
        }

        // make sure our probability is normalized to less than 1% error.
        if (!approxEqual(pTotal, 1)) {
            // need to normalize.
            double invPTotal = 1.0 / pTotal;
            for (int i = 0; i < probs.length; i++) {
                probs[i] *= invPTotal;
            }
        }
        return buildAliasTable(probs);
    }

    private abstract static class EntrySelector {
        abstract int firstEntry(Random random);

        abstract int secondEntry(Random random, int firstEntry);
    }

    // An EntrySelector that represents equally weighted entries. In this case no alias table is needed.
    private static final class EqualWeightEntrySelector extends EntrySelector {
        private final int size;

        EqualWeightEntrySelector(final int size) {
            this.size = size;
        }

        @Override
        int firstEntry(Random random) {
            return random.nextInt(size);
        }

        @Override
        int secondEntry(Random random, int firstEntry) {
            assert size >= 2;
            int result = random.nextInt(size - 1);
            if (result >= firstEntry) {
                result++;
            }
            return result;
        }
    }

    // An EntrySelector that represents equally un-evenly weighted entries.
    private final class AliasTableEntrySelector extends EntrySelector {

        private final double[] aliasProbabilities;
        private final int[] aliases;

        AliasTableEntrySelector(double[] aliasProbabilities, int[] aliases) {
            this.aliasProbabilities = aliasProbabilities;
            this.aliases = aliases;
        }

        @Override
        int secondEntry(Random random, int firstPick) {
            int result;
            int iteration = 0;
            do {
                result = pick(random);
            } while (result == firstPick && iteration++ < maxEffort);
            if (firstPick == result) {
                LOGGER.debug("{}: failed to pick two unique indices after {} selection attempts",
                        getTargetResource(), maxEffort);
            }
            return 0;
        }

        @Override
        int firstEntry(Random random) {
            return pick(random);
        }

        private int pick(Random random) {
            int i = random.nextInt(aliases.length);
            double pp = aliasProbabilities[i];
            if (pp != 1.0 && random.nextDouble() > pp) {
                // use the alias.
                i = aliases[i];
            }
            return i;
        }
    }

    private AliasTableEntrySelector buildAliasTable(double[] pin) {
        assert isNormalized(pin);
        // We use the Vose alias method to compute the alias table with linear complexity.
        // M. D. Vose, "A linear algorithm for generating random numbers with a given distribution,"
        // IEEE Transactions on Software Engineering, vol. 17, no. 9, pp. 972-975, Sept. 1991, doi: 10.1109/32.92917.
        // https://ieeexplore.ieee.org/document/92917
        final double[] pout = new double[pin.length];
        final int[] aliases = new int[pin.length];

        // Setup our two stacks. Queues would also work but we'll use simple arrays as stacks.
        final int[] small = new int[pin.length];
        final int[] large = new int[pin.length];
        int s = 0;
        int l = 0;

        for (int i = 0; i < pin.length; i++) {
            pin[i] *= pin.length;
            if (pin[i] > 1) {
                large[l++] = i;
            } else {
                small[s++] = i;
            }
        }

        // drain the stacks and populate the alias table
        while (s != 0 && l != 0) {
            int j = small[--s];
            int k = large[--l];
            pout[j] = pin[j];
            aliases[j] = k;
            pin[k] = pin[k] + pin[j] - 1;
            if (pin[k] > 1) {
                large[l++] = k;
            } else {
                small[s++] = k;
            }
        }
        while (s != 0) {
            pout[small[--s]] = 1;
        }
        while (l != 0) {
            pout[large[--l]] = 1;
        }
        return new AliasTableEntrySelector(pout, aliases);
    }

    private static boolean approxEqual(double a, double b) {
        return abs(a - b) < ACCEPTABLE_PERCENT_ERROR;
    }

    private static boolean isNormalized(double[] probabilities) {
        double ptotal = 0;
        for (double p : probabilities) {
            ptotal += p;
        }
        return approxEqual(ptotal, 1);
    }
}
