/*
 * Copyright © 2019 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.http.api.DefaultHttpHeadersFactory;
import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.http.api.HttpRequests;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.HttpResponses;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Collection;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_LENGTH;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_1;
import static io.servicetalk.http.api.HttpRequestMethod.GET;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.api.HttpSerializationProviders.textSerializer;
import static io.servicetalk.http.netty.AbstractNettyHttpServerTest.ExecutorSupplier.CACHED;
import static io.servicetalk.http.netty.HeaderUtils.setRequestContentLength;
import static io.servicetalk.http.netty.HeaderUtils.setResponseContentLength;
import static java.util.Collections.singletonList;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;

@RunWith(Parameterized.class)
public class ContentLengthTest extends AbstractNettyHttpServerTest {

    private static final DefaultHttpHeadersFactory headersFactory = new DefaultHttpHeadersFactory(false, false);

    public ContentLengthTest(final ExecutorSupplier clientExecutorSupplier,
                             final ExecutorSupplier serverExecutorSupplier) {
        super(clientExecutorSupplier, serverExecutorSupplier);
    }

    @Parameterized.Parameters
    public static Collection<ExecutorSupplier[]> clientExecutors() {
        return singletonList(new ExecutorSupplier[]{CACHED, CACHED});
    }

    @Test
    public void shouldCalculateRequestContentLengthFromEmptyPublisher() throws Exception {
        StreamingHttpRequest request = newAggregatedRequest().toStreamingRequest()
                .payloadBody(Publisher.empty());
        request = setRequestContentLength(request).toFuture().get();
        assertThat(request.headers().get(CONTENT_LENGTH), nullValue());
    }

    @Test
    public void shouldCalculateRequestContentLengthFromSingleItemPublisher() throws Exception {
        StreamingHttpRequest request = newAggregatedRequest().toStreamingRequest()
                .payloadBody(Publisher.from("Hello"), textSerializer());
        request = setRequestContentLength(request).toFuture().get();
        assertThat(request.headers().get(CONTENT_LENGTH), is("5"));
    }

    @Test
    public void shouldCalculateRequestContentLengthFromTwoItemPublisher() throws Exception {
        StreamingHttpRequest request = newAggregatedRequest().toStreamingRequest()
                .payloadBody(Publisher.from("Hello", "World"), textSerializer());
        request = setRequestContentLength(request).toFuture().get();
        assertThat(request.headers().get(CONTENT_LENGTH), is("10"));
    }

    @Test
    public void shouldCalculateRequestContentLengthFromMultipleItemPublisher() throws Exception {
        StreamingHttpRequest request = newAggregatedRequest().toStreamingRequest()
                .payloadBody(Publisher.from("Hello", " ", "World", "!"), textSerializer());
        request = setRequestContentLength(request).toFuture().get();
        assertThat(request.headers().get(CONTENT_LENGTH), is("12"));
    }

    @Test
    public void shouldCalculateResponseContentLengthFromEmptyPublisher() throws Exception {
        StreamingHttpResponse response = newAggregatedResponse().toStreamingResponse()
                .payloadBody(Publisher.empty());
        response = setResponseContentLength(response).toFuture().get();
        assertThat(response.headers().get(CONTENT_LENGTH), is("0"));
    }

    @Test
    public void shouldCalculateResponseContentLengthFromSingleItemPublisher() throws Exception {
        StreamingHttpResponse response = newAggregatedResponse().toStreamingResponse()
                .payloadBody(Publisher.from("Hello"), textSerializer());
        response = setResponseContentLength(response).toFuture().get();
        assertThat(response.headers().get(CONTENT_LENGTH), is("5"));
    }

    @Test
    public void shouldCalculateResponseContentLengthFromTwoItemPublisher() throws Exception {
        StreamingHttpResponse response = newAggregatedResponse().toStreamingResponse()
                .payloadBody(Publisher.from("Hello", "World"), textSerializer());
        response = setResponseContentLength(response).toFuture().get();
        assertThat(response.headers().get(CONTENT_LENGTH), is("10"));
    }

    @Test
    public void shouldCalculateResponseContentLengthFromMultipleItemPublisher() throws Exception {
        StreamingHttpResponse response = newAggregatedResponse().toStreamingResponse()
                .payloadBody(Publisher.from("Hello", " ", "World", "!"), textSerializer());
        response = setResponseContentLength(response).toFuture().get();
        assertThat(response.headers().get(CONTENT_LENGTH), is("12"));
    }

    private static HttpRequest newAggregatedRequest() {
        return HttpRequests.newRequest(GET, "/", HTTP_1_1, headersFactory.newHeaders(),
                headersFactory.newEmptyTrailers(), DEFAULT_ALLOCATOR);
    }

    private static HttpResponse newAggregatedResponse() {
        return HttpResponses.newResponse(OK, HTTP_1_1, headersFactory.newHeaders(),
                headersFactory.newEmptyTrailers(), DEFAULT_ALLOCATOR);
    }
}
