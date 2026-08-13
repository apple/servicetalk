/*
 * Copyright © 2021, 2026 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.http.api.DefaultHttpCookiePair;
import io.servicetalk.http.api.DefaultHttpHeadersFactory;
import io.servicetalk.http.api.HttpCookiePair;
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpHeadersFactory;

import io.netty.util.AsciiString;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static io.netty.util.internal.PlatformDependent.hashCodeAscii;
import static io.servicetalk.buffer.api.CharSequences.caseInsensitiveHashCode;
import static io.servicetalk.http.api.HttpHeaderNames.ACCEPT_PATCH;
import static io.servicetalk.http.api.HttpHeaderNames.COOKIE;
import static io.servicetalk.http.api.HttpHeaderNames.EXPIRES;
import static io.servicetalk.http.netty.H2ToStH1Utils.h1HeadersSplitCookieCrumbs;
import static io.servicetalk.http.netty.H2ToStH1Utils.invalidPathReason;
import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.util.Arrays.asList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

class H2ToStH1UtilsTest {

    private static final int ARRAY_SIZE_HINT = 16;
    private static final HttpHeadersFactory H1_FACTORY = new DefaultHttpHeadersFactory(true, true, false,
            ARRAY_SIZE_HINT, 0);
    private static final HttpHeadersFactory H2_FACTORY = new H2HeadersFactory(true, true, false, ARRAY_SIZE_HINT, 0);

    private static int bucketIndex(int hashCode) {
        return hashCode & (ARRAY_SIZE_HINT - 1);
    }

    @Test
    void testH1HeadersSplitCookieCrumbsForH1Headers() {
        CharSequence secondHeaderName = EXPIRES;
        assertThat(bucketIndex(caseInsensitiveHashCode(COOKIE)),
                equalTo(bucketIndex(caseInsensitiveHashCode(secondHeaderName))));
        testH1HeadersSplitCookieCrumbs(H1_FACTORY, secondHeaderName);
    }

    @Test
    void testH1HeadersSplitCookieCrumbsForH2Headers() {
        CharSequence secondHeaderName = ACCEPT_PATCH;
        assertThat(bucketIndex(hashCodeAscii(COOKIE)), equalTo(bucketIndex(hashCodeAscii(secondHeaderName))));
        testH1HeadersSplitCookieCrumbs(H2_FACTORY, secondHeaderName);
    }

    void testH1HeadersSplitCookieCrumbs(HttpHeadersFactory headersFactory, CharSequence secondHeaderName) {
        HttpHeaders headers = headersFactory.newHeaders();
        // Add two headers which will be saved in the same entries[index]:
        headers.add(COOKIE, "a=b; c=d; e=f");
        String secondHeaderValue = "some-value";
        headers.add(secondHeaderName, secondHeaderValue);
        h1HeadersSplitCookieCrumbs(headers);

        List<HttpCookiePair> cookies = new ArrayList<>();
        for (HttpCookiePair pair : headers.getCookies()) {
            cookies.add(pair);
        }
        assertThat(cookies, hasSize(3));
        assertThat(cookies, containsInAnyOrder(new DefaultHttpCookiePair("a", "b"),
                new DefaultHttpCookiePair("c", "d"),
                new DefaultHttpCookiePair("e", "f")));
        assertThat(headers.get(secondHeaderName), equalTo(secondHeaderValue));
    }

    /**
     * An octet is permitted in a {@code :path} iff it is visible US-ASCII (VCHAR, 0x21-0x7e). All three positions are
     * checked because the generic field rules of RFC 9113 8.2.1 prohibit SP/HTAB only at the edges, whereas the 8.3.1
     * {@code :path} production admits neither anywhere.
     */
    @ParameterizedTest(name = "{displayName} [{index}] octet={0}")
    @MethodSource("octets")
    void pathOctetContract(int octet) {
        final boolean permitted = octet >= 0x21 && octet <= 0x7e;
        final char c = (char) octet;
        for (final String path : asList(c + "/ab", "/a" + c + "b", "/ab" + c)) {
            assertThat("path=" + path, invalidPathReason(path) == null, is(permitted));
            // AsciiString is the type HPACK produces inbound, so cover the ingress type too.
            assertThat("AsciiString path=" + path,
                    invalidPathReason(new AsciiString(path.getBytes(ISO_8859_1))) == null, is(permitted));
        }
    }

    /**
     * obs-text is rejected even though RFC 9110 5.5 permits it in a generic field value, because the RFC 9113 8.3.1
     * {@code :path} production is US-ASCII only. Named explicitly so that editing {@link #pathOctetContract(int)}'s
     * single predicate cannot silently re-permit the range.
     */
    @ParameterizedTest(name = "{displayName} [{index}] octet={0}")
    @ValueSource(ints = {0x80, 0x9f, 0xa0, 0xc3, 0xe9, 0xfe, 0xff})
    void pathRejectsObsText(int octet) {
        assertThat(invalidPathReason("/a" + (char) octet + "b"), notNullValue());
        assertThat(invalidPathReason(new AsciiString(new byte[]{'/', 'a', (byte) octet, 'b'})), notNullValue());
    }

    /**
     * Regression guard against narrowing the scan to a single octet: each of these chars has a low byte that is a
     * legal VCHAR, so narrowing would accept it.
     */
    @ParameterizedTest(name = "{displayName} [{index}] char=U+{0}")
    @ValueSource(strings = {"0121", "0161", "017e", "1161", "ff21"})
    void pathRejectsCharsAboveOneByte(String hexChar) {
        final char c = (char) Integer.parseInt(hexChar, 16);
        assertThat("low byte is not a legal VCHAR, so this proves nothing",
                (c & 0xff) >= 0x21 && (c & 0xff) <= 0x7e, is(true));
        assertThat(invalidPathReason("/a" + c + "b"), notNullValue());
    }

    @ParameterizedTest(name = "{displayName} [{index}] path={0}")
    @ValueSource(strings = {"/", "*", "/ok", "/a?q=1", "/a%20b", "/~!$&'()+,;=:@"})
    void pathAcceptsValidTargets(String path) {
        assertThat(invalidPathReason(path), nullValue());
    }

    @Test
    void invalidPathReasonMessages() {
        assertThat(invalidPathReason(null), containsString("missing"));
        assertThat(invalidPathReason(""), containsString("must not be empty"));
        // Two illegal octets: the first must be the one reported.
        final String message = invalidPathReason("/a b" + (char) 0x0d + "c");
        assertThat(message, containsString("index 2"));
        assertThat(message, containsString("0x20"));
        assertThat(message, containsString("expected [VCHAR (0x21-0x7e)]"));
        // The offending value is never echoed back, only its position and code point.
        assertThat(message, not(containsString("/a")));
    }

    private static IntStream octets() {
        return IntStream.rangeClosed(0x00, 0xff);
    }
}
