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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static io.servicetalk.utils.internal.ServiceLoaderUtils.loadProviders;

/**
 * A factory to create DNS {@link io.servicetalk.client.api.ServiceDiscoverer ServiceDiscoverers}.
 */
public final class DnsServiceDiscoverers {

    private static final Logger LOGGER = LoggerFactory.getLogger(DnsServiceDiscoverers.class);

    private static final List<DnsServiceDiscovererBuilderProvider> PROVIDERS;

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
     * @param id a (unique) ID to identify the created {@link DnsClient}.
     * @return a new {@link DnsServiceDiscovererBuilder}.
     */
    @SuppressWarnings("deprecation")
    public static DnsServiceDiscovererBuilder builder(final String id) {
        return applyProviders(id, new DefaultDnsServiceDiscovererBuilder(id));
    }
}
