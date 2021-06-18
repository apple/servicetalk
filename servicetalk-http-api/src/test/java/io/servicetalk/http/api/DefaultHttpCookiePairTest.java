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

import org.junit.jupiter.api.Test;

import static io.servicetalk.buffer.api.CharSequences.newAsciiString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

class DefaultHttpCookiePairTest {

    @Test
    void testEqual() {
        assertThat(new DefaultHttpCookiePair("foo", "bar"),
                is(new DefaultHttpCookiePair("foo", "bar")));
        assertThat(new DefaultHttpCookiePair("foo", "bar").hashCode(),
                is(new DefaultHttpCookiePair("foo", "bar").hashCode()));

        // comparing String and AsciiString
        assertThat(new DefaultHttpCookiePair("foo", "bar"),
                   is(new DefaultHttpCookiePair(newAsciiString("foo"), newAsciiString("bar"))));
        assertThat(new DefaultHttpCookiePair("foo", "bar").hashCode(),
                   is(new DefaultHttpCookiePair(newAsciiString("foo"), newAsciiString("bar")).hashCode()));

        // isWrapped attribute is ignored:
        assertThat(new DefaultHttpCookiePair("foo", "bar", true),
                is(new DefaultHttpCookiePair("foo", "bar", false)));
        assertThat(new DefaultHttpCookiePair("foo", "bar", true).hashCode(),
                is(new DefaultHttpCookiePair("foo", "bar", false).hashCode()));
    }

    @Test
    void testNotEqual() {
        // Name is case-sensitive:
        assertThat(new DefaultHttpCookiePair("foo", "bar"),
                   is(not(new DefaultHttpCookiePair("Foo", "bar"))));
        assertThat(new DefaultHttpCookiePair("foo", "bar").hashCode(),
                is(not(new DefaultHttpCookiePair("fooo", "bar").hashCode())));

        assertThat(new DefaultHttpCookiePair("foo", "bar"),
                   is(not(new DefaultHttpCookiePair(newAsciiString("Foo"), newAsciiString("bar")))));
        assertThat(new DefaultHttpCookiePair("foo", "bar").hashCode(),
                   is(not(new DefaultHttpCookiePair(newAsciiString("fooo"), newAsciiString("bar")).hashCode())));

        assertThat(new DefaultHttpCookiePair("foo", "bar", true),
                is(not(new DefaultHttpCookiePair("foO", "bar", true))));
        assertThat(new DefaultHttpCookiePair("foo", "bar", true).hashCode(),
                is(not(new DefaultHttpCookiePair("fooo", "bar", true).hashCode())));

        // Value is case-sensitive:
        assertThat(new DefaultHttpCookiePair("foo", "bar"),
                is(not(new DefaultHttpCookiePair("foo", "Bar"))));
        assertThat(new DefaultHttpCookiePair("foo", "bar").hashCode(),
                is(not(new DefaultHttpCookiePair("foo", "barr").hashCode())));

        assertThat(new DefaultHttpCookiePair("foo", "bar", false),
                is(not(new DefaultHttpCookiePair("foo", "baR", false))));
        assertThat(new DefaultHttpCookiePair("foo", "bar", false),
                is(not(new DefaultHttpCookiePair("foo", "barr", false).hashCode())));
    }
}
