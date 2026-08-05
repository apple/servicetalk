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
package io.servicetalk.grpc.api;

import org.junit.jupiter.api.Test;

import static io.servicetalk.grpc.api.GrpcMessageSizeLimiter.NONE;
import static io.servicetalk.grpc.api.GrpcMessageSizeLimiter.Role.CLIENT;
import static io.servicetalk.grpc.api.GrpcMessageSizeLimiter.Role.SERVER;
import static io.servicetalk.grpc.api.GrpcMessageSizeLimiter.forMaxInboundMessageSize;
import static io.servicetalk.grpc.api.GrpcStatusCode.RESOURCE_EXHAUSTED;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.sameInstance;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GrpcMessageSizeLimiterTest {

    @Test
    void disabledNeverRejects() {
        assertThat(forMaxInboundMessageSize(0, SERVER), sameInstance(NONE));
        assertDoesNotThrow(() -> NONE.accept(Long.MAX_VALUE));
    }

    @Test
    void enforcingAllowsAtOrUnderLimit() {
        final GrpcMessageSizeLimiter limiter = forMaxInboundMessageSize(10, SERVER);
        assertDoesNotThrow(() -> limiter.accept(0));
        assertDoesNotThrow(() -> limiter.accept(10));
    }

    @Test
    void enforcingRejectsOverLimitWithResourceExhausted() {
        final GrpcMessageSizeLimiter limiter = forMaxInboundMessageSize(10, SERVER);
        final GrpcStatusException e = assertThrows(GrpcStatusException.class, () -> limiter.accept(11));
        assertThat(e.status().code(), equalTo(RESOURCE_EXHAUSTED));
        assertThat(e.status().description(), startsWith("gRPC message size=11"));
    }

    @Test
    void enforcingDecompressedRejectionHasDistinctDescription() {
        final GrpcStatusException e = assertThrows(GrpcStatusException.class,
                () -> forMaxInboundMessageSize(10, SERVER).accept(11, true));
        assertThat(e.status().code(), equalTo(RESOURCE_EXHAUSTED));
        assertThat(e.status().description(), startsWith("Decompressed gRPC message size=11"));
    }

    @Test
    void warnOnlyDeliversOverLimit() {
        // Negative selects warn-only mode at abs(value): over the threshold it warns (rate-limited) but must not throw.
        final GrpcMessageSizeLimiter limiter = forMaxInboundMessageSize(-10, SERVER);
        assertDoesNotThrow(() -> limiter.accept(11));
        assertDoesNotThrow(() -> limiter.accept(Long.MAX_VALUE));
    }

    @Test
    void clientWarnOnlyDeliversOverLimit() {
        // Exercises the CLIENT warn-message branch: warns (rate-limited) over the threshold but must not throw.
        final GrpcMessageSizeLimiter limiter = forMaxInboundMessageSize(-10, CLIENT);
        assertDoesNotThrow(() -> limiter.accept(11));
        assertDoesNotThrow(() -> limiter.accept(Long.MAX_VALUE));
    }

    @Test
    void integerMinValueWarnsRatherThanDisabled() {
        // -Integer.MIN_VALUE overflows back to a negative; the limiter must stay warn-only, not collapse to disabled.
        final GrpcMessageSizeLimiter limiter = forMaxInboundMessageSize(Integer.MIN_VALUE, SERVER);
        assertNotSame(NONE, limiter);
        assertDoesNotThrow(() -> limiter.accept(Long.MAX_VALUE));
    }
}
