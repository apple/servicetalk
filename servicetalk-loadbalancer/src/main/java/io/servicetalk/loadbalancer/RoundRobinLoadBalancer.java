/*
 * Copyright © 2018-2021 Apple Inc. and the ServiceTalk project authors
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
import java.util.Arrays;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static io.servicetalk.client.api.LoadBalancerReadyEvent.LOAD_BALANCER_NOT_READY_EVENT;
import static io.servicetalk.client.api.LoadBalancerReadyEvent.LOAD_BALANCER_READY_EVENT;
import static io.servicetalk.concurrent.api.AsyncCloseables.newCompositeCloseable;
import static io.servicetalk.concurrent.api.AsyncCloseables.toAsyncCloseable;
import static io.servicetalk.concurrent.api.Completable.completed;
import static io.servicetalk.concurrent.api.Processors.newPublisherProcessorDropHeadOnOverflow;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.api.Single.defer;
import static io.servicetalk.concurrent.api.Single.failed;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.concurrent.api.SourceAdapters.fromSource;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.loadbalancer.RoundRobinLoadBalancerFactory.EAGER_CONNECTION_SHUTDOWN_ENABLED;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.atomic.AtomicIntegerFieldUpdater.newUpdater;
import static java.util.concurrent.atomic.AtomicReferenceFieldUpdater.newUpdater;
import static java.util.stream.Collectors.toList;

/**
 * Consult {@link RoundRobinLoadBalancerFactory} for a description of this {@link LoadBalancer} type.
 *
 * @param <ResolvedAddress> The resolved address type.
 * @param <C> The type of connection.
 * @deprecated Use {@link io.servicetalk.loadbalancer.RoundRobinLoadBalancerFactory} to provide instances of this
 * {@link LoadBalancer}. This class will become package-private in the future.
 */
@Deprecated
public final class RoundRobinLoadBalancer<ResolvedAddress, C extends LoadBalancedConnection>
        implements LoadBalancer<C> {

    private static final Logger LOGGER = LoggerFactory.getLogger(RoundRobinLoadBalancer.class);
    private static final List<?> CLOSED_LIST = new ArrayList<>(0);
    private static final Object[] EMPTY_ARRAY = new Object[0];

    @SuppressWarnings("rawtypes")
    private static final AtomicReferenceFieldUpdater<RoundRobinLoadBalancer, List> usedHostsUpdater =
            newUpdater(RoundRobinLoadBalancer.class, List.class, "usedHosts");
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
    private volatile List<Host<ResolvedAddress, C>> usedHosts = emptyList();

    private final Publisher<Object> eventStream;
    private final SequentialCancellable discoveryCancellable = new SequentialCancellable();
    private final ConnectionFactory<ResolvedAddress, ? extends C> connectionFactory;
    private final ListenableAsyncCloseable asyncCloseable;

    /**
     * Creates a new instance.
     *
     * @param eventPublisher provides a stream of addresses to connect to.
     * @param connectionFactory a function which creates new connections.
     * @deprecated Use {@link io.servicetalk.loadbalancer.RoundRobinLoadBalancerFactory} to build instances
     * of this {@link LoadBalancer}.
     */
    @Deprecated
    public RoundRobinLoadBalancer(final Publisher<? extends ServiceDiscovererEvent<ResolvedAddress>> eventPublisher,
                                  final ConnectionFactory<ResolvedAddress, ? extends C> connectionFactory) {
        this(eventPublisher, connectionFactory, EAGER_CONNECTION_SHUTDOWN_ENABLED);
    }

    /**
     * Creates a new instance.
     *
     * @param eventPublisher provides a stream of addresses to connect to.
     * @param connectionFactory a function which creates new connections.
     * @param eagerConnectionShutdown whether connections with {@link ServiceDiscovererEvent#isAvailable()} flag
     * set to {@code false} should be eagerly closed. When {@code false}, the expired addresses will be used
     * for sending requests, but new connections will not be requested, allowing the server to drive
     * the connection closure and shifting traffic to other addresses.
     */
    RoundRobinLoadBalancer(final Publisher<? extends ServiceDiscovererEvent<ResolvedAddress>> eventPublisher,
                           final ConnectionFactory<ResolvedAddress, ? extends C> connectionFactory,
                           final boolean eagerConnectionShutdown) {
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
                final List<Host<ResolvedAddress, C>> usedAddresses =
                    usedHostsUpdater.updateAndGet(RoundRobinLoadBalancer.this, oldHosts -> {
                        if (oldHosts == CLOSED_LIST) {
                            return CLOSED_LIST;
                        }
                        final ResolvedAddress addr = requireNonNull(event.address());
                        @SuppressWarnings("unchecked")
                        final List<Host<ResolvedAddress, C>> oldHostsTyped = (List<Host<ResolvedAddress, C>>) oldHosts;

                        if (eagerConnectionShutdown) {
                            if (event.isAvailable()) {
                                return addHostToList(oldHostsTyped, addr, false);
                            } else {
                                return listWithHostRemoved(oldHostsTyped, host -> {
                                    boolean match = host.address.equals(addr);
                                    if (match) {
                                        host.markClosed();
                                    }
                                    return match;
                                });
                            }
                        } else if (event.isAvailable()) {
                            return addHostToList(oldHostsTyped, addr, true);
                        } else if (oldHostsTyped.isEmpty()) {
                            return emptyList();
                        } else {
                            return markHostAsExpired(oldHostsTyped, addr);
                        }
                    });

                LOGGER.debug("Load balancer {} now using {} addresses: {}", RoundRobinLoadBalancer.this,
                        usedAddresses.size(), usedAddresses);

                if (event.isAvailable()) {
                    if (usedAddresses.size() == 1) {
                        eventStreamProcessor.onNext(LOAD_BALANCER_READY_EVENT);
                    }
                } else if (usedAddresses.isEmpty()) {
                    eventStreamProcessor.onNext(LOAD_BALANCER_NOT_READY_EVENT);
                }
            }

            private List<Host<ResolvedAddress, C>> markHostAsExpired(
                    final List<Host<ResolvedAddress, C>> oldHostsTyped, final ResolvedAddress addr) {
                for (Host<ResolvedAddress, C> host : oldHostsTyped) {
                    if (host.address.equals(addr)) {
                        // Host removal will be handled by the Host's onClose::afterFinally callback
                        host.markExpired();
                    }
                }
                return oldHostsTyped;
            }

            @SuppressWarnings("unchecked")
            private Host<ResolvedAddress, C> createHost(ResolvedAddress addr) {
                Host<ResolvedAddress, C> host = new Host<>(addr);
                if (!eagerConnectionShutdown) {
                    host.onClose().afterFinally(() -> {
                                usedHostsUpdater.updateAndGet(RoundRobinLoadBalancer.this,
                                        previousHosts ->
                                                listWithHostRemoved(previousHosts, current -> current == host));
                            }
                    ).subscribe();
                }
                return host;
            }

            private List<Host<ResolvedAddress, C>> addHostToList(
                    List<Host<ResolvedAddress, C>> oldHostsTyped, ResolvedAddress addr, boolean handleExpired) {
                if (oldHostsTyped.isEmpty()) {
                    return singletonList(createHost(addr));
                }

                if (handleExpired) {
                    for (Host<ResolvedAddress, C> host : oldHostsTyped) {
                        if (host.address.equals(addr) && host.tryToMarkActive()) {
                            return oldHostsTyped;
                        }
                    }
                }

                final List<Host<ResolvedAddress, C>> newHosts = new ArrayList<>(oldHostsTyped.size() + 1);
                newHosts.addAll(oldHostsTyped);
                newHosts.add(createHost(addr));
                return newHosts;
            }

            private List<Host<ResolvedAddress, C>> listWithHostRemoved(
                    List<Host<ResolvedAddress, C>> oldHostsTyped, Predicate<Host<ResolvedAddress, C>> hostPredicate) {
                if (oldHostsTyped.isEmpty()) {
                    // this can happen when an expired host is removed during closing of the RoundRobinLoadBalancer,
                    // but all of its connections have already been closed
                    return oldHostsTyped;
                }
                final List<Host<ResolvedAddress, C>> newHosts = new ArrayList<>(oldHostsTyped.size() - 1);
                for (int i = 0; i < oldHostsTyped.size(); ++i) {
                    final Host<ResolvedAddress, C> current = oldHostsTyped.get(i);
                    if (hostPredicate.test(current)) {
                        for (int x = i + 1; x < oldHostsTyped.size(); ++x) {
                            newHosts.add(oldHostsTyped.get(x));
                        }
                        return newHosts.isEmpty() ? emptyList() : newHosts;
                    } else {
                        newHosts.add(current);
                    }
                }
                return newHosts;
            }

            @Override
            public void onError(final Throwable t) {
                List<Host<ResolvedAddress, C>> hosts = usedHosts;
                eventStreamProcessor.onError(t);
                LOGGER.error(
                        "Load balancer {}. Service discoverer {} emitted an error. Last seen addresses (size {}) {}",
                        RoundRobinLoadBalancer.this, eventPublisher, hosts.size(), hosts, t);
            }

            @Override
            public void onComplete() {
                List<Host<ResolvedAddress, C>> hosts = usedHosts;
                eventStreamProcessor.onComplete();
                LOGGER.error("Load balancer {}. Service discoverer {} completed. Last seen addresses (size {}) {}",
                        RoundRobinLoadBalancer.this, eventPublisher, hosts.size(), hosts);
            }
        });
        asyncCloseable = toAsyncCloseable(graceful -> {
            @SuppressWarnings("unchecked")
            List<Host<ResolvedAddress, C>> currentList = usedHostsUpdater.getAndSet(this, CLOSED_LIST);
            discoveryCancellable.cancel();
            eventStreamProcessor.onComplete();
            CompositeCloseable cc = newCompositeCloseable().appendAll(currentList).appendAll(connectionFactory);
            return graceful ? cc.closeAsyncGracefully() : cc.closeAsync();
        });
    }

    /**
     * Please use {@link io.servicetalk.loadbalancer.RoundRobinLoadBalancerFactory} instead of this factory.
     *
     * @param <ResolvedAddress> The resolved address type.
     * @param <C> The type of connection.
     * @return a {@link LoadBalancerFactory} that creates instances of this class.
     * @deprecated Use {@link io.servicetalk.loadbalancer.RoundRobinLoadBalancerFactory} to build instances
     * of this {@link LoadBalancer}.
     */
    @Deprecated
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
        final List<Host<ResolvedAddress, C>> usedHosts = this.usedHosts;
        if (usedHosts.isEmpty()) {
            return usedHosts == CLOSED_LIST ? failedLBClosed() :
                // This is the case when SD has emitted some items but none of the hosts are available.
                failed(StacklessNoAvailableHostException.newInstance("No hosts are available to connect.",
                    RoundRobinLoadBalancer.class, "selectConnection0(...)"));
        }

        // try one loop over hosts and if all are expired, give up
        final int cursor = (indexUpdater.getAndIncrement(this) & Integer.MAX_VALUE) % usedHosts.size();
        final ThreadLocalRandom rnd = ThreadLocalRandom.current();
        Host<ResolvedAddress, C> pickedHost = null;
        for (int i = 0; i < usedHosts.size(); ++i) {
            // for a particular iteration we maintain a local cursor without contention with other requests
            int localCursor = (cursor + i) % usedHosts.size();
            final Host<ResolvedAddress, C> host = usedHosts.get(localCursor);
            assert host != null : "Host can't be null.";

            // Try first to see if an existing connection can be used
            final Object[] connections = host.connState.connections;
            // With small enough search space, attempt all connections.
            // Back off after exploring most of the search space, it gives diminishing returns.
            final int attempts = connections.length < MIN_SEARCH_SPACE ?
                    connections.length : (int) (connections.length * SEARCH_FACTOR);
            for (int j = 0; j < attempts; ++j) {
                @SuppressWarnings("unchecked")
                final C connection = (C) connections[rnd.nextInt(connections.length)];
                if (selector.test(connection)) {
                    return succeeded(connection);
                }
            }

            // don't open new connections for expired hosts, try a different one
            if (host.isActive()) {
                pickedHost = host;
                break;
            }
        }
        if (pickedHost == null) {
            return failed(StacklessNoAvailableHostException.newInstance(
                    "Failed to pick an active host. Either all are busy or all are expired.",
                    RoundRobinLoadBalancer.class, "selectConnection0(...)"));
        }
        // No connection was selected: create a new one.
        final Host<ResolvedAddress, C> host = pickedHost;
        // This LB implementation does not automatically provide TransportObserver. Therefore, we pass "null" here.
        // Users can apply a ConnectionFactoryFilter if they need to override this "null" value with TransportObserver.
        return connectionFactory.newConnection(host.address, null)
                .flatMap(newCnx -> {
                    // Invoke the selector before adding the connection to the pool, otherwise, connection can be
                    // used concurrently and hence a new connection can be rejected by the selector.
                    if (!selector.test(newCnx)) {
                        // Failure in selection could be temporary, hence add it to the queue and be consistent
                        // with the fact that select failure does not close a connection.
                        return newCnx.closeAsync().concat(failed(new ConnectionRejectedException(
                                "Newly created connection " + newCnx + " rejected by the selection filter.")));
                    }
                    if (host.addConnection(newCnx)) {
                        return succeeded(newCnx);
                    }
                    return newCnx.closeAsync().concat(this.usedHosts == CLOSED_LIST ? failedLBClosed() :
                            failed(new ConnectionRejectedException(
                                    "Failed to add newly created connection for host: " + host)));
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
     * Please use {@link io.servicetalk.loadbalancer.RoundRobinLoadBalancerFactory} instead of this factory.
     *
     * @param <ResolvedAddress> The resolved address type.
     * @param <C> The type of connection.
     * @deprecated Use {@link io.servicetalk.loadbalancer.RoundRobinLoadBalancerFactory} to build instances
     * of this {@link LoadBalancer}
     */
    @Deprecated
    public static final class RoundRobinLoadBalancerFactory<ResolvedAddress, C extends LoadBalancedConnection>
            implements LoadBalancerFactory<ResolvedAddress, C> {

        @Override
        public <T extends C> LoadBalancer<T> newLoadBalancer(
                final Publisher<? extends ServiceDiscovererEvent<ResolvedAddress>> eventPublisher,
                final ConnectionFactory<ResolvedAddress, T> connectionFactory) {
            return new RoundRobinLoadBalancer<>(eventPublisher, connectionFactory, EAGER_CONNECTION_SHUTDOWN_ENABLED);
        }
    }

    // Visible for testing
    List<Entry<ResolvedAddress, List<C>>> usedAddresses() {
        return usedHosts.stream().map(Host::asEntry).collect(toList());
    }

    private static final class Host<Addr, C extends ListenableAsyncCloseable> implements ListenableAsyncCloseable {
        private static final ConnState EMPTY_CONN_STATE = new ConnState(EMPTY_ARRAY, State.ACTIVE);
        private static final ConnState CLOSED_CONN_STATE = new ConnState(EMPTY_ARRAY, State.CLOSED);

        private enum State {
            ACTIVE,
            EXPIRED,
            CLOSED
        }

        private static final class ConnState {
            final Object[] connections;
            final State state;

            ConnState(final Object[] connections, final State state) {
                this.connections = connections;
                this.state = state;
            }
        }

        final Addr address;
        private final ListenableAsyncCloseable closeable;

        @SuppressWarnings("rawtypes")
        private static final AtomicReferenceFieldUpdater<Host, ConnState> connStateUpdater =
                AtomicReferenceFieldUpdater.newUpdater(Host.class, ConnState.class, "connState");
        private volatile ConnState connState = EMPTY_CONN_STATE;

        Host(Addr address) {
            this.address = requireNonNull(address);
            this.closeable = toAsyncCloseable(graceful ->
                    graceful ? doClose(AsyncCloseable::closeAsyncGracefully) : doClose(AsyncCloseable::closeAsync));
        }

        boolean tryToMarkActive() {
            return connStateUpdater.getAndUpdate(this, oldConnState -> {
                if (oldConnState.state == State.EXPIRED) {
                    return new ConnState(oldConnState.connections, State.ACTIVE);
                }
                return oldConnState;
            }).state == State.EXPIRED;
        }

        void markClosed() {
            final Object[] toRemove = connStateUpdater.getAndSet(this, CLOSED_CONN_STATE).connections;
            LOGGER.debug("Closing {} connection(s) gracefully to closed address: {}", toRemove.length, address);
            for (Object conn : toRemove) {
                @SuppressWarnings("unchecked")
                final C cConn = (C) conn;
                cConn.closeAsyncGracefully().subscribe();
            }
        }

        @SuppressWarnings("PMD.CollapsibleIfStatements")
        void markExpired() {
            final ConnState newState = connStateUpdater.updateAndGet(this,
                    oldConnState -> oldConnState.connections.length == 0 ?
                            CLOSED_CONN_STATE : new ConnState(oldConnState.connections, State.EXPIRED));
            if (newState == CLOSED_CONN_STATE) {
                // If in the meantime a connection was added, we shall close it gracefully
                this.closeAsyncGracefully().subscribe();
            }
        }

        boolean isActive() {
            return connState.state == State.ACTIVE;
        }

        private boolean isClosed() {
            return connState == CLOSED_CONN_STATE;
        }

        private boolean isExpired() {
            return connState.state == State.EXPIRED;
        }

        boolean addConnection(C connection) {
            for (;;) {
                final ConnState currentConnState = this.connState;
                if (currentConnState == CLOSED_CONN_STATE) {
                    return false;
                }
                final Object[] existing = currentConnState.connections;
                Object[] newList = Arrays.copyOf(existing, existing.length + 1);
                newList[existing.length] = connection;
                if (connStateUpdater.compareAndSet(this,
                        currentConnState, new ConnState(newList, currentConnState.state))) {
                    break;
                }
            }

            // Instrument the new connection so we prune it on close
            connection.onClose().beforeFinally(() -> {
                for (;;) {
                    final ConnState currentConnState = this.connState;
                    if (currentConnState == CLOSED_CONN_STATE) {
                        return;
                    }
                    int i = 0;
                    final Object[] connections = currentConnState.connections;
                    for (; i < connections.length; ++i) {
                        if (connections[i].equals(connection)) {
                            break;
                        }
                    }
                    if (i == connections.length) {
                        break;
                    } else if (connections.length == 1 && currentConnState.state == State.EXPIRED) {
                        // We're closing the last connection, close the Host.
                        // Closing the host will trigger the Host's onClose method, which will remove the host from
                        // used hosts list. If a race condition appears and a new connection was added in the
                        // meantime, that would mean the host is available again and the CAS operation will allow for
                        // determining that. It will prevent closing the Host and will only remove the connection
                        // (previously considered as the last one), from the array in the next iteration.
                        if (connStateUpdater.compareAndSet(this, currentConnState, CLOSED_CONN_STATE)) {
                            closeAsync().subscribe();
                            break;
                        }
                    } else {
                        Object[] newList = new Object[connections.length - 1];
                        System.arraycopy(connections, 0, newList, 0, i);
                        System.arraycopy(connections, i + 1, newList, i, newList.length - i);
                        if (connStateUpdater.compareAndSet(this,
                                currentConnState, new ConnState(newList, currentConnState.state))) {
                            break;
                        }
                    }
                }
            }).subscribe();
            return true;
        }
        // Used for testing only

        @SuppressWarnings("unchecked")
        Entry<Addr, List<C>> asEntry() {
            return new SimpleImmutableEntry<>(address,
                    Stream.of(connState.connections).map(conn -> (C) conn).collect(toList()));
        }

        @Override
        public Completable closeAsync() {
            return closeable.closeAsync();
        }

        @Override
        public Completable closeAsyncGracefully() {
            return closeable.closeAsyncGracefully();
        }

        @Override
        public Completable onClose() {
            return closeable.onClose();
        }

        @SuppressWarnings("unchecked")
        private Completable doClose(final Function<? super C, Completable> closeFunction) {
            return Completable.defer(() -> {
                final Object[] connections = connStateUpdater.getAndSet(this, CLOSED_CONN_STATE).connections;
                return connections.length == 0 ? completed() :
                        from(connections).flatMapCompletableDelayError(conn -> closeFunction.apply((C) conn));
            });
        }

        @Override
        public String toString() {
            return "Host{" +
                    "address=" + address +
                    ", active=" + isActive() +
                    ", closed=" + isClosed() +
                    ", expired=" + isExpired() +
                    '}';
        }
    }

    private static final class StacklessNoAvailableHostException extends NoAvailableHostException {
        private static final long serialVersionUID = 5942960040738091793L;

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
