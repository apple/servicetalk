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
package io.servicetalk.http.netty;

import io.servicetalk.http.api.PayloadTooLargeException;

import org.junit.jupiter.api.Test;

import java.util.function.LongConsumer;

import static io.servicetalk.http.netty.AggregatedPayloadSizeLimiter.NONE;
import static io.servicetalk.http.netty.AggregatedPayloadSizeLimiter.enforcing;
import static io.servicetalk.http.netty.AggregatedPayloadSizeLimiter.warning;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AggregatedPayloadSizeLimiterTest {

    @Test
    void enforcingAllowsUpToLimit() {
        assertDoesNotThrow(() -> enforcing(10).accept(10));
    }

    @Test
    void enforcingRejectsOverLimit() {
        assertThrows(PayloadTooLargeException.class, () -> enforcing(10).accept(11));
    }

    @Test
    void warningNeverRejects() {
        final LongConsumer limiter = warning(10);
        assertDoesNotThrow(() -> limiter.accept(10));
        // Over the limit it warns (rate-limited) but must not throw.
        assertDoesNotThrow(() -> limiter.accept(Long.MAX_VALUE));
    }

    @Test
    void nonPositiveSizeIsDisabled() {
        assertSame(NONE, enforcing(0));
        assertSame(NONE, enforcing(-1));
        assertSame(NONE, warning(0));
        assertDoesNotThrow(() -> NONE.accept(Long.MAX_VALUE));
    }
}
