/*
 * Copyright © 2022 Apple Inc. and the ServiceTalk project authors
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

import org.junit.jupiter.api.Test;

import static io.servicetalk.buffer.api.Matchers.contentEqualTo;
import static io.servicetalk.http.api.DefaultHttpSetCookiesTest.quotesInValuePreserved;
import static io.servicetalk.http.api.HeaderUtils.COOKIE_STRICT_RFC_6265;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DefaultHttpSetCookiesRfc6265Test {
    @Test
    void throwIfNoSpaceBeforeCookieAttributeValue() {
        final HttpHeaders headers = DefaultHttpHeadersFactory.INSTANCE.newHeaders();
        headers.add("set-cookie", "first=12345;Extension");
        headers.add("set-cookie", "second=12345;Expires=Mon, 22 Aug 2022 20:12:35 GMT");
        headers.add("set-cookie", "third=\"12345\";Expires=Mon, 22 Aug 2022 20:12:35 GMT");
        if (COOKIE_STRICT_RFC_6265) {
            throwIfNoSpaceBeforeCookieAttributeValue(headers);
        } else {
            HttpSetCookie setCookie = headers.getSetCookie("first");
            assertThat(setCookie, notNullValue());
            assertThat(setCookie.value(), contentEqualTo("12345"));
            setCookie = headers.getSetCookie("second");
            assertThat(setCookie, notNullValue());
            assertThat(setCookie.value(), contentEqualTo("12345"));
            setCookie = headers.getSetCookie("third");
            assertThat(setCookie, notNullValue());
            assertThat(setCookie.value(), contentEqualTo("12345"));
            assertThat(setCookie.isWrapped(), equalTo(true));
        }
    }

    private static void throwIfNoSpaceBeforeCookieAttributeValue(HttpHeaders headers) {
        Exception exception;

        exception = assertThrows(IllegalArgumentException.class, () -> headers.getSetCookie("first"));
        assertThat(exception.getMessage(),
                allOf(containsString("first"), containsString("space is required after ;")));

        exception = assertThrows(IllegalArgumentException.class, () -> headers.getSetCookie("second"));
        assertThat(exception.getMessage(),
                allOf(containsString("second"), containsString("space is required after ;")));

        exception = assertThrows(IllegalArgumentException.class, () -> headers.getSetCookie("third"));
        assertThat(exception.getMessage(),
                allOf(containsString("third"), containsString("space is required after ;")));
    }

    @Test
    void spaceAfterQuotedValue() {
        final HttpHeaders headers = DefaultHttpHeadersFactory.INSTANCE.newHeaders();
        headers.add("set-cookie",
                "qwerty=\"12345\"; Domain=somecompany.co.uk; Path=/; Expires=Wed, 30 Aug 2019 00:00:00 GMT");
        quotesInValuePreserved(headers);
    }
}
