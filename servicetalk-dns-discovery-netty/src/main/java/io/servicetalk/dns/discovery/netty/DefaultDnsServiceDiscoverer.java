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
import io.servicetalk.concurrent.api.BiIntFunction;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.CompletableProcessor;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.internal.DuplicateSubscribeException;
import io.servicetalk.concurrent.internal.FlowControlUtil;
import io.servicetalk.transport.api.ExecutionContext;
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.netty.internal.EventLoopAwareNettyIoExecutor;

import io.netty.channel.EventLoop;
import io.netty.resolver.ResolvedAddressTypes;
import io.netty.resolver.dns.DefaultDnsCache;
import io.netty.resolver.dns.DnsNameResolver;
import io.netty.resolver.dns.DnsNameResolverBuilder;
import io.netty.resolver.dns.DnsNameResolverTimeoutException;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.RandomAccess;
import javax.annotation.Nullable;

import static io.servicetalk.client.internal.ServiceDiscovererUtils.calculateDifference;
import static io.servicetalk.concurrent.Cancellable.IGNORE_CANCEL;
import static io.servicetalk.concurrent.api.Publisher.error;
import static io.servicetalk.concurrent.internal.EmptySubscription.EMPTY_SUBSCRIPTION;
import static io.servicetalk.transport.netty.internal.BuilderUtils.datagramChannel;
import static io.servicetalk.transport.netty.internal.EventLoopAwareNettyIoExecutors.toEventLoopAwareNettyIoExecutor;
import static java.lang.System.nanoTime;
import static java.nio.ByteBuffer.wrap;
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

    static final Comparator<InetAddress> INET_ADDRESS_COMPARATOR = comparing(o -> wrap(o.getAddress()));

    private static final Subscriber<ServiceDiscovererEvent<InetAddress>> CANCELLED =
            new Subscriber<ServiceDiscovererEvent<InetAddress>>() {
        @Override
        public void onSubscribe(Subscription s) {
            s.cancel();
        }

        @Override
        public void onNext(ServiceDiscovererEvent<InetAddress> event) {
        }

        @Override
        public void onError(Throwable t) {
        }

        @Override
        public void onComplete() {
        }
    };

    private final CompletableProcessor closeCompletable = new CompletableProcessor();
    private final Map<String, List<DiscoverEntry>> registerMap = new HashMap<>(8);
    private final EventLoopAwareNettyIoExecutor nettyIoExecutor;
    @Nullable
    private final BiIntFunction<Throwable, Completable> retryStrategy;
    private final DnsNameResolver resolver;
    private final MinTtlCache ttlCache;
    private boolean closed;

    DefaultDnsServiceDiscoverer(ExecutionContext executionContext,
                                @Nullable BiIntFunction<Throwable, Completable> retryStrategy, int minTTL,
                                @Nullable Integer ndots, @Nullable Boolean optResourceEnabled,
                                @Nullable DnsResolverAddressTypes dnsResolverAddressTypes,
                                @Nullable DnsServerAddressStreamProvider dnsServerAddressStreamProvider) {
        // Implementation of this class expects to use only single EventLoop from IoExecutor
        this.nettyIoExecutor = toEventLoopAwareNettyIoExecutor(executionContext.ioExecutor()).next();
        this.retryStrategy = retryStrategy;
        this.ttlCache = new MinTtlCache(new DefaultDnsCache(minTTL, Integer.MAX_VALUE, minTTL), minTTL);
        EventLoop eventLoop = this.nettyIoExecutor.getEventLoopGroup().next();
        DnsNameResolverBuilder builder = new DnsNameResolverBuilder(eventLoop)
                .resolveCache(ttlCache)
                .channelType(datagramChannel(eventLoop));
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
        LOGGER.debug("Created a new DNS discoverer {} with minimum TTL (seconds): {}, ndots: {}, optResourceEnabled {}, dnsResolverAddressTypes {}, dnsServerAddressStreamProvider {}.",
                this, minTTL, ndots, optResourceEnabled, dnsResolverAddressTypes, dnsServerAddressStreamProvider);
    }

    @Override
    public Publisher<ServiceDiscovererEvent<InetAddress>> discover(String address) {
        final DiscoverEntry entry;
        if (nettyIoExecutor.isCurrentThreadEventLoop()) {
            if (closed) {
                return error(new IllegalStateException(DefaultDnsServiceDiscoverer.class.getSimpleName() + " closed!"));
            }
            entry = new DiscoverEntry(address);
            addEntry0(entry);
        } else {
            entry = new DiscoverEntry(address);
            nettyIoExecutor.asExecutor().execute(() -> {
                if (closed) {
                    entry.completeSubscription0();
                } else {
                    addEntry0(entry);
                }
            });
        }
        return entry.publisher;
    }

    private void addEntry0(DiscoverEntry entry) {
        assertInEventloop();

        registerMap.computeIfAbsent(entry.inetHost, k -> new ArrayList<>(2)).add(entry);
    }

    void removeEntry(DiscoverEntry entry) {
        if (nettyIoExecutor.isCurrentThreadEventLoop()) {
            removeEntry0(entry);
        } else {
            nettyIoExecutor.asExecutor().execute(() -> removeEntry0(entry));
        }
    }

    private void removeEntry0(DiscoverEntry entry) {
        assertInEventloop();

        LOGGER.debug("DNS discoverer {}, cancelled DNS resolution for {}.", DefaultDnsServiceDiscoverer.this,
                entry.inetHost);
        List<DiscoverEntry> entries = registerMap.get(entry.inetHost);
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
        return closeCompletable;
    }

    @Override
    public Completable closeAsync() {
        return new Completable() {
            @Override
            protected void handleSubscribe(Subscriber subscriber) {
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
        for (Map.Entry<String, List<DiscoverEntry>> mapEntry : registerMap.entrySet()) {
            for (DiscoverEntry entry : mapEntry.getValue()) {
                try {
                    entry.completeSubscription0();
                } catch (Throwable cause) {
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

    /**
     * Convert this object from {@link String} host names and {@link InetAddress} resolved address to
     * {@link HostAndPort} to {@link InetSocketAddress}.
     *
     * @return a resolver which will convert from {@link String} host names and {@link InetAddress} resolved address to
     * {@link HostAndPort} to {@link InetSocketAddress}.
     */
    ServiceDiscoverer<HostAndPort, InetSocketAddress,
            ServiceDiscovererEvent<InetSocketAddress>> toHostAndPortDiscoverer() {
        return new ServiceDiscoverer<HostAndPort, InetSocketAddress, ServiceDiscovererEvent<InetSocketAddress>>() {
            @Override
            public Completable closeAsync() {
                return DefaultDnsServiceDiscoverer.this.closeAsync();
            }

            @Override
            public Completable closeAsyncGracefully() {
                return DefaultDnsServiceDiscoverer.this.closeAsyncGracefully();
            }

            @Override
            public Completable onClose() {
                return DefaultDnsServiceDiscoverer.this.onClose();
            }

            @Override
            public Publisher<ServiceDiscovererEvent<InetSocketAddress>> discover(HostAndPort hostAndPort) {
                return DefaultDnsServiceDiscoverer.this.discover(hostAndPort.getHostName()).map(originalEvent ->
                        new DefaultServiceDiscovererEvent<>(new InetSocketAddress(originalEvent.address(),
                                hostAndPort.getPort()), originalEvent.available())
                );
            }
        };
    }

    private void assertInEventloop() {
        assert nettyIoExecutor.isCurrentThreadEventLoop() : "Must be called from the associated eventloop.";
    }

    private final class DiscoverEntry extends Publisher<Iterable<ServiceDiscovererEvent<InetAddress>>> {
        @Nullable
        private Subscriber<? super ServiceDiscovererEvent<InetAddress>> subscriber;
        final String inetHost;
        final Publisher<ServiceDiscovererEvent<InetAddress>> publisher;

        DiscoverEntry(String inetHost) {
            this.inetHost = inetHost;
            Publisher<ServiceDiscovererEvent<InetAddress>> publisher =
                    new Publisher<ServiceDiscovererEvent<InetAddress>>() {
                        @Override
                        protected void handleSubscribe(
                                Subscriber<? super ServiceDiscovererEvent<InetAddress>> subscriber) {
                            initializeSubscriber(subscriber);
                        }
                    };
            this.publisher = retryStrategy == null ? publisher :
                    publisher.retryWhen(retryStrategy);
        }

        void completeSubscription0() {
            assertInEventloop();

            Subscriber<? super ServiceDiscovererEvent<InetAddress>> oldSubscriber = subscriber;
            subscriber = CANCELLED;
            if (oldSubscriber != null) {
                completeSubscriberOnClose0(oldSubscriber);
            }
        }

        private void completeSubscriberOnClose0(
                Subscriber<? super ServiceDiscovererEvent<InetAddress>> subscriber) {
            assertInEventloop();

            final IllegalStateException cause = new IllegalStateException(DefaultDnsServiceDiscoverer.this + " has been closed!");
            if (DiscoverEntry.this.subscriber != null) {
                DiscoverEntry.this.subscriber = null;
                subscriber.onError(cause);
            }
        }

        private void initializeSubscriber(
                Subscriber<? super ServiceDiscovererEvent<InetAddress>> subscriber) {
            if (nettyIoExecutor.isCurrentThreadEventLoop()) {
                initializeSubscriber0(subscriber);
            } else {
                nettyIoExecutor.asExecutor().execute(() -> initializeSubscriber0(subscriber));
            }
        }

        private void initializeSubscriber0(
                Subscriber<? super ServiceDiscovererEvent<InetAddress>> subscriber) {
            assertInEventloop();

            if (this.subscriber != null) {
                subscriber.onSubscribe(EMPTY_SUBSCRIPTION);
                subscriber.onError(new DuplicateSubscribeException(this.subscriber, subscriber));
                return;
            }
            if (closed) {
                subscriber.onSubscribe(EMPTY_SUBSCRIPTION);
                completeSubscriberOnClose0(subscriber);
                return;
            }
            this.subscriber = subscriber;
            flatMapIterable(identity()).subscribe(subscriber);
        }

        @Override
        protected void handleSubscribe(Subscriber<? super Iterable<ServiceDiscovererEvent<InetAddress>>> subscriber) {
            assertInEventloop();

            LOGGER.debug("DNS discoverer {}, starting DNS resolution for {}.", DefaultDnsServiceDiscoverer.this,
                    inetHost);
            subscriber.onSubscribe(new Subscription() {
                private long pendingRequests;
                private List<InetAddress> activeAddresses = Collections.emptyList();
                private long resolveDoneNoScheduleTime;
                @Nullable
                private Cancellable cancellableForQuery;
                private long ttlNanos = -1;

                @Override
                public void request(long n) {
                    // TODO(scott): use a custom cache which can do the following:
                    // If the value is not in the cache (and we don't have an outstanding query?), the query for it
                    // if there is already a Runnable scheduled in TTL time to refresh, then add to pendingRequests and
                    // do nothing if the value is in the cache and not expired then return it and schedule a Runnable
                    // to refresh our data in (current time - last data delivered time) time units.
                    // If the value is in the cache, but expired then remove/query for it.
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

                private void request0(long n) {
                    assertInEventloop();

                    pendingRequests = FlowControlUtil.addWithOverflowProtection(pendingRequests, n);
                    if (cancellableForQuery == null) {
                        if (ttlNanos < 0) {
                            doQuery0();
                        } else {
                            long durationNs = nanoTime() - resolveDoneNoScheduleTime;
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

                    LOGGER.trace("DNS discoverer {}, querying DNS for {}.", DefaultDnsServiceDiscoverer.this, inetHost);

                    ttlCache.prepareForResolution(inetHost);
                    cancellableForQuery = IGNORE_CANCEL;
                    Future<List<InetAddress>> addressFuture = resolver.resolveAll(inetHost);
                    if (addressFuture.isDone()) {
                        handleResolveDone0(addressFuture);
                    } else {
                        addressFuture.addListener((FutureListener<List<InetAddress>>) this::handleResolveDone0);
                    }
                }

                private void cancel0() {
                    assertInEventloop();

                    if (cancellableForQuery != null) {
                        cancellableForQuery.cancel();
                        cancellableForQuery = null;
                    }
                    removeEntry(DiscoverEntry.this);
                }

                private void scheduleQuery0(long nanos) {
                    assertInEventloop();

                    LOGGER.trace("DNS discoverer {}, scheduling DNS query for {} after {} nanos.",
                            DefaultDnsServiceDiscoverer.this, inetHost, nanos);
                    // This value is coming from DNS TTL for which the unit is seconds and the minimum value we accept
                    // in the builder is 1 second.
                    cancellableForQuery = nettyIoExecutor.asExecutor().schedule(
                            this::doQuery0, nanos, NANOSECONDS);
                }

                private void handleResolveDone0(Future<List<InetAddress>> addressFuture) {
                    assertInEventloop();

                    if (DiscoverEntry.this.subscriber != null) {
                        Throwable cause = addressFuture.cause();
                        if (cause != null) {
                            handleError0(cause);
                        } else {
                            List<InetAddress> addresses = addressFuture.getNow();
                            List<ServiceDiscovererEvent<InetAddress>> events = calculateDifference(activeAddresses,
                                    addresses, INET_ADDRESS_COMPARATOR);
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
                                } catch (Throwable error) {
                                    handleError0(error);
                                }
                            } else {
                                LOGGER.trace("DNS discoverer {}, resolution done but no changes observed for {}. Resolution result: (size {}) {}",
                                        DefaultDnsServiceDiscoverer.this, inetHost, addresses.size(), addresses);
                                scheduleQuery0(ttlNanos);
                            }
                        }
                    }
                }

                private void handleError0(Throwable cause) {
                    assertInEventloop();

                    LOGGER.debug("DNS discoverer {}, DNS lookup failed for {}.", DefaultDnsServiceDiscoverer.this, inetHost, cause);
                    boolean wasAlreadyTerminated = DiscoverEntry.this.subscriber == null;
                    DiscoverEntry.this.subscriber = null; // allow sequential subscriptions
                    cancel0();
                    if (!wasAlreadyTerminated) {
                        final List<InetAddress> addresses = activeAddresses;
                        if (cause instanceof UnknownHostException && !(cause.getCause() instanceof DnsNameResolverTimeoutException)) {
                            List<ServiceDiscovererEvent<InetAddress>> events = new ArrayList<>(addresses.size());
                            if (addresses instanceof RandomAccess) {
                                for (int i = 0; i < addresses.size(); ++i) {
                                    events.add(new DefaultServiceDiscovererEvent<>(addresses.get(i), false));
                                }
                            } else {
                                for (final InetAddress address : addresses) {
                                    events.add(new DefaultServiceDiscovererEvent<>(address, false));
                                }
                            }
                            subscriber.onNext(events);
                        }
                        subscriber.onError(cause);
                    }
                }
            });
        }
    }

    private static ResolvedAddressTypes toNettyType(DnsResolverAddressTypes dnsResolverAddressTypes) {
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
            DnsServerAddressStreamProvider provider) {
        return hostname -> new ServiceTalkToNettyDnsServerAddressStream(provider.nameServerAddressStream(hostname));
    }

    private static final class ServiceTalkToNettyDnsServerAddressStream
            implements io.netty.resolver.dns.DnsServerAddressStream {
        private final DnsServerAddressStream stream;

        ServiceTalkToNettyDnsServerAddressStream(DnsServerAddressStream stream) {
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
}
