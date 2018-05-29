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

import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.http.api.HttpConnection;
import io.servicetalk.http.api.HttpPayloadChunk;
import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.HttpResponseMetaData;
import io.servicetalk.http.api.LastHttpPayloadChunk;
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
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.internal.Await.awaitIndefinitely;
import static io.servicetalk.concurrent.internal.Await.awaitIndefinitelyNonNull;
import static io.servicetalk.http.api.DefaultHttpHeadersFactory.INSTANCE;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_TYPE;
import static io.servicetalk.http.api.HttpHeaderValues.TEXT_PLAIN;
import static io.servicetalk.http.api.HttpPayloadChunks.newLastPayloadChunk;
import static io.servicetalk.http.api.HttpPayloadChunks.newPayloadChunk;
import static io.servicetalk.http.api.HttpProtocolVersions.HTTP_1_1;
import static io.servicetalk.http.api.HttpRequestMethods.GET;
import static io.servicetalk.http.api.HttpRequests.newRequest;
import static io.servicetalk.http.api.HttpResponseMetaDataFactory.newResponseMetaData;
import static io.servicetalk.http.api.HttpResponseStatuses.OK;
import static io.servicetalk.transport.netty.internal.ExecutionContextRule.immediate;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * This tests the common functionality in {@link AbstractHttpConnection}.
 */
public final class AbstractHttpConnectionTest {

    @Rule
    public final ServiceTalkTestTimeout timeout = new ServiceTalkTestTimeout();

    @Rule
    public final ExecutionContextRule ctx = immediate();

    @SuppressWarnings("unchecked")
    // Use Function to mock connection req/resp
    private Function<Publisher<Object>, Publisher<Object>> reqResp;

    private HttpConnection http;
    private HttpClientConfig config = new HttpClientConfig(new TcpClientConfig(true));

    private class MockHttpConnection extends AbstractHttpConnection<Connection<Object, Object>> {
        protected MockHttpConnection(final Connection<Object, Object> connection,
                                     final ReadOnlyHttpClientConfig config) {
            super(connection, config, ctx);
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
        http = new MockHttpConnection(mock(Connection.class), config.asReadOnly());
    }

    @Test
    public void shouldEmitMaxConcurrencyInSettingStream() throws ExecutionException, InterruptedException {
        Integer max = awaitIndefinitely(http.getSettingStream(HttpConnection.SettingKey.MAX_CONCURRENCY).first());
        assertThat(max, equalTo(101));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void requestShouldWriteFlatStreamToConnectionAndReadFlatStreamSplicedIntoResponseAndPayload()
            throws ExecutionException, InterruptedException {

        HttpPayloadChunk chunk1 = newPayloadChunk(DEFAULT_ALLOCATOR.fromAscii("test"));
        HttpPayloadChunk chunk2 = newPayloadChunk(DEFAULT_ALLOCATOR.fromAscii("payload"));
        LastHttpPayloadChunk chunk3 = newLastPayloadChunk(DEFAULT_ALLOCATOR.fromAscii("payload"),
                INSTANCE.newEmptyTrailers());

        HttpRequest<HttpPayloadChunk> req = newRequest(GET, "/foo", from(chunk1, chunk2, chunk3));

        HttpResponseMetaData respMeta = newResponseMetaData(HTTP_1_1, OK,
                INSTANCE.newHeaders().add(CONTENT_TYPE, TEXT_PLAIN));

        Publisher<Object> respFlat = from(respMeta, chunk1, chunk2, chunk3);
        ArgumentCaptor<Publisher<Object>> reqFlatCaptor = ArgumentCaptor.forClass(Publisher.class);
        when(reqResp.apply(reqFlatCaptor.capture())).thenReturn(respFlat);

        Single<HttpResponse<HttpPayloadChunk>> responseSingle = http.request(req);

        HttpResponse<HttpPayloadChunk> resp = awaitIndefinitelyNonNull(responseSingle);

        assertThat(awaitIndefinitely(reqFlatCaptor.getValue()), contains(req, chunk1, chunk2, chunk3));

        assertThat(resp.getStatus(), equalTo(OK));
        assertThat(resp.getVersion(), equalTo(HTTP_1_1));
        assertThat(resp.getHeaders().get(CONTENT_TYPE), equalTo(TEXT_PLAIN));

        assertThat(awaitIndefinitely(resp.getPayloadBody()), contains(chunk1, chunk2, chunk3));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void requestShouldInsertLastPayloadChunkInRequestPayloadWhenMissing()
            throws ExecutionException, InterruptedException {

        HttpPayloadChunk chunk1 = newPayloadChunk(DEFAULT_ALLOCATOR.fromAscii("test"));
        HttpPayloadChunk chunk2 = newPayloadChunk(DEFAULT_ALLOCATOR.fromAscii("payload"));

        HttpRequest<HttpPayloadChunk> req = newRequest(GET, "/foo", from(chunk1, chunk2)); // NO chunk3 here!

        HttpResponseMetaData respMeta = newResponseMetaData(HTTP_1_1, OK,
                INSTANCE.newHeaders().add(CONTENT_TYPE, TEXT_PLAIN));

        LastHttpPayloadChunk chunk3 = newLastPayloadChunk(DEFAULT_ALLOCATOR.fromAscii("payload"),
                INSTANCE.newEmptyTrailers());

        Publisher<Object> respFlat = from(respMeta, chunk1, chunk2, chunk3);
        ArgumentCaptor<Publisher<Object>> reqFlatCaptor = ArgumentCaptor.forClass(Publisher.class);
        when(reqResp.apply(reqFlatCaptor.capture())).thenReturn(respFlat);

        Single<HttpResponse<HttpPayloadChunk>> responseSingle = http.request(req);

        HttpResponse<HttpPayloadChunk> resp = awaitIndefinitelyNonNull(responseSingle);

        List<Object> objects = awaitIndefinitelyNonNull(reqFlatCaptor.getValue());
        assertThat(objects.subList(0, 3), contains(req, chunk1, chunk2)); // User provided chunks
        assertThat(objects.get(3), instanceOf(LastHttpPayloadChunk.class)); // Ensure new Last chunk inserted

        assertThat(resp.getStatus(), equalTo(OK));
        assertThat(resp.getVersion(), equalTo(HTTP_1_1));
        assertThat(resp.getHeaders().get(CONTENT_TYPE), equalTo(TEXT_PLAIN));

        assertThat(awaitIndefinitely(resp.getPayloadBody()), contains(chunk1, chunk2, chunk3));
    }
}
