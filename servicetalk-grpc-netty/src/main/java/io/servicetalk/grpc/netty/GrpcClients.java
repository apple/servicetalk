/*
 * Copyright © 2019, 2022 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.grpc.api.GrpcProviders.GrpcClientBuilderProvider;
import io.servicetalk.http.api.HttpHeaderNames;
import io.servicetalk.http.netty.HttpClients;
import io.servicetalk.transport.api.HostAndPort;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.List;
import java.util.function.Function;

import static io.servicetalk.utils.internal.ServiceLoaderUtils.loadProviders;

/**
 * A factory to create <a href="https://www.grpc.io">gRPC</a> clients.
 */
public final class GrpcClients {

    private static final Logger LOGGER = LoggerFactory.getLogger(GrpcClients.class);

    private static final List<GrpcClientBuilderProvider> PROVIDERS;

    private GrpcClients() {
        // No instances
    }

    static {
        final ClassLoader classLoader = GrpcClients.class.getClassLoader();
        PROVIDERS = loadProviders(GrpcClientBuilderProvider.class, classLoader, LOGGER);
    }

    private static <U, R> GrpcClientBuilder<U, R> applyProviders(
            final U address, GrpcClientBuilder<U, R> builder) {
        for (GrpcClientBuilderProvider provider : PROVIDERS) {
            builder = provider.newBuilder(address, builder);
        }
        return builder;
    }

    /**
     * Creates a {@link GrpcClientBuilder} for an address with default {@link LoadBalancer} and DNS
     * {@link ServiceDiscoverer}.
     * <p>
     * The returned builder can be customized using {@link GrpcClientBuilderProvider}.
     *
     * @param host host to connect to, resolved by default using a DNS {@link ServiceDiscoverer}.
     * @param port port to connect to
     * @return new builder for the address
     * @see GrpcClientBuilderProvider
     */
    public static GrpcClientBuilder<HostAndPort, InetSocketAddress> forAddress(final String host, final int port) {
        return forAddress(HostAndPort.of(host, port));
    }

    /**
     * Creates a {@link GrpcClientBuilder} for an address with default {@link LoadBalancer} and DNS
     * {@link ServiceDiscoverer}.
     * <p>
     * The returned builder can be customized using {@link GrpcClientBuilderProvider}.
     *
     * @param address the {@code UnresolvedAddress} to connect to, resolved by default using a DNS
     * {@link ServiceDiscoverer}.
     * @return new builder for the address
     * @see GrpcClientBuilderProvider
     */
    public static GrpcClientBuilder<HostAndPort, InetSocketAddress> forAddress(final HostAndPort address) {
        return applyProviders(address, new DefaultGrpcClientBuilder<>(() -> HttpClients.forSingleAddress(address)));
    }

    /**
     * Creates a {@link GrpcClientBuilder} for the passed {@code serviceName} with default {@link LoadBalancer} and a
     * DNS {@link ServiceDiscoverer} using <a href="https://tools.ietf.org/html/rfc2782">SRV record</a> lookups.
     * <p>
     * The returned builder can be customized using {@link GrpcClientBuilderProvider}.
     *
     * @param serviceName the service name to query via <a href="https://tools.ietf.org/html/rfc2782">SRV DNS</a>.
     * @return new builder for the address
     * @see GrpcClientBuilderProvider
     */
    public static GrpcClientBuilder<String, InetSocketAddress> forServiceAddress(final String serviceName) {
        return applyProviders(serviceName,
                new DefaultGrpcClientBuilder<>(() -> HttpClients.forServiceAddress(serviceName)));
    }

    /**
     * Creates a {@link GrpcClientBuilder} for a resolved address with default {@link LoadBalancer}.
     * <p>
     * The returned builder can be customized using {@link GrpcClientBuilderProvider}.
     *
     * @param host resolved host address to connect to.
     * @param port port to connect to
     * @return new builder for the address
     * @see GrpcClientBuilderProvider
     */
    public static GrpcClientBuilder<HostAndPort, InetSocketAddress> forResolvedAddress(final String host,
                                                                                       final int port) {
        return forResolvedAddress(HostAndPort.of(host, port));
    }

    /**
     * Creates a {@link GrpcClientBuilder} for an address with default {@link LoadBalancer}.
     * <p>
     * The returned builder can be customized using {@link GrpcClientBuilderProvider}.
     *
     * @param address the {@code ResolvedAddress} to connect to.
     * @return new builder for the address
     * @see GrpcClientBuilderProvider
     */
    public static GrpcClientBuilder<HostAndPort, InetSocketAddress> forResolvedAddress(final HostAndPort address) {
        return applyProviders(address, new DefaultGrpcClientBuilder<>(() -> HttpClients.forResolvedAddress(address)));
    }

    /**
     * Creates a {@link GrpcClientBuilder} for an address with default {@link LoadBalancer}.
     * <p>
     * The returned builder can be customized using {@link GrpcClientBuilderProvider}.
     *
     * @param address the {@code InetSocketAddress} to connect to.
     * @return new builder for the address
     * @see GrpcClientBuilderProvider
     */
    public static GrpcClientBuilder<InetSocketAddress, InetSocketAddress> forResolvedAddress(
            final InetSocketAddress address) {
        return applyProviders(address, new DefaultGrpcClientBuilder<>(() -> HttpClients.forResolvedAddress(address)));
    }

    /**
     * Creates a {@link GrpcClientBuilder} for an address with default {@link LoadBalancer}.
     * <p>
     * The returned builder can be customized using {@link GrpcClientBuilderProvider}.
     *
     * @param address the {@code ResolvedAddress} to connect. This address will also be used for the
     * {@link HttpHeaderNames#HOST}.
     * Use {@link io.servicetalk.http.api.SingleAddressHttpClientBuilder#unresolvedAddressToHost(Function)}
     * via {@link GrpcClientBuilder#initializeHttp(GrpcClientBuilder.HttpInitializer)}
     * if you want to override that value or
     * {@link io.servicetalk.http.api.SingleAddressHttpClientBuilder#hostHeaderFallback(boolean)}
     * if you want to disable this behavior.
     * @param <T> The type of {@link SocketAddress}.
     * @return new builder for the address
     * @see GrpcClientBuilderProvider
     */
    public static <T extends SocketAddress> GrpcClientBuilder<T, T> forResolvedAddress(final T address) {
        return applyProviders(address, new DefaultGrpcClientBuilder<>(() -> HttpClients.forResolvedAddress(address)));
    }

    /**
     * Creates a {@link GrpcClientBuilder} for a custom address type with default {@link LoadBalancer} and user
     * provided {@link ServiceDiscoverer}.
     * <p>
     * The returned builder can be customized using {@link GrpcClientBuilderProvider}.
     *
     * @param serviceDiscoverer The {@link ServiceDiscoverer} to resolve addresses of remote servers to connect to.
     * The lifecycle of the provided {@link ServiceDiscoverer} should be managed by the caller.
     * @param address the {@code UnresolvedAddress} to connect to resolved using the provided {@code serviceDiscoverer}.
     * @param <U> the type of address before resolution (unresolved address)
     * @param <R> the type of address after resolution (resolved address)
     * @return new builder with provided configuration
     * @see GrpcClientBuilderProvider
     */
    public static <U, R>
    GrpcClientBuilder<U, R> forAddress(
            final ServiceDiscoverer<U, R, ? extends ServiceDiscovererEvent<R>> serviceDiscoverer, final U address) {
        return applyProviders(address,
                new DefaultGrpcClientBuilder<>(() -> HttpClients.forSingleAddress(serviceDiscoverer, address)));
    }
}
