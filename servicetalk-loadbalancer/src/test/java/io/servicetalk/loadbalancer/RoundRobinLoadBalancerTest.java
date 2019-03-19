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
import io.servicetalk.client.api.DefaultServiceDiscovererEvent;
import io.servicetalk.client.api.LoadBalancerReadyEvent;
import io.servicetalk.client.api.NoAvailableHostException;
import io.servicetalk.client.api.ServiceDiscovererEvent;
import io.servicetalk.concurrent.CompletableSource;
import io.servicetalk.concurrent.PublisherSource.Subscriber;
import io.servicetalk.concurrent.PublisherSource.Subscription;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.LegacyMockedSingleListenerRule;
import io.servicetalk.concurrent.api.LegacyTestSingle;
import io.servicetalk.concurrent.api.ListenableAsyncCloseable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.TestPublisher;
import io.servicetalk.concurrent.api.TestSubscription;
import io.servicetalk.concurrent.internal.DeliberateException;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.Timeout;

import java.util.List;
import java.util.Map;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static io.servicetalk.concurrent.api.BlockingTestUtils.awaitIndefinitely;
import static io.servicetalk.concurrent.api.Processors.newCompletableProcessor;
import static io.servicetalk.concurrent.api.Single.error;
import static io.servicetalk.concurrent.api.Single.success;
import static io.servicetalk.concurrent.api.SourceAdapters.fromSource;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.concurrent.internal.ServiceTalkTestTimeout.DEFAULT_TIMEOUT_SECONDS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toSet;
import static org.hamcrest.Matchers.both;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class RoundRobinLoadBalancerTest {
    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();

    @Rule
    public final ExpectedException thrown = ExpectedException.none();

    @Rule
    public final LegacyMockedSingleListenerRule<TestLoadBalancedConnection> selectConnectionListener =
            new LegacyMockedSingleListenerRule<>();

    private final TestPublisher<ServiceDiscovererEvent<String>> serviceDiscoveryPublisher = new TestPublisher<>();
    private final List<TestLoadBalancedConnection> connectionsCreated = new CopyOnWriteArrayList<>();
    private final Queue<Runnable> connectionRealizers = new ConcurrentLinkedQueue<>();

    private RoundRobinLoadBalancer<String, TestLoadBalancedConnection> lb;
    private DelegatingConnectionFactory connectionFactory;

    @Before
    public void initialize() {
        connectionsCreated.clear();
        connectionRealizers.clear();
        connectionFactory = new DelegatingConnectionFactory(this::newRealizedConnectionSingle);
        lb = newTestLoadBalancer(connectionFactory);
    }

    @After
    public void closeLoadBalancer() throws Exception {
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
    public void streamEventJustClose() throws InterruptedException {
        CountDownLatch readyLatch = new CountDownLatch(1);
        CountDownLatch completeLatch = new CountDownLatch(1);
        lb.eventStream().doAfterComplete(completeLatch::countDown).first().subscribe(next -> readyLatch.countDown());
        lb.closeAsync().subscribe();

        assertThat(readyLatch.await(100, MILLISECONDS), is(false));
        completeLatch.await();
    }

    @Test
    public void streamEventReadyAndCompleteOnClose() throws InterruptedException {
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

    @SuppressWarnings("unchecked")
    @Test
    public void handleDiscoveryEvents() {
        assertThat(lb.activeAddresses(), is(empty()));

        sendServiceDiscoveryEvents(upEvent("address-1"));
        assertThat(lb.activeAddresses(), contains(
                both(hasProperty("key", is("address-1"))).and(hasProperty("value", is(empty())))));

        sendServiceDiscoveryEvents(downEvent("address-1"));
        assertThat(lb.activeAddresses(), is(empty()));

        sendServiceDiscoveryEvents(upEvent("address-2"));
        assertThat(lb.activeAddresses(), contains(
                both(hasProperty("key", is("address-2"))).and(hasProperty("value", is(empty())))));

        sendServiceDiscoveryEvents(downEvent("address-3"));
        assertThat(lb.activeAddresses(), contains(
                both(hasProperty("key", is("address-2"))).and(hasProperty("value", is(empty())))));

        sendServiceDiscoveryEvents(upEvent("address-1"));
        assertThat(lb.activeAddresses(), contains(
                both(hasProperty("key", is("address-1"))).and(hasProperty("value", is(empty()))),
                both(hasProperty("key", is("address-2"))).and(hasProperty("value", is(empty())))));

        sendServiceDiscoveryEvents(downEvent("address-1"));
        assertThat(lb.activeAddresses(), contains(
                both(hasProperty("key", is("address-2"))).and(hasProperty("value", is(empty())))));

        sendServiceDiscoveryEvents(downEvent("address-2"));
        assertThat(lb.activeAddresses(), is(empty()));

        sendServiceDiscoveryEvents(downEvent("address-3"));
        assertThat(lb.activeAddresses(), is(empty()));

        // Let's make sure that an SD failure doesn't compromise LB's internal state
        sendServiceDiscoveryEvents(upEvent("address-1"));
        assertThat(lb.activeAddresses(), contains(
                both(hasProperty("key", is("address-1"))).and(hasProperty("value", is(empty())))));
        serviceDiscoveryPublisher.onError(DELIBERATE_EXCEPTION);
        assertThat(lb.activeAddresses(), contains(
                both(hasProperty("key", is("address-1"))).and(hasProperty("value", is(empty())))));
    }

    @Test
    public void noServiceDiscoveryEvent() {
        selectConnectionListener.listen(lb.selectConnection(identity()));
        selectConnectionListener.verifyFailure(NoAvailableHostException.class);

        assertThat(connectionsCreated, is(empty()));
    }

    @Test
    public void selectStampedeUnsaturableConnection() throws Exception {
        serviceDiscoveryPublisher.onComplete();

        testSelectStampede(identity());
    }

    @Test
    public void selectStampedeSaturableConnection() throws Exception {
        serviceDiscoveryPublisher.onComplete();

        testSelectStampede(newSaturableConnectionFilter());
    }

    private void testSelectStampede(
            final Function<TestLoadBalancedConnection, TestLoadBalancedConnection> selectionFilter) throws Exception {
        connectionFactory = new DelegatingConnectionFactory(this::newUnrealizedConnectionSingle);
        lb = newTestLoadBalancer(connectionFactory);

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
                        selections = selections.concatWith(lb.selectConnection(selectionFilter));
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

    @SuppressWarnings("unchecked")
    @Test
    public void roundRobining() throws Exception {
        sendServiceDiscoveryEvents(upEvent("address-1"));
        sendServiceDiscoveryEvents(upEvent("address-2"));
        final List<String> connections = awaitIndefinitely((lb.selectConnection(identity())
                .concatWith(lb.selectConnection(identity()))
                .concatWith(lb.selectConnection(identity()))
                .concatWith(lb.selectConnection(identity()))
                .concatWith(lb.selectConnection(identity()))
                .map(TestLoadBalancedConnection::address)));

        assertThat(connections, contains("address-1", "address-2", "address-1", "address-2", "address-1"));

        assertThat(lb.activeAddresses(), contains(
                both(hasProperty("key", is("address-1"))).and(hasProperty("value", hasSize(1))),
                both(hasProperty("key", is("address-2"))).and(hasProperty("value", hasSize(1)))));

        assertThat(connectionsCreated, hasSize(2));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void closedConnectionPruning() throws Exception {
        sendServiceDiscoveryEvents(upEvent("address-1"));

        final TestLoadBalancedConnection connection = awaitIndefinitely(lb.selectConnection(identity()));
        assert connection != null;
        List<Map.Entry<String, List<TestLoadBalancedConnection>>> activeAddresses = lb.activeAddresses();

        assertThat(activeAddresses.size(), is(1));
        assertThat(activeAddresses, contains(
                both(hasProperty("key", is("address-1"))).and(hasProperty("value", hasSize(1)))));
        assertThat(activeAddresses.get(0).getValue().get(0), is(connection));
        awaitIndefinitely(connection.closeAsync());

        assertThat(lb.activeAddresses(),
                contains(both(hasProperty("key", is("address-1"))).and(hasProperty("value", is(empty())))));

        assertThat(connectionsCreated, hasSize(1));
    }

    @Test
    public void connectionFactoryErrorPropagation() throws Exception {
        serviceDiscoveryPublisher.onComplete();

        thrown.expect(instanceOf(ExecutionException.class));
        thrown.expectCause(instanceOf(DeliberateException.class));
        connectionFactory = new DelegatingConnectionFactory(__ -> error(DELIBERATE_EXCEPTION));
        lb = newTestLoadBalancer(connectionFactory);
        sendServiceDiscoveryEvents(upEvent("address-1"));
        awaitIndefinitely(lb.selectConnection(identity()));
    }

    @Test
    public void earlyFailsAfterClose() throws Exception {
        thrown.expect(instanceOf(ExecutionException.class));
        thrown.expectCause(instanceOf(IllegalStateException.class));

        sendServiceDiscoveryEvents(upEvent("address-1"));
        awaitIndefinitely(lb.closeAsync());

        try {
            awaitIndefinitely(lb.selectConnection(identity()));
        } finally {
            assertThat(connectionsCreated, is(empty()));
        }
    }

    @Test
    public void closeClosesConnectionFactory() throws Exception {
        awaitIndefinitely(lb.closeAsync());
        assertTrue("ConnectionFactory not closed.", connectionFactory.isClosed());
    }

    @SuppressWarnings("unchecked")
    private void sendServiceDiscoveryEvents(final ServiceDiscovererEvent... events) {
        serviceDiscoveryPublisher.onNext((ServiceDiscovererEvent<String>[]) events);
    }

    private static ServiceDiscovererEvent upEvent(final String address) {
        return new DefaultServiceDiscovererEvent<>(address, true);
    }

    private static ServiceDiscovererEvent downEvent(final String address) {
        return new DefaultServiceDiscovererEvent<>(address, false);
    }

    private RoundRobinLoadBalancer<String, TestLoadBalancedConnection> newTestLoadBalancer(
            final DelegatingConnectionFactory connectionFactory) {
        return new RoundRobinLoadBalancer<>(serviceDiscoveryPublisher, connectionFactory, String::compareTo);
    }

    private LegacyTestSingle<TestLoadBalancedConnection> newUnrealizedConnectionSingle(final String address) {
        final LegacyTestSingle<TestLoadBalancedConnection> unrealizedCnx = new LegacyTestSingle<>();
        connectionRealizers.offer(() -> unrealizedCnx.onSuccess(newConnection(address)));
        return unrealizedCnx;
    }

    private Single<TestLoadBalancedConnection> newRealizedConnectionSingle(final String address) {
        return success(newConnection(address));
    }

    @SuppressWarnings("unchecked")
    private TestLoadBalancedConnection newConnection(final String address) {
        final TestLoadBalancedConnection cnx = mock(TestLoadBalancedConnection.class);
        final CompletableSource.Processor closeCompletable = newCompletableProcessor();
        when(cnx.closeAsync()).thenAnswer(__ -> {
            closeCompletable.onComplete();
            return closeCompletable;
        });
        when(cnx.onClose()).thenReturn(fromSource(closeCompletable));
        when(cnx.address()).thenReturn(address);
        when(cnx.toString()).thenReturn(address + '@' + cnx.hashCode());

        connectionsCreated.add(cnx);
        return cnx;
    }

    private static Function<TestLoadBalancedConnection, TestLoadBalancedConnection> newSaturableConnectionFilter() {
        final AtomicInteger selectConnectionCount = new AtomicInteger();
        final Set<TestLoadBalancedConnection> saturatedConnections = new CopyOnWriteArraySet<>();
        return c -> {
            if (saturatedConnections.contains(c)) {
                return null;
            }
            if (selectConnectionCount.incrementAndGet() % 11 == 0) {
                saturatedConnections.add(c);
            }
            return c;
        };
    }

    private interface TestLoadBalancedConnection extends ListenableAsyncCloseable {
        String address();
    }

    private static class DelegatingConnectionFactory implements ConnectionFactory<String, TestLoadBalancedConnection> {

        private final Function<String, Single<TestLoadBalancedConnection>> connectionFactory;
        private final AtomicBoolean closed = new AtomicBoolean();

        DelegatingConnectionFactory(Function<String, Single<TestLoadBalancedConnection>> connectionFactory) {
            this.connectionFactory = connectionFactory;
        }

        @Override
        public Single<TestLoadBalancedConnection> newConnection(String s) {
            return connectionFactory.apply(s);
        }

        @Override
        public Completable onClose() {
            return Completable.completed();
        }

        @Override
        public Completable closeAsync() {
            return Completable.completed().doBeforeSubscribe(cancellable -> closed.set(true));
        }

        boolean isClosed() {
            return closed.get();
        }
    }
}
