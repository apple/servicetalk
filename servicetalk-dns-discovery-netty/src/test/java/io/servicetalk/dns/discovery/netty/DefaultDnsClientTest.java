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

import io.servicetalk.client.api.ServiceDiscovererEvent;
import io.servicetalk.client.servicediscoverer.ServiceDiscovererTestSubscriber;
import io.servicetalk.concurrent.PublisherSource.Subscriber;
import io.servicetalk.concurrent.PublisherSource.Subscription;
import io.servicetalk.concurrent.api.BiIntFunction;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.transport.netty.internal.EventLoopAwareNettyIoExecutor;

import io.netty.resolver.dns.DnsNameResolverTimeoutException;
import org.apache.directory.server.dns.messages.ResourceRecord;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static io.servicetalk.concurrent.api.Executors.immediate;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.dns.discovery.netty.DnsTestUtils.nextIp;
import static io.servicetalk.dns.discovery.netty.DnsTestUtils.nextIp6;
import static io.servicetalk.dns.discovery.netty.TestRecordStore.DEFAULT_TTL;
import static io.servicetalk.dns.discovery.netty.TestRecordStore.createRecord;
import static io.servicetalk.dns.discovery.netty.TestRecordStore.createSrvRecord;
import static io.servicetalk.transport.netty.NettyIoExecutors.createIoExecutor;
import static io.servicetalk.transport.netty.internal.EventLoopAwareNettyIoExecutors.toEventLoopAwareNettyIoExecutor;
import static java.time.Duration.ofMillis;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.function.Function.identity;
import static org.apache.directory.server.dns.messages.RecordType.A;
import static org.apache.directory.server.dns.messages.RecordType.AAAA;
import static org.apache.directory.server.dns.messages.RecordType.SRV;
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

public class DefaultDnsClientTest {
    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();

    private EventLoopAwareNettyIoExecutor nettyIoExecutor;
    private final TestRecordStore recordStore = new TestRecordStore();
    private TestDnsServer dnsServer;
    private DnsClient client;

    @Before
    public void setup() throws Exception {
        nettyIoExecutor = toEventLoopAwareNettyIoExecutor(createIoExecutor());

        dnsServer = new TestDnsServer(recordStore);
        dnsServer.start();
        client = dnsClientBuilder().build();
    }

    @After
    public void tearDown() throws Exception {
        client.closeAsync().toFuture().get();
        dnsServer.stop();
        nettyIoExecutor.closeAsync().toFuture().get();
    }

    @Test
    public void unknownHostDiscover() throws Exception {
        AtomicReference<Throwable> throwableRef = new AtomicReference<>();
        Publisher<ServiceDiscovererEvent<InetAddress>> publisher = client.dnsQuery("unknown.com")
                .flatMapConcatIterable(identity());
        final CountDownLatch latch = new CountDownLatch(1);
        ServiceDiscovererTestSubscriber<InetAddress> subscriber =
                new ServiceDiscovererTestSubscriber<>(latch, throwableRef, Long.MAX_VALUE);
        toSource(publisher).subscribe(subscriber);

        latch.await();
        assertThat("Unexpected exception during DNS lookup.",
                throwableRef.get(), instanceOf(UnknownHostException.class));
        assertThat(subscriber.activeCount(), equalTo(0));
        assertThat(subscriber.inactiveCount(), equalTo(0));
    }

    @Test
    public void singleSrvSingleADiscover() throws InterruptedException {
        final String domain = "mysvc.apple.com";
        final String targetDomain = "target.mysvc.apple.com";
        final int targetPort = 9876;
        recordStore.addSrvResponse(domain, targetDomain, 10, 10, targetPort);
        recordStore.addResponse(targetDomain, A, nextIp());
        final int expectedActiveCount = 1;
        final int expectedInactiveCount = 0;

        CountDownLatch latch = new CountDownLatch(expectedActiveCount + expectedInactiveCount);
        AtomicReference<Throwable> throwableRef = new AtomicReference<>();
        Publisher<ServiceDiscovererEvent<InetSocketAddress>> publisher = client.dnsSrvQuery(domain)
                .flatMapConcatIterable(identity());
        ServiceDiscovererTestSubscriber<InetSocketAddress> subscriber =
                new ServiceDiscovererTestSubscriber<>(latch, throwableRef, expectedActiveCount);
        toSource(publisher).subscribe(subscriber);

        latch.await();
        assertNull(throwableRef.get());
        assertThat(subscriber.activeCount(), equalTo(expectedActiveCount));
        assertThat(subscriber.inactiveCount(), equalTo(expectedInactiveCount));
    }

    @Ignore("Publisher#flatMapMerge required https://github.com/apple/servicetalk/pull/1011")
    @Test
    public void singleSrvMultipleADiscover() throws InterruptedException {
        final String domain = "mysvc.apple.com";
        final String targetDomain = "target.mysvc.apple.com";
        final int targetPort = 9876;
        recordStore.addSrvResponse(domain, targetDomain, 10, 10, targetPort);
        recordStore.addResponse(targetDomain, A, nextIp(), nextIp());
        final int expectedActiveCount = 2;
        final int expectedInactiveCount = 0;

        CountDownLatch latch = new CountDownLatch(expectedActiveCount + expectedInactiveCount);
        AtomicReference<Throwable> throwableRef = new AtomicReference<>();
        Publisher<ServiceDiscovererEvent<InetSocketAddress>> publisher = client.dnsSrvQuery(domain)
                .flatMapConcatIterable(identity());
        ServiceDiscovererTestSubscriber<InetSocketAddress> subscriber =
                new ServiceDiscovererTestSubscriber<>(latch, throwableRef, expectedActiveCount);
        toSource(publisher).subscribe(subscriber);

        latch.await();
        assertNull(throwableRef.get());
        assertThat(subscriber.activeCount(), equalTo(expectedActiveCount));
        assertThat(subscriber.inactiveCount(), equalTo(expectedInactiveCount));
    }

    @Test
    public void multipleSrvSingleADiscover() throws InterruptedException {
        final String domain = "mysvc.apple.com";
        final String targetDomain1 = "target1.mysvc.apple.com";
        final String targetDomain2 = "target2.mysvc.apple.com";
        final int targetPort1 = 9876;
        final int targetPort2 = 9878;
        recordStore.addSrvResponse(domain, targetDomain1, 10, 10, targetPort1);
        recordStore.addSrvResponse(domain, targetDomain2, 10, 10, targetPort2);
        recordStore.addResponse(targetDomain1, A, nextIp());
        recordStore.addResponse(targetDomain2, A, nextIp());
        final int expectedActiveCount = 2;
        final int expectedInactiveCount = 1;

        CountDownLatch latch = new CountDownLatch(expectedActiveCount + expectedInactiveCount);
        AtomicReference<Throwable> throwableRef = new AtomicReference<>();
        Publisher<ServiceDiscovererEvent<InetSocketAddress>> publisher = client.dnsSrvQuery(domain)
                .flatMapConcatIterable(identity());
        ServiceDiscovererTestSubscriber<InetSocketAddress> subscriber =
                new ServiceDiscovererTestSubscriber<>(latch, throwableRef, Long.MAX_VALUE);
        toSource(publisher).subscribe(subscriber);

        latch.await();
        assertNull(throwableRef.get());
        assertThat(subscriber.activeCount(), equalTo(expectedActiveCount));
        assertThat(subscriber.inactiveCount(), equalTo(expectedInactiveCount));
    }

    @Test
    public void multipleSrvChangeSingleADiscover() throws InterruptedException {
        final String domain = "mysvc.apple.com";
        final String targetDomain1 = "target1.mysvc.apple.com";
        final String targetDomain2 = "target2.mysvc.apple.com";
        final String targetDomain3 = "target3.mysvc.apple.com";
        final int targetPort1 = 9876;
        final int targetPort2 = 9877;
        final int targetPort3 = 9879;
        recordStore.addSrvResponse(domain, targetDomain1, 10, 10, targetPort1);
        List<ResourceRecord> defaultSrvRecords = new ArrayList<>();
        defaultSrvRecords.add(createSrvRecord(domain, targetDomain2, 10, 10, targetPort2, DEFAULT_TTL));
        defaultSrvRecords.add(createSrvRecord(domain, targetDomain3, 10, 10, targetPort3, DEFAULT_TTL));
        recordStore.defaultResponse(domain, SRV, () -> defaultSrvRecords);
        recordStore.addResponse(targetDomain1, A, nextIp());
        recordStore.defaultResponse(targetDomain2, A, nextIp());
        recordStore.defaultResponse(targetDomain3, A, nextIp());
        final int expectedActiveCount = 3;
        final int expectedInactiveCount = 1;

        CountDownLatch latch = new CountDownLatch(expectedActiveCount + expectedInactiveCount);
        AtomicReference<Throwable> throwableRef = new AtomicReference<>();
        Publisher<ServiceDiscovererEvent<InetSocketAddress>> publisher = client.dnsSrvQuery(domain)
                .flatMapConcatIterable(identity());
        ServiceDiscovererTestSubscriber<InetSocketAddress> subscriber =
                new ServiceDiscovererTestSubscriber<>(latch, throwableRef, Long.MAX_VALUE);
        toSource(publisher).subscribe(subscriber);

        latch.await();
        assertNull(throwableRef.get());
        assertThat(subscriber.activeCount(), equalTo(expectedActiveCount));
        assertThat(subscriber.inactiveCount(), equalTo(expectedInactiveCount));
    }

    @Ignore("Publisher#flatMapMerge required https://github.com/apple/servicetalk/pull/1011")
    @Test
    public void multipleSrvMultipleADiscover() throws InterruptedException {
        final String domain = "mysvc.apple.com";
        final String targetDomain1 = "target1.mysvc.apple.com";
        final String targetDomain2 = "target2.mysvc.apple.com";
        final int targetPort1 = 9876;
        final int targetPort2 = 9878;
        recordStore.addSrvResponse(domain, targetDomain1, 10, 10, targetPort1);
        recordStore.addSrvResponse(domain, targetDomain2, 10, 10, targetPort2);
        recordStore.addResponse(targetDomain1, A, nextIp(), nextIp());
        recordStore.addResponse(targetDomain2, A, nextIp(), nextIp());
        final int expectedActiveCount = 4;
        final int expectedInactiveCount = 0;

        CountDownLatch latch = new CountDownLatch(expectedActiveCount + expectedInactiveCount);
        AtomicReference<Throwable> throwableRef = new AtomicReference<>();
        Publisher<ServiceDiscovererEvent<InetSocketAddress>> publisher = client.dnsSrvQuery(domain)
                .flatMapConcatIterable(identity());
        ServiceDiscovererTestSubscriber<InetSocketAddress> subscriber =
                new ServiceDiscovererTestSubscriber<>(latch, throwableRef, Long.MAX_VALUE);
        toSource(publisher).subscribe(subscriber);

        latch.await();
        assertNull(throwableRef.get());
        assertThat(subscriber.activeCount(), equalTo(expectedActiveCount));
        assertThat(subscriber.inactiveCount(), equalTo(expectedInactiveCount));
    }

    @Test
    public void singleDiscover() throws InterruptedException {
        recordStore.addResponse("apple.com", A, nextIp());
        final int expectedActiveCount = 1;
        final int expectedInactiveCount = 0;

        CountDownLatch latch = new CountDownLatch(expectedActiveCount + expectedInactiveCount);
        AtomicReference<Throwable> throwableRef = new AtomicReference<>();
        Publisher<ServiceDiscovererEvent<InetAddress>> publisher = client.dnsQuery("apple.com")
                .flatMapConcatIterable(identity());
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
        Publisher<ServiceDiscovererEvent<InetAddress>> publisher = client.dnsQuery("apple.com")
                .flatMapConcatIterable(identity());
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
        Publisher<ServiceDiscovererEvent<InetAddress>> publisher = client.dnsQuery("apple.com")
                .flatMapConcatIterable(identity());
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
        Publisher<ServiceDiscovererEvent<InetAddress>> publisher = client.dnsQuery("apple.com")
                .flatMapConcatIterable(identity());
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
        Publisher<ServiceDiscovererEvent<InetAddress>> publisher = client.dnsQuery("apple.com")
                .flatMapConcatIterable(identity());
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
        Publisher<ServiceDiscovererEvent<InetAddress>> applePublisher = client.dnsQuery("apple.com")
                .flatMapConcatIterable(identity());
        Publisher<ServiceDiscovererEvent<InetAddress>> stPublisher = client.dnsQuery("servicetalk.io")
                .flatMapConcatIterable(identity());
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
        Publisher<ServiceDiscovererEvent<InetAddress>> publisher = client.dnsQuery("apple.com")
                .flatMapConcatIterable(identity());
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
        Publisher<ServiceDiscovererEvent<InetAddress>> publisher = client.dnsQuery("apple.com")
                .flatMapConcatIterable(identity());
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
        final int expectedInactiveCount = 0;
        final int expectedErrorCount = 1;

        CountDownLatch latch = new CountDownLatch(expectedActiveCount + expectedInactiveCount + expectedErrorCount);
        AtomicReference<Throwable> throwableRef = new AtomicReference<>();
        Publisher<ServiceDiscovererEvent<InetAddress>> publisher = client.dnsQuery("apple.com")
                .flatMapConcatIterable(identity());
        ServiceDiscovererTestSubscriber<InetAddress> subscriber =
                new ServiceDiscovererTestSubscriber<>(latch, throwableRef, Long.MAX_VALUE);
        toSource(publisher).subscribe(subscriber);

        latch.await();
        assertThat("Unexpected exception during DNS lookup.",
                throwableRef.get(), instanceOf(UnknownHostException.class));
        assertThat(subscriber.activeCount(), equalTo(expectedActiveCount));
        assertThat(subscriber.inactiveCount(), equalTo(expectedInactiveCount));
    }

    @Test
    public void repeatDiscoverNxDomainNoSendUnavailable() throws Exception {
        recordStore.addResponse("apple.com", A, nextIp());

        DnsClient customClient = dnsClientBuilder().build();
        try {
            final int expectedActiveCount = 1;
            final int expectedInactiveCount = 0;
            final int expectedErrorCount = 1;

            CountDownLatch latch = new CountDownLatch(expectedActiveCount + expectedInactiveCount + expectedErrorCount);
            AtomicReference<Throwable> throwableRef = new AtomicReference<>();
            Publisher<ServiceDiscovererEvent<InetAddress>> publisher = customClient.dnsQuery("apple.com")
                    .flatMapConcatIterable(identity());
            ServiceDiscovererTestSubscriber<InetAddress> subscriber =
                    new ServiceDiscovererTestSubscriber<>(latch, throwableRef, Long.MAX_VALUE);
            toSource(publisher).subscribe(subscriber);

            latch.await();
            assertThat("Unexpected exception during DNS lookup.",
                    throwableRef.get(), instanceOf(UnknownHostException.class));
            assertThat(subscriber.activeCount(), equalTo(expectedActiveCount));
            assertThat(subscriber.inactiveCount(), equalTo(expectedInactiveCount));
        } finally {
            customClient.closeAsync().toFuture().get();
        }
    }

    @Test
    public void repeatDiscoverNxDomainAndRecover() throws Exception {
        recordStore.addResponse("apple.com", A, nextIp());

        DnsClient customClient = clientBuilderWithRetry().build();
        try {
            final int expectedActiveCount = 1;
            final int expectedInactiveCount = 0;

            CountDownLatch latch1 = new CountDownLatch(expectedActiveCount + expectedInactiveCount);
            CountDownLatch latch2 = new CountDownLatch(expectedActiveCount + expectedInactiveCount + 1);
            AtomicReference<Throwable> throwableRef = new AtomicReference<>();
            Publisher<ServiceDiscovererEvent<InetAddress>> publisher = customClient.dnsQuery("apple.com")
                    .flatMapConcatIterable(identity());
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
        } finally {
            customClient.closeAsync().toFuture().get();
        }
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

        DnsClient filteredClient =
                clientBuilderWithRetry()
                        .queryTimeout(ofMillis(100))
                        .appendFilter(client -> new DnsClientFilter(client) {
                            @Override
                            public Publisher<Collection<ServiceDiscovererEvent<InetAddress>>> dnsQuery(final String s) {
                                return super.dnsQuery(s).whenOnError(t -> {
                                    if (t.getCause() instanceof DnsNameResolverTimeoutException) {
                                        timeoutLatch.countDown();
                                    } else {
                                        throw new RuntimeException("Unexpected exception", t);
                                    }
                                });
                            }
                        }).build();

        try {
            final int expectedActiveCount = 1;
            final int expectedInactiveCount = 0;

            CountDownLatch latch = new CountDownLatch(expectedActiveCount + expectedInactiveCount);
            AtomicReference<Throwable> throwableRef = new AtomicReference<>();
            Publisher<ServiceDiscovererEvent<InetAddress>> publisher = filteredClient.dnsQuery("apple.com")
                    .flatMapConcatIterable(identity());
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
            filteredClient.closeAsync().toFuture().get();
        }
    }

    @Test
    public void preferIpv4() throws InterruptedException {
        final String ipv4 = nextIp();
        recordStore.addResponse("apple.com", A, ipv4);
        recordStore.addResponse("apple.com", AAAA, nextIp6());

        // We at least expect 1 as we prefer ipv4 but depending on how fast we resolve we may also have 2.
        final int expectedActiveCount = 1;
        final int expectedInactiveCount = 0;

        CountDownLatch latch = new CountDownLatch(expectedActiveCount + expectedInactiveCount);
        AtomicReference<Throwable> throwableRef = new AtomicReference<>();
        Publisher<ServiceDiscovererEvent<InetAddress>> publisher = client.dnsQuery("apple.com")
                .flatMapConcatIterable(identity());
        final TestSubscriber subscriber = new TestSubscriber(latch);
        toSource(publisher).subscribe(subscriber);

        latch.await();
        assertNull(throwableRef.get());

        // We must receive at least 1 as we prefer A records.
        assertThat(subscriber.activeEventAddresses.size(), greaterThanOrEqualTo(1));
        assertThat(subscriber.activeEventAddresses, hasItem(ipv4));
        assertThat(subscriber.inactiveEventAddresses.size(), equalTo(expectedInactiveCount));
    }

    @Test
    public void preferIpv4ButOnlyAAAARecordIsPresent() throws InterruptedException {
        final String ipv6 = nextIp6();
        recordStore.addResponse("apple.com", AAAA, ipv6);

        final int expectedActiveCount = 1;
        final int expectedInactiveCount = 0;

        CountDownLatch latch = new CountDownLatch(expectedActiveCount + expectedInactiveCount);
        AtomicReference<Throwable> throwableRef = new AtomicReference<>();
        Publisher<ServiceDiscovererEvent<InetAddress>> publisher = client.dnsQuery("apple.com")
                .flatMapConcatIterable(identity());
        final TestSubscriber subscriber = new TestSubscriber(latch);
        toSource(publisher).subscribe(subscriber);

        latch.await();
        assertNull(throwableRef.get());

        assertThat(subscriber.activeEventAddresses.size(), equalTo(1));
        assertThat(subscriber.activeEventAddresses, hasItem(ipv6));
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
        Publisher<ServiceDiscovererEvent<InetAddress>> publisher = client.dnsQuery("apple.com")
                .flatMapConcatIterable(identity());
        final TestSubscriber subscriber = new TestSubscriber(latch);
        toSource(publisher).subscribe(subscriber);

        latch.await();
        assertNull(throwableRef.get());
        assertThat(subscriber.activeEventAddresses.size(), equalTo(expectedActiveCount));
        assertThat(subscriber.activeEventAddresses.get(0), equalTo(ipv6));
        assertThat(subscriber.inactiveEventAddresses.size(), equalTo(expectedInactiveCount));
    }

    @Ignore("This is failing because of https://github.com/apple/servicetalk/issues/280")
    @SuppressWarnings("unchecked")
    @Test
    public void exceptionInSubscriberOnErrorWhileClose() throws Exception {
        recordStore.defaultResponse("apple.com", A, nextIp());
        CountDownLatch latchOnSubscribe = new CountDownLatch(1);
        DnsClient noRetryClient = dnsClientBuilder().build();
        Subscriber<ServiceDiscovererEvent<InetAddress>> subscriber = mock(Subscriber.class);

        try {
            doAnswer(a -> {
                Subscription s = a.getArgument(0);
                s.request(1);
                latchOnSubscribe.countDown();
                return null;
            }).when(subscriber).onSubscribe(any(Subscription.class));
            doThrow(DELIBERATE_EXCEPTION).when(subscriber).onError(any());

            toSource(noRetryClient.dnsQuery("apple.com").flatMapConcatIterable(identity()))
                    .subscribe(subscriber);
            latchOnSubscribe.await();
        } finally {
            try {
                noRetryClient.closeAsync().toFuture().get();
                fail("Expected exception");
            } catch (ExecutionException e) {
                assertThat(e.getCause().getCause(), equalTo(DELIBERATE_EXCEPTION));
            }
        }
    }

    private DefaultDnsServiceDiscovererBuilder dnsClientBuilder() {
        return new DefaultDnsServiceDiscovererBuilder()
                .ioExecutor(nettyIoExecutor)
                .dnsResolverAddressTypes(DnsResolverAddressTypes.IPV4_PREFERRED)
                .optResourceEnabled(false)
                .dnsServerAddressStreamProvider(new SingletonDnsServerAddressStreamProvider(
                        new SingletonDnsServerAddresses(dnsServer.localAddress())))
                .ndots(1)
                .minTTL(1);
    }

    private DefaultDnsServiceDiscovererBuilder clientBuilderWithRetry() {
        final BiIntFunction<Throwable, ? extends Completable> retryStrategy = (i, t) -> immediate().timer(ofMillis(50));
        return dnsClientBuilder()
                .appendFilter(client -> new DnsClientFilter(client) {
                    @Override
                    public Publisher<Collection<ServiceDiscovererEvent<InetAddress>>> dnsQuery(final String hostName) {
                        return super.dnsQuery(hostName).retryWhen(retryStrategy);
                    }

                    @Override
                    public Publisher<Collection<ServiceDiscovererEvent<InetSocketAddress>>> dnsSrvQuery(
                            final String serviceName) {
                        return super.dnsSrvQuery(serviceName).retryWhen(retryStrategy);
                    }
                });
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
