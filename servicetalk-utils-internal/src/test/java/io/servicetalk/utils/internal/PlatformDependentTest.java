/*
 * Copyright © 2022 Apple Inc. and the ServiceTalk project authors
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

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import static java.lang.Boolean.getBoolean;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class PlatformDependentTest {

    @Test
    void hasUnsafe() {
        assumeFalse(getBoolean("io.servicetalk.noUnsafe"));
        assertDoesNotThrow(PlatformDependent::hasUnsafe);
    }

    @Test
    void memoryAllocation() {
        assumeTrue(PlatformDependent.hasUnsafe(), "Unsafe absent or disabled");
        long allocated = PlatformDependent.allocateMemory(1);
        assertThat(allocated, is(Matchers.not(0)));
        PlatformDependent.freeMemory(allocated);
    }

    @Test
    void throwException() {
        assumeTrue(PlatformDependent.hasUnsafe(), "Unsafe absent or disabled");
        Exception customException = new Exception() {
        };
        assertThrows(customException.getClass(), () -> PlatformDependent.throwException(customException));
    }
}
