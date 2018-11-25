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
package io.servicetalk.http.api;

import io.servicetalk.buffer.api.BufferAllocator;
import io.servicetalk.buffer.api.ReadOnlyBufferAllocators;
import io.servicetalk.client.api.DefaultGroupKey;
import io.servicetalk.client.api.GroupKey;
import io.servicetalk.concurrent.api.MockedSingleListenerRule;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.http.api.StreamingHttpClient.ReservedStreamingHttpConnection;
import io.servicetalk.transport.api.ExecutionContext;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;

import static io.servicetalk.concurrent.api.Completable.completed;
import static io.servicetalk.concurrent.api.Single.success;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class DefaultStreamingHttpClientGroupTest {
    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();

    @Rule
    public final MockedSingleListenerRule<ReservedStreamingHttpConnection> reservedHttpConnectionListener =
            new MockedSingleListenerRule<>();

    @Rule
    public final MockedSingleListenerRule<StreamingHttpResponse> httpResponseListener =
            new MockedSingleListenerRule<>();

    private final ExecutionContext executionContext = mock(ExecutionContext.class);

    @SuppressWarnings("unchecked")
    private final BiFunction<GroupKey<String>, HttpRequestMetaData, StreamingHttpClient> clientFactory =
            mock(BiFunction.class);

    private final GroupKey<String> key = mockKey(1);
    @SuppressWarnings("unchecked")
    private final StreamingHttpClient httpClient = mock(StreamingHttpClient.class);

    @SuppressWarnings("unchecked")
    private final StreamingHttpRequest request = mock(StreamingHttpRequest.class);
    @SuppressWarnings("unchecked")
    private final StreamingHttpResponse expectedResponse = mock(StreamingHttpResponse.class);
    @SuppressWarnings("unchecked")
    private final ReservedStreamingHttpConnection expectedReservedCon = mock(ReservedStreamingHttpConnection.class);

    private StreamingHttpClientGroup<String> clientGroup;

    private final HttpHeadersFactory headersFactory = DefaultHttpHeadersFactory.INSTANCE;
    private final BufferAllocator allocator = ReadOnlyBufferAllocators.DEFAULT_RO_ALLOCATOR;

    @SuppressWarnings("unchecked")
    private static GroupKey<String> mockKey(final int i) {
        final GroupKey<String> key = mock(GroupKey.class);
        when(key.address()).thenReturn("address_" + i);
        return key;
    }

    @Before
    public void setUp() {
        when(clientFactory.apply(any(), any())).thenReturn(httpClient);
        when(httpClient.request(request)).thenReturn(success(expectedResponse));
        // Mockito type-safe API can't deal with wildcard on ReservedStreamingHttpConnection
        doReturn(success(expectedReservedCon)).when(httpClient).reserveConnection(request);
        when(httpClient.closeAsync()).thenReturn(completed());
        clientGroup = new DefaultStreamingHttpClientGroup<>(allocator, headersFactory, clientFactory);
    }

    @Test(expected = NullPointerException.class)
    public void createWithNullFactory() {
        new DefaultStreamingHttpClientGroup<String>(allocator, headersFactory, null);
    }

    @Test(expected = NullPointerException.class)
    public void requestWithNullKey() {
        clientGroup.request(null, request);
    }

    @Test(expected = NullPointerException.class)
    public void requestWithNullRequest() {
        clientGroup.request(key, null);
    }

    @Test(expected = NullPointerException.class)
    public void reserveConnectionWithNullKey() {
        clientGroup.reserveConnection(null, request);
    }

    @Test(expected = NullPointerException.class)
    public void reserveConnectionWithNullRequest() {
        clientGroup.reserveConnection(key, null);
    }

    @Test
    public void successfulReservedConnection() {
        reservedHttpConnectionListener.listen(clientGroup.reserveConnection(key, request))
                .verifySuccess(expectedReservedCon);
        verify(clientFactory).apply(key, request);
    }

    @Test
    public void failedReservedConnection() {
        when(clientFactory.apply(key, request)).thenThrow(DELIBERATE_EXCEPTION);
        reservedHttpConnectionListener.listen(clientGroup.reserveConnection(key, request))
                .verifyFailure(DELIBERATE_EXCEPTION);
        verify(clientFactory).apply(key, request);
    }

    @Test
    public void successfulRequestWithNewClient() {
        httpResponseListener.listen(clientGroup.request(key, request))
                .verifySuccess(expectedResponse);
        verify(clientFactory).apply(key, request);
    }

    @Test
    public void successfulRequestWithExistingClient() {
        httpResponseListener.listen(clientGroup.request(key, request))
                .verifySuccess(expectedResponse);
        verify(clientFactory).apply(key, request);

        httpResponseListener.resetSubscriberMock()
                .listen(clientGroup.request(key, request))
                .verifySuccess(expectedResponse);
        verify(clientFactory).apply(key, request);

        clientGroup.closeAsync().subscribe();
        verify(httpClient).closeAsync();
    }

    @Test
    public void successfulRequestsWithDifferentKeys() {
        httpResponseListener.listen(clientGroup.request(key, request))
                .verifySuccess(expectedResponse);
        verify(clientFactory).apply(key, request);

        final GroupKey<String> key2 = mockKey(2);
        httpResponseListener.resetSubscriberMock()
                .listen(clientGroup.request(key2, request))
                .verifySuccess(expectedResponse);
        verify(clientFactory, times(2)).apply(any(), any());
    }

    @Test
    public void requestAfterClose() {
        clientGroup.closeAsync().subscribe();
        httpResponseListener.listen(clientGroup.request(key, request))
                .verifyFailure(IllegalStateException.class);
        verify(clientFactory, never()).apply(key, request);
        verify(httpClient, never()).closeAsync();
        clientGroup.closeAsync().subscribe();
        verify(httpClient, never()).closeAsync();
    }

    @Test
    public void clientFactoryReturnsNull() {
        when(clientFactory.apply(key, request)).thenReturn(null);
        httpResponseListener.listen(clientGroup.request(key, request))
                .verifyFailure(IllegalStateException.class);
        verify(clientFactory).apply(key, request);
    }

    @Test
    public void clientFactoryThrowsException() {
        when(clientFactory.apply(key, request)).thenThrow(DELIBERATE_EXCEPTION);
        httpResponseListener.listen(clientGroup.request(key, request))
                .verifyFailure(DELIBERATE_EXCEPTION);
        verify(clientFactory).apply(key, request);
    }

    @Test
    public void closeDuringCreatingNewClient() throws Exception {
        final CountDownLatch latchForCancel = new CountDownLatch(1);
        final CountDownLatch latchForFactory = new CountDownLatch(1);

        final AtomicBoolean invoked = new AtomicBoolean();
        final AtomicBoolean returned = new AtomicBoolean();
        final StreamingHttpClientGroup<String> clientGroup = new DefaultStreamingHttpClientGroup<>(
                allocator, headersFactory,
                (gk, md) -> {
                    invoked.set(true);
                    latchForCancel.countDown();
                    try {
                        latchForFactory.await();
                    } catch (final InterruptedException e) {
                        throw new IllegalStateException("thread should wait when client group will be closed");
                    }
                    returned.set(true);
                    return httpClient;
                });
        final ExecutorService executorService = Executors.newFixedThreadPool(1);
        try {
            final Future<?> future = executorService.submit(() ->
                    httpResponseListener.listen(clientGroup.request(key, request)));

            latchForCancel.await();
            assertTrue(invoked.get());
            assertFalse(returned.get());
            clientGroup.closeAsync().subscribe();
            verify(httpClient, never()).closeAsync();
            latchForFactory.countDown();

            future.get();
            httpResponseListener.verifyFailure(IllegalStateException.class);
            assertTrue(returned.get());
            verify(httpClient).closeAsync();
        } finally {
            executorService.shutdown();
        }
    }

    @Test
    public void closeAsyncWithNClients() {
        final int n = 10;
        for (int i = 0; i < n; i++) {
            httpResponseListener.resetSubscriberMock();
            GroupKey<String> key = mockKey(i);
            httpResponseListener.listen(clientGroup.request(key, request))
                    .verifySuccess(expectedResponse);
        }
        clientGroup.closeAsync().subscribe();
        verify(httpClient, times(n)).closeAsync();
    }

    @Test
    public void asRequester() {
        final StreamingHttpRequester requester = clientGroup.asClient(r ->
                new DefaultGroupKey<>("address", executionContext), executionContext);
        assertNotNull(requester);
        assertEquals(executionContext, requester.executionContext());
    }

    @Test
    public void asRequesterOnClosedClientGroup() {
        clientGroup.closeAsync().subscribe();
        assertNotNull(clientGroup.asClient(r ->
                new DefaultGroupKey<>("address", executionContext), executionContext));
    }

    @Test(expected = NullPointerException.class)
    public void asRequesterWithNullKeyFactory() {
        clientGroup.asClient(null, executionContext);
    }

    @Test(expected = NullPointerException.class)
    public void asRequesterWithNullExecutionContext() {
        clientGroup.asClient(r ->
                new DefaultGroupKey<>("address", executionContext), null);
    }

    @Test
    public void successfulRequestViaRequester() {
        final StreamingHttpRequester requester = clientGroup.asClient(r ->
                new DefaultGroupKey<>("address", executionContext), executionContext);
        httpResponseListener.listen(requester.request(request))
                .verifySuccess(expectedResponse);
    }

    @Test
    public void failedRequestViaRequester() {
        final StreamingHttpRequester requester = clientGroup.asClient(r -> {
            throw DELIBERATE_EXCEPTION;
        }, executionContext);
        httpResponseListener.listen(requester.request(request))
                .verifyFailure(DELIBERATE_EXCEPTION);
    }
}
