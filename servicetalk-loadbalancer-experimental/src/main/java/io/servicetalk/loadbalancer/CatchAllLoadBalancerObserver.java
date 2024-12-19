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
        safeReport(() -> delegate.onServiceDiscoveryEvent(events), "onServiceDiscoveryEvent");
    }

    @Override
    public void onHostsUpdate(Collection<? extends Host> oldHosts, Collection<? extends Host> newHosts) {
        safeReport(() -> delegate.onHostsUpdate(oldHosts, newHosts), "onHostsUpdate");
    }

    @Override
    public void onNoAvailableHostException(NoAvailableHostException exception) {
        safeReport(() -> delegate.onNoAvailableHostException(exception), "onNoAvailableHostException");
    }

    @Override
    public void onNoActiveHostException(Collection<? extends Host> hosts, NoActiveHostException exception) {
        safeReport(() -> delegate.onNoActiveHostException(hosts, exception), "onNoActiveHostException");
    }

    private static final class CatchAllHostObserver implements HostObserver {

        private final HostObserver delegate;

        CatchAllHostObserver(HostObserver delegate) {
            this.delegate = requireNonNull(delegate, "delegate");
        }

        @Override
        public void onHostMarkedExpired(int connectionCount) {
            safeReport(() -> delegate.onHostMarkedExpired(connectionCount), "onHostMarkedExpired");
        }

        @Override
        public void onActiveHostRemoved(int connectionCount) {
            safeReport(() -> delegate.onActiveHostRemoved(connectionCount), "onActiveHostRemoved");
        }

        @Override
        public void onExpiredHostRevived(int connectionCount) {
            safeReport(() -> delegate.onExpiredHostRevived(connectionCount), "onExpiredHostRevived");
        }

        @Override
        public void onExpiredHostRemoved(int connectionCount) {
            safeReport(() -> delegate.onExpiredHostRemoved(connectionCount), "onExpiredHostRemoved");
        }

        @Override
        public void onHostMarkedUnhealthy(@Nullable Throwable cause) {
            safeReport(() -> delegate.onHostMarkedUnhealthy(cause), "onHostMarkedUnhealthy");
        }

        @Override
        public void onHostRevived() {
            safeReport(() -> delegate.onHostRevived(), "onHostRevived");
        }

        private void safeReport(final Runnable runnable, final String eventName) {
            doSafeReport(runnable, delegate, eventName);
        }
    }

    private void safeReport(final Runnable runnable, final String eventName) {
        doSafeReport(runnable, delegate, eventName);
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

    private static void doSafeReport(final Runnable runnable, final Object observer, final String eventName) {
        try {
            runnable.run();
        } catch (Throwable unexpected) {
            LOGGER.warn("Unexpected exception from {} while reporting an {} event", observer, eventName, unexpected);
        }
    }
}
