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

/**
 * Interface abstracting away the method of host selection.
 * <p>
 * Thread Safety
 * Because the HostSelector is used on the hot path some care must be paid to thread safety. The easiest
 * pattern to use is to make the internal state effectively immutable and rebuilds (see below) generate new
 * immutable instances as necessary. The interface is expected to adhere to the following rules:
 *
 * <li>The {@link HostSelector#selectConnection(Predicate, ContextMap, boolean)} method will be used
 * concurrently with calls to itself as well as calls to {@link HostSelector#rebuildWithHosts(List)}.</li>
 * <li>The {@link HostSelector#rebuildWithHosts(List)} will only be called sequentially with respect to itself.</li>
 * <p>
 * Note that the HostSelector does not own the provided {@link Host}s and therefore should not
 * attempt to manage their lifecycle.
 */
interface HostSelector<ResolvedAddress, C extends LoadBalancedConnection> {

    /**
     * Select or establish a new connection from an existing Host.
     *
     * This method will be called concurrently with other selectConnection calls and
     * hostSetChanged calls and must be thread safe under those conditions.
     */
    Single<C> selectConnection(Predicate<C> selector, @Nullable ContextMap context,
                               boolean forceNewConnectionAndReserve);

    /**
     * Generate another HostSelector using the provided host list.
     * <p>
     * This method will be called when the host set is updated and provides a way for the
     * HostSelector to rebuild any data structures necessary. Note that the method can return
     * {@code this} or a new selector depending on the convenience of implementation.
     * @param hosts the new list of {@link Host}s the returned selector should choose from.
     * @return the next selector that should be used for host selection.
     */
    HostSelector<ResolvedAddress, C> rebuildWithHosts(List<? extends Host<ResolvedAddress, C>> hosts);

    /**
     * Whether the load balancer believes itself to be healthy enough for serving traffic.
     * <p>
     * Note that this is both racy and best effort: just because a {@link HostSelector} is
     * healthy doesn't guarantee that a request will succeed nor does an unhealthy status
     * indicate that this selector is guaranteed to fail a request.
     * @return whether the load balancer believes itself healthy enough and likely to successfully serve traffic.
     */
    boolean isHealthy();

    /**
     * The size of the host candidate pool for this host selector.
     * Note that this is primarily for observability purposes.
     * @return the size of the host candidate pool for this host selector.
     */
    int hostSetSize();
}
