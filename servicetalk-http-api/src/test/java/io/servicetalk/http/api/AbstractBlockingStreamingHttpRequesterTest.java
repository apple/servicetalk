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
import io.servicetalk.concurrent.BlockingIterable;
import io.servicetalk.concurrent.BlockingIterator;
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

import java.io.InputStream;
import java.util.concurrent.CountDownLatch;
import java.util.function.BiFunction;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.concurrent.api.Executors.immediate;
import static io.servicetalk.concurrent.api.Publisher.just;
import static io.servicetalk.concurrent.api.Single.success;
import static io.servicetalk.http.api.HttpProtocolVersions.HTTP_1_1;
import static io.servicetalk.http.api.HttpResponseStatuses.OK;
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
    @Rule
    public final PublisherRule<Buffer> publisherRule = new PublisherRule<>();
    @Mock
    private BlockingIterable<Buffer> mockIterable;
    @Mock
    private BlockingIterator<Buffer> mockIterator;
    private final BufferAllocator allocator = DEFAULT_ALLOCATOR;
    private final StreamingHttpRequestResponseFactory reqRespFactory = new DefaultStreamingHttpRequestResponseFactory(
            allocator, DefaultHttpHeadersFactory.INSTANCE);
    private final BlockingStreamingHttpRequestResponseFactory blkReqRespFactory =
            new StreamingHttpRequestResponseFactoryToBlockingStreamingHttpRequestResponseFactory(reqRespFactory);

    protected abstract <T extends StreamingHttpRequester & TestHttpRequester>
        T newAsyncRequester(StreamingHttpRequestResponseFactory factory, ExecutionContext executionContext,
                            BiFunction<HttpExecutionStrategy, StreamingHttpRequest, Single<StreamingHttpResponse>> doRequest);

    protected abstract <T extends BlockingStreamingHttpRequester & TestHttpRequester>
        T newBlockingRequester(BlockingStreamingHttpRequestResponseFactory factory, ExecutionContext executionContext,
                               BiFunction<HttpExecutionStrategy, BlockingStreamingHttpRequest,
                                       BlockingStreamingHttpResponse> doRequest);

    protected abstract BlockingStreamingHttpRequester toBlockingStreamingRequester(StreamingHttpRequester requester);

    protected abstract StreamingHttpRequester toStreamingRequester(BlockingStreamingHttpRequester requester);

    protected interface TestHttpRequester {
        boolean isClosed();
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
        StreamingHttpRequester asyncRequester = newAsyncRequester(reqRespFactory, mockExecutionCtx,
                (strategy, req) -> success(reqRespFactory.ok()));
        BlockingStreamingHttpRequester syncRequester = toBlockingStreamingRequester(asyncRequester);
        BlockingStreamingHttpResponse syncResponse = syncRequester.request(
                syncRequester.get("/"));
        assertEquals(HTTP_1_1, syncResponse.version());
        assertEquals(OK, syncResponse.status());
    }

    @Test
    public void asyncToSyncWithPayload() throws Exception {
        StreamingHttpRequester asyncRequester = newAsyncRequester(reqRespFactory, mockExecutionCtx,
                (strategy, req) -> success(reqRespFactory.ok().payloadBody(just(allocator.fromAscii("hello")))));
        BlockingStreamingHttpRequester syncRequester = toBlockingStreamingRequester(asyncRequester);
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
        StreamingHttpRequester asyncRequester = newAsyncRequester(reqRespFactory, mockExecutionCtx,
                (strategy, req) -> success(reqRespFactory.ok().payloadBody(just(allocator.fromAscii(expectedPayload)))));
        BlockingStreamingHttpRequester syncRequester = toBlockingStreamingRequester(asyncRequester);
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
        StreamingHttpRequester asyncRequester = newAsyncRequester(reqRespFactory, mockExecutionCtx,
                (strategy, req) -> Single.error(new IllegalStateException("shouldn't be called!")));
        BlockingStreamingHttpRequester syncRequester = toBlockingStreamingRequester(asyncRequester);
        syncRequester.close();
        assertTrue(((TestHttpRequester) asyncRequester).isClosed());
    }

    @Test
    public void asyncToSyncCancelPropagated() throws Exception {
        StreamingHttpRequester asyncRequester = newAsyncRequester(reqRespFactory, mockExecutionCtx,
                (strategy, req) -> success(reqRespFactory.ok().payloadBody(publisherRule.getPublisher())));
        BlockingStreamingHttpRequester syncRequester = toBlockingStreamingRequester(asyncRequester);
        BlockingStreamingHttpResponse syncResponse = syncRequester.request(
                syncRequester.get("/"));
        assertEquals(HTTP_1_1, syncResponse.version());
        assertEquals(OK, syncResponse.status());
        BlockingIterator iterator = syncResponse.payloadBody().iterator();
        publisherRule.sendItems(allocator.fromAscii("hello"));
        assertTrue(iterator.hasNext());
        iterator.close();
        publisherRule.verifyCancelled();
    }

    @Test
    public void syncToAsyncNoPayload() throws Exception {
        BlockingStreamingHttpRequester syncRequester = newBlockingRequester(blkReqRespFactory, mockExecutionCtx,
                (strategy, req) -> blkReqRespFactory.ok());
        StreamingHttpRequester asyncRequester = toStreamingRequester(syncRequester);
        StreamingHttpResponse asyncResponse = asyncRequester.request(asyncRequester.get("/")).toFuture().get();
        assertNotNull(asyncResponse);
        assertEquals(HTTP_1_1, asyncResponse.version());
        assertEquals(OK, asyncResponse.status());
    }

    @Test
    public void syncToAsyncWithPayload() throws Exception {
        BlockingStreamingHttpRequester syncRequester = newBlockingRequester(blkReqRespFactory, mockExecutionCtx,
                (strategy, req) -> blkReqRespFactory.ok().payloadBody(singleton(allocator.fromAscii("hello"))));
        StreamingHttpRequester asyncRequester = toStreamingRequester(syncRequester);
        StreamingHttpResponse asyncResponse = asyncRequester.request(asyncRequester.get("/")).toFuture().get();
        assertNotNull(asyncResponse);
        assertEquals(HTTP_1_1, asyncResponse.version());
        assertEquals(OK, asyncResponse.status());
        assertEquals("hello", asyncResponse.payloadBody()
                .reduce(() -> "", (acc, next) -> acc + next.toString(US_ASCII)).toFuture().get());
    }

    @Test
    public void syncToAsyncClose() throws Exception {
        BlockingStreamingHttpRequester syncRequester = newBlockingRequester(blkReqRespFactory,
                mockExecutionCtx, (strategy, req) -> {
            throw new IllegalStateException("shouldn't be called!");
        });
        StreamingHttpRequester asyncRequester = toStreamingRequester(syncRequester);
        asyncRequester.closeAsync().toFuture().get();
        assertTrue(((TestHttpRequester) syncRequester).isClosed());
    }

    @Test
    public void syncToAsyncCancelPropagated() throws Exception {
        BlockingStreamingHttpRequester syncRequester = newBlockingRequester(blkReqRespFactory, mockExecutionCtx,
                (strategy, req) -> blkReqRespFactory.ok().payloadBody(mockIterable));
        StreamingHttpRequester asyncRequester = toStreamingRequester(syncRequester);
        StreamingHttpResponse asyncResponse = asyncRequester.request(asyncRequester.get("/")).toFuture().get();
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
}
