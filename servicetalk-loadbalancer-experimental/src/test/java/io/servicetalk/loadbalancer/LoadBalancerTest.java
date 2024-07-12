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

import io.servicetalk.client.api.ConnectionLimitReachedException;
import io.servicetalk.client.api.ConnectionRejectedException;
import io.servicetalk.client.api.LoadBalancerReadyEvent;
import io.servicetalk.client.api.NoActiveHostException;
import io.servicetalk.client.api.NoAvailableHostException;
import io.servicetalk.client.api.ServiceDiscovererEvent;
import io.servicetalk.concurrent.PublisherSource.Subscriber;
import io.servicetalk.concurrent.PublisherSource.Subscription;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.DelegatingExecutor;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.LegacyTestSingle;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.TestPublisher;
import io.servicetalk.concurrent.internal.DeliberateException;
import io.servicetalk.concurrent.test.internal.TestSingleSubscriber;
import io.servicetalk.context.api.ContextMap;

import org.hamcrest.Matcher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
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
import java.util.function.Predicate;

import static io.servicetalk.client.api.ServiceDiscovererEvent.Status.AVAILABLE;
import static io.servicetalk.client.api.ServiceDiscovererEvent.Status.EXPIRED;
import static io.servicetalk.client.api.ServiceDiscovererEvent.Status.UNAVAILABLE;
import static io.servicetalk.concurrent.api.AsyncCloseables.toAsyncCloseable;
import static io.servicetalk.concurrent.api.AsyncCloseables.toListenableAsyncCloseable;
import static io.servicetalk.concurrent.api.BlockingTestUtils.awaitIndefinitely;
import static io.servicetalk.concurrent.api.Completable.completed;
import static io.servicetalk.concurrent.api.Completable.never;
import static io.servicetalk.concurrent.api.RetryStrategies.retryWithConstantBackoffFullJitter;
import static io.servicetalk.concurrent.api.Single.failed;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.concurrent.internal.TestTimeoutConstants.DEFAULT_TIMEOUT_SECONDS;
import static io.servicetalk.loadbalancer.HealthCheckConfig.DEFAULT_HEALTH_CHECK_FAILED_CONNECTIONS_THRESHOLD;
import static io.servicetalk.loadbalancer.HealthCheckConfig.DEFAULT_HEALTH_CHECK_RESUBSCRIBE_INTERVAL;
import static io.servicetalk.loadbalancer.UnhealthyHostConnectionFactory.UNHEALTHY_HOST_EXCEPTION;
import static java.lang.Long.MAX_VALUE;
import static java.time.Duration.ZERO;
import static java.time.Duration.ofMillis;
import static java.time.Duration.ofNanos;
import static java.time.Duration.ofSeconds;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.toSet;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.both;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
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
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

abstract class LoadBalancerTest extends LoadBalancerTestScaffold {

    static final String[] EMPTY_ARRAY = {};

    static final OutlierDetectorConfig BASE_CONFIG = new OutlierDetectorConfig.Builder()
            .failureDetectorInterval(ofSeconds(10), ZERO)
            .enforcingFailurePercentage(0)
            .enforcingConsecutive5xx(0)
            .enforcingSuccessRate(0)
            .build();

    private final TestSingleSubscriber<TestLoadBalancedConnection> selectConnectionListener =
            new TestSingleSubscriber<>();

    Predicate<TestLoadBalancedConnection> alwaysNewConnectionFilter() {
        return cnx -> lb.usedAddresses().stream().noneMatch(addr -> addr.getValue().stream().anyMatch(cnx::equals));
    }

    TestableLoadBalancer<String, TestLoadBalancedConnection> defaultLb(
        TestConnectionFactory connectionFactory) {
        return newTestLoadBalancer(serviceDiscoveryPublisher, connectionFactory);
    }

    protected abstract boolean isRoundRobin();

    protected abstract LoadBalancerBuilder<String, TestLoadBalancedConnection> baseLoadBalancerBuilder();

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
        connectionFactory = new TestConnectionFactory(this::newUnrealizedConnectionSingle);
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
        assumeTrue(isRoundRobin());

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

        connectionFactory = new TestConnectionFactory(__ -> failed(DELIBERATE_EXCEPTION));
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
        lb = defaultLb(new TestConnectionFactory(addr -> newRealizedConnectionSingle(addr,
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

        final TestConnectionFactory connectionFactory = unhealthyHostConnectionFactory.createFactory();
        lb = defaultLb(connectionFactory);

        sendServiceDiscoveryEvents(upEvent("address-1"));

        // We need to catch `DEFAULT_HEALTH_CHECK_FAILED_CONNECTIONS_THRESHOLD` exceptions before the bad host
        // will be taken out of rotation.
        for (int i = 0; i < DEFAULT_HEALTH_CHECK_FAILED_CONNECTIONS_THRESHOLD; i++) {
            try {
                lb.selectConnection(any(), null).toFuture().get();
                fail("Shouldn't have gotten a live connection.");
            } catch (Exception e) {
                assertThat(e.getCause(), is(UNHEALTHY_HOST_EXCEPTION));
            }
        }

        // Now add another healthy host which should be the host to get selected.
        sendServiceDiscoveryEvents(upEvent("address-2"));

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
        final TestConnectionFactory connectionFactory = unhealthyHostConnectionFactory.createFactory();

        lb = (TestableLoadBalancer<String, TestLoadBalancedConnection>)
                baseLoadBalancerBuilder()
                        .outlierDetectorConfig(new OutlierDetectorConfig.Builder(BASE_CONFIG)
                                .failedConnectionsThreshold(-1).build())
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

        final TestConnectionFactory connectionFactory = unhealthyHostConnectionFactory.createFactory();
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
        final TestConnectionFactory connectionFactory = unhealthyHostConnectionFactory.createFactory();

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
        final TestConnectionFactory connectionFactory = unhealthyHostConnectionFactory.createFactory();

        final AtomicInteger scheduleCnt = new AtomicInteger();
        lb = (TestableLoadBalancer<String, TestLoadBalancedConnection>)
                baseLoadBalancerBuilder()
                        .outlierDetectorConfig(new OutlierDetectorConfig.Builder(BASE_CONFIG)
                                .failureDetectorInterval(ofMillis(50), ofMillis(10))
                                .failedConnectionsThreshold(1)
                                .build())
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
        final TestConnectionFactory connectionFactory = unhealthyHostConnectionFactory.createFactory();

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
                assertThat(e.getCause(), instanceOf(NoActiveHostException.class));
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
                assertSelectThrows(instanceOf(NoActiveHostException.class));
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

    @Test
    void resubscribeToEventsWhenAllHostsAreUnhealthy() throws Exception {
        serviceDiscoveryPublisher.onComplete();
        assertThat(sequentialPublisherSubscriberFunction.isSubscribed(), is(false));
        assertThat(sequentialPublisherSubscriberFunction.numberOfSubscribersSeen(), is(1));

        TestConnectionFactory alwaysFail12ConnectionFactory = new TestConnectionFactory(address -> {
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
        assertSelectThrows(instanceOf(NoActiveHostException.class));
        assertThat(sequentialPublisherSubscriberFunction.numberOfSubscribersSeen(), is(3));

        // Verify state after re-subscribe
        assertAddresses(lb.usedAddresses(), "address-1", "address-2");
        sendServiceDiscoveryEvents(upEvent("address-2"), upEvent("address-3"), upEvent("address-4"));
        assertAddresses(lb.usedAddresses(), "address-2", "address-3", "address-4");

        // Verify the LB is recovered
        Map<String, Matcher<? super String>> expected = new HashMap<>();
        // While "address-2" is still in usedAddresses, it's kept UNHEALTHY and can not be selected
        expected.put("address-3", is("address-3"));
        expected.put("address-4", is("address-4"));
        String selected1 = lb.selectConnection(any(), null).toFuture().get().address();
        assertThat(selected1, is(anyOf(expected.values())));

        if (isRoundRobin()) {
            // These asserts are flaky for p2c because we don't have deterministic selection.
            expected.remove(selected1);
            assertThat(lb.selectConnection(any(), null).toFuture().get().address(), is(anyOf(expected.values())));
            assertConnectionCount(lb.usedAddresses(), connectionsCount("address-2", 0),
                    connectionsCount("address-3", 1), connectionsCount("address-4", 1));
        }
    }

    @Test
    void resubscribeToEventsNotTriggeredWhenDisabled() throws Exception {
        serviceDiscoveryPublisher.onComplete();
        assertThat(sequentialPublisherSubscriberFunction.isSubscribed(), is(false));
        assertThat(sequentialPublisherSubscriberFunction.numberOfSubscribersSeen(), is(1));

        TestConnectionFactory alwaysFailConnectionFactory =
                new TestConnectionFactory(address -> Single.failed(UNHEALTHY_HOST_EXCEPTION));
        lb = (TestableLoadBalancer<String, TestLoadBalancedConnection>)
                baseLoadBalancerBuilder()
                        .outlierDetectorConfig(new OutlierDetectorConfig.Builder(BASE_CONFIG)
                                .failureDetectorInterval(ofMillis(50), ofMillis(10))
                                // Set resubscribe interval to very large number
                                .serviceDiscoveryResubscribeInterval(ofNanos(MAX_VALUE), ZERO)
                                .build())
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
        assertSelectThrows(instanceOf(NoActiveHostException.class));
        assertThat(sequentialPublisherSubscriberFunction.numberOfSubscribersSeen(), is(2));
    }

    @Test
    void handleAllDiscoveryEvents() throws Exception {
        serviceDiscoveryPublisher.onComplete();

        lb = (TestableLoadBalancer<String, TestLoadBalancedConnection>)
                baseLoadBalancerBuilder()
                        .outlierDetectorConfig(new OutlierDetectorConfig.Builder(BASE_CONFIG)
                                .failureDetectorInterval(ofMillis(50), ofMillis(10))
                                .build())
                        .backgroundExecutor(testExecutor)
                        .build()
                        .newLoadBalancer(serviceDiscoveryPublisher, connectionFactory, "test-service");

        assertAddresses(lb.usedAddresses(), EMPTY_ARRAY);

        sendServiceDiscoveryEvents(upEvent("address-1"));
        assertAddresses(lb.usedAddresses(), "address-1");

        // addresses: [address-1]
        sendServiceDiscoveryEvents(downEvent("address-1", UNAVAILABLE));
        assertAddresses(lb.usedAddresses(), EMPTY_ARRAY);

        // addresses: []
        sendServiceDiscoveryEvents(upEvent("address-2"));
        assertAddresses(lb.usedAddresses(), "address-2");

        // addresses: [address-2]
        sendServiceDiscoveryEvents(downEvent("address-3", UNAVAILABLE));
        assertAddresses(lb.usedAddresses(), "address-2");

        // addresses: [address-2]
        sendServiceDiscoveryEvents(upEvent("address-1"));
        assertAddresses(lb.usedAddresses(), "address-2", "address-1");

        // Make sure both hosts have connections
        // addresses: [address-1, address-2]
        newForHost("address-1");
        newForHost("address-2");

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
        lb = newTestLoadBalancer();

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

    @Override
    protected final TestableLoadBalancer<String, TestLoadBalancedConnection> newTestLoadBalancer(
            final TestPublisher<Collection<ServiceDiscovererEvent<String>>> serviceDiscoveryPublisher,
            final TestConnectionFactory connectionFactory) {
        return (TestableLoadBalancer<String, TestLoadBalancedConnection>)
                baseLoadBalancerBuilder()
                        .outlierDetectorConfig(new OutlierDetectorConfig.Builder(BASE_CONFIG)
                                .failureDetectorInterval(ofMillis(50), ofMillis(10))
                                .build())
                        .backgroundExecutor(testExecutor)
                        .build()
                        .newLoadBalancer(serviceDiscoveryPublisher, connectionFactory, "test-service");
    }

    TestLoadBalancedConnection newForHost(String address) throws Exception {
        // This is necessary because p2c doesn't select hosts deterministically.
        for (;;) {
            TestLoadBalancedConnection cxn = lb.selectConnection(alwaysNewConnectionFilter(), null).toFuture().get();
            if (cxn.address().equals(address)) {
                return cxn;
            }
            // need to close it and try again.
            cxn.closeAsync().subscribe();
        }
    }

    private LegacyTestSingle<TestLoadBalancedConnection> newUnrealizedConnectionSingle(final String address) {
        final LegacyTestSingle<TestLoadBalancedConnection> unrealizedCnx = new LegacyTestSingle<>();
        connectionRealizers.offer(() -> unrealizedCnx.onSuccess(newConnection(address)));
        return unrealizedCnx;
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
}
