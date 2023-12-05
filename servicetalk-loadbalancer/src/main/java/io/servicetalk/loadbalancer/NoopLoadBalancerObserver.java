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

final class NoopLoadBalancerObserver<ResolvedAddress> implements LoadBalancerObserver<ResolvedAddress> {

    private static final LoadBalancerObserver<Object> INSTANCE = new NoopLoadBalancerObserver<>();

    private NoopLoadBalancerObserver() {
        // only private instance
    }

    @Override
    public HostObserver<ResolvedAddress> hostObserver() {
        return (HostObserver<ResolvedAddress>) NoopHostObserver.INSTANCE;
    }

    @Override
    public OutlierObserver<ResolvedAddress> outlierEventObserver() {
        return (OutlierObserver<ResolvedAddress>) NoopOutlierObserver.INSTANCE;
    }

    @Override
    public void noHostsAvailable() {
        // noop
    }

    @Override
    public void noActiveHostsAvailable(int hostSetSize, NoActiveHostException exn) {
        // noop
    }

    private static final class NoopOutlierObserver<ResolvedAddress>
            implements LoadBalancerObserver.OutlierObserver<ResolvedAddress> {

        private static final OutlierObserver<Object> INSTANCE = new NoopOutlierObserver<>();

        private NoopOutlierObserver() {
        }

        @Override
        public void hostMarkedUnhealthy(ResolvedAddress address, Throwable cause) {
            // noop
        }

        @Override
        public void hostRevived(ResolvedAddress address) {
            // noop
        }
    }

    private static final class NoopHostObserver<ResolvedAddress> implements
            LoadBalancerObserver.HostObserver<ResolvedAddress> {

        private static final HostObserver<Object> INSTANCE = new NoopHostObserver<>();

        private NoopHostObserver() {
        }

        @Override
        public void hostMarkedExpired(ResolvedAddress resolvedAddress, int connectionCount) {
            // noop
        }

        @Override
        public void expiredHostRemoved(ResolvedAddress resolvedAddress) {
            // noop
        }

        @Override
        public void expiredHostRevived(ResolvedAddress resolvedAddress, int connectionCount) {
            // noop
        }

        @Override
        public void unavailableHostRemoved(ResolvedAddress resolvedAddress, int connectionCount) {
            // noop
        }

        @Override
        public void hostCreated(ResolvedAddress resolvedAddress) {
            // noop
        }
    }

    public static <T> LoadBalancerObserver<T> instance() {
        return (LoadBalancerObserver<T>) INSTANCE;
    }
}
