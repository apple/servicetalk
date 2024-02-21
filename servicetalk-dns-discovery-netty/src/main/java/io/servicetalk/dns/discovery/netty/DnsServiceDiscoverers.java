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
package io.servicetalk.dns.discovery.netty;

import io.servicetalk.client.api.ServiceDiscoverer;
import io.servicetalk.client.api.ServiceDiscovererEvent;
import io.servicetalk.transport.api.HostAndPort;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.List;

import static io.servicetalk.utils.internal.ServiceLoaderUtils.loadProviders;

/**
 * A factory to create DNS {@link io.servicetalk.client.api.ServiceDiscoverer ServiceDiscoverers}.
 */
public final class DnsServiceDiscoverers {

    private static final Logger LOGGER = LoggerFactory.getLogger(DnsServiceDiscoverers.class);
    private static final List<DnsServiceDiscovererBuilderProvider> PROVIDERS;
    private static final String GLOBAL_A_ID = "global-a";
    private static final String GLOBAL_SRV_ID = "global-srv";

    static {
        final ClassLoader classLoader = DnsServiceDiscoverers.class.getClassLoader();
        PROVIDERS = loadProviders(DnsServiceDiscovererBuilderProvider.class, classLoader, LOGGER);
    }

    private DnsServiceDiscoverers() {
        // No instances.
    }

    private static DnsServiceDiscovererBuilder applyProviders(String id, DnsServiceDiscovererBuilder builder) {
        for (DnsServiceDiscovererBuilderProvider provider : PROVIDERS) {
            builder = provider.newBuilder(id, builder);
        }
        return builder;
    }

    /**
     * A new {@link DnsServiceDiscovererBuilder} instance.
     * <p>
     * The returned builder can be customized using {@link DnsServiceDiscovererBuilderProvider}.
     *
     * @param id a (unique) ID to identify the created {@link ServiceDiscoverer}.
     * @return a new {@link DnsServiceDiscovererBuilder}.
     */
    @SuppressWarnings("deprecation")
    public static DnsServiceDiscovererBuilder builder(final String id) {
        LOGGER.debug("Created a new {} for id={}", DnsServiceDiscovererBuilder.class.getSimpleName(), id);
        return applyProviders(id, new DefaultDnsServiceDiscovererBuilder(id));
    }

    /**
     * Get the {@link ServiceDiscoverer} that executes <a href="https://tools.ietf.org/html/rfc1035">DNS</a> lookups for
     * <a href="https://datatracker.ietf.org/doc/html/rfc1035#autoid-34">A</a>/
     * <a href="https://datatracker.ietf.org/doc/html/rfc3596">AAAA</a> records for provided
     * {@link HostAndPort#hostName()} with a fixed {@link HostAndPort#port()}.
     * <p>
     * The returned instance can be customized using {@link DnsServiceDiscovererBuilderProvider} by targeting
     * {@value GLOBAL_A_ID} identity.
     * <p>
     * The lifecycle of this instance shouldn't need to be managed by the user. The returned instance of
     * {@link ServiceDiscoverer} must not be closed.
     *
     * @return the singleton instance
     */
    public static ServiceDiscoverer<HostAndPort, InetSocketAddress,
            ServiceDiscovererEvent<InetSocketAddress>> globalARecordsDnsServiceDiscoverer() {
        return ARecordsDnsServiceDiscoverer.INSTANCE;
    }

    /**
     * Get the {@link ServiceDiscoverer} that executes <a href="https://tools.ietf.org/html/rfc1035">DNS</a> lookups for
     * <a href="https://datatracker.ietf.org/doc/html/rfc2782">SRV</a> records for provided service name as a
     * {@link String}.
     * <p>
     * The returned instance can be customized using {@link DnsServiceDiscovererBuilderProvider} by targeting
     * {@value GLOBAL_SRV_ID} identity.
     * <p>
     * The lifecycle of this instance shouldn't need to be managed by the user. The returned instance of
     * {@link ServiceDiscoverer} must not be closed.
     *
     * @return the singleton instance
     */
    public static ServiceDiscoverer<String, InetSocketAddress,
            ServiceDiscovererEvent<InetSocketAddress>> globalSrvRecordsDnsServiceDiscoverer() {
        return SrvRecordsDnsServiceDiscoverer.INSTANCE;
    }

    private static final class ARecordsDnsServiceDiscoverer {
        static final ServiceDiscoverer<HostAndPort, InetSocketAddress, ServiceDiscovererEvent<InetSocketAddress>>
                INSTANCE = DnsServiceDiscoverers.builder(GLOBAL_A_ID).buildARecordDiscoverer();

        static {
            LOGGER.debug("Initialized {}: {}", ARecordsDnsServiceDiscoverer.class.getSimpleName(), INSTANCE);
        }

        private ARecordsDnsServiceDiscoverer() {
            // Singleton
        }
    }

    private static final class SrvRecordsDnsServiceDiscoverer {
        static final ServiceDiscoverer<String, InetSocketAddress, ServiceDiscovererEvent<InetSocketAddress>>
                INSTANCE = DnsServiceDiscoverers.builder(GLOBAL_SRV_ID).buildSrvDiscoverer();

        static {
            LOGGER.debug("Initialized {}: {}", SrvRecordsDnsServiceDiscoverer.class.getSimpleName(), INSTANCE);
        }

        private SrvRecordsDnsServiceDiscoverer() {
            // Singleton
        }
    }
}
