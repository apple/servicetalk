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

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.api.CompositeCloseable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.dns.discovery.netty.DnsServiceDiscovererObserver.DnsDiscoveryObserver;
import io.servicetalk.dns.discovery.netty.DnsServiceDiscovererObserver.DnsResolutionObserver;
import io.servicetalk.dns.discovery.netty.DnsServiceDiscovererObserver.ResolutionResult;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.UnknownHostException;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.function.BiFunction;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.AsyncCloseables.newCompositeCloseable;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.dns.discovery.netty.DnsTestUtils.nextIp;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DnsServiceDiscovererObserverTest {
    private static final String HOST_NAME = "servicetalk.io";
    private static final String SERVICE_NAME = "servicetalk";
    private static final String INVALID = "invalid.";
    private static final int DEFAULT_TTL = 1;

    private final TestRecordStore recordStore = new TestRecordStore();
    private final TestDnsServer dnsServer = new TestDnsServer(recordStore);
    private final CompositeCloseable toClose = newCompositeCloseable();

    @BeforeEach
    public void setUp() throws Exception {
        recordStore.addIPv4Address(HOST_NAME, DEFAULT_TTL, nextIp(), nextIp());
        recordStore.addSrv(SERVICE_NAME, HOST_NAME, 443, DEFAULT_TTL);
        dnsServer.start();
    }

    @AfterEach
    public void tearDown() throws Exception {
        try {
            toClose.closeGracefully();
        } finally {
            dnsServer.stop();
        }
    }

    private DnsClient dnsClient(DnsServiceDiscovererObserver observer) {
        return toClose.append(new DefaultDnsServiceDiscovererBuilder()
                .observer(observer)
                .dnsResolverAddressTypes(DnsResolverAddressTypes.IPV4_PREFERRED)
                .optResourceEnabled(false)
                .dnsServerAddressStreamProvider(new SingletonDnsServerAddressStreamProvider(dnsServer.localAddress()))
                .ndots(1)
                .minTTL(1)
                .build());
    }

    @Test
    void aQueryTriggersNewDiscoveryObserver() throws Exception {
        testNewDiscoveryObserver(DnsClient::dnsQuery, HOST_NAME);
    }

    @Test
    void srvQueryTriggersNewDiscoveryObserver() throws Exception {
        testNewDiscoveryObserver(DnsClient::dnsSrvQuery, SERVICE_NAME);
    }

    private void testNewDiscoveryObserver(BiFunction<DnsClient, String, Publisher<?>> publisherFactory,
                                          String expectedName) throws Exception {
        BlockingQueue<String> newDiscoveryCalls = new LinkedBlockingDeque<>();
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
    void aQueryTriggersNewResolutionObserver() throws Exception {
        BlockingQueue<String> newResolution = new LinkedBlockingDeque<>();
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
    void srvQueryTriggersNewResolutionObserver() throws Exception {
        BlockingQueue<String> newResolution = new LinkedBlockingDeque<>();
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
    void aQueryFailedResolution() {
        testFailedResolution(DnsClient::dnsQuery);
    }

    @Test
    void srvQueryFailedResolution() {
        testFailedResolution(DnsClient::dnsSrvQuery);
    }

    private void testFailedResolution(BiFunction<DnsClient, String, Publisher<?>> publisherFactory) {
        BlockingQueue<Throwable> resolutionFailures = new LinkedBlockingDeque<>();
        DnsClient client = dnsClient(__ -> name -> new NoopDnsResolutionObserver() {
            @Override
            public void resolutionFailed(final Throwable cause) {
                resolutionFailures.add(cause);
            }
        });

        Publisher<?> publisher = publisherFactory.apply(client, INVALID);
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
    void aQueryResolutionResultNoUpdates() throws Exception {
        aQueryResolutionResult(results -> {
            assertResolutionResult(results.take(), 2, 2, 0);
            assertResolutionResult(results.take(), 2, 0, 0);
        });
    }

    @Test
    void aQueryResolutionResultNewIPsAvailable() throws Exception {
        aQueryResolutionResult(results -> {
            assertResolutionResult(results.take(), 2, 2, 0);

            recordStore.addIPv4Address(HOST_NAME, DEFAULT_TTL, nextIp(), nextIp());
            assertResolutionResult(results.take(), 4, 2, 0);
        });
    }

    @Test
    void aQueryResolutionResultOneBecameUnavailable() throws Exception {
        final String tmpIP = nextIp();
        recordStore.addIPv4Address(HOST_NAME, DEFAULT_TTL, tmpIP);
        aQueryResolutionResult(results -> {
            assertResolutionResult(results.take(), 3, 3, 0);

            recordStore.removeIPv4Address(HOST_NAME, DEFAULT_TTL, tmpIP);
            assertResolutionResult(results.take(), 2, 0, 1);
        });
    }

    @Test
    void aQueryResolutionResultNewAvailableOneUnavailable() throws Exception {
        final String tmpIP = nextIp();
        recordStore.addIPv4Address(HOST_NAME, DEFAULT_TTL, tmpIP);
        aQueryResolutionResult(results -> {
            assertResolutionResult(results.take(), 3, 3, 0);

            recordStore.removeIPv4Address(HOST_NAME, DEFAULT_TTL, tmpIP);
            recordStore.addIPv4Address(HOST_NAME, DEFAULT_TTL, nextIp());
            assertResolutionResult(results.take(), 3, 1, 1);
        });
    }

    @Test
    void aQueryResolutionResultAllNewIPs() throws Exception {
        aQueryResolutionResult(results -> {
            assertResolutionResult(results.take(), 2, 2, 0);

            recordStore.removeIPv4Addresses(HOST_NAME);
            recordStore.addIPv4Address(HOST_NAME, DEFAULT_TTL, nextIp(), nextIp(), nextIp());
            assertResolutionResult(results.take(), 3, 3, 2);
        });
    }

    private void aQueryResolutionResult(ResultsVerifier<BlockingQueue<ResolutionResult>> verifier) throws Exception {
        BlockingQueue<ResolutionResult> results = new LinkedBlockingDeque<>();
        DnsClient client = dnsClient(__ -> name -> new NoopDnsResolutionObserver() {
            @Override
            public void resolutionCompleted(final ResolutionResult result) {
                results.add(result);
            }
        });

        assertThat("Unexpected calls to resolutionCompleted", results, hasSize(0));
        Cancellable discovery = client.dnsQuery(HOST_NAME).forEach(__ -> { });
        try {
            verifier.verify(results);
        } finally {
            discovery.cancel();
        }
    }

    private static void assertResolutionResult(@Nullable ResolutionResult result,
                                               int resolvedRecords, int nAvailable, int nUnavailable) {
        assertThat("Unexpected null ResolutionResult", result, is(notNullValue()));
        assertThat("Unexpected number of resolvedRecords", result.resolvedRecords(), is(resolvedRecords));
        assertThat("Unexpected TTL value", result.ttl(), is(DEFAULT_TTL));
        assertThat("Unexpected number of nAvailable records", result.nAvailable(), is(nAvailable));
        assertThat("Unexpected number of nUnavailable records", result.nUnavailable(), is(nUnavailable));
    }

    @Test
    void srvQueryResolutionResult() throws Exception {
        Map<String, ResolutionResult> results = new ConcurrentHashMap<>();
        DnsClient client = dnsClient(__ -> name -> new NoopDnsResolutionObserver() {
            @Override
            public void resolutionCompleted(final ResolutionResult result) {
                results.put(name, result);
            }
        });

        Publisher<?> publisher = client.dnsSrvQuery(SERVICE_NAME);
        assertThat("Unexpected calls to resolutionCompleted", results.entrySet(), hasSize(0));
        // Wait until SD returns at least one address:
        publisher.takeAtMost(1).ignoreElements().toFuture().get();
        assertThat("Unexpected number of calls to resolutionCompleted", results.entrySet(), hasSize(2));

        assertResolutionResult(results.get(SERVICE_NAME), 1, 1, 0);
        assertResolutionResult(results.get(HOST_NAME + '.'), 2, 2, 0);
    }

    @Test
    void aQueryOnNewDiscoveryThrows() throws Exception {
        DnsServiceDiscovererObserver observer = mock(DnsServiceDiscovererObserver.class);
        when(observer.onNewDiscovery(anyString())).thenThrow(DELIBERATE_EXCEPTION);

        DnsClient client = dnsClient(observer);
        Publisher<?> publisher = client.dnsQuery(HOST_NAME);
        verifyNoInteractions(observer);
        // Wait until SD returns at least one address:
        publisher.takeAtMost(1).ignoreElements().toFuture().get();
        verify(observer).onNewDiscovery(HOST_NAME);
    }

    @Test
    void srvQueryOnNewDiscoveryThrows() throws Exception {
        DnsServiceDiscovererObserver observer = mock(DnsServiceDiscovererObserver.class);
        when(observer.onNewDiscovery(anyString())).thenThrow(DELIBERATE_EXCEPTION);

        DnsClient client = dnsClient(observer);
        Publisher<?> publisher = client.dnsSrvQuery(SERVICE_NAME);
        verifyNoInteractions(observer);
        // Wait until SD returns at least one address:
        publisher.takeAtMost(1).ignoreElements().toFuture().get();
        verify(observer).onNewDiscovery(SERVICE_NAME);
    }

    @Test
    void onNewResolutionThrows() throws Exception {
        DnsServiceDiscovererObserver observer = mock(DnsServiceDiscovererObserver.class);
        DnsDiscoveryObserver discoveryObserver = mock(DnsDiscoveryObserver.class);
        when(observer.onNewDiscovery(anyString())).thenReturn(discoveryObserver);
        when(discoveryObserver.onNewResolution(anyString())).thenThrow(DELIBERATE_EXCEPTION);

        DnsClient client = dnsClient(observer);
        Publisher<?> publisher = client.dnsQuery(HOST_NAME);
        verifyNoInteractions(observer, discoveryObserver);
        // Wait until SD returns at least one address:
        publisher.takeAtMost(1).ignoreElements().toFuture().get();
        verify(observer).onNewDiscovery(HOST_NAME);
        verify(discoveryObserver).onNewResolution(HOST_NAME);
    }

    @Test
    void resolutionFailedThrows() throws Exception {
        DnsServiceDiscovererObserver observer = mock(DnsServiceDiscovererObserver.class);
        DnsDiscoveryObserver discoveryObserver = mock(DnsDiscoveryObserver.class);
        DnsResolutionObserver resolutionObserver = mock(DnsResolutionObserver.class);
        when(observer.onNewDiscovery(anyString())).thenReturn(discoveryObserver);
        when(discoveryObserver.onNewResolution(anyString())).thenReturn(resolutionObserver);
        doThrow(DELIBERATE_EXCEPTION).when(resolutionObserver).resolutionFailed(any());

        DnsClient client = dnsClient(observer);
        Publisher<?> publisher = client.dnsQuery(INVALID);
        verifyNoInteractions(observer, discoveryObserver, resolutionObserver);
        // Wait until SD returns at least one address:
        ExecutionException ee = assertThrows(ExecutionException.class,
                () -> publisher.takeAtMost(1).ignoreElements().toFuture().get());
        verify(observer).onNewDiscovery(INVALID);
        verify(discoveryObserver).onNewResolution(INVALID);
        verify(resolutionObserver).resolutionFailed(ee.getCause());
    }

    @Test
    void resolutionCompletedThrows() throws Exception {
        DnsServiceDiscovererObserver observer = mock(DnsServiceDiscovererObserver.class);
        DnsDiscoveryObserver discoveryObserver = mock(DnsDiscoveryObserver.class);
        DnsResolutionObserver resolutionObserver = mock(DnsResolutionObserver.class);
        when(observer.onNewDiscovery(anyString())).thenReturn(discoveryObserver);
        when(discoveryObserver.onNewResolution(anyString())).thenReturn(resolutionObserver);
        doThrow(DELIBERATE_EXCEPTION).when(resolutionObserver).resolutionCompleted(any());

        DnsClient client = dnsClient(observer);
        Publisher<?> publisher = client.dnsQuery(HOST_NAME);
        verifyNoInteractions(observer, discoveryObserver, resolutionObserver);
        // Wait until SD returns at least one address:
        publisher.takeAtMost(1).ignoreElements().toFuture().get();
        verify(observer).onNewDiscovery(HOST_NAME);
        verify(discoveryObserver).onNewResolution(HOST_NAME);
        verify(resolutionObserver).resolutionCompleted(any());
    }

    private static final class NoopDnsDiscoveryObserver implements DnsDiscoveryObserver {
        static final DnsDiscoveryObserver INSTANCE = new NoopDnsDiscoveryObserver();

        private NoopDnsDiscoveryObserver() {
            // Singleton
        }

        @Override
        public DnsResolutionObserver onNewResolution(final String name) {
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

    @FunctionalInterface
    private interface ResultsVerifier<T> {
        void verify(T t) throws Exception;
    }
}
