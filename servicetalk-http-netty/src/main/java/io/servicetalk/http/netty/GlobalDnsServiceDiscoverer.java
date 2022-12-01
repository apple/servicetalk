/*
 * Copyright © 2018, 2022 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.client.api.DefaultServiceDiscovererEvent;
import io.servicetalk.client.api.ServiceDiscoverer;
import io.servicetalk.client.api.ServiceDiscovererEvent;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.ListenableAsyncCloseable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.dns.discovery.netty.DefaultDnsServiceDiscovererBuilder;
import io.servicetalk.transport.api.ExecutionContext;
import io.servicetalk.transport.api.HostAndPort;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.function.Function;

import static io.servicetalk.client.api.ServiceDiscovererEvent.Status.AVAILABLE;
import static io.servicetalk.concurrent.api.AsyncCloseables.emptyAsyncCloseable;
import static io.servicetalk.concurrent.api.Publisher.never;
import static java.util.Collections.singletonList;
import static java.util.Objects.requireNonNull;

/**
 * ServiceTalk's shared DNS {@link ServiceDiscoverer} with reasonable defaults for APIs when a user doesn't provide one.
 * <p>
 * A lazily initialized singleton DNS {@link ServiceDiscoverer} using a default {@link ExecutionContext}, the lifecycle
 * of this instance shouldn't need to be managed by the user. Don't attempt to close the {@link ServiceDiscoverer}.
 */
final class GlobalDnsServiceDiscoverer {
    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalDnsServiceDiscoverer.class);

    private GlobalDnsServiceDiscoverer() {
        // No instances
    }

    /**
     * Get the {@link ServiceDiscoverer} targeting fixed ports.
     *
     * @return the singleton instance
     */
    static ServiceDiscoverer<HostAndPort, InetSocketAddress,
            ServiceDiscovererEvent<InetSocketAddress>> globalDnsServiceDiscoverer() {
        return HostAndPortClientInitializer.HOST_PORT_SD;
    }

    /**
     * Get the {@link ServiceDiscoverer} targeting SRV records.
     *
     * @return the singleton instance
     */
    static ServiceDiscoverer<String, InetSocketAddress,
            ServiceDiscovererEvent<InetSocketAddress>> globalSrvDnsServiceDiscoverer() {
        return SrvClientInitializer.SRV_SD;
    }

    /**
     * Get the {@link ServiceDiscoverer} that uses the passed function to transform an unresolved to resolved address.
     *
     * @param toResolvedAddressMapper {@link Function} to transform an unresolved to resolved address
     * @param <U> the type of address before resolution (unresolved address)
     * @param <R> the type of address after resolution (resolved address)
     * @return {@link ServiceDiscoverer} that uses the passed function to transform an unresolved to resolved address
     */
    static <U, R> ServiceDiscoverer<U, R, ServiceDiscovererEvent<R>> mappingServiceDiscoverer(
            final Function<U, R> toResolvedAddressMapper) {
        return new MappingServiceDiscoverer<>(toResolvedAddressMapper);
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

    static final class MappingServiceDiscoverer<UnresolvedAddress, ResolvedAddress>
            implements ServiceDiscoverer<UnresolvedAddress, ResolvedAddress, ServiceDiscovererEvent<ResolvedAddress>> {

        private final Function<UnresolvedAddress, ResolvedAddress> toResolvedAddressMapper;
        private final ListenableAsyncCloseable closeable = emptyAsyncCloseable();

        private MappingServiceDiscoverer(final Function<UnresolvedAddress, ResolvedAddress> toResolvedAddressMapper) {
            this.toResolvedAddressMapper = requireNonNull(toResolvedAddressMapper);
        }

        @Override
        public Publisher<Collection<ServiceDiscovererEvent<ResolvedAddress>>> discover(
                final UnresolvedAddress address) {
            return Publisher.<Collection<ServiceDiscovererEvent<ResolvedAddress>>>from(
                            singletonList(new DefaultServiceDiscovererEvent<>(
                                    requireNonNull(toResolvedAddressMapper.apply(address)), AVAILABLE)))
                    // LoadBalancer will flag a termination of service discoverer Publisher as unexpected.
                    .concat(never());
        }

        @Override
        public Completable onClose() {
            return closeable.onClose();
        }

        @Override
        public Completable onClosing() {
            return closeable.onClosing();
        }

        @Override
        public Completable closeAsync() {
            return closeable.closeAsync();
        }

        @Override
        public Completable closeAsyncGracefully() {
            return closeable.closeAsyncGracefully();
        }
    }
}
