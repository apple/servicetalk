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
import io.servicetalk.encoding.api.BufferEncoder;
import io.servicetalk.serializer.api.SerializationException;
import io.servicetalk.serializer.api.Serializer;
import io.servicetalk.serializer.api.StreamingSerializer;

import io.opentelemetry.sdk.common.export.Compressor;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import static io.servicetalk.buffer.api.CharSequences.newAsciiString;

/**
 * Adapts an OpenTelemetry {@link Compressor} to a ServiceTalk {@link BufferEncoder} for use as the
 * request-side gRPC compressor. The streaming path is unsupported because the OTLP exporter only
 * issues unary (aggregated) calls.
 *
 * <p>The wrapped {@link Compressor} must be safe to invoke concurrently from multiple threads —
 * a single instance is shared across all sends. OpenTelemetry's built-in {@code GzipCompressor}
 * satisfies this; user-supplied implementations should as well.
 */
final class OtelCompressorBufferEncoder implements BufferEncoder {

    private final Compressor delegate;
    private final CharSequence encodingName;
    private final Serializer<Buffer> serializer;

    OtelCompressorBufferEncoder(Compressor delegate) {
        this.delegate = delegate;
        this.encodingName = newAsciiString(delegate.getEncoding());
        this.serializer = new CompressingSerializer();
    }

    @Override
    public CharSequence encodingName() {
        return encodingName;
    }

    @Override
    public Serializer<Buffer> encoder() {
        return serializer;
    }

    @Override
    public StreamingSerializer<Buffer> streamingEncoder() {
        // Unary OTLP only — GrpcSerializer (aggregated) is the entry point, never the streaming form.
        throw new UnsupportedOperationException("streaming compression is not supported by the OTLP sender");
    }

    private final class CompressingSerializer implements Serializer<Buffer> {

        private static final int TRANSFER_CHUNK = 8 * 1024;

        @Override
        public void serialize(Buffer in, BufferAllocator allocator, Buffer out) {
            try (OutputStream cos = delegate.compress(Buffer.asOutputStream(out))) {
                if (in.hasArray()) {
                    cos.write(in.array(), in.arrayOffset() + in.readerIndex(), in.readableBytes());
                    in.skipBytes(in.readableBytes());
                } else {
                    // Bound the intermediate allocation regardless of payload size.
                    try (InputStream is = Buffer.asInputStream(in)) {
                        byte[] tmp = new byte[Math.min(TRANSFER_CHUNK, in.readableBytes())];
                        int n;
                        while ((n = is.read(tmp)) != -1) {
                            cos.write(tmp, 0, n);
                        }
                    }
                }
            } catch (IOException e) {
                throw new SerializationException("Failed to compress with " + encodingName, e);
            }
        }

        // 2-arg overload inherited from Serializer — unused on the unary gRPC path.
    }
}
