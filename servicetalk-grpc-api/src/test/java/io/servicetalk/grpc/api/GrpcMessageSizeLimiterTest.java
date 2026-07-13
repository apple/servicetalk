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

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.encoding.api.BufferDecoder;
import io.servicetalk.serializer.api.Deserializer;
import io.servicetalk.serializer.api.MaxMessageSizeExceededException;
import io.servicetalk.serializer.api.StreamingDeserializer;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static io.servicetalk.grpc.api.GrpcMessageSizeLimiter.NONE;
import static io.servicetalk.grpc.api.GrpcMessageSizeLimiter.forMaxInboundMessageSize;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
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

    @Test
    void enforcingCapsDecoderAtLimit() {
        final AtomicInteger requested = new AtomicInteger(-1);
        final BufferDecoder recapped = new RecordingDecoder(requested);
        final BufferDecoder decoder = new RecordingDecoder(requested) {
            @Override
            public BufferDecoder withMaxDecompressedBytes(final int maxDecompressedBytes) {
                requested.set(maxDecompressedBytes);
                return recapped;
            }
        };
        assertThat(forMaxInboundMessageSize(10).capDecoder(decoder), sameInstance(recapped));
        assertThat(requested.get(), equalTo(10));
    }

    @Test
    void disabledAndWarnOnlyDoNotCapDecoder() {
        final AtomicInteger requested = new AtomicInteger(-1);
        final BufferDecoder decoder = new RecordingDecoder(requested);
        assertThat(NONE.capDecoder(decoder), sameInstance(decoder));
        assertThat(forMaxInboundMessageSize(-1).capDecoder(decoder), sameInstance(decoder));
        assertThat(requested.get(), equalTo(-1));
    }

    private static class RecordingDecoder implements BufferDecoder {
        private final AtomicInteger requested;

        RecordingDecoder(final AtomicInteger requested) {
            this.requested = requested;
        }

        @Override
        public Deserializer<Buffer> decoder() {
            throw new UnsupportedOperationException();
        }

        @Override
        public StreamingDeserializer<Buffer> streamingDecoder() {
            throw new UnsupportedOperationException();
        }

        @Override
        public CharSequence encodingName() {
            return "recording";
        }

        @Override
        public BufferDecoder withMaxDecompressedBytes(final int maxDecompressedBytes) {
            requested.set(maxDecompressedBytes);
            return this;
        }
    }
}
