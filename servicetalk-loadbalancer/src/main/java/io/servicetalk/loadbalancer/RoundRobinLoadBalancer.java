/*
 * Copyright © 2018-2019 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.loadbalancer;

import io.servicetalk.client.api.ConnectionFactory;
import io.servicetalk.client.api.ConnectionRejectedException;
import io.servicetalk.client.api.LoadBalancedConnection;
import io.servicetalk.client.api.LoadBalancer;
import io.servicetalk.client.api.LoadBalancerFactory;
import io.servicetalk.client.api.NoAvailableHostException;
import io.servicetalk.client.api.ServiceDiscovererEvent;
import io.servicetalk.concurrent.PublisherSource.Subscriber;
import io.servicetalk.concurrent.PublisherSource.Subscription;
import io.servicetalk.concurrent.api.AsyncCloseable;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.CompositeCloseable;
import io.servicetalk.concurrent.api.ListenableAsyncCloseable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.internal.SpScPublisherProcessor;
import io.servicetalk.concurrent.internal.SequentialCancellable;

import io.servicetalk.concurrent.internal.ThrowableUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.function.Predicate;
import javax.annotation.Nullable;

import static io.servicetalk.client.api.LoadBalancerReadyEvent.LOAD_BALANCER_NOT_READY_EVENT;
import static io.servicetalk.client.api.LoadBalancerReadyEvent.LOAD_BALANCER_READY_EVENT;
import static io.servicetalk.concurrent.api.AsyncCloseables.newCompositeCloseable;
import static io.servicetalk.concurrent.api.AsyncCloseables.toAsyncCloseable;
import static io.servicetalk.concurrent.api.Completable.mergeAllDelayError;
import static io.servicetalk.concurrent.api.Single.defer;
import static io.servicetalk.concurrent.api.Single.failed;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static java.util.Collections.binarySearch;
import static java.util.Collections.emptyList;
import static java.util.Comparator.comparing;
import static java.util.Comparator.comparingInt;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.atomic.AtomicIntegerFieldUpdater.newUpdater;
import static java.util.concurrent.atomic.AtomicReferenceFieldUpdater.newUpdater;
import static java.util.stream.Collectors.toList;

/**
 * A {@link LoadBalancer} that uses a round robin strategy for selecting addresses. It has the following behaviour:
 * <ul>
 * <li>Round robining is done at address level.</li>
 * <li>Connections are created lazily, without any concurrency control on their creation.
 * This can lead to over-provisioning connections when dealing with a requests surge.</li>
 * <li>Existing connections are reused unless a selector passed to {@link #selectConnection(Predicate)} suggests
 * otherwise.
 * This can lead to situations where connections will be used to their maximum capacity (for example in the context of
 * pipelining) before new connections are created.</li>
 * <li>Closed connections are automatically pruned.</li>
 * </ul>
 *
 * @param <ResolvedAddress> The resolved address type.
 * @param <C> The type of connection.
 */
public final class RoundRobinLoadBalancer<ResolvedAddress, C extends LoadBalancedConnection>
        implements LoadBalancer<C> {

    private static final Logger LOGGER = LoggerFactory.getLogger(RoundRobinLoadBalancer.class);

    private static final AtomicReferenceFieldUpdater<RoundRobinLoadBalancer, List> activeHostsUpdater =
            newUpdater(RoundRobinLoadBalancer.class, List.class, "activeHosts");
    private static final AtomicIntegerFieldUpdater<RoundRobinLoadBalancer> indexUpdater =
            newUpdater(RoundRobinLoadBalancer.class, "index");

    /**
     * With a relatively small number of connections we can minimize connection creation under moderate concurrency by
     * exhausting the full search space without sacrificing too much latency caused by the cost of a CAS operation per
     * selection attempt.
     */
    private static final int MIN_SEARCH_SPACE = 64;

    /**
     * For larger search spaces, due to the cost of a CAS operation per selection attempt we see diminishing returns for
     * trying to locate an available connection when most connections are in use. This increases tail latencies, thus
     * after some number of failed attempts it appears to be more beneficial to open a new connection instead.
     * <p>
     * The current heuristics were chosen based on a set of benchmarks under various circumstances, low connection
     * counts, larger connection counts, low connection churn, high connection churn.
     */
    private static final float SEARCH_FACTOR = 0.75f;

    private volatile boolean closed;
    @SuppressWarnings("unused")
    private volatile int index;
    private volatile List<Host<ResolvedAddress, C>> activeHosts = emptyList();

    private final SpScPublisherProcessor<Object> eventStream = new SpScPublisherProcessor<>(32);
    private final SequentialCancellable discoveryCancellable = new SequentialCancellable();
    private final ConnectionFactory<ResolvedAddress, ? extends C> connectionFactory;
    private final ListenableAsyncCloseable asyncCloseable;

    /**
     * Creates a new instance.
     *
     * @param eventPublisher    provides a stream of addresses to connect to.
     * @param connectionFactory a function which creates new connections.
     * @param comparator        used to compare addresses for lookup/iteration during the connection attempt phase.
     */
    public RoundRobinLoadBalancer(final Publisher<? extends ServiceDiscovererEvent<ResolvedAddress>> eventPublisher,
                                  final ConnectionFactory<ResolvedAddress, ? extends C> connectionFactory,
                                  final Comparator<ResolvedAddress> comparator) {

        this.connectionFactory = requireNonNull(connectionFactory);

        final Comparator<Host<ResolvedAddress, C>> activeAddressComparator =
                comparing(host -> host instanceof MutableAddressHost ?
                        ((MutableAddressHost<ResolvedAddress, C>) host).mutableAddress : host.address, comparator);

        toSource(eventPublisher).subscribe(new Subscriber<ServiceDiscovererEvent<ResolvedAddress>>() {

            @Override
            public void onSubscribe(final Subscription s) {
                // We request max value here to make sure we do not access Subscription concurrently
                // (requestN here and cancel from discoveryCancellable). If we request-1 in onNext we would have to wrap
                // the Subscription in a ConcurrentSubscription which is costly.
                // Since, we synchronously process onNexts we do not really care about flow control.
                s.request(Long.MAX_VALUE);
                discoveryCancellable.nextCancellable(s);
            }

            @SuppressWarnings("unchecked")
            @Override
            public void onNext(final ServiceDiscovererEvent<ResolvedAddress> event) {
                LOGGER.debug("Load balancer {}, received new ServiceDiscoverer event {}.", RoundRobinLoadBalancer.this,
                        event);
                final List<Host<ResolvedAddress, C>> activeAddresses =
                        activeHostsUpdater.updateAndGet(RoundRobinLoadBalancer.this, currentAddresses -> {
                            final List<Host<ResolvedAddress, C>> refreshedAddresses = new ArrayList<>(currentAddresses);
                            final MutableAddressHost<ResolvedAddress, C> searchHost = new MutableAddressHost();

                            searchHost.mutableAddress = event.address();
                            // Binary search because any insertion is performed at the index returned by the search,
                            // which is consistent with the ordering defined by the comparator
                            final int i = binarySearch(refreshedAddresses, searchHost, activeAddressComparator);

                            if (event.isAvailable()) {
                                if (i < 0) {
                                    refreshedAddresses.add(-i - 1, new Host(event.address()));
                                }
                            } else if (i >= 0) {
                                Host<ResolvedAddress, C> removed = refreshedAddresses.remove(i);
                                if (removed != null) {
                                    removed.markInactive();
                                }
                            }

                            return refreshedAddresses;
                        });

                LOGGER.debug("Load balancer {} now using {} addresses: {}", RoundRobinLoadBalancer.this,
                        activeAddresses.size(), activeAddresses);

                if (event.isAvailable()) {
                    if (activeAddresses.size() == 1) {
                        eventStream.sendOnNext(LOAD_BALANCER_READY_EVENT);
                    }
                } else if (activeAddresses.isEmpty()) {
                    eventStream.sendOnNext(LOAD_BALANCER_NOT_READY_EVENT);
                }
            }

            @Override
            public void onError(final Throwable t) {
                List<Host<ResolvedAddress, C>> hosts = activeHosts;
                eventStream.sendOnError(t);
                LOGGER.error(
                        "Load balancer {}. Service discoverer {} emitted an error. Last seen addresses (size {}) {}",
                        RoundRobinLoadBalancer.this, eventPublisher, hosts.size(), hosts, t);
            }

            @Override
            public void onComplete() {
                List<Host<ResolvedAddress, C>> hosts = activeHosts;
                eventStream.sendOnComplete();
                LOGGER.error("Load balancer {}. Service discoverer {} completed. Last seen addresses (size {}) {}",
                        RoundRobinLoadBalancer.this, eventPublisher, hosts.size(), hosts);
            }
        });
        asyncCloseable = toAsyncCloseable(graceful -> {
            closed = true;
            discoveryCancellable.cancel();
            eventStream.sendOnComplete();
            @SuppressWarnings("unchecked")
            List<Host<ResolvedAddress, C>> currentList = activeHostsUpdater
                    .getAndSet(RoundRobinLoadBalancer.this, Collections.<Host<ResolvedAddress, C>>emptyList());
            CompositeCloseable cc = newCompositeCloseable().appendAll(currentList).appendAll(connectionFactory);
            return graceful ? cc.closeAsyncGracefully() : cc.closeAsync();
        });
    }

    /**
     * Create a {@link LoadBalancerFactory} that creates instances of {@link RoundRobinLoadBalancer}.
     * @param <ResolvedAddress> The resolved address type.
     * @param <C> The type of connection.
     * @return a {@link LoadBalancerFactory} that creates instances of {@link RoundRobinLoadBalancer}.
     */
    public static <ResolvedAddress, C extends LoadBalancedConnection>
    LoadBalancerFactory<ResolvedAddress, C> newRoundRobinFactory() {
        return (eventPublisher, connectionFactory) -> new RoundRobinLoadBalancer<>(eventPublisher,
                connectionFactory,
                comparingInt(Object::hashCode));
    }

    @Override
    public Single<C> selectConnection(Predicate<C> selector) {
        return defer(() -> selectConnection0(selector).subscribeShareContext());
    }

    @Override
    public Publisher<Object> eventStream() {
        return eventStream;
    }

    private Single<C> selectConnection0(Predicate<C> selector) {
        if (closed) {
            return failed(new IllegalStateException("LoadBalancer has closed"));
        }

        final List<Host<ResolvedAddress, C>> activeHosts = this.activeHosts;
        if (activeHosts.isEmpty()) {
            // This is the case when SD has emitted some items but none of the hosts are active.
            return failed(StacklessNoAvailableHostException.newInstance(
                    "No hosts are available to connect.",  RoundRobinLoadBalancer.class, "selectConnection0(...)"));
        }

        final int cursor = (indexUpdater.getAndIncrement(this) & Integer.MAX_VALUE) % activeHosts.size();
        final Host<ResolvedAddress, C> host = activeHosts.get(cursor);
        assert host != null : "Host can't be null.";
        assert host.address != null : "Host address can't be null.";
        final ThreadLocalRandom rnd = ThreadLocalRandom.current();

        // Try first to see if an existing connection can be used
        final List<C> connections = host.connections;
        final int size = connections.size();
        // With small enough search space, attempt all connections.
        // Back off after exploring most of the search space, it gives diminishing returns.
        final int attempts = size < MIN_SEARCH_SPACE ? size : (int) (size * SEARCH_FACTOR);
        for (int i = 0; i < attempts; i++) {
            final C connection = connections.get(rnd.nextInt(size));
            if (selector.test(connection)) {
                return succeeded(connection);
            }
        }

        // No connection was selected: create a new one
        return connectionFactory.newConnection(host.address)
                .flatMap(newCnx -> {
                    // Invoke the selector before adding the connection to the pool, otherwise, connection can be used
                    // concurrently and hence a new connection can be rejected by the selector.
                    if (!selector.test(newCnx)) {
                        newCnx.closeAsync().subscribe();
                        // Failure in selection could be temporary, hence add it to the queue and be consistent with the
                        // fact that select failure does not close a connection.
                        return failed(new ConnectionRejectedException("Newly created connection " + newCnx +
                                " rejected by the selection filter."));
                    }
                    if (host.addConnection(newCnx)) {
                        // If the LB has closed, we attempt to remove the connection, if the removal succeeds, close it.
                        // If we can't remove it, it means it's been removed concurrently and we assume that whoever
                        // removed it also closed it or that it has been removed as a consequence of closing.
                        if (closed) {

                            List<C> existing = connections;
                            for (;;) {
                                if (existing == Host.INACTIVE) {
                                    break;
                                }
                                ArrayList<C> connectionRemoved = new ArrayList<>(existing);
                                if (!connectionRemoved.remove(newCnx)) {
                                    break;
                                }
                                if (Host.connectionsUpdater.compareAndSet(host, existing, connectionRemoved)) {
                                    newCnx.closeAsync().subscribe();
                                    break;
                                }
                                existing = connections;
                            }

                            return failed(new IllegalStateException("LoadBalancer has closed"));
                        }
                        return succeeded(newCnx);
                    }
                    return failed(new ConnectionRejectedException("Failed to add newly created connection for host: " +
                            host.address + ", host inactive? " + (host.connections == Host.INACTIVE)));
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

    // Visible for testing
    List<Entry<ResolvedAddress, List<C>>> activeAddresses() {
        return activeHosts.stream().map(Host::asEntry).collect(toList());
    }

    private static class Host<Addr, C extends ListenableAsyncCloseable> implements AsyncCloseable {
        private static final AtomicReferenceFieldUpdater<Host, List> connectionsUpdater =
                AtomicReferenceFieldUpdater.newUpdater(Host.class, List.class, "connections");

        static final List INACTIVE = emptyList();
        private static final List NO_CONNECTIONS = new ArrayList(0);

        @Nullable
        final Addr address;
        @SuppressWarnings("unchecked")
        private volatile List<C> connections = NO_CONNECTIONS;

        Host() {
            address = null;
        }

        Host(Addr address) {
            this.address = address;
        }

        void markInactive() {
            @SuppressWarnings("unchecked")
            List<C> toRemove = connectionsUpdater.getAndSet(this, INACTIVE);
            for (C conn : toRemove) {
                conn.closeAsync().subscribe();
            }
        }

        boolean addConnection(C connection) {

            for (;;) {
                List<C> existing = this.connections;
                if (existing == INACTIVE) {
                    connection.closeAsync().subscribe();
                    return false;
                }
                ArrayList<C> connectionAdded = new ArrayList<>(existing);
                connectionAdded.add(connection);
                if (connectionsUpdater.compareAndSet(this, existing, connectionAdded)) {
                    break;
                }
            }

            // Instrument the new connection so we prune it on close
            connection.onClose().beforeFinally(() -> {
                List<C> existing = connections;
                for (;;) {
                    if (existing == INACTIVE) {
                        break;
                    }
                    ArrayList<C> connectionRemoved = new ArrayList<>(existing);
                    if (!connectionRemoved.remove(connection) ||
                            connectionsUpdater.compareAndSet(this, existing, connectionRemoved)) {
                        break;
                    }
                    existing = connections;
                }
            }).subscribe();
            return true;
        }

        // Used for testing only
        Entry<Addr, List<C>> asEntry() {
            assert address != null;
            return new SimpleImmutableEntry<>(address, new ArrayList<>(connections));
        }

        @Override
        public Completable closeAsync() {
            return mergeAllDelayError(connections.stream()
                    .map(AsyncCloseable::closeAsync)::iterator);
        }

        @Override
        public Completable closeAsyncGracefully() {
            return mergeAllDelayError(connections.stream()
                    .map(AsyncCloseable::closeAsyncGracefully)::iterator);
        }

        @Override
        public String toString() {
            return "Host{" +
                    "address=" + address +
                    ", removed=" + (connections == INACTIVE) +
                    '}';
        }
    }

    private static final class MutableAddressHost<Addr, C extends ListenableAsyncCloseable> extends Host<Addr, C> {
        @Nullable
        Addr mutableAddress;
    }

    private static final class StacklessNoAvailableHostException extends NoAvailableHostException {
        private StacklessNoAvailableHostException(final String message) {
            super(message);
        }

        @Override
        public Throwable fillInStackTrace() {
            return this;
        }

        public static StacklessNoAvailableHostException newInstance(String message, Class<?> clazz, String method) {
            return ThrowableUtils.unknownStackTrace(new StacklessNoAvailableHostException(message), clazz, method);
        }
    }
}
