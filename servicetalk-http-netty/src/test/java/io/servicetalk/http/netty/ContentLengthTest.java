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
import io.servicetalk.http.api.HttpMetaData;
import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequests;
import io.servicetalk.http.api.StreamingHttpResponse;

import org.hamcrest.Matcher;
import org.junit.Test;

import java.util.Collection;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_LENGTH;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_1;
import static io.servicetalk.http.api.HttpRequestMethod.GET;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.api.HttpSerializationProviders.textSerializer;
import static io.servicetalk.http.api.StreamingHttpResponses.newResponse;
import static io.servicetalk.http.netty.AbstractNettyHttpServerTest.ExecutorSupplier.CACHED;
import static io.servicetalk.http.netty.HeaderUtils.setRequestContentLength;
import static io.servicetalk.http.netty.HeaderUtils.setResponseContentLength;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;

public class ContentLengthTest extends AbstractNettyHttpServerTest {

    private static final DefaultHttpHeadersFactory headersFactory = new DefaultHttpHeadersFactory(false, false);

    public ContentLengthTest() {
        super(CACHED, CACHED);
    }

    @Test
    public void shouldCalculateRequestContentLengthFromEmptyPublisher() throws Exception {
        StreamingHttpRequest request = newAggregatedRequest().toStreamingRequest()
                .payloadBody(Publisher.empty());
        setRequestContentLengthAndVerify(request, nullValue(CharSequence.class));
    }

    @Test
    public void shouldCalculateRequestContentLengthFromSingleItemPublisher() throws Exception {
        StreamingHttpRequest request = newAggregatedRequest().toStreamingRequest()
                .payloadBody(Publisher.from("Hello"), textSerializer());
        setRequestContentLengthAndVerify(request, is("5"));
    }

    @Test
    public void shouldCalculateRequestContentLengthFromTwoItemPublisher() throws Exception {
        StreamingHttpRequest request = newAggregatedRequest().toStreamingRequest()
                .payloadBody(Publisher.from("Hello", "World"), textSerializer());
        setRequestContentLengthAndVerify(request, is("10"));
    }

    @Test
    public void shouldCalculateRequestContentLengthFromMultipleItemPublisher() throws Exception {
        StreamingHttpRequest request = newAggregatedRequest().toStreamingRequest()
                .payloadBody(Publisher.from("Hello", " ", "World", "!"), textSerializer());
        setRequestContentLengthAndVerify(request, is("12"));
    }

    @Test
    public void shouldCalculateResponseContentLengthFromEmptyPublisher() throws Exception {
        StreamingHttpResponse response = newAggregatedResponse().toStreamingResponse()
                .payloadBody(Publisher.empty());
        setResponseContentLengthAndVerify(response, is("0"));
    }

    @Test
    public void shouldCalculateResponseContentLengthFromSingleItemPublisher() throws Exception {
        StreamingHttpResponse response = newAggregatedResponse().toStreamingResponse()
                .payloadBody(Publisher.from("Hello"), textSerializer());
        setResponseContentLengthAndVerify(response, is("5"));
    }

    @Test
    public void shouldCalculateResponseContentLengthFromTwoItemPublisher() throws Exception {
        StreamingHttpResponse response = newAggregatedResponse().toStreamingResponse()
                .payloadBody(Publisher.from("Hello", "World"), textSerializer());
        setResponseContentLengthAndVerify(response, is("10"));
    }

    @Test
    public void shouldCalculateResponseContentLengthFromMultipleItemPublisher() throws Exception {
        StreamingHttpResponse response = newAggregatedResponse().toStreamingResponse()
                .payloadBody(Publisher.from("Hello", " ", "World", "!"), textSerializer());
        setResponseContentLengthAndVerify(response, is("12"));
    }

    private static HttpRequest newAggregatedRequest() {
        return awaitSingleIndefinitelyNonNull(StreamingHttpRequests.newRequest(GET, "/", HTTP_1_1,
                headersFactory.newHeaders(), DEFAULT_ALLOCATOR, headersFactory).toRequest());
    }

    private static HttpResponse newAggregatedResponse() {
        return awaitSingleIndefinitelyNonNull(newResponse(OK, HTTP_1_1, headersFactory.newHeaders(),
                DEFAULT_ALLOCATOR, headersFactory).toResponse());
    }

    private static void setRequestContentLengthAndVerify(final StreamingHttpRequest request,
                                                         final Matcher<CharSequence> matcher) throws Exception {
        Collection<Object> flattened = setRequestContentLength(request).toFuture().get();
        assertThat("Unexpected items in the flattened request.", flattened, hasSize(greaterThanOrEqualTo(2)));
        Object firstItem = flattened.iterator().next();
        assertThat("Unexpected items in the flattened request.", firstItem, is(instanceOf(HttpMetaData.class)));
        assertThat(((HttpMetaData) firstItem).headers().get(CONTENT_LENGTH), matcher);
    }

    private static void setResponseContentLengthAndVerify(final StreamingHttpResponse response,
                                                          final Matcher<CharSequence> matcher) throws Exception {
        Collection<Object> flattened = setResponseContentLength(response).toFuture().get();
        assertThat("Unexpected items in the flattened response.", flattened, hasSize(greaterThanOrEqualTo(2)));
        Object firstItem = flattened.iterator().next();
        assertThat("Unexpected items in the flattened response.", firstItem, is(instanceOf(HttpMetaData.class)));
        assertThat(((HttpMetaData) firstItem).headers().get(CONTENT_LENGTH), matcher);
    }
}
