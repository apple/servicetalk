/*
 * Copyright © 2018, 2021 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.client.api.ServiceDiscoverer;
import io.servicetalk.client.api.ServiceDiscovererEvent;
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.api.IoExecutor;

import java.net.InetSocketAddress;
import java.time.Duration;
import javax.annotation.Nullable;

import static io.servicetalk.dns.discovery.netty.DnsClients.asHostAndPortDiscoverer;
import static io.servicetalk.dns.discovery.netty.DnsClients.asSrvDiscoverer;
import static io.servicetalk.transport.netty.internal.GlobalExecutionContext.globalExecutionContext;
import static java.time.Duration.ofSeconds;
import static java.util.Objects.requireNonNull;

/**
 * Builder for <a href="https://tools.ietf.org/html/rfc1035">DNS</a> {@link ServiceDiscoverer} which will attempt to
 * resolve {@code A}, {@code AAAA}, {@code CNAME}, and  {@code SRV} type queries.
 */
public final class DefaultDnsServiceDiscovererBuilder {
    @Nullable
    private DnsServerAddressStreamProvider dnsServerAddressStreamProvider;
    @Nullable
    private DnsResolverAddressTypes dnsResolverAddressTypes;
    @Nullable
    private Integer maxUdpPayloadSize;
    @Nullable
    private Integer ndots;
    @Nullable
    private Boolean optResourceEnabled;
    @Nullable
    private IoExecutor ioExecutor;
    @Nullable
    private Duration queryTimeout;
    private int minTTLSeconds = 10;
    private int srvConcurrency = 2048;
    private boolean inactiveEventsOnError;
    private boolean completeOncePreferredResolved = true;
    private boolean srvFilterDuplicateEvents;
    private Duration srvHostNameRepeatInitialDelay = ofSeconds(10);
    private Duration srvHostNameRepeatJitter = ofSeconds(5);
    @Nullable
    private DnsClientFilterFactory filterFactory;
    @Nullable
    private DnsServiceDiscovererObserver observer;

    /**
     * The minimum allowed TTL. This will be the minimum poll interval.
     *
     * @param minTTLSeconds The minimum amount of time a cache entry will be considered valid (in seconds).
     * @return {@code this}.
     */
    public DefaultDnsServiceDiscovererBuilder minTTL(final int minTTLSeconds) {
        if (minTTLSeconds <= 0) {
            throw new IllegalArgumentException("minTTLSeconds: " + minTTLSeconds + " (expected > 0)");
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
     * Set the maximum size of the receiving UDP datagram (in bytes).
     * <p>
     * If the DNS response exceeds this amount the request will be automatically retried via TCP.
     *
     * @param maxUdpPayloadSize the maximum size of the receiving UDP datagram (in bytes)
     * @return {@code this}.
     */
    public DefaultDnsServiceDiscovererBuilder maxUdpPayloadSize(final int maxUdpPayloadSize) {
        if (maxUdpPayloadSize <= 0) {
            throw new IllegalArgumentException("maxUdpPayloadSize: " + maxUdpPayloadSize + " (expected > 0)");
        }
        this.maxUdpPayloadSize = maxUdpPayloadSize;
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
     * Sets a {@link DnsServiceDiscovererObserver} that provides visibility into
     * <a href="https://tools.ietf.org/html/rfc1034">DNS</a> {@link ServiceDiscoverer} built by this builder.
     *
     * @param observer a {@link DnsServiceDiscovererObserver} that provides visibility into
     * <a href="https://tools.ietf.org/html/rfc1034">DNS</a> {@link ServiceDiscoverer} built by this builder
     * @return {@code this}.
     */
    public DefaultDnsServiceDiscovererBuilder observer(final DnsServiceDiscovererObserver observer) {
        this.observer = requireNonNull(observer);
        return this;
    }

    /**
     * Build a new {@link ServiceDiscoverer} which queries
     * <a href="https://tools.ietf.org/html/rfc2782">SRV Resource Records</a> corresponding to {@code serviceName}. For
     * each SRV answer capture the <strong>Port</strong> and resolve the <strong>Target</strong>.
     * @return a new {@link ServiceDiscoverer} which queries
     * <a href="https://tools.ietf.org/html/rfc2782">SRV Resource Records</a> corresponding to {@code serviceName}. For
     * each SRV answer capture the <strong>Port</strong> and resolve the <strong>Target</strong>.
     */
    public ServiceDiscoverer<String, InetSocketAddress, ServiceDiscovererEvent<InetSocketAddress>>
    buildSrvDiscoverer() {
        return asSrvDiscoverer(build());
    }

    /**
     * Build a new {@link ServiceDiscoverer} which targets
     * <a href="https://tools.ietf.org/html/rfc1035">host addresses</a> (e.g. A or AAAA records) and uses
     * a fixed port derived from the {@link HostAndPort}.
     * @return a new {@link ServiceDiscoverer} which targets
     * <a href="https://tools.ietf.org/html/rfc1035">host addresses</a> (e.g. A or AAAA records) and uses
     * a fixed port derived from the {@link HostAndPort}.
     */
    public ServiceDiscoverer<HostAndPort, InetSocketAddress, ServiceDiscovererEvent<InetSocketAddress>>
    buildARecordDiscoverer() {
        return asHostAndPortDiscoverer(build());
    }

    DefaultDnsServiceDiscovererBuilder inactiveEventsOnError(boolean inactiveEventsOnError) {
        this.inactiveEventsOnError = inactiveEventsOnError;
        return this;
    }

    DefaultDnsServiceDiscovererBuilder srvConcurrency(int srvConcurrency) {
        if (srvConcurrency <= 0) {
            throw new IllegalArgumentException("srvConcurrency: " + srvConcurrency + " (expected >0)");
        }
        this.srvConcurrency = srvConcurrency;
        return this;
    }

    DefaultDnsServiceDiscovererBuilder completeOncePreferredResolved(boolean completeOncePreferredResolved) {
        this.completeOncePreferredResolved = completeOncePreferredResolved;
        return this;
    }

    DefaultDnsServiceDiscovererBuilder srvHostNameRepeatDelay(
            Duration initialDelay, Duration jitter) {
        this.srvHostNameRepeatInitialDelay = requireNonNull(initialDelay);
        this.srvHostNameRepeatJitter = requireNonNull(jitter);
        return this;
    }

    DefaultDnsServiceDiscovererBuilder srvFilterDuplicateEvents(boolean srvFilterDuplicateEvents) {
        this.srvFilterDuplicateEvents = srvFilterDuplicateEvents;
        return this;
    }

    /**
     * Append the filter to the chain of filters used to decorate the {@link ServiceDiscoverer} created by this
     * builder.
     * <p>
     * Note this method will be used to decorate the result of {@link #build()} before it is returned to the user.
     * <p>
     * The order of execution of these filters are in order of append. If 3 filters are added as follows:
     * <pre>
     *     builder.append(filter1).append(filter2).append(filter3)
     * </pre>
     * making a request to a service discoverer wrapped by this filter chain the order of invocation of these filters
     * will be:
     * <pre>
     *     filter1 ⇒ filter2 ⇒ filter3 ⇒ service discoverer
     * </pre>
     *
     * @param factory {@link DnsClientFilterFactory} to decorate a {@link DnsClient} for the purpose of
     * filtering.
     * @return {@code this}
     */
    DefaultDnsServiceDiscovererBuilder appendFilter(final DnsClientFilterFactory factory) {
        requireNonNull(factory);
        filterFactory = appendFilter(filterFactory, factory);
        return this;
    }

    // Use another method to keep final references and avoid StackOverflowError
    private static DnsClientFilterFactory appendFilter(@Nullable final DnsClientFilterFactory current,
                                                       final DnsClientFilterFactory next) {
        return current == null ? next : dnsClient -> current.create(next.create(dnsClient));
    }

    /**
     * Create a new instance of {@link DnsClient}.
     *
     * @return a new instance of {@link DnsClient}.
     */
    DnsClient build() {
        final DnsClient rawClient = new DefaultDnsClient(
                ioExecutor == null ? globalExecutionContext().ioExecutor() : ioExecutor, minTTLSeconds, srvConcurrency,
                inactiveEventsOnError, completeOncePreferredResolved, srvFilterDuplicateEvents,
                srvHostNameRepeatInitialDelay, srvHostNameRepeatJitter, maxUdpPayloadSize, ndots, optResourceEnabled,
                queryTimeout, dnsResolverAddressTypes, dnsServerAddressStreamProvider, observer);
        return filterFactory == null ? rawClient : filterFactory.create(rawClient);
    }
}
