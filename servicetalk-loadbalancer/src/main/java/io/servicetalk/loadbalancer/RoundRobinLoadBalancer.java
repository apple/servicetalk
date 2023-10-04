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
import io.servicetalk.client.api.ConnectionRejectedException;
import io.servicetalk.client.api.LoadBalancedConnection;
import io.servicetalk.client.api.LoadBalancer;
import io.servicetalk.client.api.NoActiveHostException;
import io.servicetalk.client.api.NoAvailableHostException;
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
import io.servicetalk.concurrent.internal.ThrowableUtils;
import io.servicetalk.context.api.ContextMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map.Entry;
import java.util.Spliterator;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
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
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.concurrent.api.SourceAdapters.fromSource;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static java.lang.Integer.toHexString;
import static java.lang.Math.min;
import static java.lang.System.identityHashCode;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.stream.Collectors.toList;

/**
 * Consult {@link RoundRobinLoadBalancerFactory} for a description of this {@link LoadBalancer} type.
 *
 * @param <ResolvedAddress> The resolved address type.
 * @param <C> The type of connection.
 */
final class RoundRobinLoadBalancer<ResolvedAddress, C extends LoadBalancedConnection>
        implements LoadBalancer<C> {

    private static final Logger LOGGER = LoggerFactory.getLogger(RoundRobinLoadBalancer.class);

    @SuppressWarnings("rawtypes")
    private static final AtomicReferenceFieldUpdater<RoundRobinLoadBalancer, List> usedHostsUpdater =
            AtomicReferenceFieldUpdater.newUpdater(RoundRobinLoadBalancer.class, List.class, "usedHosts");
    @SuppressWarnings("rawtypes")
    private static final AtomicIntegerFieldUpdater<RoundRobinLoadBalancer> indexUpdater =
            AtomicIntegerFieldUpdater.newUpdater(RoundRobinLoadBalancer.class, "index");
    @SuppressWarnings("rawtypes")
    private static final AtomicLongFieldUpdater<RoundRobinLoadBalancer> nextResubscribeTimeUpdater =
            AtomicLongFieldUpdater.newUpdater(RoundRobinLoadBalancer.class, "nextResubscribeTime");

    private static final long RESUBSCRIBING = -1L;

    /**
     * With a relatively small number of connections we can minimize connection creation under moderate concurrency by
     * exhausting the full search space without sacrificing too much latency caused by the cost of a CAS operation per
     * selection attempt.
     */
    private static final int MIN_RANDOM_SEARCH_SPACE = 64;

    /**
     * For larger search spaces, due to the cost of a CAS operation per selection attempt we see diminishing returns for
     * trying to locate an available connection when most connections are in use. This increases tail latencies, thus
     * after some number of failed attempts it appears to be more beneficial to open a new connection instead.
     * <p>
     * The current heuristics were chosen based on a set of benchmarks under various circumstances, low connection
     * counts, larger connection counts, low connection churn, high connection churn.
     */
    private static final float RANDOM_SEARCH_FACTOR = 0.75f;

    private volatile long nextResubscribeTime = RESUBSCRIBING;
    @SuppressWarnings("unused")
    private volatile int index;
    private volatile List<Host<ResolvedAddress, C>> usedHosts = emptyList();

    private final String id;
    private final String targetResource;
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
     * @param id a (unique) ID to identify the created {@link RoundRobinLoadBalancer}.
     * @param targetResourceName {@link String} representation of the target resource for which this instance
     * is performing load balancing.
     * @param eventPublisher provides a stream of addresses to connect to.
     * @param connectionFactory a function which creates new connections.
     * @param healthCheckConfig configuration for the health checking mechanism, which monitors hosts that
     * are unable to have a connection established. Providing {@code null} disables this mechanism (meaning the host
     * continues being eligible for connecting on the request path).
     * @see RoundRobinLoadBalancerFactory
     */
    RoundRobinLoadBalancer(
            final String id,
            final String targetResourceName,
            final Publisher<? extends Collection<? extends ServiceDiscovererEvent<ResolvedAddress>>> eventPublisher,
            final ConnectionFactory<ResolvedAddress, ? extends C> connectionFactory,
            final int linearSearchSpace,
            @Nullable final HealthCheckConfig healthCheckConfig) {
        this.id = id + '@' + toHexString(identityHashCode(this));
        this.targetResource = requireNonNull(targetResourceName);
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
            final HealthCheckConfig config, final RoundRobinLoadBalancer<R, C> lb) {
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
            if (host.address.equals(event.address())) {
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
                LOGGER.debug("{}: unexpectedly received null instead of events.", RoundRobinLoadBalancer.this);
                return;
            }
            for (ServiceDiscovererEvent<ResolvedAddress> event : events) {
                final ServiceDiscovererEvent.Status eventStatus = event.status();
                LOGGER.debug("{}: received new ServiceDiscoverer event {}. Inferred status: {}.",
                        RoundRobinLoadBalancer.this, event, eventStatus);

                @SuppressWarnings("unchecked")
                final List<Host<ResolvedAddress, C>> usedAddresses =
                        usedHostsUpdater.updateAndGet(RoundRobinLoadBalancer.this, oldHosts -> {
                            if (isClosedList(oldHosts)) {
                                return oldHosts;
                            }
                            final ResolvedAddress addr = requireNonNull(event.address());
                            @SuppressWarnings("unchecked")
                            final List<Host<ResolvedAddress, C>> oldHostsTyped =
                                    (List<Host<ResolvedAddress, C>>) oldHosts;

                            if (AVAILABLE.equals(eventStatus)) {
                                return addHostToList(oldHostsTyped, addr);
                            } else if (EXPIRED.equals(eventStatus)) {
                                if (oldHostsTyped.isEmpty()) {
                                    return emptyList();
                                } else {
                                    return markHostAsExpired(oldHostsTyped, addr);
                                }
                            } else if (UNAVAILABLE.equals(eventStatus)) {
                                return listWithHostRemoved(oldHostsTyped, host -> {
                                    boolean match = host.address.equals(addr);
                                    if (match) {
                                        host.markClosed();
                                    }
                                    return match;
                                });
                            } else {
                                LOGGER.error("{}: Unexpected Status in event:" +
                                        " {} (mapped to {}). Leaving usedHosts unchanged: {}",
                                        RoundRobinLoadBalancer.this, event, eventStatus, oldHosts);
                                return oldHosts;
                            }
                        });

                LOGGER.debug("{}: now using addresses (size={}): {}.",
                        RoundRobinLoadBalancer.this, usedAddresses.size(), usedAddresses);

                if (AVAILABLE.equals(eventStatus)) {
                    if (usedAddresses.size() == 1) {
                        eventStreamProcessor.onNext(LOAD_BALANCER_READY_EVENT);
                    }
                } else if (usedAddresses.isEmpty()) {
                    eventStreamProcessor.onNext(LOAD_BALANCER_NOT_READY_EVENT);
                }
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
                final List<Host<ResolvedAddress, C>> currentHosts = usedHosts;
                for (Host<ResolvedAddress, C> host : currentHosts) {
                    if (notAvailable(host, events)) {
                        host.closeAsyncGracefully().subscribe();
                    }
                }
            }
        }

        private List<Host<ResolvedAddress, C>> markHostAsExpired(
                final List<Host<ResolvedAddress, C>> oldHostsTyped, final ResolvedAddress addr) {
            for (Host<ResolvedAddress, C> host : oldHostsTyped) {
                if (host.address.equals(addr)) {
                    // Host removal will be handled by the Host's onClose::afterFinally callback
                    host.markExpired();
                    break;  // because duplicates are not allowed, we can stop iteration
                }
            }
            return oldHostsTyped;
        }

        private Host<ResolvedAddress, C> createHost(ResolvedAddress addr) {
            // All hosts will share the healthcheck config of the parent RR loadbalancer.
            Host<ResolvedAddress, C> host = new Host<>(RoundRobinLoadBalancer.this.toString(), addr, healthCheckConfig);
            host.onClose().afterFinally(() ->
                    usedHostsUpdater.updateAndGet(RoundRobinLoadBalancer.this, previousHosts -> {
                                @SuppressWarnings("unchecked")
                                List<Host<ResolvedAddress, C>> previousHostsTyped =
                                        (List<Host<ResolvedAddress, C>>) previousHosts;
                                return listWithHostRemoved(previousHostsTyped, current -> current == host);
                            }
                    )).subscribe();
            return host;
        }

        private List<Host<ResolvedAddress, C>> addHostToList(
                List<Host<ResolvedAddress, C>> oldHostsTyped, ResolvedAddress addr) {
            if (oldHostsTyped.isEmpty()) {
                return singletonList(createHost(addr));
            }

            // duplicates are not allowed
            for (Host<ResolvedAddress, C> host : oldHostsTyped) {
                if (host.address.equals(addr)) {
                    if (!host.markActiveIfNotClosed()) {
                        // If the host is already in CLOSED state, we should create a new entry.
                        // For duplicate ACTIVE events or for repeated activation due to failed CAS
                        // of replacing the usedHosts array the marking succeeds so we will not add a new entry.
                        break;
                    }
                    return oldHostsTyped;
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
            if (healthCheckConfig == null) {
                // Terminate processor only if we will never re-subscribe
                eventStreamProcessor.onError(t);
            }
            LOGGER.error(
                "{}: service discoverer {} emitted an error. Last seen addresses (size={}): {}.",
                    RoundRobinLoadBalancer.this, eventPublisher, hosts.size(), hosts, t);
        }

        @Override
        public void onComplete() {
            List<Host<ResolvedAddress, C>> hosts = usedHosts;
            if (healthCheckConfig == null) {
                // Terminate processor only if we will never re-subscribe
                eventStreamProcessor.onComplete();
            }
            LOGGER.error("{}: service discoverer completed. Last seen addresses (size={}): {}.",
                    RoundRobinLoadBalancer.this, hosts.size(), hosts);
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

    @Override
    public Publisher<Object> eventStream() {
        return eventStream;
    }

    @Override
    public String toString() {
        return "RoundRobinLoadBalancer{" +
                "id=" + id +
                ", targetResource=" + targetResource +
                '}';
    }

    private Single<C> selectConnection0(final Predicate<C> selector, @Nullable final ContextMap context,
                                        final boolean forceNewConnectionAndReserve) {
        final List<Host<ResolvedAddress, C>> usedHosts = this.usedHosts;
        if (usedHosts.isEmpty()) {
            return isClosedList(usedHosts) ? failedLBClosed(targetResource) :
                // This is the case when SD has emitted some items but none of the hosts are available.
                failed(StacklessNoAvailableHostException.newInstance(
                        "No hosts are available to connect for " + targetResource + ".",
                        RoundRobinLoadBalancer.class, "selectConnection0(...)"));
        }

        // try one loop over hosts and if all are expired, give up
        final int cursor = (indexUpdater.getAndIncrement(this) & Integer.MAX_VALUE) % usedHosts.size();
        final ThreadLocalRandom rnd = ThreadLocalRandom.current();
        Host<ResolvedAddress, C> pickedHost = null;
        for (int i = 0; i < usedHosts.size(); ++i) {
            // for a particular iteration we maintain a local cursor without contention with other requests
            final int localCursor = (cursor + i) % usedHosts.size();
            final Host<ResolvedAddress, C> host = usedHosts.get(localCursor);
            assert host != null : "Host can't be null.";

            if (!forceNewConnectionAndReserve) {
                // Try first to see if an existing connection can be used
                final Object[] connections = host.connections();
                // Exhaust the linear search space first:
                final int linearAttempts = min(connections.length, linearSearchSpace);
                for (int j = 0; j < linearAttempts; ++j) {
                    @SuppressWarnings("unchecked")
                    final C connection = (C) connections[j];
                    if (selector.test(connection)) {
                        return succeeded(connection);
                    }
                }
                // Try other connections randomly:
                if (connections.length > linearAttempts) {
                    final int diff = connections.length - linearAttempts;
                    // With small enough search space, attempt number of times equal to number of remaining connections.
                    // Back off after exploring most of the search space, it gives diminishing returns.
                    final int randomAttempts = diff < MIN_RANDOM_SEARCH_SPACE ? diff :
                            (int) (diff * RANDOM_SEARCH_FACTOR);
                    for (int j = 0; j < randomAttempts; ++j) {
                        @SuppressWarnings("unchecked")
                        final C connection = (C) connections[rnd.nextInt(linearAttempts, connections.length)];
                        if (selector.test(connection)) {
                            return succeeded(connection);
                        }
                    }
                }
            }

            // Don't open new connections for expired or unhealthy hosts, try a different one.
            // Unhealthy hosts have no open connections – that's why we don't fail earlier, the loop will not progress.
            if (host.isActiveAndHealthy()) {
                pickedHost = host;
                break;
            }
        }
        if (pickedHost == null) {
            if (healthCheckConfig != null && allUnhealthy(usedHosts)) {
                final long currNextResubscribeTime = nextResubscribeTime;
                if (currNextResubscribeTime >= 0 &&
                        healthCheckConfig.executor.currentTime(NANOSECONDS) >= currNextResubscribeTime &&
                        nextResubscribeTimeUpdater.compareAndSet(this, currNextResubscribeTime, RESUBSCRIBING)) {
                    subscribeToEvents(true);
                }
            }
            return failed(StacklessNoActiveHostException.newInstance("Failed to pick an active host for " +
                            targetResource + ". Either all are busy, expired, or unhealthy: " + usedHosts,
                    RoundRobinLoadBalancer.class, "selectConnection0(...)"));
        }
        // No connection was selected: create a new one.
        final Host<ResolvedAddress, C> host = pickedHost;

        // This LB implementation does not automatically provide TransportObserver. Therefore, we pass "null" here.
        // Users can apply a ConnectionFactoryFilter if they need to override this "null" value with TransportObserver.
        Single<? extends C> establishConnection = connectionFactory.newConnection(host.address, context, null);
        if (healthCheckConfig != null) {
                // Schedule health check before returning
                establishConnection = establishConnection.beforeOnError(t -> host.markUnhealthy(t, connectionFactory));
        }
        return establishConnection
                .flatMap(newCnx -> {
                    if (forceNewConnectionAndReserve && !newCnx.tryReserve()) {
                        return newCnx.closeAsync().<C>concat(failed(StacklessConnectionRejectedException.newInstance(
                                "Newly created connection " + newCnx + " for " + targetResource
                                        + " could not be reserved.",
                                RoundRobinLoadBalancer.class, "selectConnection0(...)")))
                                .shareContextOnSubscribe();
                    }

                    // Invoke the selector before adding the connection to the pool, otherwise, connection can be
                    // used concurrently and hence a new connection can be rejected by the selector.
                    if (!selector.test(newCnx)) {
                        // Failure in selection could be the result of connection factory returning cached connection,
                        // and not having visibility into max-concurrent-requests, or other threads already selected the
                        // connection which uses all the max concurrent request count.

                        // If there is caching Propagate the exception and rely upon retry strategy.
                        Single<C> failedSingle = failed(StacklessConnectionRejectedException.newInstance(
                                "Newly created connection " + newCnx + " for " + targetResource
                                        + " was rejected by the selection filter.",
                                RoundRobinLoadBalancer.class, "selectConnection0(...)"));

                        // Just in case the connection is not closed add it to the host so we don't lose track,
                        // duplicates will be filtered out.
                        return (host.addConnection(newCnx, null) ?
                                failedSingle : newCnx.closeAsync().concat(failedSingle)).shareContextOnSubscribe();
                    }
                    if (host.addConnection(newCnx, null)) {
                        return succeeded(newCnx).shareContextOnSubscribe();
                    }
                    return newCnx.closeAsync().<C>concat(isClosedList(this.usedHosts) ? failedLBClosed(targetResource) :
                            failed(StacklessConnectionRejectedException.newInstance(
                                    "Failed to add newly created connection " + newCnx + " for " + targetResource
                                            + " for " + host, RoundRobinLoadBalancer.class, "selectConnection0(...)")))
                            .shareContextOnSubscribe();
                });
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

    // Visible for testing
    List<Entry<ResolvedAddress, List<C>>> usedAddresses() {
        return usedHosts.stream().map(Host::asEntry).collect(toList());
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

    private static final class StacklessNoActiveHostException extends NoActiveHostException {

        private static final long serialVersionUID = 7500474499335155869L;

        private StacklessNoActiveHostException(final String message) {
            super(message);
        }

        @Override
        public Throwable fillInStackTrace() {
            return this;
        }

        public static StacklessNoActiveHostException newInstance(String message, Class<?> clazz, String method) {
            return ThrowableUtils.unknownStackTrace(new StacklessNoActiveHostException(message), clazz, method);
        }
    }

    private static final class StacklessConnectionRejectedException extends ConnectionRejectedException {
        private static final long serialVersionUID = -4940708893680455819L;

        private StacklessConnectionRejectedException(final String message) {
            super(message);
        }

        @Override
        public Throwable fillInStackTrace() {
            return this;
        }

        public static StacklessConnectionRejectedException newInstance(String message, Class<?> clazz, String method) {
            return ThrowableUtils.unknownStackTrace(new StacklessConnectionRejectedException(message), clazz, method);
        }
    }

    private static boolean isClosedList(List<?> list) {
        return list.getClass().equals(ClosedList.class);
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
