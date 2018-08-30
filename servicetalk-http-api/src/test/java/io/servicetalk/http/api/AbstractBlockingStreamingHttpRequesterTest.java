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

import java.util.concurrent.CountDownLatch;
import java.util.function.Function;

import static io.servicetalk.concurrent.api.Executors.immediate;
import static io.servicetalk.concurrent.api.Publisher.just;
import static io.servicetalk.concurrent.api.Single.success;
import static io.servicetalk.concurrent.internal.Await.awaitIndefinitely;
import static io.servicetalk.http.api.HttpProtocolVersions.HTTP_1_1;
import static io.servicetalk.http.api.HttpRequestMethods.GET;
import static io.servicetalk.http.api.HttpResponseStatuses.OK;
import static io.servicetalk.http.api.StreamingHttpResponses.newResponse;
import static io.servicetalk.http.api.TestUtils.chunkFromString;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.util.Collections.singleton;
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
    public final PublisherRule<HttpPayloadChunk> publisherRule = new PublisherRule<>();
    @Mock
    private BlockingIterable<HttpPayloadChunk> mockIterable;
    @Mock
    private BlockingIterator<HttpPayloadChunk> mockIterator;

    protected abstract <T extends StreamingHttpRequester & TestHttpRequester>
        T newAsyncRequester(ExecutionContext executionContext,
                            Function<StreamingHttpRequest<HttpPayloadChunk>, Single<StreamingHttpResponse<HttpPayloadChunk>>> doRequest);

    protected abstract <T extends BlockingStreamingHttpRequester & TestHttpRequester>
        T newBlockingRequester(ExecutionContext executionContext,
                               Function<BlockingStreamingHttpRequest<HttpPayloadChunk>,
                                       BlockingStreamingHttpResponse<HttpPayloadChunk>> doRequest);

    protected interface TestHttpRequester {
        boolean isClosed();
    }

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        when(mockExecutionCtx.getExecutor()).thenReturn(immediate());
        when(mockCtx.getExecutionContext()).thenReturn(mockExecutionCtx);
        when(mockIterable.iterator()).thenReturn(mockIterator);
    }

    @Test
    public void asyncToSyncNoPayload() throws Exception {
        StreamingHttpRequester asyncRequester = newAsyncRequester(mockExecutionCtx,
                req -> success(newResponse(HTTP_1_1, OK)));
        BlockingStreamingHttpRequester syncRequester = asyncRequester.asBlockingStreamingRequester();
        BlockingStreamingHttpResponse<HttpPayloadChunk> syncResponse = syncRequester.request(
                BlockingStreamingHttpRequests.newRequest(HTTP_1_1, GET, "/"));
        assertEquals(HTTP_1_1, syncResponse.getVersion());
        assertEquals(OK, syncResponse.getStatus());
    }

    @Test
    public void asyncToSyncWithPayload() throws Exception {
        StreamingHttpRequester asyncRequester = newAsyncRequester(mockExecutionCtx,
                req -> success(newResponse(HTTP_1_1, OK, just(chunkFromString("hello")))));
        BlockingStreamingHttpRequester syncRequester = asyncRequester.asBlockingStreamingRequester();
        BlockingStreamingHttpResponse<HttpPayloadChunk> syncResponse = syncRequester.request(
                BlockingStreamingHttpRequests.newRequest(HTTP_1_1, GET, "/"));
        assertEquals(HTTP_1_1, syncResponse.getVersion());
        assertEquals(OK, syncResponse.getStatus());
        BlockingIterator<HttpPayloadChunk> iterator = syncResponse.getPayloadBody().iterator();
        assertTrue(iterator.hasNext());
        assertEquals(chunkFromString("hello"), iterator.next());
        assertFalse(iterator.hasNext());
    }

    @Test
    public void asyncToSyncClose() throws Exception {
        StreamingHttpRequester asyncRequester = newAsyncRequester(mockExecutionCtx,
                req -> Single.error(new IllegalStateException("shouldn't be called!")));
        BlockingStreamingHttpRequester syncRequester = asyncRequester.asBlockingStreamingRequester();
        syncRequester.close();
        assertTrue(((TestHttpRequester) asyncRequester).isClosed());
    }

    @Test
    public void asyncToSyncCancelPropagated() throws Exception {
        StreamingHttpRequester asyncRequester = newAsyncRequester(mockExecutionCtx,
                req -> success(newResponse(HTTP_1_1, OK, publisherRule.getPublisher())));
        BlockingStreamingHttpRequester syncRequester = asyncRequester.asBlockingStreamingRequester();
        BlockingStreamingHttpResponse<HttpPayloadChunk> syncResponse = syncRequester.request(
                BlockingStreamingHttpRequests.newRequest(HTTP_1_1, GET, "/"));
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
        BlockingStreamingHttpRequester syncRequester = newBlockingRequester(mockExecutionCtx,
                req -> BlockingStreamingHttpResponses.newResponse(HTTP_1_1, OK));
        StreamingHttpRequester asyncRequester = syncRequester.asStreamingRequester();
        StreamingHttpResponse<HttpPayloadChunk> asyncResponse = awaitIndefinitely(asyncRequester.request(
                StreamingHttpRequests.newRequest(HTTP_1_1, GET, "/")));
        assertNotNull(asyncResponse);
        assertEquals(HTTP_1_1, asyncResponse.getVersion());
        assertEquals(OK, asyncResponse.getStatus());
    }

    @Test
    public void syncToAsyncWithPayload() throws Exception {
        BlockingStreamingHttpRequester syncRequester = newBlockingRequester(mockExecutionCtx,
                req -> BlockingStreamingHttpResponses.newResponse(HTTP_1_1, OK, singleton(chunkFromString("hello"))));
        StreamingHttpRequester asyncRequester = syncRequester.asStreamingRequester();
        StreamingHttpResponse<HttpPayloadChunk> asyncResponse = awaitIndefinitely(asyncRequester.request(
                StreamingHttpRequests.newRequest(HTTP_1_1, GET, "/")));
        assertNotNull(asyncResponse);
        assertEquals(HTTP_1_1, asyncResponse.getVersion());
        assertEquals(OK, asyncResponse.getStatus());
        assertEquals("hello", awaitIndefinitely(asyncResponse.getPayloadBody()
                .reduce(() -> "", (acc, next) -> acc + next.getContent().toString(US_ASCII))));
    }

    @Test
    public void syncToAsyncClose() throws Exception {
        BlockingStreamingHttpRequester syncRequester = newBlockingRequester(mockExecutionCtx, req -> {
            throw new IllegalStateException("shouldn't be called!");
        });
        StreamingHttpRequester asyncRequester = syncRequester.asStreamingRequester();
        awaitIndefinitely(asyncRequester.closeAsync());
        assertTrue(((TestHttpRequester) syncRequester).isClosed());
    }

    @Test
    public void syncToAsyncCancelPropagated() throws Exception {
        BlockingStreamingHttpRequester syncRequester = newBlockingRequester(mockExecutionCtx, req ->
                BlockingStreamingHttpResponses.newResponse(HTTP_1_1, OK, mockIterable));
        StreamingHttpRequester asyncRequester = syncRequester.asStreamingRequester();
        StreamingHttpResponse<HttpPayloadChunk> asyncResponse = awaitIndefinitely(asyncRequester.request(
                StreamingHttpRequests.newRequest(HTTP_1_1, GET, "/")));
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
}
