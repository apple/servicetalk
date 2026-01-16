/*
 * Copyright © 2018-2026 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.client.api.NoActiveHostException;
import io.servicetalk.client.api.NoAvailableHostException;
import io.servicetalk.client.api.ServiceDiscovererEvent;
import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.CompletableSource;
import io.servicetalk.concurrent.PublisherSource.Processor;
import io.servicetalk.concurrent.PublisherSource.Subscriber;
import io.servicetalk.concurrent.PublisherSource.Subscription;
import io.servicetalk.concurrent.SingleSource;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.CompositeCloseable;
import io.servicetalk.concurrent.api.ListenableAsyncCloseable;
import io.servicetalk.concurrent.api.Processors;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.SourceAdapters;
import io.servicetalk.concurrent.internal.SequentialCancellable;
import io.servicetalk.context.api.ContextMap;
import io.servicetalk.utils.internal.RandomUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
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
 * The (new) default load balancer implementation.
 *
 * @param <ResolvedAddress> The resolved address type.
 * @param <C> The type of connection.
 */
final class DefaultLoadBalancer<ResolvedAddress, C extends LoadBalancedConnection>
        implements TestableLoadBalancer<ResolvedAddress, C> {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultLoadBalancer.class);

    @SuppressWarnings("rawtypes")
    private static final AtomicLongFieldUpdater<DefaultLoadBalancer> nextResubscribeTimeUpdater =
            AtomicLongFieldUpdater.newUpdater(DefaultLoadBalancer.class, "nextResubscribeTime");

    private static final long RESUBSCRIBING = -1L;

    private volatile long nextResubscribeTime = RESUBSCRIBING;
    @Nullable
    private volatile EventSubscriber currentSubscriber;

    // writes are protected by `sequentialExecutor` but the field can be read by any thread.
    private volatile HostSelector<ResolvedAddress, C> hostSelector;
    // reads and writes are protected by `sequentialExecutor`
    private List<PrioritizedHostImpl<ResolvedAddress, C>> usedHosts = emptyList();
    // reads and writes are protected by `sequentialExecutor`.
    private boolean isClosed;

    private final SequentialExecutor sequentialExecutor;
    private final String lbDescription;
    private final Publisher<? extends Collection<? extends ServiceDiscovererEvent<ResolvedAddress>>> eventPublisher;
    private final Processor<Object, Object> eventStreamProcessor = newPublisherProcessorDropHeadOnOverflow(32);
    private final Publisher<Object> eventStream;
    private final SequentialCancellable discoveryCancellable = new SequentialCancellable();
    private final ConnectionSelector<C> connectionSelector;
    private final Subsetter subsetter;
    private final ConnectionFactory<ResolvedAddress, ? extends C> connectionFactory;
    private final int minConnectionsPerHost;
    @Nullable
    private final HealthCheckConfig healthCheckConfig;
    private final HostPriorityStrategy priorityStrategy;
    private final OutlierDetector<ResolvedAddress, C> outlierDetector;
    private final Cancellable outlierDetectorStatusChangeStream;
    private final LoadBalancerObserver loadBalancerObserver;
    private final ListenableAsyncCloseable asyncCloseable;

    /**
     * Creates a new instance.
     *
     * @param id a (unique) ID to identify the created {@link DefaultLoadBalancer}.
     * @param targetResource {@link String} representation of the target resource for which this instance
     * is performing load balancing.
     * @param eventPublisher provides a stream of addresses to connect to.
     * @param priorityStrategyFactory a builder of the {@link HostPriorityStrategy} to use with the load balancer.
     * @param loadBalancingPolicy a factory of the initial host selector to use with this load balancer.
     * @param subsetterFactory a factory that generates subsetters.
     * @param connectionSelectorPolicy factory of the connection pool strategy to use with this load balancer.
     * @param connectionFactory a function which creates new connections.
     * @param minConnectionsPerHost the minimum number of connections allowed for active hosts.
     * @param loadBalancerObserverFactory factory used to build a {@link LoadBalancerObserver} to use with this
     *                                    load balancer.
     * @param healthCheckConfig configuration for the health checking mechanism, which monitors hosts that
     * are unable to have a connection established. Providing {@code null} disables this mechanism (meaning the host
     * continues being eligible for connecting on the request path).
     * @param outlierDetectorFactory outlier detector factory.
     */
    DefaultLoadBalancer(
            final String id,
            final String targetResource,
            final Publisher<? extends Collection<? extends ServiceDiscovererEvent<ResolvedAddress>>> eventPublisher,
            final Function<String, HostPriorityStrategy> priorityStrategyFactory,
            final LoadBalancingPolicy<ResolvedAddress, C> loadBalancingPolicy,
            final Subsetter.SubsetterFactory subsetterFactory,
            final ConnectionSelectorPolicy<C> connectionSelectorPolicy,
            final ConnectionFactory<ResolvedAddress, ? extends C> connectionFactory,
            final int minConnectionsPerHost,
            final LoadBalancerObserverFactory loadBalancerObserverFactory,
            @Nullable final HealthCheckConfig healthCheckConfig,
            final Function<String, OutlierDetector<ResolvedAddress, C>> outlierDetectorFactory) {
        this.lbDescription = makeDescription(id, targetResource);
        this.subsetter = requireNonNull(subsetterFactory.newSubsetter(lbDescription));
        this.hostSelector = requireNonNull(loadBalancingPolicy.buildSelector(emptyList(), lbDescription));
        this.priorityStrategy = requireNonNull(priorityStrategyFactory.apply(lbDescription));
        this.connectionSelector = requireNonNull(connectionSelectorPolicy.buildConnectionSelector(lbDescription));
        this.outlierDetector = requireNonNull(outlierDetectorFactory.apply(lbDescription));
        this.eventPublisher = eventPublisher;
        this.eventStream = fromSource(eventStreamProcessor)
                .replay(1); // Allow for multiple subscribers and provide new subscribers with last signal.
        this.connectionFactory = connectionFactory;
        this.minConnectionsPerHost = minConnectionsPerHost;
        this.loadBalancerObserver = CatchAllLoadBalancerObserver.wrap(
                loadBalancerObserverFactory.newObserver(lbDescription));
        this.healthCheckConfig = healthCheckConfig;
        this.sequentialExecutor = new SequentialExecutor((uncaughtException) ->
                LOGGER.error("{}: Uncaught exception in {}", this, this.getClass().getSimpleName(), uncaughtException));
        this.asyncCloseable = toAsyncCloseable(this::doClose);
        // Maintain a Subscriber so signals are always delivered to replay and new Subscribers get the latest signal.
        eventStream.ignoreElements().subscribe();
        // When we get a health-status event we should update the host set.
        this.outlierDetectorStatusChangeStream = this.outlierDetector.healthStatusChanged().forEach((ignored) ->
            sequentialExecutor.execute(() -> sequentialUpdateUsedHosts(usedHosts, true)));

        // We subscribe to events as the very last step so that if we subscribe to an eager service discoverer
        // we already have all the fields initialized.
        subscribeToEvents(false);

        LOGGER.info("{}: starting load balancer. Load balancing policy: {}, outlier detection: {}", this,
                loadBalancingPolicy, outlierDetector);
    }

    private void subscribeToEvents(boolean resubscribe) {
        // This method is invoked only when we are in RESUBSCRIBING state. Only one thread can own this state.
        assert nextResubscribeTime == RESUBSCRIBING;
        if (resubscribe) {
            assert healthCheckConfig != null : "Resubscribe can happen only when health-checking is configured";
            LOGGER.debug("{}: resubscribing to the ServiceDiscoverer event publisher.", this);
            discoveryCancellable.cancelCurrent();
        }
        final EventSubscriber eventSubscriber = new EventSubscriber(resubscribe);
        this.currentSubscriber = eventSubscriber;
        toSource(eventPublisher).subscribe(eventSubscriber);
        if (healthCheckConfig != null) {
            assert healthCheckConfig.executor instanceof NormalizedTimeSourceExecutor;
            nextResubscribeTime = nextResubscribeTime(healthCheckConfig, this);
        }
    }

    // This method is called eagerly, meaning the completable will be immediately subscribed to,
    // so we don't need to do any Completable.defer business.
    private Completable doClose(final boolean graceful) {
        CompletableSource.Processor processor = Processors.newCompletableProcessor();
        sequentialExecutor.execute(() -> {
            try {
                if (!isClosed) {
                    discoveryCancellable.cancel();
                    eventStreamProcessor.onComplete();
                    outlierDetectorStatusChangeStream.cancel();
                    outlierDetector.cancel();
                }
                isClosed = true;
                List<PrioritizedHostImpl<ResolvedAddress, C>> currentList = usedHosts;
                final CompositeCloseable compositeCloseable = newCompositeCloseable()
                        .appendAll(currentList)
                        .appendAll(connectionFactory);
                LOGGER.debug("{} is closing {}gracefully. Last seen addresses (size={}): {}.",
                        this, graceful ? "" : "non", currentList.size(), currentList);
                SourceAdapters.toSource((graceful ? compositeCloseable.closeAsyncGracefully() :
                                // We only want to empty the host list on error if we're closing non-gracefully.
                                compositeCloseable.closeAsync().beforeOnError(t ->
                                        sequentialExecutor.execute(this::sequentialCompleteClosed))
                                // we want to always empty out the host list if we complete successfully
                                .beforeOnComplete(() -> sequentialExecutor.execute(this::sequentialCompleteClosed))))
                        .subscribe(processor);
            } catch (Throwable ex) {
                processor.onError(ex);
            }
        });
        return SourceAdapters.fromSource(processor);
    }

    // must be called from within the sequential executor.
    private void sequentialCompleteClosed() {
        usedHosts = emptyList();
        hostSelector = new ClosedHostSelector();
    }

    private static <R, C extends LoadBalancedConnection> long nextResubscribeTime(
            final HealthCheckConfig config, final DefaultLoadBalancer<R, C> lb) {
        final long lowerNanos = config.healthCheckResubscribeLowerBound;
        final long upperNanos = config.healthCheckResubscribeUpperBound;
        final long currentTimeNanos = config.executor.currentTime(NANOSECONDS);
        final long result = currentTimeNanos + RandomUtils.nextLongInclusive(lowerNanos, upperNanos);
        LOGGER.debug("{}: current time {}, next resubscribe attempt can be performed at {}.",
                lb, currentTimeNanos, result);
        return result;
    }

    private static <ResolvedAddress, C extends LoadBalancedConnection> boolean contains(
            final Host<ResolvedAddress, C> host,
            final Collection<? extends ServiceDiscovererEvent<ResolvedAddress>> events) {
        for (ServiceDiscovererEvent<ResolvedAddress> event : events) {
            if (host.address().equals(event.address())) {
                return true;
            }
        }
        return false;
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
            if (events == null || events.isEmpty()) {
                LOGGER.debug("{}: unexpectedly received null or empty collection instead of events: {}",
                        DefaultLoadBalancer.this, events);
                return;
            }
            sequentialExecutor.execute(() -> sequentialOnNext(events));
        }

        private void sequentialOnNext(Collection<? extends ServiceDiscovererEvent<ResolvedAddress>> events) {
            assert !events.isEmpty();
            if (isClosed) {
                // nothing to do if the load balancer is closed.
                return;
            }

            // According to Reactive Streams Rule 1.8
            // (https://github.com/reactive-streams/reactive-streams-jvm?tab=readme-ov-file#1.8) new events will
            // stop eventually but not guaranteed to stop immediately after cancellation or could race with cancel.
            // Therefore, we should check that this is the current Subscriber before processing new events.
            if (currentSubscriber != this) {
                LOGGER.debug("{}: received new events after cancelling previous subscription, discarding: {}",
                        DefaultLoadBalancer.this, events);
                return;
            }

            // Notify the observer first so that we can emit the service discovery event before we start (potentially)
            // emitting events related to creating new hosts.
            loadBalancerObserver.onServiceDiscoveryEvent(events);

            boolean sendReadyEvent = false;
            final List<PrioritizedHostImpl<ResolvedAddress, C>> nextHosts = new ArrayList<>(
                    usedHosts.size() + events.size());
            final List<PrioritizedHostImpl<ResolvedAddress, C>> oldUsedHosts = usedHosts;
            // First we make a map of addresses to events so that we don't get quadratic behavior for diffing.
            final Map<ResolvedAddress, ServiceDiscovererEvent<ResolvedAddress>> eventMap = new HashMap<>();
            for (ServiceDiscovererEvent<ResolvedAddress> event : events) {
                ServiceDiscovererEvent<ResolvedAddress> old = eventMap.put(event.address(), event);
                if (old != null) {
                    LOGGER.debug("Multiple ServiceDiscoveryEvent's detected for address {}. Event: {}.",
                            event.address(), event);
                }
            }

            // First thing we do is go through the existing hosts and see if we need to transfer them. These
            // will be all existing hosts that either don't have a matching discovery event or are not marked
            // as unavailable. If they are marked unavailable, we need to close them.
            boolean hostSetChanged = false;
            for (PrioritizedHostImpl<ResolvedAddress, C> host : oldUsedHosts) {
                ServiceDiscovererEvent<ResolvedAddress> event = eventMap.remove(host.address());
                if (event == null) {
                    // Host doesn't have a SD update so just copy it over.
                    nextHosts.add(host);
                    continue;
                }
                // Set the new weight and priority of the host.
                double oldSDWeight = host.serviceDiscoveryWeight();
                int oldPriority = host.priority();
                host.serviceDiscoveryWeight(eventWeight(event));
                host.priority(eventPriority(event));
                hostSetChanged |= oldPriority != host.priority() || oldSDWeight != host.serviceDiscoveryWeight();

                if (AVAILABLE.equals(event.status())) {
                    // We only send the ready event if the previous host list was empty.
                    sendReadyEvent = oldUsedHosts.isEmpty();
                    // If the host is already in CLOSED state, we should discard it and create a new entry.
                    // For duplicate ACTIVE events the marking succeeds, so we will not add a new entry.
                    if (host.markActiveIfNotClosed()) {
                        nextHosts.add(host);
                    } else {
                        // It's a new host, so the set changed.
                        hostSetChanged = true;
                        nextHosts.add(createHost(event));
                    }
                } else if (EXPIRED.equals(event.status())) {
                    if (!host.markExpired()) {
                        nextHosts.add(host);
                    } else {
                        // Marking it expired also resulted in removing it from the set.
                        hostSetChanged = true;
                    }
                } else if (UNAVAILABLE.equals(event.status())) {
                    host.closeAsyncGracefully()
                            .beforeOnError(error -> LOGGER.warn("Closing host {} failed.", host.address(), error))
                            .subscribe();
                    hostSetChanged = true;
                } else {
                    LOGGER.warn("{}: Unsupported Status in event:" +
                                    " {} (mapped to {}). Leaving usedHosts unchanged: {}",
                            DefaultLoadBalancer.this, event, event.status(), nextHosts);
                    nextHosts.add(host);
                }
            }
            // Now process events that didn't have an existing host. The only ones that we actually care
            // about are the AVAILABLE events which result in a new host.
            for (ServiceDiscovererEvent<ResolvedAddress> event : eventMap.values()) {
                if (AVAILABLE.equals(event.status())) {
                    sendReadyEvent = true;
                    hostSetChanged = true;
                    nextHosts.add(createHost(event));
                }
            }
            sequentialUpdateUsedHosts(nextHosts, hostSetChanged);
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

                // New Subscription to the ServiceDiscoverer always starts from a state of the world. To be in sync with
                // the ServiceDiscoverer state, we should clean up and close gracefully all hosts that are not present
                // in the initial collection of events, regardless of their current state.
                for (Host<ResolvedAddress, C> host : nextHosts) {
                    if (!contains(host, events)) {
                        host.closeAsyncGracefully().subscribe();
                    }
                }
            }
        }

        private PrioritizedHostImpl<ResolvedAddress, C> createHost(ServiceDiscovererEvent<ResolvedAddress> event) {
            ResolvedAddress addr = event.address();
            final LoadBalancerObserver.HostObserver hostObserver = loadBalancerObserver.hostObserver(addr);
            // All hosts will share the health check config of the parent load balancer.
            final HealthIndicator<ResolvedAddress, C> indicator =
                    outlierDetector.newHealthIndicator(addr, hostObserver);
            // We don't need the host level health check if we are either not health checking at all or if the
            // failed connect threshold is negative, meaning disabled.
            final HealthCheckConfig hostHealthCheckConfig =
                    healthCheckConfig == null || healthCheckConfig.failedThreshold < 0 ? null : healthCheckConfig;
            final PrioritizedHostImpl<ResolvedAddress, C> host = new PrioritizedHostImpl<>(
                    new DefaultHost<>(lbDescription, addr, connectionSelector,
                    connectionFactory, minConnectionsPerHost, hostObserver, hostHealthCheckConfig, indicator),
                    eventWeight(event), eventPriority(event));
            if (indicator != null) {
                indicator.setHost(host);
            }
            host.onClose().afterFinally(() ->
                    sequentialExecutor.execute(() -> {
                        final List<PrioritizedHostImpl<ResolvedAddress, C>> currentHosts = usedHosts;
                        if (currentHosts.isEmpty()) {
                            // Can't remove an entry from an empty list.
                            return;
                        }
                        final List<PrioritizedHostImpl<ResolvedAddress, C>> nextHosts = listWithHostRemoved(
                                currentHosts, host);
                        // we only need to do anything else if we actually removed the host
                        if (nextHosts.size() != currentHosts.size()) {
                            sequentialUpdateUsedHosts(nextHosts, true);
                            if (nextHosts.isEmpty()) {
                                // We transitioned from non-empty to empty. That means we're not ready.
                                eventStreamProcessor.onNext(LOAD_BALANCER_NOT_READY_EVENT);
                            }
                        }
                    })).subscribe();
            return host;
        }

        private List<PrioritizedHostImpl<ResolvedAddress, C>> listWithHostRemoved(
                List<PrioritizedHostImpl<ResolvedAddress, C>> oldHostsTyped,
                PrioritizedHostImpl<ResolvedAddress, C> toRemove) {
            final int index = oldHostsTyped.indexOf(toRemove);
            if (index < 0) {
                // Element doesn't exist: just return the old list.
                return oldHostsTyped;
            }
            if (oldHostsTyped.size() == 1) {
                // We're removing the last host in the list so we can just return the empty list.
                return emptyList();
            }
            // Copy the remaining live elements to a new list.
            final List<PrioritizedHostImpl<ResolvedAddress, C>> newHosts =
                    new ArrayList<>(oldHostsTyped.size() - 1);
            for (int i = 0; i < oldHostsTyped.size(); ++i) {
                if (i != index) {
                    newHosts.add(oldHostsTyped.get(i));
                }
            }
            return newHosts;
        }

        @Override
        public void onError(final Throwable t) {
            sequentialExecutor.execute(() -> {
                if (healthCheckConfig == null) {
                    // Terminate processor only if we will never re-subscribe
                    eventStreamProcessor.onError(t);
                }
                List<PrioritizedHostImpl<ResolvedAddress, C>> hosts = usedHosts;
                LOGGER.error(
                    "{}: service discoverer {} emitted an error. Last seen addresses (size={}): {}.",
                        DefaultLoadBalancer.this, eventPublisher, hosts.size(), hosts, t);
            });
        }

        @Override
        public void onComplete() {
            sequentialExecutor.execute(() -> {
                List<PrioritizedHostImpl<ResolvedAddress, C>> hosts = usedHosts;
                if (healthCheckConfig == null) {
                    // Terminate processor only if we will never re-subscribe
                    eventStreamProcessor.onComplete();
                }
                LOGGER.error("{}: service discoverer completed. Last seen addresses (size={}): {}.",
                        DefaultLoadBalancer.this, hosts.size(), hosts);
            });
        }
    }

    // must be called from within the SequentialExecutor
    private void sequentialUpdateUsedHosts(List<PrioritizedHostImpl<ResolvedAddress, C>> nextHosts,
                                           boolean hostSetChanged) {
        HostSelector<ResolvedAddress, C> oldHostSelector = hostSelector;
        HostSelector<ResolvedAddress, C> newHostSelector;
        if (hostSetChanged) {
            this.usedHosts = nextHosts;
            // We need to reset the load balancing weights before we run the host set through the rest
            // of the operations that will transform and consume the load balancing weight.
            for (PrioritizedHostImpl<?, ?> host : nextHosts) {
                host.weight(host.serviceDiscoveryWeight());
            }
            nextHosts = priorityStrategy.prioritize(nextHosts);
            nextHosts = subsetter.subset(nextHosts);

            // We now mark our active hosts. We use two iterations to avoid needing to build sets.
            for (PrioritizedHostImpl<?, ?> host : nextHosts) {
                host.markWithinSubset();
            }
            for (PrioritizedHostImpl<?, ?> host : this.usedHosts) {
                host.activateWithinSubset();
            }

            this.hostSelector = newHostSelector = hostSelector.rebuildWithHosts(nextHosts);
        } else {
            newHostSelector = oldHostSelector;
        }
        final Collection<? extends LoadBalancerObserver.Host> newHosts = newHostSelector.hosts();
        loadBalancerObserver.onHostsUpdate(Collections.unmodifiableCollection(oldHostSelector.hosts()),
                Collections.unmodifiableCollection(newHosts));
        LOGGER.debug("{}: Using addresses (size={}): {}.", this, newHosts.size(), newHosts);
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
        final HostSelector<ResolvedAddress, C> currentHostSelector = hostSelector;
        Single<C> result = currentHostSelector.selectConnection(selector, context, forceNewConnectionAndReserve);
        return result.beforeOnError(exn -> {
            if (exn instanceof NoActiveHostException) {
                if (healthCheckConfig != null && !currentHostSelector.isHealthy()) {
                    final long currNextResubscribeTime = nextResubscribeTime;
                    if (currNextResubscribeTime >= 0 &&
                            healthCheckConfig.executor.currentTime(NANOSECONDS) >= currNextResubscribeTime &&
                            nextResubscribeTimeUpdater.compareAndSet(this, currNextResubscribeTime, RESUBSCRIBING)) {
                        subscribeToEvents(true);
                    }
                }
                loadBalancerObserver.onNoActiveHostException(currentHostSelector.hosts(), (NoActiveHostException) exn);
            } else if (exn instanceof NoAvailableHostException) {
                loadBalancerObserver.onNoAvailableHostException((NoAvailableHostException) exn);
            }
        });
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
        return usedHosts().stream().map(DefaultHost::asEntry).collect(toList());
    }

    List<DefaultHost<ResolvedAddress, C>> usedHosts() {
        return await(() -> {
            List<PrioritizedHostImpl<ResolvedAddress, C>> usedHosts = this.usedHosts;
            List<DefaultHost<ResolvedAddress, C>> result = new ArrayList<>(usedHosts.size());
            for (PrioritizedHostImpl<ResolvedAddress, C> host : usedHosts) {
                result.add(host.delegate);
            }
            return result;
        });
    }

    // For testing, compute a value from within the sequential executor to ensure thread safety.
    private <T> T await(Supplier<T> f) {
        // If we're already in the executor we can't submit a task and wait for it without deadlock but
        // the access is thread safe anyway so just go for it.
        if (sequentialExecutor.isCurrentThreadDraining()) {
            return f.get();
        }
        SingleSource.Processor<T, T> processor =
                Processors.newSingleProcessor();
        sequentialExecutor.execute(() -> {
            try {
                processor.onSuccess(f.get());
            } catch (Throwable ex) {
                processor.onError(ex);
            }
        });
        try {
            // This method is just for testing and our tests have timeouts it's fine to do some awaiting.
            return fromSource(processor).toFuture().get();
        } catch (Exception ex) {
            throw new AssertionError("Failed to get results", ex);
        }
    }

    private String makeDescription(String id, String targetResource) {
        return getClass().getSimpleName() + "{" +
                "id=" + id + '@' + toHexString(identityHashCode(this)) +
                ", targetResource=" + targetResource +
                '}';
    }

    private final class ClosedHostSelector implements HostSelector<ResolvedAddress, C> {
        @Override
        public Single<C> selectConnection(Predicate<C> selector, @Nullable ContextMap context,
                                          boolean forceNewConnectionAndReserve) {
            return failed(new IllegalStateException(lbDescription + ": LoadBalancer has closed"));
        }

        @Override
        public HostSelector<ResolvedAddress, C> rebuildWithHosts(List<? extends Host<ResolvedAddress, C>> hosts) {
            return this;
        }

        @Override
        public boolean isHealthy() {
            return false;
        }

        @Override
        public Collection<? extends LoadBalancerObserver.Host> hosts() {
            return emptyList();
        }
    }

    private double eventWeight(ServiceDiscovererEvent<?> event) {
        double weight = 1;
        if (event instanceof RichServiceDiscovererEvent<?>) {
            weight = ((RichServiceDiscovererEvent<?>) event).loadBalancingWeight();
            if (weight < 0) {
                LOGGER.debug("{} Unexpected negative weight {} for host {} being set to 1.0",
                        lbDescription, weight, event.address());
                weight = 1;
            }
        }
        return weight;
    }

    private int eventPriority(ServiceDiscovererEvent<?> event) {
        int priority = 0;
        if (event instanceof RichServiceDiscovererEvent<?>) {
            priority = ((RichServiceDiscovererEvent<?>) event).priority();
            if (priority < 0) {
                LOGGER.debug("{} Unexpected negative priority {} for host {} being set to 0",
                        lbDescription, priority, event.address());
                priority = 0;
            }
        }
        return priority;
    }

    // Exposed for testing
    List<PrioritizedHostImpl<ResolvedAddress, C>> hosts() {
        return new ArrayList<>(usedHosts);
    }

    static final class PrioritizedHostImpl<ResolvedAddress, C extends LoadBalancedConnection>
            implements Host<ResolvedAddress, C>, PrioritizedHost {

        private final long randomSeed = ThreadLocalRandom.current().nextLong();
        private final DefaultHost<ResolvedAddress, C> delegate;
        private int priority;
        private double serviceDiscoveryWeight;
        private double loadBalancingWeight;
        // This field is only used for marking within the `sequentialUpdateUsedHosts`, where single-threaded
        // behavior is guaranteed.
        private boolean withinSubsetMark;

        PrioritizedHostImpl(final DefaultHost<ResolvedAddress, C> delegate, final double serviceDiscoveryWeight,
                            final int priority) {
            this.delegate = delegate;
            this.priority = priority;
            this.serviceDiscoveryWeight = serviceDiscoveryWeight;
            this.loadBalancingWeight = serviceDiscoveryWeight;
        }

        @Override
        public long randomSeed() {
            return randomSeed;
        }

        @Override
        public int priority() {
            return priority;
        }

        void priority(final int priority) {
            this.priority = priority;
        }

        // Set the intrinsic weight of the host. This is the information from service discovery.
        // When this is set it also overwrites the load balancing weight which must then be recalculated.
        void serviceDiscoveryWeight(final double weight) {
            this.serviceDiscoveryWeight = weight;
        }

        double serviceDiscoveryWeight() {
            return serviceDiscoveryWeight;
        }

        // Called to annotate that this particular host is within the subset.
        void markWithinSubset() {
            this.withinSubsetMark = true;
        }

        // On the second pass of marking all hosts have this method called. It sets whether it was marked the first
        // iteration and also clears the flag for future iterations.
        void activateWithinSubset() {
            delegate.isWithinSubset(this.withinSubsetMark);
            this.withinSubsetMark = false;
        }

        // Set the weight to use in load balancing. This includes derived weight information such as prioritization
        // and is what the host selectors will use when picking hosts.
        @Override
        public void weight(final double weight) {
            this.loadBalancingWeight = weight;
        }

        @Override
        public double weight() {
            return loadBalancingWeight;
        }

        @Override
        public int score() {
            return delegate.score();
        }

        @Override
        public Completable closeAsync() {
            return delegate.closeAsync();
        }

        @Override
        public Completable closeAsyncGracefully() {
            return delegate.closeAsyncGracefully();
        }

        @Override
        public Completable onClose() {
            return delegate.onClose();
        }

        @Override
        public Completable onClosing() {
            return delegate.onClosing();
        }

        @Nullable
        @Override
        public C pickConnection(Predicate<C> selector, @Nullable ContextMap context) {
            return delegate.pickConnection(selector, context);
        }

        @Override
        public Single<C> newConnection(Predicate<C> selector, boolean forceNewConnectionAndReserve,
                                       @Nullable ContextMap context) {
            return delegate.newConnection(selector, forceNewConnectionAndReserve, context);
        }

        @Override
        public ResolvedAddress address() {
            return delegate.address();
        }

        @Override
        public boolean isHealthy() {
            return delegate.isHealthy();
        }

        @Override
        public boolean canMakeNewConnections() {
            return delegate.canMakeNewConnections();
        }

        @Override
        public boolean markActiveIfNotClosed() {
            return delegate.markActiveIfNotClosed();
        }

        @Override
        public boolean markExpired() {
            return delegate.markExpired();
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + "(priority: " + priority +
                ", intrinsicWeight: " + serviceDiscoveryWeight +
                ", loadBalancedWeight: " + loadBalancingWeight +
                ", host: " + delegate +
                ")";
        }
    }
}
