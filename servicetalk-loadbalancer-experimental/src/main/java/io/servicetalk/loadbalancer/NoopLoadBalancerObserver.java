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

final class NoopLoadBalancerObserver implements LoadBalancerObserver {

    private static final LoadBalancerObserver INSTANCE = new NoopLoadBalancerObserver();
    private static final LoadBalancerObserverFactory FACTORY_INSTANCE = new NoopLoadBalancerObserverFactory();

    private NoopLoadBalancerObserver() {
        // only private instance
    }

    @Override
    public HostObserver hostObserver(Object resolvedAddress) {
        return NoopHostObserver.INSTANCE;
    }

    @Override
    public void onServiceDiscoveryEvent(Collection<? extends ServiceDiscovererEvent<?>> events,
                                        int oldHostSetSize, int newHostSetSize) {
        // noop
    }

    @Override
    public void onHostSetChanged(Collection<? extends Host> newHosts) {
        // noop
    }

    @Override
    public void onNoAvailableHostException(NoAvailableHostException exception) {
        // noop
    }

    @Override
    public void onNoActiveHostException(int hostSetSize, NoActiveHostException exn) {
        // noop
    }

    private static final class NoopHostObserver implements LoadBalancerObserver.HostObserver {

        private static final HostObserver INSTANCE = new NoopHostObserver();

        private NoopHostObserver() {
        }

        @Override
        public void onHostMarkedExpired(int connectionCount) {
            // noop
        }

        @Override
        public void onExpiredHostRemoved(int connectionCount) {
            // noop
        }

        @Override
        public void onExpiredHostRevived(int connectionCount) {
            // noop
        }

        @Override
        public void onActiveHostRemoved(int connectionCount) {
            // noop
        }

        @Override
        public void onHostMarkedUnhealthy(@Nullable Throwable cause) {
            // noop
        }

        @Override
        public void onHostRevived() {
            // noop
        }
    }

    static LoadBalancerObserver instance() {
        return INSTANCE;
    }

    static LoadBalancerObserverFactory factory() {
        return FACTORY_INSTANCE;
    }

    private static final class NoopLoadBalancerObserverFactory implements LoadBalancerObserverFactory {
        @Override
        public LoadBalancerObserver newObserver(String lbDescription) {
            return INSTANCE;
        }

        @Override
        public String toString() {
            return "NoopLoadBalancerObserverFactory";
        }
    }
}
