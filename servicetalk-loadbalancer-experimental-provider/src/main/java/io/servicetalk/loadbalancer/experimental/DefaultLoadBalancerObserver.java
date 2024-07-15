/*
 * Copyright © 2024 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.loadbalancer.experimental;

import io.servicetalk.client.api.NoActiveHostException;
import io.servicetalk.client.api.ServiceDiscovererEvent;
import io.servicetalk.loadbalancer.LoadBalancerObserver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import javax.annotation.Nullable;

import static java.util.Objects.requireNonNull;

final class DefaultLoadBalancerObserver implements LoadBalancerObserver {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultLoadBalancerObserver.class);

    private final String lbDescription;

    DefaultLoadBalancerObserver(final String lbDescription) {
        this.lbDescription = requireNonNull(lbDescription, "lbDescription");
    }

    @Override
    public HostObserver hostObserver(Object resolvedAddress) {
        return new HostObserverImpl(resolvedAddress);
    }

    @Override
    public void onNoHostsAvailable() {
        LOGGER.debug("{}- onNoHostsAvailable()", lbDescription);
    }

    @Override
    public void onServiceDiscoveryEvent(Collection<? extends ServiceDiscovererEvent<?>> events, int oldHostSetSize,
                                        int newHostSetSize) {
        LOGGER.debug("{}- onServiceDiscoveryEvent(events: {}, oldHostSetSize: {}, newHostSetSize: {})",
                lbDescription, events, oldHostSetSize, newHostSetSize);
    }

    @Override
    public void onNoActiveHostsAvailable(int hostSetSize, NoActiveHostException exception) {
        LOGGER.debug("{}- No active hosts available. Host set size: {}.", lbDescription, hostSetSize, exception);
    }

    @Override
    public void onHostSetChanged(Collection<? extends Host> newHosts) {
        if (LOGGER.isDebugEnabled()) {
            int healthyCount = 0;
            for (Host host : newHosts) {
                if (host.isHealthy()) {
                    healthyCount++;
                }
            }
            LOGGER.debug("{}- onHostSetChanged(host set size: {}, healthy: {}). New hosts: {}", lbDescription,
                    newHosts.size(), healthyCount, newHosts);
        }
    }

    private final class HostObserverImpl implements HostObserver {

        private final Object resolvedAddress;

         HostObserverImpl(final Object resolvedAddress) {
            this.resolvedAddress = resolvedAddress;
        }

        @Override
        public void onHostMarkedExpired(int connectionCount) {
            LOGGER.debug("{}:{}- onHostMarkedExpired(connectionCount: {})",
                    lbDescription, resolvedAddress, connectionCount);
        }

        @Override
        public void onActiveHostRemoved(int connectionCount) {
            LOGGER.debug("{}:{}- onActiveHostRemoved(connectionCount: {})",
                    lbDescription, resolvedAddress, connectionCount);
        }

        @Override
        public void onExpiredHostRevived(int connectionCount) {
            LOGGER.debug("{}:{}- onExpiredHostRevived(connectionCount: {})",
                    lbDescription, resolvedAddress, connectionCount);
        }

        @Override
        public void onExpiredHostRemoved(int connectionCount) {
            LOGGER.debug("{}:{}- onExpiredHostRemoved(connectionCount: {})",
                    lbDescription, resolvedAddress, connectionCount);
        }

        @Override
        public void onHostMarkedUnhealthy(@Nullable Throwable cause) {
            LOGGER.debug("{}:{}- onHostMarkedUnhealthy(ex)", lbDescription, resolvedAddress, cause);
        }

        @Override
        public void onHostRevived() {
            LOGGER.debug("{}:{}- onHostRevived()", lbDescription, resolvedAddress);
        }
    }
}
