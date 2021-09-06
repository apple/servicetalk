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
import io.servicetalk.concurrent.PublisherSource.Subscriber;
import io.servicetalk.concurrent.PublisherSource.Subscription;
import io.servicetalk.concurrent.api.BiIntFunction;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.test.internal.TestPublisherSubscriber;
import io.servicetalk.transport.netty.internal.EventLoopAwareNettyIoExecutor;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Executors.immediate;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.dns.discovery.netty.DnsResolverAddressTypes.IPV4_ONLY;
import static io.servicetalk.dns.discovery.netty.DnsResolverAddressTypes.IPV4_PREFERRED;
import static io.servicetalk.dns.discovery.netty.DnsResolverAddressTypes.IPV6_ONLY;
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

    private EventLoopAwareNettyIoExecutor nettyIoExecutor;
    private final TestRecordStore recordStore = new TestRecordStore();
    private TestDnsServer dnsServer;
    private TestDnsServer dnsServer2;
    private DnsClient client;

    @SuppressWarnings("PMD.AvoidUsingHardCodedIP")
    @BeforeEach
    public void setup() throws Exception {
        nettyIoExecutor = createIoExecutor();

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

        client = dnsClientBuilder().build();
    }

    @AfterEach
    public void tearDown() throws Exception {
        client.closeAsync().toFuture().get();
        dnsServer.stop();
        dnsServer2.stop();
        nettyIoExecutor.closeAsync().toFuture().get();
    }

    @Test
    void singleSrvSingleADiscover() throws Exception {
        final String domain = "mysvc.apple.com";
        final String targetDomain = "target.mysvc.apple.com";
        final int targetPort = 9876;
        final String ip = nextIp();
        recordStore.addSrv(domain, targetDomain, targetPort, DEFAULT_TTL);
        recordStore.addIPv4Address(targetDomain, DEFAULT_TTL, ip);

        TestPublisherSubscriber<ServiceDiscovererEvent<InetSocketAddress>> subscriber = dnsSrvQuery(domain);
        Subscription subscription = subscriber.awaitSubscription();
        subscription.request(1);

        assertEvent(subscriber.takeOnNext(), ip, targetPort, true);
    }

    @Test
    void singleSrvMultipleADiscover() throws Exception {
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
        assertHasEvent(signals, ip1, targetPort, true);
        assertHasEvent(signals, ip2, targetPort, true);
    }

    @Test
    void multipleSrvSingleADiscover() throws Exception {
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
        assertHasEvent(signals, ip1, targetPort1, true);
        assertHasEvent(signals, ip2, targetPort2, true);
    }

    @Test
    void multipleSrvChangeSingleADiscover() throws Exception {
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
        assertHasEvent(signals, ip1, targetPort1, true);
        assertHasEvent(signals, ip2, targetPort2, true);
        assertHasEvent(signals, ip3, targetPort3, true);

        recordStore.removeSrv(domain, targetDomain2, targetPort2, 1);
        assertEvent(subscriber.takeOnNext(), ip2, targetPort2, false);
    }

    @Test
    void multipleSrvMultipleADiscover() throws Exception {
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
        assertHasEvent(signals, ip1, targetPort1, true);
        assertHasEvent(signals, ip2, targetPort1, true);
        assertHasEvent(signals, ip3, targetPort2, true);
        assertHasEvent(signals, ip4, targetPort2, true);
    }

    @Test
    void srvWithCNAMEEntryLowerTTLDoesNotFail() throws Exception {
        final String domain = "sd.servicetalk.io";
        final String srvCNAME = "sdcname.servicetalk.io";
        final String targetDomain1 = "target1.mysvc.servicetalk.io";
        final int targetPort = 9876;
        final String ip1 = nextIp();
        final int ttl = DEFAULT_TTL + 3;
        recordStore.addCNAME(domain, srvCNAME, ttl);
        recordStore.addSrv(domain, targetDomain1, targetPort, ttl);
        recordStore.addSrv(srvCNAME, targetDomain1, targetPort, 1);
        recordStore.addIPv4Address(targetDomain1, ttl, ip1);

        TestPublisherSubscriber<ServiceDiscovererEvent<InetSocketAddress>> subscriber = dnsSrvQuery(domain);
        Subscription subscription = subscriber.awaitSubscription();
        subscription.request(10);

        assertEvent(subscriber.takeOnNext(), ip1, targetPort, true);
        recordStore.removeSrv(srvCNAME, targetDomain1, targetPort, 1);
        assertNull(subscriber.pollTerminal(ttl, SECONDS));
    }

    @Test
    void srvCNAMEDuplicateAddressesRemoveFail() throws Exception {
        srvCNAMEDuplicateAddresses(false);
    }

    @Test
    void srvCNAMEDuplicateAddressesRemoveInactive() throws Exception {
        srvCNAMEDuplicateAddresses(true);
    }

    private void srvCNAMEDuplicateAddresses(boolean inactiveEventsOnError) throws Exception {
        client.closeAsync().toFuture().get();
        client = dnsClientBuilder()
                .dnsServerAddressStreamProvider(new SequentialDnsServerAddressStreamProvider(
                        dnsServer2.localAddress(), dnsServer.localAddress()))
                .inactiveEventsOnError(inactiveEventsOnError)
                .build();
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
        assertHasEvent(signals, ip1, targetPort, true);
        assertHasEvent(signals, ip2, targetPort, true);

        // Atomically remove all domain records.
        recordStore.removeRecords(
                createCnameRecord(domain, srvCNAME, ttl),
                createSrvRecord(domain, targetDomain1, targetPort, ttl),
                createSrvRecord(domain, targetDomain2, targetPort, ttl));

        if (inactiveEventsOnError) {
            signals = subscriber.takeOnNext(2);
            assertHasEvent(signals, ip1, targetPort, false);
            assertHasEvent(signals, ip2, targetPort, false);
        }
        assertThat(subscriber.awaitOnError(), instanceOf(UnknownHostException.class));
    }

    @Test
    void srvInactiveEventsAggregated() throws Exception {
        client.closeAsync().toFuture().get();
        client = dnsClientBuilder().inactiveEventsOnError(true).build();
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

        assertHasEvent(signals, ip1, targetPort, true);
        assertHasEvent(signals, ip2, targetPort, true);
        assertHasEvent(signals, ip3, targetPort, true);

        // Atomically remove all the SRV records, the next resolution should result in a host not found exception.
        recordStore.removeRecords(
                createSrvRecord(domain, targetDomain1, targetPort, DEFAULT_TTL),
                createSrvRecord(domain, targetDomain2, targetPort, DEFAULT_TTL),
                createSrvRecord(domain, targetDomain3, targetPort, DEFAULT_TTL));

        Collection<ServiceDiscovererEvent<InetSocketAddress>> next = subscriber.takeOnNext();
        assertNotNull(next);
        assertHasEvent(next, ip1, targetPort, false);
        assertHasEvent(next, ip2, targetPort, false);
        assertHasEvent(next, ip3, targetPort, false);
        assertThat(subscriber.awaitOnError(), instanceOf(UnknownHostException.class));
    }

    @Test
    void srvRecordRemovalPropagatesError() throws Exception {
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
        assertHasEvent(signals, ip1, targetPort, true);
        assertHasEvent(signals, ip2, targetPort, true);

        // Atomically remove all the SRV records, the next resolution should result in a host not found exception.
        recordStore.removeRecords(
                createSrvRecord(domain, targetDomain1, targetPort, DEFAULT_TTL),
                createSrvRecord(domain, targetDomain2, targetPort, DEFAULT_TTL));

        assertThat(subscriber.awaitOnError(), instanceOf(UnknownHostException.class));
    }

    @Test
    void srvDuplicateAddressesNoFilter() throws Exception {
        srvDuplicateAddresses(false);
    }

    @Test
    void srvDuplicateAddressesFilter() throws Exception {
        srvDuplicateAddresses(true);
    }

    private void srvDuplicateAddresses(boolean srvFilterDuplicateEvents) throws Exception {
        client.closeAsync().toFuture().get();
        client = dnsClientBuilder().srvFilterDuplicateEvents(srvFilterDuplicateEvents).build();
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

        assertEvent(subscriber.takeOnNext(), ip1, targetPort, true);
        if (srvFilterDuplicateEvents) {
            assertThat(subscriber.pollOnNext(50, MILLISECONDS), is(nullValue()));
            recordStore.removeIPv4Address(targetDomain1, 1, ip1);
            assertThat(subscriber.pollOnNext(50, MILLISECONDS), is(nullValue()));
        } else {
            assertEvent(subscriber.takeOnNext(), ip1, targetPort, true);
            recordStore.removeIPv4Address(targetDomain1, 1, ip1);
            assertEvent(subscriber.takeOnNext(), ip1, targetPort, false);
        }
        recordStore.removeIPv4Address(targetDomain2, 1, ip1);
        assertEvent(subscriber.takeOnNext(), ip1, targetPort, false);
    }

    @Test
    void srvAAAAFailsGeneratesInactive() throws Exception {
        srvAAAAFailsGeneratesInactive(true);
    }

    @Test
    void srvAAAAFailsGeneratesInactiveEvenIfNotRequested() throws Exception {
        srvAAAAFailsGeneratesInactive(false);
    }

    private void srvAAAAFailsGeneratesInactive(boolean inactiveEventsOnError) throws Exception {
        client.closeAsync().toFuture().get();
        client = dnsClientBuilder()
                .inactiveEventsOnError(inactiveEventsOnError)
                .srvHostNameRepeatDelay(ofMillis(200), ofMillis(10))
                .dnsResolverAddressTypes(IPV4_PREFERRED).build();
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
        assertHasEvent(signals, ip1, targetPort, true);
        assertHasEvent(signals, ip2, targetPort, true);

        recordStore.removeIPv6Address(targetDomain1, DEFAULT_TTL, ip1);
        assertEvent(subscriber.takeOnNext(), ip1, targetPort, false);

        recordStore.addIPv6Address(targetDomain1, DEFAULT_TTL, ip1);
        assertEvent(subscriber.takeOnNext(), ip1, targetPort, true);
    }

    @Test
    void srvNoMoreSrvRecordsFails() throws Exception {
        srvRecordFailsGeneratesInactive(false);
    }

    @Test
    void srvNoMoreSrvRecordsGeneratesInactive() throws Exception {
        srvRecordFailsGeneratesInactive(true);
    }

    private void srvRecordFailsGeneratesInactive(boolean inactiveEventsOnError) throws Exception {
        client.closeAsync().toFuture().get();
        client = dnsClientBuilder().inactiveEventsOnError(inactiveEventsOnError).build();
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
        assertHasEvent(signals, ip1, targetPort, true);
        assertHasEvent(signals, ip2, targetPort, true);

        recordStore.removeSrv(domain, targetDomain1, targetPort, DEFAULT_TTL);
        assertEvent(subscriber.takeOnNext(), ip1, targetPort, false);

        recordStore.removeSrv(domain, targetDomain2, targetPort, DEFAULT_TTL);
        if (inactiveEventsOnError) {
            assertEvent(subscriber.takeOnNext(), ip2, targetPort, false);
        }
        assertThat(subscriber.awaitOnError(), instanceOf(UnknownHostException.class));
    }

    @Test
    void unknownHostDiscover() {
        TestPublisherSubscriber<ServiceDiscovererEvent<InetAddress>> subscriber = dnsQuery("unknown.com");
        Subscription subscription = subscriber.awaitSubscription();
        subscription.request(Long.MAX_VALUE);

        assertThat(subscriber.awaitOnError(), instanceOf(UnknownHostException.class));
    }

    @Test
    void singleADiscover() throws Exception {
        final String ip = nextIp();
        final String domain = "servicetalk.io";
        recordStore.addIPv4Address(domain, DEFAULT_TTL, ip);

        TestPublisherSubscriber<ServiceDiscovererEvent<InetAddress>> subscriber = dnsQuery(domain);
        Subscription subscription = subscriber.awaitSubscription();
        subscription.request(1);

        assertEvent(subscriber.takeOnNext(), ip, true);

        // Remove the ip
        recordStore.removeIPv4Address(domain, DEFAULT_TTL, ip);
        subscription.request(1);
        assertThat(subscriber.awaitOnError(), instanceOf(UnknownHostException.class));
    }

    @Test
    void singleDiscoverMultipleRecords() throws Exception {
        final String domain = "servicetalk.io";
        final String[] ips = new String[] {nextIp(), nextIp(), nextIp(), nextIp(), nextIp()};
        recordStore.addIPv4Address(domain, DEFAULT_TTL, ips);

        TestPublisherSubscriber<ServiceDiscovererEvent<InetAddress>> subscriber = dnsQuery(domain);
        Subscription subscription = subscriber.awaitSubscription();
        subscription.request(ips.length);
        List<ServiceDiscovererEvent<InetAddress>> signals = subscriber.takeOnNext(ips.length);
        for (String ip : ips) {
            assertHasEvent(signals, ip, true);
        }

        // Remove all the ips
        recordStore.removeIPv4Address(domain, DEFAULT_TTL, ips);
        subscription.request(1);
        assertThat(subscriber.awaitOnError(), instanceOf(UnknownHostException.class));
    }

    @Test
    void singleDiscoverDuplicateRecords() throws Exception {
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
                    assertHasEvent(signals, ip, true);
                }
            } else {
                assertHasEvent(signals, ip, true);
            }
        }
    }

    @Test
    void repeatDiscoverMultipleRecords() throws Exception {
        final String domain = "servicetalk.io";
        final String[] ips = new String[] {nextIp(), nextIp(), nextIp(), nextIp(), nextIp()};
        recordStore.addIPv4Address(domain, DEFAULT_TTL, ips);

        TestPublisherSubscriber<ServiceDiscovererEvent<InetAddress>> subscriber = dnsQuery(domain);
        Subscription subscription = subscriber.awaitSubscription();
        subscription.request(ips.length);
        List<ServiceDiscovererEvent<InetAddress>> signals = subscriber.takeOnNext(ips.length);
        for (String ip : ips) {
            assertHasEvent(signals, ip, true);
        }

        final String[] ips2 = new String[] {nextIp(), nextIp(), nextIp(), nextIp(), nextIp()};
        recordStore.addIPv4Address(domain, DEFAULT_TTL, ips2);
        subscription.request(ips2.length);
        signals = subscriber.takeOnNext(ips2.length);
        for (String ip : ips2) {
            assertHasEvent(signals, ip, true);
        }

        // Remove all the IPs
        recordStore.removeIPv4Address(domain, DEFAULT_TTL, ips);
        recordStore.removeIPv4Address(domain, DEFAULT_TTL, ips2);
        subscription.request(1);
        assertThat(subscriber.awaitOnError(), instanceOf(UnknownHostException.class));
    }

    @Test
    void repeatDiscoverMultipleHosts() throws Exception {
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

        assertEvent(subscriber1.takeOnNext(), ip1, true);
        assertEvent(subscriber2.takeOnNext(), ip2, true);

        // Remove all the IPs
        recordStore.removeIPv4Address(domain1, DEFAULT_TTL, ip1);
        recordStore.removeIPv4Address(domain2, DEFAULT_TTL, ip2);
        subscription1.request(1);
        subscription2.request(1);
        assertThat(subscriber1.awaitOnError(), instanceOf(UnknownHostException.class));
        assertThat(subscriber2.awaitOnError(), instanceOf(UnknownHostException.class));
    }

    @Test
    void repeatDiscoverNxDomainAndRecover() throws Exception {
        client.closeAsync().toFuture().get();
        client = dnsClientBuilderWithRetry().inactiveEventsOnError(true).build();
        final String ip = nextIp();
        final String domain = "servicetalk.io";
        recordStore.addIPv4Address(domain, DEFAULT_TTL, ip);

        TestPublisherSubscriber<ServiceDiscovererEvent<InetAddress>> subscriber = dnsQuery(domain);
        Subscription subscription = subscriber.awaitSubscription();
        subscription.request(4);

        assertEvent(subscriber.takeOnNext(), ip, true);
        recordStore.removeIPv4Address(domain, DEFAULT_TTL, ip);
        assertEvent(subscriber.takeOnNext(), ip, false);
        recordStore.addIPv4Address(domain, DEFAULT_TTL, ip);
        assertEvent(subscriber.takeOnNext(), ip, true);
    }

    @Test
    void preferIpv4() throws Exception {
        client.closeAsync().toFuture().get();
        client = dnsClientBuilder().completeOncePreferredResolved(false)
                .dnsResolverAddressTypes(IPV4_PREFERRED).build();

        final String ipv4 = nextIp();
        final String ipv6 = nextIp6();
        final String domain = "servicetalk.io";
        recordStore.addIPv6Address(domain, DEFAULT_TTL, ipv6);
        recordStore.addIPv4Address(domain, DEFAULT_TTL, ipv4);

        TestPublisherSubscriber<ServiceDiscovererEvent<InetAddress>> subscriber = dnsQuery(domain);
        Subscription subscription = subscriber.awaitSubscription();
        subscription.request(Long.MAX_VALUE);

        List<ServiceDiscovererEvent<InetAddress>> signals = subscriber.takeOnNext(2);
        assertHasEvent(signals, ipv4, true);
        assertHasEvent(signals, ipv6, true);

        // Remove the ipv4
        recordStore.removeIPv4Address(domain, DEFAULT_TTL, ipv4);
        assertEvent(subscriber.takeOnNext(), ipv4, false);
    }

    @Test
    void preferIpv4ButOnlyAAAARecordIsPresent() throws Exception {
        client.closeAsync().toFuture().get();
        client = dnsClientBuilder().dnsResolverAddressTypes(IPV4_PREFERRED).build();
        final String ipv6 = nextIp6();
        final String domain = "servicetalk.io";
        recordStore.addIPv6Address(domain, DEFAULT_TTL, ipv6);

        TestPublisherSubscriber<ServiceDiscovererEvent<InetAddress>> subscriber = dnsQuery(domain);
        Subscription subscription = subscriber.awaitSubscription();
        subscription.request(Long.MAX_VALUE);

        assertEvent(subscriber.takeOnNext(), ipv6, true);

        // Remove all ips
        recordStore.removeIPv6Address(domain, DEFAULT_TTL, ipv6);
        assertThat(subscriber.awaitOnError(), instanceOf(UnknownHostException.class));
    }

    @Test
    void acceptOnlyIpv6() throws Exception {
        client.closeAsync().toFuture().get();
        client = dnsClientBuilder().dnsResolverAddressTypes(IPV6_ONLY).build();
        final String ipv6 = nextIp6();
        final String domain = "servicetalk.io";
        recordStore.addIPv6Address(domain, DEFAULT_TTL, ipv6);
        recordStore.addIPv4Address(domain, DEFAULT_TTL, nextIp());

        TestPublisherSubscriber<ServiceDiscovererEvent<InetAddress>> subscriber = dnsQuery(domain);
        Subscription subscription = subscriber.awaitSubscription();
        subscription.request(Long.MAX_VALUE);

        assertEvent(subscriber.takeOnNext(), ipv6, true);
    }

    @Test
    void exceptionInSubscriberOnNext() throws Exception {
        final String domain = "servicetalk.io";
        final String ip = nextIp();
        recordStore.addIPv4Address(domain, DEFAULT_TTL, ip);
        CountDownLatch latchOnError = new CountDownLatch(1);
        BlockingQueue<ServiceDiscovererEvent<InetAddress>> queue = new ArrayBlockingQueue<>(10);
        toSource(client.dnsQuery(domain).flatMapConcatIterable(identity())).subscribe(
                mockThrowSubscriber(latchOnError, queue));
        assertEvent(queue.take(), ip, true);
        latchOnError.await();
    }

    @Test
    void srvExceptionInSubscriberOnNext() throws Exception {
        client.closeAsync().toFuture().get();
        client = dnsClientBuilder().srvHostNameRepeatDelay(ofMillis(50), ofMillis(10)).build();
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
        assertEvent(queue.take(), ip, targetPort, true);
        assertEvent(queue.take(), ip, targetPort, false);
        // Remove the srv address because the mapped publishers don't propagate errors, so we want the outer SRV resolve
        // to fail.
        recordStore.removeSrv(domain, targetDomain1, targetPort, DEFAULT_TTL);
        latchOnError.await();
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
        Publisher<ServiceDiscovererEvent<InetAddress>> publisher = client.dnsQuery(domain)
                .flatMapConcatIterable(identity());
        TestPublisherSubscriber<ServiceDiscovererEvent<InetAddress>> subscriber =
                new TestPublisherSubscriber<>();
        toSource(publisher).subscribe(subscriber);
        return subscriber;
    }

    private DefaultDnsServiceDiscovererBuilder dnsClientBuilder() {
        return new DefaultDnsServiceDiscovererBuilder()
                .ioExecutor(nettyIoExecutor)
                .dnsResolverAddressTypes(IPV4_ONLY)
                .optResourceEnabled(false)
                .srvConcurrency(512)
                .dnsServerAddressStreamProvider(new SingletonDnsServerAddressStreamProvider(dnsServer.localAddress()))
                .ndots(1)
                .minTTL(1);
    }

    private DefaultDnsServiceDiscovererBuilder dnsClientBuilderWithRetry() {
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

    private static void assertEvent(@Nullable ServiceDiscovererEvent<InetSocketAddress> event,
                                    String ip, int port, boolean available) throws UnknownHostException {
        assertThat(event, is(new DefaultServiceDiscovererEvent<>(
                new InetSocketAddress(getByName(ip), port), available)));
    }

    private static void assertEvent(@Nullable ServiceDiscovererEvent<InetAddress> event,
                                    String ip, boolean available) throws UnknownHostException {
        assertThat(event, is(new DefaultServiceDiscovererEvent<>(getByName(ip), available)));
    }

    @SuppressWarnings("unchecked")
    private static void assertHasEvent(Collection<ServiceDiscovererEvent<InetAddress>> events,
                                       String ip, boolean available) throws UnknownHostException {
        assertThat(events, hasItems(new DefaultServiceDiscovererEvent<>(getByName(ip), available)));
    }

    @SuppressWarnings("unchecked")
    private static void assertHasEvent(Collection<ServiceDiscovererEvent<InetSocketAddress>> events,
                                       String ip, int port, boolean available) throws UnknownHostException {
        assertThat(events, hasItems(new DefaultServiceDiscovererEvent<>(
                new InetSocketAddress(getByName(ip), port), available)));
    }
}
