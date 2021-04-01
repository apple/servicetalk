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

import io.servicetalk.http.api.DefaultHttpHeadersFactory;
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpHeadersFactory;
import io.servicetalk.http.api.StreamingHttpRequest;

import org.junit.Before;
import org.junit.Test;

import static io.servicetalk.buffer.api.ReadOnlyBufferAllocators.DEFAULT_RO_ALLOCATOR;
import static io.servicetalk.http.api.HeaderUtils.isTransferEncodingChunked;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_LENGTH;
import static io.servicetalk.http.api.HttpHeaderNames.TRANSFER_ENCODING;
import static io.servicetalk.http.api.HttpHeaderValues.CHUNKED;
import static io.servicetalk.http.api.HttpHeaderValues.GZIP;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_1;
import static io.servicetalk.http.api.HttpRequestMethod.GET;
import static io.servicetalk.http.api.StreamingHttpRequests.newRequest;
import static io.servicetalk.http.netty.HeaderUtils.addRequestTransferEncodingIfNecessary;
import static io.servicetalk.http.netty.HeaderUtils.removeTransferEncodingChunked;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HeaderUtilsTest {

    private static final HttpHeadersFactory HTTP_HEADERS_FACTORY = DefaultHttpHeadersFactory.INSTANCE;

    private StreamingHttpRequest httpRequest;

    @Before
    public void setUp() {
        httpRequest = newRequest(GET, "/", HTTP_1_1,
                HTTP_HEADERS_FACTORY.newHeaders(), DEFAULT_RO_ALLOCATOR, HTTP_HEADERS_FACTORY);
    }

    @Test
    public void remoteTransferEncodingChunkedNoHeaders() {
        assertTrue(httpRequest.headers().isEmpty());

        removeTransferEncodingChunked(httpRequest.headers());
        assertTrue(httpRequest.headers().isEmpty());
    }

    @Test
    public void removeTransferEncodingChunkedOnlyWhenOtherHeadersPresent() {
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
    public void removeTransferEncodingChunkedWithContentLength() {
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
    public void addTransferEncodingIfNecessaryNoHeaders() {
        assertTrue(httpRequest.headers().isEmpty());
        addRequestTransferEncodingIfNecessary(httpRequest);
        assertOneTransferEncodingChunked(httpRequest.headers());
    }

    @Test
    public void addTransferEncodingIfNecessaryContentLength() {
        assertTrue(httpRequest.headers().isEmpty());
        httpRequest.headers().add(CONTENT_LENGTH, "10");
        assertEquals(1, httpRequest.headers().size());

        addRequestTransferEncodingIfNecessary(httpRequest);

        assertEquals(1, httpRequest.headers().size());
        assertFalse(isTransferEncodingChunked(httpRequest.headers()));
        assertEquals("10", httpRequest.headers().get(CONTENT_LENGTH));
    }

    @Test
    public void addTransferEncodingIfNecessaryTransferEncodingChunked() {
        assertTrue(httpRequest.headers().isEmpty());
        httpRequest.headers().add(TRANSFER_ENCODING, CHUNKED);
        assertEquals(1, httpRequest.headers().size());

        addRequestTransferEncodingIfNecessary(httpRequest);
        assertOneTransferEncodingChunked(httpRequest.headers());
    }

    @Test
    public void addTransferEncodingIfNecessaryTransferEncodingChunkedCapitalCase() {
        assertTrue(httpRequest.headers().isEmpty());
        httpRequest.headers().add("Transfer-Encoding", "Chunked");
        assertEquals(1, httpRequest.headers().size());

        addRequestTransferEncodingIfNecessary(httpRequest);
        assertOneTransferEncodingChunked(httpRequest.headers());
    }

    @Test
    public void addTransferEncodingIfNecessaryTransferEncodingChunkedRandomCase() {
        assertTrue(httpRequest.headers().isEmpty());
        httpRequest.headers().add(TRANSFER_ENCODING, "cHuNkEd");
        assertEquals(1, httpRequest.headers().size());

        addRequestTransferEncodingIfNecessary(httpRequest);
        assertOneTransferEncodingChunked(httpRequest.headers());
    }

    private static void assertOneTransferEncodingChunked(final HttpHeaders headers) {
        assertEquals(1, headers.size());
        assertTrue(isTransferEncodingChunked(headers));
    }
}
