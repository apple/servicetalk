/*
 * Copyright © 2026 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.dns.discovery.netty;

final class NoopDnsServiceDiscovererObserver implements DnsServiceDiscovererObserver {

    static final DnsServiceDiscovererObserver INSTANCE = new NoopDnsServiceDiscovererObserver();

    private NoopDnsServiceDiscovererObserver() {
        // Singleton
    }

    @Override
    @SuppressWarnings("deprecation")
    public DnsDiscoveryObserver onNewDiscovery(final String name) {
        return NoopDnsDiscoveryObserver.INSTANCE;
    }

    @Override
    public DnsDiscoveryObserver onNewDiscovery(final String serviceDiscovererId, final String name) {
        return NoopDnsDiscoveryObserver.INSTANCE;
    }

    static final class NoopDnsDiscoveryObserver implements DnsDiscoveryObserver {

        static final DnsDiscoveryObserver INSTANCE = new NoopDnsDiscoveryObserver();

        private NoopDnsDiscoveryObserver() {
            // Singleton
        }

        @Override
        public DnsResolutionObserver onNewResolution(final String name) {
            return NoopDnsResolutionObserver.INSTANCE;
        }

        @Override
        public void discoveryCancelled() {
            // noop
        }

        @Override
        public void discoveryFailed(final Throwable cause) {
            // noop
        }
    }

    static final class NoopDnsResolutionObserver implements DnsResolutionObserver {

        static final DnsResolutionObserver INSTANCE = new NoopDnsResolutionObserver();

        private NoopDnsResolutionObserver() {
            // Singleton
        }

        @Override
        public void resolutionFailed(final Throwable cause) {
            // noop
        }

        @Override
        public void resolutionCompleted(final ResolutionResult result) {
            // noop
        }
    }
}
