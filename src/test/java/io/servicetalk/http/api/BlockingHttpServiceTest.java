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

import io.servicetalk.concurrent.api.BlockingIterable;
import io.servicetalk.concurrent.api.BlockingIterator;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.PublisherRule;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.transport.api.ConnectionContext;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.servicetalk.concurrent.api.Completable.completed;
import static io.servicetalk.concurrent.api.Executors.immediate;
import static io.servicetalk.concurrent.api.Publisher.just;
import static io.servicetalk.concurrent.api.Single.success;
import static io.servicetalk.concurrent.internal.Await.awaitIndefinitely;
import static io.servicetalk.http.api.BlockingHttpService.fromBlocking;
import static io.servicetalk.http.api.HttpProtocolVersions.HTTP_1_1;
import static io.servicetalk.http.api.HttpRequestMethods.GET;
import static io.servicetalk.http.api.HttpResponseStatuses.OK;
import static io.servicetalk.http.api.HttpResponses.newResponse;
import static io.servicetalk.http.api.HttpService.fromAsync;
import static java.util.Collections.singleton;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class BlockingHttpServiceTest {
    @Rule
    public final ServiceTalkTestTimeout timeout = new ServiceTalkTestTimeout();
    @Mock
    private ConnectionContext mockCtx;
    @Rule
    public final PublisherRule<String> publisherRule = new PublisherRule<>();
    @Mock
    private BlockingIterable<String> mockIterable;
    @Mock
    private BlockingIterator<String> mockIterator;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        when(mockCtx.getExecutor()).thenReturn(immediate());
        when(mockIterable.iterator()).thenReturn(mockIterator);
    }

    @Test
    public void asyncToSyncNoPayload() throws Exception {
        HttpService<String, String> asyncService = fromAsync((ctx, request) ->
                success(newResponse(HTTP_1_1, OK)));
        BlockingHttpService<String, String> syncService = asyncService.asBlockingService();
        BlockingHttpResponse<String> syncResponse = syncService.handle(mockCtx,
                BlockingHttpRequests.newRequest(HTTP_1_1, GET, "/"));
        assertEquals(HTTP_1_1, syncResponse.getVersion());
        assertEquals(OK, syncResponse.getStatus());
    }

    @Test
    public void asyncToSyncWithPayload() throws Exception {
        HttpService<String, String> asyncService = fromAsync((ctx, request) -> success(newResponse(HTTP_1_1, OK,
                just("hello"))));
        BlockingHttpService<String, String> syncService = asyncService.asBlockingService();
        BlockingHttpResponse<String> syncResponse = syncService.handle(mockCtx,
                BlockingHttpRequests.newRequest(HTTP_1_1, GET, "/"));
        assertEquals(HTTP_1_1, syncResponse.getVersion());
        assertEquals(OK, syncResponse.getStatus());
        BlockingIterator<String> iterator = syncResponse.getPayloadBody().iterator();
        assertTrue(iterator.hasNext());
        assertEquals("hello", iterator.next());
        assertFalse(iterator.hasNext());
    }

    @Test
    public void asyncToSyncClose() throws Exception {
        final AtomicBoolean closedCalled = new AtomicBoolean();
        HttpService<String, String> asyncService = new HttpService<String, String>() {
            @Override
            public Single<HttpResponse<String>> handle(final ConnectionContext ctx, final HttpRequest<String> request) {
                return Single.error(new IllegalStateException("shouldn't be called!"));
            }

            @Override
            public Completable closeAsync() {
                closedCalled.set(true);
                return completed();
            }
        };
        BlockingHttpService<String, String> syncService = asyncService.asBlockingService();
        syncService.close();
        assertTrue(closedCalled.get());
    }

    @Test
    public void asyncToSyncCancelPropagated() throws Exception {
        HttpService<String, String> asyncService = fromAsync((ctx, request) -> success(newResponse(HTTP_1_1, OK,
                publisherRule.getPublisher())));
        BlockingHttpService<String, String> syncService = asyncService.asBlockingService();
        BlockingHttpResponse<String> syncResponse = syncService.handle(mockCtx,
                BlockingHttpRequests.newRequest(HTTP_1_1, GET, "/"));
        assertEquals(HTTP_1_1, syncResponse.getVersion());
        assertEquals(OK, syncResponse.getStatus());
        BlockingIterator<String> iterator = syncResponse.getPayloadBody().iterator();
        publisherRule.sendItems("hello");
        assertTrue(iterator.hasNext());
        iterator.close();
        publisherRule.verifyCancelled();
    }

    @Test
    public void syncToAsyncNoPayload() throws Exception {
        BlockingHttpService<String, String> syncService = fromBlocking((ctx, request) ->
                BlockingHttpResponses.newResponse(HTTP_1_1, OK));
        HttpService<String, String> asyncService = syncService.asAsynchronousService();
        HttpResponse<String> asyncResponse = awaitIndefinitely(asyncService.handle(mockCtx,
                HttpRequests.newRequest(HTTP_1_1, GET, "/")));
        assertNotNull(asyncResponse);
        assertEquals(HTTP_1_1, asyncResponse.getVersion());
        assertEquals(OK, asyncResponse.getStatus());
    }

    @Test
    public void syncToAsyncWithPayload() throws Exception {
        BlockingHttpService<String, String> syncService = fromBlocking((ctx, request) ->
                BlockingHttpResponses.newResponse(HTTP_1_1, OK, singleton("hello")));
        HttpService<String, String> asyncService = syncService.asAsynchronousService();
        HttpResponse<String> asyncResponse = awaitIndefinitely(asyncService.handle(mockCtx,
                HttpRequests.newRequest(HTTP_1_1, GET, "/")));
        assertNotNull(asyncResponse);
        assertEquals(HTTP_1_1, asyncResponse.getVersion());
        assertEquals(OK, asyncResponse.getStatus());
        assertEquals("hello", awaitIndefinitely(asyncResponse.getPayloadBody()
                .reduce(() -> "", (acc, next) -> acc + next)));
    }

    @Test
    public void syncToAsyncClose() throws Exception {
        final AtomicBoolean closedCalled = new AtomicBoolean();
        BlockingHttpService<String, String> syncService = new BlockingHttpService<String, String>() {
            @Override
            public BlockingHttpResponse<String> handle(final ConnectionContext ctx,
                                                       final BlockingHttpRequest<String> request) {
                throw new IllegalStateException("shouldn't be called!");
            }

            @Override
            public void close() {
                closedCalled.set(true);
            }
        };
        HttpService<String, String> asyncService = syncService.asAsynchronousService();
        awaitIndefinitely(asyncService.closeAsync());
        assertTrue(closedCalled.get());
    }

    @Test
    public void syncToAsyncCancelPropagated() throws Exception {
        BlockingHttpService<String, String> syncService = fromBlocking((ctx, request) ->
            BlockingHttpResponses.newResponse(HTTP_1_1, OK, mockIterable));
        HttpService<String, String> asyncService = syncService.asAsynchronousService();
        HttpResponse<String> asyncResponse = awaitIndefinitely(asyncService.handle(mockCtx,
                HttpRequests.newRequest(HTTP_1_1, GET, "/")));
        assertNotNull(asyncResponse);
        CountDownLatch latch = new CountDownLatch(1);
        asyncResponse.getPayloadBody().subscribe(new Subscriber<String>() {
            @Override
            public void onSubscribe(final Subscription s) {
                s.cancel();
                latch.countDown();
            }

            @Override
            public void onNext(final String s) {
            }

            @Override
            public void onError(final Throwable t) {
            }

            @Override
            public void onComplete() {
            }
        });
        latch.await();
        verify(mockIterator).close();
    }
}
