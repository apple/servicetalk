/*
 * Copyright © 2019 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.grpc.netty;

import io.servicetalk.client.api.LoadBalancer;
import io.servicetalk.client.api.ServiceDiscoverer;
import io.servicetalk.client.api.ServiceDiscovererEvent;
import io.servicetalk.grpc.api.GrpcClientBuilder;
import io.servicetalk.http.api.HttpHeaderNames;
import io.servicetalk.http.netty.HttpClients;
import io.servicetalk.transport.api.HostAndPort;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.function.Function;

/**
 * A factory to create <a href="https://www.grpc.io">gRPC</a> clients.
 */
public final class GrpcClients {

    private GrpcClients() {
        // No instances
    }

    /**
     * Creates a {@link GrpcClientBuilder} for an address with default {@link LoadBalancer} and DNS
     * {@link ServiceDiscoverer}.
     *
     * @param host host to connect to, resolved by default using a DNS {@link ServiceDiscoverer}.
     * @param port port to connect to
     * @return new builder for the address
     */
    public static GrpcClientBuilder<HostAndPort, InetSocketAddress> forAddress(final String host, final int port) {
        return new DefaultGrpcClientBuilder<>(HttpClients.forSingleAddress(host, port));
    }

    /**
     * Creates a {@link GrpcClientBuilder} for an address with default {@link LoadBalancer} and DNS
     * {@link ServiceDiscoverer}.
     *
     * @param address the {@code UnresolvedAddress} to connect to, resolved by default using a DNS
     * {@link ServiceDiscoverer}.
     * @return new builder for the address
     */
    public static GrpcClientBuilder<HostAndPort, InetSocketAddress> forAddress(final HostAndPort address) {
        return new DefaultGrpcClientBuilder<>(HttpClients.forSingleAddress(address));
    }

    /**
     * Creates a {@link GrpcClientBuilder} for the passed {@code serviceName} with default {@link LoadBalancer} and a
     * DNS {@link ServiceDiscoverer} using <a href="https://tools.ietf.org/html/rfc2782">SRV record</a> lookups.
     *
     * @param serviceName the service name to query via <a href="https://tools.ietf.org/html/rfc2782">SRV DNS</a>.
     * @return new builder for the address
     */
    public static GrpcClientBuilder<String, InetSocketAddress> forServiceAddress(final String serviceName) {
        return new DefaultGrpcClientBuilder<>(HttpClients.forServiceAddress(serviceName));
    }

    /**
     * Creates a {@link GrpcClientBuilder} for a resolved address with default {@link LoadBalancer}.
     *
     * @param host resolved host address to connect to.
     * @param port port to connect to
     * @return new builder for the address
     */
    public static GrpcClientBuilder<HostAndPort, InetSocketAddress> forResolvedAddress(final String host,
                                                                                       final int port) {
        return new DefaultGrpcClientBuilder<>(HttpClients.forResolvedAddress(host, port));
    }

    /**
     * Creates a {@link GrpcClientBuilder} for an address with default {@link LoadBalancer}.
     *
     * @param address the {@code ResolvedAddress} to connect to.
     * @return new builder for the address
     */
    public static GrpcClientBuilder<HostAndPort, InetSocketAddress> forResolvedAddress(final HostAndPort address) {
        return new DefaultGrpcClientBuilder<>(HttpClients.forResolvedAddress(address));
    }

    /**
     * Creates a {@link GrpcClientBuilder} for an address with default {@link LoadBalancer}.
     *
     * @param address the {@code InetSocketAddress} to connect to.
     * @return new builder for the address
     */
    public static GrpcClientBuilder<InetSocketAddress, InetSocketAddress> forResolvedAddress(
            final InetSocketAddress address) {
        return new DefaultGrpcClientBuilder<>(HttpClients.forResolvedAddress(address));
    }

    /**
     * Creates a {@link GrpcClientBuilder} for an address with default {@link LoadBalancer}.
     *
     * @param address the {@code ResolvedAddress} to connect. This address will also be used for the
     * {@link HttpHeaderNames#HOST}. Use {@link GrpcClientBuilder#unresolvedAddressToHost(Function)}
     * if you want to override that value or {@link GrpcClientBuilder#hostHeaderFallback(boolean)} if you
     * want to disable this behavior.
     * @param <T> The type of {@link SocketAddress}.
     * @return new builder for the address
     */
    public static <T extends SocketAddress> GrpcClientBuilder<T, T> forResolvedAddress(final T address) {
        return new DefaultGrpcClientBuilder<>(HttpClients.forResolvedAddress(address));
    }

    /**
     * Creates a {@link GrpcClientBuilder} for a custom address type with default {@link LoadBalancer} and user
     * provided {@link ServiceDiscoverer}.
     *
     * @param serviceDiscoverer The {@link ServiceDiscoverer} to resolve addresses of remote servers to connect to.
     * The lifecycle of the provided {@link ServiceDiscoverer} should be managed by the caller.
     * @param address the {@code UnresolvedAddress} to connect to resolved using the provided {@code serviceDiscoverer}.
     * @param <U> the type of address before resolution (unresolved address)
     * @param <R> the type of address after resolution (resolved address)
     * @return new builder with provided configuration
     */
    public static <U, R>
    GrpcClientBuilder<U, R> forAddress(final ServiceDiscoverer<U, R, ServiceDiscovererEvent<R>> serviceDiscoverer,
                                       final U address) {
        return new DefaultGrpcClientBuilder<>(HttpClients.forSingleAddress(serviceDiscoverer, address));
    }
}
