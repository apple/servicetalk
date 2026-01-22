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
package io.servicetalk.http.netty;

import io.servicetalk.client.api.DefaultServiceDiscovererEvent;
import io.servicetalk.client.api.DelegatingServiceDiscoverer;
import io.servicetalk.client.api.ServiceDiscoverer;
import io.servicetalk.client.api.ServiceDiscovererEvent;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.DelegatingListenableAsyncCloseable;
import io.servicetalk.concurrent.api.ListenableAsyncCloseable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.internal.DeliberateException;
import io.servicetalk.dns.discovery.netty.DnsServiceDiscoverers;
import io.servicetalk.http.api.BlockingHttpClient;
import io.servicetalk.http.api.HttpHeaderNames;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.HttpServerContext;
import io.servicetalk.http.api.SingleAddressHttpClientBuilder;
import io.servicetalk.transport.api.HostAndPort;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mockito;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.Collection;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nullable;

import static io.servicetalk.client.api.ServiceDiscovererEvent.Status.AVAILABLE;
import static io.servicetalk.client.api.ServiceDiscovererEvent.Status.UNAVAILABLE;
import static io.servicetalk.concurrent.api.AsyncCloseables.emptyAsyncCloseable;
import static io.servicetalk.concurrent.api.Publisher.never;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.dns.discovery.netty.DnsServiceDiscoverers.globalARecordsDnsServiceDiscoverer;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.netty.HttpClients.DiscoveryStrategy.ON_NEW_CONNECTION;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static java.net.InetSocketAddress.createUnresolved;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static java.util.Objects.requireNonNull;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

class HttpClientResolvesOnNewConnectionTest {

    @Test
    void forMultiAddressUrl() throws Exception {
        try (HttpServerContext serverContext = HttpServers.forAddress(localAddress(0))
                .listenBlockingAndAwait((ctx, request, responseFactory) -> responseFactory.ok());
             // Use "localhost" to demonstrate that the address will be resolved.
             BlockingHttpClient client = HttpClients.forMultiAddressUrl(getClass().getSimpleName(), ON_NEW_CONNECTION)
                     .buildBlocking()) {
            HttpResponse response = client.request(
                    client.get("http://localhost:" + serverHostAndPort(serverContext).port() + '/'));
            assertThat(response.status(), is(OK));
        }
    }

    @Test
    void forMultiAddressUrlWithCustomServiceDiscoverer() throws Exception {
        AtomicInteger discoverCounter = new AtomicInteger();
        try (HttpServerContext serverContext = HttpServers.forAddress(localAddress(0))
                .listenBlockingAndAwait((ctx, request, responseFactory) -> responseFactory.ok());
             // Use "localhost" to demonstrate that the address will be resolved.
             BlockingHttpClient client = HttpClients.forMultiAddressUrl(getClass().getSimpleName(),
                     // Wrap to pretend this is a custom SD:
                     new DelegatingServiceDiscoverer<HostAndPort, InetSocketAddress,
                             ServiceDiscovererEvent<InetSocketAddress>>(globalARecordsDnsServiceDiscoverer()) {
                         @Override
                         public Publisher<Collection<ServiceDiscovererEvent<InetSocketAddress>>> discover(
                                 HostAndPort hostAndPort) {
                             discoverCounter.incrementAndGet();
                             return delegate().discover(hostAndPort);
                         }
                     },
                     ON_NEW_CONNECTION).buildBlocking()) {
            HttpResponse response = client.request(
                    client.get("http://localhost:" + serverHostAndPort(serverContext).port() + '/'));
            assertThat(response.status(), is(OK));
            assertThat(discoverCounter.get(), is(greaterThan(0)));
        }
    }

    @ParameterizedTest(name = "{displayName} [{index}]: protocol={0}")
    @EnumSource(HttpProtocol.class)
    void forHostAndPort(HttpProtocol protocol) throws Exception {
        try (HttpServerContext serverContext = HttpServers.forAddress(localAddress(0))
                .protocols(protocol.config)
                .listenBlockingAndAwait((ctx, request, responseFactory) -> responseFactory.ok());
             // Use "localhost" to demonstrate that the address will be resolved.
             BlockingHttpClient client = HttpClients.forSingleAddress("localhost",
                             serverHostAndPort(serverContext).port(), ON_NEW_CONNECTION)
                     .protocols(protocol.config)
                     .buildBlocking()) {
            HttpResponse response = client.request(client.get("/"));
            assertThat(response.status(), is(OK));
        }
    }

    @ParameterizedTest(name = "{displayName} [{index}]: protocol={0}")
    @EnumSource(HttpProtocol.class)
    void withCustomDnsConfig(HttpProtocol protocol) throws Exception {
        ServiceDiscoverer<HostAndPort, InetSocketAddress, ServiceDiscovererEvent<InetSocketAddress>> spyDnsSd =
                Mockito.spy(DnsServiceDiscoverers.builder(getClass().getSimpleName())
                        .ttlJitter(Duration.ofSeconds(1))
                        .buildARecordDiscoverer());
        try (HttpServerContext serverContext = HttpServers.forAddress(localAddress(0))
                .protocols(protocol.config)
                .listenBlockingAndAwait((ctx, request, responseFactory) -> responseFactory.ok());
             // Use "localhost" to demonstrate that the address will be resolved.
             BlockingHttpClient client = HttpClients.forSingleAddress(spyDnsSd,
                             HostAndPort.of("localhost", serverHostAndPort(serverContext).port()), ON_NEW_CONNECTION)
                     .protocols(protocol.config)
                     .buildBlocking()) {
            HttpResponse response = client.request(client.get("/"));
            assertThat(response.status(), is(OK));
            verify(spyDnsSd).discover(any());
            verifyNoMoreInteractions(spyDnsSd);
        } finally {
            spyDnsSd.closeAsync().toFuture().get();
        }
    }

    @ParameterizedTest(name = "{displayName} [{index}]: protocol={0}")
    @EnumSource(HttpProtocol.class)
    void withCustomServiceDiscoverer(HttpProtocol protocol) throws Exception {
        ServiceDiscoverer<UnresolvedAddress, InetSocketAddress, ServiceDiscovererEvent<InetSocketAddress>> customSd =
                new CustomServiceDiscoverer();
        try (HttpServerContext serverContext = HttpServers.forAddress(localAddress(0))
                .protocols(protocol.config)
                .listenBlockingAndAwait((ctx, request, responseFactory) -> responseFactory.ok());
             // Use "localhost" to demonstrate that the address will be resolved.
             BlockingHttpClient client = HttpClients.forSingleAddress(customSd,
                             new UnresolvedAddress(serverContext.listenAddress()), ON_NEW_CONNECTION)
                     .protocols(protocol.config)
                     .buildBlocking()) {
            HttpResponse response = client.request(client.get("/"));
            assertThat(response.status(), is(OK));
        } finally {
            customSd.closeAsync().toFuture().get();
        }
    }

    @Test
    void serviceDiscovererCanBeOverridden() throws Exception {
        try (HttpServerContext serverContext = HttpServers.forAddress(localAddress(0))
                .listenBlockingAndAwait((ctx, request, responseFactory) -> responseFactory.ok())) {
            CountingServiceDiscoverer firstSd = new CountingServiceDiscoverer();
            CountingServiceDiscoverer secondSd = new CountingServiceDiscoverer();

            try (BlockingHttpClient client = HttpClients.forSingleAddress(firstSd,
                    HostAndPort.of("localhost", serverHostAndPort(serverContext).port()), ON_NEW_CONNECTION)
                    .serviceDiscoverer(secondSd)  // Override the first one
                    .buildBlocking()) {

                HttpResponse response = client.request(client.get("/"));
                assertThat(response.status(), is(OK));

                // Verify that only the second service discoverer was used
                assertThat("First service discoverer should not be used", firstSd.getDiscoverCount(), is(0));
                assertThat("Second service discoverer should be used", secondSd.getDiscoverCount(), is(greaterThan(0)));
            } finally {
                firstSd.closeAsync().toFuture().get();
                secondSd.closeAsync().toFuture().get();
            }
        }
    }

    @Test
    void sameBuilderDifferentServiceDiscoverersTest() throws Exception {
        try (HttpServerContext serverContext = HttpServers.forAddress(localAddress(0))
                .listenBlockingAndAwait((ctx, request, responseFactory) ->
                        responseFactory.ok().addHeader(HttpHeaderNames.CONNECTION, "close"))) {
            CountingServiceDiscoverer firstSd = new CountingServiceDiscoverer();
            CountingServiceDiscoverer secondSd = new CountingServiceDiscoverer();

            // Ensure HTTP/1.1 for Connection: close support
            SingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> builder =
                    HttpClients.forSingleAddress(firstSd, serverHostAndPort(serverContext), ON_NEW_CONNECTION)
                            .protocols(HttpProtocolConfigs.h1Default());

            try (BlockingHttpClient client1 = builder.buildBlocking()) {
                // Test client1 - should use first service discoverer
                // Server will send Connection: close to ensure connection is closed after each request
                HttpResponse response1 = client1.request(client1.get("/test1"));
                assertThat(response1.status(), is(OK));
                int firstSdCountAfterFirstRequest = firstSd.getDiscoverCount();
                assertThat("First service discoverer should be used by client1",
                        firstSdCountAfterFirstRequest, is(greaterThan(0)));

                // Now change the service discoverer on the same builder
                builder.serviceDiscoverer(secondSd);

                // Build client2 with second service discoverer
                try (BlockingHttpClient client2 = builder.buildBlocking()) {

                    // Test client2 - should use second service discoverer
                    HttpResponse response2 = client2.request(client2.get("/test2"));
                    assertThat(response2.status(), is(OK));
                    int secondSdCountAfterFirstRequest = secondSd.getDiscoverCount();
                    assertThat("Second service discoverer should be used by client2",
                            secondSdCountAfterFirstRequest, is(greaterThan(0)));

                    // Make another request to client1 - it should still use the first service discoverer
                    // and create a new connection (since previous was closed by server)
                    HttpResponse response3 = client1.request(client1.get("/test3"));
                    assertThat(response3.status(), is(OK));
                    assertThat("First service discoverer should be used again by client1",
                            firstSd.getDiscoverCount(), is(greaterThan(firstSdCountAfterFirstRequest)));

                    // Verify second service discoverer count didn't change
                    assertThat("Second service discoverer should not be affected by client1 requests",
                            secondSd.getDiscoverCount(), is(secondSdCountAfterFirstRequest));
                }
            } finally {
                firstSd.closeAsync().toFuture().get();
                secondSd.closeAsync().toFuture().get();
            }
        }
    }

    @Test
    void noHealthChecking() throws Exception {
        try (BlockingHttpClient client = HttpClients.forSingleAddress(FailureCase.SERVICE_DISCOVERER_FAILED.customSd,
                new UnresolvedAddress(null), ON_NEW_CONNECTION).buildBlocking()) {
            // The default health-checking threshold is 5, validate that client doesn't start returning
            // NoActiveHostException instead of DeliberateException after 5 attempts
            for (int i = 0; i < 10; i++) {
                assertThrows(DeliberateException.class, () -> client.request(client.get("/")));
            }
        }
    }

    @ParameterizedTest(name = "{displayName} [{index}]: failureCase={0}")
    @EnumSource(FailureCase.class)
    void failureCases(FailureCase failureCase) throws Exception {
        try (BlockingHttpClient client = HttpClients.forSingleAddress(failureCase.customSd(),
                new UnresolvedAddress(null), ON_NEW_CONNECTION).buildBlocking()) {
            assertThrows(failureCase.expectedType(), () -> client.request(client.get("/")));
        }
    }

    private enum FailureCase {
        SERVICE_DISCOVERER_FAILED(DeliberateException.class, Publisher.failed(DELIBERATE_EXCEPTION)),
        EMPTY_PUBLISHER(NoSuchElementException.class, Publisher.empty()),
        EMPTY_LIST(UnknownHostException.class, Publisher.from(emptyList())),
        EMPTY_SET(UnknownHostException.class, Publisher.from(emptySet())),
        ONE_UNAVAILABLE_EVENT_LIST(UnknownHostException.class, Publisher.from(singletonList(
                new DefaultServiceDiscovererEvent<>(createUnresolved("foo", 80), UNAVAILABLE)))),
        ONE_UNAVAILABLE_EVENT_SET(UnknownHostException.class, Publisher.from(singleton(
                new DefaultServiceDiscovererEvent<>(createUnresolved("foo", 80), UNAVAILABLE)))),
        ALL_EVENTS_ARE_UNAVAILABLE(UnknownHostException.class, Publisher.from(asList(
                new DefaultServiceDiscovererEvent<>(createUnresolved("foo", 80), UNAVAILABLE),
                new DefaultServiceDiscovererEvent<>(createUnresolved("bar", 80), UNAVAILABLE)))),
        NULL_ADDRESS(NullPointerException.class, Publisher.from(singletonList(
                new ServiceDiscovererEvent<InetSocketAddress>() {
                    @Override
                    @SuppressWarnings("DataFlowIssue")
                    public InetSocketAddress address() {
                        return null;
                    }

                    @Override
                    public Status status() {
                        return AVAILABLE;
                    }
                }
        )));

        private final Class<? extends Throwable> expectedType;
        private final ServiceDiscoverer<UnresolvedAddress, InetSocketAddress, ServiceDiscovererEvent<InetSocketAddress>>
                customSd;

        FailureCase(Class<? extends Throwable> expectedType,
                    Publisher<Collection<ServiceDiscovererEvent<InetSocketAddress>>> discoveryResult) {
            this.expectedType = expectedType;
            this.customSd = new CustomServiceDiscoverer() {

                @Override
                public Publisher<Collection<ServiceDiscovererEvent<InetSocketAddress>>> discover(
                        UnresolvedAddress unresolvedAddress) {
                    return discoveryResult;
                }
            };
        }

        Class<? extends Throwable> expectedType() {
            return expectedType;
        }

        ServiceDiscoverer<UnresolvedAddress, InetSocketAddress, ServiceDiscovererEvent<InetSocketAddress>> customSd() {
            return customSd;
        }
    }

    private static final class UnresolvedAddress {
        @Nullable
        private final InetSocketAddress address;

        UnresolvedAddress(@Nullable SocketAddress address) {
            this.address = (InetSocketAddress) address;
        }

        @Nullable
        InetSocketAddress toResolvedAddress() {
            return address;
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + "{address=" + address + '}';
        }
    }

    private static class CustomServiceDiscoverer implements ServiceDiscoverer<UnresolvedAddress,
            InetSocketAddress, ServiceDiscovererEvent<InetSocketAddress>> {

        private final ListenableAsyncCloseable closeable = emptyAsyncCloseable();

        @Override
        public Publisher<Collection<ServiceDiscovererEvent<InetSocketAddress>>> discover(
                final UnresolvedAddress unresolvedAddress) {
            InetSocketAddress resolved = requireNonNull(unresolvedAddress.toResolvedAddress());
            // Return multiple events for the same address to test random selection path.
            return Single.<Collection<ServiceDiscovererEvent<InetSocketAddress>>>succeeded(asList(
                            new DefaultServiceDiscovererEvent<>(resolved, AVAILABLE),
                            new DefaultServiceDiscovererEvent<>(resolved, AVAILABLE),
                            new DefaultServiceDiscovererEvent<>(resolved, AVAILABLE)))
                    // LoadBalancer will flag a termination of service discoverer Publisher as unexpected.
                    .concat(never());
        }

        @Override
        public final Completable closeAsync() {
            return closeable.closeAsync();
        }

        @Override
        public final Completable closeAsyncGracefully() {
            return closeable.closeAsyncGracefully();
        }

        @Override
        public final Completable onClose() {
            return closeable.onClose();
        }

        @Override
        public final Completable onClosing() {
            return closeable.onClosing();
        }

        @Override
        public final String toString() {
            return CustomServiceDiscoverer.class.getSimpleName();
        }
    }

    private static final class CountingServiceDiscoverer
            extends DelegatingListenableAsyncCloseable<ListenableAsyncCloseable>
            implements ServiceDiscoverer<HostAndPort, InetSocketAddress, ServiceDiscovererEvent<InetSocketAddress>> {

        private final AtomicInteger discoverCount = new AtomicInteger();
        private final ServiceDiscoverer<HostAndPort, InetSocketAddress, ServiceDiscovererEvent<InetSocketAddress>>
                delegate;

        CountingServiceDiscoverer() {
            super(emptyAsyncCloseable());
            this.delegate = globalARecordsDnsServiceDiscoverer();
        }

        @Override
        public Publisher<Collection<ServiceDiscovererEvent<InetSocketAddress>>> discover(
                final HostAndPort hostAndPort) {
            discoverCount.incrementAndGet();
            return delegate.discover(hostAndPort);
        }

        int getDiscoverCount() {
            return discoverCount.get();
        }
    }
}
