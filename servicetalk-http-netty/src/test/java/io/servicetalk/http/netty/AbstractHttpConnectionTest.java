/*
 * Copyright © 2018, 2021 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.http.netty;

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.buffer.api.BufferAllocator;
import io.servicetalk.client.api.ConsumableEvent;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.DefaultStreamingHttpRequestResponseFactory;
import io.servicetalk.http.api.ExecutionContextToHttpExecutionContext;
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpHeadersFactory;
import io.servicetalk.http.api.HttpResponseMetaData;
import io.servicetalk.http.api.StreamingHttpConnection;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequestResponseFactory;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.TestStreamingHttpConnection;
import io.servicetalk.transport.netty.internal.ExecutionContextExtension;
import io.servicetalk.transport.netty.internal.FlushStrategy;
import io.servicetalk.transport.netty.internal.NettyConnection;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.function.Function;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.concurrent.api.BlockingTestUtils.awaitIndefinitelyNonNull;
import static io.servicetalk.concurrent.api.Completable.never;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.http.api.DefaultHttpHeadersFactory.INSTANCE;
import static io.servicetalk.http.api.HttpEventKey.MAX_CONCURRENCY;
import static io.servicetalk.http.api.HttpExecutionStrategies.defaultStrategy;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_TYPE;
import static io.servicetalk.http.api.HttpHeaderNames.TRANSFER_ENCODING;
import static io.servicetalk.http.api.HttpHeaderValues.CHUNKED;
import static io.servicetalk.http.api.HttpHeaderValues.TEXT_PLAIN;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_1;
import static io.servicetalk.http.api.HttpRequestMethod.GET;
import static io.servicetalk.http.api.HttpResponseMetaDataFactory.newResponseMetaData;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.api.StreamingHttpRequests.newRequest;
import static io.servicetalk.http.api.StreamingHttpRequests.newTransportRequest;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * This tests the common functionality in {@link AbstractStreamingHttpConnection}.
 */
final class AbstractHttpConnectionTest {

    @RegisterExtension
    final ExecutionContextExtension ctx = ExecutionContextExtension.immediate();

    // Use Function to mock connection req/resp
    private Function<Publisher<Object>, Publisher<Object>> reqResp;

    private StreamingHttpConnection http;
    private final BufferAllocator allocator = DEFAULT_ALLOCATOR;
    private final HttpHeadersFactory headersFactory = INSTANCE;
    private final StreamingHttpRequestResponseFactory reqRespFactory =
            new DefaultStreamingHttpRequestResponseFactory(allocator, headersFactory, HTTP_1_1);

    private class MockStreamingHttpConnection
            extends AbstractStreamingHttpConnection<NettyConnection<Object, Object>> {
        MockStreamingHttpConnection(final NettyConnection<Object, Object> connection,
                                    final int maxPipelinedRequests) {
            super(connection, maxPipelinedRequests, new ExecutionContextToHttpExecutionContext(ctx, defaultStrategy()),
                    reqRespFactory, headersFactory, false);
        }

        @Override
        protected Publisher<Object> writeAndRead(final Publisher<Object> stream, final FlushStrategy flushStrategy) {
            return reqResp.apply(stream);
        }
    }

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setup() {
        reqResp = mock(Function.class);
        NettyConnection conn = mock(NettyConnection.class);
        when(conn.onClose()).thenReturn(never());
        when(conn.onClosing()).thenReturn(never());
        when(conn.transportError()).thenReturn(Single.never());
        when(conn.protocol()).thenReturn(HTTP_1_1);
        http = TestStreamingHttpConnection.from(new MockStreamingHttpConnection(conn, 101));
    }

    @Test
    void shouldEmitMaxConcurrencyInEventStream() throws Exception {
        Integer max = http.transportEventStream(MAX_CONCURRENCY)
                .afterOnNext(ConsumableEvent::eventConsumed).map(ConsumableEvent::event)
                .firstOrElse(() -> null).toFuture().get();
        assertThat(max, equalTo(101));
    }

    @SuppressWarnings("unchecked")
    @Test
    void requestShouldWriteFlatStreamToConnectionAndReadFlatStreamSplicedIntoResponseAndPayload()
            throws Exception {

        Buffer chunk1 = allocator.fromAscii("test");
        Buffer chunk2 = allocator.fromAscii("payload");
        Buffer chunk3 = allocator.fromAscii("payload");
        HttpHeaders trailers = headersFactory.newEmptyTrailers();

        HttpHeaders headers = headersFactory.newHeaders();
        headers.add(TRANSFER_ENCODING, CHUNKED);
        StreamingHttpRequest req = newTransportRequest(GET, "/foo", HTTP_1_1, headers,
                allocator, from(chunk1, chunk2, chunk3, trailers), false, headersFactory);

        HttpResponseMetaData respMeta = newResponseMetaData(HTTP_1_1, OK,
                INSTANCE.newHeaders().add(CONTENT_TYPE, TEXT_PLAIN));

        Publisher<Object> respFlat = from(respMeta, chunk1, chunk2, chunk3, trailers);
        ArgumentCaptor<Publisher<Object>> reqFlatCaptor = ArgumentCaptor.forClass(Publisher.class);
        when(reqResp.apply(reqFlatCaptor.capture())).thenReturn(respFlat);

        Single<StreamingHttpResponse> responseSingle = http.request(req);

        StreamingHttpResponse resp = awaitIndefinitelyNonNull(responseSingle);

        assertThat(reqFlatCaptor.getValue().toFuture().get(), contains(req, chunk1, chunk2, chunk3, trailers));

        assertThat(resp.status(), equalTo(OK));
        assertThat(resp.version(), equalTo(HTTP_1_1));
        assertThat(resp.headers().get(CONTENT_TYPE), equalTo(TEXT_PLAIN));

        assertThat(resp.payloadBody().toFuture().get(), contains(chunk1, chunk2, chunk3));
    }

    @SuppressWarnings("unchecked")
    @Test
    void requestShouldInsertLastPayloadChunkInRequestPayloadWhenMissing() throws Exception {

        Buffer chunk1 = allocator.fromAscii("test");
        Buffer chunk2 = allocator.fromAscii("payload");

        StreamingHttpRequest req = newRequest(GET, "/foo", HTTP_1_1, headersFactory.newHeaders(),
                allocator, headersFactory).payloadBody(from(chunk1, chunk2)); // NO chunk3 here!

        HttpResponseMetaData respMeta = newResponseMetaData(HTTP_1_1, OK,
                headersFactory.newHeaders().add(CONTENT_TYPE, TEXT_PLAIN));

        Buffer chunk3 = allocator.fromAscii("payload");
        HttpHeaders trailers = headersFactory.newEmptyTrailers();

        Publisher<Object> respFlat = from(respMeta, chunk1, chunk2, chunk3, trailers);
        ArgumentCaptor<Publisher<Object>> reqFlatCaptor = ArgumentCaptor.forClass(Publisher.class);
        when(reqResp.apply(reqFlatCaptor.capture())).thenReturn(respFlat);

        Single<StreamingHttpResponse> responseSingle = http.request(req);

        StreamingHttpResponse resp = awaitIndefinitelyNonNull(responseSingle);

        List<Object> objects = awaitIndefinitelyNonNull(reqFlatCaptor.getValue());
        assertThat(objects.subList(0, 3), contains(req, chunk1, chunk2)); // User provided chunks
        assertThat(objects.get(3), instanceOf(HttpHeaders.class)); // Ensure new Last chunk inserted

        assertThat(resp.status(), equalTo(OK));
        assertThat(resp.version(), equalTo(HTTP_1_1));
        assertThat(resp.headers().get(CONTENT_TYPE), equalTo(TEXT_PLAIN));

        assertThat(resp.payloadBody().toFuture().get(), contains(chunk1, chunk2, chunk3));
    }
}
