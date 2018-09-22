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
package io.servicetalk.http.netty;

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.buffer.api.BufferAllocator;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.http.api.DefaultStreamingHttpRequestResponseFactory;
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpHeadersFactory;
import io.servicetalk.http.api.HttpResponseMetaData;
import io.servicetalk.http.api.StreamingHttpConnection;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequestResponseFactory;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.tcp.netty.internal.TcpClientConfig;
import io.servicetalk.transport.netty.internal.Connection;
import io.servicetalk.transport.netty.internal.ExecutionContextRule;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.concurrent.api.Completable.never;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.internal.Await.awaitIndefinitely;
import static io.servicetalk.concurrent.internal.Await.awaitIndefinitelyNonNull;
import static io.servicetalk.http.api.DefaultHttpHeadersFactory.INSTANCE;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_TYPE;
import static io.servicetalk.http.api.HttpHeaderValues.TEXT_PLAIN;
import static io.servicetalk.http.api.HttpProtocolVersions.HTTP_1_1;
import static io.servicetalk.http.api.HttpRequestMethods.GET;
import static io.servicetalk.http.api.HttpResponseMetaDataFactory.newResponseMetaData;
import static io.servicetalk.http.api.HttpResponseStatuses.OK;
import static io.servicetalk.http.api.StreamingHttpConnection.SettingKey.MAX_CONCURRENCY;
import static io.servicetalk.http.api.StreamingHttpRequests.newRequest;
import static io.servicetalk.http.api.StreamingHttpRequests.newRequestWithTrailers;
import static io.servicetalk.transport.netty.internal.ExecutionContextRule.immediate;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * This tests the common functionality in {@link AbstractStreamingHttpConnection}.
 */
public final class AbstractHttpConnectionTest {

    @Rule
    public final ServiceTalkTestTimeout timeout = new ServiceTalkTestTimeout();

    @Rule
    public final ExecutionContextRule ctx = immediate();

    @SuppressWarnings("unchecked")
    // Use Function to mock connection req/resp
    private Function<Publisher<Object>, Publisher<Object>> reqResp;

    private StreamingHttpConnection http;
    private HttpClientConfig config = new HttpClientConfig(new TcpClientConfig(true));
    private final BufferAllocator allocator = DEFAULT_ALLOCATOR;
    private final HttpHeadersFactory headersFactory = INSTANCE;
    private final StreamingHttpRequestResponseFactory reqRespFactory =
            new DefaultStreamingHttpRequestResponseFactory(allocator, headersFactory);

    private class MockStreamingHttpConnection extends AbstractStreamingHttpConnection<Connection<Object, Object>> {
        protected MockStreamingHttpConnection(final Connection<Object, Object> connection,
                                              final ReadOnlyHttpClientConfig config) {
            super(connection, never(), config, ctx, reqRespFactory);
        }

        @Override
        protected Publisher<Object> writeAndRead(final Publisher<Object> stream) {
            return reqResp.apply(stream);
        }
    }

    @SuppressWarnings("unchecked")
    @Before
    public void setup() {
        reqResp = mock(Function.class);
        config.setMaxPipelinedRequests(101);
        http = new MockStreamingHttpConnection(mock(Connection.class), config.asReadOnly());
    }

    @Test
    public void shouldEmitMaxConcurrencyInSettingStream() throws ExecutionException, InterruptedException {
        Integer max = awaitIndefinitely(http.getSettingStream(MAX_CONCURRENCY).first());
        assertThat(max, equalTo(101));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void requestShouldWriteFlatStreamToConnectionAndReadFlatStreamSplicedIntoResponseAndPayload()
            throws ExecutionException, InterruptedException {

        Buffer chunk1 = allocator.fromAscii("test");
        Buffer chunk2 = allocator.fromAscii("payload");
        Buffer chunk3 = allocator.fromAscii("payload");
        HttpHeaders trailers = headersFactory.newEmptyTrailers();

        StreamingHttpRequest req = newRequestWithTrailers(GET, "/foo", HTTP_1_1,
                headersFactory.newHeaders(),
                allocator, from(chunk1, chunk2, chunk3, trailers));

        HttpResponseMetaData respMeta = newResponseMetaData(HTTP_1_1, OK,
                INSTANCE.newHeaders().add(CONTENT_TYPE, TEXT_PLAIN));

        Publisher<Object> respFlat = from(respMeta, chunk1, chunk2, chunk3, trailers);
        ArgumentCaptor<Publisher<Object>> reqFlatCaptor = ArgumentCaptor.forClass(Publisher.class);
        when(reqResp.apply(reqFlatCaptor.capture())).thenReturn(respFlat);

        Single<StreamingHttpResponse> responseSingle = http.request(req);

        StreamingHttpResponse resp = awaitIndefinitelyNonNull(responseSingle);

        assertThat(awaitIndefinitely(reqFlatCaptor.getValue()), contains(req, chunk1, chunk2, chunk3, trailers));

        assertThat(resp.status(), equalTo(OK));
        assertThat(resp.version(), equalTo(HTTP_1_1));
        assertThat(resp.headers().get(CONTENT_TYPE), equalTo(TEXT_PLAIN));

        assertThat(awaitIndefinitely(resp.payloadBody()), contains(chunk1, chunk2, chunk3));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void requestShouldInsertLastPayloadChunkInRequestPayloadWhenMissing()
            throws ExecutionException, InterruptedException {

        Buffer chunk1 = allocator.fromAscii("test");
        Buffer chunk2 = allocator.fromAscii("payload");

        StreamingHttpRequest req = newRequest(GET, "/foo", HTTP_1_1, headersFactory.newHeaders(),
                headersFactory.newEmptyTrailers(), allocator, from(chunk1, chunk2)); // NO chunk3 here!

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

        assertThat(awaitIndefinitely(resp.payloadBody()), contains(chunk1, chunk2, chunk3));
    }
}
