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
package io.servicetalk.concurrent.internal;

import org.junit.jupiter.api.Test;

import static io.servicetalk.concurrent.internal.ThrowableUtils.matches;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

class ThrowableUtilTest {

    @Test
    void testMatchesOriginalMatch() {
        NullPointerException npe = new NullPointerException();
        assertThat("Match not found.", matches(npe, NullPointerException.class), is(true));
    }

    @Test
    void testMatchesCauseMatch() {
        IllegalArgumentException npe = new IllegalArgumentException(new NullPointerException());
        assertThat("Match not found.", matches(npe, NullPointerException.class), is(true));
    }

    @Test
    void testNoMatchOriginalNoCause() {
        NullPointerException npe = new NullPointerException();
        assertThat("Match not found.", matches(npe, IllegalArgumentException.class), is(false));
    }

    @Test
    void testNoMatchOriginalAndCause() {
        IllegalStateException npe = new IllegalStateException(new NullPointerException());
        assertThat("Match not found.", matches(npe, IllegalArgumentException.class), is(false));
    }
}
