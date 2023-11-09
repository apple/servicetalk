/*
 * Copyright © 2018-2023 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.client.api.LoadBalancedConnection;
import io.servicetalk.client.api.LoadBalancer;
import io.servicetalk.client.api.ServiceDiscovererEvent;
import io.servicetalk.concurrent.PublisherSource.Processor;
import io.servicetalk.concurrent.PublisherSource.Subscriber;
import io.servicetalk.concurrent.PublisherSource.Subscription;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.CompositeCloseable;
import io.servicetalk.concurrent.api.ListenableAsyncCloseable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.internal.SequentialCancellable;
import io.servicetalk.context.api.ContextMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Spliterator;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import javax.annotation.Nullable;

import static io.servicetalk.client.api.LoadBalancerReadyEvent.LOAD_BALANCER_NOT_READY_EVENT;
import static io.servicetalk.client.api.LoadBalancerReadyEvent.LOAD_BALANCER_READY_EVENT;
import static io.servicetalk.client.api.ServiceDiscovererEvent.Status.AVAILABLE;
import static io.servicetalk.client.api.ServiceDiscovererEvent.Status.EXPIRED;
import static io.servicetalk.client.api.ServiceDiscovererEvent.Status.UNAVAILABLE;
import static io.servicetalk.concurrent.api.AsyncCloseables.newCompositeCloseable;
import static io.servicetalk.concurrent.api.AsyncCloseables.toAsyncCloseable;
import static io.servicetalk.concurrent.api.Processors.newPublisherProcessorDropHeadOnOverflow;
import static io.servicetalk.concurrent.api.Single.defer;
import static io.servicetalk.concurrent.api.Single.failed;
import static io.servicetalk.concurrent.api.SourceAdapters.fromSource;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static java.lang.Integer.toHexString;
import static java.lang.System.identityHashCode;
import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.stream.Collectors.toList;

/**
 * Consult {@link RoundRobinLoadBalancerFactory} for a description of this {@link LoadBalancer} type.
 *
 * @param <ResolvedAddress> The resolved address type.
 * @param <C> The type of connection.
 */
final class NewRoundRobinLoadBalancer<ResolvedAddress, C extends LoadBalancedConnection>
        implements TestableLoadBalancer<ResolvedAddress, C> {

    private static final Logger LOGGER = LoggerFactory.getLogger(NewRoundRobinLoadBalancer.class);

    @SuppressWarnings("rawtypes")
    private static final AtomicReferenceFieldUpdater<NewRoundRobinLoadBalancer, List> usedHostsUpdater =
            AtomicReferenceFieldUpdater.newUpdater(NewRoundRobinLoadBalancer.class, List.class, "usedHosts");

    @SuppressWarnings("rawtypes")
    private static final AtomicLongFieldUpdater<NewRoundRobinLoadBalancer> nextResubscribeTimeUpdater =
            AtomicLongFieldUpdater.newUpdater(NewRoundRobinLoadBalancer.class, "nextResubscribeTime");

    private static final long RESUBSCRIBING = -1L;

    private volatile long nextResubscribeTime = RESUBSCRIBING;
    private volatile List<Host<ResolvedAddress, C>> usedHosts = emptyList();

    private final String targetResource;
    private final String lbDescription;
    private final HostSelector<ResolvedAddress, C> hostSelector;
    private final Publisher<? extends Collection<? extends ServiceDiscovererEvent<ResolvedAddress>>> eventPublisher;
    private final Processor<Object, Object> eventStreamProcessor = newPublisherProcessorDropHeadOnOverflow(32);
    private final Publisher<Object> eventStream;
    private final SequentialCancellable discoveryCancellable = new SequentialCancellable();
    private final ConnectionFactory<ResolvedAddress, ? extends C> connectionFactory;
    private final int linearSearchSpace;
    @Nullable
    private final HealthCheckConfig healthCheckConfig;
    private final ListenableAsyncCloseable asyncCloseable;

    /**
     * Creates a new instance.
     *
     * @param id a (unique) ID to identify the created {@link NewRoundRobinLoadBalancer}.
     * @param targetResourceName {@link String} representation of the target resource for which this instance
     * is performing load balancing.
     * @param eventPublisher provides a stream of addresses to connect to.
     * @param connectionFactory a function which creates new connections.
     * @param healthCheckConfig configuration for the health checking mechanism, which monitors hosts that
     * are unable to have a connection established. Providing {@code null} disables this mechanism (meaning the host
     * continues being eligible for connecting on the request path).
     * @see RoundRobinLoadBalancerFactory
     */
    NewRoundRobinLoadBalancer(
            final String id,
            final String targetResourceName,
            final Publisher<? extends Collection<? extends ServiceDiscovererEvent<ResolvedAddress>>> eventPublisher,
            final ConnectionFactory<ResolvedAddress, ? extends C> connectionFactory,
            final int linearSearchSpace,
            @Nullable final HealthCheckConfig healthCheckConfig) {
        this.targetResource = requireNonNull(targetResourceName);
        this.lbDescription = makeDescription(id, targetResource);
        this.hostSelector = new RoundRobinSelector<>(targetResource);
        this.eventPublisher = requireNonNull(eventPublisher);
        this.eventStream = fromSource(eventStreamProcessor)
                .replay(1); // Allow for multiple subscribers and provide new subscribers with last signal.
        this.connectionFactory = requireNonNull(connectionFactory);
        this.linearSearchSpace = linearSearchSpace;
        this.healthCheckConfig = healthCheckConfig;
        this.asyncCloseable = toAsyncCloseable(graceful -> {
            discoveryCancellable.cancel();
            eventStreamProcessor.onComplete();
            final CompositeCloseable compositeCloseable;
            for (;;) {
                List<Host<ResolvedAddress, C>> currentList = usedHosts;
                if (isClosedList(currentList) ||
                        usedHostsUpdater.compareAndSet(this, currentList, new ClosedList<>(currentList))) {
                    compositeCloseable = newCompositeCloseable().appendAll(currentList).appendAll(connectionFactory);
                    LOGGER.debug("{} is closing {}gracefully. Last seen addresses (size={}): {}.",
                            this, graceful ? "" : "non", currentList.size(), currentList);
                    break;
                }
            }
            return (graceful ? compositeCloseable.closeAsyncGracefully() : compositeCloseable.closeAsync())
                    .beforeOnError(t -> {
                        if (!graceful) {
                            usedHosts = new ClosedList<>(emptyList());
                        }
                    })
                    .beforeOnComplete(() -> usedHosts = new ClosedList<>(emptyList()));
        });
        // Maintain a Subscriber so signals are always delivered to replay and new Subscribers get the latest signal.
        eventStream.ignoreElements().subscribe();
        subscribeToEvents(false);
    }

    private void subscribeToEvents(boolean resubscribe) {
        // This method is invoked only when we are in RESUBSCRIBING state. Only one thread can own this state.
        assert nextResubscribeTime == RESUBSCRIBING;
        if (resubscribe) {
            LOGGER.debug("{}: resubscribing to the ServiceDiscoverer event publisher.", this);
            discoveryCancellable.cancelCurrent();
        }
        toSource(eventPublisher).subscribe(new EventSubscriber(resubscribe));
        if (healthCheckConfig != null) {
            assert healthCheckConfig.executor instanceof NormalizedTimeSourceExecutor;
            nextResubscribeTime = nextResubscribeTime(healthCheckConfig, this);
        }
    }

    private static <R, C extends LoadBalancedConnection> long nextResubscribeTime(
            final HealthCheckConfig config, final NewRoundRobinLoadBalancer<R, C> lb) {
        final long lower = config.healthCheckResubscribeLowerBound;
        final long upper = config.healthCheckResubscribeUpperBound;
        final long currentTime = config.executor.currentTime(NANOSECONDS);
        final long result = currentTime + (lower == upper ? lower : ThreadLocalRandom.current().nextLong(lower, upper));
        LOGGER.debug("{}: current time {}, next resubscribe attempt can be performed at {}.",
                lb, currentTime, result);
        return result;
    }

    private static <ResolvedAddress, C extends LoadBalancedConnection> boolean allUnhealthy(
            final List<Host<ResolvedAddress, C>> usedHosts) {
        boolean allUnhealthy = !usedHosts.isEmpty();
        for (Host<ResolvedAddress, C> host : usedHosts) {
            if (!host.isUnhealthy()) {
                allUnhealthy = false;
                break;
            }
        }
        return allUnhealthy;
    }

    private static <ResolvedAddress> boolean onlyAvailable(
            final Collection<? extends ServiceDiscovererEvent<ResolvedAddress>> events) {
        boolean onlyAvailable = !events.isEmpty();
        for (ServiceDiscovererEvent<ResolvedAddress> event : events) {
            if (!AVAILABLE.equals(event.status())) {
                onlyAvailable = false;
                break;
            }
        }
        return onlyAvailable;
    }

    private static <ResolvedAddress, C extends LoadBalancedConnection> boolean notAvailable(
            final Host<ResolvedAddress, C> host,
            final Collection<? extends ServiceDiscovererEvent<ResolvedAddress>> events) {
        boolean available = false;
        for (ServiceDiscovererEvent<ResolvedAddress> event : events) {
            if (host.address().equals(event.address())) {
                available = true;
                break;
            }
        }
        return !available;
    }

    private final class EventSubscriber
            implements Subscriber<Collection<? extends ServiceDiscovererEvent<ResolvedAddress>>> {

        private boolean firstEventsAfterResubscribe;

        EventSubscriber(boolean resubscribe) {
            this.firstEventsAfterResubscribe = resubscribe;
        }

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
        public void onNext(@Nullable final Collection<? extends ServiceDiscovererEvent<ResolvedAddress>> events) {
            if (events == null) {
                LOGGER.debug("{}: unexpectedly received null instead of events.", NewRoundRobinLoadBalancer.this);
                return;
            }

            boolean sendReadyEvent;
            List<Host<ResolvedAddress, C>> nextHosts = new ArrayList<>();
            for (;;) {
                // TODO: we have some weirdness in the event that we fail the CAS namely that we can create a host
                //  that never gets used but is orphaned. It's fine so long as there is nothing to close but that
                //  guarantee may not always hold in the future.
                List<Host<ResolvedAddress, C>> usedHosts = usedHostsUpdater.get(NewRoundRobinLoadBalancer.this);
                if (isClosedList(usedHosts)) {
                    // We don't update if the load balancer is closed.
                    return;
                }
                nextHosts.clear();
                sendReadyEvent = false;

                // First we make a map of addresses to events so that we don't get quadratic behavior for diffing.
                // Unfortunately we need to make this every iteration of the CAS loop since we remove entries
                // for hosts that already exist. If this results in to many collisions and map rebuilds we should
                // re-assess how we manage concurrency for list mutations.
                final Map<ResolvedAddress, ServiceDiscovererEvent<ResolvedAddress>> eventMap = new HashMap<>();
                for (ServiceDiscovererEvent<ResolvedAddress> event : events) {
                    ServiceDiscovererEvent<ResolvedAddress> old = eventMap.put(event.address(), event);
                    if (old != null) {
                        LOGGER.error("Multiple ServiceDiscoveryEvent's detected for address {}. Event: {}.",
                                event.address(), event);
                    }
                }

                // First thing we do is go through the existing hosts and see if we need to transfer them. These
                // will be all existing hosts that either don't have a matching discovery event or are not marked
                // as unavailable. If they are marked unavailable, we need to close them (which is idempotent).
                for (Host<ResolvedAddress, C> host : usedHosts) {
                    ServiceDiscovererEvent<ResolvedAddress> event = eventMap.remove(host.address());
                    if (event == null) {
                        // Host doesn't have a SD update so just copy it over.
                        nextHosts.add(host);
                    } else if (AVAILABLE.equals(event.status())) {
                        sendReadyEvent = true;
                        // If the host is already in CLOSED state, we should discard it and create a new entry.
                        // For duplicate ACTIVE events or for repeated activation due to failed CAS
                        // of replacing the usedHosts array the marking succeeds so we will not add a new entry.
                        if (host.markActiveIfNotClosed()) {
                            nextHosts.add(host);
                        } else {
                            nextHosts.add(createHost(event.address()));
                        }
                    } else if (EXPIRED.equals(event.status())) {
                        if (!host.markExpired()) {
                            nextHosts.add(host);
                        }
                    } else if (UNAVAILABLE.equals(event.status())) {
                        host.markClosed();
                    } else {
                        LOGGER.error("{}: Unexpected Status in event:" +
                                        " {} (mapped to {}). Leaving usedHosts unchanged: {}",
                                NewRoundRobinLoadBalancer.this, event, event.status(), nextHosts);
                        nextHosts.add(host);
                    }
                }
                // Now process events that didn't have an existing host. The only ones that we actually care
                // about are the AVAILABLE events which result in a new host.
                for (ServiceDiscovererEvent<ResolvedAddress> event : eventMap.values()) {
                    if (AVAILABLE.equals(event.status())) {
                        sendReadyEvent = true;
                        nextHosts.add(createHost(event.address()));
                    }
                }
                // We've now built the new list so now we need to CAS it before we can move on. This should only be
                // racing with closing hosts and closing the whole LB so it shouldn't be common to lose the race.
                if (usedHostsUpdater.compareAndSet(NewRoundRobinLoadBalancer.this, usedHosts, nextHosts)) {
                    break;
                }
            }

            LOGGER.debug("{}: now using addresses (size={}): {}.",
                    NewRoundRobinLoadBalancer.this, nextHosts.size(), nextHosts);
            if (nextHosts.isEmpty()) {
                eventStreamProcessor.onNext(LOAD_BALANCER_NOT_READY_EVENT);
            } else if (sendReadyEvent) {
                eventStreamProcessor.onNext(LOAD_BALANCER_READY_EVENT);
            }

            if (firstEventsAfterResubscribe) {
                // We can enter this path only if we re-subscribed because all previous hosts were UNHEALTHY.
                if (events.isEmpty()) {
                    return; // Wait for the next collection of events.
                }
                firstEventsAfterResubscribe = false;

                if (!onlyAvailable(events)) {
                    // Looks like the current ServiceDiscoverer maintains a state between re-subscribes. It already
                    // assigned correct states to all hosts. Even if some of them were left UNHEALTHY, we should keep
                    // running health-checks.
                    return;
                }
                // Looks like the current ServiceDiscoverer doesn't maintain a state between re-subscribes and always
                // starts from an empty state propagating only AVAILABLE events. To be in sync with the
                // ServiceDiscoverer we should clean up and close gracefully all hosts that are not present in the
                // initial collection of events, regardless of their current state.
                for (Host<ResolvedAddress, C> host : nextHosts) {
                    if (notAvailable(host, events)) {
                        host.closeAsyncGracefully().subscribe();
                    }
                }
            }
        }

        private Host<ResolvedAddress, C> createHost(ResolvedAddress addr) {
            // All hosts will share the healthcheck config of the parent RR loadbalancer.
            Host<ResolvedAddress, C> host = new DefaultHost<>(NewRoundRobinLoadBalancer.this.toString(), addr,
                    connectionFactory, linearSearchSpace, healthCheckConfig);
            host.onClose().afterFinally(() ->
                    usedHostsUpdater.updateAndGet(NewRoundRobinLoadBalancer.this, previousHosts -> {
                                @SuppressWarnings("unchecked")
                                List<Host<ResolvedAddress, C>> previousHostsTyped =
                                        (List<Host<ResolvedAddress, C>>) previousHosts;
                                return listWithHostRemoved(previousHostsTyped, current -> current == host);
                            }
                    )).subscribe();
            return host;
        }

        private List<Host<ResolvedAddress, C>> listWithHostRemoved(
                List<Host<ResolvedAddress, C>> oldHostsTyped, Predicate<Host<ResolvedAddress, C>> hostPredicate) {
            if (oldHostsTyped.isEmpty()) {
                // this can happen when an expired host is removed during closing of the NewRoundRobinLoadBalancer,
                // but all of its connections have already been closed
                return oldHostsTyped;
            }
            // We keep the old size as the capacity hint because the penalty for a resize in the case that the
            // element isn't in the list is much worse than the penalty for an unused array slot.
            final List<Host<ResolvedAddress, C>> newHosts = new ArrayList<>(oldHostsTyped.size());
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
            if (healthCheckConfig == null) {
                // Terminate processor only if we will never re-subscribe
                eventStreamProcessor.onError(t);
            }
            LOGGER.error(
                "{}: service discoverer {} emitted an error. Last seen addresses (size={}): {}.",
                    NewRoundRobinLoadBalancer.this, eventPublisher, hosts.size(), hosts, t);
        }

        @Override
        public void onComplete() {
            List<Host<ResolvedAddress, C>> hosts = usedHosts;
            if (healthCheckConfig == null) {
                // Terminate processor only if we will never re-subscribe
                eventStreamProcessor.onComplete();
            }
            LOGGER.error("{}: service discoverer completed. Last seen addresses (size={}): {}.",
                    NewRoundRobinLoadBalancer.this, hosts.size(), hosts);
        }
    }

    private static <T> Single<T> failedLBClosed(String targetResource) {
        return failed(new IllegalStateException("LoadBalancer for " + targetResource + " has closed"));
    }

    @Override
    public Single<C> selectConnection(final Predicate<C> selector, @Nullable final ContextMap context) {
        return defer(() -> selectConnection0(selector, context, false).shareContextOnSubscribe());
    }

    @Override
    public Single<C> newConnection(@Nullable final ContextMap context) {
        return defer(() -> selectConnection0(c -> true, context, true).shareContextOnSubscribe());
    }

    private Single<C> selectConnection0(final Predicate<C> selector, @Nullable final ContextMap context,
                                        final boolean forceNewConnectionAndReserve) {
        final List<Host<ResolvedAddress, C>> currentHosts = this.usedHosts;
        // It's possible that we're racing with updates from the `onNext` method but since it's intrinsically
        // racy it's fine to do these 'are there any hosts at all' checks here using the total host set.
        if (currentHosts.isEmpty()) {
            return isClosedList(currentHosts) ? failedLBClosed(targetResource) :
                    // This is the case when SD has emitted some items but none of the hosts are available.
                    failed(Exceptions.StacklessNoAvailableHostException.newInstance(
                            "No hosts are available to connect for " + targetResource + ".",
                            this.getClass(), "selectConnection0(...)"));
        }

        Single<C> result = hostSelector.selectConnection(currentHosts, selector, context, forceNewConnectionAndReserve);
        if (healthCheckConfig != null) {
            result = result.beforeOnError(exn -> {
                if (exn instanceof Exceptions.StacklessNoActiveHostException && allUnhealthy(currentHosts)) {
                    final long currNextResubscribeTime = nextResubscribeTime;
                    if (currNextResubscribeTime >= 0 &&
                            healthCheckConfig.executor.currentTime(NANOSECONDS) >= currNextResubscribeTime &&
                            nextResubscribeTimeUpdater.compareAndSet(this, currNextResubscribeTime, RESUBSCRIBING)) {
                            subscribeToEvents(true);
                    }
                }
            });
        }
        return result;
    }

    @Override
    public Publisher<Object> eventStream() {
        return eventStream;
    }

    @Override
    public String toString() {
        return lbDescription;
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

    @Override
    public List<Entry<ResolvedAddress, List<C>>> usedAddresses() {
        return usedHosts.stream().map(host -> ((DefaultHost<ResolvedAddress, C>) host).asEntry()).collect(toList());
    }

    private static boolean isClosedList(List<?> list) {
        return list.getClass().equals(ClosedList.class);
    }

    private String makeDescription(String id, String targetResource) {
        return "NewRoundRobinLoadBalancer{" +
                "id=" + id + '@' + toHexString(identityHashCode(this)) +
                ", targetResource=" + targetResource +
                '}';
    }

    private static final class ClosedList<T> implements List<T> {
        private final List<T> delegate;

        private ClosedList(final List<T> delegate) {
            this.delegate = requireNonNull(delegate);
        }

        @Override
        public int size() {
            return delegate.size();
        }

        @Override
        public boolean isEmpty() {
            return delegate.isEmpty();
        }

        @Override
        public boolean contains(final Object o) {
            return delegate.contains(o);
        }

        @Override
        public Iterator<T> iterator() {
            return delegate.iterator();
        }

        @Override
        public void forEach(final Consumer<? super T> action) {
            delegate.forEach(action);
        }

        @Override
        public Object[] toArray() {
            return delegate.toArray();
        }

        @Override
        public <T1> T1[] toArray(final T1[] a) {
            return delegate.toArray(a);
        }

        @Override
        public boolean add(final T t) {
            return delegate.add(t);
        }

        @Override
        public boolean remove(final Object o) {
            return delegate.remove(o);
        }

        @Override
        public boolean containsAll(final Collection<?> c) {
            return delegate.containsAll(c);
        }

        @Override
        public boolean addAll(final Collection<? extends T> c) {
            return delegate.addAll(c);
        }

        @Override
        public boolean addAll(final int index, final Collection<? extends T> c) {
            return delegate.addAll(c);
        }

        @Override
        public boolean removeAll(final Collection<?> c) {
            return delegate.removeAll(c);
        }

        @Override
        public boolean removeIf(final Predicate<? super T> filter) {
            return delegate.removeIf(filter);
        }

        @Override
        public boolean retainAll(final Collection<?> c) {
            return delegate.retainAll(c);
        }

        @Override
        public void replaceAll(final UnaryOperator<T> operator) {
            delegate.replaceAll(operator);
        }

        @Override
        public void sort(final Comparator<? super T> c) {
            delegate.sort(c);
        }

        @Override
        public void clear() {
            delegate.clear();
        }

        @Override
        public T get(final int index) {
            return delegate.get(index);
        }

        @Override
        public T set(final int index, final T element) {
            return delegate.set(index, element);
        }

        @Override
        public void add(final int index, final T element) {
            delegate.add(index, element);
        }

        @Override
        public T remove(final int index) {
            return delegate.remove(index);
        }

        @Override
        public int indexOf(final Object o) {
            return delegate.indexOf(o);
        }

        @Override
        public int lastIndexOf(final Object o) {
            return delegate.lastIndexOf(o);
        }

        @Override
        public ListIterator<T> listIterator() {
            return delegate.listIterator();
        }

        @Override
        public ListIterator<T> listIterator(final int index) {
            return delegate.listIterator(index);
        }

        @Override
        public List<T> subList(final int fromIndex, final int toIndex) {
            return new ClosedList<>(delegate.subList(fromIndex, toIndex));
        }

        @Override
        public Spliterator<T> spliterator() {
            return delegate.spliterator();
        }

        @Override
        public Stream<T> stream() {
            return delegate.stream();
        }

        @Override
        public Stream<T> parallelStream() {
            return delegate.parallelStream();
        }
    }
}
