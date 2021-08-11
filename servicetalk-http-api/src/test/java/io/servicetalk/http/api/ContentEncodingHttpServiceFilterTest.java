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

import io.servicetalk.buffer.api.CharSequences;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static io.servicetalk.buffer.api.CharSequences.contentEqualsIgnoreCase;
import static io.servicetalk.http.api.ContentEncodingHttpServiceFilter.matchAndRemoveEncoding;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_ENCODING;
import static java.util.function.Function.identity;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

class ContentEncodingHttpServiceFilterTest {
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
}
