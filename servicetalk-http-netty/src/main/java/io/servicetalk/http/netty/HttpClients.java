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

import io.servicetalk.client.api.LoadBalancer;
import io.servicetalk.client.api.ServiceDiscoverer;
import io.servicetalk.client.api.ServiceDiscovererEvent;
import io.servicetalk.client.api.partition.PartitionAttributes;
import io.servicetalk.client.api.partition.PartitionAttributesBuilder;
import io.servicetalk.client.api.partition.PartitionedServiceDiscovererEvent;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.ListenableAsyncCloseable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.http.api.HttpClient;
import io.servicetalk.http.api.HttpHeaderNames;
import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.http.api.MultiAddressHttpClientBuilder;
import io.servicetalk.http.api.PartitionedHttpClientBuilder;
import io.servicetalk.http.api.SingleAddressHttpClientBuilder;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.transport.api.HostAndPort;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Collection;
import java.util.function.Function;

import static io.servicetalk.concurrent.api.AsyncCloseables.emptyAsyncCloseable;
import static io.servicetalk.concurrent.api.Publisher.failed;
import static io.servicetalk.http.netty.DefaultSingleAddressHttpClientBuilder.forUnknownHostAndPort;
import static io.servicetalk.transport.netty.internal.BuilderUtils.toResolvedInetSocketAddress;

/**
 * Factory methods for building {@link HttpClient} (and other API variations) instances.
 */
public final class HttpClients {

    private HttpClients() {
        // No instances
    }

    /**
     * Creates a {@link MultiAddressHttpClientBuilder} for clients capable of parsing an <a
     * href="https://tools.ietf.org/html/rfc7230#section-5.3.2">absolute-form URL</a>, connecting to multiple addresses
     * with default {@link LoadBalancer} and DNS {@link ServiceDiscoverer}.
     * <p>
     * When a <a href="https://tools.ietf.org/html/rfc3986#section-4.2">relative URL</a> is passed in the {@link
     * StreamingHttpRequest#requestTarget(String)} this client requires a {@link HttpHeaderNames#HOST} present in
     * order to infer the remote address.
     *
     * @return new builder with default configuration
     */
    public static MultiAddressHttpClientBuilder<HostAndPort, InetSocketAddress> forMultiAddressUrl() {
        return new DefaultMultiAddressUrlHttpClientBuilder(forUnknownHostAndPort());
    }

    /**
     * Creates a {@link MultiAddressHttpClientBuilder} for clients capable of parsing an <a
     * href="https://tools.ietf.org/html/rfc7230#section-5.3.2">absolute-form URL</a>, connecting to multiple addresses
     * with default {@link LoadBalancer} and user provided {@link ServiceDiscoverer}.
     * <p>
     * When a <a href="https://tools.ietf.org/html/rfc3986#section-4.2">relative URL</a> is passed in the {@link
     * StreamingHttpRequest#requestTarget(String)} this client requires a {@link HttpHeaderNames#HOST} present in
     * order to infer the remote address.
     *
     * @param serviceDiscoverer The {@link ServiceDiscoverer} to resolve addresses of remote servers to connect to.
     * The lifecycle of the provided {@link ServiceDiscoverer} should be managed by the caller.
     * @return new builder with default configuration
     */
    public static MultiAddressHttpClientBuilder<HostAndPort, InetSocketAddress> forMultiAddressUrl(
            final ServiceDiscoverer<HostAndPort, InetSocketAddress, ServiceDiscovererEvent<InetSocketAddress>>
                    serviceDiscoverer) {
        return new DefaultMultiAddressUrlHttpClientBuilder(
                new DefaultSingleAddressHttpClientBuilder<>(serviceDiscoverer));
    }

    /**
     * Creates a {@link SingleAddressHttpClientBuilder} for an address with default {@link LoadBalancer} and DNS {@link
     * ServiceDiscoverer}.
     *
     * @param host host to connect to, resolved by default using a DNS {@link ServiceDiscoverer}. This will also be
     * used for the {@link HttpHeaderNames#HOST} together with the {@code port}. Use
     * {@link SingleAddressHttpClientBuilder#unresolvedAddressToHost(Function)} if you want to override that value
     * or {@link SingleAddressHttpClientBuilder#disableHostHeaderFallback()} if you want to disable this behavior.
     * @param port port to connect to
     * @return new builder for the address
     */
    public static SingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> forSingleAddress(
            final String host, final int port) {
        return forSingleAddress(HostAndPort.of(host, port));
    }

    /**
     * Creates a {@link SingleAddressHttpClientBuilder} for an address via a proxy, with default {@link LoadBalancer}
     * and DNS {@link ServiceDiscoverer}.
     *
     * @param host host to connect to via the proxy. This will also be used for the {@link HttpHeaderNames#HOST}
     * together with the {@code port}. Use
     * {@link SingleAddressHttpClientBuilder#unresolvedAddressToHost(Function)} if you want to override that value
     * or {@link SingleAddressHttpClientBuilder#disableHostHeaderFallback()} if you want to disable this behavior.
     * @param port port to connect to
     * @param proxyHost the proxy host to connect to, resolved by default using a DNS {@link ServiceDiscoverer}.
     * @param proxyPort The proxy port to connect.
     * @return new builder for the address
     */
    public static SingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> forSingleAddressViaProxy(
            final String host, final int port, final String proxyHost, final int proxyPort) {
        return forSingleAddressViaProxy(HostAndPort.of(host, port), HostAndPort.of(proxyHost, proxyPort));
    }

    /**
     * Creates a {@link SingleAddressHttpClientBuilder} for an address with default {@link LoadBalancer} and DNS {@link
     * ServiceDiscoverer}.
     *
     * @param address the {@code UnresolvedAddress} to connect to, resolved by default using a DNS {@link
     * ServiceDiscoverer}. This address will also be used for the {@link HttpHeaderNames#HOST}.
     * Use {@link SingleAddressHttpClientBuilder#unresolvedAddressToHost(Function)} if you want to override that
     * value or {@link SingleAddressHttpClientBuilder#disableHostHeaderFallback()} if you want to disable this behavior.
     * @return new builder for the address
     */
    public static SingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> forSingleAddress(
            final HostAndPort address) {
        return DefaultSingleAddressHttpClientBuilder.forHostAndPort(address);
    }

    /**
     * Creates a {@link SingleAddressHttpClientBuilder} for the passed {@code serviceName} with default
     * {@link LoadBalancer} and a DNS {@link ServiceDiscoverer} using
     * <a href="https://tools.ietf.org/html/rfc2782">SRV record</a> lookups.
     *
     * @param serviceName The service name to resolve with <a href="https://tools.ietf.org/html/rfc2782">SRV DNS</a>.
     * @return new builder for the address
     */
    public static SingleAddressHttpClientBuilder<String, InetSocketAddress> forServiceAddress(
            final String serviceName) {
        return DefaultSingleAddressHttpClientBuilder.forServiceAddress(serviceName);
    }

    /**
     * Creates a {@link SingleAddressHttpClientBuilder} for an address via a proxy, with default {@link LoadBalancer}
     * and DNS {@link ServiceDiscoverer}.
     *
     * @param address the {@code UnresolvedAddress} to connect to via the proxy. This address will also be used for the
     * {@link HttpHeaderNames#HOST}. Use {@link SingleAddressHttpClientBuilder#unresolvedAddressToHost(Function)}
     * if you want to override that value or {@link SingleAddressHttpClientBuilder#disableHostHeaderFallback()} if you
     * want to disable this behavior.
     * @param proxyAddress the proxy {@code UnresolvedAddress} to connect to, resolved by default using a DNS {@link
     * ServiceDiscoverer}.
     * @return new builder for the address
     */
    public static SingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> forSingleAddressViaProxy(
            final HostAndPort address, final HostAndPort proxyAddress) {
        return DefaultSingleAddressHttpClientBuilder.forHostAndPortViaProxy(address, proxyAddress);
    }

    /**
     * Creates a {@link SingleAddressHttpClientBuilder} for a resolved address with default {@link LoadBalancer}.
     *
     * @param host resolved host address to connect. This will also be used for the {@link HttpHeaderNames#HOST}
     * together with the {@code port}. Use {@link SingleAddressHttpClientBuilder#unresolvedAddressToHost(Function)}
     * if you want to override that value or {@link SingleAddressHttpClientBuilder#disableHostHeaderFallback()}
     * if you want to disable this behavior.
     * @param port port to connect to
     * @return new builder for the address
     */
    public static SingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> forResolvedAddress(
            final String host, final int port) {
        return forResolvedAddress(HostAndPort.of(host, port));
    }

    /**
     * Creates a {@link SingleAddressHttpClientBuilder} for a resolved address via a proxy, with default
     * {@link LoadBalancer}.
     *
     * @param host resolved host address to connect via the proxy. This will also be used for the
     * {@link HttpHeaderNames#HOST} together with the {@code port}. Use
     * {@link SingleAddressHttpClientBuilder#unresolvedAddressToHost(Function)} if you want to override that value
     * or {@link SingleAddressHttpClientBuilder#disableHostHeaderFallback()} if you want to disable this behavior.
     * @param port port to connect to via the proxy
     * @param proxyHost The proxy resolved host address to connect.
     * @param proxyPort The proxy port to connect.
     * @return new builder for the address
     */
    public static SingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> forResolvedAddressViaProxy(
            final String host, final int port, final String proxyHost, final int proxyPort) {
        return forResolvedAddressViaProxy(HostAndPort.of(host, port), HostAndPort.of(proxyHost, proxyPort));
    }

    /**
     * Creates a {@link SingleAddressHttpClientBuilder} for an address with default {@link LoadBalancer}.
     *
     * @param address the {@code ResolvedAddress} to connect. This address will also be used for the
     * {@link HttpHeaderNames#HOST}. Use {@link SingleAddressHttpClientBuilder#unresolvedAddressToHost(Function)}
     * if you want to override that value or {@link SingleAddressHttpClientBuilder#disableHostHeaderFallback()} if you
     * want to disable this behavior.
     * @return new builder for the address
     */
    public static SingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> forResolvedAddress(
            final HostAndPort address) {
        return DefaultSingleAddressHttpClientBuilder.forResolvedAddress(address, toResolvedInetSocketAddress(address));
    }

    /**
     * Creates a {@link SingleAddressHttpClientBuilder} for an address via a proxy, with default {@link LoadBalancer}.
     *
     * @param address the {@code ResolvedAddress} to connect to via the proxy. This address will also be used for the
     * {@link HttpHeaderNames#HOST}. Use {@link SingleAddressHttpClientBuilder#unresolvedAddressToHost(Function)}
     * if you want to override that value or {@link SingleAddressHttpClientBuilder#disableHostHeaderFallback()} if you
     * want to disable this behavior.
     * @param proxyAddress The proxy {@code ResolvedAddress} to connect.
     * @return new builder for the address
     */
    public static SingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> forResolvedAddressViaProxy(
            final HostAndPort address, final HostAndPort proxyAddress) {
        return DefaultSingleAddressHttpClientBuilder.forResolvedAddressViaProxy(address,
                toResolvedInetSocketAddress(address), proxyAddress);
    }

    /**
     * Creates a {@link SingleAddressHttpClientBuilder} for an address with default {@link LoadBalancer}.
     *
     * @param address the {@code ResolvedAddress} to connect. This address will also be used for the
     * {@link HttpHeaderNames#HOST}. Use {@link SingleAddressHttpClientBuilder#unresolvedAddressToHost(Function)}
     * if you want to override that value or {@link SingleAddressHttpClientBuilder#disableHostHeaderFallback()} if you
     * want to disable this behavior.
     * @param <T> The type of {@link SocketAddress}.
     * @return new builder for the address
     */
    public static <T extends SocketAddress> SingleAddressHttpClientBuilder<T, T> forResolvedAddress(final T address) {
        return DefaultSingleAddressHttpClientBuilder.forResolvedAddress(address, address);
    }

    /**
     * Creates a {@link SingleAddressHttpClientBuilder} for an address via a proxy, with default {@link LoadBalancer}.
     *
     * @param address the {@code ResolvedAddress} to connect to via the proxy. This address will also be used for the
     * {@link HttpHeaderNames#HOST}. Use {@link SingleAddressHttpClientBuilder#unresolvedAddressToHost(Function)}
     * if you want to override that value or {@link SingleAddressHttpClientBuilder#disableHostHeaderFallback()} if you
     * want to disable this behavior.
     * @param proxyAddress The proxy {@code ResolvedAddress} to connect.
     * @return new builder for the address
     */
    public static SingleAddressHttpClientBuilder<InetSocketAddress, InetSocketAddress> forResolvedAddressViaProxy(
            final InetSocketAddress address, final InetSocketAddress proxyAddress) {
        return DefaultSingleAddressHttpClientBuilder.forResolvedAddressViaProxy(address, address, proxyAddress);
    }

    /**
     * Creates a {@link SingleAddressHttpClientBuilder} for a custom address type with default {@link LoadBalancer} and
     * user provided {@link ServiceDiscoverer}.
     *
     * @param serviceDiscoverer The {@link ServiceDiscoverer} to resolve addresses of remote servers to connect to.
     * The lifecycle of the provided {@link ServiceDiscoverer} should be managed by the caller.
     * @param address the {@code UnresolvedAddress} to connect to resolved using the provided {@code serviceDiscoverer}.
     * This address will also be used for the {@link HttpHeaderNames#HOST} using a best effort conversion. Use {@link
     * SingleAddressHttpClientBuilder#unresolvedAddressToHost(Function)} if you want to override that value or
     * {@link SingleAddressHttpClientBuilder#disableHostHeaderFallback()} if you want to disable this behavior.
     * @param <U> the type of address before resolution (unresolved address)
     * @param <R> the type of address after resolution (resolved address)
     * @return new builder with provided configuration
     */
    public static <U, R> SingleAddressHttpClientBuilder<U, R> forSingleAddress(
            final ServiceDiscoverer<U, R, ServiceDiscovererEvent<R>> serviceDiscoverer,
            final U address) {
        return new DefaultSingleAddressHttpClientBuilder<>(address, serviceDiscoverer);
    }

    /**
     * Creates a {@link PartitionedHttpClientBuilder} for a custom address type with default {@link LoadBalancer} and
     * user provided {@link ServiceDiscoverer}.
     *
     * @param serviceDiscoverer The {@link ServiceDiscoverer} to resolve addresses of remote servers to connect to.
     * The lifecycle of the provided {@link ServiceDiscoverer} should be managed by the caller.
     * @param address the {@code UnresolvedAddress} to resolve using the provided {@code serviceDiscoverer}.
     * This address will also be used for the {@link HttpHeaderNames#HOST} using a best effort conversion.
     * Use {@link PartitionedHttpClientBuilder#initializer(PartitionedHttpClientBuilder.SingleAddressInitializer)}
     * and {@link SingleAddressHttpClientBuilder#unresolvedAddressToHost(Function)} if you want to override that value
     * or {@link SingleAddressHttpClientBuilder#disableHostHeaderFallback()} if you want to disable this behavior.
     * @param partitionAttributesBuilderFactory The factory {@link Function} used to build {@link PartitionAttributes}
     * from {@link HttpRequestMetaData}.
     * @param <U> the type of address before resolution (unresolved address)
     * @param <R> the type of address after resolution (resolved address)
     * @return new builder with provided configuration
     */
    public static <U, R> PartitionedHttpClientBuilder<U, R> forPartitionedAddress(
            final ServiceDiscoverer<U, R, PartitionedServiceDiscovererEvent<R>> serviceDiscoverer,
            final U address,
            final Function<HttpRequestMetaData, PartitionAttributesBuilder> partitionAttributesBuilderFactory) {
        return new DefaultPartitionedHttpClientBuilder<>(
                new DefaultSingleAddressHttpClientBuilder<>(address,
                        new ServiceDiscoverer<U, R, ServiceDiscovererEvent<R>>() {
                            private final ListenableAsyncCloseable closeable = emptyAsyncCloseable();

                            @Override
                            public Publisher<Collection<ServiceDiscovererEvent<R>>> discover(final U u) {
                                return failed(new IllegalStateException("Invalid service discoverer."));
                            }

                            @Override
                            public Completable onClose() {
                                return closeable.onClose();
                            }

                            @Override
                            public Completable closeAsync() {
                                return closeable.closeAsync();
                            }

                            @Override
                            public Completable closeAsyncGracefully() {
                                return closeable.closeAsyncGracefully();
                            }
                        }), serviceDiscoverer, partitionAttributesBuilderFactory);
    }
}
