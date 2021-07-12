/*
 * Copyright © 2020 Apple Inc. and the ServiceTalk project authors
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

class IllegalCharacterExceptionTest {

    @Test
    @SuppressWarnings("ThrowableNotThrown")
    void messageCanBeGeneratedForAnyByteValue() {
        for (int value = Byte.MIN_VALUE; value <= Byte.MAX_VALUE; ++value) {
            new IllegalCharacterException((byte) value);
            new IllegalCharacterException((byte) value, "something");
        }
    }
}
