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

import io.servicetalk.buffer.netty.BufferAllocators;
import io.servicetalk.capacity.limiter.api.CapacityLimiter;
import io.servicetalk.capacity.limiter.api.CapacityLimiters;
import io.servicetalk.capacity.limiter.api.Classification;
import io.servicetalk.client.api.ConnectTimeoutException;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.test.StepVerifiers;
import io.servicetalk.concurrent.internal.DeliberateException;
import io.servicetalk.context.api.ContextMap;
import io.servicetalk.http.api.DefaultHttpHeadersFactory;
import io.servicetalk.http.api.HttpClient;
import io.servicetalk.http.api.HttpConnection;
import io.servicetalk.http.api.HttpProtocolConfig;
import io.servicetalk.http.api.HttpRequestMethod;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.HttpResponseStatus;
import io.servicetalk.http.api.HttpServerContext;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.StreamingHttpClient;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.api.StreamingHttpResponses;
import io.servicetalk.http.api.StreamingHttpServiceFilter;
import io.servicetalk.http.netty.HttpClients;
import io.servicetalk.http.netty.HttpServers;
import io.servicetalk.transport.api.ServerContext;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static io.netty.util.internal.PlatformDependent.normalizedOs;
import static io.servicetalk.capacity.limiter.api.CapacityLimiters.fixedCapacity;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_1;
import static io.servicetalk.http.api.HttpResponseStatus.GATEWAY_TIMEOUT;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.api.HttpResponseStatus.SERVICE_UNAVAILABLE;
import static io.servicetalk.http.netty.AsyncContextHttpFilterVerifier.verifyServerFilterAsyncContextVisibility;
import static io.servicetalk.http.netty.HttpProtocolConfigs.h1Default;
import static io.servicetalk.http.netty.HttpProtocolConfigs.h2Default;
import static io.servicetalk.traffic.resilience.http.NoOpTrafficResiliencyObserver.NO_OP_TICKET_OBSERVER;
import static io.servicetalk.traffic.resilience.http.TrafficResilienceHttpClientFilterTest.newRequest;
import static io.servicetalk.transport.api.ServiceTalkSocketOptions.CONNECT_TIMEOUT;
import static io.servicetalk.transport.api.ServiceTalkSocketOptions.SO_BACKLOG;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
                new TrafficResilienceHttpServiceFilter.Builder(() -> fixedCapacity(1).build())
                        .build());
    }

    @Test
    void verifyPeerRejectionCallbacks() throws Exception {
        final AtomicInteger consumption = new AtomicInteger();
        // Expect two state changes
        final CountDownLatch latch = new CountDownLatch(2);
        try (ServerContext serverContext = HttpServers.forAddress(localAddress(0))
                .listenBlockingAndAwait((ctx, request, responseFactory) -> responseFactory.serviceUnavailable())) {
            final TrafficResilienceHttpClientFilter trafficResilienceHttpClientFilter =
                    new TrafficResilienceHttpClientFilter.Builder(() -> CapacityLimiters.fixedCapacity(1)
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

    @ParameterizedTest(name = "{displayName} [{index}] consumeRequestBodyFirst={0}")
    @ValueSource(booleans = {true, false})
    void releaseCapacityIfDelegateThrows(boolean consumeRequestBodyFirst) throws Exception {
        CapacityLimiter limiter = mock(CapacityLimiter.class);
        CapacityLimiter.Ticket ticket = mock(CapacityLimiter.Ticket.class);
        when(limiter.tryAcquire(any(), any())).thenReturn(ticket);

        TrafficResilienceHttpServiceFilter filter =
                new TrafficResilienceHttpServiceFilter.Builder(() -> limiter).build();

        StreamingHttpServiceFilter service = mock(StreamingHttpServiceFilter.class);
        AtomicReference<StreamingHttpRequest> request = new AtomicReference<>();
        doAnswer(invocation -> {
            request.set(invocation.getArgument(1));
            if (consumeRequestBodyFirst) {
                request.get().payloadBody().ignoreElements().toFuture().get();
            }
            throw DELIBERATE_EXCEPTION;
        }).when(service).handle(any(), any(), any());

        StreamingHttpServiceFilter serviceWithFilter = filter.create(service);
        StepVerifiers.create(serviceWithFilter.handle(mock(HttpServiceContext.class), newRequest(),
                        mock(StreamingHttpResponseFactory.class)))
                .expectError(DeliberateException.class)
                .verify();
        verify(limiter).tryAcquire(any(), any());

        if (!consumeRequestBodyFirst) {
            // We shouldn't have released the ticket because the request body hasn't been consumed.
            verify(ticket, never()).failed(DELIBERATE_EXCEPTION);
            // Consume the request body and we should now see the failed ticket response.
            request.get().payloadBody().ignoreElements().toFuture().get();
        }

        verify(ticket).failed(DELIBERATE_EXCEPTION);
        verify(ticket, atLeastOnce()).state();
        verifyNoMoreInteractions(limiter, ticket);
    }

    @Test
    void dryRunWillContinueToSendRequestsToDelegate() {
        CapacityLimiter limiter = mock(CapacityLimiter.class);
        when(limiter.tryAcquire(any(), any())).thenReturn(null);

        AtomicInteger rejectedCount = new AtomicInteger();
        TrafficResiliencyObserver observer = new TrafficResiliencyObserver() {
            @Override
            public void onRejectedUnmatchedPartition(StreamingHttpRequest request) {
                // noop
            }

            @Override
            public void onRejectedLimit(StreamingHttpRequest request, String capacityLimiter, ContextMap meta,
                                        Classification classification) {
                rejectedCount.incrementAndGet();
            }

            @Override
            public void onRejectedOpenCircuit(StreamingHttpRequest request, String circuitBreaker, ContextMap meta,
                                              Classification classification) {
                // noop
            }

            @Override
            public TicketObserver onAllowedThrough(StreamingHttpRequest request, CapacityLimiter.LimiterState state) {
                return NO_OP_TICKET_OBSERVER;
            }
        };

        TrafficResilienceHttpServiceFilter filter =
                new TrafficResilienceHttpServiceFilter.Builder(() -> limiter)
                        .observer(observer)
                        .dryRun(true)
                        .build();

        StreamingHttpResponse response = StreamingHttpResponses.newResponse(HttpResponseStatus.OK,
                HTTP_1_1, DefaultHttpHeadersFactory.INSTANCE.newHeaders(), BufferAllocators.DEFAULT_ALLOCATOR,
                DefaultHttpHeadersFactory.INSTANCE);
        StreamingHttpServiceFilter service = mock(StreamingHttpServiceFilter.class);
        when(service.handle(any(), any(), any())).thenReturn(Single.succeeded(response));

        StreamingHttpServiceFilter serviceWithFilter = filter.create(service);
        StepVerifiers.create(serviceWithFilter.handle(mock(HttpServiceContext.class), mock(StreamingHttpRequest.class),
                        mock(StreamingHttpResponseFactory.class)))
                .expectSuccess()
                .verify();
        verify(limiter).tryAcquire(any(), any());
        verify(limiter).name(); // called when going through the rejection pathway.
        verifyNoMoreInteractions(limiter);

        assertThat(rejectedCount.get(), equalTo(1));
    }

    @ParameterizedTest(name = "{displayName} [{index}] dryRun={0},protocol={1}")
    @CsvSource({"true, h1", "true, h2", "false, h1", "false, h2"})
    void testStopAcceptingConnections(final boolean dryRun, final String protocol) throws Exception {
        final HttpProtocolConfig protocolConfig;
        if ("h1".equalsIgnoreCase(protocol)) {
            protocolConfig = h1Default();
        } else if ("h2".equalsIgnoreCase(protocol)) {
            protocolConfig = h2Default();
        } else {
            throw new IllegalStateException("Unexpected protocol argument: " + protocol);
        }
        final CapacityLimiter limiter = fixedCapacity(1).build();
        final ServiceRejectionPolicy serviceRejectionPolicy = new ServiceRejectionPolicy.Builder()
                .onLimitStopAcceptingConnections(true)
                // Custom response to validate during assertion stage
                .onLimitResponseBuilder((meta, respFactory) -> Single.succeeded(respFactory.gatewayTimeout()))
                .build();
        TrafficResilienceHttpServiceFilter filter = new TrafficResilienceHttpServiceFilter
                .Builder(() -> limiter)
                .rejectionPolicy(serviceRejectionPolicy)
                .dryRun(dryRun)
                .build();

        try (HttpServerContext serverContext = HttpServers.forAddress(localAddress(0))
                .protocols(protocolConfig)
                .listenSocketOption(SO_BACKLOG, TCP_BACKLOG)
                .appendNonOffloadingServiceFilter(filter)
                .listenStreamingAndAwait((ctx, request, responseFactory) ->
                        succeeded(responseFactory.ok().payloadBody(Publisher.never())))) {

            try (StreamingHttpClient client = HttpClients.forSingleAddress(serverHostAndPort(serverContext))
                    .protocols(protocolConfig)
                    .socketOption(CONNECT_TIMEOUT, (int) SECONDS.toMillis(2))
                    .buildStreaming()) {

                // First request -> Pending 1
                final StreamingHttpRequest meta1 = client.newRequest(HttpRequestMethod.GET, "/");
                client.reserveConnection(meta1)
                        .flatMap(it -> it.request(meta1))
                        .concat(Completable.defer(() -> {
                            // The response has a "never" pub as a body and we don't attempt to consume it.
                            // Concat second request -> out of capacity -> server yielded
                            final StreamingHttpRequest meta2 = client.newRequest(HttpRequestMethod.GET, "/");
                            return client.reserveConnection(meta2).flatMap(it -> it.request(meta2)).ignoreElement();
                        }))
                        .toFuture()
                        .get();

                // We expect up to a couple connections to succeed due to the intrinsic race between disabling accepts
                // and new connect requests, as well as to account for kernel connect backlog (effectively 1 for all
                // OS's). That means we can have up to two connects succeed, but expect it to fail by the 3rd attempt.
                for (int i = 0; i < 3; i++) {
                    if (dryRun) {
                        client.reserveConnection(client.newRequest(HttpRequestMethod.GET, "/")).toFuture().get()
                                .releaseAsync().toFuture().get();
                    } else {
                        try {
                            assertThat(client.reserveConnection(client.newRequest(HttpRequestMethod.GET, "/"))
                                    .toFuture().get().asConnection(), instanceOf(HttpConnection.class));
                        } catch (ExecutionException e) {
                            assertThat(e.getCause(), instanceOf(ConnectTimeoutException.class));
                            // We saw the connection rejection so we succeeded.
                            return;
                        }
                    }
                }

                if (!dryRun) {
                    fail("Connection was never rejected.");
                }
            }
        }
    }

    @Test
    void testPassThroughUnderCapacity() throws Exception {
        final TrafficResilienceHttpServiceFilter filter =
                new TrafficResilienceHttpServiceFilter.Builder(() -> CapacityLimiters.fixedCapacity(2).build())
                        .build();

        try (ServerContext serverContext = HttpServers.forAddress(localAddress(0))
                .appendServiceFilter(filter)
                .listenBlockingAndAwait((ctx, request, responseFactory) -> responseFactory.ok())) {

            try (HttpClient client = HttpClients.forSingleAddress(serverHostAndPort(serverContext))
                    .build()) {

                HttpResponse response = client.request(client.newRequest(HttpRequestMethod.GET, "/"))
                        .toFuture().get();

                assertThat(response.status(), is(OK));
            }
        }
    }

    @Test
    void testRejectionWhenHittingWatermark() throws Exception {
        // Use CountDownLatch to control when service responses are sent
        final CountDownLatch releaseFirstRequest = new CountDownLatch(1);
        final CountDownLatch firstRequestStarted = new CountDownLatch(1);

        final TrafficResilienceHttpServiceFilter filter =
                new TrafficResilienceHttpServiceFilter.Builder(() -> CapacityLimiters.fixedCapacity(1).build())
                        .rejectionPolicy(new ServiceRejectionPolicy.Builder()
                                .onLimitResponseBuilder(ServiceRejectionPolicy.serviceUnavailable())
                                .build())
                        .build();

        try (ServerContext serverContext = HttpServers.forAddress(localAddress(0))
                .appendServiceFilter(filter)
                .listenBlockingAndAwait((ctx, request, responseFactory) -> {
                    firstRequestStarted.countDown();
                    try {
                        releaseFirstRequest.await();
                        return responseFactory.ok();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(e);
                    }
                })) {

            try (HttpClient client = HttpClients.forSingleAddress(serverHostAndPort(serverContext))
                    .build()) {
                Future<HttpResponse> firstResponse = client.request(
                        client.newRequest(HttpRequestMethod.GET, "/first")).toFuture();
                firstRequestStarted.await();

                // Second request should be rejected due to capacity limit
                HttpResponse secondResponse = client.request(client.newRequest(HttpRequestMethod.GET, "/second"))
                        .toFuture().get();

                assertThat(secondResponse.status(), is(SERVICE_UNAVAILABLE));
                releaseFirstRequest.countDown();

                // Verify first request completes successfully
                HttpResponse response = firstResponse.get();
                assertThat(response.status(), is(OK));

                // Make sure our capacity is now restored.
                client.request(client.newRequest(HttpRequestMethod.GET, "/third")).toFuture().get();
            }
        }
    }

    @ParameterizedTest(name = "{displayName} [{index}] dryRun={0}")
    @ValueSource(booleans = {false, true})
    void testServiceRejectionHandling(boolean dryRun) throws Exception {
        final TrafficResilienceHttpServiceFilter filter =
                new TrafficResilienceHttpServiceFilter.Builder(() -> CapacityLimiters.fixedCapacity(1).build())
                        .rejectionPolicy(new ServiceRejectionPolicy.Builder()
                                .onLimitResponseBuilder((meta, respFactory) ->
                                        Single.succeeded(respFactory.gatewayTimeout()))
                                .build())
                        .dryRun(dryRun)
                        .build();

        if (dryRun) {
            // In dry run mode, test that requests pass through even when capacity is exceeded
            try (ServerContext serverContext = HttpServers.forAddress(localAddress(0))
                    .appendServiceFilter(filter)
                    .listenBlockingAndAwait((ctx, request, responseFactory) -> responseFactory.ok())) {

                try (HttpClient client = HttpClients.forSingleAddress(serverHostAndPort(serverContext))
                        .build()) {

                    // Make multiple requests that would exceed capacity
                    HttpResponse firstResponse = client.request(client.newRequest(HttpRequestMethod.GET, "/first"))
                            .toFuture().get();
                    HttpResponse secondResponse = client.request(client.newRequest(HttpRequestMethod.GET, "/second"))
                            .toFuture().get();

                    // Both should succeed in dry run mode
                    assertThat(firstResponse.status(), is(OK));
                    assertThat(secondResponse.status(), is(OK));
                }
            }
        } else {
            // In normal mode, test actual rejection when capacity is exceeded
            final CountDownLatch holdCapacity = new CountDownLatch(1);
            final CountDownLatch firstRequestStarted = new CountDownLatch(1);

            try (ServerContext serverContext = HttpServers.forAddress(localAddress(0))
                    .appendServiceFilter(filter)
                    .listenBlockingAndAwait((ctx, request, responseFactory) -> {
                        firstRequestStarted.countDown();
                        try {
                            holdCapacity.await();
                            return responseFactory.ok();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new RuntimeException(e);
                        }
                    })) {

                try (HttpClient client = HttpClients.forSingleAddress(serverHostAndPort(serverContext))
                        .build()) {

                    // Start first request to consume capacity
                    Future<HttpResponse> firstResponse = client.request(
                            client.newRequest(HttpRequestMethod.GET, "/first")).toFuture();
                    firstRequestStarted.await();

                    // Second request should be rejected due to capacity limit
                    HttpResponse secondResponse = client.request(client.newRequest(HttpRequestMethod.GET, "/second"))
                            .toFuture().get();
                    assertThat(secondResponse.status(), is(GATEWAY_TIMEOUT));
                    // Release the first request
                    holdCapacity.countDown();
                    // Verify first request completes successfully
                    HttpResponse response = firstResponse.get();
                    assertThat(response.status(), is(OK));
                }
            }
        }
    }

    @Test
    void testServiceCapacityObserverCallbacks() throws Exception {
        final AtomicInteger consumedCount = new AtomicInteger();
        final CountDownLatch callbackLatch = new CountDownLatch(2); // Expect 2 state changes (acquire + release)

        final TrafficResilienceHttpServiceFilter filter =
                new TrafficResilienceHttpServiceFilter.Builder(() ->
                        CapacityLimiters.fixedCapacity(1)
                                .stateObserver((capacity, consumed) -> {
                                    consumedCount.set(consumed);
                                    callbackLatch.countDown();
                                })
                                .build())
                        .build();

        try (ServerContext serverContext = HttpServers.forAddress(localAddress(0))
                .appendServiceFilter(filter)
                .listenBlockingAndAwait((ctx, request, responseFactory) -> responseFactory.ok())) {

            try (HttpClient client = HttpClients.forSingleAddress(serverHostAndPort(serverContext))
                    .build()) {

                HttpResponse response = client.request(client.newRequest(HttpRequestMethod.GET, "/"))
                        .toFuture().get();
                assertThat(response.status(), is(OK));
                // Wait for capacity observer callbacks
                callbackLatch.await();
                // Final consumed count should be 0 after request completes
                assertThat(consumedCount.get(), is(0));
            }
        }
    }
}
