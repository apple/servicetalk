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
import io.servicetalk.client.api.ScoreSupplier;
import io.servicetalk.concurrent.api.ListenableAsyncCloseable;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.context.api.ContextMap;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Representation of a concrete host that can provide connections.
 * @param <ResolvedAddress> the type of resolved address.
 * @param <C> the concrete type of returned connections.
 */
interface Host<ResolvedAddress, C extends LoadBalancedConnection> extends ListenableAsyncCloseable, ScoreSupplier {

    /**
     * Select an existing connection from the host.
     * @return the selected host, or null if a suitable host couldn't be found.
     */
    @Nullable C pickConnection(Predicate<C> selector, @Nullable final ContextMap context);

    /**
     * Create a new connection to the host.
     * @param forceNewConnectionAndReserve forces creation of a new dedicated connection that won't be part
     *                                     of the connection pool.
     * @return the selected host, or null if a suitable host couldn't be found.
     */
    Single<C> newConnection(
            Predicate<C> selector, final boolean forceNewConnectionAndReserve, @Nullable final ContextMap context);

    /**
     * The address of the host
     */
    ResolvedAddress address();

    /**
     * A snapshot of the current connection pool.
     * This is only for debugging purposes and shouldn't be used for implementation details.
     */
    Map.Entry<ResolvedAddress, List<C>> asEntry();

    /**
     * Whether the host is both considered active by service discovery and healthy by the failure
     * detection mechanisms.
     */
    boolean isActiveAndHealthy();

    /**
     * Whether the host is considered unhealthy bo the failure detection mechanisms.
     */
    boolean isUnhealthy();

    /**
     * Signal to the host that it has been re-discovered by the service-discovery mechanism and is expected
     * to be available to serve requests. This does not imply that the host is healthy.
     * @return true if the host status was successfully transitioned to active before the host closed, false otherwise.
     */
    boolean markActiveIfNotClosed();

    /**
     * Signal that the host should be considered closed and no more connections should be selected or created.
     * This does not imply that existing connection should hard close.
     */
    void markClosed();

    /**
     * Signal that the host should not be the target of new connections but existing connections are still expected
     * to be valid and can serve new requests. This does not have any implications for the health status of the host.
     */
    void markExpired();
}
