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
package io.servicetalk.http.api;

import io.servicetalk.serialization.api.SerializationException;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.util.function.Predicate;

import static io.netty.util.AsciiString.of;
import static io.servicetalk.http.api.HeaderUtils.checkContentType;
import static io.servicetalk.http.api.HeaderUtils.isTchar;
import static io.servicetalk.http.api.HeaderUtils.isTransferEncodingChunked;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_LENGTH;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_TYPE;
import static io.servicetalk.http.api.HttpHeaderNames.ORIGIN;
import static io.servicetalk.http.api.HttpHeaderNames.TRANSFER_ENCODING;
import static io.servicetalk.http.api.HttpHeaderValues.APPLICATION_JSON;
import static io.servicetalk.http.api.HttpHeaderValues.APPLICATION_X_WWW_FORM_URLENCODED;
import static io.servicetalk.http.api.HttpHeaderValues.APPLICATION_X_WWW_FORM_URLENCODED_UTF_8;
import static io.servicetalk.http.api.HttpHeaderValues.CHUNKED;
import static io.servicetalk.http.api.HttpHeaderValues.TEXT_PLAIN;
import static io.servicetalk.http.api.HttpHeaderValues.TEXT_PLAIN_UTF_8;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_16;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.rules.ExpectedException.none;

public class HeaderUtilsTest {

    @Rule
    public final ExpectedException expectedException = none();

    @Test
    public void defaultDebugHeaderFilter() {
        assertEquals(APPLICATION_JSON, HeaderUtils.DEFAULT_DEBUG_HEADER_FILTER.apply(CONTENT_TYPE, APPLICATION_JSON));

        assertEquals("3495", HeaderUtils.DEFAULT_DEBUG_HEADER_FILTER.apply(CONTENT_LENGTH, "3495"));

        assertEquals(CHUNKED, HeaderUtils.DEFAULT_DEBUG_HEADER_FILTER.apply(TRANSFER_ENCODING, CHUNKED));

        assertEquals(CHUNKED, HeaderUtils.DEFAULT_DEBUG_HEADER_FILTER.apply("TrAnsFeR-eNcOdiNg", CHUNKED));

        assertEquals("<filtered>", HeaderUtils.DEFAULT_DEBUG_HEADER_FILTER.apply(ORIGIN, "some/origin"));
    }

    @Test
    public void hasContentType() {
        assertFalse(HeaderUtils.hasContentType(
                EmptyHttpHeaders.INSTANCE, TEXT_PLAIN, null));

        assertTrue(HeaderUtils.hasContentType(
                headersWithContentType(TEXT_PLAIN), TEXT_PLAIN, null));

        assertTrue(HeaderUtils.hasContentType(
                headersWithContentType(TEXT_PLAIN_UTF_8), TEXT_PLAIN, null));

        assertFalse(HeaderUtils.hasContentType(
                headersWithContentType(TEXT_PLAIN), APPLICATION_JSON, null));

        assertTrue(HeaderUtils.hasContentType(
                headersWithContentType(TEXT_PLAIN), TEXT_PLAIN, UTF_8));

        assertFalse(HeaderUtils.hasContentType(
                headersWithContentType(TEXT_PLAIN), TEXT_PLAIN, UTF_16));

        assertTrue(HeaderUtils.hasContentType(
                headersWithContentType(TEXT_PLAIN_UTF_8), TEXT_PLAIN, UTF_8));

        assertFalse(HeaderUtils.hasContentType(
                headersWithContentType(TEXT_PLAIN_UTF_8), TEXT_PLAIN, US_ASCII));

        assertTrue(HeaderUtils.hasContentType(
                headersWithContentType(APPLICATION_X_WWW_FORM_URLENCODED),
                APPLICATION_X_WWW_FORM_URLENCODED, UTF_8));

        assertFalse(HeaderUtils.hasContentType(
                headersWithContentType(APPLICATION_X_WWW_FORM_URLENCODED),
                APPLICATION_X_WWW_FORM_URLENCODED, UTF_16));

        assertTrue(HeaderUtils.hasContentType(
                headersWithContentType(APPLICATION_X_WWW_FORM_URLENCODED_UTF_8),
                APPLICATION_X_WWW_FORM_URLENCODED, UTF_8));

        assertFalse(HeaderUtils.hasContentType(
                headersWithContentType(APPLICATION_X_WWW_FORM_URLENCODED_UTF_8),
                APPLICATION_X_WWW_FORM_URLENCODED, UTF_16));

        assertTrue(HeaderUtils.hasContentType(
                headersWithContentType(of("text/plain")), TEXT_PLAIN, null));

        assertTrue(HeaderUtils.hasContentType(
                headersWithContentType(of("Text/Plain")), TEXT_PLAIN, null));

        assertTrue(HeaderUtils.hasContentType(
                headersWithContentType(of("text/plain; charset=UTF-8")), TEXT_PLAIN, null));

        assertTrue(HeaderUtils.hasContentType(
                headersWithContentType(of("text/plain;charset=UTF-8")), TEXT_PLAIN, null));

        assertTrue(HeaderUtils.hasContentType(
                headersWithContentType(of("Text/Plain")), TEXT_PLAIN, UTF_8));

        assertTrue(HeaderUtils.hasContentType(
                headersWithContentType(of("text/plain;id=\"ABC@host.com\";charset=\"us-ascii\";total=2")),
                TEXT_PLAIN, null));

        assertTrue(HeaderUtils.hasContentType(
                headersWithContentType(of("text/plain;id=\"ABC@host.com\";charset=utf-8;total=2")),
                TEXT_PLAIN, UTF_8));

        assertTrue(HeaderUtils.hasContentType(
                headersWithContentType(of("text/plain;id=\"ABC@host.com\";charset=\"UTF-8\";total=2")),
                TEXT_PLAIN, UTF_8));

        assertFalse(HeaderUtils.hasContentType(
                headersWithContentType(of("text/plain;id=\"ABC@host.com\";Charset=\"UTF-16\";total=2")),
                TEXT_PLAIN, UTF_8));

        assertTrue(HeaderUtils.hasContentType(
                headersWithContentType(of("text/plain;id=\"ABC@host.com\";total=2")),
                TEXT_PLAIN, UTF_8));

        assertFalse(HeaderUtils.hasContentType(
                headersWithContentType(of("text/plain;id=\"ABC@host.com\";total=2")),
                TEXT_PLAIN, UTF_16));

        assertTrue(HeaderUtils.hasContentType(
                headersWithContentType(of("text/plain; charset=\"us-ascii\"")), TEXT_PLAIN, null));

        assertTrue(HeaderUtils.hasContentType(
                headersWithContentType(of("text/plain; charset=\"us-ascii\"")), TEXT_PLAIN, US_ASCII));

        assertTrue(HeaderUtils.hasContentType(
                headersWithContentType(of("text/plain;id=\"ABC@host.com\";charset=\"us-ascii\";total=2")),
                TEXT_PLAIN, US_ASCII));

        assertTrue(HeaderUtils.hasContentType(
                headersWithContentType(of("image/png")), of("image/png"), null));

        assertTrue(HeaderUtils.hasContentType(
                headersWithContentType(of("image/png")), of("image/png"), UTF_8));

        assertFalse(HeaderUtils.hasContentType(
                headersWithContentType(of("image/png")), APPLICATION_X_WWW_FORM_URLENCODED, null));

        assertFalse(HeaderUtils.hasContentType(
                headersWithContentType(of("image/png")), APPLICATION_X_WWW_FORM_URLENCODED, UTF_8));

        assertTrue(HeaderUtils.hasContentType(
                headersWithContentType(of("image/png;charset=unknown-charset")), of("image/png"), null));

        assertTrue(HeaderUtils.hasContentType(
                headersWithContentType(of("image/png;charset=unknown-charset")), of("image/png"),
                new Charset("unknown-charset", new String[0]) {
                    @Override
                    public boolean contains(final Charset cs) {
                        return false;
                    }

                    @Override
                    public CharsetDecoder newDecoder() {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public CharsetEncoder newEncoder() {
                        throw new UnsupportedOperationException();
                    }
                }));
    }

    @Test
    public void checkContentTypeCases() {
        final String invalidContentType = "invalid";
        final Predicate<HttpHeaders> jsonContentTypeValidator =
                headers -> headers.contains(CONTENT_TYPE, APPLICATION_JSON);

        checkContentType(headersWithContentType(APPLICATION_JSON), jsonContentTypeValidator);

        expectedException.expect(instanceOf(SerializationException.class));
        expectedException.expectMessage(containsString(invalidContentType));

        checkContentType(headersWithContentType(of(invalidContentType)), jsonContentTypeValidator);
    }

    @Test
    public void isTransferEncodingChunkedFalseCases() {
        HttpHeaders headers = DefaultHttpHeadersFactory.INSTANCE.newHeaders();
        assertTrue(headers.isEmpty());
        assertFalse(isTransferEncodingChunked(headers));

        headers.add("Some-Header", "Some-Value");
        assertFalse(isTransferEncodingChunked(headers));

        headers.add(TRANSFER_ENCODING, "Some-Value");
        assertFalse(isTransferEncodingChunked(headers));

        assertFalse(isTransferEncodingChunked(headersWithTransferEncoding(of("gzip"))
                .add(TRANSFER_ENCODING, "base64")));
    }

    @Test
    public void isTransferEncodingChunkedTrueCases() {
        HttpHeaders headers = DefaultHttpHeadersFactory.INSTANCE.newHeaders();
        assertTrue(headers.isEmpty());
        // lower case
        headers.set(TRANSFER_ENCODING, CHUNKED);
        assertOneTransferEncodingChunked(headers);
        // Capital Case
        headers.set("Transfer-Encoding", "Chunked");
        assertOneTransferEncodingChunked(headers);
        // Random case
        headers.set(TRANSFER_ENCODING, "cHuNkEd");
        assertOneTransferEncodingChunked(headers);

        assertTrue(isTransferEncodingChunked(headersWithTransferEncoding(of("chunked,gzip"))));
        assertTrue(isTransferEncodingChunked(headersWithTransferEncoding(of("chunked, gzip"))));
        assertTrue(isTransferEncodingChunked(headersWithTransferEncoding(of("gzip, chunked"))));
        assertTrue(isTransferEncodingChunked(headersWithTransferEncoding(of("gzip,chunked"))));
        assertTrue(isTransferEncodingChunked(headersWithTransferEncoding(of("gzip, chunked, base64"))));

        assertTrue(isTransferEncodingChunked(headersWithTransferEncoding(of("gzip"))
                .add(TRANSFER_ENCODING, "chunked")));
        assertTrue(isTransferEncodingChunked(headersWithTransferEncoding(of("chunked"))
                .add(TRANSFER_ENCODING, "gzip")));
    }

    private static HttpHeaders headersWithContentType(final CharSequence contentType) {
        return DefaultHttpHeadersFactory.INSTANCE.newHeaders().set(CONTENT_TYPE, contentType);
    }

    private static HttpHeaders headersWithTransferEncoding(final CharSequence contentType) {
        return DefaultHttpHeadersFactory.INSTANCE.newHeaders().set(TRANSFER_ENCODING, contentType);
    }

    private static void assertOneTransferEncodingChunked(final HttpHeaders headers) {
        assertEquals(1, headers.size());
        assertTrue(isTransferEncodingChunked(headers));
    }

    @Test
    public void validateToken() {
        // Make sure the old and new validation logic is equivalent:
        for (int b = Byte.MIN_VALUE; b <= Byte.MAX_VALUE; ++b) {
            final byte value = (byte) (b & 0xff);
            assertEquals("Unexpected result for byte: " + value,
                    originalValidateTokenLogic(value), isTchar(value));
        }
    }

    private static boolean originalValidateTokenLogic(final byte value) {
        if (value < '!') {
            return false;
        }
        switch (value) {
            case '(':
            case ')':
            case '<':
            case '>':
            case '@':
            case ',':
            case ';':
            case ':':
            case '\\':
            case '"':
            case '/':
            case '[':
            case ']':
            case '?':
            case '=':
            case '{':
            case '}':
            case 127:   // DEL
                return false;
            default:
                return true;
        }
    }
}
