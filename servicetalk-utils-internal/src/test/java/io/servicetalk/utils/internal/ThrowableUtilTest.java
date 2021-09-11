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
package io.servicetalk.utils.internal;

import org.junit.jupiter.api.Test;

import static io.servicetalk.utils.internal.ThrowableUtils.combine;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.arrayContaining;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.hamcrest.core.Is.is;

class ThrowableUtilTest {

    @Test
    void combineNonThrowable() {
        assertThat(combine(null, null), is(nullValue()));
        assertThat(combine(new Object(), new Object()), is(nullValue()));
    }

    @Test
    void combineOneThrowable() {
        Throwable t = new RuntimeException("Deliberate exception");
        assertThat(combine(null, t), sameInstance(t));
        assertThat(combine(t, null), sameInstance(t));
    }

    @Test
    void combineTwoThrowable() {
        Throwable first = new IllegalArgumentException("Deliberate exception");
        Throwable second = new IllegalStateException("Deliberate exception");
        testCombineTwo(first, second);
        testCombineTwo(second, first);
    }

    private static void testCombineTwo(Throwable first, Throwable second) {
        Throwable firstSecond = combine(first, second);
        assertThat(firstSecond, is(notNullValue()));
        assertThat(firstSecond, sameInstance(first));
        assertThat(firstSecond.getSuppressed(), arrayContaining(second));
    }
}
