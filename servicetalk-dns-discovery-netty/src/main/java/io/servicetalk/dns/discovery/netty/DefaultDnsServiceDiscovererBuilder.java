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

import io.servicetalk.client.api.ServiceDiscoverer;
import io.servicetalk.client.api.ServiceDiscovererEvent;
import io.servicetalk.concurrent.api.BiIntFunction;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.api.IoExecutor;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import javax.annotation.Nullable;

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
    @Nullable
    private Boolean optResourceEnabled;
    @Nullable
    private BiIntFunction<Throwable, Completable> retryStrategy;
    @Nullable
    private IoExecutor ioExecutor;
    private int minTTLSeconds = 2;

    /**
     * The minimum allowed TTL. This will be the minimum poll interval.
     *
     * @param minTTLSeconds The minimum amount of time a cache entry will be considered valid (in seconds).
     * @return {@code this}.
     */
    public DefaultDnsServiceDiscovererBuilder minTTL(int minTTLSeconds) {
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
            @Nullable DnsServerAddressStreamProvider dnsServerAddressStreamProvider) {
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
    public DefaultDnsServiceDiscovererBuilder optResourceEnabled(boolean optResourceEnabled) {
        this.optResourceEnabled = optResourceEnabled;
        return this;
    }

    /**
     * Set the number of dots which must appear in a name before an initial absolute query is made.
     *
     * @param ndots the ndots value.
     * @return {@code this}.
     */
    public DefaultDnsServiceDiscovererBuilder ndots(int ndots) {
        this.ndots = ndots;
        return this;
    }

    /**
     * Sets the list of the protocol families of the address resolved.
     *
     * @param dnsResolverAddressTypes the address types.
     * @return {@code this}.
     */
    public DefaultDnsServiceDiscovererBuilder dnsResolverAddressTypes(
            @Nullable DnsResolverAddressTypes dnsResolverAddressTypes) {
        this.dnsResolverAddressTypes = dnsResolverAddressTypes;
        return this;
    }

    /**
     * Configures retry strategy if DNS lookup fails.
     *
     * @param retryStrategy Retry strategy to use for retrying DNS lookup failures.
     * @return {@code this}.
     */
    public DefaultDnsServiceDiscovererBuilder retryDnsFailures(BiIntFunction<Throwable, Completable> retryStrategy) {
        this.retryStrategy = retryStrategy;
        return this;
    }

    /**
     * Sets the {@link IoExecutor}.
     *
     * @param ioExecutor {@link IoExecutor} to use.
     * @return {@code this}.
     */
    public DefaultDnsServiceDiscovererBuilder ioExecutor(IoExecutor ioExecutor) {
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
        return newDefaultDnsServiceDiscoverer().toHostAndPortDiscoverer();
    }

    private DefaultDnsServiceDiscoverer newDefaultDnsServiceDiscoverer() {
        return new DefaultDnsServiceDiscoverer(ioExecutor == null ? globalExecutionContext().ioExecutor() : ioExecutor,
                retryStrategy, minTTLSeconds, ndots, optResourceEnabled, dnsResolverAddressTypes,
                dnsServerAddressStreamProvider);
    }
}
