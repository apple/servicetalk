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

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.buffer.api.BufferAllocator;
import io.servicetalk.client.api.GroupKey;
import io.servicetalk.concurrent.BlockingIterable;
import io.servicetalk.concurrent.BlockingIterator;
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

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.concurrent.api.Executors.immediate;
import static io.servicetalk.concurrent.api.Publisher.just;
import static io.servicetalk.concurrent.api.Single.error;
import static io.servicetalk.concurrent.api.Single.success;
import static io.servicetalk.concurrent.internal.Await.awaitIndefinitely;
import static io.servicetalk.http.api.HttpProtocolVersions.HTTP_1_1;
import static io.servicetalk.http.api.HttpResponseStatuses.OK;
import static io.servicetalk.http.api.StreamingHttpClient.ReservedStreamingHttpConnection;
import static io.servicetalk.http.api.StreamingHttpClient.UpgradableStreamingHttpResponse;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.util.Collections.singleton;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class BlockingStreamingHttpClientGroupTest {
    @Rule
    public final ServiceTalkTestTimeout timeout = new ServiceTalkTestTimeout();
    @Mock
    private ConnectionContext mockCtx;
    @Rule
    public final PublisherRule<Buffer> publisherRule = new PublisherRule<>();
    @Mock
    private BlockingIterable<Buffer> mockIterable;
    @Mock
    private BlockingIterator<Buffer> mockIterator;
    @Mock
    private GroupKey<String> mockKey;
    @Mock
    private ExecutionContext mockExecutionContext;
    private static final BufferAllocator allocator = DEFAULT_ALLOCATOR;
    private static final StreamingHttpRequestResponseFactory reqRespFactory =
            new DefaultStreamingHttpRequestResponseFactory(allocator, DefaultHttpHeadersFactory.INSTANCE);
    private static final BlockingStreamingHttpRequestResponseFactory blkReqRespFactory =
            new StreamingHttpRequestResponseFactoryToBlockingStreamingHttpRequestResponseFactory(reqRespFactory);

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        when(mockCtx.executionContext()).thenReturn(mockExecutionContext);
        when(mockIterable.iterator()).thenReturn(mockIterator);
        when(mockExecutionContext.executor()).thenReturn(immediate());
        when(mockKey.executionContext()).thenReturn(mockExecutionContext);
    }

    @Test
    public void asyncToSyncNoPayload() throws Exception {
        StreamingHttpClientGroup<String> asyncGroup = newAsyncGroup(
                (key, req) -> success(reqRespFactory.ok()));
        BlockingStreamingHttpClientGroup<String> syncGroup = asyncGroup.asBlockingStreamingClientGroup();
        BlockingStreamingHttpResponse syncResponse = syncGroup.request(mockKey,
                syncGroup.get("/"));
        assertEquals(HTTP_1_1, syncResponse.version());
        assertEquals(OK, syncResponse.status());
    }

    @Test
    public void asyncToSyncWithPayload() throws Exception {
        StreamingHttpClientGroup<String> asyncGroup = newAsyncGroup(
                (key, req) -> success(reqRespFactory.ok().payloadBody(just(allocator.fromAscii("hello")))));
        BlockingStreamingHttpClientGroup<String> syncGroup = asyncGroup.asBlockingStreamingClientGroup();
        BlockingStreamingHttpResponse syncResponse = syncGroup.request(mockKey,
                syncGroup.get("/"));
        assertEquals(HTTP_1_1, syncResponse.version());
        assertEquals(OK, syncResponse.status());
        BlockingIterator<Buffer> iterator = syncResponse.payloadBody().iterator();
        assertTrue(iterator.hasNext());
        assertEquals(allocator.fromAscii("hello"), iterator.next());
        assertFalse(iterator.hasNext());
    }

    @Test
    public void asyncToSyncClose() throws Exception {
        TestStreamingHttpClientGroup<String> asyncGroup = newAsyncGroup(
                (key, req) -> error(new IllegalStateException("shouldn't be called!")));
        BlockingStreamingHttpClientGroup<String> syncGroup = asyncGroup.asBlockingStreamingClientGroup();
        syncGroup.close();
        assertTrue(asyncGroup.isClosed());
    }

    @Test
    public void asyncToSyncCancelPropagated() throws Exception {
        TestStreamingHttpClientGroup<String> asyncGroup = newAsyncGroup(
                (key, req) -> success(reqRespFactory.ok().payloadBody(publisherRule.getPublisher())));
        BlockingStreamingHttpClientGroup<String> syncGroup = asyncGroup.asBlockingStreamingClientGroup();
        BlockingStreamingHttpResponse syncResponse = syncGroup.request(mockKey,
                syncGroup.get("/"));
        assertEquals(HTTP_1_1, syncResponse.version());
        assertEquals(OK, syncResponse.status());
        BlockingIterator<Buffer> iterator = syncResponse.payloadBody().iterator();
        publisherRule.sendItems(allocator.fromAscii("hello"));
        assertTrue(iterator.hasNext());
        iterator.close();
        publisherRule.verifyCancelled();
    }

    @Test
    public void syncToAsyncNoPayload() throws Exception {
        BlockingStreamingHttpClientGroup<String> syncGroup = newBlockingGroup(
                (key, req) -> blkReqRespFactory.ok());
        StreamingHttpClientGroup<String> asyncRequester = syncGroup.asStreamingClientGroup();
        StreamingHttpResponse asyncResponse = awaitIndefinitely(asyncRequester.request(mockKey,
                asyncRequester.get("/")));
        assertNotNull(asyncResponse);
        assertEquals(HTTP_1_1, asyncResponse.version());
        assertEquals(OK, asyncResponse.status());
    }

    @Test
    public void syncToAsyncWithPayload() throws Exception {
        BlockingStreamingHttpClientGroup<String> syncGroup = newBlockingGroup(
                (key, req) -> blkReqRespFactory.ok().payloadBody(singleton(allocator.fromAscii("hello"))));
        StreamingHttpClientGroup<String> asyncRequester = syncGroup.asStreamingClientGroup();
        StreamingHttpResponse asyncResponse = awaitIndefinitely(asyncRequester.request(mockKey,
                asyncRequester.get("/")));
        assertNotNull(asyncResponse);
        assertEquals(HTTP_1_1, asyncResponse.version());
        assertEquals(OK, asyncResponse.status());
        assertEquals("hello", awaitIndefinitely(asyncResponse.payloadBody()
                .reduce(() -> "", (acc, next) -> acc + next.toString(US_ASCII))));
    }

    @Test
    public void syncToAsyncClose() throws Exception {
        TestBlockingStreamingHttpClientGroup<String> syncGroup = newBlockingGroup((key, req) -> {
            throw new IllegalStateException("shouldn't be called!");
        });
        StreamingHttpClientGroup<String> asyncRequester = syncGroup.asStreamingClientGroup();
        awaitIndefinitely(asyncRequester.closeAsync());
        assertTrue(syncGroup.isClosed());
    }

    @Test
    public void syncToAsyncCancelPropagated() throws Exception {
        TestBlockingStreamingHttpClientGroup<String> syncGroup = newBlockingGroup((key, req) ->
                blkReqRespFactory.ok().payloadBody(mockIterable));
        StreamingHttpClientGroup<String> asyncRequester = syncGroup.asStreamingClientGroup();
        StreamingHttpResponse asyncResponse = awaitIndefinitely(asyncRequester.request(mockKey,
                asyncRequester.get("/")));
        assertNotNull(asyncResponse);
        CountDownLatch latch = new CountDownLatch(1);
        asyncResponse.payloadBody().subscribe(new Subscriber<Buffer>() {
            @Override
            public void onSubscribe(final Subscription s) {
                s.cancel();
                latch.countDown();
            }

            @Override
            public void onNext(final Buffer s) {
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

    private static <UnresolvedAddress> TestStreamingHttpClientGroup<UnresolvedAddress> newAsyncGroup(
            BiFunction<GroupKey<UnresolvedAddress>, StreamingHttpRequest,
                    Single<StreamingHttpResponse>> doRequest) {
        return new TestStreamingHttpClientGroup<UnresolvedAddress>(reqRespFactory) {
            @Override
            public Single<StreamingHttpResponse> request(final GroupKey<UnresolvedAddress> key,
                                                         final StreamingHttpRequest request) {
                return doRequest.apply(key, request);
            }

            @Override
            public Single<? extends ReservedStreamingHttpConnection> reserveConnection(
                    final GroupKey<UnresolvedAddress> key, final StreamingHttpRequest request) {
                return error(new UnsupportedOperationException());
            }

            @Override
            public Single<? extends UpgradableStreamingHttpResponse> upgradeConnection(
                    final GroupKey<UnresolvedAddress> key, final StreamingHttpRequest request) {
                return error(new UnsupportedOperationException());
            }
        };
    }

    private static <UnresolvedAddress> TestBlockingStreamingHttpClientGroup<UnresolvedAddress> newBlockingGroup(
            BiFunction<GroupKey<UnresolvedAddress>, BlockingStreamingHttpRequest,
                    BlockingStreamingHttpResponse> doRequest) {
        return new TestBlockingStreamingHttpClientGroup<UnresolvedAddress>(blkReqRespFactory) {
            @Override
            public BlockingStreamingHttpResponse request(final GroupKey<UnresolvedAddress> key,
                                                         final BlockingStreamingHttpRequest request) {
                return doRequest.apply(key, request);
            }

            @Override
            public BlockingStreamingHttpClient.ReservedBlockingStreamingHttpConnection reserveConnection(
                    final GroupKey<UnresolvedAddress> key, final BlockingStreamingHttpRequest request) {
                throw new UnsupportedOperationException();
            }

            @Override
            public BlockingStreamingHttpClient.UpgradableBlockingStreamingHttpResponse upgradeConnection(
                    final GroupKey<UnresolvedAddress> key,
                    final BlockingStreamingHttpRequest request) {
                throw new UnsupportedOperationException();
            }
        };
    }

    private abstract static class TestStreamingHttpClientGroup<UnresolvedAddress> extends
                                                                      StreamingHttpClientGroup<UnresolvedAddress> {
        private final AtomicBoolean closed = new AtomicBoolean();
        private final CompletableProcessor onClose = new CompletableProcessor();

        /**
         * Create a new instance.
         *
         * @param reqRespFactory The {@link StreamingHttpRequestResponseFactory} used to
         * {@link #newRequest(HttpRequestMethod, String) create new requests} and {@link #httpResponseFactory()}.
         */
        protected TestStreamingHttpClientGroup(final StreamingHttpRequestResponseFactory reqRespFactory) {
            super(reqRespFactory);
        }

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

    private abstract static class TestBlockingStreamingHttpClientGroup<UnresolvedAddress> extends
                                                              BlockingStreamingHttpClientGroup<UnresolvedAddress> {
        private final AtomicBoolean closed = new AtomicBoolean();

        /**
         * Create a new instance.
         *
         * @param reqRespFactory The {@link BlockingStreamingHttpRequestResponseFactory} used to
         * {@link #newRequest(HttpRequestMethod, String) create new requests} and {@link #httpResponseFactory()}.
         */
        protected TestBlockingStreamingHttpClientGroup(final BlockingStreamingHttpRequestResponseFactory reqRespFactory) {
            super(reqRespFactory);
        }

        @Override
        public void close() {
            closed.set(true);
        }

        final boolean isClosed() {
            return closed.get();
        }
    }
}
