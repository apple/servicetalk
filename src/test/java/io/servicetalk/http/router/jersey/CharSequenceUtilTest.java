/*
 * Copyright © 2018 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.http.router.jersey;

import org.junit.Test;

import static io.servicetalk.http.api.CharSequences.newAsciiString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.assertThat;

public class CharSequenceUtilTest {
    @Test
    public void asCharSequence() {
        String s = "foo";
        assertThat(CharSequenceUtil.asCharSequence(s), is(sameInstance(s)));
        CharSequence cs = newAsciiString("bar");
        assertThat(CharSequenceUtil.asCharSequence(cs), is(sameInstance(cs)));
        assertThat(CharSequenceUtil.asCharSequence(123), is("123"));
    }

    @Test
    public void ensureNoLeadingSlash() {
        assertThat(CharSequenceUtil.ensureNoLeadingSlash(""), is(""));
        assertThat(CharSequenceUtil.ensureNoLeadingSlash("/"), is(""));
        assertThat(CharSequenceUtil.ensureNoLeadingSlash("//"), is(""));
        assertThat(CharSequenceUtil.ensureNoLeadingSlash("foo"), is("foo"));
        assertThat(CharSequenceUtil.ensureNoLeadingSlash("/bar/"), is("bar/"));
        assertThat(CharSequenceUtil.ensureNoLeadingSlash("//baz//"), is("baz//"));
    }

    @Test
    public void ensureTrailingSlash() {
        assertThat(CharSequenceUtil.ensureTrailingSlash("").toString(), is("/"));
        assertThat(CharSequenceUtil.ensureTrailingSlash("/").toString(), is("/"));
        assertThat(CharSequenceUtil.ensureTrailingSlash("//").toString(), is("//"));
        assertThat(CharSequenceUtil.ensureTrailingSlash("foo").toString(), is("foo/"));
        assertThat(CharSequenceUtil.ensureTrailingSlash("/bar/").toString(), is("/bar/"));
        assertThat(CharSequenceUtil.ensureTrailingSlash("//baz//").toString(), is("//baz//"));
    }
}
