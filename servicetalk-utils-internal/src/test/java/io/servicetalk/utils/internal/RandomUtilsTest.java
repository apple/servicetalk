/*
 * Copyright © 2024 Apple Inc. and the ServiceTalk project authors
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

import static java.util.concurrent.ThreadLocalRandom.current;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RandomUtilsTest {

    @Test
    void illegalArguments() {
        repeated(() -> {
            long lowerBound = current().nextLong(Long.MIN_VALUE + 1, Long.MAX_VALUE);
            assertThrows(IllegalArgumentException.class, () ->
                    RandomUtils.nextLongInclusive(lowerBound, lowerBound - 1));
        });
    }

    @Test
    void lowerEqualsUpperBound() {
        repeated(() -> {
            final long bound = current().nextLong();
            assertThat(RandomUtils.nextLongInclusive(bound, bound), equalTo(bound));
        });
    }

    @Test
    void longMaxValue() {
        repeated(() ->
            assertThat(RandomUtils.nextLongInclusive(Long.MAX_VALUE - 1, Long.MAX_VALUE),
                    anyOf(equalTo(Long.MAX_VALUE - 1), equalTo(Long.MAX_VALUE))));
    }

    @Test
    void longMinValue() {
        repeated(() ->
            assertThat(RandomUtils.nextLongInclusive(Long.MIN_VALUE, Long.MIN_VALUE + 1),
                    anyOf(equalTo(Long.MIN_VALUE), equalTo(Long.MIN_VALUE + 1))));
    }

    private static void repeated(Runnable r) {
        for (int i = 0; i < 100; i++) {
            r.run();
        }
    }
}
