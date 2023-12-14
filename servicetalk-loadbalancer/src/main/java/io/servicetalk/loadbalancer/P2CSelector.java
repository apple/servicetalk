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
import javax.annotation.Nullable;

/**
 * This {@link LoadBalancer} selection algorithm is based on work by Michael David Mitzenmacher in The Power of Two
 * Choices in Randomized Load Balancing.
 *
 * @see <a href="https://www.eecs.harvard.edu/~michaelm/postscripts/tpds2001.pdf">Mitzenmacher (2001) The Power of Two
 * Choices in Randomized Load Balancing</a>
 */
final class P2CSelector<ResolvedAddress, C extends LoadBalancedConnection>
        extends BaseHostSelector<ResolvedAddress, C> {

    private final List<Host<ResolvedAddress, C>> hosts;
    @Nullable
    private final Random random;
    private final int maxEffort;
    private final boolean failOpen;

    P2CSelector(List<Host<ResolvedAddress, C>> hosts, final String targetResource, final int maxEffort,
                final boolean failOpen, @Nullable final Random random) {
        super(hosts, targetResource);
        this.hosts = hosts;
        this.maxEffort = maxEffort;
        this.failOpen = failOpen;
        this.random = random;
    }

    @Override
    public HostSelector<ResolvedAddress, C> rebuildWithHosts(List<Host<ResolvedAddress, C>> hosts) {
        return new P2CSelector<>(hosts, getTargetResource(), maxEffort, failOpen, random);
    }

    @Override
    protected Single<C> selectConnection0(Predicate<C> selector, @Nullable ContextMap context,
                                          boolean forceNewConnectionAndReserve) {
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
                Host.Status status = host.status(forceNewConnectionAndReserve);
                if (failOpen || status.healthy) {
                    Single<C> result = selectFromHost(
                            host, status, selector, forceNewConnectionAndReserve, context);
                    if (result != null) {
                        return result;
                    }
                }
                return noActiveHostsFailure(hosts);
            default:
                return p2c(size, hosts, getRandom(), selector, forceNewConnectionAndReserve, context);
        }
    }

    private Single<C> p2c(int size, List<Host<ResolvedAddress, C>> hosts, Random random, Predicate<C> selector,
                          boolean forceNewConnectionAndReserve, @Nullable ContextMap contextMap) {
        // If there are only two hosts we only try once since there is no chance we'll select different hosts
        // on further iterations.
        Host<ResolvedAddress, C> failOpenHost = null;
        for (int j = hosts.size() == 2 ? 1 : maxEffort; j > 0; j--) {
            // Pick two random indexes that don't collide. Limit the range on the second index to 1 less than
            // the max value so that if there is a collision we can safety increment. We also increment if
            // i2 > i1 to avoid bias toward lower numbers since we limited the range by 1.
            final int i1 = random.nextInt(size);
            int i2 = random.nextInt(size - 1);
            if (i2 >= i1) {
                ++i2;
            }

            Host<ResolvedAddress, C> t1 = hosts.get(i1);
            Host<ResolvedAddress, C> t2 = hosts.get(i2);
            final Host.Status t1Status = t1.status(forceNewConnectionAndReserve);
            final Host.Status t2Status = t2.status(forceNewConnectionAndReserve);
            // Priority of selection: health > score > failOpen
            // Only if both hosts are healthy do we consider score.
            if (t1Status.healthy && t2Status.healthy) {
                // both are healthy. Select based on score, using t1 if equal.
                if (t1.score() < t2.score()) {
                    Host<ResolvedAddress, C> tmp = t1;
                    t1 = t2;
                    t2 = tmp;
                }
                Single<C> result = selectFromHost(
                        t1, t1Status, selector, forceNewConnectionAndReserve, contextMap);
                // We didn't get a connection from the first host: maybe it is inactive
                // and we couldn't reserve a connection. Try the second host.
                if (result == null) {
                    result = selectFromHost(t2, t2Status, selector, forceNewConnectionAndReserve, contextMap);
                }
                // If we have a connection we're good to go. Otherwise fall through for another round.
                // Since we didn't get a connection from either of them there is no reason to think they'll
                // yield a connection if we make them the fallback.
                if (result != null) {
                    return result;
                }
            } else if (t2Status.healthy) {
                Single<C> result = selectFromHost(
                        t2, t2Status, selector, forceNewConnectionAndReserve, contextMap);
                if (result != null) {
                    return result;
                }
            } else if (t1Status.healthy) {
                Single<C> result = selectFromHost(
                        t1, t1Status, selector, forceNewConnectionAndReserve, contextMap);
                if (result != null) {
                    return result;
                }
            } else if (failOpen && failOpenHost == null) {
                // both are  unhealthy. If either are active they can be the backup.
                if (t1Status.active) {
                    failOpenHost = t1;
                } else if (t2Status.active) {
                    failOpenHost = t2;
                }
            }
        }
        // Max effort exhausted. We failed to find a healthy and active host. If we want to fail open and
        // found an active host but it was considered unhealthy, try it anyway.
        if (failOpenHost != null) {
            Single<C> result = selectFromHost(failOpenHost, failOpenHost.status(forceNewConnectionAndReserve),
                    selector, forceNewConnectionAndReserve, contextMap);
            if (result != null) {
                return result;
            }
        }
        return noActiveHostsFailure(hosts);
    }

    private Random getRandom() {
        return random == null ? ThreadLocalRandom.current() : random;
    }
}
