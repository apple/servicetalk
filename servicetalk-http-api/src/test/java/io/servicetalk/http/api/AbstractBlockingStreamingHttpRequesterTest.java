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
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.TestPublisher;
import io.servicetalk.concurrent.api.TestSubscription;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.InputStream;
import java.util.function.BiFunction;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.concurrent.api.Executors.immediate;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.api.Single.failed;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.http.api.HttpExecutionStrategies.noOffloadsStrategy;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_1;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

public abstract class AbstractBlockingStreamingHttpRequesterTest {
    @Mock
    private HttpExecutionContext mockExecutionCtx;
    @Mock
    private HttpConnectionContext mockCtx;
    @Mock
    private BlockingIterable<Buffer> mockIterable;
    @Mock
    private BlockingIterator<Buffer> mockIterator;

    private final TestPublisher<Buffer> publisher = new TestPublisher<>();
    private final BufferAllocator allocator = DEFAULT_ALLOCATOR;
    private final StreamingHttpRequestResponseFactory reqRespFactory = new DefaultStreamingHttpRequestResponseFactory(
            allocator, DefaultHttpHeadersFactory.INSTANCE, HTTP_1_1);

    protected abstract <T extends StreamingHttpRequester & TestHttpRequester>
    T newAsyncRequester(StreamingHttpRequestResponseFactory factory, HttpExecutionContext executionContext,
                        BiFunction<HttpExecutionStrategy, StreamingHttpRequest, Single<StreamingHttpResponse>>
                                doRequest);

    protected abstract BlockingStreamingHttpRequester toBlockingStreamingRequester(StreamingHttpRequester requester);

    protected interface TestHttpRequester {
        boolean isClosed();
    }

    @BeforeEach
    void setup() {
        MockitoAnnotations.initMocks(this);
        when(mockExecutionCtx.executor()).thenReturn(immediate());
        when(mockCtx.executionContext()).thenReturn(mockExecutionCtx);
        when(mockIterable.iterator()).thenReturn(mockIterator);
    }

    @Test
    void asyncToSyncNoPayload() throws Exception {
        StreamingHttpRequester asyncRequester = newAsyncRequester(reqRespFactory, mockExecutionCtx,
                (strategy, req) -> succeeded(reqRespFactory.ok()));
        BlockingStreamingHttpRequester syncRequester = toBlockingStreamingRequester(asyncRequester);
        BlockingStreamingHttpResponse syncResponse = syncRequester.request(noOffloadsStrategy(),
                syncRequester.get("/"));
        assertEquals(HTTP_1_1, syncResponse.version());
        assertEquals(OK, syncResponse.status());
    }

    @Test
    void asyncToSyncWithPayload() throws Exception {
        StreamingHttpRequester asyncRequester = newAsyncRequester(reqRespFactory, mockExecutionCtx,
                (strategy, req) -> succeeded(reqRespFactory.ok().payloadBody(from(allocator.fromAscii("hello")))));
        BlockingStreamingHttpRequester syncRequester = toBlockingStreamingRequester(asyncRequester);
        BlockingStreamingHttpResponse syncResponse = syncRequester.request(noOffloadsStrategy(),
                syncRequester.get("/"));
        assertEquals(HTTP_1_1, syncResponse.version());
        assertEquals(OK, syncResponse.status());
        BlockingIterator<Buffer> iterator = syncResponse.payloadBody().iterator();
        assertTrue(iterator.hasNext());
        assertEquals(allocator.fromAscii("hello"), iterator.next());
        assertFalse(iterator.hasNext());
    }

    @Test
    void asyncToSyncWithPayloadInputStream() throws Exception {
        String expectedPayload = "hello";
        byte[] expectedPayloadBytes = expectedPayload.getBytes(US_ASCII);
        StreamingHttpRequester asyncRequester = newAsyncRequester(reqRespFactory, mockExecutionCtx,
                (strategy, req) -> succeeded(reqRespFactory.ok().payloadBody(
                        from(allocator.fromAscii(expectedPayload)))));
        BlockingStreamingHttpRequester syncRequester = toBlockingStreamingRequester(asyncRequester);
        BlockingStreamingHttpResponse syncResponse = syncRequester.request(noOffloadsStrategy(),
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
    void asyncToSyncClose() throws Exception {
        StreamingHttpRequester asyncRequester = newAsyncRequester(reqRespFactory, mockExecutionCtx,
                (strategy, req) -> failed(new IllegalStateException("shouldn't be called!")));
        BlockingStreamingHttpRequester syncRequester = toBlockingStreamingRequester(asyncRequester);
        syncRequester.close();
        assertTrue(((TestHttpRequester) asyncRequester).isClosed());
    }

    @Test
    void asyncToSyncCancelPropagated() throws Exception {
        StreamingHttpRequester asyncRequester = newAsyncRequester(reqRespFactory, mockExecutionCtx,
                (strategy, req) -> succeeded(reqRespFactory.ok().payloadBody(publisher)));
        TestSubscription subscription = new TestSubscription();
        BlockingStreamingHttpRequester syncRequester = toBlockingStreamingRequester(asyncRequester);
        BlockingStreamingHttpResponse syncResponse = syncRequester.request(noOffloadsStrategy(),
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
}
