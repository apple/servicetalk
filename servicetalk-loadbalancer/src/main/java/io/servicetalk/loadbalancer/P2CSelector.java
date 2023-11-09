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

import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Single.succeeded;

/**
 * This {@link LoadBalancer} selection algorithm is based on work by Michael David Mitzenmacher in The Power of Two
 * Choices in Randomized Load Balancing.
 *
 * @see <a href="https://www.eecs.harvard.edu/~michaelm/postscripts/tpds2001.pdf">Mitzenmacher (2001) The Power of Two
 * Choices in Randomized Load Balancing</a>
 */
final class P2CSelector<ResolvedAddress, C extends LoadBalancedConnection>
        extends BaseHostSelector<ResolvedAddress, C> {

    @Nullable
    private final Random random;
    private final int maxEffort;

    P2CSelector(final String targetResource, final int maxEffort, @Nullable final Random random) {
        super(targetResource);
        this.maxEffort = maxEffort;
        this.random = random;
    }

    @Override
    public Single<C> selectConnection(
            @Nonnull List<Host<ResolvedAddress, C>> hosts,
            @Nonnull Predicate<C> selector,
            @Nullable ContextMap context,
            boolean forceNewConnectionAndReserve) {
        final int size = hosts.size();
        switch (size) {
            case 0:
                // We shouldn't get called if the load balancer doesn't have any hosts.
                throw new AssertionError("Selector for " + getTargetResource() +
                        " received an empty host set");
            case 1:
                Host<ResolvedAddress, C> host = hosts.get(0);
                if (!forceNewConnectionAndReserve) {
                    C connection = host.pickConnection(selector, context);
                    if (connection != null) {
                        return succeeded(connection);
                    }
                }
                // Either we require a new connection or there wasn't one already established so
                // try to make a new one if the host is healthy. If it's not healthy, we fail
                // and let the higher level retries decide what to do.
                if (!host.isActiveAndHealthy()) {
                    return noActiveHosts(hosts);
                }
                return host.newConnection(selector, forceNewConnectionAndReserve, context);
            default:
                return p2c(size, hosts, getRandom(), selector, forceNewConnectionAndReserve, context);
        }
    }

    @Nullable
    private Single<C> p2c(int size, List<Host<ResolvedAddress, C>> hosts, Random random, Predicate<C> selector,
                          boolean forceNewConnectionAndReserve, @Nullable ContextMap contextMap) {
        for (int j = maxEffort; j > 0; j--) {
            // Pick two random indexes that don't collide. Limit the range on the second index to 1 less than
            // the max value so that if there is a collision we can safety increment. We also increment if
            // i2 > i1 to avoid biased toward lower numbers since we limited the range by 1.
            final int i1 = random.nextInt(size);
            int i2 = random.nextInt(size - 1);
            if (i2 >= i1) {
                ++i2;
            }
            Host<ResolvedAddress, C> t1 = hosts.get(i1);
            Host<ResolvedAddress, C> t2 = hosts.get(i2);
            // Make t1 the preferred host by score to make the logic below a bit cleaner.
            if (t1.score() < t2.score()) {
                Host<ResolvedAddress, C> tmp = t1;
                t1 = t2;
                t2 = tmp;
            }

            if (!forceNewConnectionAndReserve) {
                // First we're going to see if we can get an existing connection regardless of health status. Since t1
                // is 'better' we'll try it first. If it doesn't have any existing connections we don't fall back to t2
                // or else we would cause a bias toward hosts with existing connections which could ultimately drive all
                // traffic to the first host to make a connection in the case of a multiplexed session.
                C c = t1.pickConnection(selector, contextMap);
                if (c != null) {
                    return succeeded(c);
                }
                // We now need to consider the health status and make a new connection if either
                // host is considered healthy.
            }

            // We either couldn't find a live connection or are being forced to make a new one. Either way we're
            // going to make a new connection which means we need to consider health.
            final boolean t1Healthy = t1.isActiveAndHealthy();
            final boolean t2Healthy = t2.isActiveAndHealthy();
            if (t1Healthy) {
                return t1.newConnection(selector, forceNewConnectionAndReserve, contextMap);
            } else if (t2Healthy) {
                return t2.newConnection(selector, forceNewConnectionAndReserve, contextMap);
            }
            // Neither are healthy and capable of making a connection: fall through, perhaps for another attempt.
        }
        // Max effort exhausted. We failed to find a healthy and active host.
        return noActiveHosts(hosts);
    }

    private Random getRandom() {
        return random == null ? ThreadLocalRandom.current() : random;
    }
}
