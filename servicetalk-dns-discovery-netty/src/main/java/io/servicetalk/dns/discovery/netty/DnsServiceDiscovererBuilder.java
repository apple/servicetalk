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

/**
 * Builder for <a href="https://tools.ietf.org/html/rfc1035">DNS</a> {@link ServiceDiscoverer} which will attempt to
 * resolve {@code A}, {@code AAAA}, {@code CNAME}, and {@code SRV} type queries.
 */
public interface DnsServiceDiscovererBuilder {
    /**
     * The minimum allowed TTL. This will be the minimum poll interval.
     *
     * @param minTTLSeconds The minimum amount of time a cache entry will be considered valid (in seconds).
     * @return {@code this}.
     */
    DnsServiceDiscovererBuilder minTTL(int minTTLSeconds);

    /**
     * The jitter to apply to schedule the next query after TTL.
     * <p>
     * The jitter value will be added on top of the TTL value returned from the DNS server to help spread out
     * subsequent DNS queries.
     *
     * @param ttlJitter The jitter to apply to schedule the next query after TTL.
     * @return {@code this}.
     */
    DnsServiceDiscovererBuilder ttlJitter(Duration ttlJitter);

    /**
     * Set the {@link DnsServerAddressStreamProvider} which determines which DNS server should be used per query.
     *
     * @param dnsServerAddressStreamProvider the {@link DnsServerAddressStreamProvider} which determines which DNS
     * server should be used per query.
     * @return {@code this}.
     */
    DnsServiceDiscovererBuilder dnsServerAddressStreamProvider(
            @Nullable DnsServerAddressStreamProvider dnsServerAddressStreamProvider);

    /**
     * Enable the automatic inclusion of a optional records that tries to give the remote DNS server a hint about
     * how much data the resolver can read per response. Some DNSServer may not support this and so fail to answer
     * queries. If you find problems you may want to disable this.
     *
     * @param optResourceEnabled if optional records inclusion is enabled.
     * @return {@code this}.
     */
    DnsServiceDiscovererBuilder optResourceEnabled(boolean optResourceEnabled);

    /**
     * Set the maximum size of the receiving UDP datagram (in bytes).
     * <p>
     * If the DNS response exceeds this amount the request will be automatically retried via TCP.
     *
     * @param maxUdpPayloadSize the maximum size of the receiving UDP datagram (in bytes)
     * @return {@code this}.
     */
    DnsServiceDiscovererBuilder maxUdpPayloadSize(int maxUdpPayloadSize);

    /**
     * Set the number of dots which must appear in a name before an initial absolute query is made.
     *
     * @param ndots the ndots value.
     * @return {@code this}.
     */
    DnsServiceDiscovererBuilder ndots(int ndots);

    /**
     * Sets the timeout of each DNS query performed by this service discoverer.
     *
     * @param queryTimeout the query timeout value
     * @return {@code this}.
     */
    DnsServiceDiscovererBuilder queryTimeout(Duration queryTimeout);

    /**
     * Sets the list of the protocol families of the address resolved.
     *
     * @param dnsResolverAddressTypes the address types or {@code null} to use the default value, based on "java.net"
     * system properties: {@code java.net.preferIPv4Stack} and {@code java.net.preferIPv6Stack}.
     * @return {@code this}.
     */
    DnsServiceDiscovererBuilder dnsResolverAddressTypes(
            @Nullable DnsResolverAddressTypes dnsResolverAddressTypes);

    /**
     * Sets the {@link IoExecutor}.
     *
     * @param ioExecutor {@link IoExecutor} to use.
     * @return {@code this}.
     */
    DnsServiceDiscovererBuilder ioExecutor(IoExecutor ioExecutor);

    /**
     * Sets a {@link DnsServiceDiscovererObserver} that provides visibility into
     * <a href="https://tools.ietf.org/html/rfc1034">DNS</a> {@link ServiceDiscoverer} built by this builder.
     *
     * @param observer a {@link DnsServiceDiscovererObserver} that provides visibility into
     * <a href="https://tools.ietf.org/html/rfc1034">DNS</a> {@link ServiceDiscoverer} built by this builder
     * @return {@code this}.
     */
    DnsServiceDiscovererBuilder observer(DnsServiceDiscovererObserver observer);

    /**
     * Sets which {@link ServiceDiscovererEvent.Status} to use in {@link ServiceDiscovererEvent#status()} when a record
     * for a previously seen address is missing in the response.
     *
     * @param status a {@link ServiceDiscovererEvent.Status} for missing records.
     * @return {@code this}.
     */
    DnsServiceDiscovererBuilder missingRecordStatus(ServiceDiscovererEvent.Status status);

    /**
     * Build a new {@link ServiceDiscoverer} which queries
     * <a href="https://tools.ietf.org/html/rfc2782">SRV Resource Records</a> corresponding to {@code serviceName}. For
     * each SRV answer capture the <strong>Port</strong> and resolve the <strong>Target</strong>.
     * @return a new {@link ServiceDiscoverer} which queries
     * <a href="https://tools.ietf.org/html/rfc2782">SRV Resource Records</a> corresponding to {@code serviceName}. For
     * each SRV answer capture the <strong>Port</strong> and resolve the <strong>Target</strong>.
     */
    ServiceDiscoverer<String, InetSocketAddress, ServiceDiscovererEvent<InetSocketAddress>> buildSrvDiscoverer();

    /**
     * Build a new {@link ServiceDiscoverer} which targets
     * <a href="https://tools.ietf.org/html/rfc1035">host addresses</a> (e.g. A or AAAA records) and uses
     * a fixed port derived from the {@link HostAndPort}.
     * @return a new {@link ServiceDiscoverer} which targets
     * <a href="https://tools.ietf.org/html/rfc1035">host addresses</a> (e.g. A or AAAA records) and uses
     * a fixed port derived from the {@link HostAndPort}.
     */
    ServiceDiscoverer<HostAndPort, InetSocketAddress, ServiceDiscovererEvent<InetSocketAddress>>
    buildARecordDiscoverer();
}
