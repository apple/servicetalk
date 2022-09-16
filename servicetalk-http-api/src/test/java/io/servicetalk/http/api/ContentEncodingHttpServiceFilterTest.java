/*
 * Copyright © 2021 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.http.api;

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.buffer.api.CharSequences;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.encoding.api.BufferDecoderGroupBuilder;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.List;

import static io.servicetalk.buffer.api.CharSequences.contentEqualsIgnoreCase;
import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.api.Single.failed;
import static io.servicetalk.encoding.api.Identity.identityEncoder;
import static io.servicetalk.encoding.netty.NettyBufferEncoders.gzipDefault;
import static io.servicetalk.http.api.ContentEncodingHttpRequesterFilterTest.assertContentLength;
import static io.servicetalk.http.api.ContentEncodingHttpServiceFilter.matchAndRemoveEncoding;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_ENCODING;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_LENGTH;
import static java.util.Arrays.asList;
import static java.util.function.Function.identity;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

class ContentEncodingHttpServiceFilterTest extends AbstractHttpServiceFilterTest {
    @Test
    void testMatchAndRemoveEncodingFirst() {
        List<CharSequence> supportedDecoders = new ArrayList<>();
        supportedDecoders.add("foo");
        supportedDecoders.add("deflate");
        HttpHeaders headers = DefaultHttpHeadersFactory.INSTANCE.newHeaders();
        headers.add(CONTENT_ENCODING, CharSequences.newAsciiString(" deflate , gzip "));
        CharSequence match = matchAndRemoveEncoding(supportedDecoders, identity(),
                headers.valuesIterator(CONTENT_ENCODING), headers);
        assertThat("unexpected match: " + match, contentEqualsIgnoreCase(match, "deflate"), is(true));
        CharSequence contentEncoding = headers.get(CONTENT_ENCODING);
        assertThat("unexpected header: " + contentEncoding, contentEqualsIgnoreCase(contentEncoding, " gzip "),
                is(true));
    }

    @Test
    void testMatchAndRemoveEncodingLastDoesNotMatch() {
        List<CharSequence> supportedDecoders = new ArrayList<>();
        supportedDecoders.add("gzip");
        HttpHeaders headers = DefaultHttpHeadersFactory.INSTANCE.newHeaders();
        headers.add(CONTENT_ENCODING, CharSequences.newAsciiString(" deflate , gzip "));
        CharSequence match = matchAndRemoveEncoding(supportedDecoders, identity(),
                headers.valuesIterator(CONTENT_ENCODING), headers);
        assertThat("unexpected match: " + match, match, nullValue());
        CharSequence contentEncoding = headers.get(CONTENT_ENCODING);
        assertThat("unexpected header: " + contentEncoding,
                contentEqualsIgnoreCase(contentEncoding, " deflate , gzip "), is(true));
    }

    @Test
    void testMatchAndRemoveEncodingMiddleDoesNotMatch() {
        List<CharSequence> supportedDecoders = new ArrayList<>();
        supportedDecoders.add("foo");
        HttpHeaders headers = DefaultHttpHeadersFactory.INSTANCE.newHeaders();
        headers.add(CONTENT_ENCODING, CharSequences.newAsciiString(" deflate , foo , gzip "));
        CharSequence match = matchAndRemoveEncoding(supportedDecoders, identity(),
                headers.valuesIterator(CONTENT_ENCODING), headers);
        assertThat("unexpected match: " + match, match, nullValue());
        CharSequence contentEncoding = headers.get(CONTENT_ENCODING);
        assertThat("unexpected header: " + contentEncoding,
                contentEqualsIgnoreCase(contentEncoding, " deflate , foo , gzip "), is(true));
    }

    @ParameterizedTest(name = "{displayName} [{index}] {0}")
    @EnumSource(SecurityType.class)
    void contentLengthRemovedOnEncode(final SecurityType security) throws Exception {
        setUp(security);
        StreamingHttpRequester requester = createFilter((respFactory, request) -> {
            try {
                assertContentLength(request.headers(), request.payloadBody());
            } catch (Exception e) {
                return failed(e);
            }

            String responseStr = "aaaaaaaaaaaaaaaa";
            Buffer responseBuf = DEFAULT_ALLOCATOR.fromAscii(responseStr);
            Buffer encoded = gzipDefault().encoder().serialize(responseBuf, DEFAULT_ALLOCATOR);
            return Single.succeeded(respFactory.ok().payloadBody(from(encoded))
                    .addHeader(CONTENT_LENGTH, String.valueOf(encoded.readableBytes()))
                    .addHeader(CONTENT_ENCODING, gzipDefault().encodingName()));
        }, new ContentEncodingHttpServiceFilter(asList(gzipDefault(), identityEncoder()),
                new BufferDecoderGroupBuilder()
                        .add(gzipDefault())
                        .add(identityEncoder(), false).build()));

        // Simulate the user (or earlier filter) setting the content length before compression.
        StreamingHttpRequest request = requester.post("/foo");
        String payloadBody = "bbbbbbbbbbbbbbbbbbb";
        request.payloadBody(from(requester.executionContext().bufferAllocator().fromAscii(payloadBody)));
        request.headers().add(CONTENT_LENGTH, String.valueOf(payloadBody.length()));
        request.contentEncoding(gzipDefault());
        StreamingHttpResponse response = requester.request(request).toFuture().get();
        assertContentLength(response.headers(), response.payloadBody());
    }
}
