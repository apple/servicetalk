/*
 * Copyright © 2020 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.api.CompositeCloseable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.dns.discovery.netty.DnsServiceDiscovererObserver.DnsDiscoveryObserver;
import io.servicetalk.dns.discovery.netty.DnsServiceDiscovererObserver.DnsDiscoveryObserver.DnsResolutionObserver;
import io.servicetalk.dns.discovery.netty.DnsServiceDiscovererObserver.DnsDiscoveryObserver.DnsResolutionObserver.ResolutionResult;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.function.BiFunction;

import static io.servicetalk.concurrent.api.AsyncCloseables.newCompositeCloseable;
import static io.servicetalk.dns.discovery.netty.DnsTestUtils.nextIp;
import static io.servicetalk.dns.discovery.netty.TestRecordStore.DEFAULT_TTL;
import static io.servicetalk.dns.discovery.netty.TestRecordStore.createSrvRecord;
import static java.util.Collections.singletonList;
import static org.apache.directory.server.dns.messages.RecordType.A;
import static org.apache.directory.server.dns.messages.RecordType.SRV;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.assertThrows;

public class DnsServiceDiscovererObserverTest {
    private static final String HOST_NAME = "servicetalk.io";
    private static final String SERVICE_NAME = "servicetalk";

    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();

    private final TestRecordStore recordStore = new TestRecordStore();
    private final TestDnsServer dnsServer = new TestDnsServer(recordStore);
    private final CompositeCloseable toClose = newCompositeCloseable();

    @Before
    public void setUp() throws Exception {
        recordStore.defaultResponse(HOST_NAME, A, nextIp(), nextIp());
        recordStore.defaultResponse(SERVICE_NAME, SRV, () -> singletonList(
                createSrvRecord(SERVICE_NAME, HOST_NAME, 10, 10, 443, DEFAULT_TTL)));
        dnsServer.start();
    }

    @After
    public void tearDown() throws Exception {
        try {
            toClose.closeGracefully();
        } finally {
            dnsServer.stop();
        }
    }

    private DnsClient dnsClient(DnsServiceDiscovererObserver observerFactory) {
        return toClose.append(new DefaultDnsServiceDiscovererBuilder()
                .observer(observerFactory)
                .dnsResolverAddressTypes(DnsResolverAddressTypes.IPV4_PREFERRED)
                .optResourceEnabled(false)
                .dnsServerAddressStreamProvider(new SingletonDnsServerAddressStreamProvider(
                        new SingletonDnsServerAddresses(dnsServer.localAddress())))
                .ndots(1)
                .minTTL(1)
                .build());
    }

    @Test
    public void aQueryTriggersNewDiscoveryObserver() throws Exception {
        testNewDiscoveryObserver(DnsClient::dnsQuery, HOST_NAME);
    }

    @Test
    public void srvQueryTriggersNewDiscoveryObserver() throws Exception {
        testNewDiscoveryObserver(DnsClient::dnsSrvQuery, SERVICE_NAME);
    }

    private void testNewDiscoveryObserver(BiFunction<DnsClient, String, Publisher<?>> publisherFactory,
                                          String expectedName) throws Exception {
        List<String> newDiscoveryCalls = new ArrayList<>();
        DnsClient client = dnsClient(name -> {
            newDiscoveryCalls.add(name);
            return NoopDnsDiscoveryObserver.INSTANCE;
        });

        Publisher<?> publisher = publisherFactory.apply(client, expectedName);
        assertThat("Unexpected calls to newDiscovery(name)", newDiscoveryCalls, hasSize(0));
        // Wait until SD returns at least one address:
        publisher.takeAtMost(1).ignoreElements().toFuture().get();
        assertThat("Unexpected number of calls to newDiscovery(name)", newDiscoveryCalls, hasSize(1));
        assertThat("Unexpected name for newDiscovery(name)", newDiscoveryCalls, hasItem(equalTo(expectedName)));
    }

    @Test
    public void aQueryTriggersNewResolutionObserver() throws Exception {
        List<String> newResolution = new ArrayList<>();
        DnsClient client = dnsClient(__ -> name -> {
            newResolution.add(name);
            return NoopDnsResolutionObserver.INSTANCE;
        });

        Publisher<?> publisher = client.dnsQuery(HOST_NAME);
        assertThat("Unexpected calls to newResolution(name)", newResolution, hasSize(0));
        // Wait until SD returns at least one address:
        publisher.takeAtMost(1).ignoreElements().toFuture().get();
        assertThat("Unexpected number of calls to newResolution(name)", newResolution, hasSize(1));
        assertThat("Unexpected name for newResolution(name)", newResolution, hasItem(equalTo(HOST_NAME)));
    }

    @Test
    public void srvQueryTriggersNewResolutionObserver() throws Exception {
        System.err.println(NoopDnsResolutionObserver.INSTANCE.toString());
        List<String> newResolution = new ArrayList<>();
        DnsClient client = dnsClient(__ -> name -> {
            newResolution.add(name);
            return NoopDnsResolutionObserver.INSTANCE;
        });

        Publisher<?> publisher = client.dnsSrvQuery(SERVICE_NAME);
        assertThat("Unexpected calls to newResolution(name)", newResolution, hasSize(0));
        // Wait until SD returns at least one address:
        publisher.takeAtMost(1).ignoreElements().toFuture().get();
        assertThat("Unexpected number of calls to newResolution(name)", newResolution,
                hasSize(greaterThanOrEqualTo(2)));
        assertThat("Unexpected name for newResolution(name)", newResolution, hasItem(equalTo(SERVICE_NAME)));
        assertThat("Unexpected name for newResolution(name)", newResolution,
                hasItem(anyOf(equalTo(HOST_NAME), equalTo(HOST_NAME + '.'))));
    }

    @Test
    public void aQueryFailedResolution() {
        testFailedResolution(DnsClient::dnsQuery);
    }

    @Test
    public void srvQueryFailedResolution() {
        testFailedResolution(DnsClient::dnsSrvQuery);
    }

    private void testFailedResolution(BiFunction<DnsClient, String, Publisher<?>> publisherFactory) {
        List<Throwable> resolutionFailures = new ArrayList<>();
        DnsClient client = dnsClient(__ -> name -> new NoopDnsResolutionObserver() {
            @Override
            public void resolutionFailed(final Throwable cause) {
                resolutionFailures.add(cause);
            }
        });

        Publisher<?> publisher = publisherFactory.apply(client, "invalid.");
        assertThat("Unexpected calls to resolutionFailed(t)", resolutionFailures, hasSize(0));
        // Wait until SD returns at least one address:
        ExecutionException ee = assertThrows(ExecutionException.class,
                () -> publisher.takeAtMost(1).ignoreElements().toFuture().get());
        Throwable cause = ee.getCause();
        assertThat(cause, instanceOf(UnknownHostException.class));
        assertThat("Unexpected number of calls to resolutionFailed(t)", resolutionFailures, hasSize(1));
        assertThat("Unexpected name for resolutionFailed(t)", resolutionFailures, hasItem(sameInstance(cause)));
    }

    @Test
    public void aQueryResolutionResult() throws Exception {
        List<ResolutionResult> results = new ArrayList<>();
        DnsClient client = dnsClient(__ -> name -> new NoopDnsResolutionObserver() {
            @Override
            public void resolutionCompleted(final ResolutionResult result) {
                results.add(result);
            }
        });

        Publisher<?> publisher = client.dnsQuery(HOST_NAME);
        assertThat("Unexpected calls to resolutionComplete", results, hasSize(0));
        // Wait until SD returns at least one address:
        publisher.takeAtMost(1).ignoreElements().toFuture().get();
        assertThat("Unexpected number of calls to resolutionComplete", results, hasSize(1));
        ResolutionResult result = results.get(0);
        assertThat(result.resolvedRecords(), is(2));
        assertThat(result.ttl(), is(DEFAULT_TTL));
        assertThat(result.becameActive(), is(2));
        assertThat(result.becameInactive(), is(0));
    }

    @Test
    public void srvQueryResolutionResult() throws Exception {
        Map<String, ResolutionResult> results = new HashMap<>();
        DnsClient client = dnsClient(__ -> name -> new NoopDnsResolutionObserver() {
            @Override
            public void resolutionCompleted(final ResolutionResult result) {
                results.put(name, result);
            }
        });

        Publisher<?> publisher = client.dnsSrvQuery(SERVICE_NAME);
        assertThat("Unexpected calls to resolutionComplete", results.entrySet(), hasSize(0));
        // Wait until SD returns at least one address:
        publisher.takeAtMost(1).ignoreElements().toFuture().get();
        assertThat("Unexpected number of calls to resolutionComplete", results.entrySet(), hasSize(2));

        ResolutionResult srvResult = results.get(SERVICE_NAME);
        assertThat(srvResult.resolvedRecords(), is(1));
        assertThat(srvResult.ttl(), is(DEFAULT_TTL));
        assertThat(srvResult.becameActive(), is(1));
        assertThat(srvResult.becameInactive(), is(0));

        ResolutionResult dnsResult = results.get(HOST_NAME + '.');
        assertThat(dnsResult.resolvedRecords(), is(2));
        assertThat(dnsResult.ttl(), is(DEFAULT_TTL));
        assertThat(dnsResult.becameActive(), is(2));
        assertThat(dnsResult.becameInactive(), is(0));
    }

    private static final class NoopDnsDiscoveryObserver implements DnsDiscoveryObserver {
        static final DnsDiscoveryObserver INSTANCE = new NoopDnsDiscoveryObserver();

        private NoopDnsDiscoveryObserver() {
            // Singleton
        }

        @Override
        public DnsResolutionObserver newResolution(final String name) {
            return NoopDnsResolutionObserver.INSTANCE;
        }
    }

    private static class NoopDnsResolutionObserver implements DnsResolutionObserver {
        static final DnsResolutionObserver INSTANCE = new NoopDnsResolutionObserver();

        @Override
        public void resolutionFailed(final Throwable cause) {
            // noop
        }

        @Override
        public void resolutionCompleted(final ResolutionResult result) {
            // noop
        }
    }
}
