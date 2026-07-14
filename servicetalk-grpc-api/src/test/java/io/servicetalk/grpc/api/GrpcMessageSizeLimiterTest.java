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

import io.servicetalk.serializer.api.MaxMessageSizeExceededException;

import org.junit.jupiter.api.Test;

import static io.servicetalk.grpc.api.GrpcMessageSizeLimiter.NONE;
import static io.servicetalk.grpc.api.GrpcMessageSizeLimiter.forMaxInboundMessageSize;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GrpcMessageSizeLimiterTest {

    @Test
    void disabledNeverRejects() {
        assertThat(forMaxInboundMessageSize(0), sameInstance(NONE));
        assertDoesNotThrow(() -> NONE.accept(Long.MAX_VALUE));
    }

    @Test
    void enforcingAllowsAtOrUnderLimit() {
        final GrpcMessageSizeLimiter limiter = forMaxInboundMessageSize(10);
        assertDoesNotThrow(() -> limiter.accept(0));
        assertDoesNotThrow(() -> limiter.accept(10));
    }

    @Test
    void enforcingRejectsOverLimit() {
        final GrpcMessageSizeLimiter limiter = forMaxInboundMessageSize(10);
        assertThrows(MaxMessageSizeExceededException.class, () -> limiter.accept(11));
    }

    @Test
    void warnOnlyDeliversOverLimit() {
        final GrpcMessageSizeLimiter limiter = forMaxInboundMessageSize(-1);
        assertDoesNotThrow(() -> limiter.accept(Long.MAX_VALUE));
    }

    @Test
    void belowWarnOnlyIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> forMaxInboundMessageSize(-2));
        assertThrows(IllegalArgumentException.class, () -> forMaxInboundMessageSize(Integer.MIN_VALUE));
    }
}
