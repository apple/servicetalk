/*
 * Copyright © 2024 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.traffic.resilience.http;

import io.servicetalk.capacity.limiter.api.CapacityLimiter;
import io.servicetalk.capacity.limiter.api.CapacityLimiters;
import io.servicetalk.client.api.ConnectTimeoutException;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.test.StepVerifiers;
import io.servicetalk.concurrent.internal.DeliberateException;
import io.servicetalk.http.api.HttpClient;
import io.servicetalk.http.api.HttpConnection;
import io.servicetalk.http.api.HttpProtocolConfig;
import io.servicetalk.http.api.HttpRequestMethod;
import io.servicetalk.http.api.HttpServerContext;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.StreamingHttpClient;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.api.StreamingHttpServiceFilter;
import io.servicetalk.http.netty.HttpClients;
import io.servicetalk.http.netty.HttpServers;
import io.servicetalk.traffic.resilience.http.TrafficResilienceHttpServiceFilter.RejectionPolicy;
import io.servicetalk.transport.api.ServerContext;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import static io.netty.util.internal.PlatformDependent.normalizedOs;
import static io.servicetalk.capacity.limiter.api.CapacityLimiters.fixedCapacity;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.http.netty.AsyncContextHttpFilterVerifier.verifyServerFilterAsyncContextVisibility;
import static io.servicetalk.http.netty.HttpProtocolConfigs.h1Default;
import static io.servicetalk.http.netty.HttpProtocolConfigs.h2Default;
import static io.servicetalk.http.netty.HttpServers.forAddress;
import static io.servicetalk.transport.api.ServiceTalkSocketOptions.CONNECT_TIMEOUT;
import static io.servicetalk.transport.api.ServiceTalkSocketOptions.SO_BACKLOG;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class TrafficResilienceHttpServiceFilterTest {

    private static final boolean IS_LINUX = "linux".equals(normalizedOs());
    // There is an off-by-one behavior difference between macOS & Linux.
    // Linux has a greater-than check
    // (see. https://github.com/torvalds/linux/blob/5bfc75d92efd494db37f5c4c173d3639d4772966/include/net/sock.h#L941)
    private static final int TCP_BACKLOG = IS_LINUX ? 0 : 1;

    @Test
    void verifyAsyncContext() throws Exception {
        verifyServerFilterAsyncContextVisibility(
                new TrafficResilienceHttpServiceFilter.Builder(() -> fixedCapacity().capacity(1).build())
                        .build());
    }

    @Test
    void verifyPeerRejectionCallbacks() throws Exception {
        final AtomicInteger consumption = new AtomicInteger();
        // Expect two state changes
        final CountDownLatch latch = new CountDownLatch(2);
        try (ServerContext serverContext = HttpServers.forPort(0).listenAndAwait((ctx, request, responseFactory) ->
                succeeded(responseFactory.serviceUnavailable()))) {
            final TrafficResilienceHttpClientFilter trafficResilienceHttpClientFilter =
                    new TrafficResilienceHttpClientFilter.Builder(() -> CapacityLimiters.fixedCapacity()
                            .capacity(1)
                            .stateObserver((capacity, consumed) -> {
                                consumption.set(consumed);
                                latch.countDown();
                            })
                            .build()).build();
            try (HttpClient httpClient = HttpClients.forSingleAddress(serverHostAndPort(serverContext))
                    .appendClientFilter(trafficResilienceHttpClientFilter)
                    .build()) {
                assertThrows(ExecutionException.class, () ->
                        httpClient.request(httpClient.newRequest(HttpRequestMethod.GET, "/"))
                                .toFuture().get());
            } finally {
                latch.await();
                assertThat("Unexpected limiter consumption", consumption.get(), is(0));
            }
        }
    }

    @Test
    void releaseCapacityIfDelegateThrows() {
        CapacityLimiter limiter = mock(CapacityLimiter.class);
        CapacityLimiter.Ticket ticket = mock(CapacityLimiter.Ticket.class);
        when(limiter.tryAcquire(any(), any())).thenReturn(ticket);

        TrafficResilienceHttpServiceFilter filter =
                new TrafficResilienceHttpServiceFilter.Builder(() -> limiter).build();

        StreamingHttpServiceFilter service = mock(StreamingHttpServiceFilter.class);
        when(service.handle(any(), any(), any())).thenThrow(DELIBERATE_EXCEPTION);

        StreamingHttpServiceFilter serviceWithFilter = filter.create(service);
        StepVerifiers.create(serviceWithFilter.handle(mock(HttpServiceContext.class), mock(StreamingHttpRequest.class),
                        mock(StreamingHttpResponseFactory.class)))
                .expectError(DeliberateException.class)
                .verify();
        verify(limiter).tryAcquire(any(), any());
        verify(ticket).failed(DELIBERATE_EXCEPTION);
        verify(ticket, atLeastOnce()).state();
        verifyNoMoreInteractions(limiter, ticket);
    }

    enum Protocol {
        H1(h1Default()),
        H2(h2Default());

        private final HttpProtocolConfig config;
        Protocol(HttpProtocolConfig config) {
            this.config = config;
        }
    }

    @ParameterizedTest
    @EnumSource(Protocol.class)
    void testStopAcceptingConnections(final Protocol protocol) throws Exception {
        final CapacityLimiter limiter = fixedCapacity().capacity(1).build();
        final RejectionPolicy rejectionPolicy = new RejectionPolicy.Builder()
                .onLimitStopAcceptingConnections(true)
                // Custom response to validate during assertion stage
                .onLimitResponseBuilder((meta, respFactory) -> Single.succeeded(respFactory.gatewayTimeout()))
                .build();
        TrafficResilienceHttpServiceFilter filter = new TrafficResilienceHttpServiceFilter
                .Builder(() -> limiter)
                .onRejectionPolicy(rejectionPolicy)
                .build();

        final HttpServerContext serverContext = forAddress(localAddress(0))
                .protocols(protocol.config)
                .listenSocketOption(SO_BACKLOG, TCP_BACKLOG)
                .appendNonOffloadingServiceFilter(filter)
                .listenStreamingAndAwait((ctx, request, responseFactory) ->
                        succeeded(responseFactory.ok().payloadBody(Publisher.never())));

        final StreamingHttpClient client = HttpClients.forSingleAddress(serverHostAndPort(serverContext))
                .protocols(protocol.config)
                .socketOption(CONNECT_TIMEOUT, (int) SECONDS.toMillis(2))
                .buildStreaming();

        // First request -> Pending 1
        final StreamingHttpRequest meta1 = client.newRequest(HttpRequestMethod.GET, "/");
        client.reserveConnection(meta1)
                .flatMap(it -> it.request(meta1))
                .concat(Completable.defer(() -> {
                    // First request, has a "never" pub as a body, we don't attempt to consume it.
                    // Concat second request -> out of capacity -> server yielded
                    final StreamingHttpRequest meta2 = client.newRequest(HttpRequestMethod.GET, "/");
                    return client.reserveConnection(meta2).flatMap(it -> it.request(meta2)).ignoreElement();
                }))
                .toFuture()
                .get();

        // Netty will evaluate the "yielding" (i.e., auto-read) on this attempt, so this connection will go through.
        assertThat(client.reserveConnection(client.newRequest(HttpRequestMethod.GET, "/"))
                .toFuture().get().asConnection(), instanceOf(HttpConnection.class));

        // This connection shall full-fil the BACKLOG=1 setting
        assertThat(client.reserveConnection(client.newRequest(HttpRequestMethod.GET, "/"))
                .toFuture().get().asConnection(), instanceOf(HttpConnection.class));

        // Any attempt to create a connection now, should time out
        try {
            client.reserveConnection(client.newRequest(HttpRequestMethod.GET, "/")).toFuture().get();
            fail("Expected a connection timeout");
        } catch (ExecutionException e) {
            assertThat(e.getCause(), instanceOf(ConnectTimeoutException.class));
        }
    }
}
