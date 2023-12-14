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

    private final AtomicInteger index;
    private final List<Host<ResolvedAddress, C>> usedHosts;
    private final boolean failOpen;

    RoundRobinSelector(final List<Host<ResolvedAddress, C>> usedHosts, final String targetResource,
                       final boolean failOpen) {
        this(new AtomicInteger(), usedHosts, targetResource, failOpen);
    }

    private RoundRobinSelector(final AtomicInteger index, final List<Host<ResolvedAddress, C>> usedHosts,
                               final String targetResource, final boolean failOpen) {
        super(usedHosts, targetResource);
        this.index = index;
        this.usedHosts = usedHosts;
        this.failOpen = failOpen;
    }

    @Override
    protected Single<C> selectConnection0(
            final Predicate<C> selector, @Nullable final ContextMap context,
            final boolean forceNewConnectionAndReserve) {
        // try one loop over hosts and if all are expired, give up
        final int cursor = (index.getAndIncrement() & Integer.MAX_VALUE) % usedHosts.size();
        Host<ResolvedAddress, C> failOpenHost = null;
        for (int i = 0; i < usedHosts.size(); ++i) {
            // for a particular iteration we maintain a local cursor without contention with other requests
            final int localCursor = (cursor + i) % usedHosts.size();
            final Host<ResolvedAddress, C> host = usedHosts.get(localCursor);
            if (!host.isUnhealthy(forceNewConnectionAndReserve)) {
                Single<C> result = selectFromHealthyHost(host, selector, forceNewConnectionAndReserve, context);
                if (result != null) {
                    return result;
                }
            }

            // If the host is active we can use it for backup.
            if (failOpen && failOpenHost == null && host.isActive()) {
                failOpenHost = host;
            }
        }
        // We want to fail open even if this host think it's not healthy.
        if (failOpenHost != null) {
            return failOpenHost.newConnection(selector, forceNewConnectionAndReserve, context);
        }
        // We were unable to find a suitable host.
        return noActiveHostsFailure(usedHosts);
    }

    @Override
    public HostSelector<ResolvedAddress, C> rebuildWithHosts(@Nonnull List<Host<ResolvedAddress, C>> hosts) {
        return new RoundRobinSelector<>(index, hosts, getTargetResource(), failOpen);
    }
}
