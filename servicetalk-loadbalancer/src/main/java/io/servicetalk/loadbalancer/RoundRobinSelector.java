/*
 * Copyright © 2018-2023 Apple Inc. and the ServiceTalk project authors
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

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.function.Predicate;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Single.failed;
import static io.servicetalk.concurrent.api.Single.succeeded;

final class RoundRobinSelector<ResolvedAddress, C extends LoadBalancedConnection>
        implements HostSelector<ResolvedAddress, C> {

    @SuppressWarnings("rawtypes")
    private static final AtomicIntegerFieldUpdater<RoundRobinSelector> indexUpdater =
            AtomicIntegerFieldUpdater.newUpdater(RoundRobinSelector.class, "index");

    private final String targetResource;
    private volatile List<Host<ResolvedAddress, C>> hosts;
    @SuppressWarnings("unused")
    private volatile int index;

    RoundRobinSelector(String targetResource) {
        this.targetResource = targetResource;
        hosts = Collections.emptyList();
    }

    @Override
    public void hostSetChanged(List<Host<ResolvedAddress, C>> hosts) {
        this.hosts = hosts;
    }

    @Override
    public Single<C> selectConnection(
            final Predicate<C> selector, @Nullable final ContextMap context,
            final boolean forceNewConnectionAndReserve) {
        final List<Host<ResolvedAddress, C>> usedHosts = hosts;
        // try one loop over hosts and if all are expired, give up
        final int cursor = (indexUpdater.getAndIncrement(this) & Integer.MAX_VALUE) % usedHosts.size();
        Host<ResolvedAddress, C> pickedHost = null;
        for (int i = 0; i < usedHosts.size(); ++i) {
            // for a particular iteration we maintain a local cursor without contention with other requests
            final int localCursor = (cursor + i) % usedHosts.size();
            final Host<ResolvedAddress, C> host = usedHosts.get(localCursor);
            assert host != null : "Host can't be null.";

            if (!forceNewConnectionAndReserve) {
                // First see if an existing connection can be used
                C connection = host.pickConnection(selector, context);
                if (connection != null) {
                    return succeeded(connection);
                }
            }

            // Don't open new connections for expired or unhealthy hosts, try a different one.
            // Unhealthy hosts have no open connections – that's why we don't fail earlier, the loop will not progress.
            if (host.isActiveAndHealthy()) {
                pickedHost = host;
                break;
            }
        }
        if (pickedHost == null) {
            return failed(Exceptions.StacklessNoActiveHostException.newInstance("Failed to pick an active host for " +
                            targetResource + ". Either all are busy, expired, or unhealthy: " + usedHosts,
                    this.getClass(), "selectConnection(...)"));
        }
        // We have a host but no connection was selected: create a new one.
        return pickedHost.newConnection(selector, forceNewConnectionAndReserve, context);
    }
}
