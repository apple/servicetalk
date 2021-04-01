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

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static io.servicetalk.http.api.HttpHeaderNames.ACCEPT_PATCH;
import static io.servicetalk.http.api.HttpHeaderNames.COOKIE;
import static io.servicetalk.http.api.HttpHeaderValues.TEXT_PLAIN;
import static io.servicetalk.http.netty.H2ToStH1Utils.h1HeadersSplitCookieCrumbs;
import static java.util.Objects.requireNonNull;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;

public class H2ToStH1UtilsTest {

    @Test
    public void testH1HeadersSplitCookieCrumbsForH1Headers() {
        testH1HeadersSplitCookieCrumbs(DefaultHttpHeadersFactory.INSTANCE);
    }

    @Test
    public void testH1HeadersSplitCookieCrumbsForH2Headers() {
        testH1HeadersSplitCookieCrumbs(H2HeadersFactory.INSTANCE);
    }

    public void testH1HeadersSplitCookieCrumbs(HttpHeadersFactory headersFactory) {
        HttpHeaders headers = headersFactory.newHeaders();
        // Add two headers which will be saved in the same entries[index]:
        headers.add(new ConstantHashCharSequence(COOKIE), "a=b; c=d; e=f");
        headers.add(new ConstantHashCharSequence(ACCEPT_PATCH), TEXT_PLAIN);
        h1HeadersSplitCookieCrumbs(headers);

        List<HttpCookiePair> cookies = new ArrayList<>();
        for (HttpCookiePair pair : headers.getCookies()) {
            cookies.add(pair);
        }
        assertThat(cookies, hasSize(3));
        assertThat(cookies, containsInAnyOrder(new DefaultHttpCookiePair("a", "b"),
                new DefaultHttpCookiePair("c", "d"),
                new DefaultHttpCookiePair("e", "f")));
    }

    private static final class ConstantHashCharSequence implements CharSequence {
        private final CharSequence sequence;

        ConstantHashCharSequence(final CharSequence sequence) {
            this.sequence = requireNonNull(sequence);
        }

        @Override
        public int length() {
            return sequence.length();
        }

        @Override
        public char charAt(final int index) {
            return sequence.charAt(index);
        }

        @Override
        public CharSequence subSequence(final int start, final int end) {
            return new ConstantHashCharSequence(sequence.subSequence(start, end));
        }

        @Override
        public int hashCode() {
            return 0;   // always the same
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof ConstantHashCharSequence)) {
                return false;
            }
            return sequence.equals(((ConstantHashCharSequence) o).sequence);
        }

        @Override
        public String toString() {
            return sequence.toString();
        }
    }
}
