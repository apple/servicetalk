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

import java.util.List;
import java.util.function.Predicate;
import javax.annotation.Nullable;

/**
 * Interface abstracting away the method of host selection.
 *
 * This is useful for supporting multiple load balancing algorithms such as round-robin,
 * weighted round-robin, random, and least-loaded where each algorithm only needing to concern
 * itself with the data structures important to the algorithm itself.
 */
interface HostSelector<ResolvedAddress, C extends LoadBalancedConnection> {

    /**
     * Notify the selector that the set of available hosts has changed.
     *
     * This method gives the host selector the opportunity to modify or rebuild any
     * internal data structures that it uses in it's job of distributing traffic amongst
     * the hosts.
     * This method will not be called concurrently.
     *
     * @param hosts the current set of available hosts.
     */
    void hostSetChanged(List<Host<ResolvedAddress, C>> hosts);

    /**
     * Select or establish a new connection from an exist Host.
     *
     * This method will be called concurrently with other selectConnection calls and
     * hostSetChanged calls and must be thread safe under those conditions.
     */
    Single<C> selectConnection(Predicate<C> selector, @Nullable ContextMap context,
                               boolean forceNewConnectionAndReserve);
}
