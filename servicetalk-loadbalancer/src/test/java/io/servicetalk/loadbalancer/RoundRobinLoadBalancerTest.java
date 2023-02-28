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
import io.servicetalk.client.api.ConnectionLimitReachedException;
import io.servicetalk.client.api.ConnectionRejectedException;
import io.servicetalk.client.api.DefaultServiceDiscovererEvent;
import io.servicetalk.client.api.LoadBalancedConnection;
import io.servicetalk.client.api.LoadBalancerReadyEvent;
import io.servicetalk.client.api.NoAvailableHostException;
import io.servicetalk.client.api.ServiceDiscovererEvent;
import io.servicetalk.concurrent.PublisherSource.Subscriber;
import io.servicetalk.concurrent.PublisherSource.Subscription;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.DelegatingExecutor;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.ExecutorExtension;
import io.servicetalk.concurrent.api.LegacyTestSingle;
import io.servicetalk.concurrent.api.ListenableAsyncCloseable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.SequentialPublisherSubscriberFunction;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.TestExecutor;
import io.servicetalk.concurrent.api.TestPublisher;
import io.servicetalk.concurrent.api.TestSubscription;
import io.servicetalk.concurrent.internal.DeliberateException;
import io.servicetalk.concurrent.test.internal.TestSingleSubscriber;
import io.servicetalk.context.api.ContextMap;
import io.servicetalk.transport.api.TransportObserver;

import org.hamcrest.Matcher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.AbstractMap;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.annotation.Nullable;

import static io.servicetalk.client.api.ServiceDiscovererEvent.Status.AVAILABLE;
import static io.servicetalk.client.api.ServiceDiscovererEvent.Status.EXPIRED;
import static io.servicetalk.client.api.ServiceDiscovererEvent.Status.UNAVAILABLE;
import static io.servicetalk.concurrent.api.AsyncCloseables.emptyAsyncCloseable;
import static io.servicetalk.concurrent.api.AsyncCloseables.toAsyncCloseable;
import static io.servicetalk.concurrent.api.AsyncCloseables.toListenableAsyncCloseable;
import static io.servicetalk.concurrent.api.BlockingTestUtils.awaitIndefinitely;
import static io.servicetalk.concurrent.api.Completable.completed;
import static io.servicetalk.concurrent.api.Completable.never;
import static io.servicetalk.concurrent.api.RetryStrategies.retryWithConstantBackoffFullJitter;
import static io.servicetalk.concurrent.api.Single.defer;
import static io.servicetalk.concurrent.api.Single.failed;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.concurrent.internal.TestTimeoutConstants.DEFAULT_TIMEOUT_SECONDS;
import static io.servicetalk.loadbalancer.RoundRobinLoadBalancerFactory.DEFAULT_HEALTH_CHECK_FAILED_CONNECTIONS_THRESHOLD;
import static io.servicetalk.loadbalancer.RoundRobinLoadBalancerFactory.DEFAULT_HEALTH_CHECK_RESUBSCRIBE_INTERVAL;
import static io.servicetalk.loadbalancer.RoundRobinLoadBalancerTest.UnhealthyHostConnectionFactory.UNHEALTHY_HOST_EXCEPTION;
import static java.lang.Long.MAX_VALUE;
import static java.time.Duration.ZERO;
import static java.time.Duration.ofMillis;
import static java.time.Duration.ofNanos;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.toSet;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.both;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.emptyIterable;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

abstract class RoundRobinLoadBalancerTest {

    static final String[] EMPTY_ARRAY = new String[] {};

    @RegisterExtension
    final ExecutorExtension<TestExecutor> executor = ExecutorExtension.withTestExecutor();

    private final TestSingleSubscriber<TestLoadBalancedConnection> selectConnectionListener =
            new TestSingleSubscriber<>();
    private final List<TestLoadBalancedConnection> connectionsCreated = new CopyOnWriteArrayList<>();
    private final Queue<Runnable> connectionRealizers = new ConcurrentLinkedQueue<>();
    private final SequentialPublisherSubscriberFunction<Collection<ServiceDiscovererEvent<String>>>
            sequentialPublisherSubscriberFunction = new SequentialPublisherSubscriberFunction<>();
    final TestPublisher<Collection<ServiceDiscovererEvent<String>>> serviceDiscoveryPublisher =
            new TestPublisher.Builder<Collection<ServiceDiscovererEvent<String>>>()
                    .sequentialSubscribers(sequentialPublisherSubscriberFunction)
                    .build();
    private DelegatingConnectionFactory connectionFactory =
            new DelegatingConnectionFactory(this::newRealizedConnectionSingle);

    RoundRobinLoadBalancer<String, TestLoadBalancedConnection> lb;

    private TestExecutor testExecutor;

    static <T> Predicate<T> any() {
      return __ -> true;
    }

    Predicate<TestLoadBalancedConnection> alwaysNewConnectionFilter() {
        return cnx -> lb.usedAddresses().stream().noneMatch(addr -> addr.getValue().stream().anyMatch(cnx::equals));
    }

    RoundRobinLoadBalancer<String, TestLoadBalancedConnection> defaultLb() {
        return newTestLoadBalancer();
    }

    RoundRobinLoadBalancer<String, TestLoadBalancedConnection> defaultLb(
        DelegatingConnectionFactory connectionFactory) {
        return newTestLoadBalancer(serviceDiscoveryPublisher, connectionFactory);
    }

    protected abstract boolean eagerConnectionShutdown();

    @BeforeEach
    void initialize() {
        testExecutor = executor.executor();
        lb = defaultLb();
        connectionsCreated.clear();
        connectionRealizers.clear();
    }

    @AfterEach
    void closeLoadBalancer() throws Exception {
        awaitIndefinitely(lb.closeAsync());
        awaitIndefinitely(lb.onClose());

        TestSubscription subscription = new TestSubscription();
        serviceDiscoveryPublisher.onSubscribe(subscription);
        assertTrue(subscription.isCancelled());

        connectionsCreated.forEach(cnx -> {
            try {
                awaitIndefinitely(cnx.onClose());
            } catch (final Exception e) {
                throw new RuntimeException("Connection: " + cnx + " didn't close properly", e);
            }
        });
    }

    @Test
    void streamEventJustClose() throws InterruptedException {
        CountDownLatch readyLatch = new CountDownLatch(1);
        CountDownLatch completeLatch = new CountDownLatch(1);
        lb.eventStream().afterOnComplete(completeLatch::countDown).firstOrElse(() -> {
            throw new NoSuchElementException();
        }).subscribe(next -> readyLatch.countDown());
        lb.closeAsync().subscribe();

        assertThat(readyLatch.await(100, MILLISECONDS), is(false));
        completeLatch.await();
    }

    @Test
    void streamEventReadyAndCompleteOnClose() throws InterruptedException {
        CountDownLatch readyLatch = new CountDownLatch(1);
        CountDownLatch completeLatch = new CountDownLatch(1);
        AtomicReference<Throwable> causeRef = new AtomicReference<>();
        toSource(lb.eventStream()).subscribe(new Subscriber<Object>() {
            @Override
            public void onSubscribe(final Subscription s) {
                s.request(1);
            }

            @Override
            public void onNext(final Object event) {
                if (event instanceof LoadBalancerReadyEvent && ((LoadBalancerReadyEvent) event).isReady()) {
                    readyLatch.countDown();
                }
            }

            @Override
            public void onError(final Throwable t) {
                causeRef.set(t);
                completeLatch.countDown();
            }

            @Override
            public void onComplete() {
                completeLatch.countDown();
            }
        });
        assertThat(readyLatch.await(100, MILLISECONDS), is(false));
        sendServiceDiscoveryEvents(upEvent("address-1"));
        readyLatch.await();

        lb.closeAsync().subscribe();
        completeLatch.await();
        assertNull(causeRef.get());
    }

    @Test
    void unknownAddressIsRemoved() {
        assertAddresses(lb.usedAddresses(), EMPTY_ARRAY);
        sendServiceDiscoveryEvents(downEvent("address-1"));
        assertAddresses(lb.usedAddresses(), EMPTY_ARRAY);
    }

    @Test
    void noServiceDiscoveryEvent() {
        toSource(lb.selectConnection(any(), null)).subscribe(selectConnectionListener);
        assertThat(selectConnectionListener.awaitOnError(), instanceOf(NoAvailableHostException.class));

        assertThat(connectionsCreated, is(empty()));
    }

    @Test
    void selectStampedeUnsaturableConnection() throws Exception {
        serviceDiscoveryPublisher.onComplete();

        testSelectStampede(any());
    }

    @Test
    void selectStampedeSaturableConnection() throws Exception {
        serviceDiscoveryPublisher.onComplete();

        testSelectStampede(newSaturableConnectionFilter());
    }

    private void testSelectStampede(final Predicate<TestLoadBalancedConnection> selectionFilter) throws Exception {
        connectionFactory = new DelegatingConnectionFactory(this::newUnrealizedConnectionSingle);
        lb = defaultLb(connectionFactory);

        final ExecutorService clientExecutor = Executors.newFixedThreadPool(20);
        final List<TestLoadBalancedConnection> selectedConnections = new CopyOnWriteArrayList<>();
        final List<Throwable> exceptions = new CopyOnWriteArrayList<>();
        final CyclicBarrier stampedeBarrier = new CyclicBarrier(21);

        // Client simulator threads
        for (int i = 0; i < 20; i++) {
            clientExecutor.submit(() -> {
                try {
                    stampedeBarrier.await(DEFAULT_TIMEOUT_SECONDS, SECONDS);

                    Publisher<TestLoadBalancedConnection> selections = Publisher.empty();
                    for (int j = 0; j < 5; j++) {
                        selections = selections.concat(lb.selectConnection(selectionFilter, null));
                    }
                    selectedConnections.addAll(selections.toFuture().get());
                } catch (final Throwable t) {
                    exceptions.add(t);
                }
            });
        }

        // Async connection factory simulator
        final ExecutorService connectionFactoryExecutor = Executors.newFixedThreadPool(5);
        final AtomicBoolean runConnectionRealizers = new AtomicBoolean(true);
        for (int i = 0; i < 5; i++) {
            connectionFactoryExecutor.submit(() -> {
                while (runConnectionRealizers.get()) {
                    final Runnable realizer = connectionRealizers.poll();
                    if (realizer != null) {
                        realizer.run();
                    } else {
                        Thread.yield();
                    }
                }
            });
        }

        sendServiceDiscoveryEvents(upEvent("address-1"));
        // Release the client simulator threads
        stampedeBarrier.await(DEFAULT_TIMEOUT_SECONDS, SECONDS);

        clientExecutor.shutdown();
        clientExecutor.awaitTermination(10, SECONDS);
        runConnectionRealizers.set(false);
        connectionFactoryExecutor.shutdown();
        connectionFactoryExecutor.awaitTermination(10, SECONDS);

        assertThat(exceptions, is(empty()));
        assertThat(selectedConnections, hasSize(100));
        assertThat(selectedConnections.stream().map(TestLoadBalancedConnection::address).collect(toSet()),
                contains("address-1"));
        assertThat(connectionsCreated, hasSize(both(greaterThan(0)).and(lessThanOrEqualTo(100))));
    }

    @Test
    void roundRobining() throws Exception {
        sendServiceDiscoveryEvents(upEvent("address-1"));
        sendServiceDiscoveryEvents(upEvent("address-2"));
        final List<String> connections = awaitIndefinitely((lb.selectConnection(any(), null)
                .concat(lb.selectConnection(any(), null))
                .concat(lb.selectConnection(any(), null))
                .concat(lb.selectConnection(any(), null))
                .concat(lb.selectConnection(any(), null))
                .map(TestLoadBalancedConnection::address)));

        assertThat(connections, contains("address-1", "address-2", "address-1", "address-2", "address-1"));

        assertConnectionCount(lb.usedAddresses(),
                connectionsCount("address-1", 1),
                connectionsCount("address-2", 1));

        assertThat(connectionsCreated, hasSize(2));
    }

    @Test
    void closedConnectionPruning() throws Exception {
        sendServiceDiscoveryEvents(upEvent("address-1"));

        final TestLoadBalancedConnection connection = awaitIndefinitely(lb.selectConnection(any(), null));
        assert connection != null;
        List<Map.Entry<String, List<TestLoadBalancedConnection>>> activeAddresses = lb.usedAddresses();

        assertThat(activeAddresses.size(), is(1));
        assertConnectionCount(activeAddresses, connectionsCount("address-1", 1));
        assertThat(activeAddresses.get(0).getValue().get(0), is(connection));
        awaitIndefinitely(connection.closeAsync());

        assertConnectionCount(lb.usedAddresses(), connectionsCount("address-1", 0));

        assertThat(connectionsCreated, hasSize(1));
    }

    @Test
    void connectionFactoryErrorPropagation() {
        serviceDiscoveryPublisher.onComplete();

        connectionFactory = new DelegatingConnectionFactory(__ -> failed(DELIBERATE_EXCEPTION));
        lb = defaultLb(connectionFactory);
        sendServiceDiscoveryEvents(upEvent("address-1"));

        ExecutionException ex = assertThrows(ExecutionException.class,
                                             () -> awaitIndefinitely(lb.selectConnection(any(), null)));
        assertThat(ex.getCause(), is(instanceOf(DeliberateException.class)));
    }

    @Test
    void earlyFailsAfterClose() throws Exception {
        sendServiceDiscoveryEvents(upEvent("address-1"));
        awaitIndefinitely(lb.closeAsync());

        ExecutionException ex = assertThrows(ExecutionException.class,
                                             () -> awaitIndefinitely(lb.selectConnection(any(), null)));
        assertThat(ex.getCause(), is(instanceOf(IllegalStateException.class)));

        assertThat(connectionsCreated, is(empty()));
    }

    @Test
    void closeClosesConnectionFactory() throws Exception {
        awaitIndefinitely(lb.closeAsync());
        assertTrue(connectionFactory.isClosed(), "ConnectionFactory not closed.");
    }

    @ParameterizedTest(name = "{displayName} [{index}]: closeFromLb={0}")
    @ValueSource(booleans = {true, false})
    void closeGracefulThenClose(boolean closeFromLb)
            throws ExecutionException, InterruptedException {
        serviceDiscoveryPublisher.onComplete();
        lb = defaultLb(new DelegatingConnectionFactory(addr -> newRealizedConnectionSingle(addr,
                toListenableAsyncCloseable(toAsyncCloseable(graceful -> graceful ? never() : completed())))));
        sendServiceDiscoveryEvents(upEvent("address-1"));
        final TestLoadBalancedConnection connection = awaitIndefinitely(lb.selectConnection(any(), null));
        assertThat(connection, notNullValue());
            assertThat(assertThrows(ExecutionException.class,
                    () -> (closeFromLb ? lb.closeAsyncGracefully() : connection.closeAsyncGracefully())
                            .timeout(ofMillis(1)).toFuture().get())
                    .getCause(), instanceOf(TimeoutException.class));

        if (closeFromLb) {
            lb.onClosing().toFuture().get();
        }
        connection.onClosing().toFuture().get();

        lb.closeAsync().toFuture().get();
        if (!closeFromLb) {
            lb.onClosing().toFuture().get();
        }

        lb.onClose().toFuture().get(); // Verify onClose reflects the close status.
        connection.onClose().toFuture().get(); // Make sure closeable is closed completely.
    }

    @Test
    void newConnectionIsNotClosedWhenSelectorRejects() throws Exception {
        sendServiceDiscoveryEvents(upEvent("address-1"));
        try {
            awaitIndefinitely(lb.selectConnection(__ -> false, null));
            fail();
        } catch (ExecutionException e) {
            assertThat(e.getCause(), is(instanceOf(ConnectionRejectedException.class)));
        }
        assertThat(connectionsCreated, hasSize(1));
        TestLoadBalancedConnection connection = connectionsCreated.get(0);
        assertThat(connection, is(notNullValue()));
        assertThat(connection.address(), is(equalTo("address-1")));
        // Verify that no close is initiated. Selection may fail because max concurrency changed or connection is
        // being cached and re-used.
        assertThrows(TimeoutException.class, () -> connection.onClose().toFuture().get(10, MILLISECONDS));
        connection.closeAsync().toFuture().get();
    }

    @Test
    void unhealthyHostTakenOutOfPoolForSelection() throws Exception {
        serviceDiscoveryPublisher.onComplete();

        final Single<TestLoadBalancedConnection> properConnection = newRealizedConnectionSingle("address-1");
        final int timeAdvancementsTillHealthy = 3;
        final UnhealthyHostConnectionFactory unhealthyHostConnectionFactory = new UnhealthyHostConnectionFactory(
                "address-1", timeAdvancementsTillHealthy, properConnection);

        final DelegatingConnectionFactory connectionFactory = unhealthyHostConnectionFactory.createFactory();
        lb = defaultLb(connectionFactory);

        sendServiceDiscoveryEvents(upEvent("address-1"));
        sendServiceDiscoveryEvents(upEvent("address-2"));

        for (int i = 0; i < DEFAULT_HEALTH_CHECK_FAILED_CONNECTIONS_THRESHOLD * 2; ++i) {
            try {
                final TestLoadBalancedConnection selectedConnection = lb.selectConnection(any(), null).toFuture().get();
                assertThat(selectedConnection, equalTo(properConnection.toFuture().get()));
            } catch (Exception e) {
                assertThat(e.getCause(), is(UNHEALTHY_HOST_EXCEPTION));
            }
        }

        for (int i = 0; i < 10; ++i) {
            final TestLoadBalancedConnection selectedConnection = lb.selectConnection(any(), null).toFuture().get();
            assertThat(selectedConnection, equalTo(properConnection.toFuture().get()));
        }

        assertThat(testExecutor.scheduledTasksPending(), equalTo(1));

        for (int i = 0; i < timeAdvancementsTillHealthy; ++i) {
            unhealthyHostConnectionFactory.advanceTime(testExecutor);
        }

        assertThat(testExecutor.scheduledTasksPending(), equalTo(0));

        for (int i = 0; i < 10; ++i) {
            final TestLoadBalancedConnection selectedConnection = lb.selectConnection(any(), null).toFuture().get();
            assertThat(selectedConnection, equalTo(properConnection.toFuture().get()));
        }
    }

    @Test
    void disabledHealthCheckDoesntRun() throws Exception {
        serviceDiscoveryPublisher.onComplete();

        final Single<TestLoadBalancedConnection> properConnection = newRealizedConnectionSingle("address-1");
        final int timeAdvancementsTillHealthy = 3;
        final UnhealthyHostConnectionFactory unhealthyHostConnectionFactory = new UnhealthyHostConnectionFactory(
                "address-1", timeAdvancementsTillHealthy, properConnection);
        final DelegatingConnectionFactory connectionFactory = unhealthyHostConnectionFactory.createFactory();

        lb = (RoundRobinLoadBalancer<String, TestLoadBalancedConnection>)
                RoundRobinLoadBalancers.<String, TestLoadBalancedConnection>builder(getClass().getSimpleName())
                        .healthCheckFailedConnectionsThreshold(-1)
                        .build()
                        .newLoadBalancer(serviceDiscoveryPublisher, connectionFactory, "test-service");

        sendServiceDiscoveryEvents(upEvent("address-1"));

        for (int i = 0; i < DEFAULT_HEALTH_CHECK_FAILED_CONNECTIONS_THRESHOLD; ++i) {
            assertSelectThrows(is(UNHEALTHY_HOST_EXCEPTION));
        }

        assertThat(testExecutor.scheduledTasksPending(), equalTo(0));

        for (int i = 0; i < timeAdvancementsTillHealthy - 1; ++i) {
            unhealthyHostConnectionFactory.advanceTime(testExecutor);
            assertSelectThrows(is(UNHEALTHY_HOST_EXCEPTION));
        }

        unhealthyHostConnectionFactory.advanceTime(testExecutor);
        assertThat(testExecutor.scheduledTasksPending(), equalTo(0));

        final TestLoadBalancedConnection selectedConnection = lb.selectConnection(any(), null).toFuture().get();
        assertThat(selectedConnection, equalTo(properConnection.toFuture().get()));
    }

    @Test
    void connectionLimitReachedExceptionDoesNotMarkHostAsUnhealthy() throws Exception {
        serviceDiscoveryPublisher.onComplete();

        final Single<TestLoadBalancedConnection> properConnection = newRealizedConnectionSingle("address-1");
        final int timeAdvancementsTillHealthy = 3;
        final ConnectionLimitReachedException exception = new ConnectionLimitReachedException("deliberate");
        final UnhealthyHostConnectionFactory unhealthyHostConnectionFactory = new UnhealthyHostConnectionFactory(
                "address-1", timeAdvancementsTillHealthy, properConnection, exception);

        final DelegatingConnectionFactory connectionFactory = unhealthyHostConnectionFactory.createFactory();
        lb = defaultLb(connectionFactory);

        sendServiceDiscoveryEvents(upEvent("address-1"));

        for (int i = 0; i < DEFAULT_HEALTH_CHECK_FAILED_CONNECTIONS_THRESHOLD; ++i) {
            assertSelectThrows(is(exception));
        }
        assertThat(testExecutor.scheduledTasksPending(), equalTo(0));

        for (int i = 0; i < timeAdvancementsTillHealthy - 1; ++i) {
            unhealthyHostConnectionFactory.advanceTime(testExecutor);
            assertSelectThrows(is(exception));
        }

        unhealthyHostConnectionFactory.advanceTime(testExecutor);
        assertThat(testExecutor.scheduledTasksPending(), equalTo(0));

        final TestLoadBalancedConnection selectedConnection = lb.selectConnection(any(), null).toFuture().get();
        assertThat(selectedConnection, equalTo(properConnection.toFuture().get()));
    }

    @Test
    void hostUnhealthyIsHealthChecked() throws Exception {
        serviceDiscoveryPublisher.onComplete();
        final Single<TestLoadBalancedConnection> properConnection = newRealizedConnectionSingle("address-1");
        final int timeAdvancementsTillHealthy = 3;
        final UnhealthyHostConnectionFactory unhealthyHostConnectionFactory = new UnhealthyHostConnectionFactory(
                "address-1", timeAdvancementsTillHealthy, properConnection);
        final DelegatingConnectionFactory connectionFactory = unhealthyHostConnectionFactory.createFactory();

        lb = defaultLb(connectionFactory);

        sendServiceDiscoveryEvents(upEvent("address-1"));

        for (int i = 0; i < DEFAULT_HEALTH_CHECK_FAILED_CONNECTIONS_THRESHOLD; ++i) {
            assertSelectThrows(is(UNHEALTHY_HOST_EXCEPTION));
        }

        for (int i = 0; i < timeAdvancementsTillHealthy; ++i) {
            unhealthyHostConnectionFactory.advanceTime(testExecutor);
        }

        final TestLoadBalancedConnection selectedConnection = lb.selectConnection(any(), null).toFuture().get();
        assertThat(selectedConnection, equalTo(properConnection.toFuture().get()));

        // 5 failed attempts trigger health check, 2 health check attempts fail, 3rd health check attempt
        // uses the proper connection, final selection reuses that connection. 8 total creation attempts.
        int expectedConnectionAttempts = 8;
        assertThat(unhealthyHostConnectionFactory.requests.get(), equalTo(expectedConnectionAttempts));
    }

    @Test
    void healthCheckRecoversFromUnexpectedError() throws Exception {
        serviceDiscoveryPublisher.onComplete();
        final Single<TestLoadBalancedConnection> properConnection = newRealizedConnectionSingle("address-1");
        final UnhealthyHostConnectionFactory unhealthyHostConnectionFactory = new UnhealthyHostConnectionFactory(
                "address-1", 4, properConnection);
        final DelegatingConnectionFactory connectionFactory = unhealthyHostConnectionFactory.createFactory();

        final AtomicInteger scheduleCnt = new AtomicInteger();
        lb = (RoundRobinLoadBalancer<String, TestLoadBalancedConnection>)
                RoundRobinLoadBalancers.<String, TestLoadBalancedConnection>builder(getClass().getSimpleName())
                        .healthCheckInterval(ofMillis(50), ofMillis(10))
                        .healthCheckFailedConnectionsThreshold(1)
                        .backgroundExecutor(new DelegatingExecutor(testExecutor) {

                            @Override
                            public Completable timer(final long delay, final TimeUnit unit) {
                                if (scheduleCnt.incrementAndGet() == 2) {
                                    throw DELIBERATE_EXCEPTION;
                                }
                                return delegate().timer(delay, unit);
                            }
                        })
                        .build()
                        .newLoadBalancer(serviceDiscoveryPublisher, connectionFactory, "test-service");

        sendServiceDiscoveryEvents(upEvent("address-1"));

        // Trigger first health check:
        assertSelectThrows(is(UNHEALTHY_HOST_EXCEPTION));
        // Execute two health checks: first will fail due to connectionFactory,
        // second - due to an unexpected error from executor:
        for (int i = 0; i < 2; ++i) {
            unhealthyHostConnectionFactory.advanceTime(testExecutor);
        }

        // Trigger yet another health check:
        assertSelectThrows(is(UNHEALTHY_HOST_EXCEPTION));
        // Execute two health checks: first will fail due to connectionFactory, second succeeds:
        for (int i = 0; i < 2; ++i) {
            unhealthyHostConnectionFactory.advanceTime(testExecutor);
        }

        // Make sure we can select a connection:
        final TestLoadBalancedConnection selectedConnection = lb.selectConnection(any(), null).toFuture().get();
        assertThat(selectedConnection, equalTo(properConnection.toFuture().get()));

        assertThat(unhealthyHostConnectionFactory.requests.get(), equalTo(5));
    }

    // Concurrency test, run multiple times (at least 1000).
    @Test
    void hostUnhealthyDoesntRaceToRunHealthCheck() throws Exception {
        serviceDiscoveryPublisher.onComplete();

        final Single<TestLoadBalancedConnection> properConnection = newRealizedConnectionSingle("address-1");
        final int timeAdvancementsTillHealthy = 3;
        final UnhealthyHostConnectionFactory unhealthyHostConnectionFactory = new UnhealthyHostConnectionFactory(
                "address-1", timeAdvancementsTillHealthy, properConnection);
        final DelegatingConnectionFactory connectionFactory = unhealthyHostConnectionFactory.createFactory();

        lb = defaultLb(connectionFactory);
        sendServiceDiscoveryEvents(upEvent("address-1"));

        // Imitate concurrency by running multiple threads attempting to establish connections.
        ExecutorService executor = Executors.newFixedThreadPool(3);
        try {
            final Runnable runnable = () -> assertSelectThrows(is(UNHEALTHY_HOST_EXCEPTION));
            for (int i = 0; i < 1000; i++) {
                executor.submit(runnable);
            }

            // From test main thread, wait until the host becomes UNHEALTHY, which is apparent from
            // NoHostAvailableException being thrown from selection AFTER a health check was scheduled by any thread.
            final Executor executorForRetries = io.servicetalk.concurrent.api.Executors.newFixedSizeExecutor(1);
            try {
                awaitIndefinitely(lb.selectConnection(any(), null).retryWhen(retryWithConstantBackoffFullJitter((t) ->
                                // DeliberateException comes from connection opening, check for that first
                                // Next, NoAvailableHostException is thrown when the host is unhealthy,
                                // but we still wait until the health check is scheduled and only then stop retrying.
                                t instanceof DeliberateException || testExecutor.scheduledTasksPending() == 0,
                        // try to prevent stack overflow
                        ofMillis(30), executorForRetries)));
            } catch (Exception e) {
                assertThat(e.getCause(), instanceOf(NoAvailableHostException.class));
            } finally {
                executorForRetries.closeAsync().toFuture().get();
            }

            // At this point, either the above selection caused the host to be marked as UNHEALTHY,
            // or any background thread. We also know that a health check is pending to be executed.
            // Now we can validate if there is just one health check happening and confirm that by asserting the host
            // is not selected. If our assumption doesn't hold, it means more than one health check was scheduled.
            for (int i = 0; i < timeAdvancementsTillHealthy - 1; ++i) {
                unhealthyHostConnectionFactory.advanceTime(testExecutor);

                // Assert still unhealthy
                assertSelectThrows(instanceOf(NoAvailableHostException.class));
            }
        } finally {
            // Shutdown the concurrent validation of unhealthiness.
            executor.shutdownNow();
            executor.awaitTermination(10, SECONDS);
        }

        unhealthyHostConnectionFactory.advanceTime(testExecutor);

        final TestLoadBalancedConnection selectedConnection = lb.selectConnection(any(), null).toFuture().get();
        assertThat(selectedConnection, equalTo(properConnection.toFuture().get()));
    }

    @ParameterizedTest(name = "{displayName} [{index}]: sdReturnsDelta={0}")
    @ValueSource(booleans = {false, true})
    void resubscribeToEventsWhenAllHostsAreUnhealthy(boolean sdReturnsDelta) throws Exception {
        serviceDiscoveryPublisher.onComplete();
        assertThat(sequentialPublisherSubscriberFunction.isSubscribed(), is(false));
        assertThat(sequentialPublisherSubscriberFunction.numberOfSubscribersSeen(), is(1));

        DelegatingConnectionFactory alwaysFail12ConnectionFactory = new DelegatingConnectionFactory(address -> {
            switch (address) {
                case "address-1":
                case "address-2":
                    return Single.failed(UNHEALTHY_HOST_EXCEPTION);
                default:
                    return Single.succeeded(newConnection(address));
            }
        });
        lb = defaultLb(alwaysFail12ConnectionFactory);

        assertThat(sequentialPublisherSubscriberFunction.isSubscribed(), is(true));
        assertThat(sequentialPublisherSubscriberFunction.numberOfSubscribersSeen(), is(2));

        assertAddresses(lb.usedAddresses(), EMPTY_ARRAY);
        sendServiceDiscoveryEvents(upEvent("address-1"));
        sendServiceDiscoveryEvents(upEvent("address-2"));
        assertAddresses(lb.usedAddresses(), "address-1", "address-2");

        // Force all usedAddresses into UNHEALTHY state
        for (int i = 0; i < DEFAULT_HEALTH_CHECK_FAILED_CONNECTIONS_THRESHOLD * lb.usedAddresses().size(); ++i) {
            assertSelectThrows(is(UNHEALTHY_HOST_EXCEPTION));
        }
        assertThat(sequentialPublisherSubscriberFunction.numberOfSubscribersSeen(), is(2));
        // Assert the next select attempt after resubscribe internal triggers re-subscribe
        testExecutor.advanceTimeBy(DEFAULT_HEALTH_CHECK_RESUBSCRIBE_INTERVAL.toMillis() * 2, MILLISECONDS);
        assertThat(sequentialPublisherSubscriberFunction.numberOfSubscribersSeen(), is(2));
        assertSelectThrows(instanceOf(NoAvailableHostException.class));
        assertThat(sequentialPublisherSubscriberFunction.numberOfSubscribersSeen(), is(3));

        // Verify state after re-subscribe
        assertAddresses(lb.usedAddresses(), "address-1", "address-2");
        if (sdReturnsDelta) {
            sendServiceDiscoveryEvents(upEvent("address-3"), upEvent("address-4"), downEvent("address-1"));
            assertAddresses(lb.usedAddresses(), "address-2", "address-3", "address-4");
            sendServiceDiscoveryEvents(downEvent("address-2"));
        } else {
            sendServiceDiscoveryEvents(upEvent("address-3"), upEvent("address-4"));
        }
        assertAddresses(lb.usedAddresses(), "address-3", "address-4");

        // Verify the LB is recovered
        Map<String, Matcher<? super String>> expected = new HashMap<>();
        expected.put("address-3", is("address-3"));
        expected.put("address-4", is("address-4"));
        String selected1 = lb.selectConnection(any(), null).toFuture().get().address();
        assertThat(selected1, is(anyOf(expected.values())));
        expected.remove(selected1);
        assertThat(lb.selectConnection(any(), null).toFuture().get().address(), is(anyOf(expected.values())));
        assertConnectionCount(lb.usedAddresses(), connectionsCount("address-3", 1), connectionsCount("address-4", 1));
    }

    @Test
    void resubscribeToEventsNotTriggeredWhenDisabled() throws Exception {
        serviceDiscoveryPublisher.onComplete();
        assertThat(sequentialPublisherSubscriberFunction.isSubscribed(), is(false));
        assertThat(sequentialPublisherSubscriberFunction.numberOfSubscribersSeen(), is(1));

        DelegatingConnectionFactory alwaysFailConnectionFactory =
                new DelegatingConnectionFactory(address -> Single.failed(UNHEALTHY_HOST_EXCEPTION));
        lb = (RoundRobinLoadBalancer<String, TestLoadBalancedConnection>)
                RoundRobinLoadBalancers.<String, TestLoadBalancedConnection>builder(getClass().getSimpleName())
                        .healthCheckInterval(ofMillis(50), ofMillis(10))
                        // Set resubscribe interval to very large number
                        .healthCheckResubscribeInterval(ofNanos(MAX_VALUE), ZERO)
                        .backgroundExecutor(testExecutor)
                        .build()
                        .newLoadBalancer(serviceDiscoveryPublisher, alwaysFailConnectionFactory, "test-service");

        assertThat(sequentialPublisherSubscriberFunction.isSubscribed(), is(true));
        assertThat(sequentialPublisherSubscriberFunction.numberOfSubscribersSeen(), is(2));

        assertAddresses(lb.usedAddresses(), EMPTY_ARRAY);
        sendServiceDiscoveryEvents(upEvent("address-1"));
        sendServiceDiscoveryEvents(upEvent("address-2"));
        assertAddresses(lb.usedAddresses(), "address-1", "address-2");

        // Force all usedAddresses into UNHEALTHY state
        for (int i = 0; i < DEFAULT_HEALTH_CHECK_FAILED_CONNECTIONS_THRESHOLD * lb.usedAddresses().size(); ++i) {
            assertSelectThrows(is(UNHEALTHY_HOST_EXCEPTION));
        }
        assertThat(sequentialPublisherSubscriberFunction.numberOfSubscribersSeen(), is(2));
        testExecutor.advanceTimeBy(DEFAULT_HEALTH_CHECK_RESUBSCRIBE_INTERVAL.toMillis() * 2, MILLISECONDS);
        assertThat(sequentialPublisherSubscriberFunction.numberOfSubscribersSeen(), is(2));
        assertSelectThrows(instanceOf(NoAvailableHostException.class));
        assertThat(sequentialPublisherSubscriberFunction.numberOfSubscribersSeen(), is(2));
    }

    @Test
    void handleAllDiscoveryEvents() throws Exception {
        serviceDiscoveryPublisher.onComplete();

        lb = (RoundRobinLoadBalancer<String, TestLoadBalancedConnection>)
                RoundRobinLoadBalancers.<String, TestLoadBalancedConnection>builder(getClass().getSimpleName())
                        .healthCheckInterval(ofMillis(50), ofMillis(10))
                        .backgroundExecutor(testExecutor)
                        .build()
                        .newLoadBalancer(serviceDiscoveryPublisher, connectionFactory, "test-service");

        assertAddresses(lb.usedAddresses(), EMPTY_ARRAY);

        sendServiceDiscoveryEvents(upEvent("address-1"));
        assertAddresses(lb.usedAddresses(), "address-1");

        sendServiceDiscoveryEvents(downEvent("address-1", UNAVAILABLE));
        assertAddresses(lb.usedAddresses(), EMPTY_ARRAY);

        sendServiceDiscoveryEvents(upEvent("address-2"));
        assertAddresses(lb.usedAddresses(), "address-2");

        sendServiceDiscoveryEvents(downEvent("address-3", UNAVAILABLE));
        assertAddresses(lb.usedAddresses(), "address-2");

        sendServiceDiscoveryEvents(upEvent("address-1"));
        assertAddresses(lb.usedAddresses(), "address-2", "address-1");

        // Make sure both hosts have connections
        lb.selectConnection(any(), null).toFuture().get();
        lb.selectConnection(any(), null).toFuture().get();

        sendServiceDiscoveryEvents(downEvent("address-1", EXPIRED));
        assertAddresses(lb.usedAddresses(), "address-2", "address-1");

        sendServiceDiscoveryEvents(downEvent("address-2", UNAVAILABLE));
        assertAddresses(lb.usedAddresses(), "address-1");

        sendServiceDiscoveryEvents(downEvent("address-1", AVAILABLE));
        assertAddresses(lb.usedAddresses(), "address-1");

        // Let's make sure that an SD failure doesn't compromise LB's internal state
        serviceDiscoveryPublisher.onError(DELIBERATE_EXCEPTION);
        assertAddresses(lb.usedAddresses(), "address-1");
    }

    /**
     * This test verifies that the {@link io.servicetalk.client.api.LoadBalancer#newConnection(ContextMap)} API is
     * supported.
     */
    @Test
    void registersNewConnections() throws Exception {
        serviceDiscoveryPublisher.onComplete();
        lb = defaultLb();

        assertAddresses(lb.usedAddresses(), EMPTY_ARRAY);

        sendServiceDiscoveryEvents(upEvent("address-1"));
        assertAddresses(lb.usedAddresses(), "address-1");

        assertTrue(connectionsCreated.isEmpty());
        TestLoadBalancedConnection conn1 = lb.newConnection(null).toFuture().get();
        assertEquals(1, connectionsCreated.size());
        TestLoadBalancedConnection conn2 = lb.newConnection(null).toFuture().get();
        assertEquals(2, connectionsCreated.size());

        verify(conn1, times(1)).tryReserve();
        verify(conn2, times(1)).tryReserve();
    }

    void sendServiceDiscoveryEvents(final ServiceDiscovererEvent... events) {
        sendServiceDiscoveryEvents(serviceDiscoveryPublisher, events);
    }

    @SuppressWarnings("unchecked")
    private void sendServiceDiscoveryEvents(
            TestPublisher<Collection<ServiceDiscovererEvent<String>>> serviceDiscoveryPublisher,
            final ServiceDiscovererEvent... events) {
        serviceDiscoveryPublisher.onNext(Arrays.asList(events));
    }

    ServiceDiscovererEvent upEvent(final String address) {
        return new DefaultServiceDiscovererEvent<>(address, AVAILABLE);
    }

    ServiceDiscovererEvent downEvent(final String address) {
        return new DefaultServiceDiscovererEvent<>(address, eagerConnectionShutdown() ? UNAVAILABLE : EXPIRED);
    }

    ServiceDiscovererEvent downEvent(final String address, ServiceDiscovererEvent.Status status) {
        return new DefaultServiceDiscovererEvent<>(address, status);
    }

    RoundRobinLoadBalancer<String, TestLoadBalancedConnection> newTestLoadBalancer() {
        return newTestLoadBalancer(serviceDiscoveryPublisher, connectionFactory);
    }

    RoundRobinLoadBalancer<String, TestLoadBalancedConnection> newTestLoadBalancer(
        final TestPublisher<Collection<ServiceDiscovererEvent<String>>> serviceDiscoveryPublisher,
        final DelegatingConnectionFactory connectionFactory) {
        return (RoundRobinLoadBalancer<String, TestLoadBalancedConnection>)
                RoundRobinLoadBalancers.<String, TestLoadBalancedConnection>builder(getClass().getSimpleName())
                        .healthCheckInterval(ofMillis(50), ofMillis(10))
                        .backgroundExecutor(testExecutor)
                        .build()
                        .newLoadBalancer(serviceDiscoveryPublisher, connectionFactory, "test-service");
    }

    @SafeVarargs
    static <T> void assertConnectionCount(
        Iterable<T> addresses, Map.Entry<String, Integer>... addressAndConnCount) {
        @SuppressWarnings("unchecked")
        final Matcher<? super T>[] args = (Matcher<? super T>[]) Arrays.stream(addressAndConnCount)
                .map(ac -> both(hasProperty("key", is(ac.getKey())))
                        .and(hasProperty("value", hasSize(ac.getValue()))))
                .collect(Collectors.toList())
                .toArray(new Matcher[] {});
        final Matcher<Iterable<? extends T>> iterableMatcher =
                addressAndConnCount.length == 0 ? emptyIterable() : contains(args);
        assertThat(addresses, iterableMatcher);
    }

    private void assertSelectThrows(Matcher<Throwable> matcher) {
        Exception e = assertThrows(ExecutionException.class, () -> lb.selectConnection(any(), null).toFuture().get());
        assertThat(e.getCause(), matcher);
    }

    Map.Entry<String, Integer> connectionsCount(String addr, int count) {
        return new AbstractMap.SimpleImmutableEntry<>(addr, count);
    }

    <T> void assertAddresses(Iterable<T> addresses, String... address) {
        @SuppressWarnings("unchecked")
        final Matcher<? super T>[] args = (Matcher<? super T>[]) Arrays.stream(address)
                .map(a -> hasProperty("key", is(a)))
                .collect(Collectors.toList())
                .toArray(new Matcher[] {});
        final Matcher<Iterable<? extends T>> iterableMatcher =
                address.length == 0 ? emptyIterable() : contains(args);
        assertThat(addresses, iterableMatcher);
    }

    private LegacyTestSingle<TestLoadBalancedConnection> newUnrealizedConnectionSingle(final String address) {
        final LegacyTestSingle<TestLoadBalancedConnection> unrealizedCnx = new LegacyTestSingle<>();
        connectionRealizers.offer(() -> unrealizedCnx.onSuccess(newConnection(address)));
        return unrealizedCnx;
    }

    private Single<TestLoadBalancedConnection> newRealizedConnectionSingle(final String address) {
        return newRealizedConnectionSingle(address, emptyAsyncCloseable());
    }

    private Single<TestLoadBalancedConnection> newRealizedConnectionSingle(final String address,
                                                                           final ListenableAsyncCloseable closeable) {
        return succeeded(newConnection(address, closeable));
    }

    private TestLoadBalancedConnection newConnection(final String address) {
        return newConnection(address, emptyAsyncCloseable());
    }

    private TestLoadBalancedConnection newConnection(final String address, final ListenableAsyncCloseable closeable) {
        final TestLoadBalancedConnection cnx = mock(TestLoadBalancedConnection.class);
        when(cnx.closeAsync()).thenReturn(closeable.closeAsync());
        when(cnx.closeAsyncGracefully()).thenReturn(closeable.closeAsyncGracefully());
        when(cnx.onClose()).thenReturn(closeable.onClose());
        when(cnx.onClosing()).thenReturn(closeable.onClosing());
        when(cnx.address()).thenReturn(address);
        when(cnx.toString()).thenReturn(address + '@' + cnx.hashCode());
        when(cnx.tryReserve()).thenReturn(true);

        connectionsCreated.add(cnx);
        return cnx;
    }

    private static Predicate<TestLoadBalancedConnection> newSaturableConnectionFilter() {
        final AtomicInteger selectConnectionCount = new AtomicInteger();
        final Set<TestLoadBalancedConnection> saturatedConnections = new CopyOnWriteArraySet<>();
        return c -> {
            if (saturatedConnections.contains(c)) {
                return false;
            }
            if (selectConnectionCount.incrementAndGet() % 11 == 0) {
                saturatedConnections.add(c);
            }
            return true;
        };
    }

    interface TestLoadBalancedConnection extends LoadBalancedConnection {
        String address();
    }

    static class DelegatingConnectionFactory implements
                                                       ConnectionFactory<String, TestLoadBalancedConnection> {

        private final Function<String, Single<TestLoadBalancedConnection>> connectionFactory;
        private final AtomicBoolean closed = new AtomicBoolean();

        DelegatingConnectionFactory(Function<String, Single<TestLoadBalancedConnection>> connectionFactory) {
            this.connectionFactory = connectionFactory;
        }

        @Override
        public Single<TestLoadBalancedConnection> newConnection(String s, TransportObserver observer) {
            return connectionFactory.apply(s);
        }

        @Override
        public Single<TestLoadBalancedConnection> newConnection(String s, @Nullable ContextMap context,
                                                                @Nullable TransportObserver observer) {
            return connectionFactory.apply(s);
        }

        @Override
        public Completable onClose() {
            return Completable.completed();
        }

        @Override
        public Completable closeAsync() {
            return Completable.completed().beforeOnSubscribe(cancellable -> closed.set(true));
        }

        boolean isClosed() {
            return closed.get();
        }
    }

    static class UnhealthyHostConnectionFactory {
        // Create a new instance of DeliberateException to avoid "Self-suppression not permitted"
        static final DeliberateException UNHEALTHY_HOST_EXCEPTION = new DeliberateException();
        private final String failingHost;
        private final AtomicInteger momentInTime = new AtomicInteger();
        final AtomicInteger requests = new AtomicInteger();
        final Single<TestLoadBalancedConnection> properConnection;
        final List<Single<TestLoadBalancedConnection>> connections;

        final Function<String, Single<TestLoadBalancedConnection>> factory =
                new Function<String, Single<TestLoadBalancedConnection>>() {

            @Override
            public Single<TestLoadBalancedConnection> apply(final String s) {
                return defer(() -> {
                    if (s.equals(failingHost)) {
                        requests.incrementAndGet();
                        if (momentInTime.get() >= connections.size()) {
                            return properConnection;
                        }
                        return connections.get(momentInTime.get());
                    }
                    return properConnection;
                });
            }
        };

        UnhealthyHostConnectionFactory(String failingHost, int timeAdvancementsTillHealthy,
                                       Single<TestLoadBalancedConnection> properConnection) {
            this(failingHost, timeAdvancementsTillHealthy, properConnection, UNHEALTHY_HOST_EXCEPTION);
        }

        UnhealthyHostConnectionFactory(String failingHost, int timeAdvancementsTillHealthy,
                                       Single<TestLoadBalancedConnection> properConnection, Throwable exception) {
            this.failingHost = failingHost;
            this.connections = IntStream.range(0, timeAdvancementsTillHealthy)
                    .<Single<TestLoadBalancedConnection>>mapToObj(__ -> failed(exception))
                    .collect(Collectors.toList());
            this.properConnection = properConnection;
        }

        DelegatingConnectionFactory createFactory() {
            return new DelegatingConnectionFactory(this.factory);
        }

        void advanceTime(TestExecutor executor) {
            momentInTime.incrementAndGet();
            executor.advanceTimeBy(1, SECONDS);
        }
    }
}
