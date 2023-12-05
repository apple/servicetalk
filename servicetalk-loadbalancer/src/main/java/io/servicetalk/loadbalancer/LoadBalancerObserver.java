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

import javax.annotation.Nullable;
import java.util.Collection;

/**
 * An observer that provides visibility into a {@link io.servicetalk.client.api.LoadBalancer}
 * @param <ResolvedAddress> the type of the resolved address.
 */
interface LoadBalancerObserver<ResolvedAddress> {

    /**
     * Get a {@link HostObserver}.
     * @return a {@link HostObserver}.
     */
    HostObserver<ResolvedAddress> hostObserver();

    /**
     * Get an {@link OutlierObserver}.
     * @return an {@link OutlierObserver}.
     */
    OutlierObserver<ResolvedAddress> outlierEventObserver();

    /**
     * Callback for when connection selection fails due to no hosts being available.
     */
    void noHostsAvailable();

    /**
     * Callback for monitoring the changes due to a service discovery update.
     */
    void serviceDiscoveryEvent(Collection<? extends ServiceDiscovererEvent<ResolvedAddress>> events,
                               int oldHostSetSize, int newHostSetSize);

    /**
     * Callback for when connection selection fails due to all hosts being inactive.
     */
    void noActiveHostsAvailable(int hostSetSize, NoActiveHostException exn);

    interface HostObserver<ResolvedAddress> {

        /**
         * Callback for when an active host is marked expired.
         * @param address the resolved address.
         * @param connectionCount the number of active connections for the host.
         */
        void hostMarkedExpired(ResolvedAddress address, int connectionCount);

        /**
         * Callback for when a host is removed by service discovery.
         * @param address the resolved address.
         * @param connectionCount the number of connections that were associated with the host.
         */
        void activeHostRemoved(ResolvedAddress address, int connectionCount);

        /**
         * Callback for when an expired host is returned to an active state.
         * @param address the resolved address.
         * @param connectionCount the number of active connections when the host was revived.
         */
        void expiredHostRevived(ResolvedAddress address, int connectionCount);

        /**
         * Callback for when an expired host is removed.
         * @param address the resolved address.
         */
        void expiredHostRemoved(ResolvedAddress address);

        /**
         * Callback for when a host is created.
         * @param address the resolved address.
         */
        void hostCreated(ResolvedAddress address);
    }

    /**
     * An observer of outlier events.
     * @param <ResolvedAddress> the type of the resolved address.
     */
    interface OutlierObserver<ResolvedAddress> {

        /**
         * Callback for when a {@link Host} is transitions from healthy to unhealthy.
         * @param address the resolved address.
         * @param cause the most recent cause of the transition.
         */
        void hostMarkedUnhealthy(ResolvedAddress address, @Nullable Throwable cause);

        /**
         * Callback for when a {@link Host} transitions from unhealthy to healthy.
         * @param address the resolved address.
         */
        void hostRevived(ResolvedAddress address);
    }
}
