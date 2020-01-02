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
import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.CompletableSource;
import io.servicetalk.concurrent.CompletableSource.Processor;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.internal.SubscribableCompletable;
import io.servicetalk.concurrent.api.internal.SubscribablePublisher;
import io.servicetalk.concurrent.internal.DuplicateSubscribeException;
import io.servicetalk.concurrent.internal.FlowControlUtils;
import io.servicetalk.concurrent.internal.RejectedSubscribeError;
import io.servicetalk.transport.api.IoExecutor;
import io.servicetalk.transport.netty.internal.EventLoopAwareNettyIoExecutor;

import io.netty.channel.EventLoop;
import io.netty.channel.socket.SocketChannel;
import io.netty.resolver.ResolvedAddressTypes;
import io.netty.resolver.dns.DefaultDnsCache;
import io.netty.resolver.dns.DnsNameResolver;
import io.netty.resolver.dns.DnsNameResolverBuilder;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.RandomAccess;
import java.util.function.Predicate;
import javax.annotation.Nullable;

import static io.servicetalk.client.api.internal.ServiceDiscovererUtils.calculateDifference;
import static io.servicetalk.concurrent.api.Processors.newCompletableProcessor;
import static io.servicetalk.concurrent.api.Publisher.failed;
import static io.servicetalk.concurrent.api.SourceAdapters.fromSource;
import static io.servicetalk.concurrent.internal.EmptySubscription.EMPTY_SUBSCRIPTION;
import static io.servicetalk.concurrent.internal.SubscriberUtils.isRequestNValid;
import static io.servicetalk.concurrent.internal.SubscriberUtils.newExceptionForInvalidRequestN;
import static io.servicetalk.transport.netty.internal.BuilderUtils.datagramChannel;
import static io.servicetalk.transport.netty.internal.BuilderUtils.socketChannel;
import static io.servicetalk.transport.netty.internal.EventLoopAwareNettyIoExecutors.toEventLoopAwareNettyIoExecutor;
import static java.lang.System.nanoTime;
import static java.nio.ByteBuffer.wrap;
import static java.util.Collections.emptyList;
import static java.util.Comparator.comparing;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.function.Function.identity;

/**
 * Default load balancer which will attempt to resolve A, AAAA, and CNAME type queries.
 */
final class DefaultDnsServiceDiscoverer
        implements ServiceDiscoverer<String, InetAddress, ServiceDiscovererEvent<InetAddress>> {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultDnsServiceDiscoverer.class);
    private static final Comparator<InetAddress> INET_ADDRESS_COMPARATOR = comparing(o -> wrap(o.getAddress()));
    private static final Cancellable TERMINATED = () -> { };

    private final Processor closeCompletable = newCompletableProcessor();
    private final Map<String, List<DiscoverEntry>> registerMap = new HashMap<>(8);
    private final EventLoopAwareNettyIoExecutor nettyIoExecutor;
    private final DnsNameResolver resolver;
    private final MinTtlCache ttlCache;
    private final Predicate<Throwable> invalidateHostsOnDnsFailure;
    private boolean closed;

    DefaultDnsServiceDiscoverer(final IoExecutor ioExecutor, final int minTTL,
                                @Nullable final Integer ndots, final Predicate<Throwable> invalidateHostsOnDnsFailure,
                                @Nullable final Boolean optResourceEnabled, @Nullable final Duration queryTimeout,
                                @Nullable final DnsResolverAddressTypes dnsResolverAddressTypes,
                                @Nullable final DnsServerAddressStreamProvider dnsServerAddressStreamProvider) {
        // Implementation of this class expects to use only single EventLoop from IoExecutor
        this.nettyIoExecutor = toEventLoopAwareNettyIoExecutor(ioExecutor).next();
        this.ttlCache = new MinTtlCache(new DefaultDnsCache(minTTL, Integer.MAX_VALUE, minTTL), minTTL);
        this.invalidateHostsOnDnsFailure = invalidateHostsOnDnsFailure;
        final EventLoop eventLoop = this.nettyIoExecutor.eventLoopGroup().next();
        @SuppressWarnings("unchecked")
        final Class<? extends SocketChannel> socketChannelClass =
                (Class<? extends SocketChannel>) socketChannel(eventLoop, InetSocketAddress.class);
        final DnsNameResolverBuilder builder = new DnsNameResolverBuilder(eventLoop)
                .resolveCache(ttlCache)
                .channelType(datagramChannel(eventLoop))
                // Enable TCP fallback to be able to handle truncated responses.
                // https://tools.ietf.org/html/rfc7766
                .socketChannelType(socketChannelClass)
                // We should complete once the preferred address types could be resolved to ensure we always
                // respond as fast as possible.
                .completeOncePreferredResolved(true);
        if (queryTimeout != null) {
            builder.queryTimeoutMillis(queryTimeout.toMillis());
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
        if (dnsResolverAddressTypes != null) {
            builder.resolvedAddressTypes(toNettyType(dnsResolverAddressTypes));
        }

        resolver = builder.build();
        LOGGER.debug("Created a new DNS discoverer {} with minimum TTL (seconds): {}, ndots: {}, " +
                        "optResourceEnabled {}, dnsResolverAddressTypes {}, dnsServerAddressStreamProvider {}.",
                this, minTTL, ndots, optResourceEnabled, dnsResolverAddressTypes, dnsServerAddressStreamProvider);
    }

    @Override
    public Publisher<ServiceDiscovererEvent<InetAddress>> discover(final String address) {
        final DiscoverEntry entry;
        if (nettyIoExecutor.isCurrentThreadEventLoop()) {
            if (closed) {
                return failed(new IllegalStateException(DefaultDnsServiceDiscoverer.class.getSimpleName() +
                        " closed!"));
            }
            entry = new DiscoverEntry(address);
            addEntry0(entry);
        } else {
            entry = new DiscoverEntry(address);
            nettyIoExecutor.asExecutor().execute(() -> {
                if (closed) {
                    entry.close0();
                } else {
                    addEntry0(entry);
                }
            });
        }
        return entry.publisher;
    }

    private void addEntry0(final DiscoverEntry entry) {
        assertInEventloop();

        registerMap.computeIfAbsent(entry.inetHost, k -> new ArrayList<>(2)).add(entry);
    }

    private void removeEntry0(final DiscoverEntry entry) {
        assertInEventloop();

        LOGGER.debug("DNS discoverer {}, cancelled DNS resolution for {}.", DefaultDnsServiceDiscoverer.this,
                entry.inetHost);
        final List<DiscoverEntry> entries = registerMap.get(entry.inetHost);
        if (entries == null) {
            return;
        }
        entries.remove(entry);
        if (entries.isEmpty()) {
            registerMap.remove(entry.inetHost);
        }
    }

    @Override
    public Completable onClose() {
        return fromSource(closeCompletable);
    }

    @Override
    public Completable closeAsync() {
        return new SubscribableCompletable() {
            @Override
            protected void handleSubscribe(final CompletableSource.Subscriber subscriber) {
                closeCompletable.subscribe(subscriber);
                if (nettyIoExecutor.isCurrentThreadEventLoop()) {
                    closeAsync0();
                } else {
                    nettyIoExecutor.asExecutor().execute(DefaultDnsServiceDiscoverer.this::closeAsync0);
                }
            }
        };
    }

    private void closeAsync0() {
        assertInEventloop();

        if (closed) {
            return;
        }
        closed = true;
        resolver.close();
        RuntimeException aggregateCause = null;
        for (final Map.Entry<String, List<DiscoverEntry>> mapEntry : registerMap.entrySet()) {
            for (final DiscoverEntry entry : mapEntry.getValue()) {
                try {
                    entry.close0();
                } catch (final Throwable cause) {
                    if (aggregateCause == null) {
                        aggregateCause = new RuntimeException(
                                "Unexpected exception completing " + entry + " when closing " + this, cause);
                    } else {
                        aggregateCause.addSuppressed(cause);
                    }
                }
            }
        }
        registerMap.clear();
        if (aggregateCause != null) {
            LOGGER.debug("Closed with error", aggregateCause);
            closeCompletable.onError(aggregateCause);
        } else {
            LOGGER.debug("Successfully closed");
            closeCompletable.onComplete();
        }
    }

    private void assertInEventloop() {
        assert nettyIoExecutor.isCurrentThreadEventLoop() : "Must be called from the associated eventloop.";
    }

    private final class DiscoverEntry {
        private final String inetHost;
        private final EntriesPublisher entriesPublisher = new EntriesPublisher();
        private final Publisher<ServiceDiscovererEvent<InetAddress>> publisher;

        DiscoverEntry(final String inetHost) {
            this.inetHost = inetHost;
            publisher = new EntriesPublisher().flatMapConcatIterable(identity());
        }

        void close0() {
            entriesPublisher.close0();
        }

        private final class EntriesPublisher
                extends SubscribablePublisher<Iterable<ServiceDiscovererEvent<InetAddress>>> {

            @Nullable
            private Subscriber<? super Iterable<ServiceDiscovererEvent<InetAddress>>> discoverySubscriber;
            @Nullable
            private EntriesPublisherSubscription subscription;

            @Override
            protected void handleSubscribe(
                    final Subscriber<? super Iterable<ServiceDiscovererEvent<InetAddress>>> subscriber) {

                if (nettyIoExecutor.isCurrentThreadEventLoop()) {
                    handleSubscribe0(subscriber);
                } else {
                    nettyIoExecutor.asExecutor().execute(() -> handleSubscribe0(subscriber));
                }
            }

            private void handleSubscribe0(
                    final Subscriber<? super Iterable<ServiceDiscovererEvent<InetAddress>>> subscriber) {
                assertInEventloop();

                if (discoverySubscriber != null) {
                    subscriber.onSubscribe(EMPTY_SUBSCRIPTION);
                    subscriber.onError(new DuplicateSubscribeException(discoverySubscriber, subscriber));
                } else if (closed) {
                    subscriber.onSubscribe(EMPTY_SUBSCRIPTION);
                    subscriber.onError(new ClosedServiceDiscovererException(DefaultDnsServiceDiscoverer.this +
                            " has been closed!"));
                } else {
                    subscription = new EntriesPublisherSubscription(subscriber);
                    discoverySubscriber = subscriber;
                    LOGGER.debug("DNS discoverer {}, starting DNS resolution for {}.",
                            DefaultDnsServiceDiscoverer.this, inetHost);
                    subscriber.onSubscribe(subscription);
                }
            }

            void close0() {
                assertInEventloop();

                final Subscriber<? super Iterable<ServiceDiscovererEvent<InetAddress>>> oldSubscriber =
                        discoverySubscriber;
                discoverySubscriber = null;
                if (oldSubscriber != null) {
                    assert subscription != null;
                    subscription.cancelWithoutRemove0();
                    oldSubscriber.onError(new ClosedServiceDiscovererException(DefaultDnsServiceDiscoverer.this +
                            " has been closed!"));
                }
            }

            private final class EntriesPublisherSubscription implements Subscription {

                private final Subscriber<? super Iterable<ServiceDiscovererEvent<InetAddress>>> subscriber;
                private long pendingRequests;
                private List<InetAddress> activeAddresses;
                private long resolveDoneNoScheduleTime;
                @Nullable
                private Cancellable cancellableForQuery;
                private long ttlNanos;

                EntriesPublisherSubscription(
                        final Subscriber<? super Iterable<ServiceDiscovererEvent<InetAddress>>> subscriber) {
                    this.subscriber = subscriber;
                    activeAddresses = emptyList();
                    ttlNanos = -1;
                }

                @Override
                public void request(final long n) {
                    if (nettyIoExecutor.isCurrentThreadEventLoop()) {
                        request0(n);
                    } else {
                        nettyIoExecutor.asExecutor().execute(() -> request0(n));
                    }
                }

                @Override
                public void cancel() {
                    if (nettyIoExecutor.isCurrentThreadEventLoop()) {
                        cancel0();
                    } else {
                        nettyIoExecutor.asExecutor().execute(this::cancel0);
                    }
                }

                private void request0(final long n) {
                    assertInEventloop();

                    if (!isRequestNValid(n)) {
                        handleError0(newExceptionForInvalidRequestN(n), __ -> false);
                        return;
                    }

                    pendingRequests = FlowControlUtils.addWithOverflowProtectionIfNotNegative(pendingRequests, n);
                    if (cancellableForQuery == null) {
                        if (ttlNanos < 0) {
                            doQuery0();
                        } else {
                            final long durationNs = nanoTime() - resolveDoneNoScheduleTime;
                            if (durationNs > ttlNanos) {
                                doQuery0();
                            } else {
                                scheduleQuery0(ttlNanos - durationNs);
                            }
                        }
                    }
                }

                private void doQuery0() {
                    assertInEventloop();

                    LOGGER.trace("DNS discoverer {}, querying DNS for {}.", DefaultDnsServiceDiscoverer.this,
                            inetHost);

                    ttlCache.prepareForResolution(inetHost);
                    final Future<List<InetAddress>> addressFuture = resolver.resolveAll(inetHost);
                    cancellableForQuery = () -> addressFuture.cancel(true);
                    if (addressFuture.isDone()) {
                        handleResolveDone0(addressFuture);
                    } else {
                        addressFuture.addListener((FutureListener<List<InetAddress>>) this::handleResolveDone0);
                    }
                }

                private void cancel0() {
                    assertInEventloop();

                    removeEntry0(DiscoverEntry.this);
                    cancelWithoutRemove0();
                }

                private void cancelWithoutRemove0() {
                    if (cancellableForQuery != null) {
                        cancellableForQuery = TERMINATED;
                        discoverySubscriber = null;
                        pendingRequests = -1;
                        cancellableForQuery.cancel();
                    }
                }

                private void scheduleQuery0(final long nanos) {
                    assertInEventloop();

                    LOGGER.trace("DNS discoverer {}, scheduling DNS query for {} after {} nanos.",
                            DefaultDnsServiceDiscoverer.this, inetHost, nanos);
                    // This value is coming from DNS TTL for which the unit is seconds and the minimum value we accept
                    // in the builder is 1 second.
                    cancellableForQuery = nettyIoExecutor.asExecutor().schedule(
                            this::doQuery0, nanos, NANOSECONDS);
                }

                private void handleResolveDone0(final Future<List<InetAddress>> addressFuture) {
                    assertInEventloop();

                    // If `discoverySubscriber` is null, then this publisher has terminated, so we can't send any more
                    // signals. There's no point in even scheduling a query in that case.
                    if (discoverySubscriber != null) {
                        final Throwable cause = addressFuture.cause();
                        if (cause != null) {
                            handleError0(cause, invalidateHostsOnDnsFailure);
                        } else {
                            // DNS lookup can return duplicate InetAddress
                            final List<InetAddress> addresses = addressFuture.getNow();
                            final List<ServiceDiscovererEvent<InetAddress>> events =
                                    calculateDifference(activeAddresses, addresses, INET_ADDRESS_COMPARATOR);
                            ttlNanos = SECONDS.toNanos(ttlCache.minTtl(inetHost));
                            if (events != null) {
                                --pendingRequests;
                                if (pendingRequests > 0) {
                                    scheduleQuery0(ttlNanos);
                                } else {
                                    resolveDoneNoScheduleTime = nanoTime();
                                    cancellableForQuery = null;
                                }
                                activeAddresses = addresses;
                                try {
                                    LOGGER.debug("DNS discoverer {}, sending events for address {}: (size {}) {}.",
                                            DefaultDnsServiceDiscoverer.this, inetHost, events.size(), events);

                                    subscriber.onNext(events);
                                } catch (final Throwable error) {
                                    handleError0(error, __ -> false);
                                }
                            } else {
                                LOGGER.trace("DNS discoverer {}, resolution done but no changes observed for {}. " +
                                                "Resolution result: (size {}) {}",
                                        DefaultDnsServiceDiscoverer.this, inetHost, addresses.size(), addresses);
                                scheduleQuery0(ttlNanos);
                            }
                        }
                    }
                }

                private void handleError0(final Throwable cause,
                                          final Predicate<Throwable> invalidateHostsOnDnsFailure) {
                    assertInEventloop();

                    LOGGER.debug("DNS discoverer {}, DNS lookup failed for {}.", DefaultDnsServiceDiscoverer.this,
                            inetHost, cause);
                    final boolean wasAlreadyTerminated = discoverySubscriber == null;
                    discoverySubscriber = null; // allow sequential subscriptions
                    cancel0();
                    if (wasAlreadyTerminated) {
                        return;
                    }

                    if (invalidateHostsOnDnsFailure.test(cause)) {
                        final List<InetAddress> addresses = activeAddresses;
                        final List<ServiceDiscovererEvent<InetAddress>> events = new ArrayList<>(addresses.size());
                        if (addresses instanceof RandomAccess) {
                            for (int i = 0; i < addresses.size(); ++i) {
                                events.add(new DefaultServiceDiscovererEvent<>(addresses.get(i), false));
                            }
                        } else {
                            for (final InetAddress address : addresses) {
                                events.add(new DefaultServiceDiscovererEvent<>(address, false));
                            }
                        }
                        try {
                            subscriber.onNext(events);
                        } catch (final Throwable e) {
                            LOGGER.warn("Exception from subscriber while handling error", e);
                        }
                    }
                    subscriber.onError(cause);
                }
            }
        }
    }

    private static ResolvedAddressTypes toNettyType(final DnsResolverAddressTypes dnsResolverAddressTypes) {
        switch (dnsResolverAddressTypes) {
            case IPV4_ONLY:
                return ResolvedAddressTypes.IPV4_ONLY;
            case IPV6_ONLY:
                return ResolvedAddressTypes.IPV6_ONLY;
            case IPV6_PREFERRED:
                return ResolvedAddressTypes.IPV6_PREFERRED;
            case IPV4_PREFERRED:
                return ResolvedAddressTypes.IPV4_PREFERRED;
            default:
                throw new Error();
        }
    }

    private static io.netty.resolver.dns.DnsServerAddressStreamProvider toNettyType(
            final DnsServerAddressStreamProvider provider) {
        return hostname -> new ServiceTalkToNettyDnsServerAddressStream(provider.nameServerAddressStream(hostname));
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

    private static final class ClosedServiceDiscovererException extends RuntimeException
            implements RejectedSubscribeError {
        ClosedServiceDiscovererException(final String message) {
            super(message);
        }
    }
}
