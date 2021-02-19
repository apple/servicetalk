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

import org.junit.Test;

import static io.servicetalk.buffer.api.CharSequences.newAsciiString;
import static io.servicetalk.http.api.HttpSetCookie.SameSite.Lax;
import static io.servicetalk.http.api.HttpSetCookie.SameSite.None;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

public class DefaultHttpSetCookieTest {

    @Test
    public void testEqual() {
        assertThat(new DefaultHttpSetCookie("foo", "bar"),
                is(new DefaultHttpSetCookie("foo", "bar")));
        assertThat(new DefaultHttpSetCookie("foo", "bar").hashCode(),
                is(new DefaultHttpSetCookie("foo", "bar").hashCode()));

        assertThat(new DefaultHttpSetCookie("foo", "bar"),
                   is(new DefaultHttpSetCookie(newAsciiString("foo"), newAsciiString("bar"))));
        assertThat(new DefaultHttpSetCookie("foo", "bar").hashCode(),
                   is(new DefaultHttpSetCookie(newAsciiString("foo"), newAsciiString("bar")).hashCode()));

        // Domain is case-insensitive, other attributes are ignored:
        assertThat(new DefaultHttpSetCookie("foo", "bar", "/", "servicetalk.io", null, 1L, None, true, false, true),
                is(new DefaultHttpSetCookie("foo", "bar", "/", "ServiceTalk.io", null, 2L, Lax, false, true, false)));
        assertThat(new DefaultHttpSetCookie("foo", "bar", "/", "servicetalk.io", null, 1L, None, true, false, true)
                        .hashCode(),
                is(new DefaultHttpSetCookie("foo", "bar", "/", "ServiceTalk.io", null, 2L, Lax, false, true, false)
                        .hashCode()));
    }

    @Test
    public void testNotEqual() {
        // Name is case-sensitive:
        assertThat(new DefaultHttpSetCookie("foo", "bar"),
                is(not(new DefaultHttpSetCookie("Foo", "bar"))));
        assertThat(new DefaultHttpSetCookie("foo", "bar").hashCode(),
                is(not(new DefaultHttpSetCookie("fooo", "bar").hashCode())));

        // Value is case-sensitive:
        assertThat(new DefaultHttpSetCookie("foo", "bar"),
                is(not(new DefaultHttpSetCookie("foo", "Bar"))));
        assertThat(new DefaultHttpSetCookie("foo", "bar").hashCode(),
                is(not(new DefaultHttpSetCookie("foo", "barr").hashCode())));

        // Path is case-sensitive:
        assertThat(new DefaultHttpSetCookie("foo", "bar", "/path", "servicetalk.io",
                        null, 1L, None, true, false, true),
                is(not(new DefaultHttpSetCookie("foo", "bar", "/Path", "servicetalk.io",
                        null, 1L, None, true, false, true))));
        assertThat(new DefaultHttpSetCookie("foo", "bar", "/path", "servicetalk.io",
                        null, 1L, None, true, false, true).hashCode(),
                is(not(new DefaultHttpSetCookie("foo", "bar", "/pathh", "servicetalk.io",
                        null, 1L, None, true, false, true).hashCode())));

        // Domain doesn't match:
        assertThat(new DefaultHttpSetCookie("foo", "bar", "/path", "servicetalk.io",
                        null, 1L, None, true, false, true),
                is(not(new DefaultHttpSetCookie("foo", "bar", "/path", "docs.servicetalk.io",
                        null, 1L, None, true, false, true))));
        assertThat(new DefaultHttpSetCookie("foo", "bar", "/path", "servicetalk.io",
                        null, 1L, None, true, false, true).hashCode(),
                is(not(new DefaultHttpSetCookie("foo", "bar", "/path", "docs.servicetalk.io",
                        null, 1L, None, true, false, true).hashCode())));
    }
}
