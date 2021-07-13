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
import io.servicetalk.concurrent.api.LegacyTestSingle;
import io.servicetalk.concurrent.api.ListenableAsyncCloseable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.TestPublisher;
import io.servicetalk.concurrent.api.TestSubscription;
import io.servicetalk.concurrent.internal.DeliberateException;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.concurrent.test.internal.TestSingleSubscriber;
import io.servicetalk.transport.api.TransportObserver;

import org.hamcrest.Matcher;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.Timeout;

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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static io.servicetalk.concurrent.api.AsyncCloseables.emptyAsyncCloseable;
import static io.servicetalk.concurrent.api.BlockingTestUtils.awaitIndefinitely;
import static io.servicetalk.concurrent.api.Single.failed;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.concurrent.internal.TestTimeoutConstants.DEFAULT_TIMEOUT_SECONDS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.toSet;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.both;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

abstract class RoundRobinLoadBalancerTest {

    protected static final String[] EMPTY_ARRAY = new String[] {};

    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();

    @Rule
    public final ExpectedException thrown = ExpectedException.none();

    protected final TestSingleSubscriber<TestLoadBalancedConnection> selectConnectionListener =
            new TestSingleSubscriber<>();
    protected final List<TestLoadBalancedConnection> connectionsCreated = new CopyOnWriteArrayList<>();
    protected final Queue<Runnable> connectionRealizers = new ConcurrentLinkedQueue<>();

    protected final TestPublisher<ServiceDiscovererEvent<String>> serviceDiscoveryPublisher = new TestPublisher<>();
    private DelegatingConnectionFactory connectionFactory =
            new DelegatingConnectionFactory(this::newRealizedConnectionSingle);

    protected RoundRobinLoadBalancer<String, TestLoadBalancedConnection> lb;

    protected static <T> Predicate<T> any() {
      return __ -> true;
    }

    protected Predicate<TestLoadBalancedConnection> alwaysNewConnectionFilter() {
        return cnx -> lb.activeAddresses().stream().noneMatch(addr -> addr.getValue().stream().anyMatch(cnx::equals));
    }

    protected abstract RoundRobinLoadBalancer<String, TestLoadBalancedConnection> defaultLb();

    protected abstract RoundRobinLoadBalancer<String, TestLoadBalancedConnection> defaultLb(
            DelegatingConnectionFactory connectionFactory);

    @Before
    public void initialize() {
        lb = defaultLb();
        connectionsCreated.clear();
        connectionRealizers.clear();
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
        lb.eventStream().afterOnComplete(completeLatch::countDown).firstOrElse(() -> {
            throw new NoSuchElementException();
        }).subscribe(next -> readyLatch.countDown());
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

    @Test
    public void unknownAddressIsRemoved() {
        assertAddresses(lb.activeAddresses(), EMPTY_ARRAY);
        sendServiceDiscoveryEvents(downEvent("address-1"));
        assertAddresses(lb.activeAddresses(), EMPTY_ARRAY);
    }

    @Test
    public void noServiceDiscoveryEvent() {
        toSource(lb.selectConnection(any())).subscribe(selectConnectionListener);
        assertThat(selectConnectionListener.awaitOnError(), instanceOf(NoAvailableHostException.class));

        assertThat(connectionsCreated, is(empty()));
    }

    @Test
    public void selectStampedeUnsaturableConnection() throws Exception {
        serviceDiscoveryPublisher.onComplete();

        testSelectStampede(any());
    }

    @Test
    public void selectStampedeSaturableConnection() throws Exception {
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

    @SuppressWarnings("unchecked")
    @Test
    public void roundRobining() throws Exception {
        sendServiceDiscoveryEvents(upEvent("address-1"));
        sendServiceDiscoveryEvents(upEvent("address-2"));
        final List<String> connections = awaitIndefinitely((lb.selectConnection(any())
                .concat(lb.selectConnection(any()))
                .concat(lb.selectConnection(any()))
                .concat(lb.selectConnection(any()))
                .concat(lb.selectConnection(any()))
                .map(TestLoadBalancedConnection::address)));

        assertThat(connections, contains("address-1", "address-2", "address-1", "address-2", "address-1"));

        assertConnectionCount(lb.activeAddresses(),
                connectionsCount("address-1", 1),
                connectionsCount("address-2", 1));

        assertThat(connectionsCreated, hasSize(2));
    }

    @Test
    public void closedConnectionPruning() throws Exception {
        sendServiceDiscoveryEvents(upEvent("address-1"));

        final TestLoadBalancedConnection connection = awaitIndefinitely(lb.selectConnection(any()));
        assert connection != null;
        List<Map.Entry<String, List<TestLoadBalancedConnection>>> activeAddresses = lb.activeAddresses();

        assertThat(activeAddresses.size(), is(1));
        assertConnectionCount(activeAddresses, connectionsCount("address-1", 1));
        assertThat(activeAddresses.get(0).getValue().get(0), is(connection));
        awaitIndefinitely(connection.closeAsync());

        assertConnectionCount(lb.activeAddresses(), connectionsCount("address-1", 0));

        assertThat(connectionsCreated, hasSize(1));
    }

    @Test
    public void connectionFactoryErrorPropagation() throws Exception {
        serviceDiscoveryPublisher.onComplete();

        thrown.expect(instanceOf(ExecutionException.class));
        thrown.expectCause(instanceOf(DeliberateException.class));
        connectionFactory = new DelegatingConnectionFactory(__ -> failed(DELIBERATE_EXCEPTION));
        lb = defaultLb(connectionFactory);
        sendServiceDiscoveryEvents(upEvent("address-1"));
        awaitIndefinitely(lb.selectConnection(any()));
    }

    @Test
    public void earlyFailsAfterClose() throws Exception {
        thrown.expect(instanceOf(ExecutionException.class));
        thrown.expectCause(instanceOf(IllegalStateException.class));

        sendServiceDiscoveryEvents(upEvent("address-1"));
        awaitIndefinitely(lb.closeAsync());

        try {
            awaitIndefinitely(lb.selectConnection(any()));
        } finally {
            assertThat(connectionsCreated, is(empty()));
        }
    }

    @Test
    public void closeClosesConnectionFactory() throws Exception {
        awaitIndefinitely(lb.closeAsync());
        assertTrue("ConnectionFactory not closed.", connectionFactory.isClosed());
    }

    @Test
    public void newConnectionIsClosedWhenSelectorRejects() throws Exception {
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

    @SuppressWarnings("unchecked")
    protected void sendServiceDiscoveryEvents(final ServiceDiscovererEvent... events) {
        sendServiceDiscoveryEvents(serviceDiscoveryPublisher, events);
    }

    @SuppressWarnings("unchecked")
    private void sendServiceDiscoveryEvents(TestPublisher<ServiceDiscovererEvent<String>> serviceDiscoveryPublisher,
                                            final ServiceDiscovererEvent... events) {
        serviceDiscoveryPublisher.onNext(events);
    }

    protected static ServiceDiscovererEvent upEvent(final String address) {
        return new DefaultServiceDiscovererEvent<>(address, true);
    }

    protected static ServiceDiscovererEvent downEvent(final String address) {
        return new DefaultServiceDiscovererEvent<>(address, false);
    }

    protected RoundRobinLoadBalancer<String, TestLoadBalancedConnection> newTestLoadBalancer(
            boolean eagerConnectionShutdown) {
        return newTestLoadBalancer(serviceDiscoveryPublisher, connectionFactory, eagerConnectionShutdown);
    }

    protected RoundRobinLoadBalancer<String, TestLoadBalancedConnection> newTestLoadBalancer(
            final TestPublisher<ServiceDiscovererEvent<String>> serviceDiscoveryPublisher,
            final DelegatingConnectionFactory connectionFactory, final boolean eagerConnectionShutdown) {
        return new RoundRobinLoadBalancer<>(serviceDiscoveryPublisher, connectionFactory, eagerConnectionShutdown);
    }

    @SafeVarargs
    protected final <T> void assertConnectionCount(
            Iterable<T> addresses, Map.Entry<String, Integer>... addressAndConnCount) {
        @SuppressWarnings("unchecked")
        final Matcher<? super T>[] args = (Matcher<? super T>[]) Arrays.stream(addressAndConnCount)
                .map(ac -> both(hasProperty("key", is(ac.getKey())))
                        .and(hasProperty("value", hasSize(ac.getValue()))))
                .collect(Collectors.toList())
                .toArray(new Matcher[] {});
        final Matcher<Iterable<? extends T>> iterableMatcher =
                addressAndConnCount.length == 0 ? emptyIterable() : containsInAnyOrder(args);
        assertThat(addresses, iterableMatcher);
    }

    protected Map.Entry<String, Integer> connectionsCount(String addr, int count) {
        return new AbstractMap.SimpleImmutableEntry<>(addr, count);
    }

    protected <T> void assertAddresses(Iterable<T> addresses, String... address) {
        @SuppressWarnings("unchecked")
        final Matcher<? super T>[] args = (Matcher<? super T>[]) Arrays.stream(address)
                .map(a -> hasProperty("key", is(a)))
                .collect(Collectors.toList())
                .toArray(new Matcher[] {});
        final Matcher<Iterable<? extends T>> iterableMatcher =
                address.length == 0 ? emptyIterable() : containsInAnyOrder(args);
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

    protected interface TestLoadBalancedConnection extends ListenableAsyncCloseable, LoadBalancedConnection {
        String address();
    }

    protected static class DelegatingConnectionFactory implements
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
}
