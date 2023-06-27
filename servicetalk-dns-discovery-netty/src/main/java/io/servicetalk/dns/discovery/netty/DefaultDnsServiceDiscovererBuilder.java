/*
 * Copyright © 2018, 2021-2022 Apple Inc. and the ServiceTalk project authors
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;

import static io.servicetalk.client.api.ServiceDiscovererEvent.Status.AVAILABLE;
import static io.servicetalk.client.api.ServiceDiscovererEvent.Status.EXPIRED;
import static io.servicetalk.dns.discovery.netty.DnsClients.asHostAndPortDiscoverer;
import static io.servicetalk.dns.discovery.netty.DnsClients.asSrvDiscoverer;
import static io.servicetalk.dns.discovery.netty.DnsResolverAddressTypes.systemDefault;
import static io.servicetalk.transport.netty.internal.GlobalExecutionContext.globalExecutionContext;
import static io.servicetalk.utils.internal.DurationUtils.ensurePositive;
import static java.lang.Boolean.getBoolean;
import static java.lang.Math.min;
import static java.time.Duration.ofSeconds;
import static java.util.Objects.requireNonNull;

/**
 * Builder for <a href="https://tools.ietf.org/html/rfc1035">DNS</a> {@link ServiceDiscoverer} which will attempt to
 * resolve {@code A}, {@code AAAA}, {@code CNAME}, and {@code SRV} type queries.
 *
 * @deprecated this class will be made package-private in the future, rely on the {@link DnsServiceDiscovererBuilder}
 * instead.
 */
@Deprecated // FIXME: 0.43 - make package private
public final class DefaultDnsServiceDiscovererBuilder implements DnsServiceDiscovererBuilder {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultDnsServiceDiscovererBuilder.class);

    /**
     * @deprecated This system property is introduced temporarily as a way for users to skip binding and revert the
     * preexisting behavior.
     */
    @Deprecated // FIXME: 0.43 - consider removing this system property
    private static final String SKIP_BINDING_PROPERTY = "io.servicetalk.dns.discovery.netty.skipBinding";
    @Nullable
    private static final SocketAddress DEFAULT_LOCAL_ADDRESS =
            getBoolean(SKIP_BINDING_PROPERTY) ? null : new InetSocketAddress(0);
    private static final DnsResolverAddressTypes DEFAULT_DNS_RESOLVER_ADDRESS_TYPES = systemDefault();
    private static final int DEFAULT_MIN_TTL_POLL_SECONDS = 10;
    private static final int DEFAULT_MAX_TTL_POLL_SECONDS = (int) TimeUnit.MINUTES.toSeconds(5);
    private static final int DEFAULT_MIN_TTL_CACHE_SECONDS = 0;
    private static final int DEFAULT_MAX_TTL_CACHE_SECONDS = 30;
    private static final int DEFAULT_TTL_POLL_JITTER_SECONDS = 4;
    private static final ServiceDiscovererEvent.Status DEFAULT_MISSING_RECOREDS_STATUS = EXPIRED;

    static {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("-D{}: {}", SKIP_BINDING_PROPERTY, getBoolean(SKIP_BINDING_PROPERTY));
            LOGGER.debug("Default local address to bind to: {}", DEFAULT_LOCAL_ADDRESS);
            LOGGER.debug("Default DnsResolverAddressTypes: {}", DEFAULT_DNS_RESOLVER_ADDRESS_TYPES);
            LOGGER.debug("Default TTL poll boundaries in seconds: [{}, {}]",
                    DEFAULT_MIN_TTL_POLL_SECONDS, DEFAULT_MAX_TTL_POLL_SECONDS);
            LOGGER.debug("Default TTL poll jitter seconds: {}", DEFAULT_TTL_POLL_JITTER_SECONDS);
            LOGGER.debug("Default TTL cache boundaries in seconds: [{}, {}]",
                    DEFAULT_MIN_TTL_CACHE_SECONDS, DEFAULT_MAX_TTL_CACHE_SECONDS);
            LOGGER.debug("Default missing records status: {}", DEFAULT_MISSING_RECOREDS_STATUS);
        }
    }

    private final String id;
    @Nullable
    private SocketAddress localAddress = DEFAULT_LOCAL_ADDRESS;
    @Nullable
    private DnsServerAddressStreamProvider dnsServerAddressStreamProvider;
    private DnsResolverAddressTypes dnsResolverAddressTypes = DEFAULT_DNS_RESOLVER_ADDRESS_TYPES;
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
    private int minTTLSeconds = DEFAULT_MIN_TTL_POLL_SECONDS;
    private int maxTTLSeconds = DEFAULT_MAX_TTL_POLL_SECONDS;
    private int minTTLCacheSeconds = DEFAULT_MIN_TTL_CACHE_SECONDS;
    private int maxTTLCacheSeconds = DEFAULT_MAX_TTL_CACHE_SECONDS;
    private Duration ttlJitter = ofSeconds(DEFAULT_TTL_POLL_JITTER_SECONDS);
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
    private ServiceDiscovererEvent.Status missingRecordStatus = DEFAULT_MISSING_RECOREDS_STATUS;

    /**
     * Creates a new {@link DefaultDnsServiceDiscovererBuilder}.
     *
     * @deprecated use {@link DnsServiceDiscoverers#builder(String)} instead.
     */
    @Deprecated // FIXME: 0.43 - remove deprecated constructor
    public DefaultDnsServiceDiscovererBuilder() {
        this("undefined");
    }

    DefaultDnsServiceDiscovererBuilder(final String id) {
        if (id.isEmpty()) {
            throw new IllegalArgumentException("id can not be empty");
        }
        this.id = id;
    }

    /**
     * The minimum allowed TTL. This will be the minimum poll interval.
     *
     * @param minTTLSeconds The minimum amount of time a cache entry will be considered valid (in seconds).
     * @return {@code this}.
     * @deprecated Use {@link #ttl(int, int)}.
     */
    @Deprecated
    public DefaultDnsServiceDiscovererBuilder minTTL(final int minTTLSeconds) {
        if (minTTLSeconds <= 0) {
            throw new IllegalArgumentException("minTTLSeconds: " + minTTLSeconds + " (expected > 0)");
        }
        this.minTTLSeconds = minTTLSeconds;
        return this;
    }

    @Override
    public DefaultDnsServiceDiscovererBuilder ttl(final int minSeconds, final int maxSeconds) {
        ttl(minSeconds, maxSeconds,
                min(minSeconds, DEFAULT_MIN_TTL_CACHE_SECONDS), min(maxSeconds, DEFAULT_MAX_TTL_CACHE_SECONDS));
        return this;
    }

    @Override
    public DefaultDnsServiceDiscovererBuilder ttl(final int minSeconds, final int maxSeconds,
                                                  final int minCacheSeconds, final int maxCacheSeconds) {
        if (minSeconds <= 0 || maxSeconds < minSeconds) {
            throw new IllegalArgumentException("minSeconds: " + minSeconds + ", maxSeconds: " + maxSeconds +
                    " (expected: 0 < minSeconds <= maxSeconds)");
        }
        if (minCacheSeconds < 0 || maxCacheSeconds < minCacheSeconds) {
            throw new IllegalArgumentException("minCacheSeconds: " + minCacheSeconds + ", maxCacheSeconds: " +
                    maxCacheSeconds + " (expected: 0 <= minCacheSeconds <= maxCacheSeconds)");
        }
        if (minCacheSeconds > minSeconds || maxCacheSeconds > maxSeconds) {
            throw new IllegalArgumentException("minCacheSeconds: " + minCacheSeconds +
                    ", maxCacheSeconds: " + maxCacheSeconds +
                    " (expected: 0 <= minCacheSeconds <= minSeconds(" + minSeconds +
                    ") <= maxCacheSeconds <= maxSeconds(" + maxSeconds + "))");
        }
        this.minTTLSeconds = minSeconds;
        this.maxTTLSeconds = maxSeconds;
        this.minTTLCacheSeconds = minCacheSeconds;
        this.maxTTLCacheSeconds = maxCacheSeconds;
        return this;
    }

    @Override
    public DefaultDnsServiceDiscovererBuilder ttlJitter(final Duration ttlJitter) {
        ensurePositive(ttlJitter, "jitter");
        this.ttlJitter = ttlJitter;
        return this;
    }

    @Override
    public DefaultDnsServiceDiscovererBuilder localAddress(@Nullable final SocketAddress localAddress) {
        this.localAddress = localAddress;
        return this;
    }

    @Override
    public DefaultDnsServiceDiscovererBuilder dnsServerAddressStreamProvider(
            @Nullable final DnsServerAddressStreamProvider dnsServerAddressStreamProvider) {
        this.dnsServerAddressStreamProvider = dnsServerAddressStreamProvider;
        return this;
    }

    @Override
    public DefaultDnsServiceDiscovererBuilder optResourceEnabled(final boolean optResourceEnabled) {
        this.optResourceEnabled = optResourceEnabled;
        return this;
    }

    @Override
    public DefaultDnsServiceDiscovererBuilder maxUdpPayloadSize(final int maxUdpPayloadSize) {
        if (maxUdpPayloadSize <= 0) {
            throw new IllegalArgumentException("maxUdpPayloadSize: " + maxUdpPayloadSize + " (expected > 0)");
        }
        this.maxUdpPayloadSize = maxUdpPayloadSize;
        return this;
    }

    @Override
    public DefaultDnsServiceDiscovererBuilder ndots(final int ndots) {
        this.ndots = ndots;
        return this;
    }

    @Override
    public DefaultDnsServiceDiscovererBuilder queryTimeout(final Duration queryTimeout) {
        this.queryTimeout = queryTimeout;
        return this;
    }

    @Override
    public DefaultDnsServiceDiscovererBuilder dnsResolverAddressTypes(
            @Nullable final DnsResolverAddressTypes dnsResolverAddressTypes) {
        this.dnsResolverAddressTypes = dnsResolverAddressTypes != null ? dnsResolverAddressTypes :
                systemDefault();
        return this;
    }

    @Override
    public DefaultDnsServiceDiscovererBuilder ioExecutor(final IoExecutor ioExecutor) {
        this.ioExecutor = ioExecutor;
        return this;
    }

    @Override
    public DefaultDnsServiceDiscovererBuilder observer(final DnsServiceDiscovererObserver observer) {
        this.observer = requireNonNull(observer);
        return this;
    }

    @Override
    public DefaultDnsServiceDiscovererBuilder missingRecordStatus(ServiceDiscovererEvent.Status status) {
        if (AVAILABLE.equals(status)) {
            throw new IllegalArgumentException(AVAILABLE + " status can not be used as missing records' status.");
        }
        this.missingRecordStatus = requireNonNull(status);
        return this;
    }

    @Override
    public ServiceDiscoverer<String, InetSocketAddress, ServiceDiscovererEvent<InetSocketAddress>>
    buildSrvDiscoverer() {
        return asSrvDiscoverer(build());
    }

    @Override
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
        final DnsClient rawClient = new DefaultDnsClient(id,
                ioExecutor == null ? globalExecutionContext().ioExecutor() : ioExecutor,
                minTTLSeconds, maxTTLSeconds, minTTLCacheSeconds, maxTTLCacheSeconds, ttlJitter.toNanos(),
                srvConcurrency, inactiveEventsOnError, completeOncePreferredResolved, srvFilterDuplicateEvents,
                srvHostNameRepeatInitialDelay, srvHostNameRepeatJitter, maxUdpPayloadSize, ndots, optResourceEnabled,
                queryTimeout, dnsResolverAddressTypes, localAddress, dnsServerAddressStreamProvider, observer,
                missingRecordStatus);
        return filterFactory == null ? rawClient : filterFactory.create(rawClient);
    }
}
