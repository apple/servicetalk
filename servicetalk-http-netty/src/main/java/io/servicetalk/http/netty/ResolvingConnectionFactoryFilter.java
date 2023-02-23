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
package io.servicetalk.http.netty;

import io.servicetalk.client.api.ConnectionFactory;
import io.servicetalk.client.api.ConnectionFactoryFilter;
import io.servicetalk.client.api.DelegatingConnectionFactory;
import io.servicetalk.client.api.ServiceDiscoverer;
import io.servicetalk.client.api.ServiceDiscovererEvent;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.context.api.ContextMap;
import io.servicetalk.http.api.FilterableStreamingHttpConnection;
import io.servicetalk.transport.api.ConnectExecutionStrategy;
import io.servicetalk.transport.api.ExecutionStrategy;
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.api.TransportObserver;

import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;

import static io.servicetalk.client.api.ServiceDiscovererEvent.Status.AVAILABLE;
import static io.servicetalk.http.netty.GlobalDnsServiceDiscoverer.globalDnsServiceDiscoverer;

/**
 * A {@link ConnectionFactoryFilter} that will resolve the passed unresolved {@link InetSocketAddress} on each attempt
 * to create a {@link ConnectionFactory#newConnection(Object, ContextMap, TransportObserver) new connection} using
 * {@link GlobalDnsServiceDiscoverer#globalDnsServiceDiscoverer()}.
 */
final class ResolvingConnectionFactoryFilter
        implements ConnectionFactoryFilter<InetSocketAddress, FilterableStreamingHttpConnection> {

    static final ConnectionFactoryFilter<InetSocketAddress, FilterableStreamingHttpConnection> INSTANCE =
            new ResolvingConnectionFactoryFilter();

    private final ServiceDiscoverer<HostAndPort, InetSocketAddress, ServiceDiscovererEvent<InetSocketAddress>> sd =
            globalDnsServiceDiscoverer();

    private ResolvingConnectionFactoryFilter() {
        // Singleton
    }

    @Override
    public ConnectionFactory<InetSocketAddress, FilterableStreamingHttpConnection> create(
            final ConnectionFactory<InetSocketAddress, FilterableStreamingHttpConnection> original) {
        return new DelegatingConnectionFactory<InetSocketAddress, FilterableStreamingHttpConnection>(original) {

            @Override
            public Single<FilterableStreamingHttpConnection> newConnection(final InetSocketAddress address,
                                                                           @Nullable final ContextMap context,
                                                                           @Nullable final TransportObserver observer) {
                assert address.isUnresolved();
                return sd.discover(HostAndPort.of(address)).takeAtMost(1).firstOrError()
                        .flatMap(resolvedAddresses -> {
                            if (resolvedAddresses.size() > 1) {
                                // In case DNS server returns multiple IPs, it's recommended to shuffle the result to
                                // make sure the client balances load between all available IPs.
                                List<ServiceDiscovererEvent<InetSocketAddress>> list =
                                        resolvedAddresses instanceof List ?
                                                (List<ServiceDiscovererEvent<InetSocketAddress>>) resolvedAddresses :
                                                new ArrayList<>(resolvedAddresses);
                                Collections.shuffle(list);
                                resolvedAddresses = list;
                            }
                            @Nullable
                            ServiceDiscovererEvent<InetSocketAddress> resolved = resolvedAddresses.stream()
                                    .filter(event -> event.status() == AVAILABLE).findFirst().orElse(null);
                            if (resolved == null) {
                                return Single.<FilterableStreamingHttpConnection>failed(
                                        new UnknownHostException(sd + " didn't return any available record for " +
                                                address.getHostString()))
                                        .shareContextOnSubscribe();
                            }
                            return delegate().newConnection(resolved.address(), context, observer)
                                    .shareContextOnSubscribe();
                        });
            }
        };
    }

    @Override
    public ExecutionStrategy requiredOffloads() {
        return ConnectExecutionStrategy.offloadNone();
    }
}
