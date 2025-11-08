/*
 * Copyright © 2023 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.client.api.DefaultServiceDiscovererEvent;
import io.servicetalk.client.api.ServiceDiscovererEvent;
import io.servicetalk.concurrent.api.ExecutorExtension;
import io.servicetalk.concurrent.api.ListenableAsyncCloseable;
import io.servicetalk.concurrent.api.SequentialPublisherSubscriberFunction;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.TestExecutor;
import io.servicetalk.concurrent.api.TestPublisher;
import io.servicetalk.concurrent.api.TestSubscription;

import org.hamcrest.Matcher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

import static io.servicetalk.client.api.ServiceDiscovererEvent.Status.AVAILABLE;
import static io.servicetalk.client.api.ServiceDiscovererEvent.Status.EXPIRED;
import static io.servicetalk.client.api.ServiceDiscovererEvent.Status.UNAVAILABLE;
import static io.servicetalk.concurrent.api.AsyncCloseables.emptyAsyncCloseable;
import static io.servicetalk.concurrent.api.BlockingTestUtils.awaitIndefinitely;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.both;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.emptyIterable;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

abstract class LoadBalancerTestScaffold {

    @RegisterExtension
    final ExecutorExtension<TestExecutor> executor = ExecutorExtension.withTestExecutor();

    final List<TestLoadBalancedConnection> connectionsCreated = new CopyOnWriteArrayList<>();
    final Queue<Runnable> connectionRealizers = new ConcurrentLinkedQueue<>();
    final SequentialPublisherSubscriberFunction<Collection<ServiceDiscovererEvent<String>>>
            sequentialPublisherSubscriberFunction = new SequentialPublisherSubscriberFunction<>();
    final TestPublisher<Collection<ServiceDiscovererEvent<String>>> serviceDiscoveryPublisher =
            new TestPublisher.Builder<Collection<ServiceDiscovererEvent<String>>>()
                    .sequentialSubscribers(sequentialPublisherSubscriberFunction)
                    .build();
    TestConnectionFactory connectionFactory =
            new TestConnectionFactory(this::newRealizedConnectionSingle);

    @Nullable
    TestableLoadBalancer<String, TestLoadBalancedConnection> lb;
    @Nullable
    TestExecutor testExecutor;

    protected abstract boolean eagerConnectionShutdown();

    @BeforeEach
    void initialize() {
        testExecutor = executor.executor();
        lb = newTestLoadBalancer();
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

    final TestableLoadBalancer<String, TestLoadBalancedConnection> newTestLoadBalancer() {
        return newTestLoadBalancer(serviceDiscoveryPublisher, connectionFactory);
    }

    abstract TestableLoadBalancer<String, TestLoadBalancedConnection> newTestLoadBalancer(
            TestPublisher<Collection<ServiceDiscovererEvent<String>>> serviceDiscoveryPublisher,
            TestConnectionFactory connectionFactory);

    @SafeVarargs
    final void sendServiceDiscoveryEvents(final ServiceDiscovererEvent<String>... events) {
        sendServiceDiscoveryEvents(serviceDiscoveryPublisher, events);
    }

    @SuppressWarnings("unchecked")
    private void sendServiceDiscoveryEvents(
            TestPublisher<Collection<ServiceDiscovererEvent<String>>> serviceDiscoveryPublisher,
            final ServiceDiscovererEvent<String>... events) {
        serviceDiscoveryPublisher.onNext(Arrays.asList(events));
    }

    ServiceDiscovererEvent<String> upEvent(final String address) {
        return new DefaultServiceDiscovererEvent<>(address, AVAILABLE);
    }

    ServiceDiscovererEvent<String> downEvent(final String address) {
        return new DefaultServiceDiscovererEvent<>(address, eagerConnectionShutdown() ? UNAVAILABLE : EXPIRED);
    }

    ServiceDiscovererEvent<String> downEvent(final String address, ServiceDiscovererEvent.Status status) {
        return new DefaultServiceDiscovererEvent<>(address, status);
    }

    Single<TestLoadBalancedConnection> newRealizedConnectionSingle(final String address) {
        return newRealizedConnectionSingle(address, emptyAsyncCloseable());
    }

    Single<TestLoadBalancedConnection> newRealizedConnectionSingle(final String address,
                                                                           final ListenableAsyncCloseable closeable) {
        return succeeded(newConnection(address, closeable));
    }

    TestLoadBalancedConnection newConnection(final String address) {
        return newConnection(address, emptyAsyncCloseable());
    }

    private TestLoadBalancedConnection newConnection(final String address, final ListenableAsyncCloseable closeable) {
        final TestLoadBalancedConnection cnx = TestLoadBalancedConnection.mockConnection(address, closeable);
        connectionsCreated.add(cnx);
        return cnx;
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
                addressAndConnCount.length == 0 ? emptyIterable() : containsInAnyOrder(args);
        assertThat(addresses, iterableMatcher);
    }

    <T extends Map.Entry<String, ?>> void assertAddresses(Iterable<T> addresses, String... address) {
        List<String> actualKeys = new ArrayList<>();
        for (T item : addresses) {
            actualKeys.add(item.getKey());
        }
        assertThat(actualKeys, containsInAnyOrder(address));
    }

    Map.Entry<String, Integer> connectionsCount(String addr, int count) {
        return new AbstractMap.SimpleImmutableEntry<>(addr, count);
    }

    void assertSelectThrows(Matcher<Throwable> matcher) {
        Exception e = assertThrows(ExecutionException.class, () -> lb.selectConnection(any(), null).toFuture().get());
        assertThat(e.getCause(), matcher);
    }

    static <T> Predicate<T> any() {
        return __ -> true;
    }
}
