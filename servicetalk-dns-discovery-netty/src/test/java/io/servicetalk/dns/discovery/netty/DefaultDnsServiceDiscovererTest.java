/*
 * Copyright © 2018 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.dns.discovery.netty;

import io.servicetalk.client.api.ServiceDiscoverer;
import io.servicetalk.client.api.ServiceDiscovererEvent;
import io.servicetalk.client.api.ServiceDiscovererFilter;
import io.servicetalk.client.servicediscoverer.ServiceDiscovererTestSubscriber;
import io.servicetalk.concurrent.PublisherSource.Subscriber;
import io.servicetalk.concurrent.PublisherSource.Subscription;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.transport.netty.internal.EventLoopAwareNettyIoExecutor;

import io.netty.resolver.dns.DnsNameResolverTimeoutException;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static io.servicetalk.concurrent.api.Completable.failed;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.dns.discovery.netty.DnsTestUtils.nextIp;
import static io.servicetalk.dns.discovery.netty.DnsTestUtils.nextIp6;
import static io.servicetalk.dns.discovery.netty.TestRecordStore.createRecord;
import static io.servicetalk.transport.netty.NettyIoExecutors.createIoExecutor;
import static io.servicetalk.transport.netty.internal.EventLoopAwareNettyIoExecutors.toEventLoopAwareNettyIoExecutor;
import static io.servicetalk.transport.netty.internal.GlobalExecutionContext.globalExecutionContext;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.apache.directory.server.dns.messages.RecordType.A;
import static org.apache.directory.server.dns.messages.RecordType.AAAA;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.lessThan;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

public class DefaultDnsServiceDiscovererTest {
    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();

    private EventLoopAwareNettyIoExecutor nettyIoExecutor;
    private TestRecordStore recordStore = new TestRecordStore();
    private TestDnsServer dnsServer;
    private ServiceDiscoverer<String, InetAddress, ServiceDiscovererEvent<InetAddress>> discoverer;

    @Before
    public void setup() throws Exception {
        nettyIoExecutor = toEventLoopAwareNettyIoExecutor(createIoExecutor());

        dnsServer = new TestDnsServer(recordStore);
        dnsServer.start();
        discoverer = serviceDiscovererBuilder().buildInetDiscoverer();
    }

    @After
    public void tearDown() throws Exception {
        discoverer.closeAsync().toFuture().get();
        dnsServer.stop();
        nettyIoExecutor.closeAsync().toFuture().get();
    }

    @Test
    public void unknownHostDiscover() throws Exception {
        CountDownLatch retryLatch = new CountDownLatch(2);
        ServiceDiscoverer<String, InetAddress, ServiceDiscovererEvent<InetAddress>> discoverer =
                serviceDiscovererBuilderWithoutRetry()
                        .appendFilter(serviceDiscoverer -> new RetryingDnsServiceDiscovererFilter(
                                serviceDiscoverer, (retryCount, cause) -> {
                            retryLatch.countDown();
                            return retryCount == 1 && cause instanceof UnknownHostException ?
                                    globalExecutionContext().executor().timer(Duration.ofSeconds(1)) : failed(cause);
                        })).buildInetDiscoverer();

        try {
            AtomicReference<Throwable> throwableRef = new AtomicReference<>();
            Publisher<ServiceDiscovererEvent<InetAddress>> publisher = discoverer.discover("unknown.com");
            final CountDownLatch latch = new CountDownLatch(1);
            ServiceDiscovererTestSubscriber<InetAddress> subscriber =
                    new ServiceDiscovererTestSubscriber<>(latch, throwableRef, Long.MAX_VALUE);
            toSource(publisher).subscribe(subscriber);

            retryLatch.await();
            latch.await();
            assertThat("Unexpected exception during DNS lookup.",
                    throwableRef.get(), instanceOf(UnknownHostException.class));
            assertThat(subscriber.activeCount(), equalTo(0));
            assertThat(subscriber.inactiveCount(), equalTo(0));
        } finally {
            discoverer.closeAsync().toFuture().get();
        }
    }

    @Test
    public void singleDiscover() throws InterruptedException {
        recordStore.addResponse("apple.com", A, nextIp());
        final int expectedActiveCount = 1;
        final int expectedInactiveCount = 0;

        CountDownLatch latch = new CountDownLatch(expectedActiveCount + expectedInactiveCount);
        AtomicReference<Throwable> throwableRef = new AtomicReference<>();
        Publisher<ServiceDiscovererEvent<InetAddress>> publisher = discoverer.discover("apple.com");
        ServiceDiscovererTestSubscriber<InetAddress> subscriber =
                new ServiceDiscovererTestSubscriber<>(latch, throwableRef, expectedActiveCount);
        toSource(publisher).subscribe(subscriber);

        latch.await();
        assertNull(throwableRef.get());
        assertThat(subscriber.activeCount(), equalTo(expectedActiveCount));
        assertThat(subscriber.inactiveCount(), equalTo(expectedInactiveCount));
    }

    @Test
    public void singleDiscoverMultipleRecords() throws InterruptedException {
        recordStore.addResponse("apple.com", A, nextIp(), nextIp(), nextIp(), nextIp(), nextIp());

        final int expectedActiveCount = 5;
        final int expectedInactiveCount = 0;

        CountDownLatch latch = new CountDownLatch(expectedActiveCount + expectedInactiveCount);
        AtomicReference<Throwable> throwableRef = new AtomicReference<>();
        Publisher<ServiceDiscovererEvent<InetAddress>> publisher = discoverer.discover("apple.com");
        ServiceDiscovererTestSubscriber<InetAddress> subscriber =
                new ServiceDiscovererTestSubscriber<>(latch, throwableRef, expectedActiveCount);
        toSource(publisher).subscribe(subscriber);

        latch.await();
        assertNull(throwableRef.get());
        assertThat(subscriber.activeCount(), equalTo(expectedActiveCount));
        assertThat(subscriber.inactiveCount(), equalTo(expectedInactiveCount));
    }

    @Test
    public void singleDiscoverDuplicateRecords() throws InterruptedException {
        final String ip = nextIp();
        recordStore.addResponse("apple.com", A, nextIp(), ip, ip, nextIp());

        final int expectedActiveCount = 3;
        final int expectedInactiveCount = 0;

        CountDownLatch latch = new CountDownLatch(expectedActiveCount + expectedInactiveCount);
        AtomicReference<Throwable> throwableRef = new AtomicReference<>();
        Publisher<ServiceDiscovererEvent<InetAddress>> publisher = discoverer.discover("apple.com");
        ServiceDiscovererTestSubscriber<InetAddress> subscriber =
                new ServiceDiscovererTestSubscriber<>(latch, throwableRef, expectedActiveCount);
        toSource(publisher).subscribe(subscriber);

        latch.await();
        assertNull(throwableRef.get());
        assertThat(subscriber.activeCount(), equalTo(expectedActiveCount));
        assertThat(subscriber.inactiveCount(), equalTo(expectedInactiveCount));
    }

    @Test
    public void repeatDiscoverMultipleRecords() throws Exception {
        recordStore.addResponse("apple.com", A, nextIp(), nextIp(), nextIp(), nextIp(), nextIp())
                .defaultResponse("apple.com", A, nextIp(), nextIp(), nextIp(), nextIp(), nextIp());

        final int expectedActiveCount = 10;
        final int expectedInactiveCount = 5;

        CountDownLatch latch = new CountDownLatch(expectedActiveCount + expectedInactiveCount);
        AtomicReference<Throwable> throwableRef = new AtomicReference<>();
        Publisher<ServiceDiscovererEvent<InetAddress>> publisher = discoverer.discover("apple.com");
        ServiceDiscovererTestSubscriber<InetAddress> subscriber =
                new ServiceDiscovererTestSubscriber<>(latch, throwableRef, Long.MAX_VALUE);
        toSource(publisher).subscribe(subscriber);

        latch.await();
        assertNull(throwableRef.get());
        assertThat(subscriber.activeCount(), equalTo(expectedActiveCount));
        assertThat(subscriber.inactiveCount(), equalTo(expectedInactiveCount));
    }

    @Test
    public void repeatDiscover() throws Exception {
        recordStore.addResponse("apple.com", A, nextIp())
                .defaultResponse("apple.com", A, nextIp());

        final int expectedActiveCount = 2;
        final int expectedInactiveCount = 1;

        CountDownLatch latch = new CountDownLatch(expectedActiveCount + expectedInactiveCount);
        AtomicReference<Throwable> throwableRef = new AtomicReference<>();
        Publisher<ServiceDiscovererEvent<InetAddress>> publisher = discoverer.discover("apple.com");
        ServiceDiscovererTestSubscriber<InetAddress> subscriber =
                new ServiceDiscovererTestSubscriber<>(latch, throwableRef, Long.MAX_VALUE);
        toSource(publisher).subscribe(subscriber);

        latch.await();
        assertNull(throwableRef.get());
        assertThat(subscriber.activeCount(), equalTo(expectedActiveCount));
        assertThat(subscriber.inactiveCount(), equalTo(expectedInactiveCount));
    }

    @Test
    public void repeatDiscoverMultipleHosts() throws Exception {
        recordStore.addResponse("apple.com", A, nextIp())
                .defaultResponse("apple.com", A, nextIp())
                .addResponse("servicetalk.io", A, nextIp())
                .defaultResponse("servicetalk.io", A, nextIp());

        final int expectedAppleActiveCount = 2;
        final int expectedAppleInactiveCount = 1;
        final int expectedStActiveCount = 2;
        final int expectedStInactiveCount = 1;

        CountDownLatch appleLatch = new CountDownLatch(expectedAppleActiveCount + expectedAppleInactiveCount);
        CountDownLatch stLatch = new CountDownLatch(expectedStActiveCount + expectedStInactiveCount);

        AtomicReference<Throwable> throwableRef = new AtomicReference<>();
        Publisher<ServiceDiscovererEvent<InetAddress>> applePublisher = discoverer.discover("apple.com");
        Publisher<ServiceDiscovererEvent<InetAddress>> stPublisher = discoverer.discover("servicetalk.io");
        ServiceDiscovererTestSubscriber<InetAddress> appleSubscriber =
                new ServiceDiscovererTestSubscriber<>(appleLatch, throwableRef, Long.MAX_VALUE);
        ServiceDiscovererTestSubscriber<InetAddress> stSubscriber =
                new ServiceDiscovererTestSubscriber<>(stLatch, throwableRef, Long.MAX_VALUE);
        toSource(applePublisher).subscribe(appleSubscriber);
        toSource(stPublisher).subscribe(stSubscriber);

        appleLatch.await();
        stLatch.await();
        assertNull(throwableRef.get());
        assertThat(appleSubscriber.activeCount(), equalTo(expectedAppleActiveCount));
        assertThat(appleSubscriber.inactiveCount(), equalTo(expectedAppleInactiveCount));
        assertThat(stSubscriber.activeCount(), equalTo(expectedStActiveCount));
        assertThat(stSubscriber.inactiveCount(), equalTo(expectedStInactiveCount));
    }

    @Test
    public void repeatDiscoverTtl() throws InterruptedException {
        AtomicLong firstTime = new AtomicLong();
        AtomicLong secondTime = new AtomicLong();
        recordStore
                .addResponse("apple.com", A, () -> {
                    firstTime.set(System.currentTimeMillis());
                    return singletonList(createRecord("apple.com", A, 2, nextIp()));
                })
                .defaultResponse("apple.com", A, () -> {
                    secondTime.set(System.currentTimeMillis());
                    return singletonList(createRecord("apple.com", A, 2, nextIp()));
                });

        final int expectedActiveCount = 2;
        final int expectedInactiveCount = 1;

        CountDownLatch latch = new CountDownLatch(expectedActiveCount + expectedInactiveCount);
        AtomicReference<Throwable> throwableRef = new AtomicReference<>();
        Publisher<ServiceDiscovererEvent<InetAddress>> publisher = discoverer.discover("apple.com");
        ServiceDiscovererTestSubscriber<InetAddress> subscriber =
                new ServiceDiscovererTestSubscriber<>(latch, throwableRef, Long.MAX_VALUE);
        toSource(publisher).subscribe(subscriber);

        latch.await();
        assertNull(throwableRef.get());
        assertThat(subscriber.activeCount(), equalTo(expectedActiveCount));
        assertThat(subscriber.inactiveCount(), equalTo(expectedInactiveCount));
        long timeBetweenQueries = secondTime.get() - firstTime.get();
        assertThat(timeBetweenQueries, greaterThanOrEqualTo(2000L));
    }

    @Test
    public void repeatDiscoverMultiTtl() throws InterruptedException {
        final String ipA1 = nextIp();
        final String ipA2 = nextIp();
        final String ipB1 = nextIp();
        final String ipB2 = nextIp();

        AtomicLong firstTime = new AtomicLong();
        AtomicLong secondTime = new AtomicLong();
        recordStore
                .addResponse("apple.com", A, () -> {
                    firstTime.set(System.currentTimeMillis());
                    return asList(createRecord("apple.com", A, 1, ipA1),
                            createRecord("apple.com", A, 10, ipA2));
                })
                .defaultResponse("apple.com", A, () -> {
                    secondTime.set(System.currentTimeMillis());
                    return asList(createRecord("apple.com", A, 10, ipB1),
                            createRecord("apple.com", A, 10, ipB2));
                });

        final int expectedActiveCount = 4;
        final int expectedInactiveCount = 2;

        CountDownLatch latch = new CountDownLatch(expectedActiveCount + expectedInactiveCount);
        Publisher<ServiceDiscovererEvent<InetAddress>> publisher = discoverer.discover("apple.com");
        final TestSubscriber subscriber = new TestSubscriber(latch);
        toSource(publisher).subscribe(subscriber);

        latch.await();
        assertNull(subscriber.throwableRef.get());
        assertThat(new HashSet<>(subscriber.activeEventAddresses),
                equalTo(new HashSet<>(asList(ipA1, ipA2, ipB1, ipB2))));
        assertThat(subscriber.activeEventAddresses.size(), equalTo(expectedActiveCount));
        assertThat(new HashSet<>(subscriber.inactiveEventAddresses), equalTo(new HashSet<>(asList(ipA1, ipA2))));
        assertThat(subscriber.inactiveEventAddresses.size(), equalTo(expectedInactiveCount));
        long timeBetweenQueries = secondTime.get() - firstTime.get();
        assertThat(timeBetweenQueries, greaterThanOrEqualTo(1000L));
        assertThat(timeBetweenQueries, lessThan(10_000L));
    }

    @Test
    public void repeatDiscoverNxDomain() throws Exception {
        recordStore.addResponse("apple.com", A, nextIp());

        final int expectedActiveCount = 1;
        final int expectedInactiveCount = 1;

        CountDownLatch latch = new CountDownLatch(expectedActiveCount + expectedInactiveCount);
        AtomicReference<Throwable> throwableRef = new AtomicReference<>();
        Publisher<ServiceDiscovererEvent<InetAddress>> publisher = discoverer.discover("apple.com");
        ServiceDiscovererTestSubscriber<InetAddress> subscriber =
                new ServiceDiscovererTestSubscriber<>(latch, throwableRef, Long.MAX_VALUE);
        toSource(publisher).subscribe(subscriber);

        latch.await();
        assertNull(throwableRef.get());
        assertThat(subscriber.activeCount(), equalTo(expectedActiveCount));
        assertThat(subscriber.inactiveCount(), equalTo(expectedInactiveCount));
    }

    @Test
    public void repeatDiscoverNxDomainNoSendUnavailable() throws Exception {
        recordStore.addResponse("apple.com", A, nextIp());

        ServiceDiscoverer<String, InetAddress, ServiceDiscovererEvent<InetAddress>> discoverer =
                serviceDiscovererBuilderWithoutRetry()
                        .invalidateHostsOnDnsFailure(__ -> false)
                        .buildInetDiscoverer();
        try {
            final int expectedActiveCount = 1;
            final int expectedInactiveCount = 0;

            CountDownLatch latch = new CountDownLatch(expectedActiveCount + expectedInactiveCount + 1);
            AtomicReference<Throwable> throwableRef = new AtomicReference<>();
            Publisher<ServiceDiscovererEvent<InetAddress>> publisher = discoverer.discover("apple.com");
            ServiceDiscovererTestSubscriber<InetAddress> subscriber =
                    new ServiceDiscovererTestSubscriber<>(latch, throwableRef, Long.MAX_VALUE);
            toSource(publisher).subscribe(subscriber);

            latch.await();
            assertThat("Unexpected exception during DNS lookup.",
                    throwableRef.get(), instanceOf(UnknownHostException.class));
            assertThat(subscriber.activeCount(), equalTo(expectedActiveCount));
            assertThat(subscriber.inactiveCount(), equalTo(expectedInactiveCount));
        } finally {
            discoverer.closeAsync().toFuture().get();
        }
    }

    @Test
    public void repeatDiscoverNxDomainAndRecover() throws Exception {
        recordStore.addResponse("apple.com", A, nextIp());

        final int expectedActiveCount = 1;
        final int expectedInactiveCount = 1;

        CountDownLatch latch1 = new CountDownLatch(expectedActiveCount + expectedInactiveCount);
        CountDownLatch latch2 = new CountDownLatch(expectedActiveCount + expectedInactiveCount + 1);
        AtomicReference<Throwable> throwableRef = new AtomicReference<>();
        Publisher<ServiceDiscovererEvent<InetAddress>> publisher = discoverer.discover("apple.com");
        ServiceDiscovererTestSubscriber<InetAddress> subscriber =
                new ServiceDiscovererTestSubscriber<>(latch1, throwableRef, Long.MAX_VALUE);
        toSource(publisher.beforeOnNext(n -> latch2.countDown())).subscribe(subscriber);

        latch1.await();
        assertNull(throwableRef.get());
        assertThat(subscriber.activeCount(), equalTo(expectedActiveCount));
        assertThat(subscriber.inactiveCount(), equalTo(expectedInactiveCount));

        recordStore.defaultResponse("apple.com", A, nextIp());
        latch2.await();
        assertThat(subscriber.activeCount(), equalTo(expectedActiveCount + 1));
    }

    @Test
    public void testTimeoutDoesNotInactivate() throws Exception {
        CountDownLatch timeoutLatch = new CountDownLatch(2);
        CountDownLatch responseLatch = new CountDownLatch(1);
        recordStore.addResponse("apple.com", A, nextIp());
        recordStore.addResponse("apple.com", A, () -> {
            try {
                responseLatch.await();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            return emptyList();
        });

        ServiceDiscoverer<String, InetAddress, ServiceDiscovererEvent<InetAddress>> discoverer =
                serviceDiscovererBuilder()
                        .queryTimeout(Duration.ofMillis(100))
                        .appendFilter(client -> new ServiceDiscovererFilter<String, InetAddress,
                                ServiceDiscovererEvent<InetAddress>>(client) {
                            @Override
                            public Publisher<ServiceDiscovererEvent<InetAddress>> discover(final String s) {
                                return super.discover(s).whenOnError(t -> {
                                    if (t.getCause() instanceof DnsNameResolverTimeoutException) {
                                        timeoutLatch.countDown();
                                    } else {
                                        throw new RuntimeException("Unexpected exception", t);
                                    }
                                });
                            }
                        })
                        .buildInetDiscoverer();

        try {
            final int expectedActiveCount = 1;
            final int expectedInactiveCount = 0;

            CountDownLatch latch = new CountDownLatch(expectedActiveCount + expectedInactiveCount);
            AtomicReference<Throwable> throwableRef = new AtomicReference<>();
            Publisher<ServiceDiscovererEvent<InetAddress>> publisher = discoverer.discover("apple.com");
            ServiceDiscovererTestSubscriber<InetAddress> subscriber =
                    new ServiceDiscovererTestSubscriber<>(latch, throwableRef, Long.MAX_VALUE);
            toSource(publisher).subscribe(subscriber);

            latch.await();
            assertNull(throwableRef.get());
            assertThat(subscriber.activeCount(), equalTo(expectedActiveCount));
            assertThat(subscriber.inactiveCount(), equalTo(expectedInactiveCount));

            timeoutLatch.await();
            assertNull(throwableRef.get());
            assertThat(subscriber.activeCount(), equalTo(expectedActiveCount));
            assertThat(subscriber.inactiveCount(), equalTo(expectedInactiveCount));
        } finally {
            responseLatch.countDown();
            discoverer.closeAsync().toFuture().get();
        }
    }

    @Test
    public void preferIpv4() throws InterruptedException {
        final String ipv4 = nextIp();
        recordStore.addResponse("apple.com", A, ipv4);
        recordStore.addResponse("apple.com", AAAA, nextIp6());

        final int expectedActiveCount = 2;
        final int expectedInactiveCount = 0;

        CountDownLatch latch = new CountDownLatch(expectedActiveCount + expectedInactiveCount);
        AtomicReference<Throwable> throwableRef = new AtomicReference<>();
        Publisher<ServiceDiscovererEvent<InetAddress>> publisher = discoverer.discover("apple.com");
        final TestSubscriber subscriber = new TestSubscriber(latch);
        toSource(publisher).subscribe(subscriber);

        latch.await();
        assertNull(throwableRef.get());
        assertThat(subscriber.activeEventAddresses.size(), greaterThanOrEqualTo(expectedActiveCount));
        assertThat(subscriber.activeEventAddresses, hasItem(ipv4));
        assertThat(subscriber.inactiveEventAddresses.size(), equalTo(expectedInactiveCount));
    }

    @Test
    public void acceptOnlyIpv6() throws InterruptedException {
        final String ipv6 = nextIp6();
        recordStore.defaultResponse("apple.com", AAAA, ipv6);

        final int expectedActiveCount = 1;
        final int expectedInactiveCount = 0;

        CountDownLatch latch = new CountDownLatch(expectedActiveCount + expectedInactiveCount);
        AtomicReference<Throwable> throwableRef = new AtomicReference<>();
        Publisher<ServiceDiscovererEvent<InetAddress>> publisher = discoverer.discover("apple.com");
        final TestSubscriber subscriber = new TestSubscriber(latch);
        toSource(publisher).subscribe(subscriber);

        latch.await();
        assertNull(throwableRef.get());
        assertThat(subscriber.activeEventAddresses.size(), equalTo(expectedActiveCount));
        assertThat(subscriber.activeEventAddresses.get(0), equalTo(ipv6));
        assertThat(subscriber.inactiveEventAddresses.size(), equalTo(expectedInactiveCount));
    }

    @Ignore("This is failing because of https://github.com/servicetalk/servicetalk/issues/280")
    @SuppressWarnings("unchecked")
    @Test
    public void exceptionInSubscriberOnErrorWhileClose() throws Exception {
        recordStore.defaultResponse("apple.com", A, nextIp());
        CountDownLatch latchOnSubscribe = new CountDownLatch(1);
        ServiceDiscoverer<String, InetAddress, ServiceDiscovererEvent<InetAddress>> discoverer =
                serviceDiscovererBuilderWithoutRetry()
                        .buildInetDiscoverer();
        Subscriber<ServiceDiscovererEvent<InetAddress>> subscriber = mock(Subscriber.class);

        try {
            doAnswer(a -> {
                Subscription s = a.getArgument(0);
                s.request(1);
                latchOnSubscribe.countDown();
                return null;
            }).when(subscriber).onSubscribe(any(Subscription.class));
            doThrow(DELIBERATE_EXCEPTION).when(subscriber).onError(any());

            toSource(discoverer.discover("apple.com")).subscribe(subscriber);
            latchOnSubscribe.await();
        } finally {
            try {
                discoverer.closeAsync().toFuture().get();
                fail("Expected exception");
            } catch (ExecutionException e) {
                assertThat(e.getCause().getCause(), equalTo(DELIBERATE_EXCEPTION));
            }
        }
    }

    private DefaultDnsServiceDiscovererBuilder serviceDiscovererBuilderWithoutRetry() {
        return new DefaultDnsServiceDiscovererBuilder()
                .ioExecutor(nettyIoExecutor)
                .noRetriesOnDnsFailures()
                .dnsResolverAddressTypes(DnsResolverAddressTypes.IPV4_PREFERRED)
                .optResourceEnabled(false)
                .dnsServerAddressStreamProvider(new SingletonDnsServerAddressStreamProvider(
                        new SingletonDnsServerAddresses(dnsServer.localAddress())))
                .ndots(1)
                .minTTL(1);
    }

    private DefaultDnsServiceDiscovererBuilder serviceDiscovererBuilder() {
        return serviceDiscovererBuilderWithoutRetry()
                .appendFilter(serviceDiscoverer -> new RetryingDnsServiceDiscovererFilter(
                        serviceDiscoverer, (i, t) -> globalExecutionContext().executor().timer(Duration.ofSeconds(1))));
    }

    private static class TestSubscriber implements Subscriber<ServiceDiscovererEvent<InetAddress>> {
        private final CountDownLatch latch;
        private final AtomicReference<Throwable> throwableRef = new AtomicReference<>();
        private final List<String> activeEventAddresses = new ArrayList<>();
        private final List<String> inactiveEventAddresses = new ArrayList<>();

        TestSubscriber(final CountDownLatch latch) {
            this.latch = latch;
        }

        @Override
        public void onSubscribe(final Subscription s) {
            s.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(final ServiceDiscovererEvent<InetAddress> event) {
            if (event.isAvailable()) {
                activeEventAddresses.add(event.address().getHostAddress());
            } else {
                inactiveEventAddresses.add(event.address().getHostAddress());
            }
            latch.countDown();
        }

        @Override
        public void onError(final Throwable t) {
            throwableRef.set(t);
            latch.countDown();
        }

        @Override
        public void onComplete() {
            throwableRef.set(new IllegalStateException("Unexpected completion"));
        }
    }
}
