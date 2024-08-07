/*
 * Copyright © 2018, 2021-2023 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.client.api.ServiceDiscovererEvent;
import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.PublisherSource.Subscriber;
import io.servicetalk.concurrent.PublisherSource.Subscription;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.ListenableAsyncCloseable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.PublisherOperator;
import io.servicetalk.concurrent.api.TerminalSignalConsumer;
import io.servicetalk.concurrent.api.internal.SubscribablePublisher;
import io.servicetalk.concurrent.internal.CancelImmediatelySubscriber;
import io.servicetalk.concurrent.internal.DuplicateSubscribeException;
import io.servicetalk.concurrent.internal.RejectedSubscribeError;
import io.servicetalk.dns.discovery.netty.DnsServiceDiscovererObserver.DnsDiscoveryObserver;
import io.servicetalk.dns.discovery.netty.DnsServiceDiscovererObserver.DnsResolutionObserver;
import io.servicetalk.dns.discovery.netty.DnsServiceDiscovererObserver.ResolutionResult;
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.api.IoExecutor;
import io.servicetalk.transport.netty.internal.EventLoopAwareNettyIoExecutor;
import io.servicetalk.utils.internal.RandomUtils;

import io.netty.buffer.ByteBuf;
import io.netty.channel.EventLoop;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.dns.DefaultDnsQuestion;
import io.netty.handler.codec.dns.DnsRawRecord;
import io.netty.handler.codec.dns.DnsRecord;
import io.netty.resolver.ResolvedAddressTypes;
import io.netty.resolver.dns.DefaultAuthoritativeDnsServerCache;
import io.netty.resolver.dns.DefaultDnsCache;
import io.netty.resolver.dns.DefaultDnsCnameCache;
import io.netty.resolver.dns.DnsNameResolverBuilder;
import io.netty.resolver.dns.NameServerComparator;
import io.netty.resolver.dns.NoopAuthoritativeDnsServerCache;
import io.netty.resolver.dns.NoopDnsCache;
import io.netty.resolver.dns.NoopDnsCnameCache;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.ClosedChannelException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.RandomAccess;
import java.util.function.IntFunction;
import javax.annotation.Nullable;

import static io.netty.handler.codec.dns.DefaultDnsRecordDecoder.decodeName;
import static io.netty.handler.codec.dns.DnsRecordType.SRV;
import static io.netty.handler.codec.dns.DnsResponseCode.NXDOMAIN;
import static io.servicetalk.client.api.ServiceDiscovererEvent.Status.AVAILABLE;
import static io.servicetalk.concurrent.api.AsyncCloseables.toAsyncCloseable;
import static io.servicetalk.concurrent.api.Completable.completed;
import static io.servicetalk.concurrent.api.Publisher.defer;
import static io.servicetalk.concurrent.api.Publisher.empty;
import static io.servicetalk.concurrent.api.Publisher.failed;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.api.RepeatStrategies.repeatWithConstantBackoffDeltaJitter;
import static io.servicetalk.concurrent.internal.FlowControlUtils.addWithOverflowProtection;
import static io.servicetalk.concurrent.internal.SubscriberUtils.deliverErrorFromSource;
import static io.servicetalk.concurrent.internal.SubscriberUtils.handleExceptionFromOnSubscribe;
import static io.servicetalk.concurrent.internal.SubscriberUtils.isRequestNValid;
import static io.servicetalk.concurrent.internal.SubscriberUtils.newExceptionForInvalidRequestN;
import static io.servicetalk.concurrent.internal.SubscriberUtils.safeOnError;
import static io.servicetalk.concurrent.internal.ThrowableUtils.unknownStackTrace;
import static io.servicetalk.dns.discovery.netty.DnsClients.mapEventList;
import static io.servicetalk.dns.discovery.netty.DnsResolverAddressTypes.IPV4_PREFERRED;
import static io.servicetalk.dns.discovery.netty.DnsResolverAddressTypes.IPV6_PREFERRED;
import static io.servicetalk.dns.discovery.netty.DnsResolverAddressTypes.preferredAddressType;
import static io.servicetalk.dns.discovery.netty.DnsResolverAddressTypes.toRecordTypeNames;
import static io.servicetalk.dns.discovery.netty.ServiceDiscovererUtils.calculateDifference;
import static io.servicetalk.transport.netty.internal.BuilderUtils.datagramChannel;
import static io.servicetalk.transport.netty.internal.BuilderUtils.socketChannel;
import static io.servicetalk.transport.netty.internal.EventLoopAwareNettyIoExecutors.toEventLoopAwareNettyIoExecutor;
import static io.servicetalk.utils.internal.ThrowableUtils.addSuppressed;
import static java.lang.Integer.toHexString;
import static java.lang.System.identityHashCode;
import static java.nio.ByteBuffer.wrap;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static java.util.Comparator.comparing;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.function.Function.identity;

final class DefaultDnsClient implements DnsClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultDnsClient.class);
    private static final Comparator<InetAddress> INET_ADDRESS_COMPARATOR = comparing(o -> wrap(o.getAddress()));
    private static final Comparator<HostAndPort> HOST_AND_PORT_COMPARATOR = comparing(HostAndPort::hostName)
            .thenComparingInt(HostAndPort::port);
    private static final Cancellable TERMINATED = () -> { };

    private final EventLoopAwareNettyIoExecutor nettyIoExecutor;
    private final UnderlyingDnsResolver resolver;
    private final MinTtlCache ttlCache;
    private final long maxTTLNanos;
    private final long ttlJitterNanos;
    private final long resolutionTimeoutMillis;
    private final ListenableAsyncCloseable asyncCloseable;
    @Nullable
    private final DnsServiceDiscovererObserver observer;
    private final ServiceDiscovererEvent.Status missingRecordStatus;
    private final boolean nxInvalidation;
    private final IntFunction<? extends Completable> srvHostNameRepeater;
    private final int srvConcurrency;
    private final boolean srvFilterDuplicateEvents;
    private final DnsResolverAddressTypes addressTypes;
    private final String id;
    private boolean closed;

    DefaultDnsClient(final String id, final IoExecutor ioExecutor, final int consolidateCacheSize,
                     final int minTTL, final int maxTTL, final int minCacheTTL, final int maxCacheTTL,
                     final int negativeTTLCacheSeconds, final long ttlJitterNanos,
                     final int srvConcurrency,
                     final boolean completeOncePreferredResolved, final boolean srvFilterDuplicateEvents,
                     Duration srvHostNameRepeatInitialDelay, Duration srvHostNameRepeatJitter,
                     @Nullable Integer maxUdpPayloadSize, @Nullable final Integer ndots,
                     @Nullable final Boolean optResourceEnabled, @Nullable final Duration queryTimeout,
                     @Nullable Duration resolutionTimeout,
                     final DnsResolverAddressTypes dnsResolverAddressTypes,
                     @Nullable final SocketAddress localAddress,
                     @Nullable final DnsServerAddressStreamProvider dnsServerAddressStreamProvider,
                     @Nullable final DnsServiceDiscovererObserver observer,
                     final ServiceDiscovererEvent.Status missingRecordStatus,
                     final boolean nxInvalidation) {
        this.srvConcurrency = srvConcurrency;
        this.srvFilterDuplicateEvents = srvFilterDuplicateEvents;
        // Implementation of this class expects to use only single EventLoop from IoExecutor
        this.nettyIoExecutor = toEventLoopAwareNettyIoExecutor(ioExecutor).next();
        // We must use nettyIoExecutor for the repeater for thread safety!
        srvHostNameRepeater = repeatWithConstantBackoffDeltaJitter(
                srvHostNameRepeatInitialDelay, srvHostNameRepeatJitter, nettyIoExecutor);
        this.ttlCache = new MinTtlCache(
                maxCacheTTL == 0 && negativeTTLCacheSeconds == 0 ? NoopDnsCache.INSTANCE :
                        new DefaultDnsCache(minCacheTTL, maxCacheTTL, negativeTTLCacheSeconds),
                minTTL, nettyIoExecutor);
        this.maxTTLNanos = SECONDS.toNanos(maxTTL);
        this.ttlJitterNanos = ttlJitterNanos;
        this.addressTypes = dnsResolverAddressTypes;
        this.observer = observer;
        this.missingRecordStatus = missingRecordStatus;
        this.nxInvalidation = nxInvalidation;
        this.id = id + '@' + toHexString(identityHashCode(this));
        asyncCloseable = toAsyncCloseable(graceful -> {
            if (nettyIoExecutor.isCurrentThreadEventLoop()) {
                closeAsync0();
                return completed();
            }
            return nettyIoExecutor.submit(this::closeAsync0);
        });
        final EventLoop eventLoop = this.nettyIoExecutor.eventLoopGroup().next();
        @SuppressWarnings("unchecked")
        final Class<? extends SocketChannel> socketChannelClass =
                (Class<? extends SocketChannel>) socketChannel(eventLoop, InetSocketAddress.class);
        final ResolvedAddressTypes resolvedAddressTypes = DnsResolverAddressTypes.toNettyType(addressTypes);
        final DnsNameResolverBuilder builder = new DnsNameResolverBuilder(eventLoop)
                .localAddress(localAddress)
                .channelType(datagramChannel(eventLoop))
                .resolvedAddressTypes(resolvedAddressTypes)
                // Enable TCP fallback to be able to handle truncated responses.
                // https://tools.ietf.org/html/rfc7766
                .socketChannelType(socketChannelClass)
                // We should complete once the preferred address types could be resolved to ensure we always
                // respond as fast as possible.
                .completeOncePreferredResolved(completeOncePreferredResolved)
                // Configure caches. Because we wrap DnsCache to intercept TTL values, we have to configure the same
                // TTL limits for the other two caches (DnsCnameCache & AuthoritativeDnsServerCache).
                .resolveCache(ttlCache)
                .cnameCache(maxCacheTTL == 0 ? NoopDnsCnameCache.INSTANCE :
                        new DefaultDnsCnameCache(minCacheTTL, maxCacheTTL))
                .authoritativeDnsServerCache(maxCacheTTL == 0 ? NoopAuthoritativeDnsServerCache.INSTANCE :
                        new DefaultAuthoritativeDnsServerCache(minCacheTTL, maxCacheTTL,
                                // Use the same comparator as Netty uses by default.
                                new NameServerComparator(preferredAddressType(resolvedAddressTypes).addressType())));

        DnsNameResolverBuilderUtils.consolidateCacheSize(id, builder, consolidateCacheSize);
        if (queryTimeout != null) {
            builder.queryTimeoutMillis(queryTimeout.toMillis());
        }
        if (maxUdpPayloadSize != null) {
            builder.maxPayloadSize(maxUdpPayloadSize);
        }
        if (ndots != null) {
            builder.ndots(ndots);
        }
        if (optResourceEnabled != null) {
            builder.optResourceEnabled(optResourceEnabled);
        }
        if (dnsServerAddressStreamProvider != null) {
            builder.nameServerProvider(toNettyType(dnsServerAddressStreamProvider));
        }
        if (true /* hedging enabled */) { // need to wire this in.
            DnsNameResolverBuilderUtils.consolidateCacheSize(id, builder, 0);
            resolver = new HedgingDnsNameResolver(
//                    new UnderlyingDnsResolver.NettyDnsNameResolver(builder.build()), nettyIoExecutor);
                    // TODO: this is just for hacking together tests.
                    new UnderlyingDnsResolver.NettyDnsNameResolver(builder.build()), nettyIoExecutor,
                    HedgingDnsNameResolver.constantTracker(100), HedgingDnsNameResolver.alwaysAllowBudget());
        } else {
            resolver = new UnderlyingDnsResolver.NettyDnsNameResolver(builder.build());
        }
        this.resolutionTimeoutMillis = resolutionTimeout != null ? resolutionTimeout.toMillis() :
                // Default value is chosen based on a combination of default "timeout" and "attempts" options of
                // /etc/resolv.conf: https://man7.org/linux/man-pages/man5/resolv.conf.5.html
                resolver.queryTimeoutMillis() * 2;
    }

    @Override
    public String toString() {
        return id;
    }

    // visible for testing
    MinTtlCache ttlCache() {
        return ttlCache;
    }

    @Nullable
    private DnsDiscoveryObserver newDiscoveryObserver(final String address) {
        if (observer == null) {
            return null;
        }
        try {
            return observer.onNewDiscovery(id, address);
        } catch (Throwable unexpected) {
            LOGGER.warn("{} unexpected exception from {} while reporting new DNS discovery for {}",
                    this, observer, address, unexpected);
            return null;
        }
    }

    @Override
    public Publisher<Collection<ServiceDiscovererEvent<InetAddress>>> dnsQuery(final String address) {
        requireNonNull(address);
        return defer(() -> {
            final DnsDiscoveryObserver discoveryObserver = newDiscoveryObserver(address);
            ARecordPublisher pub = new ARecordPublisher(address, discoveryObserver);
            Publisher<? extends Collection<ServiceDiscovererEvent<InetAddress>>> events =
                    recoverWithInactiveEvents(pub, false, nxInvalidation);
            return discoveryObserver == null ? events : events.beforeFinally(new TerminalSignalConsumer() {
                    @Override
                    public void onComplete() {
                        // this event will never be triggered
                    }

                    @Override
                    public void onError(final Throwable cause) {
                        try {
                            discoveryObserver.discoveryFailed(cause);
                        } catch (Throwable unexpected) {
                            addSuppressed(unexpected, cause);
                            LOGGER.warn("{} Unexpected exception from observer while reporting discovery failure",
                                    DefaultDnsClient.this, unexpected);
                        }
                    }

                    @Override
                    public void cancel() {
                        try {
                            discoveryObserver.discoveryCancelled();
                        } catch (Throwable unexpected) {
                            LOGGER.warn("{} Unexpected exception from observer while reporting discovery cancellation",
                                    DefaultDnsClient.this, unexpected);
                        }
                    }
                });
        });
    }

    @Override
    public Publisher<Collection<ServiceDiscovererEvent<InetSocketAddress>>> dnsSrvQuery(final String serviceName) {
        requireNonNull(serviceName);
        return defer(() -> {
        // State per subscribe requires defer so each subscribe gets independent state.
        final Map<String, ARecordPublisher> aRecordMap = new HashMap<>(8);
        final Map<InetSocketAddress, Integer> availableAddresses = srvFilterDuplicateEvents ?
                new HashMap<>(8) : emptyMap();
        final DnsDiscoveryObserver discoveryObserver = newDiscoveryObserver(serviceName);
        // We "recover" unconditionally to force inactive events to propagate to all mapped A* publishers to cancel
        // any pending scheduled tasks. SrvInactiveCombinerOperator is used to filter the aggregated collection of
        // inactive events if necessary.
        Publisher<Collection<ServiceDiscovererEvent<InetSocketAddress>>> events =
                recoverWithInactiveEvents(new SrvRecordPublisher(serviceName, discoveryObserver), true, nxInvalidation)
                .flatMapConcatIterable(identity())
                .flatMapMerge(srvEvent -> {
                assertInEventloop();
                if (AVAILABLE.equals(srvEvent.status())) {
                    return defer(() -> {
                        final ARecordPublisher aPublisher =
                                new ARecordPublisher(srvEvent.address().hostName(), discoveryObserver);
                        final ARecordPublisher prevAPublisher = aRecordMap.putIfAbsent(srvEvent.address().hostName(),
                                aPublisher);
                        if (prevAPublisher != null) {
                            return newDuplicateSrv(serviceName, srvEvent.address().hostName());
                        }

                        // NXDOMAIN = invalidation for A queries part of SRV lookups for backwards compatibility.
                        // This is a behavior difference between plain A lookups and SRV rooted A lookups.
                        Publisher<? extends Collection<ServiceDiscovererEvent<InetAddress>>> returnPub =
                                recoverWithInactiveEvents(aPublisher, false, true);
                        return srvFilterDuplicateEvents ?
                                srvFilterDups(returnPub, availableAddresses, srvEvent.address().port()) :
                                returnPub.map(ev -> mapEventList(ev, inetAddress ->
                                        new InetSocketAddress(inetAddress, srvEvent.address().port())));
                    }).retryWhen(false, (i, cause) -> {
                        assertInEventloop();
                        // If this error is because the SRV entry was detected as inactive, then propagate the error and
                        // don't retry. Otherwise this is a resolution exception (e.g. UnknownHostException), and retry.
                        return cause.getClass().equals(SrvAddressRemovedException.class) ||
                                aRecordMap.remove(srvEvent.address().hostName()) == null ?
                                Completable.failed(cause) : srvHostNameRepeater.apply(i);
                    }).onErrorComplete(); // retryWhen will propagate onError, but we don't want this.
                } else if (srvEvent instanceof SrvInactiveEvent) {
                    // Unwrap the list so we can use it in SrvInactiveCombinerOperator below.
                    return from(((SrvInactiveEvent<HostAndPort, InetSocketAddress>) srvEvent).aggregatedEvents);
                } else {
                    final ARecordPublisher aPublisher = aRecordMap.remove(srvEvent.address().hostName());
                    if (aPublisher != null) {
                        aPublisher.cancelAndFail0(
                                SrvAddressRemovedException.newInstance(DefaultDnsClient.class, "dnsSrvQuery"));
                    }
                    return empty();
                }
            }, srvConcurrency)
            .liftSync(SrvInactiveCombinerOperator.EMIT);

            return discoveryObserver == null ? events : events.beforeFinally(new TerminalSignalConsumer() {
                @Override
                public void onComplete() {
                    // this event will never be triggered
                }

                @Override
                public void onError(final Throwable cause) {
                    try {
                        discoveryObserver.discoveryFailed(cause);
                    } catch (Throwable unexpected) {
                        addSuppressed(unexpected, cause);
                        LOGGER.warn("{} Unexpected exception from observer while reporting discovery failure",
                                DefaultDnsClient.this, unexpected);
                    }
                }

                @Override
                public void cancel() {
                    try {
                        discoveryObserver.discoveryCancelled();
                    } catch (Throwable unexpected) {
                        LOGGER.warn("{} Unexpected exception from observer while reporting discovery cancellation",
                                DefaultDnsClient.this, unexpected);
                    }
                }
            });
        });
    }

    @Override
    public Completable onClose() {
        return asyncCloseable.onClose();
    }

    @Override
    public Completable onClosing() {
        return asyncCloseable.onClosing();
    }

    @Override
    public Completable closeAsync() {
        return asyncCloseable.closeAsync();
    }

    @Override
    public Completable closeAsyncGracefully() {
        return asyncCloseable.closeAsyncGracefully();
    }

    private void closeAsync0() {
        assertInEventloop();

        if (closed) {
            return;
        }
        closed = true;
        resolver.close();
        ttlCache.clear();
    }

    private void assertInEventloop() {
        assert nettyIoExecutor.isCurrentThreadEventLoop();
    }

    private final class SrvRecordPublisher extends AbstractDnsPublisher<HostAndPort> {
        private SrvRecordPublisher(final String serviceName, @Nullable final DnsDiscoveryObserver discoveryObserver) {
            super(serviceName, discoveryObserver);
        }

        @Override
        public String toString() {
            return "SRV records of " + name + " @" + toHexString(hashCode());
        }

        @Override
        protected AbstractDnsSubscription newSubscription(
                final Subscriber<? super List<ServiceDiscovererEvent<HostAndPort>>> subscriber) {
            return new AbstractDnsSubscription(subscriber) {
                @Override
                protected Future<DnsAnswer<HostAndPort>> doDnsQuery(final boolean scheduledQuery) {
                    final EventLoop eventLoop = nettyIoExecutor.eventLoopGroup().next();
                    final Promise<DnsAnswer<HostAndPort>> promise = eventLoop.newPromise();
                    final Future<List<DnsRecord>> resolveFuture =
                            resolver.resolveAllQuestion(new DefaultDnsQuestion(name, SRV));
                    final Future<?> timeoutFuture = resolutionTimeoutMillis == 0L ? null : eventLoop.schedule(() -> {
                        if (!promise.isDone() && promise.tryFailure(DnsNameResolverTimeoutException.newInstance(
                                name, resolutionTimeoutMillis, SRV.toString(),
                                SrvRecordPublisher.class, "doDnsQuery"))) {
                            resolveFuture.cancel(true);
                        }
                    }, resolutionTimeoutMillis, MILLISECONDS);
                    resolveFuture.addListener((Future<? super List<DnsRecord>> completedFuture) -> {
                                if (timeoutFuture != null) {
                                    timeoutFuture.cancel(true);
                                }
                                Throwable cause = completedFuture.cause();
                                if (cause != null) {
                                    promise.tryFailure(cause);
                                } else {
                                    final DnsAnswer<HostAndPort> dnsAnswer;
                                    long minTTLSeconds = Long.MAX_VALUE;
                                    List<DnsRecord> toRelease = null;
                                    try {
                                        @SuppressWarnings("unchecked")
                                        final List<DnsRecord> dnsRecords = (List<DnsRecord>) completedFuture.getNow();
                                        toRelease = dnsRecords;
                                        final List<HostAndPort> hostAndPorts = new ArrayList<>(dnsRecords.size());
                                        for (DnsRecord dnsRecord : dnsRecords) {
                                            if (!SRV.equals(dnsRecord.type()) || !(dnsRecord instanceof DnsRawRecord)) {
                                                throw new IllegalArgumentException(
                                                        "Unsupported DNS record type for SRV query: " + dnsRecord);
                                            }
                                            if (dnsRecord.timeToLive() < minTTLSeconds) {
                                                minTTLSeconds = dnsRecord.timeToLive();
                                            }
                                            ByteBuf content = ((DnsRawRecord) dnsRecord).content();
                                            // https://tools.ietf.org/html/rfc2782
                                            // Priority(16u) Weight(16u) Port(16u) Target(varstring)
                                            content.skipBytes(4); // ignore Priority and Weight for now
                                            final int port = content.readUnsignedShort();
                                            hostAndPorts.add(HostAndPort.of(decodeName(content), port));
                                        }
                                        LOGGER.trace("{} original result for {} (size={}, TTL={}s): {}.",
                                                DefaultDnsClient.this, SrvRecordPublisher.this,
                                                toRelease.size(), minTTLSeconds, toRelease);
                                        dnsAnswer = new DnsAnswer<>(hostAndPorts, SECONDS.toNanos(minTTLSeconds));
                                    } catch (Throwable cause2) {
                                        promise.tryFailure(cause2);
                                        return;
                                    } finally {
                                        if (toRelease != null) {
                                            for (DnsRecord dnsRecord : toRelease) {
                                                ReferenceCountUtil.release(dnsRecord);
                                            }
                                        }
                                    }
                                    promise.trySuccess(dnsAnswer);
                                }
                            });
                    return promise;
                }

                @Override
                protected Comparator<HostAndPort> comparator() {
                    return HOST_AND_PORT_COMPARATOR;
                }
            };
        }
    }

    private class ARecordPublisher extends AbstractDnsPublisher<InetAddress> {
        ARecordPublisher(final String inetHost, @Nullable final DnsDiscoveryObserver discoveryObserver) {
            super(inetHost, discoveryObserver);
        }

        @Override
        public String toString() {
            return "A* records of " + name + " @" + toHexString(hashCode());
        }

        @Override
        protected AbstractDnsSubscription newSubscription(
                final Subscriber<? super List<ServiceDiscovererEvent<InetAddress>>> subscriber) {
            return new AbstractDnsSubscription(subscriber) {
                @Override
                protected Future<DnsAnswer<InetAddress>> doDnsQuery(final boolean scheduledQuery) {
                    if (scheduledQuery) {
                        ttlCache.prepareForResolution(name);
                    }
                    final EventLoop eventLoop = nettyIoExecutor.eventLoopGroup().next();
                    final Promise<DnsAnswer<InetAddress>> dnsAnswerPromise = eventLoop.newPromise();
                    final Future<List<InetAddress>> resolveFuture = resolver.resolveAll(name);
                    final Future<?> timeoutFuture = resolutionTimeoutMillis == 0L ? null : eventLoop.schedule(() -> {
                        if (!dnsAnswerPromise.isDone() && dnsAnswerPromise.tryFailure(
                                DnsNameResolverTimeoutException.newInstance(name, resolutionTimeoutMillis,
                                        toRecordTypeNames(addressTypes), ARecordPublisher.class, "doDnsQuery"))) {
                            resolveFuture.cancel(true);
                        }
                    }, resolutionTimeoutMillis, MILLISECONDS);

                    resolveFuture.addListener(completedFuture -> {
                        if (timeoutFuture != null) {
                            timeoutFuture.cancel(true);
                        }
                        Throwable cause = completedFuture.cause();
                        if (cause != null) {
                            dnsAnswerPromise.tryFailure(cause);
                        } else {
                            final DnsAnswer<InetAddress> dnsAnswer;
                            @SuppressWarnings("unchecked")
                            final List<InetAddress> original = (List<InetAddress>) completedFuture.getNow();
                            final long minTTLSeconds = ttlCache.minTtl(name);
                            LOGGER.trace("{} original result for {} (size={}, TTL={}s): {}.",
                                    DefaultDnsClient.this, ARecordPublisher.this,
                                    original.size(), minTTLSeconds, original);
                            try {
                                dnsAnswer = new DnsAnswer<>(toAddresses(original), SECONDS.toNanos(minTTLSeconds));
                            } catch (Throwable cause2) {
                                dnsAnswerPromise.tryFailure(cause2);
                                return;
                            }
                            dnsAnswerPromise.trySuccess(dnsAnswer);
                        }
                    });
                    return dnsAnswerPromise;
                }

                @Override
                protected Comparator<InetAddress> comparator() {
                    return INET_ADDRESS_COMPARATOR;
                }

                private List<InetAddress> toAddresses(final List<InetAddress> original) {
                    if (addressTypes == IPV4_PREFERRED || addressTypes == IPV6_PREFERRED) {
                        // Filter out addresses to keep only preferred if both available.
                        int ipv4Cnt = 0;
                        int ipv6Cnt = 0;
                        for (InetAddress address : original) {
                            if (address instanceof Inet4Address) {
                                ++ipv4Cnt;
                            } else {
                                assert address instanceof Inet6Address;
                                ++ipv6Cnt;
                            }
                        }
                        if (ipv4Cnt > 0 && ipv6Cnt > 0) {
                            final int capacity = addressTypes == IPV4_PREFERRED ? ipv4Cnt : ipv6Cnt;
                            final List<InetAddress> result = new ArrayList<>(capacity);
                            for (InetAddress address : original) {
                                if ((addressTypes == IPV4_PREFERRED && address instanceof Inet4Address) ||
                                        (addressTypes == IPV6_PREFERRED && address instanceof Inet6Address)) {
                                    result.add(address);
                                }
                            }
                            assert result.size() == capacity;
                            return result;
                        }
                    }
                    // Make a copy of the address List in-case the underlying cache modifies the List we
                    // can avoid a ConcurrentModificationException.
                    return new ArrayList<>(original);
                }
            };
        }
    }

    private static final class DnsAnswer<T> {
        private final List<T> answer;
        private final long ttlNanos;

        DnsAnswer(final List<T> answer, final long ttlNanos) {
            this.answer = answer;
            this.ttlNanos = ttlNanos;
        }

        List<T> answer() {
            return answer;
        }

        long ttlNanos() {
            return ttlNanos;
        }
    }

    private abstract class AbstractDnsPublisher<T>
            extends SubscribablePublisher<List<ServiceDiscovererEvent<T>>> {
        /**
         * Name of the DNS record to query.
         */
        protected final String name;
        /**
         * A {@link DnsDiscoveryObserver} for this publisher that provides visibility into individual DNS resolutions.
         */
        @Nullable
        protected final DnsDiscoveryObserver discoveryObserver;
        @Nullable
        AbstractDnsSubscription subscription;

        AbstractDnsPublisher(final String name, @Nullable final DnsDiscoveryObserver discoveryObserver) {
            this.name = name;
            this.discoveryObserver = discoveryObserver;
            LOGGER.debug("{} initializing a new publisher for {}.", DefaultDnsClient.this, this);
        }

        /**
         * Creates a new {@link Subscription} for this {@link Publisher}.
         *
         * @param subscriber {@link Subscriber} for a new {@link Subscription}
         * @return a new {@link Subscription} for this {@link Publisher}
         */
        protected abstract AbstractDnsSubscription newSubscription(
                Subscriber<? super List<ServiceDiscovererEvent<T>>> subscriber);

        @Override
        protected final void handleSubscribe(
                final Subscriber<? super List<ServiceDiscovererEvent<T>>> subscriber) {
            if (nettyIoExecutor.isCurrentThreadEventLoop()) {
                handleSubscribe0(subscriber);
            } else {
                nettyIoExecutor.execute(() -> handleSubscribe0(subscriber));
            }
        }

        private void handleSubscribe0(
                final Subscriber<? super List<ServiceDiscovererEvent<T>>> subscriber) {
            assertInEventloop();

            if (subscription != null) {
                deliverErrorFromSource(subscriber, new DuplicateSubscribeException(subscription, subscriber));
            } else if (closed) {
                deliverErrorFromSource(subscriber, new ClosedDnsServiceDiscovererException());
            } else {
                subscription = newSubscription(subscriber);
                try {
                    subscriber.onSubscribe(subscription);
                } catch (Throwable cause) {
                    handleExceptionFromOnSubscribe(subscriber, cause);
                }
            }
        }

        final void cancelAndFail0(Throwable cause) {
            assertInEventloop();
            if (subscription != null) {
                subscription.cancelAndTerminate0(cause);
            } else {
                subscription = newSubscription(CancelImmediatelySubscriber.INSTANCE);
            }
        }

        abstract class AbstractDnsSubscription implements Subscription {
            private final Subscriber<? super List<ServiceDiscovererEvent<T>>> subscriber;
            private long pendingRequests;
            private List<T> activeAddresses;
            private long resolveDoneNoScheduleTime;
            @Nullable
            private Cancellable cancellableForQuery;
            private long ttlNanos;

            AbstractDnsSubscription(final Subscriber<? super List<ServiceDiscovererEvent<T>>> subscriber) {
                this.subscriber = subscriber;
                activeAddresses = emptyList();
                ttlNanos = -1;
            }

            /**
             * Performs DNS query.
             *
             * @param scheduledQuery indicates when query was scheduled
             * @return a {@link Future} that will be notified when {@link DnsAnswer} is available
             */
            protected abstract Future<DnsAnswer<T>> doDnsQuery(boolean scheduledQuery);

            /**
             * Returns a {@link Comparator} for the resolved address type.
             *
             * @return a {@link Comparator} for the resolved address type
             */
            protected abstract Comparator<T> comparator();

            /**
             * Returns {@link ServiceDiscovererEvent.Status} to use for {@link ServiceDiscovererEvent#status()}
             * when a record for previously seen address is missing in the response.
             *
             * @return a {@link ServiceDiscovererEvent.Status} for missing records.
             */
            protected final ServiceDiscovererEvent.Status missingRecordStatus() {
                return DefaultDnsClient.this.missingRecordStatus;
            }

            @Override
            public final void request(final long n) {
                if (nettyIoExecutor.isCurrentThreadEventLoop()) {
                    request0(n);
                } else {
                    nettyIoExecutor.execute(() -> request0(n));
                }
            }

            @Override
            public final void cancel() {
                if (nettyIoExecutor.isCurrentThreadEventLoop()) {
                    cancel0();
                } else {
                    nettyIoExecutor.execute(this::cancel0);
                }
            }

            private void request0(final long n) {
                assertInEventloop();

                if (!isRequestNValid(n)) {
                    handleTerminalError0(newExceptionForInvalidRequestN(n));
                    return;
                }

                pendingRequests = addWithOverflowProtection(pendingRequests, n);
                if (cancellableForQuery == null) {
                    if (ttlNanos < 0) {
                        doQuery0(false);
                    } else {
                        final long durationNs =
                                nettyIoExecutor.currentTime(NANOSECONDS) - resolveDoneNoScheduleTime;
                        if (durationNs > ttlNanos) {
                            doQuery0(false);
                        } else {
                            scheduleQuery0(ttlNanos - durationNs, ttlNanos);
                        }
                    }
                }
            }

            private void executeScheduledQuery0() {
                doQuery0(true);
            }

            private void doQuery0(final boolean scheduledQuery) {
                assertInEventloop();

                if (closed) {
                    // best effort check to cleanup state after close.
                    handleTerminalError0(new ClosedDnsServiceDiscovererException());
                } else {
                    final DnsResolutionObserver resolutionObserver = newResolutionObserver();
                    LOGGER.trace("{} querying DNS for {}.", DefaultDnsClient.this, AbstractDnsPublisher.this);
                    final Future<DnsAnswer<T>> addressFuture = doDnsQuery(scheduledQuery);
                    cancellableForQuery = () -> addressFuture.cancel(true);
                    if (addressFuture.isDone()) {
                        handleResolveDone0(addressFuture, resolutionObserver);
                    } else {
                        addressFuture.addListener((FutureListener<DnsAnswer<T>>) f ->
                                handleResolveDone0(f, resolutionObserver));
                    }
                }
            }

            @Nullable
            private DnsResolutionObserver newResolutionObserver() {
                final DnsDiscoveryObserver discoveryObserver = AbstractDnsPublisher.this.discoveryObserver;
                if (discoveryObserver == null) {
                    return null;
                }
                try {
                    return discoveryObserver.onNewResolution(name);
                } catch (Throwable unexpected) {
                    LOGGER.warn("{} unexpected exception from {} while reporting new DNS resolution for: {}",
                            DefaultDnsClient.this, observer, name, unexpected);
                    return null;
                }
            }

            private void cancel0() {
                assertInEventloop();
                LOGGER.debug("{} subscription for {} is cancelled.", DefaultDnsClient.this, AbstractDnsPublisher.this);
                Cancellable oldCancellable = cancellableForQuery;
                cancellableForQuery = TERMINATED;
                if (oldCancellable != null) {
                    oldCancellable.cancel();
                }
            }

            private void cancelAndTerminate0(Throwable cause) {
                assertInEventloop();
                LOGGER.debug("{} subscription for {} will be cancelled and terminated with an error.",
                        DefaultDnsClient.this, AbstractDnsPublisher.this, cause);
                try {
                    cancel0();
                } finally {
                    safeOnError(subscriber, cause);
                }
            }

            private void scheduleQuery0(final long remainingTtlNanos) {
                scheduleQuery0(remainingTtlNanos, remainingTtlNanos);
            }

            private void scheduleQuery0(final long remainingTtlNanos, final long originalTtlNanos) {
                assertInEventloop();

                final long delay = RandomUtils.nextLongInclusive(remainingTtlNanos,
                                addWithOverflowProtection(remainingTtlNanos, ttlJitterNanos));
                LOGGER.debug("{} scheduling DNS query for {} after {}ms (TTL={}s, jitter={}ms).",
                        DefaultDnsClient.this, AbstractDnsPublisher.this, NANOSECONDS.toMillis(delay),
                        NANOSECONDS.toSeconds(originalTtlNanos), NANOSECONDS.toMillis(ttlJitterNanos));

                // This value is coming from DNS TTL for which the unit is seconds and the minimum value we accept
                // in the builder is 1 second.
                cancellableForQuery = nettyIoExecutor.schedule(this::executeScheduledQuery0, delay, NANOSECONDS);
            }

            private void handleResolveDone0(final Future<DnsAnswer<T>> addressFuture,
                                            @Nullable final DnsResolutionObserver resolutionObserver) {
                assertInEventloop();
                assert pendingRequests > 0;
                if (cancellableForQuery == TERMINATED) {
                    return;
                }
                final Throwable cause = addressFuture.cause();
                if (cause != null) {
                    reportResolutionFailed(resolutionObserver, cause);
                    cancelAndTerminate0(cause);
                } else {
                    // DNS lookup can return duplicate InetAddress
                    final DnsAnswer<T> dnsAnswer = addressFuture.getNow();
                    final List<T> addresses = dnsAnswer.answer();
                    ttlNanos = dnsAnswer.ttlNanos();
                    if (ttlNanos > maxTTLNanos) {
                        if (LOGGER.isDebugEnabled()) {
                            LOGGER.debug("{} result for {} has TTL={} > maxTTL={}",
                                    DefaultDnsClient.this, AbstractDnsPublisher.this, NANOSECONDS.toSeconds(ttlNanos),
                                    NANOSECONDS.toSeconds(maxTTLNanos));
                        }
                        ttlNanos = maxTTLNanos;
                    }
                    final List<ServiceDiscovererEvent<T>> events = calculateDifference(activeAddresses, addresses,
                            comparator(), resolutionObserver == null ? null : (nAvailable, nMissing) ->
                                    reportResolutionResult(resolutionObserver, new DefaultResolutionResult(
                                            addresses.size(), (int) NANOSECONDS.toSeconds(ttlNanos),
                                            nAvailable, nMissing)),
                            missingRecordStatus);

                    if (events != null) {
                        activeAddresses = addresses;
                        if (--pendingRequests > 0) {
                            scheduleQuery0(ttlNanos);
                        } else {
                            resolveDoneNoScheduleTime = nettyIoExecutor.currentTime(NANOSECONDS);
                            cancellableForQuery = null;
                        }
                        try {
                            // Shuffle events to avoid multiple clients connecting to the same upstream IP address if
                            // they receive identical result set from DNS.
                            Collections.shuffle(events);

                            LOGGER.debug("{} sending events for {} (size={}, TTL={}s): {}.",
                                    DefaultDnsClient.this, AbstractDnsPublisher.this, events.size(),
                                    NANOSECONDS.toSeconds(ttlNanos), events);

                            subscriber.onNext(events);
                        } catch (final Throwable error) {
                            handleTerminalError0(error);
                        }
                    } else {
                        LOGGER.trace("{} resolution is complete but no changes detected for {} based on result " +
                                        "(size={}, TTL={}s) {}.",
                                DefaultDnsClient.this, AbstractDnsPublisher.this, activeAddresses.size(),
                                NANOSECONDS.toSeconds(ttlNanos), activeAddresses);

                        scheduleQuery0(ttlNanos);
                    }
                }
            }

            private void reportResolutionFailed(@Nullable final DnsResolutionObserver resolutionObserver,
                                                final Throwable cause) {
                if (resolutionObserver == null) {
                    return;
                }
                try {
                    resolutionObserver.resolutionFailed(cause);
                } catch (Throwable unexpected) {
                    addSuppressed(unexpected, cause);
                    LOGGER.warn("{} unexpected exception from {} while reporting DNS resolution failure",
                            DefaultDnsClient.this, resolutionObserver, unexpected);
                }
            }

            private void reportResolutionResult(final DnsResolutionObserver resolutionObserver,
                                                final ResolutionResult result) {
                try {
                    resolutionObserver.resolutionCompleted(result);
                } catch (Throwable unexpected) {
                    LOGGER.warn("{} unexpected exception from {} while reporting DNS resolution result {}",
                            DefaultDnsClient.this, resolutionObserver, result, unexpected);
                }
            }

            private void handleTerminalError0(final Throwable cause) {
                assertInEventloop();
                if (cancellableForQuery != TERMINATED) {
                    cancelAndTerminate0(cause);
                }
            }

            @SuppressWarnings("ForLoopReplaceableByForEach")
            private List<ServiceDiscovererEvent<T>> generateInactiveEvent() {
                final List<ServiceDiscovererEvent<T>> events = new ArrayList<>(activeAddresses.size());
                if (activeAddresses instanceof RandomAccess) {
                    for (int i = 0; i < activeAddresses.size(); ++i) {
                        events.add(new DefaultServiceDiscovererEvent<>(activeAddresses.get(i), missingRecordStatus));
                    }
                } else {
                    for (final T address : activeAddresses) {
                        events.add(new DefaultServiceDiscovererEvent<>(address, missingRecordStatus));
                    }
                }
                activeAddresses = emptyList();
                return events;
            }
        }
    }

    private static Publisher<? extends Collection<ServiceDiscovererEvent<InetSocketAddress>>> srvFilterDups(
            Publisher<? extends Collection<ServiceDiscovererEvent<InetAddress>>> returnPub,
            Map<InetSocketAddress, Integer> availableAddresses, int port) {
        return returnPub.map(events -> {
            ArrayList<ServiceDiscovererEvent<InetSocketAddress>> mappedEvents = new ArrayList<>(events.size());
            for (ServiceDiscovererEvent<InetAddress> event : events) {
                InetSocketAddress addr = new InetSocketAddress(event.address(), port);
                final ServiceDiscovererEvent.Status status = event.status();
                Integer count = availableAddresses.get(addr);
                if (AVAILABLE.equals(status)) {
                    if (count == null) {
                        mappedEvents.add(new DefaultServiceDiscovererEvent<>(addr, status));
                        availableAddresses.put(addr, 1);
                    } else {
                        availableAddresses.put(addr, count + 1);
                    }
                } else {
                    if (count == null) {
                        throw new IllegalStateException("null count for: " + addr);
                    }
                    if (count == 1) {
                        mappedEvents.add(new DefaultServiceDiscovererEvent<>(addr, status));
                        availableAddresses.remove(addr);
                    } else {
                        availableAddresses.put(addr, count - 1);
                    }
                }
            }
            return mappedEvents;
        }).filter(events -> !events.isEmpty());
    }

    private static <T, A> Publisher<? extends Collection<ServiceDiscovererEvent<T>>> recoverWithInactiveEvents(
            AbstractDnsPublisher<T> pub, boolean generateAggregateEvent, boolean nxInvalidation) {
        return pub.onErrorResume(cause -> {
            AbstractDnsPublisher<T>.AbstractDnsSubscription subscription = pub.subscription;
            if (subscription != null && shouldRevokeState(cause, nxInvalidation)) {
                List<ServiceDiscovererEvent<T>> events = subscription.generateInactiveEvent();
                if (!events.isEmpty()) {
                    return (generateAggregateEvent ? Publisher.<List<ServiceDiscovererEvent<T>>>from(
                            singletonList(new SrvInactiveEvent<T, A>(subscription.missingRecordStatus())), events)
                            : from(events))
                            .concat(failed(cause));
                }
            }
            return failed(cause);
        });
    }

    private static boolean shouldRevokeState(final Throwable t, final boolean nxInvalidation) {
        // ISE => Subscriber exceptions (downstream of retry)
        return t instanceof SrvAddressRemovedException || t instanceof IllegalStateException ||
                t instanceof ClosedDnsServiceDiscovererException || (nxInvalidation &&
                // string matching is done on purpose to avoid the hard Netty dependency
                (t.getCause() != null && t.getCause().getClass().getName()
                        .equals("io.netty.resolver.dns.DnsErrorCauseException")) &&
                NXDOMAIN.equals(((io.netty.resolver.dns.DnsErrorCauseException) t.getCause()).getCode()));
    }

    private static <T> Publisher<T> newDuplicateSrv(String serviceName, String resolvedAddress) {
        return failed(new IllegalStateException("Duplicate SRV entry for SRV name " + serviceName + " for address " +
                resolvedAddress));
    }

    private static io.netty.resolver.dns.DnsServerAddressStreamProvider toNettyType(
            final DnsServerAddressStreamProvider provider) {
        return hostname -> new ServiceTalkToNettyDnsServerAddressStream(provider.nameServerAddressStream(hostname));
    }

    private static final class SrvInactiveCombinerOperator implements
                            PublisherOperator<Collection<ServiceDiscovererEvent<InetSocketAddress>>,
                                              Collection<ServiceDiscovererEvent<InetSocketAddress>>> {
        static final SrvInactiveCombinerOperator EMIT = new SrvInactiveCombinerOperator(true);
        private final boolean emitAggregatedEvents;

        private SrvInactiveCombinerOperator(boolean emitAggregatedEvents) {
            this.emitAggregatedEvents = emitAggregatedEvents;
        }

        @Override
        public Subscriber<? super Collection<ServiceDiscovererEvent<InetSocketAddress>>> apply(
                final Subscriber<? super Collection<ServiceDiscovererEvent<InetSocketAddress>>> subscriber) {
            return new Subscriber<Collection<ServiceDiscovererEvent<InetSocketAddress>>>() {
                @Nullable
                private List<ServiceDiscovererEvent<InetSocketAddress>> aggregatedEvents;
                @Nullable
                private Subscription subscription;
                @Override
                public void onSubscribe(final Subscription s) {
                    this.subscription = s;
                    subscriber.onSubscribe(s);
                }

                @Override
                public void onNext(@Nullable final Collection<ServiceDiscovererEvent<InetSocketAddress>> evts) {
                    assert subscription != null;
                    if (aggregatedEvents != null) {
                        if (evts != null && emitAggregatedEvents) {
                            aggregatedEvents.addAll(evts);
                        }
                        subscription.request(1);
                    } else if (evts instanceof SrvAggregateList) {
                        aggregatedEvents = (List<ServiceDiscovererEvent<InetSocketAddress>>) evts;
                        subscription.request(1);
                    } else { // if there hasn't been an SrvAggregateList event, we should just pass through.
                        subscriber.onNext(evts);
                    }
                }

                @Override
                public void onError(final Throwable t) {
                    try {
                        if (aggregatedEvents != null && emitAggregatedEvents) {
                            // requestN is OK. We previously didn't deliver the item which gave us inactiveEvents.
                            subscriber.onNext(aggregatedEvents);
                        }
                    } finally {
                        subscriber.onError(t);
                    }
                }

                @Override
                public void onComplete() {
                    subscriber.onComplete();
                }
            };
        }
    }

    private static final class SrvInactiveEvent<T, A> implements ServiceDiscovererEvent<T> {
        private final Status missingRecordStatus;
        private final List<ServiceDiscovererEvent<A>> aggregatedEvents = new SrvAggregateList<>();

        SrvInactiveEvent(Status missingRecordStatus) {
            this.missingRecordStatus = missingRecordStatus;
        }

        @Override
        public T address() {
            throw new IllegalStateException("address method should not be called when isAvailable is false!");
        }

        @Override
        public Status status() {
            return missingRecordStatus;
        }
    }

    private static final class SrvAggregateList<T> extends ArrayList<T> {
        private static final long serialVersionUID = -6105010311426084245L;
    }

    private static final class ServiceTalkToNettyDnsServerAddressStream
            implements io.netty.resolver.dns.DnsServerAddressStream {
        private final DnsServerAddressStream stream;

        ServiceTalkToNettyDnsServerAddressStream(final DnsServerAddressStream stream) {
            this.stream = stream;
        }

        @Override
        public InetSocketAddress next() {
            return stream.next();
        }

        @Override
        public int size() {
            return stream.size();
        }

        @Override
        public io.netty.resolver.dns.DnsServerAddressStream duplicate() {
            return new ServiceTalkToNettyDnsServerAddressStream(stream.duplicate());
        }
    }

    private static final class ClosedDnsServiceDiscovererException extends ClosedChannelException
            implements RejectedSubscribeError {
        private static final long serialVersionUID = -8092675984257002148L;
    }

    private static final class SrvAddressRemovedException extends RuntimeException {
        private static final long serialVersionUID = -4083873869084533456L;

        private SrvAddressRemovedException() {
        }

        static SrvAddressRemovedException newInstance(Class<?> clazz, String method) {
            return unknownStackTrace(new SrvAddressRemovedException(), clazz, method);
        }
    }
}
