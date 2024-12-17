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
import io.servicetalk.client.api.NoAvailableHostException;
import io.servicetalk.client.api.ServiceDiscovererEvent;

import java.util.Collection;
import javax.annotation.Nullable;

/**
 * An observer that provides visibility into a {@link io.servicetalk.client.api.LoadBalancer}.
 */
public interface LoadBalancerObserver {

    /**
     * Get a {@link HostObserver}.
     * @param resolvedAddress the resolved address of the host.
     * @return a {@link HostObserver}.
     */
    HostObserver hostObserver(Object resolvedAddress);

    /**
     * Callback for monitoring the changes due to a service discovery update.
     * @param events the collection of {@link ServiceDiscovererEvent}s received by the load balancer.
     */
    void onServiceDiscoveryEvent(Collection<? extends ServiceDiscovererEvent<?>> events);

    /**
     * Callback for when the set of hosts used by the load balancer has changed. This set may not
     * exactly reflect the state of the service discovery system due to filtering of zero-weight
     * hosts and forms of sub-setting and thus may only represent the hosts that the selection
     * algorithm may use.
     * @param oldHosts the old set of hosts used by the selection algorithm.
     * @param newHosts the new set of hosts used by the selection algorithm.
     */
    void onHostsUpdate(Collection<? extends Host> oldHosts, Collection<? extends Host> newHosts);

    /**
     * Callback for when connection selection fails due to no hosts being available.
     * @param exception an exception with more details about the failure.
     */
    void onNoAvailableHostException(NoAvailableHostException exception);

    /**
     * Callback for when connection selection fails due to all hosts being inactive.
     * @param hostsCount the size of the current host set where all hosts are inactive.
     * @param exception an exception with more details about the failure.
     */
    void onNoActiveHostException(int hostsCount, NoActiveHostException exception);

    /**
     * An observer for {@link io.servicetalk.loadbalancer.Host} events.
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
         * Callback for when a {@link io.servicetalk.loadbalancer.Host} transitions from healthy to unhealthy.
         * @param cause the most recent cause of the transition.
         */
        void onHostMarkedUnhealthy(@Nullable Throwable cause);

        /**
         * Callback for when a {@link io.servicetalk.loadbalancer.Host} transitions from unhealthy to healthy.
         */
        void onHostRevived();
    }

    /**
     * A description of a host.
     */
    interface Host {
        /**
         * The address of the host.
         * @return the address of the host.
         */
        Object address();

        /**
         * Determine the health status of this host.
         * @return whether the host considers itself healthy enough to serve traffic. This is best effort and does not
         *         guarantee that the request will succeed.
         */
        boolean isHealthy();

        /**
         * The weight of the host, relative to the weights of associated hosts as used for load balancing.
         * @return the relative weight of the host.
         */
        double loadBalancingWeight();
    }
}
