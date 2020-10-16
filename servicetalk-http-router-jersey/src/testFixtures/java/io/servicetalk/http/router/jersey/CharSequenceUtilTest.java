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
        assertThat(CharSequenceUtils.asCharSequence(s), is(sameInstance(s)));
        CharSequence cs = newAsciiString("bar");
        assertThat(CharSequenceUtils.asCharSequence(cs), is(sameInstance(cs)));
        assertThat(CharSequenceUtils.asCharSequence(123), is("123"));
    }
}
