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
import io.servicetalk.client.api.NoActiveHostException;
import io.servicetalk.client.api.NoAvailableHostException;
import io.servicetalk.client.api.ServiceDiscovererEvent;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;
import java.util.function.Function;
import java.util.function.Predicate;
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

    // writes are protected by `sequentialExecutor` but the field can be read by any thread.
    private volatile HostSelector<ResolvedAddress, C> hostSelector;
    // reads and writes are protected by `sequentialExecutor`.
    private List<Host<ResolvedAddress, C>> usedHosts = emptyList();
    // reads and writes are protected by `sequentialExecutor`.
    private boolean isClosed;

    private final String targetResource;
    private final SequentialExecutor sequentialExecutor;
    private final String lbDescription;
    private final Publisher<? extends Collection<? extends ServiceDiscovererEvent<ResolvedAddress>>> eventPublisher;
    private final Processor<Object, Object> eventStreamProcessor = newPublisherProcessorDropHeadOnOverflow(32);
    private final Publisher<Object> eventStream;
    private final SequentialCancellable discoveryCancellable = new SequentialCancellable();
    private final ConnectionPoolStrategy<C> connectionPoolStrategy;
    private final ConnectionFactory<ResolvedAddress, ? extends C> connectionFactory;
    @Nullable
    private final HealthCheckConfig healthCheckConfig;
    private final OutlierDetector<ResolvedAddress, C> outlierDetector;
    private final LoadBalancerObserver loadBalancerObserver;
    private final ListenableAsyncCloseable asyncCloseable;

    /**
     * Creates a new instance.
     *
     * @param id a (unique) ID to identify the created {@link DefaultLoadBalancer}.
     * @param targetResourceName {@link String} representation of the target resource for which this instance
     * is performing load balancing.
     * @param eventPublisher provides a stream of addresses to connect to.
     * @param connectionFactory a function which creates new connections.
     * @param healthCheckConfig configuration for the health checking mechanism, which monitors hosts that
     * are unable to have a connection established. Providing {@code null} disables this mechanism (meaning the host
     * continues being eligible for connecting on the request path).
     * @see RoundRobinLoadBalancerFactory
     */
    DefaultLoadBalancer(
            final String id,
            final String targetResourceName,
            final Publisher<? extends Collection<? extends ServiceDiscovererEvent<ResolvedAddress>>> eventPublisher,
            final HostSelector<ResolvedAddress, C> hostSelector,
            final ConnectionPoolStrategy<C> connectionPoolStrategy,
            final ConnectionFactory<ResolvedAddress, ? extends C> connectionFactory,
            final LoadBalancerObserver loadBalancerObserver,
            @Nullable final HealthCheckConfig healthCheckConfig,
            final Function<String, OutlierDetector<ResolvedAddress, C>> outlierDetectorFactory) {
        this.targetResource = requireNonNull(targetResourceName);
        this.lbDescription = makeDescription(id, targetResource);
        this.hostSelector = requireNonNull(hostSelector, "hostSelector");
        this.connectionPoolStrategy = requireNonNull(connectionPoolStrategy, "connectionPoolStrategy");
        this.eventPublisher = requireNonNull(eventPublisher);
        this.eventStream = fromSource(eventStreamProcessor)
                .replay(1); // Allow for multiple subscribers and provide new subscribers with last signal.
        this.connectionFactory = requireNonNull(connectionFactory);
        this.loadBalancerObserver = requireNonNull(loadBalancerObserver, "loadBalancerObserver");
        this.healthCheckConfig = healthCheckConfig;
        this.sequentialExecutor = new SequentialExecutor((uncaughtException) ->
                LOGGER.error("{}: Uncaught exception in {}", this, this.getClass().getSimpleName(), uncaughtException));
        this.asyncCloseable = toAsyncCloseable(this::doClose);
        // Maintain a Subscriber so signals are always delivered to replay and new Subscribers get the latest signal.
        eventStream.ignoreElements().subscribe();
        this.outlierDetector = requireNonNull(outlierDetectorFactory, "outlierDetectorFactory").apply(lbDescription);
        // We subscribe to events as the very last step so that if we subscribe to an eager service discoverer
        // we already have all the fields initialized.
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

    // This method is called eagerly, meaning the completable will be immediately subscribed to,
    // so we don't need to do any Completable.defer business.
    private Completable doClose(final boolean graceful) {
        CompletableSource.Processor processor = Processors.newCompletableProcessor();
        sequentialExecutor.execute(() -> {
            try {
                if (!isClosed) {
                    discoveryCancellable.cancel();
                    eventStreamProcessor.onComplete();
                    outlierDetector.cancel();
                }
                isClosed = true;
                List<Host<ResolvedAddress, C>> currentList = usedHosts;
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
        final long result = currentTimeNanos + (lowerNanos == upperNanos ? lowerNanos :
                RandomUtils.nextLongInclusive(lowerNanos, upperNanos));
        LOGGER.debug("{}: current time {}, next resubscribe attempt can be performed at {}.",
                lb, currentTimeNanos, result);
        return result;
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
            if (events == null || events.isEmpty()) {
                LOGGER.debug("{}: unexpectedly received null or empty list instead of events.",
                        DefaultLoadBalancer.this);
                return;
            }
            sequentialExecutor.execute(() -> sequentialOnNext(events));
        }

        private void sequentialOnNext(Collection<? extends ServiceDiscovererEvent<ResolvedAddress>> events) {
            if (isClosed || events.isEmpty()) {
                // nothing to do if the load balancer is closed or there are no events.
                return;
            }

            boolean sendReadyEvent = false;
            final List<Host<ResolvedAddress, C>> nextHosts = new ArrayList<>(usedHosts.size() + events.size());
            final List<Host<ResolvedAddress, C>> oldUsedHosts = usedHosts;
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
            for (Host<ResolvedAddress, C> host : oldUsedHosts) {
                ServiceDiscovererEvent<ResolvedAddress> event = eventMap.remove(host.address());
                if (event == null) {
                    // Host doesn't have a SD update so just copy it over.
                    nextHosts.add(host);
                } else if (AVAILABLE.equals(event.status())) {
                    // We only send the ready event if the previous host list was empty.
                    sendReadyEvent = oldUsedHosts.isEmpty();
                    // If the host is already in CLOSED state, we should discard it and create a new entry.
                    // For duplicate ACTIVE events the marking succeeds, so we will not add a new entry.
                    if (host.markActiveIfNotClosed()) {
                        nextHosts.add(host);
                    } else {
                        // It's a new host, so the set changed.
                        hostSetChanged = true;
                        nextHosts.add(createHost(event.address()));
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
                    nextHosts.add(createHost(event.address()));
                }
            }

            // Always send the event regardless of if we update the actual list.
            loadBalancerObserver.onServiceDiscoveryEvent(events, usedHosts.size(), nextHosts.size());
            // We've built a materially different host set so now set it for consumption and send our events.
            if (hostSetChanged) {
                sequentialUpdateUsedHosts(nextHosts);
            }

            LOGGER.debug("{}: now using addresses (size={}): {}.",
                    DefaultLoadBalancer.this, nextHosts.size(), nextHosts);
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
            final LoadBalancerObserver.HostObserver hostObserver = loadBalancerObserver.hostObserver(addr);
            // All hosts will share the health check config of the parent load balancer.
            final HealthIndicator indicator = outlierDetector.newHealthIndicator(addr, hostObserver);
            // We don't need the host level health check if we are either not health checking at all or if the
            // failed connect threshold is negative, meaning disabled.
            final HealthCheckConfig hostHealthCheckConfig =
                    healthCheckConfig == null || healthCheckConfig.failedThreshold < 0 ? null : healthCheckConfig;
            final Host<ResolvedAddress, C> host = new DefaultHost<>(lbDescription, addr, connectionPoolStrategy,
                    connectionFactory, hostObserver, hostHealthCheckConfig, indicator);
            if (indicator != null) {
                indicator.setHost(host);
            }
            host.onClose().afterFinally(() ->
                    sequentialExecutor.execute(() -> {
                        final List<Host<ResolvedAddress, C>> currentHosts = usedHosts;
                        if (currentHosts.isEmpty()) {
                            // Can't remove an entry from an empty list.
                            return;
                        }
                        final List<Host<ResolvedAddress, C>> nextHosts = listWithHostRemoved(currentHosts, host);
                        // we only need to do anything else if we actually removed the host
                        if (nextHosts.size() != currentHosts.size()) {
                            sequentialUpdateUsedHosts(nextHosts);
                            if (nextHosts.isEmpty()) {
                                // We transitioned from non-empty to empty. That means we're not ready.
                                eventStreamProcessor.onNext(LOAD_BALANCER_NOT_READY_EVENT);
                            }
                        }
                    })).subscribe();
            return host;
        }

        private List<Host<ResolvedAddress, C>> listWithHostRemoved(List<Host<ResolvedAddress, C>> oldHostsTyped,
                                                                   Host<ResolvedAddress, C> toRemove) {
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
            final List<Host<ResolvedAddress, C>> newHosts = new ArrayList<>(oldHostsTyped.size() - 1);
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
                List<Host<ResolvedAddress, C>> hosts = usedHosts;
                LOGGER.error(
                    "{}: service discoverer {} emitted an error. Last seen addresses (size={}): {}.",
                        DefaultLoadBalancer.this, eventPublisher, hosts.size(), hosts, t);
            });
        }

        @Override
        public void onComplete() {
            sequentialExecutor.execute(() -> {
                List<Host<ResolvedAddress, C>> hosts = usedHosts;
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
    private void sequentialUpdateUsedHosts(List<Host<ResolvedAddress, C>> nextHosts) {
        this.usedHosts = nextHosts;
        this.hostSelector = hostSelector.rebuildWithHosts(usedHosts);
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
                if (!currentHostSelector.isHealthy()) {
                    final long currNextResubscribeTime = nextResubscribeTime;
                    if (currNextResubscribeTime >= 0 &&
                            healthCheckConfig.executor.currentTime(NANOSECONDS) >= currNextResubscribeTime &&
                            nextResubscribeTimeUpdater.compareAndSet(this, currNextResubscribeTime, RESUBSCRIBING)) {
                        subscribeToEvents(true);
                    }
                }
                loadBalancerObserver.onNoActiveHostsAvailable(
                        currentHostSelector.hostSetSize(), (NoActiveHostException) exn);
            } else if (exn instanceof NoAvailableHostException) {
                loadBalancerObserver.onNoHostsAvailable();
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
        // If we're already in the executor we can't submit a task and wait for it without deadlock but
        // the access is thread safe anyway so just go for it.
        if (sequentialExecutor.isCurrentThreadDraining()) {
            return sequentialUsedAddresses();
        }
        SingleSource.Processor<List<Entry<ResolvedAddress, List<C>>>, List<Entry<ResolvedAddress, List<C>>>> processor =
                Processors.newSingleProcessor();
        sequentialExecutor.execute(() -> processor.onSuccess(sequentialUsedAddresses()));
        try {
            // This method is just for testing and our tests have timeouts it's fine to do some awaiting.
            return SourceAdapters.fromSource(processor).toFuture().get();
        } catch (Exception ex) {
            throw new AssertionError("Failed to get results", ex);
        }
    }

    // must be called from within the sequential executor.
    private List<Entry<ResolvedAddress, List<C>>> sequentialUsedAddresses() {
        return usedHosts.stream().map(host -> ((DefaultHost<ResolvedAddress, C>) host).asEntry()).collect(toList());
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
            return failed(new IllegalStateException("LoadBalancer for " + targetResource + " has closed"));
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
        public int hostSetSize() {
            return 0;
        }
    }
}
