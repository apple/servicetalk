package io.servicetalk.loadbalancer;

import io.servicetalk.client.api.LoadBalancedConnection;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.context.api.ContextMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;

final class WeightedP2CSelector<ResolvedAddress, C extends LoadBalancedConnection>
        extends BaseHostSelector<ResolvedAddress, C> {

    private static final Logger LOGGER = LoggerFactory.getLogger(WeightedP2CSelector.class);

    private static final EntrySelector EMPTY_SELECTOR = new EqualWeightTable(0);

    private final List<? extends Host<ResolvedAddress, C>> hosts;
    // null if the weights are all the same or if the list is empty.
    @Nullable
    private final EntrySelector entrySelector;
    private final int maxEffort;
    private final boolean failOpen;
    private final String targetResource;

    public WeightedP2CSelector(final List<? extends Host<ResolvedAddress, C>> hosts, final int maxEffort,
                               final boolean failOpen, final String targetResource) {
        super(hosts, targetResource);
        this.hosts = hosts;
        this.entrySelector = makeAliasTable(hosts);
        this.maxEffort = maxEffort;
        this.failOpen = failOpen;
        this.targetResource = targetResource;
    }

    @Override
    protected Single<C> selectConnection0(Predicate<C> selector,
                                          @Nullable ContextMap context, boolean forceNewConnectionAndReserve) {
        final int size = hosts.size();
        switch (size) {
            case 0:
                // We shouldn't get called if the load balancer doesn't have any hosts.
                throw new AssertionError("Selector for " + getTargetResource() +
                        " received an empty host set");
            case 1:
                // There is only a single host, so we don't need to do any of the looping or comparison logic.
                Host<ResolvedAddress, C> host = hosts.get(0);
                // If we're going to fail open we just yo-lo it, otherwise check if it's considered
                // healthy.
                if (failOpen || host.isHealthy()) {
                    Single<C> result = selectFromHost(
                            host, selector, forceNewConnectionAndReserve, context);
                    if (result != null) {
                        return result;
                    }
                }
                return noActiveHostsFailure(hosts);
            default:
                return p2c(size, hosts, getRandom(), selector, forceNewConnectionAndReserve, context);
        }
    }

    // for testing purposes.
    protected Random getRandom() {
        return ThreadLocalRandom.current();
    }

    @Override
    public HostSelector<ResolvedAddress, C> rebuildWithHosts(List<? extends Host<ResolvedAddress, C>> hosts) {
        return new WeightedP2CSelector<>(hosts, maxEffort, failOpen, targetResource);
    }

    private Single<C> p2c(int size, List<? extends Host<ResolvedAddress, C>> hosts, Random random,
                          Predicate<C> selector, boolean forceNewConnectionAndReserve,
                          @Nullable ContextMap contextMap) {
        // If there are only two hosts we only try once since there is no chance we'll select different hosts
        // on further iterations.
        Host<ResolvedAddress, C> failOpenHost = null;
        for (int j = hosts.size() == 2 ? 1 : maxEffort; j > 0; j--) {
            // Pick two random indexes that don't collide.
            final int i1 = entrySelector.randomEntry(random);
            int i2 = entrySelector.randomEntry(random);
            // re-pick i2 up to maxEffort times to avoid collisions.
            for (int i = 0; i2 == i1 && i < maxEffort; i++) {
                i2 = entrySelector.randomEntry(random);
            }
            if (i1 == i2) {
                LOGGER.debug("{}: failed to pick two unique indices after {} selection attempts",
                        targetResource, maxEffort);
            }

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
        // found an active host, but it was considered unhealthy, try it anyway.
        if (failOpenHost != null) {
            Single<C> result = selectFromHost(failOpenHost, selector, forceNewConnectionAndReserve, contextMap);
            if (result != null) {
                return result;
            }
        }
        return noActiveHostsFailure(hosts);
    }

    @Nullable
    static EntrySelector makeAliasTable(List<? extends Host<?, ?>> hosts) {
        if (hosts.isEmpty()) {
            return EMPTY_SELECTOR;
        }

        double[] probs = new double[hosts.size()];
        boolean allSameProbability = true;
        double pTotal = 0;
        for (int i = 0; i < hosts.size(); i++) {
            probs[i] = hosts.get(i).weight();
            pTotal += probs[i];
            allSameProbability &= allSameProbability && approxEqual(probs[i], probs[0]);
        }

        if (allSameProbability) {
            // no need for a DRV table.
            return new EqualWeightTable(hosts.size());
        }

        // make sure our probability is normalized to less than 1% error.
        if (!approxEqual(pTotal, 1)) {
            // need to normalize.
            double invPTotal = 1.0 / pTotal;
            for (int i = 0; i < probs.length; i++) {
                probs[i] *= invPTotal;
            }
        }
        return makeAliasTable(probs);
    }

    private static AliasTable makeAliasTable(double[] pin) {
        // We use the Vose alias method to compute the alias table with linear complexity.
        // M. D. Vose, "A linear algorithm for generating random numbers with a given distribution,"
        // IEEE Transactions on Software Engineering, vol. 17, no. 9, pp. 972-975, Sept. 1991, doi: 10.1109/32.92917.
        // https://ieeexplore.ieee.org/document/92917
        double[] pout = new double[pin.length];
        int[] aliases = new int[pin.length];

        // setup our two stacks/queues.
        int[] small = new int[pin.length];
        int[] large = new int[pin.length];
        int s = 0;
        int l = 0;

        for (int i = 0; i < pin.length; i++) {
            pin[i] = pin[i] * pin.length;
            if (pin[i] > 1) {
                large[l++] = i;
            } else {
                small[s++] = i;
            }
        }

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

        return new AliasTable(pout, aliases);
    }

    static abstract class EntrySelector {
        abstract int randomEntry(Random random);
    }

    // An EntrySelector that represents equally weighted entries. In this case no alias table is needed.
    static final class EqualWeightTable extends EntrySelector {
        private final int size;

        EqualWeightTable(final int size) {
            this.size = size;
        }

        @Override
        int randomEntry(Random random) {
            return random.nextInt(size);
        }
    }

    // An EntrySelector that represents equally un-evenly weighted entries.
    static final class AliasTable extends EntrySelector {
        private final double[] aliasProbabilities;
        private final int[] aliases;

        public AliasTable(double[] aliasProbabilities, int[] aliases) {
            this.aliasProbabilities = aliasProbabilities;
            this.aliases = aliases;
        }

        @Override
        int randomEntry(Random random) {
            int i = random.nextInt(aliases.length);
            double pp = aliasProbabilities[i];
            if (pp != 1.0 && random.nextDouble() > pp) {
                // use the alias.
                i = aliases[i];
            }
            return i;
        }
    }

    private static boolean approxEqual(double a, double b) {
        double diff = a - b;
        if (diff < 0) {
            diff = -diff;
        }
        return diff < 0.01;
    }
}
