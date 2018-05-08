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
package io.servicetalk.client.servicediscoverer.dns;

import io.servicetalk.client.api.DefaultServiceDiscovererEvent;
import io.servicetalk.client.api.ServiceDiscoverer;
import io.servicetalk.client.internal.HostAndPort;
import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.api.BiIntFunction;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.CompletableProcessor;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.internal.FlowControlUtil;
import io.servicetalk.transport.api.IoExecutor;
import io.servicetalk.transport.netty.internal.EventLoopAwareNettyIoExecutor;

import io.netty.channel.EventLoop;
import io.netty.resolver.ResolvedAddressTypes;
import io.netty.resolver.dns.DnsNameResolver;
import io.netty.resolver.dns.DnsNameResolverBuilder;
import io.netty.util.concurrent.Future;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;

import static io.servicetalk.client.internal.ServiceDiscovererUtils.calculateDifference;
import static io.servicetalk.concurrent.Cancellable.IGNORE_CANCEL;
import static io.servicetalk.concurrent.internal.EmptySubscription.EMPTY_SUBSCRIPTION;
import static io.servicetalk.transport.netty.internal.BuilderUtils.datagramChannel;
import static io.servicetalk.transport.netty.internal.EventLoopAwareNettyIoExecutors.toEventLoopAwareNettyIoExecutor;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.function.Function.identity;

/**
 * Default load balancer which will attempt to resolve A, AAAA, and CNAME type queries.
 */
public final class DefaultDnsServiceDiscoverer implements ServiceDiscoverer<String, InetAddress> {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultDnsServiceDiscoverer.class);

    static final Comparator<InetAddress> INET_ADDRESS_COMPARATOR = Comparator.comparing(o -> ByteBuffer.wrap(o.getAddress()));

    private static final org.reactivestreams.Subscriber<Event<InetAddress>> CANCELLED = new org.reactivestreams.Subscriber<Event<InetAddress>>() {
        @Override
        public void onSubscribe(Subscription s) {
            s.cancel();
        }

        @Override
        public void onNext(Event<InetAddress> event) {
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
    private final Executor executor;
    @Nullable
    private final BiIntFunction<Throwable, Completable> retryStrategy;
    private final DnsNameResolver resolver;
    private boolean closed;

    /**
     * Builder use to create objects of type {@link DefaultDnsServiceDiscoverer}.
     */
    public static final class Builder {
        private final IoExecutor ioExecutor;
        private final Executor executor;
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
        private int minTTLSeconds = 2;

        /**
         * Create a new instance.
         * @param ioExecutor The {@link IoExecutor} which will be used for I/O.
         * @param executor The {@link Executor} which will be used for calling user code.
         */
        public Builder(IoExecutor ioExecutor, Executor executor) {
            this.ioExecutor = requireNonNull(ioExecutor);
            this.executor = requireNonNull(executor);
        }

        /**
         * The minimum allowed TTL. This will be the minimum poll interval.
         * @param minTTLSeconds The minimum amount of time a cache entry will be considered valid (in seconds).
         * @return {@code this}.
         */
        public Builder setMinTTL(int minTTLSeconds) {
            if (minTTLSeconds < 1) {
                throw new IllegalArgumentException("minTTLSeconds: " + minTTLSeconds + " (expected > 1)");
            }
            this.minTTLSeconds = minTTLSeconds;
            return this;
        }

        /**
         * Set the {@link DnsServerAddressStreamProvider} which determines which DNS server should be used per query.
         * @param dnsServerAddressStreamProvider the {@link DnsServerAddressStreamProvider} which determines which DNS server should be used per query.
         * @return {@code this}.
         */
        public Builder setDnsServerAddressStreamProvider(@Nullable DnsServerAddressStreamProvider dnsServerAddressStreamProvider) {
            this.dnsServerAddressStreamProvider = dnsServerAddressStreamProvider;
            return this;
        }

        /**
         * Enable the automatic inclusion of a optional records that tries to give the remote DNS server a hint about
         * how much data the resolver can read per response. Some DNSServer may not support this and so fail to answer
         * queries. If you find problems you may want to disable this.
         * @param optResourceEnabled if optional records inclusion is enabled.
         * @return {@code this}.
         */
        public Builder setOptResourceEnabled(boolean optResourceEnabled) {
            this.optResourceEnabled = optResourceEnabled;
            return this;
        }

        /**
         * Set the number of dots which must appear in a name before an initial absolute query is made.
         * The default value is {@code 1}.
         * @param ndots the ndots value.
         * @return {@code this}.
         */
        public Builder setNdots(int ndots) {
            this.ndots = ndots;
            return this;
        }

        /**
         * Sets the list of the protocol families of the address resolved.
         * @param dnsResolverAddressTypes the address types.
         * @return {@code this}.
         */
        public Builder setDnsResolverAddressTypes(@Nullable DnsResolverAddressTypes dnsResolverAddressTypes) {
            this.dnsResolverAddressTypes = dnsResolverAddressTypes;
            return this;
        }

        /**
         * Configures retry strategy if DNS lookup fails.
         * @param retryStrategy Retry strategy to use for retrying DNS lookup failures.
         * @return {@code this}.
         */
        public Builder retryDnsFailures(BiIntFunction<Throwable, Completable> retryStrategy) {
            this.retryStrategy = retryStrategy;
            return this;
        }

        /**
         * Build a new instance of {@link DefaultDnsServiceDiscoverer}.
         * @return a new instance of {@link DefaultDnsServiceDiscoverer}.
         */
        public DefaultDnsServiceDiscoverer build() {
            return new DefaultDnsServiceDiscoverer(ioExecutor, executor, retryStrategy, minTTLSeconds, ndots,
                    optResourceEnabled, dnsResolverAddressTypes, dnsServerAddressStreamProvider);
        }
    }

    private DefaultDnsServiceDiscoverer(IoExecutor ioExecutor, Executor executor,
                                        @Nullable BiIntFunction<Throwable, Completable> retryStrategy, int minTTL,
                                        @Nullable Integer ndots, @Nullable Boolean optResourceEnabled,
                                        @Nullable DnsResolverAddressTypes dnsResolverAddressTypes,
                                        @Nullable DnsServerAddressStreamProvider dnsServerAddressStreamProvider) {
        // Implementation of this class expects to use only single EventLoop from IoExecutor
        this.nettyIoExecutor = toEventLoopAwareNettyIoExecutor(ioExecutor).next();
        this.executor = executor;
        this.retryStrategy = retryStrategy;
        EventLoop eventLoop = this.nettyIoExecutor.getEventLoopGroup().next();
        DnsNameResolverBuilder builder = new DnsNameResolverBuilder(eventLoop)
                // TODO(scott): handle the TTL in our custom cache implementation.
                .ttl(minTTL, Integer.MAX_VALUE)
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
    }

    @Override
    public Publisher<Event<InetAddress>> discover(String address) {
        final DiscoverEntry entry;
        if (nettyIoExecutor.isCurrentThreadEventLoop()) {
            if (closed) {
                return Publisher.error(new IllegalStateException(DefaultDnsServiceDiscoverer.class.getSimpleName() + " closed!"),
                        executor);
            }
            entry = new DiscoverEntry(address, executor);
            addEntry(entry);
        } else {
            entry = new DiscoverEntry(address, executor);
            nettyIoExecutor.executeOnEventloop(() -> {
                if (closed) {
                    entry.completeSubscription();
                } else {
                    addEntry(entry);
                }
            });
        }
        return entry.publisher;
    }

    private void addEntry(DiscoverEntry entry) {
        List<DiscoverEntry> entries = registerMap.get(entry.inetHost);
        if (entries == null) {
            entries = new ArrayList<>(2);
            registerMap.put(entry.inetHost, entries);
        }
        entries.add(entry);
    }

    void removeEntry(DiscoverEntry entry) {
        if (nettyIoExecutor.isCurrentThreadEventLoop()) {
            removeEntry0(entry);
        } else {
            nettyIoExecutor.executeOnEventloop(() -> removeEntry0(entry));
        }
    }

    private void removeEntry0(DiscoverEntry entry) {
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
                    nettyIoExecutor.executeOnEventloop(DefaultDnsServiceDiscoverer.this::closeAsync0);
                }
            }
        };
    }

    private void closeAsync0() {
        if (closed) {
            return;
        }
        closed = true;
        resolver.close();
        RuntimeException aggregateCause = null;
        for (Map.Entry<String, List<DiscoverEntry>> mapEntry : registerMap.entrySet()) {
            for (DiscoverEntry entry : mapEntry.getValue()) {
                try {
                    entry.completeSubscription();
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
     * @return a resolver which will convert from {@link String} host names and {@link InetAddress} resolved address to
     * {@link HostAndPort} to {@link InetSocketAddress}.
     */
    public ServiceDiscoverer<HostAndPort, InetSocketAddress> toHostAndPortDiscoverer() {
        return new ServiceDiscoverer<HostAndPort, InetSocketAddress>() {
            @Override
            public Completable closeAsync() {
                return DefaultDnsServiceDiscoverer.this.closeAsync();
            }

            @Override
            public Completable onClose() {
                return DefaultDnsServiceDiscoverer.this.onClose();
            }

            @Override
            public Publisher<Event<InetSocketAddress>> discover(HostAndPort hostAndPort) {
                return DefaultDnsServiceDiscoverer.this.discover(hostAndPort.getHostName()).map(originalEvent ->
                        new DefaultServiceDiscovererEvent<>(new InetSocketAddress(originalEvent.getAddress(), hostAndPort.getPort()), originalEvent.isAvailable())
                );
            }
        };
    }

    private final class DiscoverEntry extends Publisher<Iterable<Event<InetAddress>>> {
        @Nullable
        private org.reactivestreams.Subscriber<? super Event<InetAddress>> subscriber;
        final String inetHost;
        final Publisher<Event<InetAddress>> publisher;

        DiscoverEntry(String inetHost, Executor executor) {
            super(executor);
            this.inetHost = inetHost;
            Publisher<Event<InetAddress>> publisher = new Publisher<Event<InetAddress>>(executor) {
                @Override
                protected void handleSubscribe(org.reactivestreams.Subscriber<? super Event<InetAddress>> subscriber) {
                    initializeSubscriber(subscriber);
                }
            };
            this.publisher = retryStrategy == null ? publisher : publisher.retryWhen(retryStrategy);
        }

        void completeSubscription() {
            org.reactivestreams.Subscriber<? super Event<InetAddress>> oldSubscriber = subscriber;
            subscriber = CANCELLED;
            if (oldSubscriber != null) {
                completeSubscriberOnClose(oldSubscriber);
            }
        }

        private void completeSubscriberOnClose(org.reactivestreams.Subscriber<? super Event<InetAddress>> subscriber) {
            // TODO(scott): protect for concurrent access to subscriber?
            subscriber.onError(new IllegalStateException(DefaultDnsServiceDiscoverer.this + " has been closed!"));
        }

        private void initializeSubscriber(org.reactivestreams.Subscriber<? super Event<InetAddress>> subscriber) {
            if (nettyIoExecutor.isCurrentThreadEventLoop()) {
                initializeSubscriber0(subscriber);
            } else {
                nettyIoExecutor.executeOnEventloop(() -> initializeSubscriber0(subscriber));
            }
        }

        private void initializeSubscriber0(org.reactivestreams.Subscriber<? super Event<InetAddress>> subscriber) {
            if (this.subscriber != null) {
                subscriber.onSubscribe(EMPTY_SUBSCRIPTION);
                subscriber.onError(new IllegalStateException("a subscriber already exists"));
                return;
            }
            if (closed) {
                subscriber.onSubscribe(EMPTY_SUBSCRIPTION);
                completeSubscriberOnClose(subscriber);
                return;
            }
            this.subscriber = subscriber;
            flatMapIterable(identity()).subscribe(subscriber);
        }

        @Override
        protected void handleSubscribe(Subscriber<? super Iterable<Event<InetAddress>>> subscriber) {
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
                    // if there is already a Runnable scheduled in TTL time to refresh, then add to pendingRequests and do nothing
                    // if the value is in the cache and not expired then return it and schedule a Runnable to refresh our data in
                    //    (current time - last data delivered time) time units.
                    // If the value is in the cache, but expired then remove/query for it.
                    if (nettyIoExecutor.isCurrentThreadEventLoop()) {
                        request0(n);
                    } else {
                        nettyIoExecutor.executeOnEventloop(() -> request0(n));
                    }
                }

                @Override
                public void cancel() {
                    if (nettyIoExecutor.isCurrentThreadEventLoop()) {
                        cancel0();
                    } else {
                        nettyIoExecutor.executeOnEventloop(this::cancel0);
                    }
                }

                private void request0(long n) {
                    pendingRequests = FlowControlUtil.addWithOverflowProtection(pendingRequests, n);
                    if (cancellableForQuery == null) {
                        if (ttlNanos < 0) {
                            doQuery();
                        } else {
                            long durationNs = System.nanoTime() - resolveDoneNoScheduleTime;
                            if (durationNs > ttlNanos) {
                                doQuery();
                            } else {
                                scheduleQuery(ttlNanos - durationNs);
                            }
                        }
                    }
                }

                private void doQuery() {
                    cancellableForQuery = IGNORE_CANCEL;
                    Future<List<InetAddress>> addressFuture = resolver.resolveAll(inetHost);
                    if (addressFuture.isDone()) {
                        handleResolveDone(addressFuture);
                    } else {
                        addressFuture.addListener(future -> handleResolveDone((Future<List<InetAddress>>) future));
                    }
                }

                private void cancel0() {
                    if (cancellableForQuery != null) {
                        cancellableForQuery.cancel();
                        cancellableForQuery = null;
                    }
                    removeEntry(DiscoverEntry.this);
                }

                private void scheduleQuery(long nanos) {
                    // This value is coming from DNS TTL for which the unit is seconds and the minimum value we accept
                    // in the constructor is 1 second.
                    nettyIoExecutor.scheduleOnEventloop(nanos, NANOSECONDS).subscribe(new Completable.Subscriber() {
                        @Override
                        public void onSubscribe(Cancellable cancellable) {
                            // It is assumed this will happen synchronously and on the same thread.
                            cancellableForQuery = cancellable;
                        }

                        @Override
                        public void onComplete() {
                            doQuery();
                        }

                        @Override
                        public void onError(Throwable t) {
                            // This is likely because the timer was cancelled.
                            cancel0();
                        }
                    });
                }

                private void handleResolveDone(Future<List<InetAddress>> addressFuture) {
                    Throwable cause = addressFuture.cause();
                    if (cause != null) {
                        handleError(cause);
                    } else {
                        ttlNanos = TimeUnit.SECONDS.toNanos(2); // TODO(scott): the TTL value should be derived from the cache.
                        --pendingRequests;
                        if (pendingRequests > 0) {
                            scheduleQuery(ttlNanos);
                        } else {
                            resolveDoneNoScheduleTime = System.nanoTime();
                            cancellableForQuery = null;
                        }
                        List<InetAddress> addresses = addressFuture.getNow();
                        List<Event<InetAddress>> events = calculateDifference(activeAddresses, addresses, INET_ADDRESS_COMPARATOR);
                        if (events != null) {
                            activeAddresses = addresses;
                            try {
                                subscriber.onNext(events);
                            } catch (Throwable error) {
                                handleError(error);
                            }
                        }
                    }
                }

                private void handleError(Throwable cause) {
                    DiscoverEntry.this.subscriber = null; // allow sequential subscriptions
                    cancel0();
                    subscriber.onError(cause);
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

    private static io.netty.resolver.dns.DnsServerAddressStreamProvider toNettyType(DnsServerAddressStreamProvider provider) {
        return hostname -> new ServiceTalkToNettyDnsServerAddressStream(provider.nameServerAddressStream(hostname));
    }

    private static final class ServiceTalkToNettyDnsServerAddressStream implements io.netty.resolver.dns.DnsServerAddressStream {
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
