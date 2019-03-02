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

import org.junit.Test;

import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;

import static io.netty.util.AsciiString.of;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_TYPE;
import static io.servicetalk.http.api.HttpHeaderValues.APPLICATION_JSON;
import static io.servicetalk.http.api.HttpHeaderValues.APPLICATION_X_WWW_FORM_URLENCODED;
import static io.servicetalk.http.api.HttpHeaderValues.APPLICATION_X_WWW_FORM_URLENCODED_UTF8;
import static io.servicetalk.http.api.HttpHeaderValues.TEXT_PLAIN;
import static io.servicetalk.http.api.HttpHeaderValues.TEXT_PLAIN_UTF_8;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HeaderUtilsTest {
    @Test
    public void hasContentType() {
        assertFalse(HeaderUtils.hasContentType(
                EmptyHttpHeaders.INSTANCE, TEXT_PLAIN, null));

        assertTrue(HeaderUtils.hasContentType(
                headersWithContentType(TEXT_PLAIN), TEXT_PLAIN, null));

        assertFalse(HeaderUtils.hasContentType(
                headersWithContentType(TEXT_PLAIN), APPLICATION_JSON, null));

        assertFalse(HeaderUtils.hasContentType(
                headersWithContentType(TEXT_PLAIN), TEXT_PLAIN, UTF_8));

        assertTrue(HeaderUtils.hasContentType(
                headersWithContentType(TEXT_PLAIN_UTF_8), TEXT_PLAIN, UTF_8));

        assertFalse(HeaderUtils.hasContentType(
                headersWithContentType(TEXT_PLAIN_UTF_8), TEXT_PLAIN, US_ASCII));

        assertTrue(HeaderUtils.hasContentType(
                headersWithContentType(APPLICATION_X_WWW_FORM_URLENCODED_UTF8),
                APPLICATION_X_WWW_FORM_URLENCODED, UTF_8));

        assertTrue(HeaderUtils.hasContentType(
                headersWithContentType(of("text/plain")), TEXT_PLAIN, null));

        assertTrue(HeaderUtils.hasContentType(
                headersWithContentType(of("text/plain;id=\"ABC@host.com\";charset=\"us-ascii\";total=2")),
                TEXT_PLAIN, null));

        assertTrue(HeaderUtils.hasContentType(
                headersWithContentType(of("text/plain;id=\"ABC@host.com\";charset=utf-8;total=2")),
                TEXT_PLAIN, UTF_8));

        assertTrue(HeaderUtils.hasContentType(
                headersWithContentType(of("text/plain;id=\"ABC@host.com\";charset=\"UTF-8\";total=2")),
                TEXT_PLAIN, UTF_8));

        assertTrue(HeaderUtils.hasContentType(
                headersWithContentType(of("text/plain; charset=\"us-ascii\"")), TEXT_PLAIN, null));

        assertTrue(HeaderUtils.hasContentType(
                headersWithContentType(of("text/plain; charset=\"us-ascii\"")), TEXT_PLAIN, US_ASCII));

        assertTrue(HeaderUtils.hasContentType(
                headersWithContentType(of("text/plain;id=\"ABC@host.com\";charset=\"us-ascii\";total=2")),
                TEXT_PLAIN, US_ASCII));

        assertTrue(HeaderUtils.hasContentType(
                headersWithContentType(of("image/png")), of("image/png"), null));

        assertFalse(HeaderUtils.hasContentType(
                headersWithContentType(of("image/png")), APPLICATION_X_WWW_FORM_URLENCODED, null));

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

    private static HttpHeaders headersWithContentType(final CharSequence contentType) {
        return DefaultHttpHeadersFactory.INSTANCE.newHeaders().set(CONTENT_TYPE, contentType);
    }
}
