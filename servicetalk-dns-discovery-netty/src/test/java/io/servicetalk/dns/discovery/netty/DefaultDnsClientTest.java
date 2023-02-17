/*
 * Copyright © 2018-2020 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.client.api.DefaultServiceDiscovererEvent;
import io.servicetalk.client.api.ServiceDiscovererEvent;
import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.PublisherSource.Subscriber;
import io.servicetalk.concurrent.PublisherSource.Subscription;
import io.servicetalk.concurrent.api.BiIntFunction;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.ExecutorExtension;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.TestExecutor;
import io.servicetalk.concurrent.test.internal.TestPublisherSubscriber;
import io.servicetalk.transport.netty.internal.EventLoopAwareNettyIoExecutor;

import io.netty.channel.EventLoopGroup;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import javax.annotation.Nullable;

import static io.servicetalk.client.api.ServiceDiscovererEvent.Status.AVAILABLE;
import static io.servicetalk.client.api.ServiceDiscovererEvent.Status.EXPIRED;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.dns.discovery.netty.DnsResolverAddressTypes.IPV4_ONLY;
import static io.servicetalk.dns.discovery.netty.DnsResolverAddressTypes.IPV4_PREFERRED;
import static io.servicetalk.dns.discovery.netty.DnsResolverAddressTypes.IPV4_PREFERRED_RETURN_ALL;
import static io.servicetalk.dns.discovery.netty.DnsResolverAddressTypes.IPV6_ONLY;
import static io.servicetalk.dns.discovery.netty.DnsResolverAddressTypes.IPV6_PREFERRED;
import static io.servicetalk.dns.discovery.netty.DnsTestUtils.nextIp;
import static io.servicetalk.dns.discovery.netty.DnsTestUtils.nextIp6;
import static io.servicetalk.dns.discovery.netty.TestRecordStore.createCnameRecord;
import static io.servicetalk.dns.discovery.netty.TestRecordStore.createSrvRecord;
import static io.servicetalk.transport.netty.internal.NettyIoExecutors.createIoExecutor;
import static java.net.InetAddress.getByName;
import static java.time.Duration.ofMillis;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.function.Function.identity;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

class DefaultDnsClientTest {
    private static final int DEFAULT_TTL = 1;

    @RegisterExtension
    static final ExecutorExtension<TestExecutor> timerExecutor = ExecutorExtension.withTestExecutor()
            .setClassLevel(true);

    @RegisterExtension
    static final ExecutorExtension<EventLoopAwareNettyIoExecutor> ioExecutor = ExecutorExtension
            .withExecutor(() -> createIoExecutor(1))
            .setClassLevel(true);

    private final TestRecordStore recordStore = new TestRecordStore();
    private TestDnsServer dnsServer;
    private TestDnsServer dnsServer2;
    private DefaultDnsClient client;

    void setup() throws Exception {
        setup(UnaryOperator.identity());
    }

    @SuppressWarnings("PMD.AvoidUsingHardCodedIP")
    void setup(UnaryOperator<DefaultDnsServiceDiscovererBuilder> builderFunction) throws Exception {
        dnsServer = new TestDnsServer(recordStore);
        dnsServer.start();

        // Try to bind IPv6 for variety, if not fallback to IPv4
        try {
            dnsServer2 = new TestDnsServer(new TestRecordStore(), new InetSocketAddress("::1", 0));
            dnsServer2.start();
        } catch (Throwable cause) {
            if (dnsServer2 != null) {
                dnsServer2.stop();
            }
            dnsServer2 = new TestDnsServer(new TestRecordStore());
            dnsServer2.start();
        }

        client = (DefaultDnsClient) builderFunction.apply(dnsClientBuilder()).build();
    }

    @AfterEach
    public void tearDown() throws Exception {
        client.closeAsync().toFuture().get();
        dnsServer.stop();
        dnsServer2.stop();
    }

    private void advanceTime() throws Exception {
        advanceTime(DEFAULT_TTL);
    }

    private void advanceTime(int ttl) throws Exception {
        // Netty schedules cache invalidation on the EventLoop, using real time. Because we schedule subsequent
        // resolutions on TestExecutor, we need to clear the cache manually before we advance time.
        ioExecutor.executor().submit(() -> client.ttlCache().clear()).toFuture().get();
        // Add one more second to make sure we cover the jitter.
        timerExecutor.executor().advanceTimeBy(ttl + 1, SECONDS);
    }

    static Stream<ServiceDiscovererEvent.Status> missingRecordStatus() {
        return Stream.of(ServiceDiscovererEvent.Status.EXPIRED, ServiceDiscovererEvent.Status.UNAVAILABLE);
    }

    @Test
    void singleSrvSingleADiscover() throws Exception {
        setup();
        final String domain = "mysvc.apple.com";
        final String targetDomain = "target.mysvc.apple.com";
        final int targetPort = 9876;
        final String ip = nextIp();
        recordStore.addSrv(domain, targetDomain, targetPort, DEFAULT_TTL);
        recordStore.addIPv4Address(targetDomain, DEFAULT_TTL, ip);

        TestPublisherSubscriber<ServiceDiscovererEvent<InetSocketAddress>> subscriber = dnsSrvQuery(domain);
        Subscription subscription = subscriber.awaitSubscription();
        subscription.request(1);

        assertEvent(subscriber.takeOnNext(), ip, targetPort, AVAILABLE);
    }

    @Test
    void singleSrvMultipleADiscover() throws Exception {
        setup();
        final String domain = "mysvc.apple.com";
        final String targetDomain = "target.mysvc.apple.com";
        final int targetPort = 9876;
        final String ip1 = nextIp();
        final String ip2 = nextIp();
        recordStore.addSrv(domain, targetDomain, targetPort, DEFAULT_TTL);
        recordStore.addIPv4Address(targetDomain, DEFAULT_TTL, ip1, ip2);

        TestPublisherSubscriber<ServiceDiscovererEvent<InetSocketAddress>> subscriber = dnsSrvQuery(domain);
        Subscription subscription = subscriber.awaitSubscription();
        subscription.request(2);

        List<ServiceDiscovererEvent<InetSocketAddress>> signals = subscriber.takeOnNext(2);
        assertHasEvent(signals, ip1, targetPort, AVAILABLE);
        assertHasEvent(signals, ip2, targetPort, AVAILABLE);
    }

    @Test
    void multipleSrvSingleADiscover() throws Exception {
        setup();
        final String domain = "mysvc.apple.com";
        final String targetDomain1 = "target1.mysvc.apple.com";
        final String targetDomain2 = "target2.mysvc.apple.com";
        final int targetPort1 = 9876;
        final int targetPort2 = 9878;
        final String ip1 = nextIp();
        final String ip2 = nextIp();
        recordStore.addSrv(domain, targetDomain1, targetPort1, DEFAULT_TTL);
        recordStore.addSrv(domain, targetDomain2, targetPort2, DEFAULT_TTL);
        recordStore.addIPv4Address(targetDomain1, DEFAULT_TTL, ip1);
        recordStore.addIPv4Address(targetDomain2, DEFAULT_TTL, ip2);

        TestPublisherSubscriber<ServiceDiscovererEvent<InetSocketAddress>> subscriber = dnsSrvQuery(domain);
        Subscription subscription = subscriber.awaitSubscription();
        subscription.request(2);

        List<ServiceDiscovererEvent<InetSocketAddress>> signals = subscriber.takeOnNext(2);
        assertHasEvent(signals, ip1, targetPort1, AVAILABLE);
        assertHasEvent(signals, ip2, targetPort2, AVAILABLE);
    }

    @ParameterizedTest(name = "{displayName} [{index}] missingRecordStatus={0}")
    @MethodSource("missingRecordStatus")
    void multipleSrvChangeSingleADiscover(ServiceDiscovererEvent.Status missingRecordStatus) throws Exception {
        setup(builder -> builder.missingRecordStatus(missingRecordStatus));
        final String domain = "mysvc.apple.com";
        final String targetDomain1 = "target1.mysvc.apple.com";
        final String targetDomain2 = "target2.mysvc.apple.com";
        final String targetDomain3 = "target3.mysvc.apple.com";
        final int targetPort1 = 9876;
        final int targetPort2 = 9877;
        final int targetPort3 = 9879;
        final String ip1 = nextIp();
        final String ip2 = nextIp();
        final String ip3 = nextIp();
        recordStore.addSrv(domain, targetDomain1, targetPort1, DEFAULT_TTL);
        recordStore.addSrv(domain, targetDomain2, targetPort2, 1);
        recordStore.addSrv(domain, targetDomain3, targetPort3, DEFAULT_TTL);
        recordStore.addIPv4Address(targetDomain1, DEFAULT_TTL, ip1);
        recordStore.addIPv4Address(targetDomain2, DEFAULT_TTL, ip2);
        recordStore.addIPv4Address(targetDomain3, DEFAULT_TTL, ip3);

        TestPublisherSubscriber<ServiceDiscovererEvent<InetSocketAddress>> subscriber = dnsSrvQuery(domain);
        Subscription subscription = subscriber.awaitSubscription();
        subscription.request(4);

        List<ServiceDiscovererEvent<InetSocketAddress>> signals = subscriber.takeOnNext(3);
        assertHasEvent(signals, ip1, targetPort1, AVAILABLE);
        assertHasEvent(signals, ip2, targetPort2, AVAILABLE);
        assertHasEvent(signals, ip3, targetPort3, AVAILABLE);

        recordStore.removeSrv(domain, targetDomain2, targetPort2, 1);
        advanceTime();
        assertEvent(subscriber.takeOnNext(), ip2, targetPort2, missingRecordStatus);
    }

    @Test
    void multipleSrvMultipleADiscover() throws Exception {
        setup();
        final String domain = "mysvc.apple.com";
        final String targetDomain1 = "target1.mysvc.apple.com";
        final String targetDomain2 = "target2.mysvc.apple.com";
        final int targetPort1 = 9876;
        final int targetPort2 = 9878;
        final String ip1 = nextIp();
        final String ip2 = nextIp();
        final String ip3 = nextIp();
        final String ip4 = nextIp();
        recordStore.addSrv(domain, targetDomain1, targetPort1, DEFAULT_TTL);
        recordStore.addSrv(domain, targetDomain2, targetPort2, DEFAULT_TTL);
        recordStore.addIPv4Address(targetDomain1, DEFAULT_TTL, ip1, ip2);
        recordStore.addIPv4Address(targetDomain2, DEFAULT_TTL, ip3, ip4);

        TestPublisherSubscriber<ServiceDiscovererEvent<InetSocketAddress>> subscriber = dnsSrvQuery(domain);
        Subscription subscription = subscriber.awaitSubscription();
        subscription.request(4);

        List<ServiceDiscovererEvent<InetSocketAddress>> signals = subscriber.takeOnNext(4);
        assertHasEvent(signals, ip1, targetPort1, AVAILABLE);
        assertHasEvent(signals, ip2, targetPort1, AVAILABLE);
        assertHasEvent(signals, ip3, targetPort2, AVAILABLE);
        assertHasEvent(signals, ip4, targetPort2, AVAILABLE);
    }

    @Test
    void srvWithCNAMEEntryLowerTTLDoesNotFail() throws Exception {
        setup();
        final String domain = "sd.servicetalk.io";
        final String srvCNAME = "sdcname.servicetalk.io";
        final String targetDomain1 = "target1.mysvc.servicetalk.io";
        final int targetPort = 9876;
        final String ip1 = nextIp();
        final int ttl = DEFAULT_TTL + 2;
        recordStore.addCNAME(domain, srvCNAME, ttl);
        recordStore.addSrv(domain, targetDomain1, targetPort, ttl);
        recordStore.addSrv(srvCNAME, targetDomain1, targetPort, DEFAULT_TTL);
        recordStore.addIPv4Address(targetDomain1, ttl, ip1);

        TestPublisherSubscriber<ServiceDiscovererEvent<InetSocketAddress>> subscriber = dnsSrvQuery(domain);
        Subscription subscription = subscriber.awaitSubscription();
        subscription.request(10);

        assertEvent(subscriber.takeOnNext(), ip1, targetPort, AVAILABLE);
        recordStore.removeSrv(srvCNAME, targetDomain1, targetPort, 1);
        advanceTime(ttl);
        assertNull(subscriber.pollTerminal(50, MILLISECONDS));
    }

    @ParameterizedTest(name = "{displayName} [{index}] inactiveEventsOnError={0}")
    @ValueSource(booleans = {false, true})
    void srvCNAMEDuplicateAddresses(boolean inactiveEventsOnError) throws Exception {
        setup(builder -> builder
                .dnsServerAddressStreamProvider(new SequentialDnsServerAddressStreamProvider(
                        dnsServer2.localAddress(), dnsServer.localAddress()))
                .inactiveEventsOnError(inactiveEventsOnError));
        final String domain = "sd.servicetalk.io";
        final String srvCNAME = "sdcname.servicetalk.io";
        final String targetDomain1 = "target1.mysvc.servicetalk.io";
        final String targetDomain2 = "target2.mysvc.servicetalk.io";
        final int targetPort = 9876;
        final String ip1 = nextIp();
        final String ip2 = nextIp();
        final int ttl = DEFAULT_TTL + 10;
        recordStore.addCNAME(domain, srvCNAME, ttl);
        recordStore.addSrv(domain, targetDomain1, targetPort, ttl);
        recordStore.addSrv(domain, targetDomain2, targetPort, ttl);
        recordStore.addSrv(srvCNAME, targetDomain1, targetPort, 1);
        recordStore.addSrv(srvCNAME, targetDomain2, targetPort, 1);
        recordStore.addIPv4Address(targetDomain1, ttl, ip1);
        recordStore.addIPv4Address(targetDomain2, ttl, ip2);

        TestPublisherSubscriber<ServiceDiscovererEvent<InetSocketAddress>> subscriber = dnsSrvQuery(domain);
        Subscription subscription = subscriber.awaitSubscription();
        subscription.request(10);

        List<ServiceDiscovererEvent<InetSocketAddress>> signals = subscriber.takeOnNext(2);
        assertHasEvent(signals, ip1, targetPort, AVAILABLE);
        assertHasEvent(signals, ip2, targetPort, AVAILABLE);

        // Atomically remove all domain records.
        recordStore.removeRecords(
                createCnameRecord(domain, srvCNAME, ttl),
                createSrvRecord(domain, targetDomain1, targetPort, ttl),
                createSrvRecord(domain, targetDomain2, targetPort, ttl));

        advanceTime();
        if (inactiveEventsOnError) {
            signals = subscriber.takeOnNext(2);
            assertHasEvent(signals, ip1, targetPort, EXPIRED);
            assertHasEvent(signals, ip2, targetPort, EXPIRED);
        }
        assertThat(subscriber.awaitOnError(), instanceOf(UnknownHostException.class));
    }

    @ParameterizedTest(name = "{displayName} [{index}] missingRecordStatus={0}")
    @MethodSource("missingRecordStatus")
    void srvInactiveEventsAggregated(ServiceDiscovererEvent.Status missingRecordStatus) throws Exception {
        setup(builder -> builder.inactiveEventsOnError(true).missingRecordStatus(missingRecordStatus));
        final String domain = "sd.servicetalk.io";
        final String targetDomain1 = "target1.mysvc.servicetalk.io";
        final String targetDomain2 = "target2.mysvc.servicetalk.io";
        final String targetDomain3 = "target3.mysvc.servicetalk.io";
        final int targetPort = 9876;
        final String ip1 = nextIp();
        final String ip2 = nextIp();
        final String ip3 = nextIp();
        recordStore.addSrv(domain, targetDomain1, targetPort, DEFAULT_TTL);
        recordStore.addSrv(domain, targetDomain2, targetPort, DEFAULT_TTL);
        recordStore.addSrv(domain, targetDomain3, targetPort, DEFAULT_TTL);
        recordStore.addIPv4Address(targetDomain1, DEFAULT_TTL, ip1);
        recordStore.addIPv4Address(targetDomain2, DEFAULT_TTL, ip2);
        recordStore.addIPv4Address(targetDomain3, DEFAULT_TTL, ip3);

        Publisher<Collection<ServiceDiscovererEvent<InetSocketAddress>>> publisher = client.dnsSrvQuery(domain);
        TestPublisherSubscriber<Collection<ServiceDiscovererEvent<InetSocketAddress>>> subscriber =
                new TestPublisherSubscriber<>();
        toSource(publisher).subscribe(subscriber);

        Subscription subscription = subscriber.awaitSubscription();
        subscription.request(10);

        List<ServiceDiscovererEvent<InetSocketAddress>> signals = new ArrayList<>();
        do {
            Collection<ServiceDiscovererEvent<InetSocketAddress>> next = subscriber.takeOnNext();
            assertNotNull(next);
            signals.addAll(next);
        } while (signals.size() != 3);

        assertHasEvent(signals, ip1, targetPort, AVAILABLE);
        assertHasEvent(signals, ip2, targetPort, AVAILABLE);
        assertHasEvent(signals, ip3, targetPort, AVAILABLE);

        // Atomically remove all the SRV records, the next resolution should result in a host not found exception.
        recordStore.removeRecords(
                createSrvRecord(domain, targetDomain1, targetPort, DEFAULT_TTL),
                createSrvRecord(domain, targetDomain2, targetPort, DEFAULT_TTL),
                createSrvRecord(domain, targetDomain3, targetPort, DEFAULT_TTL));
        advanceTime();
        Collection<ServiceDiscovererEvent<InetSocketAddress>> next = subscriber.takeOnNext();
        assertNotNull(next);
        assertHasEvent(next, ip1, targetPort, missingRecordStatus);
        assertHasEvent(next, ip2, targetPort, missingRecordStatus);
        assertHasEvent(next, ip3, targetPort, missingRecordStatus);
        assertThat(subscriber.awaitOnError(), instanceOf(UnknownHostException.class));
    }

    @Test
    void srvRecordRemovalPropagatesError() throws Exception {
        setup();
        final String domain = "sd.servicetalk.io";
        final String targetDomain1 = "target1.mysvc.servicetalk.io";
        final String targetDomain2 = "target2.mysvc.servicetalk.io";
        final int targetPort = 9876;
        final String ip1 = nextIp();
        final String ip2 = nextIp();
        recordStore.addSrv(domain, targetDomain1, targetPort, DEFAULT_TTL);
        recordStore.addSrv(domain, targetDomain2, targetPort, DEFAULT_TTL);
        recordStore.addIPv4Address(targetDomain1, DEFAULT_TTL + 10, ip1);
        recordStore.addIPv4Address(targetDomain2, DEFAULT_TTL + 10, ip2);

        TestPublisherSubscriber<ServiceDiscovererEvent<InetSocketAddress>> subscriber = dnsSrvQuery(domain);
        Subscription subscription = subscriber.awaitSubscription();
        subscription.request(10);

        List<ServiceDiscovererEvent<InetSocketAddress>> signals = subscriber.takeOnNext(2);
        assertHasEvent(signals, ip1, targetPort, AVAILABLE);
        assertHasEvent(signals, ip2, targetPort, AVAILABLE);

        // Atomically remove all the SRV records, the next resolution should result in a host not found exception.
        recordStore.removeRecords(
                createSrvRecord(domain, targetDomain1, targetPort, DEFAULT_TTL),
                createSrvRecord(domain, targetDomain2, targetPort, DEFAULT_TTL));
        advanceTime();
        assertThat(subscriber.awaitOnError(), instanceOf(UnknownHostException.class));
    }

    @ParameterizedTest(name = "{displayName} [{index}] srvFilterDuplicateEvents={0}")
    @ValueSource(booleans = {false, true})
    void srvDuplicateAddresses(boolean srvFilterDuplicateEvents) throws Exception {
        setup(builder -> builder.srvFilterDuplicateEvents(srvFilterDuplicateEvents));
        final String domain = "sd.servicetalk.io";
        final String targetDomain1 = "target1.mysvc.servicetalk.io";
        final String targetDomain2 = "target2.mysvc.servicetalk.io";
        final int targetPort = 9876;
        final String ip1 = nextIp();
        final int ttl = DEFAULT_TTL + 10;
        recordStore.addSrv(domain, targetDomain1, targetPort, ttl);
        recordStore.addSrv(domain, targetDomain2, targetPort, 1);
        recordStore.addIPv4Address(targetDomain1, 1, ip1);
        recordStore.addIPv4Address(targetDomain2, 1, ip1);

        TestPublisherSubscriber<ServiceDiscovererEvent<InetSocketAddress>> subscriber = dnsSrvQuery(domain);
        Subscription subscription = subscriber.awaitSubscription();
        subscription.request(10);

        assertEvent(subscriber.takeOnNext(), ip1, targetPort, AVAILABLE);
        if (srvFilterDuplicateEvents) {
            assertThat(subscriber.pollOnNext(50, MILLISECONDS), is(nullValue()));
            recordStore.removeIPv4Address(targetDomain1, 1, ip1);
            advanceTime();
            assertThat(subscriber.pollOnNext(50, MILLISECONDS), is(nullValue()));
        } else {
            assertEvent(subscriber.takeOnNext(), ip1, targetPort, AVAILABLE);
            recordStore.removeIPv4Address(targetDomain1, 1, ip1);
            advanceTime();
            assertEvent(subscriber.takeOnNext(), ip1, targetPort, EXPIRED);
        }
        recordStore.removeIPv4Address(targetDomain2, 1, ip1);
        advanceTime();
        assertEvent(subscriber.takeOnNext(), ip1, targetPort, EXPIRED);
    }

    @ParameterizedTest(name = "{displayName} [{index}] inactiveEventsOnError={0}")
    @ValueSource(booleans = {false, true})
    void srvAAAAFailsGeneratesInactive(boolean inactiveEventsOnError) throws Exception {
        setup(builder -> builder
                .inactiveEventsOnError(inactiveEventsOnError)
                .srvHostNameRepeatDelay(ofMillis(200), ofMillis(10))
                .dnsResolverAddressTypes(IPV4_PREFERRED));
        final String domain = "sd.servicetalk.io";
        final String targetDomain1 = "target1.mysvc.servicetalk.io";
        final String targetDomain2 = "target2.mysvc.servicetalk.io";
        final int targetPort = 9876;
        final String ip1 = nextIp6();
        final String ip2 = nextIp();
        final int ttl = DEFAULT_TTL + 10;
        recordStore.addIPv6Address(targetDomain1, DEFAULT_TTL, ip1);
        recordStore.addIPv4Address(targetDomain2, ttl, ip2);
        recordStore.addSrv(domain, targetDomain1, targetPort, ttl);
        recordStore.addSrv(domain, targetDomain2, targetPort, ttl);

        TestPublisherSubscriber<ServiceDiscovererEvent<InetSocketAddress>> subscriber = dnsSrvQuery(domain);
        Subscription subscription = subscriber.awaitSubscription();
        subscription.request(Long.MAX_VALUE);

        List<ServiceDiscovererEvent<InetSocketAddress>> signals = subscriber.takeOnNext(2);
        assertHasEvent(signals, ip1, targetPort, AVAILABLE);
        assertHasEvent(signals, ip2, targetPort, AVAILABLE);

        recordStore.removeIPv6Address(targetDomain1, DEFAULT_TTL, ip1);
        advanceTime();
        assertEvent(subscriber.takeOnNext(), ip1, targetPort, EXPIRED);

        recordStore.addIPv6Address(targetDomain1, DEFAULT_TTL, ip1);
        advanceTime();
        assertEvent(subscriber.takeOnNext(), ip1, targetPort, AVAILABLE);
    }

    @ParameterizedTest(name = "{displayName} [{index}] inactiveEventsOnError={0}")
    @ValueSource(booleans = {false, true})
    void srvRecordFailsGeneratesInactive(boolean inactiveEventsOnError) throws Exception {
        setup(builder -> builder.inactiveEventsOnError(inactiveEventsOnError));
        final String domain = "sd.servicetalk.io";
        final String targetDomain1 = "target1.mysvc.servicetalk.io";
        final String targetDomain2 = "target2.mysvc.servicetalk.io";
        final int targetPort = 9876;
        final String ip1 = nextIp();
        final String ip2 = nextIp();
        final int ttl = DEFAULT_TTL + 10;
        recordStore.addIPv4Address(targetDomain1, ttl, ip1);
        recordStore.addIPv4Address(targetDomain2, ttl, ip2);
        recordStore.addSrv(domain, targetDomain1, targetPort, DEFAULT_TTL);
        recordStore.addSrv(domain, targetDomain2, targetPort, DEFAULT_TTL);

        TestPublisherSubscriber<ServiceDiscovererEvent<InetSocketAddress>> subscriber = dnsSrvQuery(domain);
        Subscription subscription = subscriber.awaitSubscription();
        subscription.request(10);

        List<ServiceDiscovererEvent<InetSocketAddress>> signals = subscriber.takeOnNext(2);
        assertHasEvent(signals, ip1, targetPort, AVAILABLE);
        assertHasEvent(signals, ip2, targetPort, AVAILABLE);

        recordStore.removeSrv(domain, targetDomain1, targetPort, DEFAULT_TTL);
        advanceTime();
        assertEvent(subscriber.takeOnNext(), ip1, targetPort, EXPIRED);

        recordStore.removeSrv(domain, targetDomain2, targetPort, DEFAULT_TTL);
        advanceTime();
        if (inactiveEventsOnError) {
            assertEvent(subscriber.takeOnNext(), ip2, targetPort, EXPIRED);
        }
        assertThat(subscriber.awaitOnError(), instanceOf(UnknownHostException.class));
    }

    @Test
    void unknownHostDiscover() throws Exception {
        setup();
        TestPublisherSubscriber<ServiceDiscovererEvent<InetAddress>> subscriber = dnsQuery("unknown.com");
        Subscription subscription = subscriber.awaitSubscription();
        subscription.request(Long.MAX_VALUE);

        assertThat(subscriber.awaitOnError(), instanceOf(UnknownHostException.class));
    }

    @Test
    void singleADiscover() throws Exception {
        setup();
        final String ip = nextIp();
        final String domain = "servicetalk.io";
        recordStore.addIPv4Address(domain, DEFAULT_TTL, ip);

        TestPublisherSubscriber<ServiceDiscovererEvent<InetAddress>> subscriber = dnsQuery(domain);
        Subscription subscription = subscriber.awaitSubscription();
        subscription.request(1);

        assertEvent(subscriber.takeOnNext(), ip, AVAILABLE);

        // Remove the ip
        recordStore.removeIPv4Address(domain, DEFAULT_TTL, ip);
        subscription.request(1);
        advanceTime();
        assertThat(subscriber.awaitOnError(), instanceOf(UnknownHostException.class));
    }

    @Test
    void singleDiscoverMultipleRecords() throws Exception {
        setup();
        final String domain = "servicetalk.io";
        final String[] ips = new String[] {nextIp(), nextIp(), nextIp(), nextIp(), nextIp()};
        recordStore.addIPv4Address(domain, DEFAULT_TTL, ips);

        TestPublisherSubscriber<ServiceDiscovererEvent<InetAddress>> subscriber = dnsQuery(domain);
        Subscription subscription = subscriber.awaitSubscription();
        subscription.request(ips.length);
        List<ServiceDiscovererEvent<InetAddress>> signals = subscriber.takeOnNext(ips.length);
        for (String ip : ips) {
            assertHasEvent(signals, ip, AVAILABLE);
        }

        // Remove all the ips
        recordStore.removeIPv4Address(domain, DEFAULT_TTL, ips);
        subscription.request(1);
        advanceTime();
        assertThat(subscriber.awaitOnError(), instanceOf(UnknownHostException.class));
    }

    @Test
    void singleDiscoverDuplicateRecords() throws Exception {
        setup();
        final String dupIp = nextIp();
        final String domain = "servicetalk.io";
        final String[] ips = new String[] {nextIp(), nextIp(), dupIp, dupIp, nextIp()};
        recordStore.addIPv4Address(domain, DEFAULT_TTL, ips);

        TestPublisherSubscriber<ServiceDiscovererEvent<InetAddress>> subscriber = dnsQuery(domain);
        Subscription subscription = subscriber.awaitSubscription();
        subscription.request(ips.length);
        List<ServiceDiscovererEvent<InetAddress>> signals = subscriber.takeOnNext(ips.length - 1);

        boolean assertedDup = false;
        for (String ip : ips) {
            if (ip.equals(dupIp)) {
                if (!assertedDup) {
                    assertedDup = true;
                    assertHasEvent(signals, ip, AVAILABLE);
                }
            } else {
                assertHasEvent(signals, ip, AVAILABLE);
            }
        }
    }

    @Test
    void repeatDiscoverMultipleRecords() throws Exception {
        setup();
        final String domain = "servicetalk.io";
        final String[] ips = new String[] {nextIp(), nextIp(), nextIp(), nextIp(), nextIp()};
        recordStore.addIPv4Address(domain, DEFAULT_TTL, ips);

        TestPublisherSubscriber<ServiceDiscovererEvent<InetAddress>> subscriber = dnsQuery(domain);
        Subscription subscription = subscriber.awaitSubscription();
        subscription.request(ips.length);
        List<ServiceDiscovererEvent<InetAddress>> signals = subscriber.takeOnNext(ips.length);
        for (String ip : ips) {
            assertHasEvent(signals, ip, AVAILABLE);
        }

        final String[] ips2 = new String[] {nextIp(), nextIp(), nextIp(), nextIp(), nextIp()};
        recordStore.addIPv4Address(domain, DEFAULT_TTL, ips2);
        subscription.request(ips2.length);
        advanceTime();
        signals = subscriber.takeOnNext(ips2.length);
        for (String ip : ips2) {
            assertHasEvent(signals, ip, AVAILABLE);
        }

        // Remove all the IPs
        recordStore.removeIPv4Address(domain, DEFAULT_TTL, ips);
        recordStore.removeIPv4Address(domain, DEFAULT_TTL, ips2);
        subscription.request(1);
        advanceTime();
        assertThat(subscriber.awaitOnError(), instanceOf(UnknownHostException.class));
    }

    @Test
    void repeatDiscoverMultipleHosts() throws Exception {
        setup();
        final String ip1 = nextIp();
        final String domain1 = "servicetalk.io";
        final String ip2 = nextIp();
        final String domain2 = "backup.servicetalk.io";
        recordStore.addIPv4Address(domain1, DEFAULT_TTL, ip1);
        recordStore.addIPv4Address(domain2, DEFAULT_TTL, ip2);

        TestPublisherSubscriber<ServiceDiscovererEvent<InetAddress>> subscriber1 = dnsQuery(domain1);
        Subscription subscription1 = subscriber1.awaitSubscription();
        subscription1.request(1);

        TestPublisherSubscriber<ServiceDiscovererEvent<InetAddress>> subscriber2 = dnsQuery(domain2);
        Subscription subscription2 = subscriber2.awaitSubscription();
        subscription2.request(1);

        assertEvent(subscriber1.takeOnNext(), ip1, AVAILABLE);
        assertEvent(subscriber2.takeOnNext(), ip2, AVAILABLE);

        // Remove all the IPs
        recordStore.removeIPv4Address(domain1, DEFAULT_TTL, ip1);
        recordStore.removeIPv4Address(domain2, DEFAULT_TTL, ip2);
        subscription1.request(1);
        subscription2.request(1);
        advanceTime();
        assertThat(subscriber1.awaitOnError(), instanceOf(UnknownHostException.class));
        assertThat(subscriber2.awaitOnError(), instanceOf(UnknownHostException.class));
    }

    @ParameterizedTest(name = "{displayName} [{index}] missingRecordStatus={0}")
    @MethodSource("missingRecordStatus")
    void repeatDiscoverNxDomainAndRecover(ServiceDiscovererEvent.Status missingRecordStatus) throws Exception {
        setup(builder -> builder
                .inactiveEventsOnError(true)
                .missingRecordStatus(missingRecordStatus));
        final String ip = nextIp();
        final String domain = "servicetalk.io";
        recordStore.addIPv4Address(domain, DEFAULT_TTL, ip);

        BiIntFunction<Throwable, ? extends Completable> retryStrategy =
                (i, t) -> timerExecutor.executor().timer(ofMillis(50));
        TestPublisherSubscriber<ServiceDiscovererEvent<InetAddress>> subscriber = dnsQuery(domain, retryStrategy);
        Subscription subscription = subscriber.awaitSubscription();
        subscription.request(4);

        assertEvent(subscriber.takeOnNext(), ip, AVAILABLE);
        recordStore.removeIPv4Address(domain, DEFAULT_TTL, ip);
        advanceTime();
        assertEvent(subscriber.takeOnNext(), ip, missingRecordStatus);
        recordStore.addIPv4Address(domain, DEFAULT_TTL, ip);
        advanceTime();
        assertEvent(subscriber.takeOnNext(), ip, AVAILABLE);
    }

    @Test
    void preferIpv4() throws Exception {
        setup(builder -> builder
                .completeOncePreferredResolved(false)
                .dnsResolverAddressTypes(IPV4_PREFERRED));

        final String ipv4A = nextIp();
        final String ipv4B = nextIp();
        final String ipv6 = nextIp6();
        final String domain = "servicetalk.io";
        recordStore.addIPv6Address(domain, DEFAULT_TTL, ipv6);
        recordStore.addIPv4Address(domain, DEFAULT_TTL, ipv4A);
        recordStore.addIPv4Address(domain, DEFAULT_TTL, ipv4B);

        TestPublisherSubscriber<ServiceDiscovererEvent<InetAddress>> subscriber = dnsQuery(domain);
        Subscription subscription = subscriber.awaitSubscription();
        subscription.request(Long.MAX_VALUE);

        List<ServiceDiscovererEvent<InetAddress>> signals = subscriber.takeOnNext(2);
        assertHasEvent(signals, ipv4A, AVAILABLE);
        assertHasEvent(signals, ipv4B, AVAILABLE);
        assertThat(subscriber.pollAllOnNext(), is(empty()));

        // Removal of IPv6 doesn't affect output as long as there is at least one IPv4
        recordStore.removeIPv6Address(domain, DEFAULT_TTL, ipv6);
        recordStore.removeIPv4Address(domain, DEFAULT_TTL, ipv4A);
        advanceTime();
        assertEvent(subscriber.takeOnNext(), ipv4A, EXPIRED);
        assertThat(subscriber.pollAllOnNext(), is(empty()));
    }

    @Test
    void preferIpv6() throws Exception {
        setup(builder -> builder
                .completeOncePreferredResolved(false)
                .dnsResolverAddressTypes(IPV6_PREFERRED));

        final String ipv4 = nextIp();
        final String ipv6A = nextIp6();
        final String ipv6B = nextIp6();
        final String domain = "servicetalk.io";
        recordStore.addIPv4Address(domain, DEFAULT_TTL, ipv4);
        recordStore.addIPv6Address(domain, DEFAULT_TTL, ipv6A);
        recordStore.addIPv6Address(domain, DEFAULT_TTL, ipv6B);

        TestPublisherSubscriber<ServiceDiscovererEvent<InetAddress>> subscriber = dnsQuery(domain);
        Subscription subscription = subscriber.awaitSubscription();
        subscription.request(Long.MAX_VALUE);

        List<ServiceDiscovererEvent<InetAddress>> signals = subscriber.takeOnNext(2);
        assertHasEvent(signals, ipv6A, AVAILABLE);
        assertHasEvent(signals, ipv6B, AVAILABLE);
        assertThat(subscriber.pollAllOnNext(), is(empty()));

        // Removal of IPv4 doesn't affect output as long as there is at least one IPv6
        recordStore.removeIPv4Address(domain, DEFAULT_TTL, ipv4);
        recordStore.removeIPv6Address(domain, DEFAULT_TTL, ipv6A);
        advanceTime();
        assertEvent(subscriber.takeOnNext(), ipv6A, EXPIRED);
        assertThat(subscriber.pollAllOnNext(), is(empty()));
    }

    @Test
    void preferIpv4FallbackToIpv6() throws Exception {
        setup(builder -> builder
                .completeOncePreferredResolved(false)
                .dnsResolverAddressTypes(IPV4_PREFERRED));

        final String ipv4A = nextIp();
        final String ipv4B = nextIp();
        final String ipv6A = nextIp6();
        final String ipv6B = nextIp6();
        final String domain = "servicetalk.io";
        recordStore.addIPv4Address(domain, DEFAULT_TTL, ipv4A);
        recordStore.addIPv4Address(domain, DEFAULT_TTL, ipv4B);
        recordStore.addIPv6Address(domain, DEFAULT_TTL, ipv6A);
        recordStore.addIPv6Address(domain, DEFAULT_TTL, ipv6B);

        TestPublisherSubscriber<ServiceDiscovererEvent<InetAddress>> subscriber = dnsQuery(domain);
        Subscription subscription = subscriber.awaitSubscription();
        subscription.request(Long.MAX_VALUE);

        List<ServiceDiscovererEvent<InetAddress>> signals = subscriber.takeOnNext(2);
        assertHasEvent(signals, ipv4A, AVAILABLE);
        assertHasEvent(signals, ipv4B, AVAILABLE);
        assertThat(subscriber.pollAllOnNext(), is(empty()));

        // Removal of IPv4 results in a fallback to IPv6
        recordStore.removeIPv4Address(domain, DEFAULT_TTL, ipv4A);
        recordStore.removeIPv4Address(domain, DEFAULT_TTL, ipv4B);
        advanceTime();
        signals = subscriber.takeOnNext(4);
        assertHasEvent(signals, ipv6A, AVAILABLE);
        assertHasEvent(signals, ipv6B, AVAILABLE);
        assertHasEvent(signals, ipv4A, EXPIRED);
        assertHasEvent(signals, ipv4B, EXPIRED);
        assertThat(subscriber.pollAllOnNext(), is(empty()));

        // Return back to IPv4 as soon as it appears in the result again
        recordStore.addIPv4Address(domain, DEFAULT_TTL, ipv4A);
        advanceTime();
        signals = subscriber.takeOnNext(3);
        assertHasEvent(signals, ipv4A, AVAILABLE);
        assertHasEvent(signals, ipv6A, EXPIRED);
        assertHasEvent(signals, ipv6B, EXPIRED);
        assertThat(subscriber.pollAllOnNext(), is(empty()));
    }

    @Test
    void preferIpv6FallbackToIpv4() throws Exception {
        setup(builder -> builder
                .completeOncePreferredResolved(false)
                .dnsResolverAddressTypes(IPV6_PREFERRED));

        final String ipv4A = nextIp();
        final String ipv4B = nextIp();
        final String ipv6A = nextIp6();
        final String ipv6B = nextIp6();
        final String domain = "servicetalk.io";
        recordStore.addIPv4Address(domain, DEFAULT_TTL, ipv4A);
        recordStore.addIPv4Address(domain, DEFAULT_TTL, ipv4B);
        recordStore.addIPv6Address(domain, DEFAULT_TTL, ipv6A);
        recordStore.addIPv6Address(domain, DEFAULT_TTL, ipv6B);

        TestPublisherSubscriber<ServiceDiscovererEvent<InetAddress>> subscriber = dnsQuery(domain);
        Subscription subscription = subscriber.awaitSubscription();
        subscription.request(Long.MAX_VALUE);

        List<ServiceDiscovererEvent<InetAddress>> signals = subscriber.takeOnNext(2);
        assertHasEvent(signals, ipv6A, AVAILABLE);
        assertHasEvent(signals, ipv6B, AVAILABLE);
        assertThat(subscriber.pollAllOnNext(), is(empty()));

        // Removal of IPv6 results in a fallback to IPv4
        recordStore.removeIPv6Address(domain, DEFAULT_TTL, ipv6A);
        recordStore.removeIPv6Address(domain, DEFAULT_TTL, ipv6B);
        advanceTime();
        signals = subscriber.takeOnNext(4);
        assertHasEvent(signals, ipv4A, AVAILABLE);
        assertHasEvent(signals, ipv4B, AVAILABLE);
        assertHasEvent(signals, ipv6A, EXPIRED);
        assertHasEvent(signals, ipv6B, EXPIRED);
        assertThat(subscriber.pollAllOnNext(), is(empty()));

        // Return back to IPv6 as soon as it appears in the result again
        recordStore.addIPv6Address(domain, DEFAULT_TTL, ipv6A);
        advanceTime();
        signals = subscriber.takeOnNext(3);
        assertHasEvent(signals, ipv6A, AVAILABLE);
        assertHasEvent(signals, ipv4A, EXPIRED);
        assertHasEvent(signals, ipv4B, EXPIRED);
        assertThat(subscriber.pollAllOnNext(), is(empty()));
    }

    @ParameterizedTest(name = "{displayName} [{index}] dnsResolverAddressTypes={0}")
    @EnumSource(value = DnsResolverAddressTypes.class,
            names = {"IPV4_PREFERRED_RETURN_ALL", "IPV6_PREFERRED_RETURN_ALL"})
    void returnAll(DnsResolverAddressTypes addressTypes) throws Exception {
        setup(builder -> builder
                .completeOncePreferredResolved(false)
                .dnsResolverAddressTypes(addressTypes));

        final String ipv4 = nextIp();
        final String ipv6 = nextIp6();
        final String domain = "servicetalk.io";
        recordStore.addIPv4Address(domain, DEFAULT_TTL, ipv4);
        recordStore.addIPv6Address(domain, DEFAULT_TTL, ipv6);

        TestPublisherSubscriber<ServiceDiscovererEvent<InetAddress>> subscriber = dnsQuery(domain);
        Subscription subscription = subscriber.awaitSubscription();
        subscription.request(Long.MAX_VALUE);

        List<ServiceDiscovererEvent<InetAddress>> signals = subscriber.takeOnNext(2);
        assertHasEvent(signals, ipv4, AVAILABLE);
        assertHasEvent(signals, ipv6, AVAILABLE);

        // Remove one
        if (addressTypes == IPV4_PREFERRED_RETURN_ALL) {
            recordStore.removeIPv4Address(domain, DEFAULT_TTL, ipv4);
            advanceTime();
            assertEvent(subscriber.takeOnNext(), ipv4, EXPIRED);
        } else {
            recordStore.removeIPv6Address(domain, DEFAULT_TTL, ipv6);
            advanceTime();
            assertEvent(subscriber.takeOnNext(), ipv6, EXPIRED);
        }
    }

    @ParameterizedTest(name = "{displayName} [{index}] dnsResolverAddressTypes={0}")
    @EnumSource(value = DnsResolverAddressTypes.class, names = {"IPV4_PREFERRED", "IPV4_PREFERRED_RETURN_ALL"})
    void preferIpv4ButOnlyAAAARecordIsPresent(DnsResolverAddressTypes addressTypes) throws Exception {
        setup(builder -> builder.dnsResolverAddressTypes(addressTypes));
        final String ipv6 = nextIp6();
        final String domain = "servicetalk.io";
        recordStore.addIPv6Address(domain, DEFAULT_TTL, ipv6);

        TestPublisherSubscriber<ServiceDiscovererEvent<InetAddress>> subscriber = dnsQuery(domain);
        Subscription subscription = subscriber.awaitSubscription();
        subscription.request(Long.MAX_VALUE);

        assertEvent(subscriber.takeOnNext(), ipv6, AVAILABLE);

        // Remove all ips
        recordStore.removeIPv6Address(domain, DEFAULT_TTL, ipv6);
        advanceTime();
        assertThat(subscriber.awaitOnError(), instanceOf(UnknownHostException.class));
    }

    @ParameterizedTest(name = "{displayName} [{index}] dnsResolverAddressTypes={0}")
    @EnumSource(value = DnsResolverAddressTypes.class, names = {"IPV6_PREFERRED", "IPV6_PREFERRED_RETURN_ALL"})
    void preferIpv6ButOnlyARecordIsPresent(DnsResolverAddressTypes addressTypes) throws Exception {
        setup(builder -> builder.dnsResolverAddressTypes(addressTypes));
        final String ipv4 = nextIp();
        final String domain = "servicetalk.io";
        recordStore.addIPv4Address(domain, DEFAULT_TTL, ipv4);

        TestPublisherSubscriber<ServiceDiscovererEvent<InetAddress>> subscriber = dnsQuery(domain);
        Subscription subscription = subscriber.awaitSubscription();
        subscription.request(Long.MAX_VALUE);

        assertEvent(subscriber.takeOnNext(), ipv4, AVAILABLE);

        // Remove all ips
        recordStore.removeIPv4Address(domain, DEFAULT_TTL, ipv4);
        advanceTime();
        assertThat(subscriber.awaitOnError(), instanceOf(UnknownHostException.class));
    }

    @Test
    void acceptOnlyIpv6() throws Exception {
        setup(builder -> builder.dnsResolverAddressTypes(IPV6_ONLY));
        final String ipv6 = nextIp6();
        final String domain = "servicetalk.io";
        recordStore.addIPv6Address(domain, DEFAULT_TTL, ipv6);
        recordStore.addIPv4Address(domain, DEFAULT_TTL, nextIp());

        TestPublisherSubscriber<ServiceDiscovererEvent<InetAddress>> subscriber = dnsQuery(domain);
        Subscription subscription = subscriber.awaitSubscription();
        subscription.request(Long.MAX_VALUE);

        assertEvent(subscriber.takeOnNext(), ipv6, AVAILABLE);
    }

    @Test
    void exceptionInSubscriberOnNext() throws Exception {
        setup();
        final String domain = "servicetalk.io";
        final String ip = nextIp();
        recordStore.addIPv4Address(domain, DEFAULT_TTL, ip);
        CountDownLatch latchOnError = new CountDownLatch(1);
        BlockingQueue<ServiceDiscovererEvent<InetAddress>> queue = new ArrayBlockingQueue<>(10);
        toSource(client.dnsQuery(domain).flatMapConcatIterable(identity())).subscribe(
                mockThrowSubscriber(latchOnError, queue));
        assertEvent(queue.take(), ip, AVAILABLE);
        latchOnError.await();
    }

    @Test
    void srvExceptionInSubscriberOnNext() throws Exception {
        setup(builder -> builder.srvHostNameRepeatDelay(ofMillis(50), ofMillis(10)));
        final String domain = "sd.servicetalk.io";
        final String targetDomain1 = "target1.mysvc.servicetalk.io";
        final String ip = nextIp();
        final int targetPort = 9876;
        final int ttl = DEFAULT_TTL + 10;
        recordStore.addIPv4Address(targetDomain1, ttl, ip);
        recordStore.addSrv(domain, targetDomain1, targetPort, DEFAULT_TTL);
        CountDownLatch latchOnError = new CountDownLatch(1);
        BlockingQueue<ServiceDiscovererEvent<InetSocketAddress>> queue = new ArrayBlockingQueue<>(10);
        toSource(client.dnsSrvQuery(domain).flatMapConcatIterable(identity())).subscribe(
                mockThrowSubscriber(latchOnError, queue));
        assertEvent(queue.take(), ip, targetPort, AVAILABLE);
        assertEvent(queue.take(), ip, targetPort, EXPIRED);
        // Remove the srv address because the mapped publishers don't propagate errors, so we want the outer SRV resolve
        // to fail.
        recordStore.removeSrv(domain, targetDomain1, targetPort, DEFAULT_TTL);
        advanceTime();
        latchOnError.await();
    }

    @Test
    void capsMaxTTL() throws Exception {
        setup(builder -> builder.maxTTL(3));
        final String domain = "servicetalk.io";
        String ip = nextIp();
        recordStore.addIPv4Address(domain, 5, ip);

        TestPublisherSubscriber<ServiceDiscovererEvent<InetAddress>> subscriber = dnsQuery(domain);
        Subscription subscription = subscriber.awaitSubscription();
        subscription.request(Long.MAX_VALUE);

        System.err.println(subscriber.takeOnNext());
        recordStore.removeIPv4Address(domain, 1, ip);
        advanceTime(3);
        System.err.println(subscriber.takeOnNext());

        //assertEvent(subscriber.takeOnNext(), ipv6, AVAILABLE);
    }

    private static <T> Subscriber<ServiceDiscovererEvent<T>> mockThrowSubscriber(
            CountDownLatch latchOnError, Queue<ServiceDiscovererEvent<T>> queue) {
        @SuppressWarnings("unchecked")
        Subscriber<ServiceDiscovererEvent<T>> subscriber = mock(Subscriber.class);
        AtomicInteger onNextCount = new AtomicInteger();
        doAnswer(a -> {
            Subscription s = a.getArgument(0);
            s.request(Long.MAX_VALUE);
            return null;
        }).when(subscriber).onSubscribe(any(Subscription.class));
        doAnswer(a -> {
            latchOnError.countDown();
            return null;
        }).when(subscriber).onError(any());
        doAnswer(a -> {
            queue.add(a.getArgument(0));
            if (onNextCount.getAndIncrement() == 0) {
                throw DELIBERATE_EXCEPTION;
            }
            return null;
        }).when(subscriber).onNext(any());
        return subscriber;
    }

    private TestPublisherSubscriber<ServiceDiscovererEvent<InetSocketAddress>> dnsSrvQuery(String domain) {
        Publisher<ServiceDiscovererEvent<InetSocketAddress>> publisher = client.dnsSrvQuery(domain)
                .flatMapConcatIterable(identity());
        TestPublisherSubscriber<ServiceDiscovererEvent<InetSocketAddress>> subscriber =
                new TestPublisherSubscriber<>();
        toSource(publisher).subscribe(subscriber);
        return subscriber;
    }

    private TestPublisherSubscriber<ServiceDiscovererEvent<InetAddress>> dnsQuery(String domain) {
        return dnsQuery(domain, (i, t) -> Completable.failed(t));
    }

    private TestPublisherSubscriber<ServiceDiscovererEvent<InetAddress>> dnsQuery(String domain,
            BiIntFunction<Throwable, ? extends Completable> retryStrategy) {
        Publisher<ServiceDiscovererEvent<InetAddress>> publisher = client.dnsQuery(domain)
                .retryWhen(retryStrategy)
                .flatMapConcatIterable(identity());
        TestPublisherSubscriber<ServiceDiscovererEvent<InetAddress>> subscriber =
                new TestPublisherSubscriber<>();
        toSource(publisher).subscribe(subscriber);
        return subscriber;
    }

    private DefaultDnsServiceDiscovererBuilder dnsClientBuilder() {
        return new DefaultDnsServiceDiscovererBuilder()
                .ioExecutor(new NettyIoExecutorWithTestTimer(ioExecutor.executor(), timerExecutor.executor()))
                .dnsResolverAddressTypes(IPV4_ONLY)
                .optResourceEnabled(false)
                .srvConcurrency(512)
                .dnsServerAddressStreamProvider(new SingletonDnsServerAddressStreamProvider(dnsServer.localAddress()))
                .ndots(1)
                .minTTL(1)
                .ttlJitter(Duration.ofNanos(1));
    }

    private static void assertEvent(@Nullable ServiceDiscovererEvent<InetSocketAddress> event,
                                    String ip, int port, ServiceDiscovererEvent.Status status)
            throws UnknownHostException {
        assertThat(event, is(new DefaultServiceDiscovererEvent<>(
                new InetSocketAddress(getByName(ip), port), status)));
    }

    private static void assertEvent(@Nullable ServiceDiscovererEvent<InetAddress> event,
                                    String ip, ServiceDiscovererEvent.Status status) throws UnknownHostException {
        assertThat(event, is(new DefaultServiceDiscovererEvent<>(getByName(ip), status)));
    }

    private static void assertHasEvent(Collection<ServiceDiscovererEvent<InetAddress>> events,
                                       String ip, ServiceDiscovererEvent.Status status) throws UnknownHostException {
        assertThat(events, hasItems(new DefaultServiceDiscovererEvent<>(getByName(ip), status)));
    }

    private static void assertHasEvent(Collection<ServiceDiscovererEvent<InetSocketAddress>> events,
                                       String ip, int port, ServiceDiscovererEvent.Status status)
            throws UnknownHostException {
        assertThat(events, hasItems(new DefaultServiceDiscovererEvent<>(
                new InetSocketAddress(getByName(ip), port), status)));
    }

    private static final class NettyIoExecutorWithTestTimer implements EventLoopAwareNettyIoExecutor {

        private final EventLoopAwareNettyIoExecutor delegate;
        private final TestExecutor timer;

        NettyIoExecutorWithTestTimer(EventLoopAwareNettyIoExecutor delegate, TestExecutor timer) {
            // We need to take a single EventLoopIoExecutor to make sure that isCurrentThreadEventLoop() works.
            this.delegate = delegate.next();
            this.timer = timer;
        }

        @Override
        public Completable closeAsync() {
            return delegate.closeAsync();
        }

        @Override
        public Completable closeAsyncGracefully() {
            return delegate.closeAsyncGracefully();
        }

        @Override
        public Completable onClose() {
            return delegate.onClose();
        }

        @Override
        public Completable onClosing() {
            return delegate.onClosing();
        }

        @Override
        public boolean isUnixDomainSocketSupported() {
            return delegate.isUnixDomainSocketSupported();
        }

        @Override
        public boolean isFileDescriptorSocketAddressSupported() {
            return delegate.isFileDescriptorSocketAddressSupported();
        }

        @Override
        public boolean isIoThreadSupported() {
            return delegate.isIoThreadSupported();
        }

        @Override
        public boolean isCurrentThreadEventLoop() {
            return delegate.isCurrentThreadEventLoop();
        }

        @Override
        public EventLoopGroup eventLoopGroup() {
            return delegate.eventLoopGroup();
        }

        @Override
        public EventLoopAwareNettyIoExecutor next() {
            return this;
        }

        @Override
        public Cancellable execute(final Runnable task) throws RejectedExecutionException {
            return delegate.execute(task);
        }

        @Override
        public Completable submit(final Runnable runnable) {
            return delegate.submit(runnable);
        }

        @Override
        public Completable submitRunnable(final Supplier<Runnable> runnableSupplier) {
            return delegate.submitRunnable(runnableSupplier);
        }

        @Override
        public <T> Single<T> submit(final Callable<? extends T> callable) {
            return delegate.submit(callable);
        }

        @Override
        public <T> Single<T> submitCallable(final Supplier<? extends Callable<? extends T>> callableSupplier) {
            return delegate.submitCallable(callableSupplier);
        }

        @Override
        public Executor asExecutor() {
            return timer;
        }

        @Override
        public long currentTime(final TimeUnit unit) {
            return timer.currentTime(unit);
        }

        @Override
        public Cancellable schedule(final Runnable task, final long delay, final TimeUnit unit)
                throws RejectedExecutionException {
            // Original IoExecutor schedules and executes tasks on EventLoop. If we move scheduling to TestExecutor,
            // we need to offload the task back to EventLoop thread.
            return timer.schedule(() -> delegate.execute(task), delay, unit);
        }

        @Override
        public Cancellable schedule(final Runnable task, final Duration delay) throws RejectedExecutionException {
            // Original IoExecutor schedules and executes tasks on EventLoop. If we move scheduling to TestExecutor,
            // we need to offload the task back to EventLoop thread.
            return timer.schedule(() -> delegate.execute(task), delay);
        }

        @Override
        public Completable timer(final long delay, final TimeUnit unit) {
            return timer.timer(delay, unit).publishOn(delegate);
        }

        @Override
        public Completable timer(final Duration delay) {
            return timer.timer(delay).publishOn(delegate);
        }
    }
}
