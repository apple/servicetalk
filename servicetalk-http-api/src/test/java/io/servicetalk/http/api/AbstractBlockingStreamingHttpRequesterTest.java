/*
 * Copyright © 2018-2019 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.BlockingIterable;
import io.servicetalk.concurrent.BlockingIterator;
import io.servicetalk.concurrent.PublisherSource.Subscriber;
import io.servicetalk.concurrent.PublisherSource.Subscription;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.TestPublisher;
import io.servicetalk.concurrent.api.TestSubscription;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.transport.api.ConnectionContext;
import io.servicetalk.transport.api.ExecutionContext;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.InputStream;
import java.util.concurrent.CountDownLatch;
import java.util.function.BiFunction;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.concurrent.api.Executors.immediate;
import static io.servicetalk.concurrent.api.Publisher.just;
import static io.servicetalk.concurrent.api.Single.error;
import static io.servicetalk.concurrent.api.Single.success;
import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.http.api.HttpExecutionStrategies.defaultStrategy;
import static io.servicetalk.http.api.HttpExecutionStrategies.noOffloadsStrategy;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_1;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.api.RequestResponseFactories.toBlockingStreaming;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.util.Collections.singleton;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public abstract class AbstractBlockingStreamingHttpRequesterTest {
    @Rule
    public final ServiceTalkTestTimeout timeout = new ServiceTalkTestTimeout();
    @Mock
    private ExecutionContext mockExecutionCtx;
    @Mock
    private ConnectionContext mockCtx;
    @Mock
    private BlockingIterable<Buffer> mockIterable;
    @Mock
    private BlockingIterator<Buffer> mockIterator;

    private final TestPublisher<Buffer> publisher = new TestPublisher<>();
    private final BufferAllocator allocator = DEFAULT_ALLOCATOR;
    private final StreamingHttpRequestResponseFactory reqRespFactory = new DefaultStreamingHttpRequestResponseFactory(
            allocator, DefaultHttpHeadersFactory.INSTANCE);
    private final BlockingStreamingHttpRequestResponseFactory blkReqRespFactory = toBlockingStreaming(reqRespFactory);

    protected abstract TestStreamingHttpRequester newAsyncRequester(
            StreamingHttpRequestResponseFactory factory, ExecutionContext executionContext,
            BiFunction<HttpExecutionStrategy, StreamingHttpRequest, Single<StreamingHttpResponse>> doRequest);

    protected abstract TestBlockingStreamingHttpRequester newBlockingRequester(
            BlockingStreamingHttpRequestResponseFactory factory, ExecutionContext executionContext,
            BiFunction<HttpExecutionStrategy, BlockingStreamingHttpRequest, BlockingStreamingHttpResponse> doRequest);

    protected abstract static class TestStreamingHttpRequester implements StreamingHttpRequester {

        private final StreamingHttpRequestResponseFactory reqRespFactory;
        private final HttpExecutionStrategy strategy;

        protected TestStreamingHttpRequester(final StreamingHttpRequestResponseFactory reqRespFactory,
                                             final HttpExecutionStrategy strategy) {
            this.reqRespFactory = reqRespFactory;
            this.strategy = strategy;
        }

        public final Single<StreamingHttpResponse> request(final StreamingHttpRequest request) {
            return request(strategy, request);
        }

        @Override
        public final StreamingHttpRequest newRequest(final HttpRequestMethod method, final String requestTarget) {
            return reqRespFactory.newRequest(method, requestTarget);
        }

        @Override
        public final StreamingHttpResponseFactory httpResponseFactory() {
            return reqRespFactory;
        }

        public abstract boolean isClosed();

        public abstract BlockingStreamingHttpRequester asBlockingStreaming();
    }

    protected abstract static class TestBlockingStreamingHttpRequester implements BlockingStreamingHttpRequester {

        private final BlockingStreamingHttpRequestResponseFactory reqRespFactory;
        private final HttpExecutionStrategy strategy;

        protected TestBlockingStreamingHttpRequester(final BlockingStreamingHttpRequestResponseFactory reqRespFactory,
                                                     final HttpExecutionStrategy strategy) {
            this.reqRespFactory = reqRespFactory;
            this.strategy = strategy;
        }

        @Override
        public final BlockingStreamingHttpResponse request(
                final BlockingStreamingHttpRequest request) throws Exception {
            return request(strategy, request);
        }

        @Override
        public final BlockingStreamingHttpRequest newRequest(final HttpRequestMethod method,
                                                             final String requestTarget) {
            return reqRespFactory.newRequest(method, requestTarget);
        }

        @Override
        public BlockingStreamingHttpResponseFactory httpResponseFactory() {
            return reqRespFactory;
        }

        public abstract boolean isClosed();

        public abstract StreamingHttpRequester asStreaming();
    }

    // TODO(jayv) should be converted to BlockingStreamingHttp(Client|Connection)Filter when that API exists
    protected static final class BlockingFilter
            implements HttpClientFilterFactory, HttpConnectionFilterFactory {

        final BiFunction<HttpExecutionStrategy, BlockingStreamingHttpRequest,
                BlockingStreamingHttpResponse> doRequest;

        protected BlockingFilter(final BiFunction<HttpExecutionStrategy, BlockingStreamingHttpRequest,
                BlockingStreamingHttpResponse> doRequest) {
            this.doRequest = doRequest;
        }

        @Override
        public StreamingHttpClientFilter create(final StreamingHttpClientFilter client,
                                                final Publisher<Object> lbEvents) {
            return new StreamingHttpClientFilter(client) {
                @Override
                protected Single<StreamingHttpResponse> request(final StreamingHttpRequester delegate,
                                                                final HttpExecutionStrategy strategy,
                                                                final StreamingHttpRequest request) {
                    return BlockingUtils.request(asBlockingRequester(doRequest, reqRespFactory),
                            strategy, request);
                }

                @Override
                protected Single<ReservedStreamingHttpConnectionFilter> reserveConnection(
                        final StreamingHttpClientFilter delegate,
                        final HttpExecutionStrategy strategy,
                        final HttpRequestMetaData metaData) {
                    return error(new UnsupportedOperationException());
                }
            };
        }

        @Override
        public StreamingHttpConnectionFilter create(final StreamingHttpConnectionFilter connection) {
            return new StreamingHttpConnectionFilter(connection) {
                @Override
                protected Single<StreamingHttpResponse> request(final StreamingHttpRequester delegate,
                                                                final HttpExecutionStrategy strategy,
                                                                final StreamingHttpRequest request) {
                    return BlockingUtils.request(asBlockingRequester(doRequest, reqRespFactory),
                            strategy, request);
                }
            };
        }

        // This converts a blocking request() to streaming request() for use in a StreamingHttpC*Filter
        private static BlockingStreamingHttpRequester asBlockingRequester(
                final BiFunction<HttpExecutionStrategy, BlockingStreamingHttpRequest,
                        BlockingStreamingHttpResponse> doRequest,
                final StreamingHttpRequestResponseFactory reqRespFactory) {
            return new BlockingStreamingHttpRequester() {

                BlockingStreamingHttpRequestResponseFactory blkReqRespFactory = toBlockingStreaming(reqRespFactory);

                @Override
                public BlockingStreamingHttpRequest newRequest(final HttpRequestMethod method,
                                                               final String requestTarget) {
                    return blkReqRespFactory.newRequest(method, requestTarget);
                }

                @Override
                public BlockingStreamingHttpResponseFactory httpResponseFactory() {
                    return blkReqRespFactory;
                }

                @Override
                public BlockingStreamingHttpResponse request(
                        final BlockingStreamingHttpRequest request) throws Exception {
                    return request(noOffloadsStrategy(), request);
                }

                @Override
                public BlockingStreamingHttpResponse request(final HttpExecutionStrategy strategy,
                                                             final BlockingStreamingHttpRequest request) {
                    return doRequest.apply(strategy, request);
                }

                @Override
                public ExecutionContext executionContext() {
                    throw new UnsupportedOperationException();
                }

                @Override
                public void close() {
                    throw new UnsupportedOperationException();
                }
            };
        }
    }

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        when(mockExecutionCtx.executor()).thenReturn(immediate());
        when(mockCtx.executionContext()).thenReturn(mockExecutionCtx);
        when(mockIterable.iterator()).thenReturn(mockIterator);
    }

    @Test
    public void asyncToSyncNoPayload() throws Exception {
        TestStreamingHttpRequester asyncRequester = newAsyncRequester(reqRespFactory, mockExecutionCtx,
                (strategy, req) -> success(reqRespFactory.ok()));
        BlockingStreamingHttpRequester syncRequester = asyncRequester.asBlockingStreaming();
        BlockingStreamingHttpResponse syncResponse = syncRequester.request(
                syncRequester.get("/"));
        assertEquals(HTTP_1_1, syncResponse.version());
        assertEquals(OK, syncResponse.status());
    }

    @Test
    public void asyncToSyncWithPayload() throws Exception {
        TestStreamingHttpRequester asyncRequester = newAsyncRequester(reqRespFactory, mockExecutionCtx,
                (strategy, req) -> success(reqRespFactory.ok().payloadBody(just(allocator.fromAscii("hello")))));
        BlockingStreamingHttpRequester syncRequester = asyncRequester.asBlockingStreaming();
        BlockingStreamingHttpResponse syncResponse = syncRequester.request(
                syncRequester.get("/"));
        assertEquals(HTTP_1_1, syncResponse.version());
        assertEquals(OK, syncResponse.status());
        BlockingIterator<Buffer> iterator = syncResponse.payloadBody().iterator();
        assertTrue(iterator.hasNext());
        assertEquals(allocator.fromAscii("hello"), iterator.next());
        assertFalse(iterator.hasNext());
    }

    @Test
    public void asyncToSyncWithPayloadInputStream() throws Exception {
        String expectedPayload = "hello";
        byte[] expectedPayloadBytes = expectedPayload.getBytes(US_ASCII);
        TestStreamingHttpRequester asyncRequester = newAsyncRequester(reqRespFactory, mockExecutionCtx,
                (strategy, req) -> success(reqRespFactory.ok().payloadBody(
                        just(allocator.fromAscii(expectedPayload)))));
        BlockingStreamingHttpRequester syncRequester = asyncRequester.asBlockingStreaming();
        BlockingStreamingHttpResponse syncResponse = syncRequester.request(
                syncRequester.get("/"));
        assertEquals(HTTP_1_1, syncResponse.version());
        assertEquals(OK, syncResponse.status());
        InputStream is = syncResponse.payloadBodyInputStream();
        byte[] actualPayloadBytes = new byte[expectedPayloadBytes.length];
        assertEquals(expectedPayloadBytes.length, is.read(actualPayloadBytes, 0, actualPayloadBytes.length));
        assertArrayEquals(expectedPayloadBytes, actualPayloadBytes);
        is.close();
    }

    @Test
    public void asyncToSyncClose() throws Exception {
        TestStreamingHttpRequester asyncRequester = newAsyncRequester(reqRespFactory, mockExecutionCtx,
                (strategy, req) -> error(new IllegalStateException("shouldn't be called!")));
        BlockingStreamingHttpRequester syncRequester = asyncRequester.asBlockingStreaming();
        syncRequester.close();
        assertTrue(asyncRequester.isClosed());
    }

    @Test
    public void asyncToSyncCancelPropagated() throws Exception {
        TestStreamingHttpRequester asyncRequester = newAsyncRequester(reqRespFactory, mockExecutionCtx,
                (strategy, req) -> success(reqRespFactory.ok().payloadBody(publisher)));
        TestSubscription subscription = new TestSubscription();
        BlockingStreamingHttpRequester syncRequester = asyncRequester.asBlockingStreaming();
        BlockingStreamingHttpResponse syncResponse = syncRequester.request(
                syncRequester.get("/"));
        assertEquals(HTTP_1_1, syncResponse.version());
        assertEquals(OK, syncResponse.status());
        BlockingIterator iterator = syncResponse.payloadBody().iterator();
        publisher.onSubscribe(subscription);
        publisher.onNext(allocator.fromAscii("hello"));
        assertTrue(iterator.hasNext());
        iterator.close();
        assertTrue(subscription.isCancelled());
    }

    @Test
    public void syncToAsyncNoPayload() throws Exception {
        TestBlockingStreamingHttpRequester syncRequester = newBlockingRequester(blkReqRespFactory, mockExecutionCtx,
                (strategy, req) -> blkReqRespFactory.ok());
        StreamingHttpRequester asyncRequester = syncRequester.asStreaming();
        StreamingHttpResponse asyncResponse =
                asyncRequester.request(defaultStrategy(), asyncRequester.get("/")).toFuture().get();
        assertNotNull(asyncResponse);
        assertEquals(HTTP_1_1, asyncResponse.version());
        assertEquals(OK, asyncResponse.status());
    }

    @Test
    public void syncToAsyncWithPayload() throws Exception {
        TestBlockingStreamingHttpRequester syncRequester = newBlockingRequester(blkReqRespFactory, mockExecutionCtx,
                (strategy, req) -> blkReqRespFactory.ok().payloadBody(singleton(allocator.fromAscii("hello"))));
        StreamingHttpRequester asyncRequester = syncRequester.asStreaming();
        StreamingHttpResponse asyncResponse =
                asyncRequester.request(defaultStrategy(), asyncRequester.get("/")).toFuture().get();
        assertNotNull(asyncResponse);
        assertEquals(HTTP_1_1, asyncResponse.version());
        assertEquals(OK, asyncResponse.status());
        assertEquals("hello", asyncResponse.payloadBody()
                .reduce(() -> "", (acc, next) -> acc + next.toString(US_ASCII)).toFuture().get());
    }

    @Test
    public void syncToAsyncClose() throws Exception {
        TestBlockingStreamingHttpRequester syncRequester = newBlockingRequester(blkReqRespFactory,
                mockExecutionCtx, (strategy, req) -> {
            throw new IllegalStateException("shouldn't be called!");
        });
        StreamingHttpRequester asyncRequester = syncRequester.asStreaming();
        asyncRequester.closeAsync().toFuture().get();
        assertTrue(syncRequester.isClosed());
    }

    @Test
    public void syncToAsyncCancelPropagated() throws Exception {
        TestBlockingStreamingHttpRequester syncRequester = newBlockingRequester(blkReqRespFactory, mockExecutionCtx,
                (strategy, req) -> blkReqRespFactory.ok().payloadBody(mockIterable));
        StreamingHttpRequester asyncRequester = syncRequester.asStreaming();
        StreamingHttpResponse asyncResponse =
                asyncRequester.request(defaultStrategy(), asyncRequester.get("/")).toFuture().get();
        assertNotNull(asyncResponse);
        CountDownLatch latch = new CountDownLatch(1);
        toSource(asyncResponse.payloadBody()).subscribe(new Subscriber<Buffer>() {
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
}
