/*
 * Copyright © 2020 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.client.api.DefaultServiceDiscovererEvent;
import io.servicetalk.client.api.ServiceDiscoverer;
import io.servicetalk.client.api.ServiceDiscovererEvent;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.transport.api.HostAndPort;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

/**
 * Utilities methods and conversion routines for {@link DnsClient}.
 */
final class DnsClients {
    private DnsClients() {
        // no instances
    }

    /**
     * Convert this object to a {@link ServiceDiscoverer} that queries
     * <a href="https://tools.ietf.org/html/rfc2782">SRV Resource Records</a>.
     *
     * @param dns The {@link DnsClient} used for the underlying {@link DnsClient#dnsQuery(String)}
     * queries.
     * @return a {@link ServiceDiscoverer} that queries
     * <a href="https://tools.ietf.org/html/rfc2782">SRV Resource Records</a>
     */
    static ServiceDiscoverer<String, InetSocketAddress, ServiceDiscovererEvent<InetSocketAddress>>
    asSrvDiscoverer(DnsClient dns) {
        return new ServiceDiscoverer<String, InetSocketAddress, ServiceDiscovererEvent<InetSocketAddress>>() {
            @Override
            public Completable closeAsync() {
                return dns.closeAsync();
            }

            @Override
            public Completable closeAsyncGracefully() {
                return dns.closeAsyncGracefully();
            }

            @Override
            public Completable onClose() {
                return dns.onClose();
            }

            @Override
            public Publisher<Collection<ServiceDiscovererEvent<InetSocketAddress>>> discover(final String s) {
                return dns.dnsSrvQuery(s);
            }
        };
    }

    /**
     * Convert from a {@link DnsClient} to a {@link ServiceDiscoverer} that does fixed port host name
     * resolutions.
     *
     * @param dns The {@link DnsClient} used for the underlying {@link DnsClient#dnsQuery(String)}
     * queries.
     * @return a {@link ServiceDiscoverer} which will convert from {@link String} host names and {@link InetAddress}
     * resolved address to {@link HostAndPort} to {@link InetSocketAddress}.
     */
    static ServiceDiscoverer<HostAndPort, InetSocketAddress, ServiceDiscovererEvent<InetSocketAddress>>
    asHostAndPortDiscoverer(DnsClient dns) {
        return new ServiceDiscoverer<HostAndPort, InetSocketAddress, ServiceDiscovererEvent<InetSocketAddress>>() {
            @Override
            public Completable closeAsync() {
                return dns.closeAsync();
            }

            @Override
            public Completable closeAsyncGracefully() {
                return dns.closeAsyncGracefully();
            }

            @Override
            public Completable onClose() {
                return dns.onClose();
            }

            @Override
            public Publisher<Collection<ServiceDiscovererEvent<InetSocketAddress>>> discover(
                    final HostAndPort hostAndPort) {
                return dns.dnsQuery(hostAndPort.hostName())
                        .map(events -> mapEventList(events,
                                inetAddress -> new InetSocketAddress(inetAddress, hostAndPort.port())));
            }
        };
    }

    static <T, R> List<ServiceDiscovererEvent<R>> mapEventList(final Collection<ServiceDiscovererEvent<T>> original,
                                                               final Function<T, R> mapper) {
        List<ServiceDiscovererEvent<R>> result = new ArrayList<>(original.size());
        for (ServiceDiscovererEvent<T> evt : original) {
            result.add(new DefaultServiceDiscovererEvent<>(mapper.apply(evt.address()), evt.isAvailable()));
        }
        return result;
    }
}
