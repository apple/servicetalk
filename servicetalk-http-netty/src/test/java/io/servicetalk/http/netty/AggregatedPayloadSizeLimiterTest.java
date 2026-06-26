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
import static io.servicetalk.http.netty.HttpConfig.DEFAULT_MAX_AGGREGATED_PAYLOAD_SIZE_VALUE;
import static io.servicetalk.http.netty.HttpConfig.toAggregatedPayloadSizeLimiter;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotSame;
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

    @Test
    void mapEnforcesPositiveSize() {
        final LongConsumer limiter = toAggregatedPayloadSizeLimiter(10, DEFAULT_MAX_AGGREGATED_PAYLOAD_SIZE_VALUE);
        assertNotSame(NONE, limiter);
        assertDoesNotThrow(() -> limiter.accept(10));
        assertThrows(PayloadTooLargeException.class, () -> limiter.accept(11));
    }

    @Test
    void mapZeroIsDisabled() {
        assertSame(NONE, toAggregatedPayloadSizeLimiter(0, DEFAULT_MAX_AGGREGATED_PAYLOAD_SIZE_VALUE));
    }

    @Test
    void mapWarnOnlyWarnsAtDefault() {
        final LongConsumer limiter = toAggregatedPayloadSizeLimiter(-1, DEFAULT_MAX_AGGREGATED_PAYLOAD_SIZE_VALUE);
        assertNotSame(NONE, limiter);
        // Over the default it warns (rate-limited) but must not throw.
        assertDoesNotThrow(() -> limiter.accept((long) DEFAULT_MAX_AGGREGATED_PAYLOAD_SIZE_VALUE + 1));
    }

    @Test
    void mapWarnOnlyAtRaisedDefault() {
        final int raised = DEFAULT_MAX_AGGREGATED_PAYLOAD_SIZE_VALUE * 2;
        final LongConsumer limiter = toAggregatedPayloadSizeLimiter(-1, raised);
        assertNotSame(NONE, limiter);
        assertDoesNotThrow(() -> limiter.accept((long) raised + 1));
    }

    @Test
    void mapWarnOnlyNeverCollapsesToDisabledWhenDefaultNonPositive() {
        assertNotSame(NONE, toAggregatedPayloadSizeLimiter(-1, -1));
        assertNotSame(NONE, toAggregatedPayloadSizeLimiter(-1, 0));
        assertDoesNotThrow(() -> toAggregatedPayloadSizeLimiter(-1, -1).accept(Long.MAX_VALUE));
    }

    @Test
    void configuredValueTakesPrecedenceOverPropertyDefault() {
        // The configured (builder) value drives the mode; the property-resolved default only supplies the warn
        // threshold. So an explicit builder call always wins over the property's mode.
        // builder enforce beats property warn-only:
        assertThrows(PayloadTooLargeException.class, () -> toAggregatedPayloadSizeLimiter(10, -1).accept(11));
        // builder disable beats property warn-only:
        assertSame(NONE, toAggregatedPayloadSizeLimiter(0, -1));
        // builder warn-only beats property enforce:
        final LongConsumer warn = toAggregatedPayloadSizeLimiter(-1, 10);
        assertNotSame(NONE, warn);
        assertDoesNotThrow(() -> warn.accept(Long.MAX_VALUE));
    }
}
