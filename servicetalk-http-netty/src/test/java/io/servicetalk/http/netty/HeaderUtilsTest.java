/*
 * Copyright © 2019, 2021 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.http.api.DefaultHttpHeadersFactory;
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpHeadersFactory;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.servicetalk.buffer.api.ReadOnlyBufferAllocators.DEFAULT_RO_ALLOCATOR;
import static io.servicetalk.http.api.HeaderUtils.isTransferEncodingChunked;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_LENGTH;
import static io.servicetalk.http.api.HttpHeaderNames.TRANSFER_ENCODING;
import static io.servicetalk.http.api.HttpHeaderValues.CHUNKED;
import static io.servicetalk.http.api.HttpHeaderValues.GZIP;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_0;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_1;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_2_0;
import static io.servicetalk.http.api.HttpRequestMethod.GET;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.api.StreamingHttpRequests.newRequest;
import static io.servicetalk.http.api.StreamingHttpResponses.newResponse;
import static io.servicetalk.http.netty.HeaderUtils.addRequestTransferEncodingIfNecessary;
import static io.servicetalk.http.netty.HeaderUtils.addResponseTransferEncodingIfNecessary;
import static io.servicetalk.http.netty.HeaderUtils.removeTransferEncodingChunked;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeaderUtilsTest {

    private static final HttpHeadersFactory HTTP_HEADERS_FACTORY = DefaultHttpHeadersFactory.INSTANCE;

    private StreamingHttpRequest httpRequest;

    @BeforeEach
    void setUp() {
        httpRequest = newRequest(GET, "/", HTTP_1_1,
                HTTP_HEADERS_FACTORY.newHeaders(), DEFAULT_RO_ALLOCATOR, HTTP_HEADERS_FACTORY);
    }

    @Test
    void remoteTransferEncodingChunkedNoHeaders() {
        assertTrue(httpRequest.headers().isEmpty());

        removeTransferEncodingChunked(httpRequest.headers());
        assertTrue(httpRequest.headers().isEmpty());
    }

    @Test
    void removeTransferEncodingChunkedOnlyWhenOtherHeadersPresent() {
        assertTrue(httpRequest.headers().isEmpty());
        httpRequest.headers().add(CONTENT_LENGTH, "10")
                .add(TRANSFER_ENCODING, GZIP)
                .add(TRANSFER_ENCODING, CHUNKED)
                .add("Transfer-Encoding", "Chunked")
                .add(TRANSFER_ENCODING, "cHuNkEd");
        assertEquals(5, httpRequest.headers().size());

        removeTransferEncodingChunked(httpRequest.headers());
        assertFalse(isTransferEncodingChunked(httpRequest.headers()));
        assertEquals(2, httpRequest.headers().size());
        assertEquals(GZIP, httpRequest.headers().get(TRANSFER_ENCODING));
        assertEquals("10", httpRequest.headers().get(CONTENT_LENGTH));
    }

    @Test
    void removeTransferEncodingChunkedWithContentLength() {
        assertTrue(httpRequest.headers().isEmpty());
        httpRequest.headers().add(CONTENT_LENGTH, "10")
                .add(TRANSFER_ENCODING, CHUNKED);
        assertEquals(2, httpRequest.headers().size());

        removeTransferEncodingChunked(httpRequest.headers());
        assertFalse(isTransferEncodingChunked(httpRequest.headers()));
        assertEquals(1, httpRequest.headers().size());
        assertEquals("10", httpRequest.headers().get(CONTENT_LENGTH));
    }

    @Test
    void addTransferEncodingIfNecessaryNoHeaders() {
        assertTrue(httpRequest.headers().isEmpty());
        addRequestTransferEncodingIfNecessary(httpRequest, HTTP_1_1);
        assertOneTransferEncodingChunked(httpRequest.headers());
    }

    @Test
    void addTransferEncodingIfNecessaryContentLength() {
        assertTrue(httpRequest.headers().isEmpty());
        httpRequest.headers().add(CONTENT_LENGTH, "10");
        assertEquals(1, httpRequest.headers().size());

        addRequestTransferEncodingIfNecessary(httpRequest, HTTP_1_1);

        assertEquals(1, httpRequest.headers().size());
        assertFalse(isTransferEncodingChunked(httpRequest.headers()));
        assertEquals("10", httpRequest.headers().get(CONTENT_LENGTH));
    }

    @Test
    void addTransferEncodingIfNecessaryTransferEncodingChunked() {
        assertTrue(httpRequest.headers().isEmpty());
        httpRequest.headers().add(TRANSFER_ENCODING, CHUNKED);
        assertEquals(1, httpRequest.headers().size());

        addRequestTransferEncodingIfNecessary(httpRequest, HTTP_1_1);
        assertOneTransferEncodingChunked(httpRequest.headers());
    }

    @Test
    void addTransferEncodingIfNecessaryTransferEncodingChunkedCapitalCase() {
        assertTrue(httpRequest.headers().isEmpty());
        httpRequest.headers().add("Transfer-Encoding", "Chunked");
        assertEquals(1, httpRequest.headers().size());

        addRequestTransferEncodingIfNecessary(httpRequest, HTTP_1_1);
        assertOneTransferEncodingChunked(httpRequest.headers());
    }

    @Test
    void addTransferEncodingIfNecessaryTransferEncodingChunkedRandomCase() {
        assertTrue(httpRequest.headers().isEmpty());
        httpRequest.headers().add(TRANSFER_ENCODING, "cHuNkEd");
        assertEquals(1, httpRequest.headers().size());

        addRequestTransferEncodingIfNecessary(httpRequest, HTTP_1_1);
        assertOneTransferEncodingChunked(httpRequest.headers());
    }

    @Test
    void addTransferEncodingUsesConnectionProtocolNotRequestVersion() {
        // Mirrors ALPN h2->h1 fallback: an h2-created request on an h1 connection must still get TE: chunked.
        httpRequest = newRequest(GET, "/", HTTP_2_0,
                HTTP_HEADERS_FACTORY.newHeaders(), DEFAULT_RO_ALLOCATOR, HTTP_HEADERS_FACTORY);
        assertTrue(httpRequest.headers().isEmpty());

        addRequestTransferEncodingIfNecessary(httpRequest, HTTP_1_1);
        assertOneTransferEncodingChunked(httpRequest.headers());
    }

    @Test
    void addTransferEncodingSkippedForH2Connection() {
        // h2 frames the body itself, so no TE: chunked even when the request could carry one.
        httpRequest = newRequest(GET, "/", HTTP_2_0,
                HTTP_HEADERS_FACTORY.newHeaders(), DEFAULT_RO_ALLOCATOR, HTTP_HEADERS_FACTORY);
        assertTrue(httpRequest.headers().isEmpty());

        addRequestTransferEncodingIfNecessary(httpRequest, HTTP_2_0);
        assertTrue(httpRequest.headers().isEmpty());
    }

    @Test
    void addTransferEncodingSkippedForSubH2RequestOnH2Connection() {
        // An h2 transport must never carry TE: chunked, even when the request is versioned below h2.
        httpRequest = newRequest(GET, "/", HTTP_1_1,
                HTTP_HEADERS_FACTORY.newHeaders(), DEFAULT_RO_ALLOCATOR, HTTP_HEADERS_FACTORY);
        assertTrue(httpRequest.headers().isEmpty());

        addRequestTransferEncodingIfNecessary(httpRequest, HTTP_2_0);
        assertTrue(httpRequest.headers().isEmpty());
    }

    @Test
    void addTransferEncodingSkippedForHttp10Request() {
        // Encoded version is min(request, connection) = HTTP/1.0, which does not support chunked.
        httpRequest = newRequest(GET, "/", HTTP_1_0,
                HTTP_HEADERS_FACTORY.newHeaders(), DEFAULT_RO_ALLOCATOR, HTTP_HEADERS_FACTORY);
        assertTrue(httpRequest.headers().isEmpty());

        addRequestTransferEncodingIfNecessary(httpRequest, HTTP_1_1);
        assertTrue(httpRequest.headers().isEmpty());
    }

    @Test
    void addResponseTransferEncodingForHttp11() {
        final StreamingHttpResponse response = newResponse(OK, HTTP_1_1,
                HTTP_HEADERS_FACTORY.newHeaders(), DEFAULT_RO_ALLOCATOR, HTTP_HEADERS_FACTORY);
        addResponseTransferEncodingIfNecessary(response, GET, HTTP_1_1);
        assertOneTransferEncodingChunked(response.headers());
    }

    @Test
    void addResponseTransferEncodingSkippedForHttp10() {
        // Encoded version is min(response, connection) = HTTP/1.0, which does not support chunked; such a response
        // is close-delimited. Guards against keying the decision off the (higher) connection protocol.
        final StreamingHttpResponse response = newResponse(OK, HTTP_1_0,
                HTTP_HEADERS_FACTORY.newHeaders(), DEFAULT_RO_ALLOCATOR, HTTP_HEADERS_FACTORY);
        addResponseTransferEncodingIfNecessary(response, GET, HTTP_1_1);
        assertTrue(response.headers().isEmpty());
    }

    @Test
    void addResponseTransferEncodingSkippedForSubH2ResponseOnH2Connection() {
        // An h2 transport must never carry TE: chunked, even when the response is versioned below h2.
        final StreamingHttpResponse response = newResponse(OK, HTTP_1_1,
                HTTP_HEADERS_FACTORY.newHeaders(), DEFAULT_RO_ALLOCATOR, HTTP_HEADERS_FACTORY);
        addResponseTransferEncodingIfNecessary(response, GET, HTTP_2_0);
        assertTrue(response.headers().isEmpty());
    }

    private static void assertOneTransferEncodingChunked(final HttpHeaders headers) {
        assertEquals(1, headers.size());
        assertTrue(isTransferEncodingChunked(headers));
    }
}
