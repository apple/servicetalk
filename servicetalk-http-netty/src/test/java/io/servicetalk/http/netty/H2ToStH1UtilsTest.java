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
package io.servicetalk.http.netty;

import io.servicetalk.http.api.DefaultHttpCookiePair;
import io.servicetalk.http.api.DefaultHttpHeadersFactory;
import io.servicetalk.http.api.HttpCookiePair;
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpHeadersFactory;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static io.netty.util.internal.PlatformDependent.hashCodeAscii;
import static io.servicetalk.buffer.api.CharSequences.caseInsensitiveHashCode;
import static io.servicetalk.http.api.HttpHeaderNames.ACCEPT_PATCH;
import static io.servicetalk.http.api.HttpHeaderNames.COOKIE;
import static io.servicetalk.http.api.HttpHeaderNames.EXPIRES;
import static io.servicetalk.http.netty.H2ToStH1Utils.h1HeadersSplitCookieCrumbs;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

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
}
