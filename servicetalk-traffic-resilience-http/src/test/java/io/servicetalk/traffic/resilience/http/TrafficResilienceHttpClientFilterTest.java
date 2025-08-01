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
import io.servicetalk.capacity.limiter.api.CapacityLimiter.Ticket;
import io.servicetalk.capacity.limiter.api.CapacityLimiters;
import io.servicetalk.capacity.limiter.api.RequestDroppedException;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.test.StepVerifiers;
import io.servicetalk.concurrent.internal.DeliberateException;
import io.servicetalk.http.api.DefaultHttpHeadersFactory;
import io.servicetalk.http.api.DefaultStreamingHttpRequestResponseFactory;
import io.servicetalk.http.api.FilterableStreamingHttpClient;
import io.servicetalk.http.api.HttpClient;
import io.servicetalk.http.api.HttpRequestMethod;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.StreamingHttpClientFilter;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequestResponseFactory;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.netty.HttpClients;
import io.servicetalk.http.netty.HttpServers;
import io.servicetalk.transport.api.ServerContext;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_1;
import static io.servicetalk.http.api.HttpResponseStatus.BAD_GATEWAY;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.api.HttpResponseStatus.TOO_MANY_REQUESTS;
import static io.servicetalk.traffic.resilience.http.ClientPeerRejectionPolicy.ofPassthrough;
import static io.servicetalk.traffic.resilience.http.ClientPeerRejectionPolicy.ofRejection;
import static io.servicetalk.traffic.resilience.http.ClientPeerRejectionPolicy.ofRejectionWithRetries;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class TrafficResilienceHttpClientFilterTest {
    private static final StreamingHttpRequestResponseFactory REQ_RES_FACTORY =
            new DefaultStreamingHttpRequestResponseFactory(DEFAULT_ALLOCATOR, DefaultHttpHeadersFactory.INSTANCE,
                    HTTP_1_1);

    @ParameterizedTest(name = "{displayName} [{index}] dryRun={0}")
    @ValueSource(booleans = {false, true})
    void verifyPeerRetryableRejection(boolean dryRun) throws Exception {
        final TrafficResilienceHttpClientFilter trafficResilienceHttpClientFilter =
                new TrafficResilienceHttpClientFilter.Builder(
                        () -> CapacityLimiters.fixedCapacity(1).build())
                        .dryRun(dryRun)
                        .build();

        FilterableStreamingHttpClient client = mock(FilterableStreamingHttpClient.class);
        when(client.request(any())).thenReturn(Single.succeeded(REQ_RES_FACTORY.newResponse(BAD_GATEWAY)));

        final StreamingHttpClientFilter clientWithFilter = trafficResilienceHttpClientFilter.create(client);
        if (dryRun) {
            StreamingHttpResponse response = clientWithFilter.request(newRequest()).toFuture().get();
            response.messageBody().ignoreElements().toFuture().get();
            assertThat(response.status(), equalTo(BAD_GATEWAY));
        } else {
            assertThrows(DelayedRetryRequestDroppedException.class, () -> {
                try {
                    clientWithFilter.request(newRequest()).toFuture().get();
                } catch (ExecutionException e) {
                    throw e.getCause();
                }
            });
        }
    }

    @ParameterizedTest(name = "{displayName} [{index}] dryRun={0}")
    @ValueSource(booleans = {false, true})
    void verifyPeerRejection(boolean dryRun) throws Exception {
        final TrafficResilienceHttpClientFilter trafficResilienceHttpClientFilter =
                new TrafficResilienceHttpClientFilter.Builder(
                        () -> CapacityLimiters.fixedCapacity(1).build())
                        .rejectionPolicy(ofRejection(resp -> BAD_GATEWAY.equals(resp.status())))
                        .dryRun(dryRun)
                        .build();

        FilterableStreamingHttpClient client = mock(FilterableStreamingHttpClient.class);
        AtomicBoolean payloadDrained = new AtomicBoolean();
        when(client.request(any())).thenReturn(Single.succeeded(REQ_RES_FACTORY.newResponse(BAD_GATEWAY)
                // Use non-replayable payload body:
                .payloadBody(Publisher.fromInputStream(new ByteArrayInputStream("content".getBytes(UTF_8)))
                        .map(DEFAULT_ALLOCATOR::wrap).whenOnComplete(() -> payloadDrained.set(true)))));

        final StreamingHttpClientFilter clientWithFilter = trafficResilienceHttpClientFilter.create(client);
        if (dryRun) {
            StreamingHttpResponse response = clientWithFilter.request(newRequest()).toFuture().get();
            assertThat(response.status(), equalTo(BAD_GATEWAY));
            response.messageBody().ignoreElements().toFuture().get();
        } else {
            assertThrows(RequestDroppedException.class, () -> {
                try {
                    clientWithFilter.request(newRequest()).toFuture().get();
                } catch (ExecutionException e) {
                    throw e.getCause();
                }
            });
        }
        assertThat(payloadDrained.get(), is(true));
    }

    @ParameterizedTest(name = "{displayName} [{index}] dryRun={0}")
    @ValueSource(booleans = {false, true})
    void verifyPeerRejectionPassthrough(boolean dryRun) throws Exception {
        final TrafficResilienceHttpClientFilter trafficResilienceHttpClientFilter =
                new TrafficResilienceHttpClientFilter.Builder(
                        () -> CapacityLimiters.fixedCapacity(1).build())
                        .rejectionPolicy(ofPassthrough(resp -> BAD_GATEWAY.equals(resp.status())))
                        .dryRun(dryRun)
                        .build();

        FilterableStreamingHttpClient client = mock(FilterableStreamingHttpClient.class);
        when(client.request(any())).thenReturn(Single.succeeded(REQ_RES_FACTORY.newResponse(BAD_GATEWAY)
                // Use non-replayable payload body:
                .payloadBody(Publisher.fromInputStream(new ByteArrayInputStream("content".getBytes(UTF_8)))
                        .map(DEFAULT_ALLOCATOR::wrap))));

        final StreamingHttpClientFilter clientWithFilter = trafficResilienceHttpClientFilter.create(client);
        final HttpResponse response = clientWithFilter.request(newRequest())
                .flatMap(StreamingHttpResponse::toResponse).toFuture().get();
        assertThat(response.status(), equalTo(BAD_GATEWAY));
        assertThat(response.payloadBody().toString(UTF_8), is(equalTo("content")));
    }

    @ParameterizedTest(name = "{displayName} [{index}] dryRun={0}")
    @ValueSource(booleans = {true, false})
    void releaseCapacityIfDelegateThrows(boolean dryRun) throws Exception {
        CapacityLimiter limiter = mock(CapacityLimiter.class);
        Ticket ticket = mock(Ticket.class);
        when(limiter.tryAcquire(any(), any())).thenReturn(ticket);

        TrafficResilienceHttpClientFilter filter =
                new TrafficResilienceHttpClientFilter.Builder(() -> limiter)
                        .dryRun(dryRun)
                        .build();

        FilterableStreamingHttpClient client = mock(FilterableStreamingHttpClient.class);
        AtomicReference<StreamingHttpRequest> request = new AtomicReference<>();
        doAnswer(invocation -> {
            request.set(invocation.getArgument(0));
            throw DELIBERATE_EXCEPTION;
        }).when(client).request(any());

        StreamingHttpClientFilter clientWithFilter = filter.create(client);
        StepVerifiers.create(clientWithFilter.request(newRequest()))
                .expectError(DeliberateException.class)
                .verify();
        verify(limiter).tryAcquire(any(), any());
        verify(ticket).failed(DELIBERATE_EXCEPTION);
        verify(ticket, atLeastOnce()).state();
        verifyNoMoreInteractions(limiter, ticket);
    }

    @Test
    void testPassThroughUnderCapacity() throws Exception {
        try (ServerContext serverContext = HttpServers.forAddress(localAddress(0))
                .listenBlockingAndAwait((ctx, request, responseFactory) -> responseFactory.ok())) {

            final TrafficResilienceHttpClientFilter filter =
                    new TrafficResilienceHttpClientFilter.Builder(() -> CapacityLimiters.fixedCapacity(2).build())
                            .build();

            try (HttpClient client = HttpClients.forSingleAddress(serverHostAndPort(serverContext))
                    .appendClientFilter(filter)
                    .build()) {

                HttpResponse response = client.request(client.newRequest(HttpRequestMethod.GET, "/"))
                        .toFuture().get();

                assertThat(response.status(), is(OK));
            }
        }
    }

    @ParameterizedTest(name = "{displayName} [{index}] dryRun={0}")
    @ValueSource(booleans = {false, true})
    void testPeerRejectionHandling(boolean dryRun) throws Exception {
        try (ServerContext serverContext = HttpServers.forAddress(localAddress(0))
                .listenBlockingAndAwait((ctx, request, responseFactory) -> {
                    // Simulate peer rejection
                    return responseFactory.badGateway();
                })) {

            final TrafficResilienceHttpClientFilter filter =
                    new TrafficResilienceHttpClientFilter.Builder(() -> CapacityLimiters.fixedCapacity(5).build())
                            .rejectionPolicy(ofRejection(resp -> BAD_GATEWAY.equals(resp.status())))
                            .dryRun(dryRun)
                            .build();

            try (HttpClient client = HttpClients.forSingleAddress(serverHostAndPort(serverContext))
                    .appendClientFilter(filter)
                    .build()) {

                if (dryRun) {
                    // In dry run mode, the response should pass through
                    HttpResponse response = client.request(client.newRequest(HttpRequestMethod.GET, "/"))
                            .toFuture().get();
                    assertThat(response.status(), is(BAD_GATEWAY));
                } else {
                    // In normal mode, should throw RequestDroppedException
                    ExecutionException exception = assertThrows(ExecutionException.class, () ->
                            client.request(client.newRequest(HttpRequestMethod.GET, "/")).toFuture().get());

                    assertThat(exception.getCause(), is(instanceOf(RequestDroppedException.class)));
                }
            }
        }
    }

    @Test
    void testRejectionWhenHittingWatermark() throws Exception {
        // Use CountDownLatch to control when server responses are sent
        final CountDownLatch releaseFirstRequest = new CountDownLatch(1);
        final CountDownLatch firstRequestStarted = new CountDownLatch(1);

        final TrafficResilienceHttpClientFilter filter =
                new TrafficResilienceHttpClientFilter.Builder(() -> CapacityLimiters.fixedCapacity(1).build())
                        .build();

        try (ServerContext serverContext = HttpServers.forAddress(localAddress(0))
                .listenBlockingAndAwait((ctx, request, responseFactory) -> {
                    // Signal that first request has started
                    firstRequestStarted.countDown();
                    try {
                        // Block until we signal to release
                        releaseFirstRequest.await();
                        return responseFactory.ok();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(e);
                    }
                })) {

            try (HttpClient client = HttpClients.forSingleAddress(serverHostAndPort(serverContext))
                    .appendClientFilter(filter)
                    .build()) {

                // Start first request asynchronously (will hold capacity)
                Future<HttpResponse> firstResponse = client.request(
                        client.newRequest(HttpRequestMethod.GET, "/first")).toFuture();

                // Wait for first request to reach server and hold capacity
                firstRequestStarted.await();

                // Second request should be rejected immediately due to capacity limit
                ExecutionException exception = assertThrows(ExecutionException.class, () ->
                        client.request(client.newRequest(HttpRequestMethod.GET, "/second")).toFuture().get());

                assertThat(exception.getCause(), is(instanceOf(RequestDroppedException.class)));

                // Release the first request
                releaseFirstRequest.countDown();

                // Verify first request completes successfully
                HttpResponse response = firstResponse.get();
                assertThat(response.status(), is(OK));

                // Now try a new request to make our limiter has recovered.
                client.request(client.newRequest(HttpRequestMethod.GET, "/third")).toFuture().get();
            }
        }
    }

    @Test
    void testRetryablePeerRejectionHandling() throws Exception {
        try (ServerContext serverContext = HttpServers.forAddress(localAddress(0))
                .listenBlockingAndAwait((ctx, request, responseFactory) -> {
                    // Simulate peer rejection with TOO_MANY_REQUESTS
                    return responseFactory.tooManyRequests();
                })) {

            final TrafficResilienceHttpClientFilter filter =
                    new TrafficResilienceHttpClientFilter.Builder(() -> CapacityLimiters.fixedCapacity(5).build())
                            .rejectionPolicy(ofRejectionWithRetries(
                                    resp -> TOO_MANY_REQUESTS.equals(resp.status()),
                                    resp -> Duration.ofMillis(100)))
                            .build();

            try (HttpClient client = HttpClients.forSingleAddress(serverHostAndPort(serverContext))
                    .appendClientFilter(filter)
                    .build()) {

                ExecutionException exception = assertThrows(ExecutionException.class, () ->
                        client.request(client.newRequest(HttpRequestMethod.GET, "/")).toFuture().get());

                assertThat(exception.getCause(), is(instanceOf(DelayedRetryRequestDroppedException.class)));
            }
        }
    }

    static StreamingHttpRequest newRequest() {
        return REQ_RES_FACTORY.newRequest(HttpRequestMethod.GET, "");
    }
}
