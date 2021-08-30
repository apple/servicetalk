/*
 * Copyright © 2019-2021 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.http.api.StreamingHttpResponse;

import org.hamcrest.Matcher;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.servicetalk.buffer.api.Matchers.contentEqualTo;
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
import static io.servicetalk.http.api.StreamingHttpRequests.newRequest;
import static io.servicetalk.http.api.StreamingHttpResponses.newResponse;
import static io.servicetalk.http.netty.HeaderUtils.setRequestContentLength;
import static io.servicetalk.http.netty.HeaderUtils.setResponseContentLength;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

class ContentLengthTest {

    private static final DefaultHttpHeadersFactory headersFactory = new DefaultHttpHeadersFactory(false, false, false);

    @Test
    void shouldNotCalculateRequestContentLengthFromEmptyPublisher() throws Exception {
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
    void shouldCalculateRequestContentLengthWhenNoPayloadBodySet() throws Exception {
        shouldCalculateRequestContentLengthFromEmptyPublisher(POST, false);
        shouldCalculateRequestContentLengthFromEmptyPublisher(PUT, false);
        shouldCalculateRequestContentLengthFromEmptyPublisher(PATCH, false);
    }

    @Test
    void shouldCalculateRequestContentLengthFromEmptyPublisher() throws Exception {
        shouldCalculateRequestContentLengthFromEmptyPublisher(POST, true);
        shouldCalculateRequestContentLengthFromEmptyPublisher(PUT, true);
        shouldCalculateRequestContentLengthFromEmptyPublisher(PATCH, true);
    }

    private static void shouldCalculateRequestContentLengthFromEmptyPublisher(HttpRequestMethod method,
                                                                              boolean emptyPublisher)
            throws Exception {
        StreamingHttpRequest request = newAggregatedRequest(method).toStreamingRequest();
        if (emptyPublisher) {
            request = request.payloadBody(Publisher.empty());
        }
        setRequestContentLengthAndVerify(request, contentEqualTo("0"));
    }

    @Test
    void shouldCalculateRequestContentLengthFromSingleItemPublisher() throws Exception {
        StreamingHttpRequest request = newAggregatedRequest().toStreamingRequest()
                .payloadBody(Publisher.from("Hello"), textSerializer());
        setRequestContentLengthAndVerify(request, contentEqualTo("5"));
    }

    @Test
    void shouldCalculateRequestContentLengthFromTwoItemPublisher() throws Exception {
        StreamingHttpRequest request = newAggregatedRequest().toStreamingRequest()
                .payloadBody(Publisher.from("Hello", "World"), textSerializer());
        setRequestContentLengthAndVerify(request, contentEqualTo("10"));
    }

    @Test
    void shouldCalculateRequestContentLengthFromMultipleItemPublisher() throws Exception {
        StreamingHttpRequest request = newAggregatedRequest().toStreamingRequest()
                .payloadBody(Publisher.from("Hello", " ", "World", "!"), textSerializer());
        setRequestContentLengthAndVerify(request, contentEqualTo("12"));
    }

    @Test
    void shouldCalculateRequestContentLengthFromTransformedMultipleItemPublisher() throws Exception {
        StreamingHttpRequest request = newAggregatedRequest().payloadBody("Hello", textSerializer())
                .toStreamingRequest().transformPayloadBody(payload -> payload.concat(Publisher.from(" ", "World", "!")),
                        textDeserializer(), textSerializer());
        setRequestContentLengthAndVerify(request, contentEqualTo("12"));
    }

    @Test
    void shouldCalculateRequestContentLengthFromTransformedRawMultipleItemPublisher() throws Exception {
        StreamingHttpRequest request = newAggregatedRequest().payloadBody("Hello", textSerializer())
                .toStreamingRequest().transformMessageBody(payload -> payload.map(obj -> (Buffer) obj)
                        .concat(Publisher.from(" ", "World", "!").map(DEFAULT_RO_ALLOCATOR::fromAscii)));
        setRequestContentLengthAndVerify(request, contentEqualTo("12"));
    }

    @Test
    void shouldCalculateResponseContentLengthWhenNoPayloadBodySet() throws Exception {
        StreamingHttpResponse response = newAggregatedResponse().toStreamingResponse();
        setResponseContentLengthAndVerify(response, contentEqualTo("0"));
    }

    @Test
    void shouldCalculateResponseContentLengthFromEmptyPublisher() throws Exception {
        StreamingHttpResponse response = newAggregatedResponse().toStreamingResponse()
                .payloadBody(Publisher.empty());
        setResponseContentLengthAndVerify(response, contentEqualTo("0"));
    }

    @Test
    void shouldCalculateResponseContentLengthFromSingleItemPublisher() throws Exception {
        StreamingHttpResponse response = newAggregatedResponse().toStreamingResponse()
                .payloadBody(Publisher.from("Hello"), textSerializer());
        setResponseContentLengthAndVerify(response, contentEqualTo("5"));
    }

    @Test
    void shouldCalculateResponseContentLengthFromTwoItemPublisher() throws Exception {
        StreamingHttpResponse response = newAggregatedResponse().toStreamingResponse()
                .payloadBody(Publisher.from("Hello", "World"), textSerializer());
        setResponseContentLengthAndVerify(response, contentEqualTo("10"));
    }

    @Test
    void shouldCalculateResponseContentLengthFromMultipleItemPublisher() throws Exception {
        StreamingHttpResponse response = newAggregatedResponse().toStreamingResponse()
                .payloadBody(Publisher.from("Hello", " ", "World", "!"), textSerializer());
        setResponseContentLengthAndVerify(response, contentEqualTo("12"));
    }

    @Test
    void shouldCalculateResponseContentLengthFromTransformedMultipleItemPublisher() throws Exception {
        StreamingHttpResponse response = newAggregatedResponse().payloadBody("Hello", textSerializer())
                .toStreamingResponse()
                .transformPayloadBody(payload -> payload.concat(Publisher.from(" ", "World", "!")),
                        textDeserializer(), textSerializer());
        setResponseContentLengthAndVerify(response, contentEqualTo("12"));
    }

    private static HttpRequest newAggregatedRequest() throws Exception {
        return newAggregatedRequest(GET);
    }

    private static HttpRequest newAggregatedRequest(HttpRequestMethod method) throws Exception {
        return newRequest(method, "/", HTTP_1_1, headersFactory.newHeaders(), DEFAULT_ALLOCATOR, headersFactory)
                .toRequest().toFuture().get();
    }

    private static HttpResponse newAggregatedResponse() throws Exception {
        return newResponse(OK, HTTP_1_1, headersFactory.newHeaders(), DEFAULT_ALLOCATOR, headersFactory)
                .toResponse().toFuture().get();
    }

    private static void setRequestContentLengthAndVerify(final StreamingHttpRequest request,
                                                         final Matcher<CharSequence> matcher) throws Exception {
        final AtomicBoolean messageBodySubscribed = new AtomicBoolean(false);
        request.transformMessageBody(publisher -> publisher.afterOnSubscribe(__ -> messageBodySubscribed.set(true)));
        Collection<Object> flattened = setRequestContentLength(request.version(), request).toFuture().get();
        assertFlattened(flattened, matcher, messageBodySubscribed);
    }

    private static void setResponseContentLengthAndVerify(final StreamingHttpResponse response,
                                                          final Matcher<CharSequence> matcher) throws Exception {

        final AtomicBoolean messageBodySubscribed = new AtomicBoolean(false);
        response.transformMessageBody(publisher -> publisher.afterOnSubscribe(__ -> messageBodySubscribed.set(true)));
        Collection<Object> flattened = setResponseContentLength(response.version(), response).toFuture().get();
        assertFlattened(flattened, matcher, messageBodySubscribed);
    }

    private static void assertFlattened(final Collection<Object> flattened,
                                        final Matcher<CharSequence> matcher,
                                        final AtomicBoolean messageBodySubscribed) {
        assertThat("Unexpected number of items in the flattened stream.", flattened, hasSize(greaterThanOrEqualTo(2)));
        Iterator<Object> iterator = flattened.iterator();
        Object firstItem = iterator.next();
        assertThat("Unexpected first item in the flattened stream.", firstItem, instanceOf(HttpMetaData.class));
        assertThat(((HttpMetaData) firstItem).headers().get(CONTENT_LENGTH), matcher);
        assertLastItems(iterator);
        assertThat("No subscribe for message body", messageBodySubscribed.get(), is(true));
    }

    private static void assertLastItems(Iterator<Object> iterator) {
        Object prev = null;
        Object last = null;
        while (iterator.hasNext()) {
            prev = last;
            last = iterator.next();
        }
        assertThat("Unexpected last item in the flattened stream.", last, instanceOf(HttpHeaders.class));
        assertThat("Unexpected previous item in the flattened stream.", prev,
                anyOf(nullValue(), instanceOf(Buffer.class)));
    }
}
