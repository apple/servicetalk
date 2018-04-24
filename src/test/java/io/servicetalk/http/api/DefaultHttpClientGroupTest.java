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

import io.servicetalk.client.api.GroupKey;
import io.servicetalk.concurrent.api.MockedSingleListenerRule;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.http.api.HttpClient.ReservedHttpConnection;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import static io.servicetalk.concurrent.api.Completable.completed;
import static io.servicetalk.concurrent.api.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.concurrent.api.Single.success;
import static io.servicetalk.http.api.HttpClientGroups.newHttpClientGroup;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class DefaultHttpClientGroupTest {
    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();

    @Rule
    public final MockedSingleListenerRule<ReservedHttpConnection<String, String>> reservedHttpConnectionListener =
            new MockedSingleListenerRule<>();

    @Rule
    public final MockedSingleListenerRule<HttpResponse<String>> httpResponseListener = new MockedSingleListenerRule<>();

    @SuppressWarnings("unchecked")
    private final Function<GroupKey<String>, HttpClient<String, String>> clientFactory = mock(Function.class);

    private final GroupKey<String> key = mockKey(1);
    @SuppressWarnings("unchecked")
    private final HttpClient<String, String> httpClient = mock(HttpClient.class);

    @SuppressWarnings("unchecked")
    private final HttpRequest<String> request = mock(HttpRequest.class);
    @SuppressWarnings("unchecked")
    private final HttpResponse<String> expectedResponse = mock(HttpResponse.class);
    @SuppressWarnings("unchecked")
    private final ReservedHttpConnection<String, String> expectedReservedCon = mock(ReservedHttpConnection.class);

    private HttpClientGroup<String, String, String> clientGroup;

    @SuppressWarnings("unchecked")
    private static GroupKey<String> mockKey(final int i) {
        final GroupKey<String> key = mock(GroupKey.class);
        when(key.getAddress()).thenReturn("address_" + i);
        return key;
    }

    @Before
    public void setUp() {
        when(clientFactory.apply(any())).thenReturn(httpClient);
        when(httpClient.request(request)).thenReturn(success(expectedResponse));
        when(httpClient.reserveConnection(request)).thenReturn(success(expectedReservedCon));
        when(httpClient.closeAsync()).thenReturn(completed());
        clientGroup = newHttpClientGroup(clientFactory);
    }

    @Test(expected = NullPointerException.class)
    public void createWithNullFactory() {
        new DefaultHttpClientGroup<String, String, String>(null);
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
        verify(clientFactory).apply(key);
    }

    @Test
    public void failedReservedConnection() {
        when(clientFactory.apply(key)).thenThrow(DELIBERATE_EXCEPTION);
        reservedHttpConnectionListener.listen(clientGroup.reserveConnection(key, request))
                .verifyFailure(DELIBERATE_EXCEPTION);
        verify(clientFactory).apply(key);
    }

    @Test
    public void successfulRequestWithNewClient() {
        httpResponseListener.listen(clientGroup.request(key, request))
                .verifySuccess(expectedResponse);
        verify(clientFactory).apply(key);
    }

    @Test
    public void successfulRequestWithExistingClient() {
        httpResponseListener.listen(clientGroup.request(key, request))
                .verifySuccess(expectedResponse);
        verify(clientFactory).apply(key);

        httpResponseListener.resetSubscriberMock()
                .listen(clientGroup.request(key, request))
                .verifySuccess(expectedResponse);
        verify(clientFactory).apply(key);

        clientGroup.closeAsync().subscribe();
        verify(httpClient).closeAsync();
    }

    @Test
    public void successfulRequestsWithDifferentKeys() {
        httpResponseListener.listen(clientGroup.request(key, request))
                .verifySuccess(expectedResponse);
        verify(clientFactory).apply(key);

        final GroupKey<String> key2 = mockKey(2);
        httpResponseListener.resetSubscriberMock()
                .listen(clientGroup.request(key2, request))
                .verifySuccess(expectedResponse);
        verify(clientFactory, times(2)).apply(any());
    }

    @Test
    public void requestAfterClose() {
        clientGroup.closeAsync().subscribe();
        httpResponseListener.listen(clientGroup.request(key, request))
                .verifyFailure(IllegalStateException.class);
        verify(clientFactory, never()).apply(key);
        verify(httpClient, never()).closeAsync();
        clientGroup.closeAsync().subscribe();
        verify(httpClient, never()).closeAsync();
    }

    @Test
    public void clientFactoryReturnsNull() {
        when(clientFactory.apply(key)).thenReturn(null);
        httpResponseListener.listen(clientGroup.request(key, request))
                .verifyFailure(IllegalStateException.class);
        verify(clientFactory).apply(key);
    }

    @Test
    public void clientFactoryThrowsException() {
        when(clientFactory.apply(key)).thenThrow(DELIBERATE_EXCEPTION);
        httpResponseListener.listen(clientGroup.request(key, request))
                .verifyFailure(DELIBERATE_EXCEPTION);
        verify(clientFactory).apply(key);
    }

    @Test
    public void closeDuringCreatingNewClient() throws Exception {
        final CountDownLatch latchForCancel = new CountDownLatch(1);
        final CountDownLatch latchForFactory = new CountDownLatch(1);

        final AtomicBoolean invoked = new AtomicBoolean();
        final AtomicBoolean returned = new AtomicBoolean();
        final HttpClientGroup<String, String, String> clientGroup = newHttpClientGroup(gk -> {
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
}
