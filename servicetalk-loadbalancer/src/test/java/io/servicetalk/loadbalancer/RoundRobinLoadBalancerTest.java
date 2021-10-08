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
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.TestExecutor;
import io.servicetalk.concurrent.api.TestPublisher;
import io.servicetalk.concurrent.api.TestSubscription;
import io.servicetalk.concurrent.internal.DeliberateException;
import io.servicetalk.concurrent.test.internal.TestSingleSubscriber;
import io.servicetalk.transport.api.TransportObserver;

import org.hamcrest.Matcher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.time.Duration;
import java.util.AbstractMap;
import java.util.Arrays;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static io.servicetalk.concurrent.api.AsyncCloseables.emptyAsyncCloseable;
import static io.servicetalk.concurrent.api.BlockingTestUtils.awaitIndefinitely;
import static io.servicetalk.concurrent.api.RetryStrategies.retryWithConstantBackoffFullJitter;
import static io.servicetalk.concurrent.api.Single.defer;
import static io.servicetalk.concurrent.api.Single.failed;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.concurrent.internal.TestTimeoutConstants.DEFAULT_TIMEOUT_SECONDS;
import static io.servicetalk.loadbalancer.RoundRobinLoadBalancerFactory.DEFAULT_HEALTH_CHECK_FAILED_CONNECTIONS_THRESHOLD;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.toSet;
import static org.hamcrest.MatcherAssert.assertThat;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

abstract class RoundRobinLoadBalancerTest {

    static final String[] EMPTY_ARRAY = new String[] {};

    @RegisterExtension
    final ExecutorExtension<TestExecutor> executor = ExecutorExtension.withTestExecutor();

    private final TestSingleSubscriber<TestLoadBalancedConnection> selectConnectionListener =
            new TestSingleSubscriber<>();
    private final List<TestLoadBalancedConnection> connectionsCreated = new CopyOnWriteArrayList<>();
    private final Queue<Runnable> connectionRealizers = new ConcurrentLinkedQueue<>();

    final TestPublisher<ServiceDiscovererEvent<String>> serviceDiscoveryPublisher = new TestPublisher<>();
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
        return newTestLoadBalancer(eagerConnectionShutdown());
    }

    RoundRobinLoadBalancer<String, TestLoadBalancedConnection> defaultLb(
        DelegatingConnectionFactory connectionFactory) {
        return newTestLoadBalancer(serviceDiscoveryPublisher, connectionFactory, eagerConnectionShutdown());
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
        toSource(lb.selectConnection(any())).subscribe(selectConnectionListener);
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
                        selections = selections.concat(lb.selectConnection(selectionFilter));
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
        final List<String> connections = awaitIndefinitely((lb.selectConnection(any())
                .concat(lb.selectConnection(any()))
                .concat(lb.selectConnection(any()))
                .concat(lb.selectConnection(any()))
                .concat(lb.selectConnection(any()))
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

        final TestLoadBalancedConnection connection = awaitIndefinitely(lb.selectConnection(any()));
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
                                             () -> awaitIndefinitely(lb.selectConnection(any())));
        assertThat(ex.getCause(), is(instanceOf(DeliberateException.class)));
    }

    @Test
    void earlyFailsAfterClose() throws Exception {
        sendServiceDiscoveryEvents(upEvent("address-1"));
        awaitIndefinitely(lb.closeAsync());

        ExecutionException ex = assertThrows(ExecutionException.class,
                                             () -> awaitIndefinitely(lb.selectConnection(any())));
        assertThat(ex.getCause(), is(instanceOf(IllegalStateException.class)));

        assertThat(connectionsCreated, is(empty()));
    }

    @Test
    void closeClosesConnectionFactory() throws Exception {
        awaitIndefinitely(lb.closeAsync());
        assertTrue(connectionFactory.isClosed(), "ConnectionFactory not closed.");
    }

    @Test
    void newConnectionIsClosedWhenSelectorRejects() throws Exception {
        sendServiceDiscoveryEvents(upEvent("address-1"));
        try {
            awaitIndefinitely(lb.selectConnection(__ -> false));
            fail();
        } catch (ExecutionException e) {
            assertThat(e.getCause(), is(instanceOf(ConnectionRejectedException.class)));
        }
        assertThat(connectionsCreated, hasSize(1));
        TestLoadBalancedConnection connection = connectionsCreated.get(0);
        assertThat(connection, is(notNullValue()));
        assertThat(connection.address(), is(equalTo("address-1")));
        awaitIndefinitely(connection.onClose());
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
                final TestLoadBalancedConnection selectedConnection = lb.selectConnection(any()).toFuture().get();
                assertThat(selectedConnection, equalTo(properConnection.toFuture().get()));
            } catch (Exception e) {
                assertThat(e.getCause(), is(DELIBERATE_EXCEPTION));
            }
        }

        for (int i = 0; i < 10; ++i) {
            final TestLoadBalancedConnection selectedConnection = lb.selectConnection(any()).toFuture().get();
            assertThat(selectedConnection, equalTo(properConnection.toFuture().get()));
        }

        assertThat(testExecutor.scheduledTasksPending(), equalTo(1));

        for (int i = 0; i < timeAdvancementsTillHealthy; ++i) {
            unhealthyHostConnectionFactory.advanceTime(testExecutor);
        }

        assertThat(testExecutor.scheduledTasksPending(), equalTo(0));

        for (int i = 0; i < 10; ++i) {
            final TestLoadBalancedConnection selectedConnection = lb.selectConnection(any()).toFuture().get();
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
                new RoundRobinLoadBalancerFactory.Builder<String, TestLoadBalancedConnection>()
                        .eagerConnectionShutdown(eagerConnectionShutdown())
                        .healthCheckFailedConnectionsThreshold(-1)
                        .build()
                        .newLoadBalancer(serviceDiscoveryPublisher, connectionFactory);

        sendServiceDiscoveryEvents(upEvent("address-1"));

        for (int i = 0; i < DEFAULT_HEALTH_CHECK_FAILED_CONNECTIONS_THRESHOLD; ++i) {
            Exception e = assertThrows(ExecutionException.class, () -> lb.selectConnection(any()).toFuture().get());
            assertThat(e.getCause(), is(DELIBERATE_EXCEPTION));
        }

        assertThat(testExecutor.scheduledTasksPending(), equalTo(0));

        for (int i = 0; i < timeAdvancementsTillHealthy - 1; ++i) {
            unhealthyHostConnectionFactory.advanceTime(testExecutor);
            Exception e = assertThrows(ExecutionException.class, () -> lb.selectConnection(any()).toFuture().get());
            assertThat(e.getCause(), is(DELIBERATE_EXCEPTION));
        }

        unhealthyHostConnectionFactory.advanceTime(testExecutor);
        assertThat(testExecutor.scheduledTasksPending(), equalTo(0));

        final TestLoadBalancedConnection selectedConnection = lb.selectConnection(any()).toFuture().get();
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
            Exception e = assertThrows(ExecutionException.class, () -> lb.selectConnection(any()).toFuture().get());
            assertThat(e.getCause(), is(DELIBERATE_EXCEPTION));
        }

        for (int i = 0; i < timeAdvancementsTillHealthy; ++i) {
            unhealthyHostConnectionFactory.advanceTime(testExecutor);
        }

        final TestLoadBalancedConnection selectedConnection = lb.selectConnection(any()).toFuture().get();
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
                new RoundRobinLoadBalancerFactory.Builder<String, TestLoadBalancedConnection>()
                        .eagerConnectionShutdown(eagerConnectionShutdown())
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
                        .newLoadBalancer(serviceDiscoveryPublisher, connectionFactory);

        sendServiceDiscoveryEvents(upEvent("address-1"));

        // Trigger first health check:
        Exception e = assertThrows(ExecutionException.class, () -> lb.selectConnection(any()).toFuture().get());
        assertThat(e.getCause(), is(DELIBERATE_EXCEPTION));
        // Execute two health checks: first will fail due to connectionFactory,
        // second - due to an unexpected error from executor:
        for (int i = 0; i < 2; ++i) {
            unhealthyHostConnectionFactory.advanceTime(testExecutor);
        }

        // Trigger yet another health check:
        e = assertThrows(ExecutionException.class, () -> lb.selectConnection(any()).toFuture().get());
        assertThat(e.getCause(), is(DELIBERATE_EXCEPTION));
        // Execute two health checks: first will fail due to connectionFactory, second succeeds:
        for (int i = 0; i < 2; ++i) {
            unhealthyHostConnectionFactory.advanceTime(testExecutor);
        }

        // Make sure we can select a connection:
        final TestLoadBalancedConnection selectedConnection = lb.selectConnection(any()).toFuture().get();
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
            final Runnable runnable = () ->
                    assertThrows(ExecutionException.class, () -> lb.selectConnection(any()).toFuture().get());

            for (int i = 0; i < 1000; i++) {
                executor.submit(runnable);
            }

            // From test main thread, wait until the host becomes UNHEALTHY, which is apparent from
            // NoHostAvailableException being thrown from selection AFTER a health check was scheduled by any thread.
            final Executor executorForRetries = io.servicetalk.concurrent.api.Executors.newFixedSizeExecutor(1);
            try {
                awaitIndefinitely(lb.selectConnection(any()).retryWhen(retryWithConstantBackoffFullJitter((t) ->
                                // DeliberateException comes from connection opening, check for that first
                                // Next, NoAvailableHostException is thrown when the host is unhealthy,
                                // but we still wait until the health check is scheduled and only then stop retrying.
                                t instanceof DeliberateException || testExecutor.scheduledTasksPending() == 0,
                        // try to prevent stack overflow
                        Duration.ofMillis(30), executorForRetries)));
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
                Exception e = assertThrows(ExecutionException.class, () -> lb.selectConnection(any()).toFuture().get());
                assertThat(e.getCause(), instanceOf(NoAvailableHostException.class));
            }
        } finally {
            // Shutdown the concurrent validation of unhealthiness.
            executor.shutdownNow();
            executor.awaitTermination(10, SECONDS);
        }

        unhealthyHostConnectionFactory.advanceTime(testExecutor);

        final TestLoadBalancedConnection selectedConnection = lb.selectConnection(any()).toFuture().get();
        assertThat(selectedConnection, equalTo(properConnection.toFuture().get()));
    }

    void sendServiceDiscoveryEvents(final ServiceDiscovererEvent... events) {
        sendServiceDiscoveryEvents(serviceDiscoveryPublisher, events);
    }

    @SuppressWarnings("unchecked")
    private void sendServiceDiscoveryEvents(TestPublisher<ServiceDiscovererEvent<String>> serviceDiscoveryPublisher,
                                            final ServiceDiscovererEvent... events) {
        serviceDiscoveryPublisher.onNext(events);
    }

    static ServiceDiscovererEvent upEvent(final String address) {
        return new DefaultServiceDiscovererEvent<>(address, true);
    }

    static ServiceDiscovererEvent downEvent(final String address) {
        return new DefaultServiceDiscovererEvent<>(address, false);
    }

    RoundRobinLoadBalancer<String, TestLoadBalancedConnection> newTestLoadBalancer(
        boolean eagerConnectionShutdown) {
        return newTestLoadBalancer(serviceDiscoveryPublisher, connectionFactory, eagerConnectionShutdown);
    }

    RoundRobinLoadBalancer<String, TestLoadBalancedConnection> newTestLoadBalancer(
        final TestPublisher<ServiceDiscovererEvent<String>> serviceDiscoveryPublisher,
        final DelegatingConnectionFactory connectionFactory, final boolean eagerConnectionShutdown) {
        return (RoundRobinLoadBalancer<String, TestLoadBalancedConnection>)
                new RoundRobinLoadBalancerFactory.Builder<String, TestLoadBalancedConnection>()
                        .eagerConnectionShutdown(eagerConnectionShutdown)
                        .backgroundExecutor(testExecutor)
                        .build()
                        .newLoadBalancer(serviceDiscoveryPublisher, connectionFactory);
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
        return succeeded(newConnection(address));
    }

    private TestLoadBalancedConnection newConnection(final String address) {
        final TestLoadBalancedConnection cnx = mock(TestLoadBalancedConnection.class);
        final ListenableAsyncCloseable closeable = emptyAsyncCloseable();
        when(cnx.closeAsync()).thenReturn(closeable.closeAsync());
        when(cnx.closeAsyncGracefully()).thenReturn(closeable.closeAsyncGracefully());
        when(cnx.onClose()).thenReturn(closeable.onClose());
        when(cnx.address()).thenReturn(address);
        when(cnx.toString()).thenReturn(address + '@' + cnx.hashCode());

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

        UnhealthyHostConnectionFactory(final String failingHost, int timeAdvancementsTillHealthy,
                                       Single<TestLoadBalancedConnection> properConnection) {
            this.failingHost = failingHost;
            this.connections = IntStream.range(0, timeAdvancementsTillHealthy)
                    .<Single<TestLoadBalancedConnection>>mapToObj(__ -> failed(DELIBERATE_EXCEPTION))
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
