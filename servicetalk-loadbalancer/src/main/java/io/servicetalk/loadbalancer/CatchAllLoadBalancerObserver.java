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
package io.servicetalk.loadbalancer;

import io.servicetalk.client.api.NoActiveHostException;
import io.servicetalk.client.api.NoAvailableHostException;
import io.servicetalk.client.api.ServiceDiscovererEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import javax.annotation.Nullable;

import static java.util.Objects.requireNonNull;

final class CatchAllLoadBalancerObserver implements LoadBalancerObserver {

    private static final Logger LOGGER = LoggerFactory.getLogger(CatchAllLoadBalancerObserver.class);

    private final LoadBalancerObserver delegate;

    private CatchAllLoadBalancerObserver(LoadBalancerObserver delegate) {
        this.delegate = delegate;
    }

    @Override
    public HostObserver hostObserver(Object resolvedAddress) {
        try {
            return new CatchAllHostObserver(delegate.hostObserver(resolvedAddress));
        } catch (Throwable ex) {
            LOGGER.warn("Unexpected exception from {} while getting a HostObserver", delegate, ex);
            return NoopLoadBalancerObserver.NoopHostObserver.INSTANCE;
        }
    }

    @Override
    public void onServiceDiscoveryEvent(Collection<? extends ServiceDiscovererEvent<?>> events) {
        try {
            delegate.onServiceDiscoveryEvent(events);
        } catch (Throwable unexpected) {
            LOGGER.warn("Unexpected exception from {} while reporting an onServiceDiscoveryEvent event",
                    delegate, unexpected);
        }
    }

    @Override
    public void onHostsUpdate(Collection<? extends Host> oldHosts, Collection<? extends Host> newHosts) {
        try {
            delegate.onHostsUpdate(oldHosts, newHosts);
        } catch (Throwable unexpected) {
            LOGGER.warn("Unexpected exception from {} while reporting an onHostsUpdate event", delegate, unexpected);
        }
    }

    @Override
    public void onNoAvailableHostException(NoAvailableHostException exception) {
        try {
            delegate.onNoAvailableHostException(exception);
        } catch (Throwable unexpected) {
            LOGGER.warn("Unexpected exception from {} while reporting an onNoAvailableHostException event",
                    delegate, unexpected);
        }
    }

    @Override
    public void onNoActiveHostException(Collection<? extends Host> hosts, NoActiveHostException exception) {
        try {
            delegate.onNoActiveHostException(hosts, exception);
        } catch (Throwable unexpected) {
            LOGGER.warn("Unexpected exception from {} while reporting an onNoActiveHostException event",
                    delegate, unexpected);
        }
    }

    private static final class CatchAllHostObserver implements HostObserver {

        private final HostObserver delegate;

        CatchAllHostObserver(HostObserver delegate) {
            this.delegate = requireNonNull(delegate, "delegate");
        }

        @Override
        public void onHostMarkedExpired(int connectionCount) {
            try {
                delegate.onHostMarkedExpired(connectionCount);
            } catch (Throwable unexpected) {
                LOGGER.warn("Unexpected exception from {} while reporting an onHostMarkedExpired event",
                        delegate, unexpected);
            }
        }

        @Override
        public void onActiveHostRemoved(int connectionCount) {
            try {
                delegate.onActiveHostRemoved(connectionCount);
            } catch (Throwable unexpected) {
                LOGGER.warn("Unexpected exception from {} while reporting an onActiveHostRemoved event",
                        delegate, unexpected);
            }
        }

        @Override
        public void onExpiredHostRevived(int connectionCount) {
            try {
                delegate.onExpiredHostRevived(connectionCount);
            } catch (Throwable unexpected) {
                LOGGER.warn("Unexpected exception from {} while reporting an onExpiredHostRevived event",
                        delegate, unexpected);
            }
        }

        @Override
        public void onExpiredHostRemoved(int connectionCount) {
            try {
                delegate.onExpiredHostRemoved(connectionCount);
            } catch (Throwable unexpected) {
                LOGGER.warn("Unexpected exception from {} while reporting an onExpiredHostRemoved event",
                        delegate, unexpected);
            }
        }

        @Override
        public void onHostMarkedUnhealthy(@Nullable Throwable cause) {
            try {
                delegate.onHostMarkedUnhealthy(cause);
            } catch (Throwable unexpected) {
                LOGGER.warn("Unexpected exception from {} while reporting an onHostMarkedUnhealthy event",
                        delegate, unexpected);
            }
        }

        @Override
        public void onHostRevived() {
            try {
                delegate.onHostRevived();
            } catch (Throwable unexpected) {
                LOGGER.warn("Unexpected exception from {} while reporting an onHostRevived event",
                        delegate, unexpected);
            }
        }
    }

    static LoadBalancerObserver wrap(LoadBalancerObserver observer) {
        requireNonNull(observer, "observer");
        if (observer instanceof CatchAllLoadBalancerObserver) {
            return observer;
        }
        if (observer instanceof NoopLoadBalancerObserver) {
            return observer;
        }
        return new CatchAllLoadBalancerObserver(observer);
    }
}
