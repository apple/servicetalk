/*
 * Copyright © 2019-2020 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.http.api.DefaultHttpHeadersFactory;
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpMetaData;
import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.http.api.HttpRequestMethod;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequests;
import io.servicetalk.http.api.StreamingHttpResponse;

import org.hamcrest.Matcher;
import org.junit.Test;

import java.util.Collection;
import java.util.Iterator;

import static io.servicetalk.buffer.api.ReadOnlyBufferAllocators.DEFAULT_RO_ALLOCATOR;
import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_LENGTH;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_1;
import static io.servicetalk.http.api.HttpRequestMethod.CONNECT;
import static io.servicetalk.http.api.HttpRequestMethod.DELETE;
import static io.servicetalk.http.api.HttpRequestMethod.GET;
import static io.servicetalk.http.api.HttpRequestMethod.HEAD;
import static io.servicetalk.http.api.HttpRequestMethod.OPTIONS;
import static io.servicetalk.http.api.HttpRequestMethod.PATCH;
import static io.servicetalk.http.api.HttpRequestMethod.POST;
import static io.servicetalk.http.api.HttpRequestMethod.PUT;
import static io.servicetalk.http.api.HttpRequestMethod.TRACE;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.api.HttpSerializationProviders.textDeserializer;
import static io.servicetalk.http.api.HttpSerializationProviders.textSerializer;
import static io.servicetalk.http.api.StreamingHttpResponses.newResponse;
import static io.servicetalk.http.netty.AbstractNettyHttpServerTest.ExecutorSupplier.CACHED;
import static io.servicetalk.http.netty.HeaderUtils.setRequestContentLength;
import static io.servicetalk.http.netty.HeaderUtils.setResponseContentLength;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

public class ContentLengthTest extends AbstractNettyHttpServerTest {

    private static final DefaultHttpHeadersFactory headersFactory = new DefaultHttpHeadersFactory(false, false);

    public ContentLengthTest() {
        super(CACHED, CACHED);
    }

    @Test
    public void shouldNotCalculateRequestContentLengthFromEmptyPublisherForGetRequest() throws Exception {
        shouldNotCalculateRequestContentLengthFromEmptyPublisher(GET);
        shouldNotCalculateRequestContentLengthFromEmptyPublisher(HEAD);
        shouldNotCalculateRequestContentLengthFromEmptyPublisher(DELETE);
        shouldNotCalculateRequestContentLengthFromEmptyPublisher(CONNECT);
        shouldNotCalculateRequestContentLengthFromEmptyPublisher(OPTIONS);
        shouldNotCalculateRequestContentLengthFromEmptyPublisher(TRACE);
    }

    private static void shouldNotCalculateRequestContentLengthFromEmptyPublisher(HttpRequestMethod method)
            throws Exception {
        StreamingHttpRequest request = newAggregatedRequest(method).toStreamingRequest()
                .payloadBody(Publisher.empty());
        setRequestContentLengthAndVerify(request, nullValue(CharSequence.class));
    }

    @Test
    public void shouldCalculateRequestContentLengthFromEmptyPublisherForPostRequest() throws Exception {
        shouldCalculateRequestContentLengthFromEmptyPublisher(POST);
        shouldCalculateRequestContentLengthFromEmptyPublisher(PUT);
        shouldCalculateRequestContentLengthFromEmptyPublisher(PATCH);
    }

    private static void shouldCalculateRequestContentLengthFromEmptyPublisher(HttpRequestMethod method)
            throws Exception {
        StreamingHttpRequest request = newAggregatedRequest(method).toStreamingRequest()
                .payloadBody(Publisher.empty());
        setRequestContentLengthAndVerify(request, is("0"));
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
    public void shouldCalculateRequestContentLengthFromTransformedMultipleItemPublisher() throws Exception {
        StreamingHttpRequest request = newAggregatedRequest().payloadBody("Hello", textSerializer())
                .toStreamingRequest().transformPayloadBody(payload -> payload.concat(Publisher.from(" ", "World", "!")),
                        textDeserializer(), textSerializer());
        setRequestContentLengthAndVerify(request, is("12"));
    }

    @Test
    public void shouldCalculateRequestContentLengthFromTransformedRawMultipleItemPublisher() throws Exception {
        StreamingHttpRequest request = newAggregatedRequest().payloadBody("Hello", textSerializer())
                .toStreamingRequest().transformRawPayloadBody(payload -> payload.map(obj -> (Buffer) obj)
                        .concat(Publisher.from(" ", "World", "!").map(DEFAULT_RO_ALLOCATOR::fromAscii)));
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

    @Test
    public void shouldCalculateResponseContentLengthFromTransformedMultipleItemPublisher() throws Exception {
        StreamingHttpResponse response = newAggregatedResponse().payloadBody("Hello", textSerializer())
                .toStreamingResponse().transformPayloadBody(payload -> payload.concat(Publisher.from(" ", "World", "!")),
                        textDeserializer(), textSerializer());
        setResponseContentLengthAndVerify(response, is("12"));
    }

    @Test
    public void shouldCalculateResponseContentLengthFromTransformedRawMultipleItemPublisher() throws Exception {
        StreamingHttpResponse response = newAggregatedResponse().payloadBody("Hello", textSerializer())
                .toStreamingResponse().transformRawPayloadBody(payload -> payload.map(obj -> (Buffer) obj)
                        .concat(Publisher.from(" ", "World", "!").map(DEFAULT_RO_ALLOCATOR::fromAscii)));
        setResponseContentLengthAndVerify(response, is("12"));
    }

    private static HttpRequest newAggregatedRequest() {
        return newAggregatedRequest(GET);
    }

    private static HttpRequest newAggregatedRequest(HttpRequestMethod method) {
        return awaitSingleIndefinitelyNonNull(StreamingHttpRequests.newRequest(method, "/", HTTP_1_1,
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
        Iterator<Object> iterator = flattened.iterator();
        Object firstItem = iterator.next();
        assertThat("Unexpected items in the flattened request.", firstItem, is(instanceOf(HttpMetaData.class)));
        assertThat(((HttpMetaData) firstItem).headers().get(CONTENT_LENGTH), matcher);
        assertLastItem(iterator);
    }

    private static void setResponseContentLengthAndVerify(final StreamingHttpResponse response,
                                                          final Matcher<CharSequence> matcher) throws Exception {
        Collection<Object> flattened = setResponseContentLength(response).toFuture().get();
        assertThat("Unexpected items in the flattened response.", flattened, hasSize(greaterThanOrEqualTo(2)));
        Iterator<Object> iterator = flattened.iterator();
        Object firstItem = iterator.next();
        assertThat("Unexpected items in the flattened response.", firstItem, is(instanceOf(HttpMetaData.class)));
        assertThat(((HttpMetaData) firstItem).headers().get(CONTENT_LENGTH), matcher);
        assertLastItem(iterator);
    }

    private static void assertLastItem(Iterator<Object> iterator) {
        Object item = null;
        while (iterator.hasNext()) {
            item = iterator.next();
        }
        assertThat("Unexpected last item in the flattened stream.", item, is(instanceOf(HttpHeaders.class)));
    }
}
