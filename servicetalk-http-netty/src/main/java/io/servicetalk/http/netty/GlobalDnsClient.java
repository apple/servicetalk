/*
 * Copyright © 2018 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.http.netty;

import io.servicetalk.client.api.ServiceDiscoverer;
import io.servicetalk.client.api.ServiceDiscovererEvent;
import io.servicetalk.dns.discovery.netty.DefaultDnsServiceDiscovererBuilder;
import io.servicetalk.transport.api.ExecutionContext;
import io.servicetalk.transport.api.HostAndPort;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;

/**
 * ServiceTalk's shared DNS {@link ServiceDiscoverer} with reasonable defaults for APIs when a user doesn't provide one.
 * <p>
 * A lazily initialized singleton DNS {@link ServiceDiscoverer} using a default {@link ExecutionContext}, the lifecycle
 * of this instance shouldn't need to be managed by the user. Don't attempt to close the {@link ServiceDiscoverer}.
 */
final class GlobalDnsClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalDnsClient.class);

    private GlobalDnsClient() {
        // No instances
    }

    /**
     * Get the {@link ServiceDiscoverer} targeting fixed ports.
     *
     * @return the singleton instance
     */
    static ServiceDiscoverer<HostAndPort, InetSocketAddress,
            ServiceDiscovererEvent<InetSocketAddress>> globalDnsWithFixedPort() {
        return HostAndPortClientInitializer.HOST_PORT_SD;
    }

    /**
     * Get the {@link ServiceDiscoverer} targeting SRV records.
     *
     * @return the singleton instance
     */
    static ServiceDiscoverer<String, InetSocketAddress,
            ServiceDiscovererEvent<InetSocketAddress>> globalDnsSrv() {
        return SrvClientInitializer.SRV_SD;
    }

    private static final class HostAndPortClientInitializer {
        static final ServiceDiscoverer<HostAndPort, InetSocketAddress, ServiceDiscovererEvent<InetSocketAddress>>
                HOST_PORT_SD = new DefaultDnsServiceDiscovererBuilder().buildARecordDiscoverer();

        static {
            LOGGER.debug("Initialized HostAndPortClientInitializer");
        }

        private HostAndPortClientInitializer() {
            // No instances
        }
    }

    private static final class SrvClientInitializer {
        static final ServiceDiscoverer<String, InetSocketAddress, ServiceDiscovererEvent<InetSocketAddress>> SRV_SD =
                new DefaultDnsServiceDiscovererBuilder().buildSrvDiscoverer();

        static {
            LOGGER.debug("Initialized SrvClientInitializer");
        }

        private SrvClientInitializer() {
            // No instances
        }
    }
}
