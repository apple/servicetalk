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
    private final List<Host<ResolvedAddress, C>> hosts;

    P2CSelector(@Nonnull List<Host<ResolvedAddress, C>> hosts,
                final String targetResource, final int maxEffort, @Nullable final Random random) {
        super(hosts.isEmpty(), targetResource);
        this.hosts = hosts;
        this.maxEffort = maxEffort;
        this.random = random;
    }

    @Override
    public HostSelector<ResolvedAddress, C> rebuildWithHosts(@Nonnull List<Host<ResolvedAddress, C>> hosts) {
        return new P2CSelector<>(hosts, getTargetResource(), maxEffort, random);
    }

    @Override
    protected Single<C> selectConnection0(
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
                // There is only a single host, so we don't need to do any of the looping or comparison logic.
                Single<C> connection = selectFromHost(hosts.get(0), selector, forceNewConnectionAndReserve, context);
                return connection == null ? noActiveHostsFailure(hosts) : connection;
            default:
                return p2c(size, hosts, getRandom(), selector, forceNewConnectionAndReserve, context);
        }
    }

    private Single<C> p2c(int size, List<Host<ResolvedAddress, C>> hosts, Random random, Predicate<C> selector,
                          boolean forceNewConnectionAndReserve, @Nullable ContextMap contextMap) {
        // If there are only two hosts we only try once since there is no chance we'll select different hosts
        // on further iterations.
        for (int j = hosts.size() == 2 ? 1 : maxEffort; j > 0; j--) {
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

            // Attempt to get a connection from t1 first since it's 'better'. If we can't, then try t2.
            Single<C> result = selectFromHost(t1, selector, forceNewConnectionAndReserve, contextMap);
            if (result != null) {
                return result;
            }
            result = selectFromHost(t2, selector, forceNewConnectionAndReserve, contextMap);
            if (result != null) {
                return result;
            }
            // Neither t1 nor t2 yielded a connection. Fall through, potentially for another attempt.
        }
        // Max effort exhausted. We failed to find a healthy and active host.
        return noActiveHostsFailure(hosts);
    }

    @Nullable
    private Single<C> selectFromHost(Host<ResolvedAddress, C> host, Predicate<C> selector,
                                     boolean forceNewConnectionAndReserve, @Nullable ContextMap contextMap) {
        // First see if we can get an existing connection regardless of health status.
        if (!forceNewConnectionAndReserve) {
            C c = host.pickConnection(selector, contextMap);
            if (c != null) {
                return succeeded(c);
            }
        }
        // We need to make a new connection to the host but we'll only do so if it's considered healthy.
        if (host.isActiveAndHealthy()) {
            return host.newConnection(selector, forceNewConnectionAndReserve, contextMap);
        }

        // no selectable active connections and the host is unhealthy, so we return `null`.
        return null;
    }

    private Random getRandom() {
        return random == null ? ThreadLocalRandom.current() : random;
    }
}
