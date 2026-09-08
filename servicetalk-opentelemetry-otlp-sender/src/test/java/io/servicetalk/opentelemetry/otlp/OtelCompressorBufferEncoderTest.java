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
package io.servicetalk.opentelemetry.otlp;

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.buffer.api.BufferAllocator;
import io.servicetalk.buffer.netty.BufferAllocators;
import io.servicetalk.encoding.netty.NettyBufferEncoders;

import io.opentelemetry.sdk.common.export.Compressor;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPOutputStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class OtelCompressorBufferEncoderTest {

    private static final BufferAllocator ALLOCATOR = BufferAllocators.DEFAULT_ALLOCATOR;

    @Test
    void encodingNameMatchesDelegate() {
        OtelCompressorBufferEncoder encoder = new OtelCompressorBufferEncoder(GzipCompressor.INSTANCE);
        assertThat(encoder.encodingName().toString(), equalTo("gzip"));
    }

    @Test
    void compressedBytesRoundTripThroughServiceTalkGzipDecoder() {
        OtelCompressorBufferEncoder encoder = new OtelCompressorBufferEncoder(GzipCompressor.INSTANCE);

        byte[] payload = "the quick brown fox jumps over the lazy dog".getBytes(StandardCharsets.UTF_8);
        Buffer in = ALLOCATOR.wrap(payload);
        Buffer compressed = encoder.encoder().serialize(in, ALLOCATOR);

        Buffer roundTripped = NettyBufferEncoders.gzipDefault().decoder().deserialize(compressed, ALLOCATOR);
        byte[] result = new byte[roundTripped.readableBytes()];
        roundTripped.readBytes(result);

        assertThat(result, equalTo(payload));
    }

    @Test
    void compressFromNonArrayBackedBuffer() {
        OtelCompressorBufferEncoder encoder = new OtelCompressorBufferEncoder(GzipCompressor.INSTANCE);

        byte[] payload = new byte[1024];
        for (int i = 0; i < payload.length; i++) {
            payload[i] = (byte) i;
        }
        // Force a direct (non-heap, non-array) buffer.
        Buffer in = ALLOCATOR.newBuffer(payload.length, true);
        in.writeBytes(payload);

        Buffer compressed = encoder.encoder().serialize(in, ALLOCATOR);
        Buffer roundTripped = NettyBufferEncoders.gzipDefault().decoder().deserialize(compressed, ALLOCATOR);
        byte[] result = new byte[roundTripped.readableBytes()];
        roundTripped.readBytes(result);

        assertThat(result, equalTo(payload));
    }

    @Test
    void streamingEncoderIsUnsupported() {
        OtelCompressorBufferEncoder encoder = new OtelCompressorBufferEncoder(GzipCompressor.INSTANCE);
        assertThrows(UnsupportedOperationException.class, encoder::streamingEncoder);
    }

    private static final class GzipCompressor implements Compressor {
        static final GzipCompressor INSTANCE = new GzipCompressor();

        @Override
        public String getEncoding() {
            return "gzip";
        }

        @Override
        public OutputStream compress(OutputStream outputStream) throws IOException {
            return new GZIPOutputStream(outputStream);
        }
    }
}
