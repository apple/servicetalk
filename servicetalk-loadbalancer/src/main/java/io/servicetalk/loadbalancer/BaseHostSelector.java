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
import java.util.function.Predicate;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Single.failed;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static java.util.Objects.requireNonNull;

abstract class BaseHostSelector<ResolvedAddress, C extends LoadBalancedConnection>
        implements HostSelector<ResolvedAddress, C> {

    private final String targetResource;
    private final List<Host<ResolvedAddress, C>> hosts;
    BaseHostSelector(final List<Host<ResolvedAddress, C>> hosts, final String targetResource) {
        this.hosts = hosts;
        this.targetResource = requireNonNull(targetResource, "targetResource");
    }

    protected abstract Single<C> selectConnection0(Predicate<C> selector, @Nullable ContextMap context,
                                         boolean forceNewConnectionAndReserve);

    @Override
    public final Single<C> selectConnection(Predicate<C> selector, @Nullable ContextMap context,
                                      boolean forceNewConnectionAndReserve) {
        return hosts.isEmpty() ? noHostsFailure() : selectConnection0(selector, context, forceNewConnectionAndReserve);
    }

    @Override
    public final int hostSetSize() {
        return hosts.size();
    }

    @Override
    public final boolean isHealthy() {
        // TODO: in the future we may want to make this more of a "are at least X hosts available" question
        //  so that we can compose a group of selectors into a priority set.
        return anyHealthy(hosts);
    }

    protected final String getTargetResource() {
        return targetResource;
    }

    protected final Single<C> noActiveHostsFailure(List<Host<ResolvedAddress, C>> usedHosts) {
        return failed(Exceptions.StacklessNoActiveHostException.newInstance("Failed to pick an active host for " +
                        getTargetResource() + ". Either all are busy, expired, or unhealthy: " + usedHosts,
                this.getClass(), "selectConnection(...)"));
    }

    // TODO: this could really be the core method on `Host` other than the nullable part. We could use Optional...
    //  The API of passing in the status is weird but we could either live with it or just ask the question again
    //  if we need to make a new connection.
    protected final @Nullable Single<C> selectFromHost(
            Host<ResolvedAddress, C> host, Host.Status status, Predicate<C> selector,
            boolean forceNewConnectionAndReserve, @Nullable ContextMap contextMap) {
        // First see if we can get an existing connection regardless of health status.
        if (!forceNewConnectionAndReserve) {
            C c = host.pickConnection(selector, contextMap);
            if (c != null) {
                return succeeded(c);
            }
        }
        // We can only create a new connection if the host is active. It's possible for it to think that
        // it's healthy based on having connections but not being active but we weren't able to pick an
        // existing connection.
        return status.active ? host.newConnection(selector, forceNewConnectionAndReserve, contextMap) : null;
    }

    private Single<C> noHostsFailure() {
        return failed(Exceptions.StacklessNoAvailableHostException.newInstance(
                "No hosts are available to connect for " + targetResource + ".",
                this.getClass(), "selectConnection(...)"));
    }

    private static <ResolvedAddress, C extends LoadBalancedConnection> boolean anyHealthy(
            final List<Host<ResolvedAddress, C>> usedHosts) {
        for (Host<ResolvedAddress, C> host : usedHosts) {
            if (host.status(false).healthy) {
                return true;
            }
        }
        return usedHosts.isEmpty();
    }
}
