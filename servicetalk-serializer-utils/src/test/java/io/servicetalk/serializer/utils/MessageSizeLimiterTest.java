/*
 * Copyright © 2026 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.serializer.utils;

import io.servicetalk.serializer.api.MaxMessageSizeExceededException;

import org.junit.jupiter.api.Test;

import static io.servicetalk.serializer.utils.MessageSizeLimiter.WARN_ONLY;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MessageSizeLimiterTest {
    @Test
    void disabledNeverRejects() {
        assertDoesNotThrow(() -> MessageSizeLimiter.forMaxMessageSize(0).checkMessageSize(Integer.MAX_VALUE));
    }

    @Test
    void enforcingRejectsAboveLimit() {
        assertThrows(MaxMessageSizeExceededException.class,
                () -> MessageSizeLimiter.forMaxMessageSize(8).checkMessageSize(9));
    }

    @Test
    void enforcingAllowsAtLimit() {
        assertDoesNotThrow(() -> MessageSizeLimiter.forMaxMessageSize(8).checkMessageSize(8));
    }

    @Test
    void warnOnlyAllowsAboveLimit() {
        assertDoesNotThrow(() -> MessageSizeLimiter.forMaxMessageSize(WARN_ONLY).checkMessageSize(Integer.MAX_VALUE));
    }

    @Test
    void invalidMaxMessageSizeRejected() {
        assertThrows(IllegalArgumentException.class, () -> MessageSizeLimiter.forMaxMessageSize(-2));
    }
}
