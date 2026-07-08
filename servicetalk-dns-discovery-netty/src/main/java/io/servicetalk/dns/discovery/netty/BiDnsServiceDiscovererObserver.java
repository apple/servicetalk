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

import io.servicetalk.dns.discovery.netty.DnsServiceDiscovererObserver.DnsDiscoveryObserver;
import io.servicetalk.dns.discovery.netty.DnsServiceDiscovererObserver.DnsResolutionObserver;
import io.servicetalk.dns.discovery.netty.DnsServiceDiscovererObserver.ResolutionResult;

import static io.servicetalk.dns.discovery.netty.DnsServiceDiscovererObservers.asSafeObserver;

final class BiDnsServiceDiscovererObserver implements DnsServiceDiscovererObserver {

    private final DnsServiceDiscovererObserver first;
    private final DnsServiceDiscovererObserver second;

    BiDnsServiceDiscovererObserver(final DnsServiceDiscovererObserver first,
                                   final DnsServiceDiscovererObserver second) {
        this.first = asSafeObserver(first);
        this.second = asSafeObserver(second);
    }

    DnsServiceDiscovererObserver first() {
        return first;
    }

    DnsServiceDiscovererObserver second() {
        return second;
    }

    @Override
    @SuppressWarnings("deprecation")
    public DnsDiscoveryObserver onNewDiscovery(final String name) {
        return new BiDnsDiscoveryObserver(first.onNewDiscovery(name), second.onNewDiscovery(name));
    }

    @Override
    public DnsDiscoveryObserver onNewDiscovery(final String serviceDiscovererId, final String name) {
        return new BiDnsDiscoveryObserver(first.onNewDiscovery(serviceDiscovererId, name),
                second.onNewDiscovery(serviceDiscovererId, name));
    }

    private static final class BiDnsDiscoveryObserver implements DnsDiscoveryObserver {

        private final DnsDiscoveryObserver first;
        private final DnsDiscoveryObserver second;

        private BiDnsDiscoveryObserver(final DnsDiscoveryObserver first, final DnsDiscoveryObserver second) {
            this.first = first;
            this.second = second;
        }

        @Override
        public DnsResolutionObserver onNewResolution(final String name) {
            return new BiDnsResolutionObserver(first.onNewResolution(name), second.onNewResolution(name));
        }

        @Override
        public void discoveryCancelled() {
            first.discoveryCancelled();
            second.discoveryCancelled();
        }

        @Override
        public void discoveryFailed(final Throwable cause) {
            first.discoveryFailed(cause);
            second.discoveryFailed(cause);
        }
    }

    private static final class BiDnsResolutionObserver implements DnsResolutionObserver {

        private final DnsResolutionObserver first;
        private final DnsResolutionObserver second;

        private BiDnsResolutionObserver(final DnsResolutionObserver first, final DnsResolutionObserver second) {
            this.first = first;
            this.second = second;
        }

        @Override
        public void resolutionFailed(final Throwable cause) {
            first.resolutionFailed(cause);
            second.resolutionFailed(cause);
        }

        @Override
        public void resolutionCompleted(final ResolutionResult result) {
            first.resolutionCompleted(result);
            second.resolutionCompleted(result);
        }
    }
}
