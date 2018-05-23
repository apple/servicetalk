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
import io.servicetalk.concurrent.api.BlockingIterable;
import io.servicetalk.concurrent.api.BlockingIterator;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.CompletableProcessor;
import io.servicetalk.concurrent.api.PublisherRule;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.transport.api.ConnectionContext;
import io.servicetalk.transport.api.ExecutionContext;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;

import static io.servicetalk.concurrent.api.Executors.immediate;
import static io.servicetalk.concurrent.api.Publisher.just;
import static io.servicetalk.concurrent.api.Single.error;
import static io.servicetalk.concurrent.api.Single.success;
import static io.servicetalk.concurrent.internal.Await.awaitIndefinitely;
import static io.servicetalk.http.api.HttpProtocolVersions.HTTP_1_1;
import static io.servicetalk.http.api.HttpRequestMethods.GET;
import static io.servicetalk.http.api.HttpResponseStatuses.OK;
import static io.servicetalk.http.api.HttpResponses.newResponse;
import static io.servicetalk.http.api.TestUtils.chunkFromString;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.util.Collections.singleton;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class BlockingHttpClientGroupTest {
    @Rule
    public final ServiceTalkTestTimeout timeout = new ServiceTalkTestTimeout();
    @Mock
    private ConnectionContext mockCtx;
    @Rule
    public final PublisherRule<HttpPayloadChunk> publisherRule = new PublisherRule<>();
    @Mock
    private BlockingIterable<HttpPayloadChunk> mockIterable;
    @Mock
    private BlockingIterator<HttpPayloadChunk> mockIterator;
    @Mock
    private GroupKey<String> mockKey;
    @Mock
    private ExecutionContext mockExecutionContext;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        when(mockCtx.getExecutor()).thenReturn(immediate());
        when(mockIterable.iterator()).thenReturn(mockIterator);
        when(mockExecutionContext.getExecutor()).thenReturn(immediate());
        when(mockKey.getExecutionContext()).thenReturn(mockExecutionContext);
    }

    @Test
    public void asyncToSyncNoPayload() throws Exception {
        HttpClientGroup<String> asyncGroup = newAsyncGroup(
                (key, req) -> success(newResponse(HTTP_1_1, OK)));
        BlockingHttpClientGroup<String> syncGroup = asyncGroup.asBlockingClientGroup();
        BlockingHttpResponse<HttpPayloadChunk> syncResponse = syncGroup.request(mockKey,
                BlockingHttpRequests.newRequest(HTTP_1_1, GET, "/"));
        assertEquals(HTTP_1_1, syncResponse.getVersion());
        assertEquals(OK, syncResponse.getStatus());
    }

    @Test
    public void asyncToSyncWithPayload() throws Exception {
        HttpClientGroup<String> asyncGroup = newAsyncGroup(
                (key, req) -> success(newResponse(HTTP_1_1, OK, just(chunkFromString("hello")))));
        BlockingHttpClientGroup<String> syncGroup = asyncGroup.asBlockingClientGroup();
        BlockingHttpResponse<HttpPayloadChunk> syncResponse = syncGroup.request(mockKey,
                BlockingHttpRequests.newRequest(HTTP_1_1, GET, "/"));
        assertEquals(HTTP_1_1, syncResponse.getVersion());
        assertEquals(OK, syncResponse.getStatus());
        BlockingIterator<HttpPayloadChunk> iterator = syncResponse.getPayloadBody().iterator();
        assertTrue(iterator.hasNext());
        assertEquals(chunkFromString("hello"), iterator.next());
        assertFalse(iterator.hasNext());
    }

    @Test
    public void asyncToSyncClose() throws Exception {
        TestHttpClientGroup<String> asyncGroup = newAsyncGroup(
                (key, req) -> error(new IllegalStateException("shouldn't be called!")));
        BlockingHttpClientGroup<String> syncGroup = asyncGroup.asBlockingClientGroup();
        syncGroup.close();
        assertTrue(asyncGroup.isClosed());
    }

    @Test
    public void asyncToSyncCancelPropagated() throws Exception {
        TestHttpClientGroup<String> asyncGroup = newAsyncGroup(
                (key, req) -> success(newResponse(HTTP_1_1, OK, publisherRule.getPublisher())));
        BlockingHttpClientGroup<String> syncGroup = asyncGroup.asBlockingClientGroup();
        BlockingHttpResponse<HttpPayloadChunk> syncResponse = syncGroup.request(mockKey,
                BlockingHttpRequests.newRequest(HTTP_1_1, GET, "/"));
        assertEquals(HTTP_1_1, syncResponse.getVersion());
        assertEquals(OK, syncResponse.getStatus());
        BlockingIterator<HttpPayloadChunk> iterator = syncResponse.getPayloadBody().iterator();
        publisherRule.sendItems(chunkFromString("hello"));
        assertTrue(iterator.hasNext());
        iterator.close();
        publisherRule.verifyCancelled();
    }

    @Test
    public void syncToAsyncNoPayload() throws Exception {
        BlockingHttpClientGroup<String> syncGroup = newBlockingGroup(
                (key, req) -> BlockingHttpResponses.newResponse(HTTP_1_1, OK));
        HttpClientGroup<String> asyncRequester = syncGroup.asClientGroup();
        HttpResponse<HttpPayloadChunk> asyncResponse = awaitIndefinitely(asyncRequester.request(mockKey,
                HttpRequests.newRequest(HTTP_1_1, GET, "/")));
        assertNotNull(asyncResponse);
        assertEquals(HTTP_1_1, asyncResponse.getVersion());
        assertEquals(OK, asyncResponse.getStatus());
    }

    @Test
    public void syncToAsyncWithPayload() throws Exception {
        BlockingHttpClientGroup<String> syncGroup = newBlockingGroup(
                (key, req) -> BlockingHttpResponses.newResponse(HTTP_1_1, OK, singleton(chunkFromString("hello"))));
        HttpClientGroup<String> asyncRequester = syncGroup.asClientGroup();
        HttpResponse<HttpPayloadChunk> asyncResponse = awaitIndefinitely(asyncRequester.request(mockKey,
                HttpRequests.newRequest(HTTP_1_1, GET, "/")));
        assertNotNull(asyncResponse);
        assertEquals(HTTP_1_1, asyncResponse.getVersion());
        assertEquals(OK, asyncResponse.getStatus());
        assertEquals("hello", awaitIndefinitely(asyncResponse.getPayloadBody()
                .reduce(() -> "", (acc, next) -> acc + next.getContent().toString(US_ASCII))));
    }

    @Test
    public void syncToAsyncClose() throws Exception {
        TestBlockingHttpClientGroup<String> syncGroup = newBlockingGroup((key, req) -> {
            throw new IllegalStateException("shouldn't be called!");
        });
        HttpClientGroup<String> asyncRequester = syncGroup.asClientGroup();
        awaitIndefinitely(asyncRequester.closeAsync());
        assertTrue(syncGroup.isClosed());
    }

    @Test
    public void syncToAsyncCancelPropagated() throws Exception {
        TestBlockingHttpClientGroup<String> syncGroup = newBlockingGroup((key, req) ->
                BlockingHttpResponses.newResponse(HTTP_1_1, OK, mockIterable));
        HttpClientGroup<String> asyncRequester = syncGroup.asClientGroup();
        HttpResponse<HttpPayloadChunk> asyncResponse = awaitIndefinitely(asyncRequester.request(mockKey,
                HttpRequests.newRequest(HTTP_1_1, GET, "/")));
        assertNotNull(asyncResponse);
        CountDownLatch latch = new CountDownLatch(1);
        asyncResponse.getPayloadBody().subscribe(new Subscriber<HttpPayloadChunk>() {
            @Override
            public void onSubscribe(final Subscription s) {
                s.cancel();
                latch.countDown();
            }

            @Override
            public void onNext(final HttpPayloadChunk s) {
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

    private static <UnresolvedAddress> TestHttpClientGroup<UnresolvedAddress> newAsyncGroup(
            BiFunction<GroupKey<UnresolvedAddress>, HttpRequest<HttpPayloadChunk>,
                    Single<HttpResponse<HttpPayloadChunk>>> doRequest) {
        return new TestHttpClientGroup<UnresolvedAddress>() {
            @Override
            public Single<HttpResponse<HttpPayloadChunk>> request(final GroupKey<UnresolvedAddress> key,
                                                   final HttpRequest<HttpPayloadChunk> request) {
                return doRequest.apply(key, request);
            }

            @Override
            public Single<? extends HttpClient.ReservedHttpConnection> reserveConnection(
                    final GroupKey<UnresolvedAddress> key, final HttpRequest<HttpPayloadChunk> request) {
                return error(new UnsupportedOperationException());
            }
        };
    }

    private static <UnresolvedAddress> TestBlockingHttpClientGroup<UnresolvedAddress> newBlockingGroup(
            BiFunction<GroupKey<UnresolvedAddress>, BlockingHttpRequest<HttpPayloadChunk>,
                    BlockingHttpResponse<HttpPayloadChunk>> doRequest) {
        return new TestBlockingHttpClientGroup<UnresolvedAddress>() {
            @Override
            public BlockingHttpResponse<HttpPayloadChunk> request(final GroupKey<UnresolvedAddress> key,
                                                   final BlockingHttpRequest<HttpPayloadChunk> request) {
                return doRequest.apply(key, request);
            }

            @Override
            public BlockingHttpClient.BlockingReservedHttpConnection reserveConnection(
                    final GroupKey<UnresolvedAddress> key, final BlockingHttpRequest<HttpPayloadChunk> request) {
                throw new UnsupportedOperationException();
            }
        };
    }

    private abstract static class TestHttpClientGroup<UnresolvedAddress> extends
                                                                      HttpClientGroup<UnresolvedAddress> {
        private final AtomicBoolean closed = new AtomicBoolean();
        private final CompletableProcessor onClose = new CompletableProcessor();

        @Override
        public final Completable onClose() {
            return onClose;
        }

        @Override
        public final Completable closeAsync() {
            return new Completable() {
                @Override
                protected void handleSubscribe(final Subscriber subscriber) {
                    if (closed.compareAndSet(false, true)) {
                        onClose.onComplete();
                    }
                    onClose.subscribe(subscriber);
                }
            };
        }

        final boolean isClosed() {
            return closed.get();
        }
    }

    private abstract static class TestBlockingHttpClientGroup<UnresolvedAddress> extends
            BlockingHttpClientGroup<UnresolvedAddress> {
        private final AtomicBoolean closed = new AtomicBoolean();

        @Override
        public void close() {
            closed.set(true);
        }

        final boolean isClosed() {
            return closed.get();
        }
    }
}
