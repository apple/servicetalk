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

import java.time.Duration;

import static io.servicetalk.utils.internal.DurationUtils.ensurePositive;
import static io.servicetalk.utils.internal.DurationUtils.isInfinite;
import static io.servicetalk.utils.internal.DurationUtils.isPositive;
import static java.time.Duration.ofNanos;
import static java.time.Duration.ofSeconds;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DurationUtilsTest {

    private static final Duration MAX_DURATION = ofSeconds(2);

    @Test
    void testIsPositiveSeconds() {
        assertThat(isPositive(ofSeconds(1L)), is(true));
        assertThat(isPositive(ofSeconds(0L)), is(false));
        assertThat(isPositive(ofSeconds(1L).negated()), is(false));
    }

    @Test
    void testIsPositiveNanos() {
        assertThat(isPositive(ofNanos(1L)), is(true));
        assertThat(isPositive(ofNanos(0L)), is(false));
        assertThat(isPositive(ofNanos(1L).negated()), is(false));
    }

    @Test
    void testEnsurePositive() {
        assertThrows(NullPointerException.class, () -> ensurePositive(null, "duration"));
        assertThrows(IllegalArgumentException.class, () -> ensurePositive(Duration.ZERO, "duration"));
        assertThrows(IllegalArgumentException.class, () -> ensurePositive(ofNanos(1L).negated(), "duration"));
        assertThrows(IllegalArgumentException.class, () -> ensurePositive(ofSeconds(1L).negated(), "duration"));
    }

    @Test
    void testIsInfinite() {
        assertThat(isInfinite(null, MAX_DURATION), is(true));
        assertThat(isInfinite(ofSeconds(3), MAX_DURATION), is(true));
        assertThat(isInfinite(ofSeconds(1), MAX_DURATION), is(false));
        assertThat(isInfinite(MAX_DURATION, MAX_DURATION), is(false));
    }
}
