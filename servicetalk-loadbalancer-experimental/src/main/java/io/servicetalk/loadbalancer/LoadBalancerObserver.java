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

import io.servicetalk.client.api.NoActiveHostException;
import io.servicetalk.client.api.ServiceDiscovererEvent;

import java.util.Collection;
import javax.annotation.Nullable;

/**
 * An observer that provides visibility into a {@link io.servicetalk.client.api.LoadBalancer}
 * @param <ResolvedAddress> the type of the resolved address.
 */
public interface LoadBalancerObserver<ResolvedAddress> {

    /**
     * Get a {@link HostObserver}.
     * @return a {@link HostObserver}.
     */
    HostObserver hostObserver(ResolvedAddress resolvedAddress);

    /**
     * Callback for when connection selection fails due to no hosts being available.
     */
    void onNoHostsAvailable();

    /**
     * Callback for monitoring the changes due to a service discovery update.
     */
    void onServiceDiscoveryEvent(Collection<? extends ServiceDiscovererEvent<ResolvedAddress>> events,
                                 int oldHostSetSize, int newHostSetSize);

    /**
     * Callback for when connection selection fails due to all hosts being inactive.
     */
    void onNoActiveHostsAvailable(int hostSetSize, NoActiveHostException exn);

    /**
     * An observer for {@link Host} events.
     */
    interface HostObserver {

        /**
         * Callback for when an active host is marked expired.
         * @param connectionCount the number of active connections for the host.
         */
        void onHostMarkedExpired(int connectionCount);

        /**
         * Callback for when a host is removed by service discovery.
         * @param connectionCount the number of open connections when the host was removed.
         */
        void onActiveHostRemoved(int connectionCount);

        /**
         * Callback for when an expired host is returned to an active state.
         * @param connectionCount the number of active connections when the host was revived.
         */
        void onExpiredHostRevived(int connectionCount);

        /**
         * Callback for when an expired host is removed.
         * @param connectionCount the number of open connections when the host was removed.
         */
        void onExpiredHostRemoved(int connectionCount);

        /**
         * Callback for when a {@link Host} transitions from healthy to unhealthy.
         * @param cause the most recent cause of the transition.
         */
        void onHostMarkedUnhealthy(@Nullable Throwable cause);

        /**
         * Callback for when a {@link Host} transitions from unhealthy to healthy.
         */
        void onHostRevived();
    }
}
