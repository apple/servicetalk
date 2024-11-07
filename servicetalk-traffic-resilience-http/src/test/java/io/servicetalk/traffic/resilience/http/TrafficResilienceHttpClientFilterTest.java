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
import io.servicetalk.http.api.HttpRequestMethod;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.StreamingHttpClientFilter;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequestResponseFactory;
import io.servicetalk.http.api.StreamingHttpResponse;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_1;
import static io.servicetalk.http.api.HttpResponseStatus.BAD_GATEWAY;
import static io.servicetalk.traffic.resilience.http.ClientPeerRejectionPolicy.ofPassthrough;
import static io.servicetalk.traffic.resilience.http.ClientPeerRejectionPolicy.ofRejection;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class TrafficResilienceHttpClientFilterTest {
    private static final StreamingHttpRequestResponseFactory REQ_RES_FACTORY =
            new DefaultStreamingHttpRequestResponseFactory(DEFAULT_ALLOCATOR, DefaultHttpHeadersFactory.INSTANCE,
                    HTTP_1_1);

    private static final StreamingHttpRequest REQUEST = REQ_RES_FACTORY.newRequest(HttpRequestMethod.GET, "");

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
            StreamingHttpResponse response = clientWithFilter.request(REQUEST).toFuture().get();
            response.messageBody().ignoreElements().toFuture().get();
            assertThat(response.status(), equalTo(BAD_GATEWAY));
        } else {
            assertThrows(DelayedRetryRequestDroppedException.class, () -> {
                try {
                    clientWithFilter.request(REQUEST).toFuture().get();
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
            StreamingHttpResponse response = clientWithFilter.request(REQUEST).toFuture().get();
            assertThat(response.status(), equalTo(BAD_GATEWAY));
            response.messageBody().ignoreElements().toFuture().get();
        } else {
            assertThrows(RequestDroppedException.class, () -> {
                try {
                    clientWithFilter.request(REQUEST).toFuture().get();
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
        final HttpResponse response = clientWithFilter.request(REQUEST)
                .flatMap(StreamingHttpResponse::toResponse).toFuture().get();
        assertThat(response.status(), equalTo(BAD_GATEWAY));
        assertThat(response.payloadBody().toString(UTF_8), is(equalTo("content")));
    }

    @ParameterizedTest(name = "{displayName} [{index}] dryRun={0}")
    @ValueSource(booleans = {false, true})
    void releaseCapacityIfDelegateThrows(boolean dryRun) {
        CapacityLimiter limiter = mock(CapacityLimiter.class);
        Ticket ticket = mock(Ticket.class);
        when(limiter.tryAcquire(any(), any())).thenReturn(ticket);

        TrafficResilienceHttpClientFilter filter =
                new TrafficResilienceHttpClientFilter.Builder(() -> limiter)
                        .dryRun(dryRun)
                        .build();

        FilterableStreamingHttpClient client = mock(FilterableStreamingHttpClient.class);
        when(client.request(any())).thenThrow(DELIBERATE_EXCEPTION);

        StreamingHttpClientFilter clientWithFilter = filter.create(client);
        StepVerifiers.create(clientWithFilter.request(mock(StreamingHttpRequest.class)))
                .expectError(DeliberateException.class)
                .verify();
        verify(limiter).tryAcquire(any(), any());
        verify(ticket).failed(DELIBERATE_EXCEPTION);
        verify(ticket, atLeastOnce()).state();
        verifyNoMoreInteractions(limiter, ticket);
    }
}
