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
package io.servicetalk.dns.discovery.netty;

import io.servicetalk.client.api.ServiceDiscoverer;
import io.servicetalk.client.api.ServiceDiscovererEvent;
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.api.IoExecutor;

import java.net.InetSocketAddress;
import java.time.Duration;
import javax.annotation.Nullable;

import static java.util.Objects.requireNonNull;

/**
 * A {@link DnsServiceDiscovererBuilder} that delegates all methods to another {@link DnsServiceDiscovererBuilder}.
 */
public class DelegatingDnsServiceDiscovererBuilder implements DnsServiceDiscovererBuilder {

    private DnsServiceDiscovererBuilder delegate;

    /**
     * Creates a new builder which delegates to the provided {@link DnsServiceDiscovererBuilder}.
     *
     * @param delegate the delegate builder.
     */
    public DelegatingDnsServiceDiscovererBuilder(final DnsServiceDiscovererBuilder delegate) {
        this.delegate = requireNonNull(delegate);
    }

    /**
     * Returns the {@link DnsServiceDiscovererBuilder} delegate.
     *
     * @return Delegate {@link DnsServiceDiscovererBuilder}.
     */
    protected final DnsServiceDiscovererBuilder delegate() {
        return delegate;
    }

    @Override
    public DnsServiceDiscovererBuilder ttl(final int minSeconds, final int maxSeconds, final boolean cache) {
        delegate = delegate.ttl(minSeconds, maxSeconds, cache);
        return this;
    }

    @Override
    public DnsServiceDiscovererBuilder ttlJitter(final Duration ttlJitter) {
        delegate = delegate.ttlJitter(ttlJitter);
        return this;
    }

    @Override
    public DnsServiceDiscovererBuilder dnsServerAddressStreamProvider(
            @Nullable final DnsServerAddressStreamProvider dnsServerAddressStreamProvider) {
        delegate = delegate.dnsServerAddressStreamProvider(dnsServerAddressStreamProvider);
        return this;
    }

    @Override
    public DnsServiceDiscovererBuilder optResourceEnabled(final boolean optResourceEnabled) {
        delegate = delegate.optResourceEnabled(optResourceEnabled);
        return this;
    }

    @Override
    public DnsServiceDiscovererBuilder maxUdpPayloadSize(final int maxUdpPayloadSize) {
        delegate = delegate.maxUdpPayloadSize(maxUdpPayloadSize);
        return this;
    }

    @Override
    public DnsServiceDiscovererBuilder ndots(final int ndots) {
        delegate = delegate.ndots(ndots);
        return this;
    }

    @Override
    public DnsServiceDiscovererBuilder queryTimeout(final Duration queryTimeout) {
        delegate = delegate.queryTimeout(queryTimeout);
        return this;
    }

    @Override
    public DnsServiceDiscovererBuilder dnsResolverAddressTypes(
            @Nullable final DnsResolverAddressTypes dnsResolverAddressTypes) {
        delegate = delegate.dnsResolverAddressTypes(dnsResolverAddressTypes);
        return this;
    }

    @Override
    public DnsServiceDiscovererBuilder ioExecutor(final IoExecutor ioExecutor) {
        delegate = delegate.ioExecutor(ioExecutor);
        return this;
    }

    @Override
    public DnsServiceDiscovererBuilder observer(final DnsServiceDiscovererObserver observer) {
        delegate = delegate.observer(observer);
        return this;
    }

    @Override
    public DnsServiceDiscovererBuilder missingRecordStatus(final ServiceDiscovererEvent.Status status) {
        delegate = delegate.missingRecordStatus(status);
        return this;
    }

    @Override
    public ServiceDiscoverer<String, InetSocketAddress, ServiceDiscovererEvent<InetSocketAddress>>
    buildSrvDiscoverer() {
        return delegate.buildSrvDiscoverer();
    }

    @Override
    public ServiceDiscoverer<HostAndPort, InetSocketAddress, ServiceDiscovererEvent<InetSocketAddress>>
    buildARecordDiscoverer() {
        return delegate.buildARecordDiscoverer();
    }
}
