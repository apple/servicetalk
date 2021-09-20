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

import static io.servicetalk.concurrent.internal.FlowControlUtils.addWithOverflowProtectionIfPositive;
import static io.servicetalk.concurrent.internal.FlowControlUtils.addWithUnderOverflowProtection;
import static org.junit.jupiter.api.Assertions.assertEquals;

class FlowControlUtilsTests {
    @Test
    void addWithOverflowIfPositiveRespectsZero() {
        assertEquals(0, addWithOverflowProtectionIfPositive(0, -1));
    }

    @Test
    void addWithUnderOverflowProtectionPositiveNoOverflow() {
        assertEquals(3, addWithUnderOverflowProtection(1, 2));
    }

    @Test
    void addWithUnderOverflowProtectionNegativeNoOverflow() {
        assertEquals(-3, addWithUnderOverflowProtection(-1, -2));
    }

    @Test
    void addWithUnderOverflowProtectionPositivePlusNegative() {
        assertEquals(1, addWithUnderOverflowProtection(-1, 2));
    }

    @Test
    void addWithUnderOverflowProtectionZeroToMin() {
        assertEquals(Long.MIN_VALUE, addWithUnderOverflowProtection(0, Long.MIN_VALUE));
    }

    @Test
    void addWithUnderOverflowProtectionNegativeOneToMin() {
        assertEquals(Long.MIN_VALUE, addWithUnderOverflowProtection(-1, Long.MIN_VALUE));
    }

    @Test
    void addWithUnderOverflowProtectionMinToMin() {
        assertEquals(Long.MIN_VALUE, addWithUnderOverflowProtection(Long.MIN_VALUE, Long.MIN_VALUE));
    }

    @Test
    void addWithUnderOverflowProtectionZeroToMax() {
        assertEquals(Long.MAX_VALUE, addWithUnderOverflowProtection(0, Long.MAX_VALUE));
    }

    @Test
    void addWithUnderOverflowProtectionOneToMax() {
        assertEquals(Long.MAX_VALUE, addWithUnderOverflowProtection(1, Long.MAX_VALUE));
    }

    @Test
    void addWithUnderOverflowProtectionMaxToMax() {
        assertEquals(Long.MAX_VALUE, addWithUnderOverflowProtection(Long.MAX_VALUE, Long.MAX_VALUE));
    }
}
