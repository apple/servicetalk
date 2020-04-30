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
import io.servicetalk.client.api.ServiceDiscovererEvent;
import io.servicetalk.client.api.internal.ServiceDiscovererUtils;
import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.ListenableAsyncCloseable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.internal.SubscribablePublisher;
import io.servicetalk.concurrent.internal.DuplicateSubscribeException;
import io.servicetalk.concurrent.internal.RejectedSubscribeError;
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.api.IoExecutor;
import io.servicetalk.transport.netty.internal.EventLoopAwareNettyIoExecutor;

import io.netty.buffer.ByteBuf;
import io.netty.channel.EventLoop;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.dns.DefaultDnsQuestion;
import io.netty.handler.codec.dns.DnsRawRecord;
import io.netty.handler.codec.dns.DnsRecord;
import io.netty.resolver.ResolvedAddressTypes;
import io.netty.resolver.dns.DefaultDnsCache;
import io.netty.resolver.dns.DnsNameResolver;
import io.netty.resolver.dns.DnsNameResolverBuilder;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.ImmediateEventExecutor;
import io.netty.util.concurrent.Promise;
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

import static io.netty.handler.codec.dns.DefaultDnsRecordDecoder.decodeName;
import static io.netty.handler.codec.dns.DnsRecordType.SRV;
import static io.servicetalk.concurrent.api.AsyncCloseables.toAsyncCloseable;
import static io.servicetalk.concurrent.api.Completable.completed;
import static io.servicetalk.concurrent.api.Single.failed;
import static io.servicetalk.concurrent.internal.FlowControlUtils.addWithOverflowProtection;
import static io.servicetalk.concurrent.internal.SubscriberUtils.deliverErrorFromSource;
import static io.servicetalk.concurrent.internal.SubscriberUtils.isRequestNValid;
import static io.servicetalk.concurrent.internal.SubscriberUtils.newExceptionForInvalidRequestN;
import static io.servicetalk.concurrent.internal.SubscriberUtils.safeOnComplete;
import static io.servicetalk.concurrent.internal.SubscriberUtils.safeOnError;
import static io.servicetalk.transport.netty.internal.BuilderUtils.datagramChannel;
import static io.servicetalk.transport.netty.internal.BuilderUtils.socketChannel;
import static io.servicetalk.transport.netty.internal.EventLoopAwareNettyIoExecutors.toEventLoopAwareNettyIoExecutor;
import static java.lang.System.nanoTime;
import static java.nio.ByteBuffer.wrap;
import static java.util.Collections.emptyList;
import static java.util.Comparator.comparing;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.function.Function.identity;

final class DefaultDnsClient implements DnsClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultDnsClient.class);
    private static final Comparator<InetAddress> INET_ADDRESS_COMPARATOR = comparing(o -> wrap(o.getAddress()));
    private static final Comparator<HostAndPort> HOST_AND_PORT_COMPARATOR = comparing(HostAndPort::hostName)
            .thenComparingInt(HostAndPort::port);
    private static final Cancellable TERMINATED = () -> { };
    private static final Cancellable TERMINATE_ON_NEXT_REQUEST_N = () -> { };

    private final EventLoopAwareNettyIoExecutor nettyIoExecutor;
    private final DnsNameResolver resolver;
    private final MinTtlCache ttlCache;
    private final Predicate<Throwable> invalidateHostsOnDnsFailure;
    private final ListenableAsyncCloseable asyncCloseable;
    private boolean closed;

    DefaultDnsClient(final IoExecutor ioExecutor, final int minTTL,
                     @Nullable final Integer ndots, final Predicate<Throwable> invalidateHostsOnDnsFailure,
                     @Nullable final Boolean optResourceEnabled, @Nullable final Duration queryTimeout,
                     @Nullable final DnsResolverAddressTypes dnsResolverAddressTypes,
                     @Nullable final DnsServerAddressStreamProvider dnsServerAddressStreamProvider) {
        // Implementation of this class expects to use only single EventLoop from IoExecutor
        this.nettyIoExecutor = toEventLoopAwareNettyIoExecutor(ioExecutor).next();
        this.ttlCache = new MinTtlCache(new DefaultDnsCache(minTTL, Integer.MAX_VALUE, minTTL), minTTL);
        this.invalidateHostsOnDnsFailure = invalidateHostsOnDnsFailure;
        asyncCloseable = toAsyncCloseable(graceful -> {
            if (nettyIoExecutor.isCurrentThreadEventLoop()) {
                closeAsync0();
                return completed();
            }
            return nettyIoExecutor.asExecutor().submit(this::closeAsync0);
        });
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
    }

    @Override
    public Publisher<ServiceDiscovererEvent<InetAddress>> dnsQuery(final String address) {
        return new ARecordPublisher(true, address).flatMapConcatIterable(identity());
    }

    @Override
    public Publisher<ServiceDiscovererEvent<InetSocketAddress>> dnsSrvQuery(final String serviceName) {
        return Publisher.defer(() -> {
            // State per subscribe requires defer so each subscribe gets independent state.
            final Map<HostAndPort, ARecordPublisher> aRecordMap = new HashMap<>(8);
            return new SrvRecordPublisher(serviceName).flatMapConcatIterable(identity())
                    .flatMapMergeSingle(srvEvent -> {
                assertInEventloop();
                if (srvEvent.isAvailable()) {
                    final ARecordPublisher aPublisher = new ARecordPublisher(false, srvEvent.address().hostName());
                    final ARecordPublisher prevAPublisher = aRecordMap.putIfAbsent(srvEvent.address(), aPublisher);
                    if (prevAPublisher != null) {
                        return failed(new IllegalStateException("Only 1 A* record per SRV record is supported. " +
                                srvEvent.address() + " corresponding to SRV name " + serviceName +
                                " had a pre-existing A* record:" + prevAPublisher.inetHost +
                                " when new A* record arrived: " + aPublisher.inetHost));
                    }

                    return srvARecordPubToSingle(aPublisher, srvEvent, serviceName);
                } else {
                    final ARecordPublisher aPublisher = aRecordMap.remove(srvEvent.address());
                    if (aPublisher != null) {
                        final List<ServiceDiscovererEvent<InetAddress>> list = aPublisher.close0();
                        if (list != null) {
                            return srvARecordPubToSingle(Publisher.from(list), srvEvent, serviceName);
                        }
                    }

                    return failed(new IllegalStateException("Received an SRV inactive event for " +
                            srvEvent.address() + " corresponding to SRV name " + serviceName +
                            " but failed to find the corresponding A* record Publisher."));
                }
            });
        });
    }

    private static Single<? extends ServiceDiscovererEvent<InetSocketAddress>> srvARecordPubToSingle(
            Publisher<Iterable<ServiceDiscovererEvent<InetAddress>>> aRecordPublisher,
            ServiceDiscovererEvent<HostAndPort> srvEvent, String serviceName) {
        return aRecordPublisher.flatMapConcatIterable(identity()).map(inetEvent -> new DefaultServiceDiscovererEvent<>(
                new InetSocketAddress(inetEvent.address(), srvEvent.address().port()), inetEvent.isAvailable())
        ).firstOrElse(() -> {
            LOGGER.info("No A* records found for {} corresponding to SRV name {}", srvEvent.address(), serviceName);
            return null;
        });
    }

    @Override
    public Completable onClose() {
        return asyncCloseable.onClose();
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
        assert nettyIoExecutor.isCurrentThreadEventLoop() : "Must be called from the associated eventloop.";
    }

    private final class SrvRecordPublisher extends AbstractDnsPublisher<HostAndPort> {
        private final String serviceName;

        private SrvRecordPublisher(String serviceName) {
            this.serviceName = requireNonNull(serviceName);
        }

        @Override
        public String toString() {
            return serviceName;
        }

        @Override
        protected AbstractDnsSubscription newSubscription(
                final Subscriber<? super Iterable<ServiceDiscovererEvent<HostAndPort>>> subscriber) {
            return new AbstractDnsSubscription(true, subscriber) {
                @Override
                protected Future<DnsAnswer<HostAndPort>> doDnsQuery() {
                    Promise<DnsAnswer<HostAndPort>> promise = ImmediateEventExecutor.INSTANCE.newPromise();
                    resolver.resolveAll(new DefaultDnsQuestion(serviceName, SRV))
                            .addListener((Future<? super List<DnsRecord>> completedFuture) -> {
                                Throwable cause = completedFuture.cause();
                                if (cause != null) {
                                    promise.setFailure(cause);
                                } else {
                                    final DnsAnswer<HostAndPort> dnsAnswer;
                                    long minTTLSeconds = Long.MAX_VALUE;
                                    try {
                                        @SuppressWarnings("unchecked")
                                        final List<DnsRecord> dnsRecords = (List<DnsRecord>) completedFuture.getNow();
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
                                        dnsAnswer = new DnsAnswer<>(hostAndPorts, SECONDS.toNanos(minTTLSeconds));
                                    } catch (Throwable cause2) {
                                        promise.setFailure(cause2);
                                        return;
                                    }
                                    promise.setSuccess(dnsAnswer);
                                }
                            });
                    return promise;
                }

                @Nullable
                @Override
                protected List<ServiceDiscovererEvent<HostAndPort>> calculateDifference(
                        final List<HostAndPort> previousList, final List<HostAndPort> newList) {
                    return ServiceDiscovererUtils.calculateDifference(
                            previousList, newList, HOST_AND_PORT_COMPARATOR);
                }
            };
        }
    }

    private class ARecordPublisher extends AbstractDnsPublisher<InetAddress> {
        private final String inetHost;
        private final boolean cancelClearsSubscription;

        ARecordPublisher(boolean cancelClearsSubscription, String inetHost) {
            this.cancelClearsSubscription = cancelClearsSubscription;
            this.inetHost = inetHost;
        }

        @Override
        public String toString() {
            return inetHost;
        }

        @Override
        protected AbstractDnsSubscription newSubscription(
                final Subscriber<? super Iterable<ServiceDiscovererEvent<InetAddress>>> subscriber) {
            return new AbstractDnsSubscription(cancelClearsSubscription, subscriber) {
                @Override
                protected Future<DnsAnswer<InetAddress>> doDnsQuery() {
                    ttlCache.prepareForResolution(inetHost);
                    Promise<DnsAnswer<InetAddress>> dnsAnswerPromise = ImmediateEventExecutor.INSTANCE.newPromise();
                    resolver.resolveAll(inetHost).addListener(completedFuture -> {
                        Throwable cause = completedFuture.cause();
                        if (cause != null) {
                            dnsAnswerPromise.setFailure(cause);
                        } else {
                            final DnsAnswer<InetAddress> dnsAnswer;
                            try {
                                @SuppressWarnings("unchecked")
                                final List<InetAddress> addresses = (List<InetAddress>) completedFuture.getNow();
                                dnsAnswer = new DnsAnswer<>(addresses, SECONDS.toNanos(ttlCache.minTtl(inetHost)));
                            } catch (Throwable cause2) {
                                dnsAnswerPromise.setFailure(cause2);
                                return;
                            }
                            dnsAnswerPromise.setSuccess(dnsAnswer);
                        }
                    });
                    return dnsAnswerPromise;
                }

                @Nullable
                @Override
                protected List<ServiceDiscovererEvent<InetAddress>> calculateDifference(
                        final List<InetAddress> previousList, final List<InetAddress> newList) {
                    return ServiceDiscovererUtils.calculateDifference(
                            previousList, newList, INET_ADDRESS_COMPARATOR);
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
            extends SubscribablePublisher<Iterable<ServiceDiscovererEvent<T>>> {
        @Nullable
        private AbstractDnsSubscription subscription;

        protected abstract AbstractDnsSubscription
                newSubscription(Subscriber<? super Iterable<ServiceDiscovererEvent<T>>> subscriber);

        @Override
        protected final void handleSubscribe(
                final Subscriber<? super Iterable<ServiceDiscovererEvent<T>>> subscriber) {
            if (nettyIoExecutor.isCurrentThreadEventLoop()) {
                handleSubscribe0(subscriber);
            } else {
                nettyIoExecutor.asExecutor().execute(() -> handleSubscribe0(subscriber));
            }
        }

        private void handleSubscribe0(
                final Subscriber<? super Iterable<ServiceDiscovererEvent<T>>> subscriber) {
            assertInEventloop();

            if (subscription != null) {
                deliverErrorFromSource(subscriber, new DuplicateSubscribeException(subscription, subscriber));
            } else if (closed) {
                deliverErrorFromSource(subscriber, new ClosedServiceDiscovererException(DefaultDnsClient.this +
                                " has been closed!"));
            } else {
                subscription = newSubscription(subscriber);
                subscriber.onSubscribe(subscription);
            }
        }

        @Nullable
        final List<ServiceDiscovererEvent<T>> close0() {
            assertInEventloop();

            if (subscription != null) {
                List<ServiceDiscovererEvent<T>> events = subscription.cancelClearsSubscription ? null :
                        subscription.generateInactiveEvent();

                // this method call will null out the subscription reference after it is terminated.
                subscription.closeAndTerminate0();

                return events;
            }
            return null;
        }

        abstract class AbstractDnsSubscription implements Subscription {
            private final Subscriber<? super Iterable<ServiceDiscovererEvent<T>>> subscriber;
            /**
             * A record resolution for SRV uses flatMapSingle and firstOnOrError which will cancel inline with the
             * first onNext signal. However when we encounter a SRV in-active event we need to manually generate a A*
             * record in-active event corresponding for the currently active A* addresses. This means when the cancel
             * occurs we need to preserve the {@link #activeAddresses} state for {@link #generateInactiveEvent()}.
             */
            private final boolean cancelClearsSubscription;
            private long pendingRequests;
            private List<T> activeAddresses;
            private long resolveDoneNoScheduleTime;
            @Nullable
            private Cancellable cancellableForQuery;
            private long ttlNanos;

            AbstractDnsSubscription(boolean cancelClearsSubscription,
                                    final Subscriber<? super Iterable<ServiceDiscovererEvent<T>>> subscriber) {
                this.cancelClearsSubscription = cancelClearsSubscription;
                this.subscriber = subscriber;
                activeAddresses = emptyList();
                ttlNanos = -1;
            }

            protected abstract Future<DnsAnswer<T>> doDnsQuery();

            @Nullable
            protected abstract List<ServiceDiscovererEvent<T>>
                    calculateDifference(List<T> previousList, List<T> newList);

            @Override
            public final void request(final long n) {
                if (nettyIoExecutor.isCurrentThreadEventLoop()) {
                    request0(n);
                } else {
                    nettyIoExecutor.asExecutor().execute(() -> request0(n));
                }
            }

            @Override
            public final void cancel() {
                if (nettyIoExecutor.isCurrentThreadEventLoop()) {
                    cancel0();
                } else {
                    nettyIoExecutor.asExecutor().execute(this::cancel0);
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
                        doQuery0();
                    } else {
                        final long durationNs = nanoTime() - resolveDoneNoScheduleTime;
                        if (durationNs > ttlNanos) {
                            doQuery0();
                        } else {
                            scheduleQuery0(ttlNanos - durationNs);
                        }
                    }
                } else if (cancellableForQuery == TERMINATE_ON_NEXT_REQUEST_N) {
                    assert pendingRequests > 0;
                    tryTerminateOnComplete(); // we were waiting for demand, and got it. time to deliver the data.
                }
            }

            private void doQuery0() {
                assertInEventloop();

                if (closed) {
                    // best effort check to cleanup state after close.
                    handleTerminalError0(new ClosedServiceDiscovererException(DefaultDnsClient.this +
                            " has been closed!"));
                } else {
                    LOGGER.trace("DnsClient {}, querying DNS for {}", DefaultDnsClient.this, AbstractDnsPublisher.this);
                    final Future<DnsAnswer<T>> addressFuture = doDnsQuery();
                    cancellableForQuery = () -> addressFuture.cancel(true);
                    if (addressFuture.isDone()) {
                        handleResolveDone0(addressFuture);
                    } else {
                        addressFuture.addListener((FutureListener<DnsAnswer<T>>) this::handleResolveDone0);
                    }
                }
            }

            private void cancel0() {
                assertInEventloop();
                if (cancellableForQuery != null) {
                    cancellableForQuery.cancel();
                }
                if (cancelClearsSubscription) {
                    clearState();
                }
            }

            private void clearState() {
                cancellableForQuery = TERMINATED;
                subscription = null;
            }

            private void closeAndTerminate0() {
                if (cancellableForQuery != null) {
                    cancellableForQuery.cancel();
                }
                tryTerminateOnComplete();
            }

            private void tryTerminateOnComplete() {
                final boolean deliverTerminal;
                try {
                    deliverTerminal = clearAddressesAndPropagateRemovalEvents();
                } catch (Throwable cause) {
                    clearState(); // must null out subscription before terminating to allow for re-subscribe.
                    safeOnError(subscriber, cause);
                    return;
                }
                if (deliverTerminal) {
                    clearState(); // must null out subscription before terminating to allow for re-subscribe.
                    safeOnComplete(subscriber);
                } else {
                    cancellableForQuery = TERMINATE_ON_NEXT_REQUEST_N;
                }
            }

            private void scheduleQuery0(final long nanos) {
                assertInEventloop();

                LOGGER.trace("DnsClient {}, scheduling DNS query for {} after {} nanos.",
                        DefaultDnsClient.this, AbstractDnsPublisher.this, nanos);

                // This value is coming from DNS TTL for which the unit is seconds and the minimum value we accept
                // in the builder is 1 second.
                cancellableForQuery = nettyIoExecutor.asExecutor().schedule(
                        this::doQuery0, nanos, NANOSECONDS);
            }

            private void handleResolveDone0(final Future<DnsAnswer<T>> addressFuture) {
                assertInEventloop();
                assert pendingRequests > 0;
                if (cancellableForQuery == TERMINATED) {
                    return;
                } else if (cancellableForQuery == TERMINATE_ON_NEXT_REQUEST_N) {
                    tryTerminateOnComplete();
                    return;
                }
                final Throwable cause = addressFuture.cause();
                if (cause != null) {
                    boolean deliverTerminal = true;
                    try {
                        deliverTerminal = !invalidateHostsOnDnsFailure.test(cause) ||
                                clearAddressesAndPropagateRemovalEvents();
                    } catch (Throwable cause2) {
                        logUnexpectedException(cause2);
                    }

                    if (deliverTerminal) {
                        cancel0();
                        safeOnError(subscriber, cause);
                    } else {
                        cancellableForQuery = TERMINATE_ON_NEXT_REQUEST_N;
                    }
                } else {
                    // DNS lookup can return duplicate InetAddress
                    DnsAnswer<T> dnsAnswer = addressFuture.getNow();
                    final List<T> addresses = dnsAnswer.answer();
                    final List<ServiceDiscovererEvent<T>> events = calculateDifference(activeAddresses, addresses);
                    ttlNanos = dnsAnswer.ttlNanos();
                    if (events != null) {
                        activeAddresses = addresses;
                        if (--pendingRequests > 0) {
                            scheduleQuery0(ttlNanos);
                        } else {
                            resolveDoneNoScheduleTime = nanoTime();
                            cancellableForQuery = null;
                        }
                        try {
                            LOGGER.debug("DnsClient {}, sending events for address: {} (size {}) {}.",
                                    DefaultDnsClient.this, AbstractDnsPublisher.this, events.size(), events);

                            subscriber.onNext(events);
                        } catch (final Throwable error) {
                            handleTerminalError0(error);
                        }
                    } else {
                        LOGGER.trace("DnsClient {}, resolution done but no changes for address: {} (size {}) {}.",
                                DefaultDnsClient.this, AbstractDnsPublisher.this, activeAddresses.size(),
                                activeAddresses);

                        scheduleQuery0(ttlNanos);
                    }
                }
            }

            private void handleTerminalError0(final Throwable cause) {
                assertInEventloop();
                if (cancellableForQuery != TERMINATED) {
                    cancel0();
                    safeOnError(subscriber, cause);
                }
            }

            private void logUnexpectedException(Throwable cause) {
                LOGGER.warn("Exception from subscriber {} while handling error in DNS subscription {}",
                        subscriber, this, cause);
            }

            private boolean clearAddressesAndPropagateRemovalEvents() {
                assertInEventloop();

                if (activeAddresses.isEmpty()) {
                    return true;
                } else if (pendingRequests > 0) {
                    --pendingRequests;
                    subscriber.onNext(generateInactiveEvent());
                    return true;
                }
                return false;
            }

            @SuppressWarnings("ForLoopReplaceableByForEach")
            private List<ServiceDiscovererEvent<T>> generateInactiveEvent() {
                final List<ServiceDiscovererEvent<T>> events = new ArrayList<>(activeAddresses.size());
                if (activeAddresses instanceof RandomAccess) {
                    for (int i = 0; i < activeAddresses.size(); ++i) {
                        events.add(new DefaultServiceDiscovererEvent<>(activeAddresses.get(i), false));
                    }
                } else {
                    for (final T address : activeAddresses) {
                        events.add(new DefaultServiceDiscovererEvent<>(address, false));
                    }
                }
                activeAddresses = emptyList();
                return events;
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
