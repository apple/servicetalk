/*
 * Copyright © 2018, 2022-2023 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.client.api.ClientGroup;
import io.servicetalk.client.api.LoadBalancer;
import io.servicetalk.client.api.ServiceDiscoverer;
import io.servicetalk.client.api.ServiceDiscovererEvent;
import io.servicetalk.client.api.partition.PartitionAttributes;
import io.servicetalk.client.api.partition.PartitionAttributesBuilder;
import io.servicetalk.client.api.partition.PartitionedServiceDiscovererEvent;
import io.servicetalk.concurrent.api.AsyncContext;
import io.servicetalk.concurrent.api.BiIntFunction;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.ListenableAsyncCloseable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.http.api.DelegatingSingleAddressHttpClientBuilder;
import io.servicetalk.http.api.FilterableStreamingHttpLoadBalancedConnection;
import io.servicetalk.http.api.HttpClient;
import io.servicetalk.http.api.HttpHeaderNames;
import io.servicetalk.http.api.HttpProviders.MultiAddressHttpClientBuilderProvider;
import io.servicetalk.http.api.HttpProviders.SingleAddressHttpClientBuilderProvider;
import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.http.api.MultiAddressHttpClientBuilder;
import io.servicetalk.http.api.MultiAddressHttpClientBuilder.SingleAddressInitializer;
import io.servicetalk.http.api.PartitionedHttpClientBuilder;
import io.servicetalk.http.api.ProxyConfig;
import io.servicetalk.http.api.SingleAddressHttpClientBuilder;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.loadbalancer.RoundRobinLoadBalancers;
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.api.TransportObserver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

import static io.servicetalk.concurrent.api.AsyncCloseables.emptyAsyncCloseable;
import static io.servicetalk.concurrent.api.Publisher.failed;
import static io.servicetalk.dns.discovery.netty.DnsServiceDiscoverers.globalARecordsDnsServiceDiscoverer;
import static io.servicetalk.dns.discovery.netty.DnsServiceDiscoverers.globalSrvRecordsDnsServiceDiscoverer;
import static io.servicetalk.http.netty.InternalServiceDiscoverers.mappingServiceDiscoverer;
import static io.servicetalk.http.netty.InternalServiceDiscoverers.resolvedServiceDiscoverer;
import static io.servicetalk.utils.internal.ServiceLoaderUtils.loadProviders;
import static java.util.function.Function.identity;

/**
 * Factory methods for building {@link HttpClient} (and other API variations) instances.
 */
public final class HttpClients {

    private static final Logger LOGGER = LoggerFactory.getLogger(HttpClients.class);

    private static final List<SingleAddressHttpClientBuilderProvider> SINGLE_ADDRESS_PROVIDERS;
    private static final List<MultiAddressHttpClientBuilderProvider> MULTI_ADDRESS_PROVIDERS;
    private static final String UNDEFINED = "undefined";

    static {
        final ClassLoader classLoader = HttpClients.class.getClassLoader();
        SINGLE_ADDRESS_PROVIDERS = loadProviders(SingleAddressHttpClientBuilderProvider.class, classLoader, LOGGER);
        MULTI_ADDRESS_PROVIDERS = loadProviders(MultiAddressHttpClientBuilderProvider.class, classLoader, LOGGER);
    }

    private HttpClients() {
        // No instances
    }

    private static <U, R> SingleAddressHttpClientBuilder<U, R> applyProviders(
            final U address, SingleAddressHttpClientBuilder<U, R> builder) {
        for (SingleAddressHttpClientBuilderProvider provider : SINGLE_ADDRESS_PROVIDERS) {
            builder = provider.newBuilder(address, builder);
        }
        return builder;
    }

    private static <U, R> MultiAddressHttpClientBuilder<U, R> applyProviders(
            final String id, MultiAddressHttpClientBuilder<U, R> builder) {
        if (id.isEmpty()) {
            throw new IllegalArgumentException("ID can not be empty");
        }
        for (MultiAddressHttpClientBuilderProvider provider : MULTI_ADDRESS_PROVIDERS) {
            builder = provider.newBuilder(id, builder);
        }
        return builder;
    }

    /**
     * Creates a {@link MultiAddressHttpClientBuilder} for clients capable of parsing an <a
     * href="https://tools.ietf.org/html/rfc7230#section-5.3.2">absolute-form URL</a>, connecting to multiple addresses
     * with default {@link LoadBalancer} and DNS {@link ServiceDiscoverer} using
     * {@link DiscoveryStrategy#BACKGROUND background} discovery strategy.
     * <p>
     * When a <a href="https://tools.ietf.org/html/rfc3986#section-4.2">relative URL</a> is passed in the {@link
     * StreamingHttpRequest#requestTarget(String)} this client requires a {@link HttpHeaderNames#HOST} present in
     * order to infer the remote address.
     * <p>
     * The returned builder can be customized using {@link MultiAddressHttpClientBuilderProvider}.
     *
     * @return new builder with default configuration
     * @see MultiAddressHttpClientBuilderProvider
     */
    public static MultiAddressHttpClientBuilder<HostAndPort, InetSocketAddress> forMultiAddressUrl() {
        return forMultiAddressUrl(UNDEFINED);
    }

    /**
     * Creates a {@link MultiAddressHttpClientBuilder} for clients capable of parsing an <a
     * href="https://tools.ietf.org/html/rfc7230#section-5.3.2">absolute-form URL</a>, connecting to multiple addresses
     * with default {@link LoadBalancer} and DNS {@link ServiceDiscoverer} using
     * {@link DiscoveryStrategy#BACKGROUND background} discovery strategy.
     * <p>
     * When a <a href="https://tools.ietf.org/html/rfc3986#section-4.2">relative URL</a> is passed in the {@link
     * StreamingHttpRequest#requestTarget(String)} this client requires a {@link HttpHeaderNames#HOST} present in
     * order to infer the remote address.
     * <p>
     * The returned builder can be customized using {@link MultiAddressHttpClientBuilderProvider}.
     *
     * @param id a (unique) ID to identify the created {@link MultiAddressHttpClientBuilder}, like a name or a purpose
     * of the future client that will be built. This helps  {@link MultiAddressHttpClientBuilderProvider} to distinguish
     * this builder from others.
     * @return new builder with default configuration
     * @see MultiAddressHttpClientBuilderProvider
     */
    public static MultiAddressHttpClientBuilder<HostAndPort, InetSocketAddress> forMultiAddressUrl(
            final String id) {
        return applyProviders(id, new DefaultMultiAddressUrlHttpClientBuilder(HttpClients::forSingleAddress));
    }

    /**
     * Creates a {@link MultiAddressHttpClientBuilder} for clients capable of parsing an <a
     * href="https://tools.ietf.org/html/rfc7230#section-5.3.2">absolute-form URL</a>, connecting to multiple addresses
     * with default {@link LoadBalancer} and DNS {@link ServiceDiscoverer} using the specified
     * {@link DiscoveryStrategy}.
     * <p>
     * When a <a href="https://tools.ietf.org/html/rfc3986#section-4.2">relative URL</a> is passed in the {@link
     * StreamingHttpRequest#requestTarget(String)} this client requires a {@link HttpHeaderNames#HOST} present in
     * order to infer the remote address.
     * <p>
     * The returned builder can be customized using {@link MultiAddressHttpClientBuilderProvider}.
     *
     * @param id a (unique) ID to identify the created {@link MultiAddressHttpClientBuilder}, like a name or a purpose
     * of the future client that will be built. This helps  {@link MultiAddressHttpClientBuilderProvider} to distinguish
     * this builder from others.
     * @param discoveryStrategy {@link DiscoveryStrategy} to use
     * @return new builder with default configuration
     * @see MultiAddressHttpClientBuilderProvider
     */
    public static MultiAddressHttpClientBuilder<HostAndPort, InetSocketAddress> forMultiAddressUrl(
            final String id, final DiscoveryStrategy discoveryStrategy) {
        return applyProviders(id, new DefaultMultiAddressUrlHttpClientBuilder(
                hostAndPort -> forSingleAddress(hostAndPort, discoveryStrategy)));
    }

    /**
     * Creates a {@link MultiAddressHttpClientBuilder} for clients capable of parsing an <a
     * href="https://tools.ietf.org/html/rfc7230#section-5.3.2">absolute-form URL</a>, connecting to multiple addresses
     * with default {@link LoadBalancer}, using the specified {@link ServiceDiscoverer} and {@link DiscoveryStrategy}.
     * <p>
     * When a <a href="https://tools.ietf.org/html/rfc3986#section-4.2">relative URL</a> is passed in the {@link
     * StreamingHttpRequest#requestTarget(String)} this client requires a {@link HttpHeaderNames#HOST} present in
     * order to infer the remote address.
     * <p>
     * The returned builder can be customized using {@link MultiAddressHttpClientBuilderProvider}.
     *
     * @param id a (unique) ID to identify the created {@link MultiAddressHttpClientBuilder}, like a name or a purpose
     * of the future client that will be built. This helps  {@link MultiAddressHttpClientBuilderProvider} to distinguish
     * this builder from others.
     * @param serviceDiscoverer The {@link ServiceDiscoverer} to resolve addresses of remote servers to connect to.
     * The lifecycle of the provided {@link ServiceDiscoverer} should be managed by the caller.
     * @param discoveryStrategy {@link DiscoveryStrategy} to use.
     * @return new builder with default configuration.
     * @see MultiAddressHttpClientBuilderProvider
     */
    public static MultiAddressHttpClientBuilder<HostAndPort, InetSocketAddress> forMultiAddressUrl(
            final String id,
            final ServiceDiscoverer<HostAndPort, InetSocketAddress, ServiceDiscovererEvent<InetSocketAddress>>
                    serviceDiscoverer,
            final DiscoveryStrategy discoveryStrategy) {
        return applyProviders(id, new DefaultMultiAddressUrlHttpClientBuilder(
                hostAndPort -> forSingleAddress(serviceDiscoverer, hostAndPort, discoveryStrategy)));
    }

    /**
     * Creates a {@link MultiAddressHttpClientBuilder} for clients capable of parsing an <a
     * href="https://tools.ietf.org/html/rfc7230#section-5.3.2">absolute-form URL</a>, connecting to multiple addresses
     * with default {@link LoadBalancer} and user provided {@link ServiceDiscoverer}.
     * <p>
     * When a <a href="https://tools.ietf.org/html/rfc3986#section-4.2">relative URL</a> is passed in the {@link
     * StreamingHttpRequest#requestTarget(String)} this client requires a {@link HttpHeaderNames#HOST} present in
     * order to infer the remote address.
     * <p>
     * The returned builder can be customized using {@link MultiAddressHttpClientBuilderProvider}.
     *
     * @param serviceDiscoverer The {@link ServiceDiscoverer} to resolve addresses of remote servers to connect to.
     * The lifecycle of the provided {@link ServiceDiscoverer} should be managed by the caller.
     * @return new builder with default configuration
     * @see MultiAddressHttpClientBuilderProvider
     * @deprecated Use {@link #forMultiAddressUrl()} to create {@link MultiAddressHttpClientBuilder}, then use
     * {@link MultiAddressHttpClientBuilder#initializer(SingleAddressInitializer)} to override {@link ServiceDiscoverer}
     * using {@link SingleAddressHttpClientBuilder#serviceDiscoverer(ServiceDiscoverer)} for all or some of the internal
     * clients.
     */
    @Deprecated // FIXME: 0.43 - remove deprecated method
    public static MultiAddressHttpClientBuilder<HostAndPort, InetSocketAddress> forMultiAddressUrl(
            final ServiceDiscoverer<HostAndPort, InetSocketAddress, ServiceDiscovererEvent<InetSocketAddress>>
                    serviceDiscoverer) {
        return applyProviders(UNDEFINED,
                new DefaultMultiAddressUrlHttpClientBuilder(address -> forSingleAddress(serviceDiscoverer, address)));
    }

    /**
     * Creates a {@link SingleAddressHttpClientBuilder} for an address with default {@link LoadBalancer} and DNS {@link
     * ServiceDiscoverer} using {@link DiscoveryStrategy#BACKGROUND background} discovery strategy.
     * <p>
     * The returned builder can be customized using {@link SingleAddressHttpClientBuilderProvider}.
     *
     * @param host host to connect to, resolved by default using a DNS {@link ServiceDiscoverer}. This will also be
     * used for the {@link HttpHeaderNames#HOST} together with the {@code port}. Use
     * {@link SingleAddressHttpClientBuilder#unresolvedAddressToHost(Function)} if you want to override that value
     * or {@link SingleAddressHttpClientBuilder#hostHeaderFallback(boolean)} if you want to disable this behavior.
     * @param port port to connect to
     * @return new builder for the address
     * @see SingleAddressHttpClientBuilderProvider
     */
    public static SingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> forSingleAddress(
            final String host, final int port) {
        return forSingleAddress(HostAndPort.of(host, port));
    }

    /**
     * Creates a {@link SingleAddressHttpClientBuilder} for an address with default {@link LoadBalancer} and DNS {@link
     * ServiceDiscoverer} using {@link DiscoveryStrategy#BACKGROUND background} discovery strategy.
     * <p>
     * The returned builder can be customized using {@link SingleAddressHttpClientBuilderProvider}.
     *
     * @param address the {@code UnresolvedAddress} to connect to, resolved by default using a DNS {@link
     * ServiceDiscoverer}. This address will also be used for the {@link HttpHeaderNames#HOST}.
     * Use {@link SingleAddressHttpClientBuilder#unresolvedAddressToHost(Function)} if you want to override that
     * value or {@link SingleAddressHttpClientBuilder#hostHeaderFallback(boolean)} if you want to disable this behavior.
     * @return new builder for the address
     * @see SingleAddressHttpClientBuilderProvider
     */
    public static SingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> forSingleAddress(
            final HostAndPort address) {
        return forSingleAddress(address, DiscoveryStrategy.BACKGROUND);
    }

    /**
     * Creates a {@link SingleAddressHttpClientBuilder} for an address with default {@link LoadBalancer} and DNS {@link
     * ServiceDiscoverer} using the specified {@link DiscoveryStrategy}.
     * <p>
     * The returned builder can be customized using {@link SingleAddressHttpClientBuilderProvider}.
     *
     * @param host host to connect to, resolved by default using a DNS {@link ServiceDiscoverer}. This will also be
     * used for the {@link HttpHeaderNames#HOST} together with the {@code port}. Use
     * {@link SingleAddressHttpClientBuilder#unresolvedAddressToHost(Function)} if you want to override that value
     * or {@link SingleAddressHttpClientBuilder#hostHeaderFallback(boolean)} if you want to disable this behavior.
     * @param port port to connect to
     * @param discoveryStrategy {@link DiscoveryStrategy} to use
     * @return new builder for the address
     * @see SingleAddressHttpClientBuilderProvider
     */
    public static SingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> forSingleAddress(
            final String host, final int port, final DiscoveryStrategy discoveryStrategy) {
        return forSingleAddress(HostAndPort.of(host, port), discoveryStrategy);
    }

    /**
     * Creates a {@link SingleAddressHttpClientBuilder} for an address with default {@link LoadBalancer} and DNS {@link
     * ServiceDiscoverer} using the specified {@link DiscoveryStrategy}.
     * <p>
     * The returned builder can be customized using {@link SingleAddressHttpClientBuilderProvider}.
     *
     * @param address the {@code UnresolvedAddress} to connect to, resolved by default using a DNS {@link
     * ServiceDiscoverer}. This address will also be used for the {@link HttpHeaderNames#HOST}.
     * Use {@link SingleAddressHttpClientBuilder#unresolvedAddressToHost(Function)} if you want to override that
     * value or {@link SingleAddressHttpClientBuilder#hostHeaderFallback(boolean)} if you want to disable this behavior.
     * @param discoveryStrategy {@link DiscoveryStrategy} to use
     * @return new builder for the address
     * @see SingleAddressHttpClientBuilderProvider
     */
    public static SingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> forSingleAddress(
            final HostAndPort address, final DiscoveryStrategy discoveryStrategy) {
        return forSingleAddress(globalARecordsDnsServiceDiscoverer(), address, discoveryStrategy,
                InternalServiceDiscoverers::unresolvedServiceDiscoverer,
                ResolvingConnectionFactoryFilter::withGlobalARecordsDnsServiceDiscoverer);
    }

    /**
     * Creates a {@link SingleAddressHttpClientBuilder} for the passed {@code serviceName} with default
     * {@link LoadBalancer} and a DNS {@link ServiceDiscoverer} using
     * <a href="https://tools.ietf.org/html/rfc2782">SRV record</a> lookups with
     * {@link DiscoveryStrategy#BACKGROUND background} discovery strategy.
     * <p>
     * The returned builder can be customized using {@link SingleAddressHttpClientBuilderProvider}.
     *
     * @param serviceName The service name to resolve with <a href="https://tools.ietf.org/html/rfc2782">SRV DNS</a>.
     * @return new builder for the address
     * @see SingleAddressHttpClientBuilderProvider
     */
    public static SingleAddressHttpClientBuilder<String, InetSocketAddress> forServiceAddress(
            final String serviceName) {
        final ServiceDiscoverer<String, InetSocketAddress, ServiceDiscovererEvent<InetSocketAddress>> sd =
                globalSrvRecordsDnsServiceDiscoverer();
        return applyProviders(serviceName,
                new DefaultSingleAddressHttpClientBuilder<>(serviceName, sd))
                // We need to pass SD into constructor to align types, but providers won't see that.
                // Invoke a builder method only to notify providers what SD is actually used.
                .serviceDiscoverer(sd);
    }

    /**
     * Creates a {@link SingleAddressHttpClientBuilder} for a resolved address with default {@link LoadBalancer}.
     *
     * @param host resolved host address to connect. This will also be used for the {@link HttpHeaderNames#HOST}
     * together with the {@code port}. Use {@link SingleAddressHttpClientBuilder#unresolvedAddressToHost(Function)}
     * if you want to override that value or {@link SingleAddressHttpClientBuilder#hostHeaderFallback(boolean)}
     * if you want to disable this behavior.
     * <p>
     * Note, if {@link SingleAddressHttpClientBuilder#proxyConfig(ProxyConfig) a proxy} is configured for this client,
     * the proxy address also needs to be already resolved. Otherwise, runtime exceptions will be thrown when
     * the client is built.
     * <p>
     * The returned builder can be customized using {@link SingleAddressHttpClientBuilderProvider}.
     *
     * @param port port to connect to
     * @return new builder for the address
     * @see SingleAddressHttpClientBuilderProvider
     */
    public static SingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> forResolvedAddress(
            final String host, final int port) {
        return forResolvedAddress(HostAndPort.of(host, port));
    }

    /**
     * Creates a {@link SingleAddressHttpClientBuilder} for an address with default {@link LoadBalancer}.
     *
     * @param address the {@code ResolvedAddress} to connect. This address will also be used for the
     * {@link HttpHeaderNames#HOST}. Use {@link SingleAddressHttpClientBuilder#unresolvedAddressToHost(Function)}
     * if you want to override that value or {@link SingleAddressHttpClientBuilder#hostHeaderFallback(boolean)} if you
     * want to disable this behavior.
     * <p>
     * Note, if {@link SingleAddressHttpClientBuilder#proxyConfig(ProxyConfig) a proxy} is configured for this client,
     * the proxy address also needs to be already resolved. Otherwise, runtime exceptions will be thrown when
     * the client is built.
     * <p>
     * The returned builder can be customized using {@link SingleAddressHttpClientBuilderProvider}.
     *
     * @return new builder for the address
     * @see SingleAddressHttpClientBuilderProvider
     */
    public static SingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> forResolvedAddress(
            final HostAndPort address) {
        final ServiceDiscoverer<HostAndPort, InetSocketAddress, ServiceDiscovererEvent<InetSocketAddress>> sd =
                resolvedServiceDiscoverer();
        return applyProviders(address,
                withUnmodifiableServiceDiscoverer(new DefaultSingleAddressHttpClientBuilder<>(address, sd),
                        sd, "resolved address " + address))
                // Apply after providers to let them see these customizations.
                .serviceDiscoverer(sd)
                .retryServiceDiscoveryErrors(NoRetriesStrategy.INSTANCE);
    }

    /**
     * Creates a {@link SingleAddressHttpClientBuilder} for an address with default {@link LoadBalancer}.
     * <p>
     * The returned builder can be customized using {@link SingleAddressHttpClientBuilderProvider}.
     *
     * @param address the {@code ResolvedAddress} to connect. This address will also be used for the
     * {@link HttpHeaderNames#HOST}. Use {@link SingleAddressHttpClientBuilder#unresolvedAddressToHost(Function)}
     * if you want to override that value or {@link SingleAddressHttpClientBuilder#hostHeaderFallback(boolean)} if you
     * want to disable this behavior.
     * @param <R> The type of resolved {@link SocketAddress}.
     * @return new builder for the address
     * @see SingleAddressHttpClientBuilderProvider
     */
    public static <R extends SocketAddress> SingleAddressHttpClientBuilder<R, R> forResolvedAddress(final R address) {
        final ServiceDiscoverer<R, R, ServiceDiscovererEvent<R>> sd =
                mappingServiceDiscoverer(identity(), "identity for " + address.getClass().getSimpleName());
        return applyProviders(address,
                withUnmodifiableServiceDiscoverer(new DefaultSingleAddressHttpClientBuilder<>(address, sd),
                        sd, "resolved address " + address))
                // Apply after providers to let them see these customizations.
                .serviceDiscoverer(sd)
                .retryServiceDiscoveryErrors(NoRetriesStrategy.INSTANCE);
    }

    /**
     * Creates a {@link SingleAddressHttpClientBuilder} for a custom address type with default {@link LoadBalancer} and
     * user provided {@link ServiceDiscoverer} using {@link DiscoveryStrategy#BACKGROUND background} discovery strategy.
     * <p>
     * The returned builder can be customized using {@link SingleAddressHttpClientBuilderProvider}.
     *
     * @param serviceDiscoverer The {@link ServiceDiscoverer} to resolve addresses of remote servers to connect to.
     * The lifecycle of the provided {@link ServiceDiscoverer} should be managed by the caller.
     * @param address the {@code UnresolvedAddress} to connect to resolved using the provided {@code serviceDiscoverer}.
     * This address will also be used for the {@link HttpHeaderNames#HOST} using a best effort conversion. Use {@link
     * SingleAddressHttpClientBuilder#unresolvedAddressToHost(Function)} if you want to override that value or
     * {@link SingleAddressHttpClientBuilder#hostHeaderFallback(boolean)} if you want to disable this behavior.
     * @param <U> the type of address before resolution (unresolved address)
     * @param <R> the type of address after resolution (resolved address)
     * @return new builder with provided configuration
     * @see SingleAddressHttpClientBuilderProvider
     */
    public static <U, R> SingleAddressHttpClientBuilder<U, R> forSingleAddress(
            final ServiceDiscoverer<U, R, ? extends ServiceDiscovererEvent<R>> serviceDiscoverer,
            final U address) {
        return forSingleAddress(serviceDiscoverer, address, DiscoveryStrategy.BACKGROUND);
    }

    /**
     * Creates a {@link SingleAddressHttpClientBuilder} for a custom address type with default {@link LoadBalancer} and
     * user provided {@link ServiceDiscoverer} using the specified {@link DiscoveryStrategy}.
     * <p>
     * The returned builder can be customized using {@link SingleAddressHttpClientBuilderProvider}.
     *
     * @param serviceDiscoverer The {@link ServiceDiscoverer} to resolve addresses of remote servers to connect to.
     * The lifecycle of the provided {@link ServiceDiscoverer} should be managed by the caller.
     * @param address the {@code UnresolvedAddress} to connect to resolved using the provided {@code serviceDiscoverer}.
     * This address will also be used for the {@link HttpHeaderNames#HOST} using a best effort conversion. Use {@link
     * SingleAddressHttpClientBuilder#unresolvedAddressToHost(Function)} if you want to override that value or
     * {@link SingleAddressHttpClientBuilder#hostHeaderFallback(boolean)} if you want to disable this behavior.
     * @param discoveryStrategy {@link DiscoveryStrategy} to use
     * @param <U> the type of address before resolution (unresolved address)
     * @param <R> the type of address after resolution (resolved address)
     * @return new builder with provided configuration
     * @see SingleAddressHttpClientBuilderProvider
     */
    @SuppressWarnings("unchecked")
    public static <U, R> SingleAddressHttpClientBuilder<U, R> forSingleAddress(
            final ServiceDiscoverer<U, R, ? extends ServiceDiscovererEvent<R>> serviceDiscoverer,
            final U address,
            final DiscoveryStrategy discoveryStrategy) {
        return forSingleAddress(serviceDiscoverer, address, discoveryStrategy,
                // Because the mapping is unknown, the unchecked cast here is required to fool the compiler but won't
                // cause issues at runtime because all parametrized types are translated into Object type by javac.
                () -> mappingServiceDiscoverer(u -> (R) u,
                        "from " + address.getClass().getSimpleName() + " to an " + Object.class.getSimpleName()),
                // Propagate unresolved address directly to the CF if we cannot map/unmap U and R.
                () -> new ResolvingConnectionFactoryFilter<>(__ -> address, serviceDiscoverer));
    }

    private static <U, R> SingleAddressHttpClientBuilder<U, R> forSingleAddress(
            final ServiceDiscoverer<U, R, ? extends ServiceDiscovererEvent<R>> serviceDiscoverer,
            final U address,
            final DiscoveryStrategy discoveryStrategy,
            final Supplier<ServiceDiscoverer<U, R, ? extends ServiceDiscovererEvent<R>>> unresolvedServiceDiscoverer,
            final Supplier<ResolvingConnectionFactoryFilter<U, R>> resolvingConnectionFactory) {
        switch (discoveryStrategy) {
            case BACKGROUND:
                return applyProviders(address, new DefaultSingleAddressHttpClientBuilder<>(address, serviceDiscoverer))
                        // Apply after providers to let them see these customizations.
                        .serviceDiscoverer(serviceDiscoverer);
            case ON_NEW_CONNECTION:
                // Use a special ServiceDiscoverer that will propagate the unresolved address to LB and CF,
                // then append a ConnectionFactory that will run resolve the address.
                final ServiceDiscoverer<U, R, ? extends ServiceDiscovererEvent<R>> usd =
                        unresolvedServiceDiscoverer.get();
                return applyProviders(address,
                        withUnmodifiableServiceDiscoverer(new DefaultSingleAddressHttpClientBuilder<>(address, usd),
                                usd, address + " with " + discoveryStrategy.name() + " discovery strategy"))
                        // Apply after providers to let them see these customizations.
                        .serviceDiscoverer(usd)
                        .retryServiceDiscoveryErrors(NoRetriesStrategy.INSTANCE)
                        // Disable health-checking:
                        .loadBalancerFactory(DefaultHttpLoadBalancerFactory.Builder.from(
                                RoundRobinLoadBalancers.<R, FilterableStreamingHttpLoadBalancedConnection>builder(
                                        // Use a different ID to let providers distinguish this LB from the default one
                                        DefaultHttpLoadBalancerFactory.class.getSimpleName() + '-' +
                                                DiscoveryStrategy.ON_NEW_CONNECTION.name())
                                        .healthCheckFailedConnectionsThreshold(-1)
                                        .build())
                                .build())
                        .appendConnectionFactoryFilter(resolvingConnectionFactory.get());
            default:
                throw new IllegalArgumentException("Unsupported strategy: " + discoveryStrategy);
        }
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
     * or {@link SingleAddressHttpClientBuilder#hostHeaderFallback(boolean)} if you want to disable this behavior.
     * @param partitionAttributesBuilderFactory The factory {@link Function} used to build {@link PartitionAttributes}
     * from {@link HttpRequestMetaData}.
     * @param <U> the type of address before resolution (unresolved address)
     * @param <R> the type of address after resolution (resolved address)
     * @return new builder with provided configuration
     * @deprecated We are unaware of anyone using "partition" feature and plan to remove it in future releases.
     * If you depend on it, consider using {@link ClientGroup} as an alternative or reach out to the maintainers
     * describing the use-case.
     */
    @Deprecated // FIXME: 0.43 - remove deprecated method
    public static <U, R> PartitionedHttpClientBuilder<U, R> forPartitionedAddress(
            final ServiceDiscoverer<U, R, PartitionedServiceDiscovererEvent<R>> serviceDiscoverer,
            final U address,
            final Function<HttpRequestMetaData, PartitionAttributesBuilder> partitionAttributesBuilderFactory) {
        return new DefaultPartitionedHttpClientBuilder<>(address,
                () -> forSingleAddress(
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
                        }, address), serviceDiscoverer, partitionAttributesBuilderFactory);
    }

    // Prevents users from overriding a ServiceDiscoverer when it's not expected based on the used client factory.
    private static <U, R> SingleAddressHttpClientBuilder<U, R> withUnmodifiableServiceDiscoverer(
            final SingleAddressHttpClientBuilder<U, R> delegate,
            final ServiceDiscoverer<U, R, ? extends ServiceDiscovererEvent<R>> preConfiguredSd,
            final String description) {
        return new DelegatingSingleAddressHttpClientBuilder<U, R>(delegate) {
            @Override
            public SingleAddressHttpClientBuilder<U, R> serviceDiscoverer(
                    final ServiceDiscoverer<U, R, ? extends ServiceDiscovererEvent<R>> serviceDiscoverer) {
                if (serviceDiscoverer != preConfiguredSd) {
                    throw new IllegalArgumentException("Builder for a client for " + description +
                            " doesn't allow changing ServiceDiscoverer to any other instance except the pre-configured "
                            + preConfiguredSd + ", passed argument: " + serviceDiscoverer);
                }
                delegate().serviceDiscoverer(serviceDiscoverer);
                return this;
            }
        };
    }

    /**
     * A retry strategy that never retries. Useful for {@link ServiceDiscoverer} instances that are known to never fail.
     */
    static final class NoRetriesStrategy implements BiIntFunction<Throwable, Completable> {

        static final BiIntFunction<Throwable, Completable> INSTANCE = new NoRetriesStrategy();

        private NoRetriesStrategy() {
            // Singleton
        }

        @Override
        public Completable apply(final int i, final Throwable t) {
            return Completable.failed(t);
        }
    }

    /**
     * Defines {@link ServiceDiscoverer} will be used.
     */
    public enum DiscoveryStrategy {
        /**
         * Resolves an address in a background.
         * <p>
         * The {@link LoadBalancer} subscribes to the {@link ServiceDiscoverer#discover(Object) stream of events} from
         * {@link ServiceDiscoverer} and listens for updates in a background. All changes propagated by a discovery
         * system will be available to the {@link LoadBalancer}. When a new connection is required, the resolved address
         * will already be available.
         * <p>
         * This is the recommended default strategy that allows a client to:
         * <ol>
         *     <li>Perform <a href="https://docs.servicetalk.io/servicetalk-loadbalancer/SNAPSHOT/index.html">
         *     client-side load balancing</a>.</li>
         *     <li>Remove the cost (latency) required for resolving an address from the hot path of request processing.
         *     </li>
         *     <li>Move/shift traffic immediately based on updates from the {@link ServiceDiscoverer}.</li>
         * </ol>
         *
         * use
         * <a href="https://docs.servicetalk.io/servicetalk-loadbalancer/SNAPSHOT/index.html">client-side load balancing
         * </a> and removes the cost (latency) required for resolving an address from the hot execution path.
         */
        BACKGROUND,

        /**
         * Resolves an address every time a new connection is required.
         * <p>
         * Client holds the unresolved address internally and uses {@link ServiceDiscoverer} to resolve it only when a
         * new connection is required. This behavior may be beneficial for the following scenarios:
         * <ol>
         *     <li>Client has a low rate of opening new connections.</li>
         *     <li>Application creates many clients (or uses a {@link #forMultiAddressUrl() multi-address} client) that
         *     talk to many different hosts. the default {@link #BACKGROUND} strategy introduces a risk to overload the
         *     discovery system. The impact might be more visible when {@link ServiceDiscoverer} uses polling to receive
         *     updated, like DNS.</li>
         *     <li>To mimic behavior of other HTTP client implementations, like default Java HttpClient or
         *     {@link HttpURLConnection}.</li>
         * </ol>
         * <p>
         * Important side effects of this strategy to take into account:
         * <ol>
         *     <li>The total latency for opening a new connection will be increased by a latency of the first
         *     {@link ServiceDiscoverer} answer.</li>
         *     <li>If the target host has more than one resolved address, created clients loose ability to perform
         *     <a href="https://docs.servicetalk.io/servicetalk-loadbalancer/SNAPSHOT/index.html">client-side load
         *     balancing</a> for each request. Instead, the client will connect to any random resolved address returned
         *     by a {@link ServiceDiscoverer}. This approach introduces randomness at connect time but doesn't guarantee
         *     balancing for every request.</li>
         *     <li>Created clients won't be able to move/shift traffic based on changes observed by a
         *     {@link ServiceDiscoverer} until the remote server closes existing connections.</li>
         *     <li>The only way to change or customize a {@link ServiceDiscoverer} for this strategy is to use
         *     {@link #forSingleAddress(ServiceDiscoverer, Object, DiscoveryStrategy)} client factory. Setting a
         *     different {@link ServiceDiscoverer} instance via
         *     {@link SingleAddressHttpClientBuilder#serviceDiscoverer(ServiceDiscoverer)} method later will throw an
         *     exception.</li>
         *     <li>Currently, {@link TransportObserver} won't be able to take {@link ServiceDiscoverer} latency into
         *     account because {@link TransportObserver#onNewConnection(Object, Object) onNewConnection} callback is
         *     invoked later. Users need to use observability features provided by a {@link ServiceDiscoverer}
         *     implementation or intercept its {@link ServiceDiscoverer#discover(Object)} publisher to track this
         *     latency. Correlation between that tracking logic and
         *     {@link TransportObserver#onNewConnection(Object, Object)} (if desired) can be achieved using a state
         *     propagated via {@link AsyncContext}. Observability features for this strategy will be improved in the
         *     future releases.</li>
         * </ol>
         */
        ON_NEW_CONNECTION
    }
}
