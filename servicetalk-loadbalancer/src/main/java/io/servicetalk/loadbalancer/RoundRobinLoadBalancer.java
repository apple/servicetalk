/*
 * Copyright © 2018-2020 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.PublisherSource.Processor;
import io.servicetalk.concurrent.PublisherSource.Subscriber;
import io.servicetalk.concurrent.PublisherSource.Subscription;
import io.servicetalk.concurrent.api.AsyncCloseable;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.CompositeCloseable;
import io.servicetalk.concurrent.api.ListenableAsyncCloseable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.internal.SequentialCancellable;
import io.servicetalk.concurrent.internal.ThrowableUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.function.Function;
import java.util.function.Predicate;

import static io.servicetalk.client.api.LoadBalancerReadyEvent.LOAD_BALANCER_NOT_READY_EVENT;
import static io.servicetalk.client.api.LoadBalancerReadyEvent.LOAD_BALANCER_READY_EVENT;
import static io.servicetalk.concurrent.api.AsyncCloseables.newCompositeCloseable;
import static io.servicetalk.concurrent.api.AsyncCloseables.toAsyncCloseable;
import static io.servicetalk.concurrent.api.Completable.mergeAllDelayError;
import static io.servicetalk.concurrent.api.Processors.newPublisherProcessorDropHeadOnOverflow;
import static io.servicetalk.concurrent.api.Single.defer;
import static io.servicetalk.concurrent.api.Single.failed;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.concurrent.api.SourceAdapters.fromSource;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
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
    private static final List<?> CLOSED_LIST = new ArrayList<>(0);

    @SuppressWarnings("rawtypes")
    private static final AtomicReferenceFieldUpdater<RoundRobinLoadBalancer, List> activeHostsUpdater =
            newUpdater(RoundRobinLoadBalancer.class, List.class, "activeHosts");
    @SuppressWarnings("rawtypes")
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

    @SuppressWarnings("unused")
    private volatile int index;
    private volatile List<Host<ResolvedAddress, C>> activeHosts = emptyList();

    private final Publisher<Object> eventStream;
    private final SequentialCancellable discoveryCancellable = new SequentialCancellable();
    private final ConnectionFactory<ResolvedAddress, ? extends C> connectionFactory;
    private final ListenableAsyncCloseable asyncCloseable;

    /**
     * Creates a new instance.
     *
     * @param eventPublisher    provides a stream of addresses to connect to.
     * @param connectionFactory a function which creates new connections.
     */
    public RoundRobinLoadBalancer(final Publisher<? extends ServiceDiscovererEvent<ResolvedAddress>> eventPublisher,
                                  final ConnectionFactory<ResolvedAddress, ? extends C> connectionFactory) {
        Processor<Object, Object> eventStreamProcessor = newPublisherProcessorDropHeadOnOverflow(32);
        this.eventStream = fromSource(eventStreamProcessor);
        this.connectionFactory = requireNonNull(connectionFactory);

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

            @Override
            public void onNext(final ServiceDiscovererEvent<ResolvedAddress> event) {
                LOGGER.debug("Load balancer {}, received new ServiceDiscoverer event {}.", RoundRobinLoadBalancer.this,
                        event);
                @SuppressWarnings("unchecked")
                final List<Host<ResolvedAddress, C>> activeAddresses =
                    activeHostsUpdater.updateAndGet(RoundRobinLoadBalancer.this, oldHosts -> {
                        if (oldHosts == CLOSED_LIST) {
                            return CLOSED_LIST;
                        }
                        final ResolvedAddress addr = requireNonNull(event.address());
                        @SuppressWarnings("unchecked")
                        final List<Host<ResolvedAddress, C>> oldHostsTyped = (List<Host<ResolvedAddress, C>>) oldHosts;
                        if (event.isAvailable()) {
                            if (oldHostsTyped.isEmpty()) {
                                return singletonList(new Host<>(addr));
                            }
                            final List<Host<ResolvedAddress, C>> newHosts = new ArrayList<>(oldHostsTyped.size() + 1);
                            newHosts.addAll(oldHostsTyped);
                            newHosts.add(new Host<>(addr));
                            return newHosts;
                        } else if (oldHostsTyped.isEmpty()) {
                            return emptyList();
                        } else {
                            final List<Host<ResolvedAddress, C>> newHosts = new ArrayList<>(oldHostsTyped.size() - 1);
                            for (int i = 0; i < oldHostsTyped.size(); ++i) {
                                final Host<ResolvedAddress, C> host = oldHostsTyped.get(i);
                                if (host.address.equals(addr)) {
                                    host.markInactive();
                                    for (int x = i + 1; x < oldHostsTyped.size(); ++x) {
                                        newHosts.add(oldHostsTyped.get(x));
                                    }
                                    return newHosts.isEmpty() ? emptyList() : newHosts;
                                } else {
                                    newHosts.add(host);
                                }
                            }
                            return newHosts;
                        }
                    });

                LOGGER.debug("Load balancer {} now using {} addresses: {}", RoundRobinLoadBalancer.this,
                        activeAddresses.size(), activeAddresses);

                if (event.isAvailable()) {
                    if (activeAddresses.size() == 1) {
                        eventStreamProcessor.onNext(LOAD_BALANCER_READY_EVENT);
                    }
                } else if (activeAddresses.isEmpty()) {
                    eventStreamProcessor.onNext(LOAD_BALANCER_NOT_READY_EVENT);
                }
            }

            @Override
            public void onError(final Throwable t) {
                List<Host<ResolvedAddress, C>> hosts = activeHosts;
                eventStreamProcessor.onError(t);
                LOGGER.error(
                        "Load balancer {}. Service discoverer {} emitted an error. Last seen addresses (size {}) {}",
                        RoundRobinLoadBalancer.this, eventPublisher, hosts.size(), hosts, t);
            }

            @Override
            public void onComplete() {
                List<Host<ResolvedAddress, C>> hosts = activeHosts;
                eventStreamProcessor.onComplete();
                LOGGER.error("Load balancer {}. Service discoverer {} completed. Last seen addresses (size {}) {}",
                        RoundRobinLoadBalancer.this, eventPublisher, hosts.size(), hosts);
            }
        });
        asyncCloseable = toAsyncCloseable(graceful -> {
            @SuppressWarnings("unchecked")
            List<Host<ResolvedAddress, C>> currentList = activeHostsUpdater.getAndSet(this, CLOSED_LIST);
            discoveryCancellable.cancel();
            eventStreamProcessor.onComplete();
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
    RoundRobinLoadBalancerFactory<ResolvedAddress, C> newRoundRobinFactory() {
        return new RoundRobinLoadBalancerFactory<>();
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
        final List<Host<ResolvedAddress, C>> activeHosts = this.activeHosts;
        if (activeHosts.isEmpty()) {
            return activeHosts == CLOSED_LIST ? failedLBClosed() :
                // This is the case when SD has emitted some items but none of the hosts are active.
                failed(StacklessNoAvailableHostException.newInstance(
                    "No hosts are available to connect.", RoundRobinLoadBalancer.class, "selectConnection0(...)"));
        }

        final int cursor = (indexUpdater.getAndIncrement(this) & Integer.MAX_VALUE) % activeHosts.size();
        final Host<ResolvedAddress, C> host = activeHosts.get(cursor);
        assert host != null : "Host can't be null.";
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

        // No connection was selected: create a new one.
        // This LB implementation does not automatically provide TransportObserver. Therefore, we pass "null" here.
        // Users can apply a ConnectionFactoryFilter if they need to override this "null" value with TransportObserver.
        return connectionFactory.newConnection(host.address, null)
                .flatMap(newCnx -> {
                    // Invoke the selector before adding the connection to the pool, otherwise, connection can be used
                    // concurrently and hence a new connection can be rejected by the selector.
                    if (!selector.test(newCnx)) {
                        // Failure in selection could be temporary, hence add it to the queue and be consistent with the
                        // fact that select failure does not close a connection.
                        return newCnx.closeAsync().concat(failed(new ConnectionRejectedException(
                                "Newly created connection " + newCnx + " rejected by the selection filter.")));
                    }
                    if (host.addConnection(newCnx)) {
                        return succeeded(newCnx);
                    }
                    return newCnx.closeAsync().concat(this.activeHosts == CLOSED_LIST ? failedLBClosed() :
                            failed(new ConnectionRejectedException("Failed to add newly created connection for host: " +
                                    host.address + ", host inactive? " + host.isInactive())));
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

    /**
     * {@link LoadBalancerFactory} for {@link RoundRobinLoadBalancer}.
     *
     * @param <ResolvedAddress> The resolved address type.
     * @param <C> The type of connection.
     */
    public static final class RoundRobinLoadBalancerFactory<ResolvedAddress, C extends LoadBalancedConnection>
            implements LoadBalancerFactory<ResolvedAddress, C> {

        @Override
        public <T extends C> LoadBalancer<T> newLoadBalancer(
                final Publisher<? extends ServiceDiscovererEvent<ResolvedAddress>> eventPublisher,
                final ConnectionFactory<ResolvedAddress, T> connectionFactory) {
            return new RoundRobinLoadBalancer<>(eventPublisher, connectionFactory);
        }
    }

    // Visible for testing
    List<Entry<ResolvedAddress, List<C>>> activeAddresses() {
        return activeHosts.stream().map(Host::asEntry).collect(toList());
    }

    private static class Host<Addr, C extends ListenableAsyncCloseable> implements AsyncCloseable {
        @SuppressWarnings("rawtypes")
        private static final AtomicReferenceFieldUpdater<Host, List> connectionsUpdater =
                AtomicReferenceFieldUpdater.newUpdater(Host.class, List.class, "connections");

        final Addr address;
        private volatile List<C> connections = emptyList();

        Host(Addr address) {
            this.address = requireNonNull(address);
        }

        void markInactive() {
            @SuppressWarnings("unchecked")
            final List<C> toRemove = connectionsUpdater.getAndSet(this, CLOSED_LIST);
            for (C conn : toRemove) {
                conn.closeAsyncGracefully().subscribe();
            }
        }

        boolean isInactive() {
            return connections == CLOSED_LIST;
        }

        boolean addConnection(C connection) {
            for (;;) {
                List<C> existing = this.connections;
                if (existing == CLOSED_LIST) {
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
                for (;;) {
                    final List<C> existing = connections;
                    if (existing == CLOSED_LIST) {
                        break;
                    }
                    ArrayList<C> connectionRemoved = new ArrayList<>(existing);
                    if (!connectionRemoved.remove(connection) ||
                            connectionsUpdater.compareAndSet(this, existing, connectionRemoved)) {
                        break;
                    }
                }
            }).subscribe();
            return true;
        }

        // Used for testing only
        Entry<Addr, List<C>> asEntry() {
            return new SimpleImmutableEntry<>(address, new ArrayList<>(connections));
        }

        @Override
        public Completable closeAsync() {
            return doClose(AsyncCloseable::closeAsync);
        }

        @Override
        public Completable closeAsyncGracefully() {
            return doClose(AsyncCloseable::closeAsyncGracefully);
        }

        @SuppressWarnings("unchecked")
        private Completable doClose(final Function<? super C, Completable> closeFunction) {
            return defer(() -> succeeded((List<C>) connectionsUpdater.getAndSet(this, CLOSED_LIST)))
                    .flatMapCompletable(list -> mergeAllDelayError(list.stream().map(closeFunction)::iterator));
        }

        @Override
        public String toString() {
            return "Host{" +
                    "address=" + address +
                    ", removed=" + (connections == CLOSED_LIST) +
                    '}';
        }
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

    private static <T> Single<T> failedLBClosed() {
        return failed(new IllegalStateException("LoadBalancer has closed"));
    }
}
