/*
 * Copyright © 2021-2023 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.client.api.ConnectionLimitReachedException;
import io.servicetalk.client.api.LoadBalancedConnection;
import io.servicetalk.concurrent.api.AsyncCloseable;
import io.servicetalk.concurrent.api.AsyncContext;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.ListenableAsyncCloseable;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.internal.DelayedCancellable;
import io.servicetalk.context.api.ContextMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.AbstractMap;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.AsyncCloseables.toAsyncCloseable;
import static io.servicetalk.concurrent.api.Completable.completed;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.api.RetryStrategies.retryWithConstantBackoffDeltaJitter;
import static io.servicetalk.concurrent.api.Single.failed;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.concurrent.internal.FlowControlUtils.addWithOverflowProtection;
import static java.lang.Math.min;
import static java.util.concurrent.atomic.AtomicReferenceFieldUpdater.newUpdater;
import static java.util.stream.Collectors.toList;

final class DefaultHost<Addr, C extends LoadBalancedConnection> implements Host<Addr, C> {

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

    private static final Object[] EMPTY_ARRAY = new Object[0];
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultHost.class);

    private enum State {
        // The enum is not exhaustive, as other states have dynamic properties.
        // For clarity, the other state classes are listed as comments:
        // ACTIVE - see ActiveState
        // UNHEALTHY - see HealthCheck
        EXPIRED,
        CLOSED
    }

    private static final ActiveState STATE_ACTIVE_NO_FAILURES = new ActiveState();
    private static final ConnState ACTIVE_EMPTY_CONN_STATE = new ConnState(EMPTY_ARRAY, STATE_ACTIVE_NO_FAILURES);
    private static final ConnState CLOSED_CONN_STATE = new ConnState(EMPTY_ARRAY, State.CLOSED);

    @SuppressWarnings("rawtypes")
    private static final AtomicReferenceFieldUpdater<DefaultHost, ConnState> connStateUpdater =
            newUpdater(DefaultHost.class, ConnState.class, "connState");

    private final String lbDescription;
    final Addr address;
    @Nullable
    private final HealthCheckConfig healthCheckConfig;
    private final ConnectionFactory<Addr, ? extends C> connectionFactory;
    private final int linearSearchSpace;
    private final ListenableAsyncCloseable closeable;
    private volatile ConnState connState = ACTIVE_EMPTY_CONN_STATE;

    DefaultHost(String lbDescription, Addr address, ConnectionFactory<Addr, ? extends C> connectionFactory,
                int linearSearchSpace, @Nullable HealthCheckConfig healthCheckConfig) {
        this.lbDescription = lbDescription;
        this.address = address;
        this.healthCheckConfig = healthCheckConfig;
        this.connectionFactory = connectionFactory;
        this.linearSearchSpace = linearSearchSpace;
        this.closeable = toAsyncCloseable(graceful ->
                graceful ? doClose(AsyncCloseable::closeAsyncGracefully) : doClose(AsyncCloseable::closeAsync));
    }

    @Override
    public Addr address() {
        return address;
    }

    @Override
    public boolean markActiveIfNotClosed() {
        final Object oldState = connStateUpdater.getAndUpdate(this, oldConnState -> {
            if (oldConnState.state == State.EXPIRED) {
                return new ConnState(oldConnState.connections, STATE_ACTIVE_NO_FAILURES);
            }
            // If oldConnState.state == State.ACTIVE this could mean either a duplicate event,
            // or a repeated CAS operation. We could issue a warning, but as we don't know, we don't log anything.
            // UNHEALTHY state cannot transition to ACTIVE without passing the health check.
            return oldConnState;
        }).state;
        return oldState != State.CLOSED;
    }

    @Override
    public void markClosed() {
        final ConnState oldState = closeConnState();
        final Object[] toRemove = oldState.connections;
        cancelIfHealthCheck(oldState);
        LOGGER.debug("{}: closing {} connection(s) gracefully to the closed address: {}.",
                lbDescription, toRemove.length, address);
        for (Object conn : toRemove) {
            @SuppressWarnings("unchecked")
            final C cConn = (C) conn;
            cConn.closeAsyncGracefully().subscribe();
        }
    }

    private ConnState closeConnState() {
        for (;;) {
            // We need to keep the oldState.connections around even if we are closed because the user may do
            // closeGracefully with a timeout, which fails, and then force close. If we discard connections when
            // closeGracefully is started we may leak connections.
            final ConnState oldState = connState;
            if (oldState.state == State.CLOSED || connStateUpdater.compareAndSet(this, oldState,
                    new ConnState(oldState.connections, State.CLOSED))) {
                return oldState;
            }
        }
    }

    @Override
    public void markExpired() {
        for (;;) {
            ConnState oldState = connStateUpdater.get(this);
            if (oldState.state == State.EXPIRED || oldState.state == State.CLOSED) {
                break;
            }
            Object nextState = oldState.connections.length == 0 ? State.CLOSED : State.EXPIRED;

            if (connStateUpdater.compareAndSet(this, oldState,
                    new ConnState(oldState.connections, nextState))) {
                cancelIfHealthCheck(oldState);
                if (nextState == State.CLOSED) {
                    // Trigger the callback to remove the host from usedHosts array.
                    this.closeAsync().subscribe();
                }
                break;
            }
        }
    }

    @Override
    public @Nullable C pickConnection(Predicate<C> selector, @Nullable final ContextMap context) {
        final Object[] connections = connState.connections;
        // Exhaust the linear search space first:
        final int linearAttempts = min(connections.length, linearSearchSpace);
        for (int j = 0; j < linearAttempts; ++j) {
            @SuppressWarnings("unchecked")
            final C connection = (C) connections[j];
            if (selector.test(connection)) {
                return connection;
            }
        }
        // Try other connections randomly:
        if (connections.length > linearAttempts) {
            final int diff = connections.length - linearAttempts;
            // With small enough search space, attempt number of times equal to number of remaining connections.
            // Back off after exploring most of the search space, it gives diminishing returns.
            final int randomAttempts = diff < MIN_RANDOM_SEARCH_SPACE ? diff :
                    (int) (diff * RANDOM_SEARCH_FACTOR);
            final ThreadLocalRandom rnd = ThreadLocalRandom.current();
            for (int j = 0; j < randomAttempts; ++j) {
                @SuppressWarnings("unchecked")
                final C connection = (C) connections[rnd.nextInt(linearAttempts, connections.length)];
                if (selector.test(connection)) {
                    return connection;
                }
            }
        }
        // So sad, we didn't find a healthy connection.
        return null;
    }

    @Override
    public Single<C> newConnection(
            Predicate<C> selector, final boolean forceNewConnectionAndReserve, @Nullable final ContextMap context) {
        // This LB implementation does not automatically provide TransportObserver. Therefore, we pass "null" here.
        // Users can apply a ConnectionFactoryFilter if they need to override this "null" value with TransportObserver.
        Single<? extends C> establishConnection = connectionFactory.newConnection(address, context, null);
        if (healthCheckConfig != null) {
            // Schedule health check before returning
            establishConnection = establishConnection.beforeOnError(t -> markUnhealthy(t));
        }
        return establishConnection
            .flatMap(newCnx -> {
                if (forceNewConnectionAndReserve && !newCnx.tryReserve()) {
                    return newCnx.closeAsync().<C>concat(failed(
                            Exceptions.StacklessConnectionRejectedException.newInstance(
                                    "Newly created connection " + newCnx + " for " + lbDescription
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
                    Single<C> failedSingle = failed(Exceptions.StacklessConnectionRejectedException.newInstance(
                            "Newly created connection " + newCnx + " for " + lbDescription
                                    + " was rejected by the selection filter.",
                            RoundRobinLoadBalancer.class, "selectConnection0(...)"));

                    // Just in case the connection is not closed add it to the host so we don't lose track,
                    // duplicates will be filtered out.
                    return (addConnection(newCnx, null) ?
                            failedSingle : newCnx.closeAsync().concat(failedSingle)).shareContextOnSubscribe();
                }
                if (addConnection(newCnx, null)) {
                    return succeeded(newCnx).shareContextOnSubscribe();
                }
                return newCnx.closeAsync().<C>concat(
                                failed(Exceptions.StacklessConnectionRejectedException.newInstance(
                                        "Failed to add newly created connection " + newCnx + " for " + toString(),
                                        RoundRobinLoadBalancer.class, "selectConnection0(...)")))
                        .shareContextOnSubscribe();
            });
    }

    private void markHealthy(final HealthCheck<Addr, C> originalHealthCheckState) {
        // Marking healthy is called when we need to recover from an unexpected error.
        // However, it is possible that in the meantime, the host entered an EXPIRED state, then ACTIVE, then failed
        // to open connections and entered the UNHEALTHY state before the original thread continues execution here.
        // In such case, the flipped state is not the same as the one that just succeeded to open a connection.
        // In an unlikely scenario that the following connection attempts fail indefinitely, a health check task
        // would leak and would not be cancelled. Therefore, we cancel it here and allow failures to trigger a new
        // health check.
        ConnState oldState = connStateUpdater.getAndUpdate(this, previous -> {
            if (DefaultHost.isUnhealthy(previous)) {
                return new ConnState(previous.connections, STATE_ACTIVE_NO_FAILURES);
            }
            return previous;
        });
        if (oldState.state != originalHealthCheckState) {
            cancelIfHealthCheck(oldState);
        }
    }

    private void markUnhealthy(final Throwable cause) {
        assert healthCheckConfig != null;
        for (;;) {
            ConnState previous = connStateUpdater.get(this);

            if (!DefaultHost.isActive(previous) || previous.connections.length > 0
                    || cause instanceof ConnectionLimitReachedException) {
                LOGGER.debug("{}: failed to open a new connection to the host on address {}. {}.",
                        lbDescription, address, previous, cause);
                break;
            }

            ActiveState previousState = (ActiveState) previous.state;
            if (previousState.failedConnections + 1 < healthCheckConfig.failedThreshold) {
                final ActiveState nextState = previousState.forNextFailedConnection();
                if (connStateUpdater.compareAndSet(this, previous,
                        new ConnState(previous.connections, nextState))) {
                    LOGGER.debug("{}: failed to open a new connection to the host on address {}" +
                                    " {} time(s) ({} consecutive failures will trigger health-checking).",
                            lbDescription, address, nextState.failedConnections,
                            healthCheckConfig.failedThreshold, cause);
                    break;
                }
                // another thread won the race, try again
                continue;
            }

            final HealthCheck<Addr, C> healthCheck = new HealthCheck<>(connectionFactory, this, cause);
            final ConnState nextState = new ConnState(previous.connections, healthCheck);
            if (connStateUpdater.compareAndSet(this, previous, nextState)) {
                LOGGER.info("{}: failed to open a new connection to the host on address {} " +
                                "{} time(s) in a row. Error counting threshold reached, marking this host as " +
                                "UNHEALTHY for the selection algorithm and triggering background health-checking.",
                        lbDescription, address, healthCheckConfig.failedThreshold, cause);
                healthCheck.schedule(cause);
                break;
            }
        }
    }

    @Override
    public boolean isActiveAndHealthy() {
        return isActive(connState);
    }

    @Override
    public boolean isUnhealthy() {
        return isUnhealthy(connState);
    }

    private static boolean isActive(final ConnState connState) {
        return ActiveState.class.equals(connState.state.getClass());
    }

    private static boolean isUnhealthy(ConnState connState) {
        return HealthCheck.class.equals(connState.state.getClass());
    }

    private boolean addConnection(final C connection, final @Nullable HealthCheck<Addr, C> currentHealthCheck) {
        int addAttempt = 0;
        for (;;) {
            final ConnState previous = connStateUpdater.get(this);
            if (previous.state == State.CLOSED) {
                return false;
            }
            ++addAttempt;

            final Object[] existing = previous.connections;
            // Brute force iteration to avoid duplicates. If connections grow larger and faster lookup is required
            // we can keep a Set for faster lookups (at the cost of more memory) as well as array.
            for (final Object o : existing) {
                if (o.equals(connection)) {
                    return true;
                }
            }
            Object[] newList = Arrays.copyOf(existing, existing.length + 1);
            newList[existing.length] = connection;

            // If we were able to add a new connection to the list, we should mark the host as ACTIVE again and
            // reset its failures counter.
            final Object newState = DefaultHost.isActive(previous) || DefaultHost.isUnhealthy(previous) ?
                    STATE_ACTIVE_NO_FAILURES : previous.state;

            if (connStateUpdater.compareAndSet(this,
                    previous, new ConnState(newList, newState))) {
                // It could happen that the Host turned into UNHEALTHY state either concurrently with adding a new
                // connection or with passing a previous health-check (if SD turned it into ACTIVE state). In both
                // cases we have to cancel the "previous" ongoing health check. See "markHealthy" for more context.
                if (DefaultHost.isUnhealthy(previous) &&
                        (currentHealthCheck == null || previous.state != currentHealthCheck)) {
                    assert newState == STATE_ACTIVE_NO_FAILURES;
                    cancelIfHealthCheck(previous);
                }
                break;
            }
        }

        LOGGER.trace("{}: added a new connection {} to {} after {} attempt(s).",
                lbDescription, connection, this, addAttempt);
        // Instrument the new connection so we prune it on close
        connection.onClose().beforeFinally(() -> {
            int removeAttempt = 0;
            for (;;) {
                final ConnState currentConnState = this.connState;
                if (currentConnState.state == State.CLOSED) {
                    break;
                }
                assert currentConnState.connections.length > 0;
                ++removeAttempt;
                int i = 0;
                final Object[] connections = currentConnState.connections;
                // Search for the connection in the list.
                for (; i < connections.length; ++i) {
                    if (connections[i].equals(connection)) {
                        break;
                    }
                }
                if (i == connections.length) {
                    // Connection was already removed, nothing to do.
                    break;
                } else if (connections.length == 1) {
                    assert !DefaultHost.isUnhealthy(currentConnState) : "Cannot be UNHEALTHY with #connections > 0";
                    if (DefaultHost.isActive(currentConnState)) {
                        if (connStateUpdater.compareAndSet(this, currentConnState,
                                new ConnState(EMPTY_ARRAY, currentConnState.state))) {
                            break;
                        }
                    } else if (currentConnState.state == State.EXPIRED
                            // We're closing the last connection, close the Host.
                            // Closing the host will trigger the Host's onClose method, which will remove the host
                            // from used hosts list. If a race condition appears and a new connection was added
                            // in the meantime, that would mean the host is available again and the CAS operation
                            // will allow for determining that. It will prevent closing the Host and will only
                            // remove the connection (previously considered as the last one) from the array
                            // in the next iteration.
                            && connStateUpdater.compareAndSet(this, currentConnState, CLOSED_CONN_STATE)) {
                        this.closeAsync().subscribe();
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
            LOGGER.trace("{}: removed connection {} from {} after {} attempt(s).",
                    lbDescription, connection, this, removeAttempt);
        }).onErrorComplete(t -> {
            // Use onErrorComplete instead of whenOnError to avoid double logging of an error inside subscribe():
            // SimpleCompletableSubscriber.
            LOGGER.error("{}: unexpected error while processing connection.onClose() for {}.",
                    lbDescription, connection, t);
            return true;
        }).subscribe();
        return true;
    }

    // Used for testing only
    @Override@SuppressWarnings("unchecked")
    public Map.Entry<Addr, List<C>> asEntry() {
        return new AbstractMap.SimpleImmutableEntry<>(address,
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

    @Override
    public Completable onClosing() {
        return closeable.onClosing();
    }

    @SuppressWarnings("unchecked")
    private Completable doClose(final Function<? super C, Completable> closeFunction) {
        return Completable.defer(() -> {
            final ConnState oldState = closeConnState();
            cancelIfHealthCheck(oldState);
            final Object[] connections = oldState.connections;
            return (connections.length == 0 ? completed() :
                    from(connections).flatMapCompletableDelayError(conn -> closeFunction.apply((C) conn)))
                    .shareContextOnSubscribe();
        });
    }

    private void cancelIfHealthCheck(ConnState connState) {
        if (DefaultHost.isUnhealthy(connState)) {
            @SuppressWarnings("unchecked")
            HealthCheck<Addr, C> healthCheck = (HealthCheck<Addr, C>) connState.state;
            LOGGER.debug("{}: health check cancelled for {}.", lbDescription, healthCheck.host);
            healthCheck.cancel();
        }
    }

    @Override
    public int score() {
        // TODO: this is going to need some refinement but it's fine for now.
        return 1;
    }

    @Override
    public String toString() {
        final ConnState connState = this.connState;
        return "Host{" +
                "lbDescription=" + lbDescription +
                ", address=" + address +
                ", state=" + connState.state +
                ", #connections=" + connState.connections.length +
                '}';
    }

    private static final class ActiveState {
        private final int failedConnections;

        ActiveState() {
            this(0);
        }

        private ActiveState(int failedConnections) {
            this.failedConnections = failedConnections;
        }

        ActiveState forNextFailedConnection() {
            return new ActiveState(addWithOverflowProtection(this.failedConnections, 1));
        }

        @Override
        public String toString() {
            return "ACTIVE(failedConnections=" + failedConnections + ')';
        }
    }

    private static final class HealthCheck<ResolvedAddress, C extends LoadBalancedConnection>
            extends DelayedCancellable {
        private final ConnectionFactory<ResolvedAddress, ? extends C> connectionFactory;
        private final DefaultHost<ResolvedAddress, C> host;
        private final Throwable lastError;

        private HealthCheck(final ConnectionFactory<ResolvedAddress, ? extends C> connectionFactory,
                            final DefaultHost<ResolvedAddress, C> host, final Throwable lastError) {
            this.connectionFactory = connectionFactory;
            this.host = host;
            this.lastError = lastError;
        }

        public void schedule(final Throwable originalCause) {
            assert host.healthCheckConfig != null;
            delayedCancellable(
                    // Use retry strategy to utilize jitter.
                    retryWithConstantBackoffDeltaJitter(cause -> true,
                            host.healthCheckConfig.healthCheckInterval,
                            host.healthCheckConfig.jitter,
                            host.healthCheckConfig.executor)
                            .apply(0, originalCause)
                            // Remove any state from async context
                            .beforeOnSubscribe(__ -> AsyncContext.clear())
                            .concat(connectionFactory.newConnection(host.address, null, null)
                                    // There is no risk for StackOverflowError because result of each connection
                                    // attempt will be invoked on IoExecutor as a new task.
                                    .retryWhen(retryWithConstantBackoffDeltaJitter(
                                            cause -> {
                                                LOGGER.debug("{}: health check failed for {}.",
                                                        host.lbDescription, host, cause);
                                                return true;
                                            },
                                            host.healthCheckConfig.healthCheckInterval,
                                            host.healthCheckConfig.jitter,
                                            host.healthCheckConfig.executor)))
                            .flatMapCompletable(newCnx -> {
                                if (host.addConnection(newCnx, this)) {
                                    LOGGER.info("{}: health check passed for {}, marked this " +
                                                    "host as ACTIVE for the selection algorithm.",
                                            host.lbDescription, host);
                                    return completed();
                                } else {
                                    // This happens only if the host is closed, no need to mark as healthy.
                                    assert host.connState.state == State.CLOSED;
                                    LOGGER.debug("{}: health check passed for {}, but the " +
                                                    "host rejected a new connection {}. Closing it now.",
                                            host.lbDescription, host, newCnx);
                                    return newCnx.closeAsync();
                                }
                            })
                            // Use onErrorComplete instead of whenOnError to avoid double logging of an error inside
                            // subscribe(): SimpleCompletableSubscriber.
                            .onErrorComplete(t -> {
                                LOGGER.error("{}: health check terminated with " +
                                        "an unexpected error for {}. Marking this host as ACTIVE as a fallback " +
                                        "to allow connection attempts.", host.lbDescription, host, t);
                                host.markHealthy(this);
                                return true;
                            })
                            .subscribe());
        }

        @Override
        public String toString() {
            return "UNHEALTHY(" + lastError + ')';
        }
    }

    private static final class ConnState {
        final Object[] connections;
        final Object state;

        ConnState(final Object[] connections, final Object state) {
            this.connections = connections;
            this.state = state;
        }

        @Override
        public String toString() {
            return "ConnState{" +
                    "state=" + state +
                    ", #connections=" + connections.length +
                    '}';
        }
    }
}
