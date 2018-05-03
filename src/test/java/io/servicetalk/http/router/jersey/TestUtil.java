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
package io.servicetalk.http.router.jersey;

import io.servicetalk.buffer.Buffer;
import io.servicetalk.buffer.BufferAllocator;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.http.api.HttpPayloadChunk;
import io.servicetalk.http.api.HttpProtocolVersion;
import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.http.api.HttpRequestMethod;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.HttpResponseStatus;

import org.hamcrest.Matcher;

import java.util.function.Function;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Executors.immediate;
import static io.servicetalk.concurrent.api.Publisher.just;
import static io.servicetalk.concurrent.internal.Await.awaitIndefinitely;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_LENGTH;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_TYPE;
import static io.servicetalk.http.api.HttpHeaderNames.HOST;
import static io.servicetalk.http.api.HttpHeaderNames.TRANSFER_ENCODING;
import static io.servicetalk.http.api.HttpHeaderValues.CHUNKED;
import static io.servicetalk.http.api.HttpPayloadChunks.newPayloadChunk;
import static io.servicetalk.http.api.HttpProtocolVersions.HTTP_1_0;
import static io.servicetalk.http.api.HttpProtocolVersions.HTTP_1_1;
import static io.servicetalk.http.api.HttpRequests.newRequest;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.Matchers.equalToIgnoringCase;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

public final class TestUtil {
    static final String TEST_HOST = "some.fakehost.tld";

    private TestUtil() {
        // no instances
    }

    public static String getContentAsString(final HttpResponse<HttpPayloadChunk> res) {
        return getContentAsString(res.getPayloadBody());
    }

    public static String getContentAsString(final Publisher<HttpPayloadChunk> content) {
        try {
            //noinspection ConstantConditions
            return awaitIndefinitely(content
                    .reduce(StringBuilder::new, (sb, c) -> sb.append(c.getContent().toString(UTF_8)))
                    .map(StringBuilder::toString));
        } catch (final Throwable t) {
            throw new IllegalStateException("Failed to extract content from: " + content, t);
        }
    }

    public static Publisher<HttpPayloadChunk> asChunkPublisher(final String content, final BufferAllocator allocator) {
        return asChunkPublisher(allocator.fromUtf8(content));
    }

    public static Publisher<HttpPayloadChunk> asChunkPublisher(final byte[] content, final BufferAllocator allocator) {
        return asChunkPublisher(allocator.wrap(content));
    }

    private static Publisher<HttpPayloadChunk> asChunkPublisher(final Buffer content) {
        return just(newPayloadChunk(content), immediate());
    }

    static HttpRequest<HttpPayloadChunk> newH10Request(final HttpRequestMethod method,
                                                       final String requestTarget) {
        return newRequest(HTTP_1_0, method, "http://" + TEST_HOST + requestTarget, immediate());
    }

    static HttpRequest<HttpPayloadChunk> newH11Request(final HttpRequestMethod method,
                                                       final String requestTarget) {
        final HttpRequest<HttpPayloadChunk> request = newRequest(HTTP_1_1, method, requestTarget, immediate());
        request.getHeaders().add(HOST, TEST_HOST);
        return request;
    }

    static HttpRequest<HttpPayloadChunk> newH11Request(final HttpRequestMethod method,
                                                       final String requestTarget,
                                                       final Buffer content) {
        final HttpRequest<HttpPayloadChunk> request = newRequest(HTTP_1_1, method, requestTarget,
                just(newPayloadChunk(content), immediate()));
        request.getHeaders().add(HOST, TEST_HOST);
        request.getHeaders().add(CONTENT_LENGTH, Integer.toString(content.getReadableBytes()));
        return request;
    }

    static void assertResponse(final HttpResponse<HttpPayloadChunk> res,
                               final HttpResponseStatus expectedStatusCode,
                               @Nullable final CharSequence expectedContentType,
                               final String expectedContent) {
        assertResponse(res, expectedStatusCode, expectedContentType, is(expectedContent), $ -> expectedContent.length());
    }

    static void assertResponse(final HttpResponse<HttpPayloadChunk> res,
                               final HttpResponseStatus expectedStatusCode,
                               @Nullable final CharSequence expectedContentType,
                               final Matcher<String> contentMatcher,
                               final int expectedContentLength) {
        assertResponse(res, expectedStatusCode, expectedContentType, contentMatcher, $ -> expectedContentLength);
    }

    static void assertResponse(final HttpResponse<HttpPayloadChunk> res,
                               final HttpResponseStatus expectedStatusCode,
                               @Nullable final CharSequence expectedContentType,
                               final Matcher<String> contentMatcher,
                               final Function<String, Integer> expectedContentLengthExtractor) {
        assertResponse(res, HTTP_1_1, expectedStatusCode, expectedContentType, contentMatcher,
                expectedContentLengthExtractor);
    }

    static void assertResponse(final HttpResponse<HttpPayloadChunk> res,
                               final HttpProtocolVersion expectedHttpVersion,
                               final HttpResponseStatus expectedStatusCode,
                               @Nullable final CharSequence expectedContentType,
                               final Matcher<String> contentMatcher,
                               final Function<String, Integer> expectedContentLengthExtractor) {

        assertThat(res.getVersion(), is(expectedHttpVersion));
        assertThat(res.getStatus().getCode(), is(expectedStatusCode.getCode()));
        assertThat(res.getStatus().getReasonPhrase(), is(expectedStatusCode.getReasonPhrase()));

        if (expectedContentType != null) {
            assertThat(res.getHeaders().get(CONTENT_TYPE), is(expectedContentType));
        } else {
            assertThat(res.getHeaders().contains(CONTENT_TYPE), is(false));
        }

        final String contentAsString = getContentAsString(res);

        @Nullable
        final Integer expectedContentLength = expectedContentLengthExtractor.apply(contentAsString);
        if (expectedContentLength != null) {
            assertThat(res.getHeaders().get(CONTENT_LENGTH), is(Integer.toString(expectedContentLength)));
            res.getHeaders().getAll(TRANSFER_ENCODING)
                    .forEachRemaining(h -> assertThat(h.toString(), equalToIgnoringCase("chunked")));
        } else {
            assertThat(res.getHeaders().contains(CONTENT_LENGTH), is(false));
            assertThat(res.getHeaders().get(TRANSFER_ENCODING), is(CHUNKED));
        }

        assertThat(contentAsString, contentMatcher);
    }
}
