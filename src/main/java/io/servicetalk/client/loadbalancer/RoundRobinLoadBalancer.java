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
package io.servicetalk.client.loadbalancer;

import io.servicetalk.client.api.ConnectionFactory;
import io.servicetalk.client.api.ConnectionRejectedException;
import io.servicetalk.client.api.LoadBalancer;
import io.servicetalk.client.api.NoAvailableHostException;
import io.servicetalk.client.api.RetryableException;
import io.servicetalk.client.api.ServiceDiscoverer;
import io.servicetalk.concurrent.api.AsyncCloseable;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.CompletableProcessor;
import io.servicetalk.concurrent.api.ListenableAsyncCloseable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.internal.SequentialCancellable;

import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.function.Function;
import java.util.stream.Stream;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Single.error;
import static io.servicetalk.concurrent.api.Single.success;
import static io.servicetalk.concurrent.internal.ThrowableUtil.unknownStackTrace;
import static java.util.Collections.binarySearch;
import static java.util.Collections.emptyList;
import static java.util.Comparator.comparing;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.atomic.AtomicIntegerFieldUpdater.newUpdater;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Stream.concat;

/**
 * A {@link LoadBalancer} that uses a round robin strategy for selecting addresses. It has the following behaviour:
 * <ul>
 * <li>Round robining is done at address level.</li>
 * <li>Connections are created lazily, without any concurrency control on their creation.
 * This can lead to over-provisioning connections when dealing with a requests surge.</li>
 * <li>Existing connections are reused unless a selector passed to {@link #selectConnection(Function)} suggests otherwise.
 * This can lead to situations where connections will be used to their maximum capacity (for example in the context of pipelining)
 * before new connections are created.</li>
 * <li>Closed connections are automatically pruned.</li>
 * </ul>
 *
 * @param <ResolvedAddress> The resolved address type.
 * @param <C> The type of connection.
 */
public final class RoundRobinLoadBalancer<ResolvedAddress, C extends ListenableAsyncCloseable> implements LoadBalancer<C> {

    private static final Logger LOGGER = LoggerFactory.getLogger(RoundRobinLoadBalancer.class);
    private static final IllegalStateException LB_CLOSED_SELECT_CNX_EXCEPTION =
            unknownStackTrace(new IllegalStateException("LoadBalancer has closed"), RoundRobinLoadBalancer.class, "selectConnection0(...)");
    private static final NoAvailableHostException NO_ACTIVE_HOSTS_SELECT_CNX_EXCEPTION =
            unknownStackTrace(new NoAvailableHostException("No hosts are available to connect."), RoundRobinLoadBalancer.class, "selectConnection0(...)");

    private static final AtomicReferenceFieldUpdater<RoundRobinLoadBalancer, List> activeHostsUpdater =
            AtomicReferenceFieldUpdater.newUpdater(RoundRobinLoadBalancer.class, List.class, "activeHosts");
    private static final AtomicIntegerFieldUpdater<RoundRobinLoadBalancer> closedUpdater =
            newUpdater(RoundRobinLoadBalancer.class, "closed");
    private static final AtomicIntegerFieldUpdater<RoundRobinLoadBalancer> indexUpdater =
            newUpdater(RoundRobinLoadBalancer.class, "index");

    @SuppressWarnings("unused")
    private volatile int closed;
    @SuppressWarnings("unused")
    private volatile int index;
    private volatile List<Host<ResolvedAddress, C>> activeHosts = emptyList();

    private final CompletableProcessor closeCompletable = new CompletableProcessor();
    private final SequentialCancellable discoveryCancellable = new SequentialCancellable();
    private final ConnectionFactory<ResolvedAddress, ? extends C> connectionFactory;

    /**
     * Creates a new instance.
     *
     * @param eventPublisher    provides a stream of addresses to connect to.
     * @param connectionFactory a function which creates new connections.
     * @param comparator        used to compare addresses for lookup/iteration during the connection attempt phase.
     */
    public RoundRobinLoadBalancer(final Publisher<? extends ServiceDiscoverer.Event<ResolvedAddress>> eventPublisher,
                                  final ConnectionFactory<ResolvedAddress, ? extends C> connectionFactory,
                                  final Comparator<ResolvedAddress> comparator) {

        this.connectionFactory = requireNonNull(connectionFactory);

        final Comparator<Host<ResolvedAddress, C>> activeAddressComparator =
                comparing(host -> host instanceof MutableAddressHost ? ((MutableAddressHost<ResolvedAddress, C>) host).mutableAddress : host.address, comparator);

        eventPublisher.subscribe(new org.reactivestreams.Subscriber<ServiceDiscoverer.Event<ResolvedAddress>>() {

            @Override
            public void onSubscribe(final Subscription s) {
                // We request max value here to make sure we do not access Subscription concurrently
                // (requestN here and cancel from discoveryCancellable). If we request-1 in onNext we would have to wrap
                // the Subscription in a ConcurrentSubscription which is costly.
                // Since, we synchronously process onNexts we do not really care about flow control.
                s.request(Long.MAX_VALUE);
                discoveryCancellable.setNextCancellable(s::cancel);
            }

            @SuppressWarnings("unchecked")
            @Override
            public void onNext(final ServiceDiscoverer.Event<ResolvedAddress> event) {
                final List<Host<ResolvedAddress, C>> activeAddresses =
                        activeHostsUpdater.updateAndGet(RoundRobinLoadBalancer.this, currentAddresses -> {
                            final List<Host<ResolvedAddress, C>> refreshedAddresses = new ArrayList<>(currentAddresses);
                            final MutableAddressHost<ResolvedAddress, C> searchHost = new MutableAddressHost();

                            searchHost.mutableAddress = event.getAddress();
                            // Binary search because any insertion is performed at the index returned by the search,
                            // which is consistent with the ordering defined by the comparator
                            final int i = binarySearch(refreshedAddresses, searchHost, activeAddressComparator);

                            if (event.isAvailable()) {
                                if (i < 0) {
                                    refreshedAddresses.add(-i - 1, new Host(event.getAddress()));
                                }
                            } else if (i >= 0) {
                                Host<ResolvedAddress, C> removed = refreshedAddresses.remove(i);
                                if (removed != null) {
                                    removed.markInactive();
                                }
                            }

                            return refreshedAddresses;
                        });

                LOGGER.debug("Running with {} active addresses", activeAddresses.size());
            }

            @Override
            public void onError(final Throwable t) {
                LOGGER.error("Service discoverer {} failure.", eventPublisher, t);
            }

            @Override
            public void onComplete() {
                LOGGER.debug("Service discoverer {} has completed.", eventPublisher);
            }
        });
    }

    @Override
    public <CC extends C> Single<CC> selectConnection(Function<C, CC> selector) {
        return new Single<CC>() {
            @Override
            protected void handleSubscribe(Subscriber<? super CC> subscriber) {
                selectConnection0(selector).subscribe(subscriber);
            }
        };
    }

    private <CC extends C> Single<CC> selectConnection0(Function<? super C, CC> selector) {
        if (closed != 0) {
            return error(LB_CLOSED_SELECT_CNX_EXCEPTION);
        }

        final List<Host<ResolvedAddress, C>> activeHosts = this.activeHosts;
        if (activeHosts.isEmpty()) {
            // This is the case when SD has emitted some items but none of the hosts are active.
            return error(NO_ACTIVE_HOSTS_SELECT_CNX_EXCEPTION);
        }

        final int cursor = indexUpdater.getAndUpdate(this, i -> (++i & Integer.MAX_VALUE)) % activeHosts.size();
        final Host<ResolvedAddress, C> host = activeHosts.get(cursor);
        assert host != null : "Host can't be null.";
        assert host.connections != null : "Host connections queue can't be null.";
        assert host.address != null : "Host address can't be null.";

        // Try first to see if an existing connection can be used
        for (final C connection : host.connections) {
            CC selection = selector.apply(connection);
            if (selection != null) {
                return success(selection);
            }
        }

        // No connection was selected: create a new one
        return connectionFactory.newConnection(host.address)
                .flatMap(newCnx -> {
                    // Invoke the selector before adding the connection to the pool, otherwise, connection can be used concurrently
                    // and hence a new connection can be rejected by the selector.
                    CC selection = selector.apply(newCnx);
                    if (selection == null) {
                        newCnx.closeAsync().subscribe();
                        // Failure in selection could be temporary, hence add it to the queue and be consistent with the
                        // fact that select failure does not close a connection.
                        return error(new ConnectionRejectedException("Newly created connection " + newCnx + " rejected by the selection filter."));
                    }
                    if (host.addConnection(newCnx)) {
                        // If the LB has closed, we attempt to remove the connection, if the removal succeeds, close it.
                        // If we can't remove it, it means it's been removed concurrently and we assume that whoever removed it also closed it
                        // or that it has been removed as a consequence of closing.
                        if (closed != 0) {
                            if (host.connections.remove(newCnx)) {
                                newCnx.closeAsync().subscribe();
                            }
                            return error(LB_CLOSED_SELECT_CNX_EXCEPTION);
                        }
                        return success(selection);
                    }
                    return error(new RetryableException("Failed to add newly created connection for host: " + host.address + ", host inactive? " + host.removed));
                });
    }

    @Override
    public Completable onClose() {
        return closeCompletable;
    }

    @Override
    public Completable closeAsync() {
        return new Completable() {
            @Override
            protected void handleSubscribe(final Subscriber subscriber) {
                if (closedUpdater.compareAndSet(RoundRobinLoadBalancer.this, 0, 1)) {
                    discoveryCancellable.cancel();
                    @SuppressWarnings("unchecked")
                    List<Host<ResolvedAddress, C>> currentList = activeHostsUpdater.getAndSet(RoundRobinLoadBalancer.this, Collections.<Host<ResolvedAddress, C>>emptyList());
                    completed().mergeDelayError(concat(currentList.stream().map(AsyncCloseable::closeAsync), Stream.of(connectionFactory.closeAsync()))::iterator).subscribe(closeCompletable);
                }

                closeCompletable.subscribe(subscriber);
            }
        };
    }

    // Visible for testing
    List<Entry<ResolvedAddress, List<C>>> getActiveAddresses() {
        return activeHosts.stream().map(Host::asEntry).collect(toList());
    }

    private static class Host<Addr, C extends ListenableAsyncCloseable> implements AsyncCloseable {

        @Nullable
        final Addr address;
        @Nullable
        final Queue<C> connections;
        private volatile boolean removed;

        Host() {
            address = null;
            connections = null;
        }

        Host(Addr address) {
            this.address = address;
            this.connections = new ConcurrentLinkedQueue<>();
        }

        void markInactive() {
            removed = true;
            assert connections != null;
            for (;;) {
                C next = connections.poll();
                if (next == null) {
                    return;
                }
                next.closeAsync().subscribe();
            }
        }

        boolean addConnection(C connection) {
            assert connections != null;
            final boolean added = connections.offer(connection);
            if (!added || removed) {
                // It could be that this host was removed concurrently and was not closed by markInactive().
                // So, we check removed again and remove from the queue + close.
                if (added && connections.remove(connection)) {
                    connection.closeAsync().subscribe();
                }
                return false;
            }

            // Instrument the new connection so we prune it on close
            connection.onClose().doBeforeFinally(() -> connections.remove(connection)).subscribe();
            return true;
        }

        // Used for testing only
        Entry<Addr, List<C>> asEntry() {
            assert address != null;
            assert connections != null;
            return new SimpleImmutableEntry<>(address, new ArrayList<>(connections));
        }

        @Override
        public Completable closeAsync() {
            return connections == null ? Completable.completed() : Completable.completed().mergeDelayError(connections.stream().map(AsyncCloseable::closeAsync)::iterator);
        }
    }

    private static final class MutableAddressHost<Addr, C extends ListenableAsyncCloseable> extends Host<Addr, C> {
        @Nullable
        Addr mutableAddress;
    }
}
