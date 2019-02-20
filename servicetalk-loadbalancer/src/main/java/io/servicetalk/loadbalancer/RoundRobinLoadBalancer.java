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
import io.servicetalk.concurrent.internal.DuplicateSubscribeException;
import io.servicetalk.concurrent.internal.SequentialCancellable;

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
import javax.annotation.Nullable;

import static io.servicetalk.client.api.LoadBalancerReadyEvent.LOAD_BALANCER_NOT_READY_EVENT;
import static io.servicetalk.client.api.LoadBalancerReadyEvent.LOAD_BALANCER_READY_EVENT;
import static io.servicetalk.concurrent.api.AsyncCloseables.newCompositeCloseable;
import static io.servicetalk.concurrent.api.AsyncCloseables.toAsyncCloseable;
import static io.servicetalk.concurrent.api.Completable.completed;
import static io.servicetalk.concurrent.api.Single.defer;
import static io.servicetalk.concurrent.api.Single.error;
import static io.servicetalk.concurrent.api.Single.success;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.EmptySubscription.EMPTY_SUBSCRIPTION;
import static io.servicetalk.concurrent.internal.SubscriberUtils.SUBSCRIBER_STATE_TERMINATED;
import static io.servicetalk.concurrent.internal.SubscriberUtils.checkTerminationValidWithConcurrentOnNextCheck;
import static io.servicetalk.concurrent.internal.SubscriberUtils.isRequestNValid;
import static io.servicetalk.concurrent.internal.SubscriberUtils.newExceptionForInvalidRequestN;
import static io.servicetalk.concurrent.internal.SubscriberUtils.sendOnNextWithConcurrentTerminationCheck;
import static io.servicetalk.concurrent.internal.TerminalNotification.complete;
import static io.servicetalk.concurrent.internal.ThrowableUtil.unknownStackTrace;
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
 * <li>Existing connections are reused unless a selector passed to {@link #selectConnection(Function)} suggests
 * otherwise.
 * This can lead to situations where connections will be used to their maximum capacity (for example in the context of
 * pipelining) before new connections are created.</li>
 * <li>Closed connections are automatically pruned.</li>
 * </ul>
 *
 * @param <ResolvedAddress> The resolved address type.
 * @param <C> The type of connection.
 */
public final class RoundRobinLoadBalancer<ResolvedAddress, C extends ListenableAsyncCloseable> implements LoadBalancer<C> {

    private static final Logger LOGGER = LoggerFactory.getLogger(RoundRobinLoadBalancer.class);
    private static final IllegalStateException LB_CLOSED_SELECT_CNX_EXCEPTION =
            unknownStackTrace(new IllegalStateException("LoadBalancer has closed"), RoundRobinLoadBalancer.class,
                    "selectConnection0(...)");
    private static final NoAvailableHostException NO_ACTIVE_HOSTS_SELECT_CNX_EXCEPTION =
            unknownStackTrace(new NoAvailableHostException("No hosts are available to connect."),
                    RoundRobinLoadBalancer.class, "selectConnection0(...)");

    private static final AtomicReferenceFieldUpdater<RoundRobinLoadBalancer, List> activeHostsUpdater =
            newUpdater(RoundRobinLoadBalancer.class, List.class, "activeHosts");
    private static final AtomicIntegerFieldUpdater<RoundRobinLoadBalancer> indexUpdater =
            newUpdater(RoundRobinLoadBalancer.class, "index");

    private volatile boolean closed;
    @SuppressWarnings("unused")
    private volatile int index;
    private volatile List<Host<ResolvedAddress, C>> activeHosts = emptyList();

    private final PublisherProcessorSingle<Object> eventStream = new PublisherProcessorSingle<>();
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
                discoveryCancellable.setNextCancellable(s);
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

                            if (event.available()) {
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

                if (event.available()) {
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
                LOGGER.error(
                        "Load balancer {}. Service discoverer {} emitted an error. Last seen addresses (size {}) {}",
                        RoundRobinLoadBalancer.this, eventPublisher, hosts.size(), hosts, t);
            }

            @Override
            public void onComplete() {
                List<Host<ResolvedAddress, C>> hosts = activeHosts;
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
    public static <ResolvedAddress, C extends ListenableAsyncCloseable>
                                    LoadBalancerFactory<ResolvedAddress, C> newRoundRobinFactory() {
        return (eventPublisher, connectionFactory) -> new RoundRobinLoadBalancer<>(eventPublisher,
                connectionFactory,
                comparingInt(Object::hashCode));
    }

    @Override
    public <CC extends C> Single<CC> selectConnection(Function<C, CC> selector) {
        return defer(() -> selectConnection0(selector).subscribeShareContext());
    }

    @Override
    public Publisher<Object> eventStream() {
        return eventStream;
    }

    private <CC extends C> Single<CC> selectConnection0(Function<? super C, CC> selector) {
        if (closed) {
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
                    // Invoke the selector before adding the connection to the pool, otherwise, connection can be used
                    // concurrently and hence a new connection can be rejected by the selector.
                    CC selection = selector.apply(newCnx);
                    if (selection == null) {
                        newCnx.closeAsync().subscribe();
                        // Failure in selection could be temporary, hence add it to the queue and be consistent with the
                        // fact that select failure does not close a connection.
                        return error(new ConnectionRejectedException("Newly created connection " + newCnx +
                                " rejected by the selection filter."));
                    }
                    if (host.addConnection(newCnx)) {
                        // If the LB has closed, we attempt to remove the connection, if the removal succeeds, close it.
                        // If we can't remove it, it means it's been removed concurrently and we assume that whoever
                        // removed it also closed it or that it has been removed as a consequence of closing.
                        if (closed) {
                            if (host.connections.remove(newCnx)) {
                                newCnx.closeAsync().subscribe();
                            }
                            return error(LB_CLOSED_SELECT_CNX_EXCEPTION);
                        }
                        return success(selection);
                    }
                    return error(new ConnectionRejectedException("Failed to add newly created connection for host: " +
                            host.address + ", host inactive? " + host.removed));
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
                if (!added || connections.remove(connection)) {
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
            return connections == null ? completed() :
                    completed().mergeDelayError(connections.stream()
                            .map(AsyncCloseable::closeAsync)::iterator);
        }

        @Override
        public Completable closeAsyncGracefully() {
            return connections == null ? completed() :
                    completed().mergeDelayError(connections.stream()
                            .map(AsyncCloseable::closeAsyncGracefully)::iterator);
        }

        @Override
        public String toString() {
            return "Host{" +
                    "address=" + address +
                    ", removed=" + removed +
                    '}';
        }
    }

    private static final class MutableAddressHost<Addr, C extends ListenableAsyncCloseable> extends Host<Addr, C> {
        @Nullable
        Addr mutableAddress;
    }

    /**
     * Eventually this will be replaced by a PublisherProcessor which is backed by a queue and allows for
     * arbitrary events being published.
     * @param <T> The type of data delivered to {@link Subscriber}s.
     */
    private static final class PublisherProcessorSingle<T> extends Publisher<T> {
        private static final AtomicIntegerFieldUpdater<PublisherProcessorSingle> subscriberStateUpdater =
                newUpdater(PublisherProcessorSingle.class, "subscriberState");
        private static final AtomicReferenceFieldUpdater<PublisherProcessorSingle, Object> terminalNotificationUpdater =
                newUpdater(PublisherProcessorSingle.class, Object.class, "terminalNotification");
        private static final AtomicReferenceFieldUpdater<PublisherProcessorSingle, Object> eventUpdater =
                newUpdater(PublisherProcessorSingle.class, Object.class, "event");
        private static final Object REQUESTED = new Object();
        private static final Object DELIVERED = new Object();
        private static final Object COMPLETED = new Object();
        @Nullable
        private volatile Subscriber<? super T> subscriber;
        @Nullable
        private volatile Object event;
        @SuppressWarnings("unused")
        private volatile int subscriberState;
        @SuppressWarnings("unused")
        @Nullable
        private volatile Object terminalNotification;

        @Override
        protected void handleSubscribe(final Subscriber<? super T> subscriber) {
            Subscriber<? super T> thisSubscriber = this.subscriber;
            if (thisSubscriber == null) {
                this.subscriber = subscriber;
                subscriber.onSubscribe(new Subscription() {
                    @Override
                    public void request(final long n) {
                        if (isRequestNValid(n)) {
                            for (;;) {
                                Object event = PublisherProcessorSingle.this.event;
                                if (event == REQUESTED || event == DELIVERED || event == COMPLETED) {
                                    break;
                                } else if (event != null) {
                                    if (eventUpdater.compareAndSet(PublisherProcessorSingle.this, event, DELIVERED)) {
                                        @SuppressWarnings("unchecked")
                                        final T finalT = (T) event;
                                        deliverOnNext(finalT, subscriber);
                                        break;
                                    }
                                } else if (eventUpdater.compareAndSet(PublisherProcessorSingle.this, null, REQUESTED)) {
                                    // Check if sendOnComplete was called but the Subscriber was not visible, and then
                                    // deliver the terminal notification if we change the event to COMPLETED.
                                    if (subscriberState == SUBSCRIBER_STATE_TERMINATED &&
                                        eventUpdater.compareAndSet(PublisherProcessorSingle.this, REQUESTED, COMPLETED)) {
                                        subscriber.onComplete();
                                    }
                                    break;
                                }
                            }
                        } else {
                            Throwable cause = newExceptionForInvalidRequestN(n);
                            if (checkTerminationValidWithConcurrentOnNextCheck(null, cause, subscriberStateUpdater,
                                    terminalNotificationUpdater, PublisherProcessorSingle.this)) {
                                for (;;) {
                                    Object event = PublisherProcessorSingle.this.event;
                                    if (event == null || event == REQUESTED || event == DELIVERED) {
                                        if (eventUpdater.compareAndSet(PublisherProcessorSingle.this, event, COMPLETED)) {
                                            subscriber.onError(cause);
                                            break;
                                        }
                                    } else {
                                        // If there is data pending we will deliver the error after the data.
                                        break;
                                    }
                                }
                            }
                        }
                    }

                    @Override
                    public void cancel() {
                        // This will ensure the event is GCed (if it exists) and will swallow any terminal events which
                        // also maybe pending.
                        event = COMPLETED;
                    }
                });
            } else {
                subscriber.onSubscribe(EMPTY_SUBSCRIPTION);
                subscriber.onError(new DuplicateSubscribeException(thisSubscriber, subscriber));
            }
        }

        void sendOnNext(T next) {
            for (;;) {
                Object event = this.event;
                if (event == null) {
                    if (eventUpdater.compareAndSet(this, null, next)) {
                        break;
                    }
                } else if (event == REQUESTED) {
                    if (eventUpdater.compareAndSet(this, REQUESTED, DELIVERED)) {
                        Subscriber<? super T> subscriber = this.subscriber;
                        assert subscriber != null;
                        deliverOnNext(next, subscriber);
                        break;
                    }
                } else {
                    break;
                }
            }
        }

        void sendOnComplete() {
            if (checkTerminationValidWithConcurrentOnNextCheck(null, complete(),
                    subscriberStateUpdater, terminalNotificationUpdater, this)) {
                Subscriber<? super T> subscriber = this.subscriber;
                // If the subscriber is not null, deliver the event now, otherwise we deliver in the Subscription.
                if (subscriber != null) {
                    // Make a best effort to terminate early, if there is data pending we will let the Subscription
                    // deliver the terminal event after it delivers the data.
                    for (;;) {
                        Object event = this.event;
                        if (event == null || event == REQUESTED || event == DELIVERED) {
                            if (eventUpdater.compareAndSet(this, event, COMPLETED)) {
                                subscriber.onComplete();
                                break;
                            }
                        } else {
                            // Note that the Subscription may have requested data in the mean time, but we let the
                            // Subscription handle that.
                            break;
                        }
                    }
                }
            }
        }

        private void deliverOnNext(T next, Subscriber<? super T> subscriber) {
            sendOnNextWithConcurrentTerminationCheck(subscriber, next, this::terminate,
                    subscriberStateUpdater, terminalNotificationUpdater, this);
        }

        private void terminate(Object terminalNotification) {
            Subscriber<? super T> subscriber = this.subscriber;
            assert subscriber != null;
            if (terminalNotification instanceof Throwable) {
                subscriber.onError((Throwable) terminalNotification);
            } else {
                subscriber.onComplete();
            }
        }
    }
}
