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
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

import static io.servicetalk.client.api.ServiceDiscovererEvent.Status.AVAILABLE;
import static io.servicetalk.http.netty.GlobalDnsServiceDiscoverer.globalDnsServiceDiscoverer;
import static java.util.Objects.requireNonNull;

/**
 * A {@link ConnectionFactoryFilter} that will resolve the passed unresolved {@link InetSocketAddress} on each attempt
 * to create a {@link ConnectionFactory#newConnection(Object, ContextMap, TransportObserver) new connection} using
 * {@link GlobalDnsServiceDiscoverer#globalDnsServiceDiscoverer()}.
 *
 * @param <U> the type of address before resolution (unresolved address)
 * @param <R> the type of address after resolution (resolved address)
 */
final class ResolvingConnectionFactoryFilter<U, R>
        implements ConnectionFactoryFilter<R, FilterableStreamingHttpConnection> {

    private final Function<R, U> toUnresolvedAddressMapper;
    private final ServiceDiscoverer<U, R, ? extends ServiceDiscovererEvent<R>> serviceDiscoverer;

    ResolvingConnectionFactoryFilter(
            final Function<R, U> toUnresolvedAddressMapper,
            final ServiceDiscoverer<U, R, ? extends ServiceDiscovererEvent<R>> serviceDiscoverer) {
        this.toUnresolvedAddressMapper = requireNonNull(toUnresolvedAddressMapper);
        this.serviceDiscoverer = requireNonNull(serviceDiscoverer);
    }

    @Override
    public ConnectionFactory<R, FilterableStreamingHttpConnection> create(
            final ConnectionFactory<R, FilterableStreamingHttpConnection> original) {
        return new DelegatingConnectionFactory<R, FilterableStreamingHttpConnection>(original) {

            @Override
            @SuppressWarnings("unchecked")
            public Single<FilterableStreamingHttpConnection> newConnection(final R address,
                                           @Nullable final ContextMap context,
                                           @Nullable final TransportObserver observer) {
                final U unresolvedAddress = toUnresolvedAddressMapper.apply(address);
                return serviceDiscoverer.discover(unresolvedAddress).takeAtMost(1).firstOrError()
                        .flatMap(resolvedAddresses -> {
                            @Nullable
                            ServiceDiscovererEvent<R> resolved;
                            if (resolvedAddresses.isEmpty()) {
                                resolved = null;
                            } else if (resolvedAddresses.size() == 1) {
                                resolved = resolvedAddresses instanceof List ?
                                        ((List<ServiceDiscovererEvent<R>>) resolvedAddresses).get(0) :
                                        resolvedAddresses.stream().findFirst().orElse(null);
                                if (!AVAILABLE.equals(resolved.status())) {
                                    resolved = null;
                                }
                            } else {
                                // In case DNS server returns multiple IPs, it's recommended to pick a random one to
                                // make sure the client balances load between all available IPs.
                                final List<ServiceDiscovererEvent<R>> list = resolvedAddresses.stream()
                                        .filter(event -> AVAILABLE.equals(event.status()))
                                        .collect(Collectors.toList());
                                resolved = list.isEmpty() ? null :
                                        list.get(ThreadLocalRandom.current().nextInt(0, list.size()));
                            }
                            return (resolved == null ? unknownHostException(unresolvedAddress, resolvedAddresses) :
                                    delegate().newConnection(resolved.address(), context, observer))
                                    .shareContextOnSubscribe();
                        });
            }
        };
    }

    private Single<FilterableStreamingHttpConnection> unknownHostException(
            final U unresolvedAddress, final Collection<? extends ServiceDiscovererEvent<R>> resolvedAddresses) {
        return Single.<FilterableStreamingHttpConnection>failed(
                new UnknownHostException(serviceDiscoverer + " didn't return any available record for "
                        + unresolvedAddress + ", resolved addresses: " + resolvedAddresses));
    }

    @Override
    public ExecutionStrategy requiredOffloads() {
        return ConnectExecutionStrategy.offloadNone();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() +
                "{toUnresolvedAddressMapper=" + toUnresolvedAddressMapper +
                ", serviceDiscoverer=" + serviceDiscoverer +
                '}';
    }

    static ResolvingConnectionFactoryFilter<HostAndPort, InetSocketAddress> withGlobalDnsServiceDiscoverer() {
        return DefaultResolvingConnectionFactoryFilterInitializer.INSTANCE;
    }

    private static final class DefaultResolvingConnectionFactoryFilterInitializer {

        static final ResolvingConnectionFactoryFilter<HostAndPort, InetSocketAddress> INSTANCE =
                new ResolvingConnectionFactoryFilter<>(HostAndPort::of, globalDnsServiceDiscoverer());

        private DefaultResolvingConnectionFactoryFilterInitializer() {
            // Singleton
        }
    }
}
