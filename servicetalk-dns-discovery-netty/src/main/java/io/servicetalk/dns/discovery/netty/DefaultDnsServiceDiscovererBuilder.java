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
package io.servicetalk.dns.discovery.netty;

import io.servicetalk.client.api.DefaultServiceDiscovererEvent;
import io.servicetalk.client.api.ServiceDiscoverer;
import io.servicetalk.client.api.ServiceDiscovererEvent;
import io.servicetalk.client.api.ServiceDiscovererFilterFactory;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.api.IoExecutor;

import io.netty.resolver.dns.DnsNameResolverTimeoutException;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.function.Predicate;
import javax.annotation.Nullable;

import static io.servicetalk.client.api.ServiceDiscovererFilterFactory.identity;
import static io.servicetalk.concurrent.api.Executors.immediate;
import static io.servicetalk.concurrent.api.RetryStrategies.retryWithConstantBackoffAndJitter;
import static io.servicetalk.transport.netty.internal.GlobalExecutionContext.globalExecutionContext;

/**
 * Builder use to create objects of type {@link DefaultDnsServiceDiscoverer}.
 */
public final class DefaultDnsServiceDiscovererBuilder {
    @Nullable
    private DnsServerAddressStreamProvider dnsServerAddressStreamProvider;
    @Nullable
    private DnsResolverAddressTypes dnsResolverAddressTypes;
    @Nullable
    private Integer ndots;
    private Predicate<Throwable> invalidateHostsOnDnsFailure = defaultInvalidateHostsOnDnsFailurePredicate();
    @Nullable
    private Boolean optResourceEnabled;
    @Nullable
    private IoExecutor ioExecutor;
    @Nullable
    private Duration queryTimeout;
    private boolean applyRetryFilter = true;
    private int minTTLSeconds = 10;
    private ServiceDiscovererFilterFactory<String, InetAddress, ServiceDiscovererEvent<InetAddress>>
            serviceDiscoveryFilterFactory = identity();

    /**
     * The minimum allowed TTL. This will be the minimum poll interval.
     *
     * @param minTTLSeconds The minimum amount of time a cache entry will be considered valid (in seconds).
     * @return {@code this}.
     */
    public DefaultDnsServiceDiscovererBuilder minTTL(final int minTTLSeconds) {
        if (minTTLSeconds < 1) {
            throw new IllegalArgumentException("minTTLSeconds: " + minTTLSeconds + " (expected > 1)");
        }
        this.minTTLSeconds = minTTLSeconds;
        return this;
    }

    /**
     * Set the {@link DnsServerAddressStreamProvider} which determines which DNS server should be used per query.
     *
     * @param dnsServerAddressStreamProvider the {@link DnsServerAddressStreamProvider} which determines which DNS
     * server should be used per query.
     * @return {@code this}.
     */
    public DefaultDnsServiceDiscovererBuilder dnsServerAddressStreamProvider(
            @Nullable final DnsServerAddressStreamProvider dnsServerAddressStreamProvider) {
        this.dnsServerAddressStreamProvider = dnsServerAddressStreamProvider;
        return this;
    }

    /**
     * Enable the automatic inclusion of a optional records that tries to give the remote DNS server a hint about
     * how much data the resolver can read per response. Some DNSServer may not support this and so fail to answer
     * queries. If you find problems you may want to disable this.
     *
     * @param optResourceEnabled if optional records inclusion is enabled.
     * @return {@code this}.
     */
    public DefaultDnsServiceDiscovererBuilder optResourceEnabled(final boolean optResourceEnabled) {
        this.optResourceEnabled = optResourceEnabled;
        return this;
    }

    /**
     * Set the number of dots which must appear in a name before an initial absolute query is made.
     *
     * @param ndots the ndots value.
     * @return {@code this}.
     */
    public DefaultDnsServiceDiscovererBuilder ndots(final int ndots) {
        this.ndots = ndots;
        return this;
    }

    /**
     * Sets the timeout of each DNS query performed by this service discoverer.
     *
     * @param queryTimeout the query timeout value
     * @return {@code this}.
     */
    public DefaultDnsServiceDiscovererBuilder queryTimeout(final Duration queryTimeout) {
        this.queryTimeout = queryTimeout;
        return this;
    }

    /**
     * Allows sending 'unavailable' events for all current active hosts for particular DNS errors.
     * <p>
     * Note: The default does not send 'unavailable' events when a DNS lookup times out.
     *
     * @param invalidateHostsOnDnsFailure determines whether or not to send 'unavailable' events.
     * @return {@code this}.
     */
    public DefaultDnsServiceDiscovererBuilder invalidateHostsOnDnsFailure(
            final Predicate<Throwable> invalidateHostsOnDnsFailure) {
        this.invalidateHostsOnDnsFailure = invalidateHostsOnDnsFailure;
        return this;
    }

    /**
     * Returns a default value for {@link #invalidateHostsOnDnsFailure(Predicate)}.
     *
     * @return a default value for {@link #invalidateHostsOnDnsFailure(Predicate)}
     */
    public Predicate<Throwable> defaultInvalidateHostsOnDnsFailurePredicate() {
        return t -> t instanceof UnknownHostException &&
                !(t.getCause() instanceof DnsNameResolverTimeoutException);
    }

    /**
     * Sets the list of the protocol families of the address resolved.
     *
     * @param dnsResolverAddressTypes the address types.
     * @return {@code this}.
     */
    public DefaultDnsServiceDiscovererBuilder dnsResolverAddressTypes(
            @Nullable final DnsResolverAddressTypes dnsResolverAddressTypes) {
        this.dnsResolverAddressTypes = dnsResolverAddressTypes;
        return this;
    }

    /**
     * Do not perform retries if DNS lookup fails. Instead, terminate the {@link Publisher} with the error.
     *
     * @return {@code this}.
     */
    public DefaultDnsServiceDiscovererBuilder noRetriesOnDnsFailures() {
        this.applyRetryFilter = false;
        return this;
    }

    /**
     * Append the filter to the chain of filters used to decorate the {@link ServiceDiscoverer} created by this
     * builder.
     * <p>
     * Note this method will be used to decorate the result of {@link #build()}/{@link #buildInetDiscoverer()} before
     * it is returned to the user.
     * <p>
     * The order of execution of these filters are in order of append. If 3 filters are added as follows:
     * <pre>
     *     builder.append(filter1).append(filter2).append(filter3)
     * </pre>
     * making a request to a service discoverer wrapped by this filter chain the order of invocation of these filters
     * will be:
     * <pre>
     *     filter1 =&gt; filter2 =&gt; filter3 =&gt; service discoverer
     * </pre>
     *
     * @param factory {@link ServiceDiscovererFilterFactory} to decorate a {@link ServiceDiscoverer} for the purpose of
     * filtering.
     * @return {@code this}
     */
    public DefaultDnsServiceDiscovererBuilder appendFilter(
            final ServiceDiscovererFilterFactory<String, InetAddress, ServiceDiscovererEvent<InetAddress>>
                    factory) {
        serviceDiscoveryFilterFactory = serviceDiscoveryFilterFactory.append(factory);
        return this;
    }

    /**
     * Sets the {@link IoExecutor}.
     *
     * @param ioExecutor {@link IoExecutor} to use.
     * @return {@code this}.
     */
    public DefaultDnsServiceDiscovererBuilder ioExecutor(final IoExecutor ioExecutor) {
        this.ioExecutor = ioExecutor;
        return this;
    }

    /**
     * Build a new instance of {@link ServiceDiscoverer ServiceDiscoverer&lt;String, InetAddress&gt;}.
     *
     * @return a new instance of {@link ServiceDiscoverer ServiceDiscoverer&lt;String, InetAddress&gt;}.
     */
    public ServiceDiscoverer<String, InetAddress, ServiceDiscovererEvent<InetAddress>> buildInetDiscoverer() {
        return newDefaultDnsServiceDiscoverer();
    }

    /**
     * Build a new instance of {@link ServiceDiscoverer ServiceDiscoverer&lt;HostAndPort, InetSocketAddress&gt;}.
     *
     * @return a new instance of {@link ServiceDiscoverer ServiceDiscoverer&lt;HostAndPort, InetSocketAddress&gt;}.
     * @see HostAndPort
     */
    public ServiceDiscoverer<HostAndPort, InetSocketAddress, ServiceDiscovererEvent<InetSocketAddress>> build() {
        return toHostAndPortDiscoverer(newDefaultDnsServiceDiscoverer());
    }

    private ServiceDiscoverer<String, InetAddress,
            ServiceDiscovererEvent<InetAddress>> newDefaultDnsServiceDiscoverer() {
        ServiceDiscovererFilterFactory<String, InetAddress, ServiceDiscovererEvent<InetAddress>> factory =
                this.serviceDiscoveryFilterFactory;

        if (applyRetryFilter) {
            final ServiceDiscovererFilterFactory<String, InetAddress, ServiceDiscovererEvent<InetAddress>>
                    defaultFilterFactory = serviceDiscoverer -> new RetryingDnsServiceDiscovererFilter(
                    serviceDiscoverer, retryWithConstantBackoffAndJitter(
                    Integer.MAX_VALUE, t -> true, Duration.ofSeconds(60), immediate()));
            factory = defaultFilterFactory.append(factory);
        }
        return factory.create(new DefaultDnsServiceDiscoverer(
                ioExecutor == null ? globalExecutionContext().ioExecutor() : ioExecutor, minTTLSeconds, ndots,
                invalidateHostsOnDnsFailure, optResourceEnabled, queryTimeout, dnsResolverAddressTypes,
                dnsServerAddressStreamProvider));
    }

    /**
     * Convert this object from {@link String} host names and {@link InetAddress} resolved address to
     * {@link HostAndPort} to {@link InetSocketAddress}.
     *
     * @return a resolver which will convert from {@link String} host names and {@link InetAddress} resolved address to
     * {@link HostAndPort} to {@link InetSocketAddress}.
     */
    private static ServiceDiscoverer<HostAndPort, InetSocketAddress,
            ServiceDiscovererEvent<InetSocketAddress>> toHostAndPortDiscoverer(
            final ServiceDiscoverer<String, InetAddress, ServiceDiscovererEvent<InetAddress>> serviceDiscoverer) {
        return new ServiceDiscoverer<HostAndPort, InetSocketAddress, ServiceDiscovererEvent<InetSocketAddress>>() {
            @Override
            public Completable closeAsync() {
                return serviceDiscoverer.closeAsync();
            }

            @Override
            public Completable closeAsyncGracefully() {
                return serviceDiscoverer.closeAsyncGracefully();
            }

            @Override
            public Completable onClose() {
                return serviceDiscoverer.onClose();
            }

            @Override
            public Publisher<ServiceDiscovererEvent<InetSocketAddress>> discover(final HostAndPort hostAndPort) {
                return serviceDiscoverer.discover(hostAndPort.hostName()).map(originalEvent ->
                        new DefaultServiceDiscovererEvent<>(new InetSocketAddress(originalEvent.address(),
                                hostAndPort.port()), originalEvent.isAvailable())
                );
            }
        };
    }
}
