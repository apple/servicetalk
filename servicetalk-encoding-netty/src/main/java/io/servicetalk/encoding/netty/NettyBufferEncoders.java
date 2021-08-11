/*
 * Copyright © 2021 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.encoding.netty;

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.encoding.api.BufferEncoderDecoder;
import io.servicetalk.serializer.api.SerializerDeserializer;
import io.servicetalk.serializer.api.StreamingSerializerDeserializer;

import static io.servicetalk.buffer.api.CharSequences.newAsciiString;
import static io.servicetalk.encoding.netty.NettyCompression.deflateDefaultStreaming;
import static io.servicetalk.encoding.netty.NettyCompression.gzipDefaultStreaming;

/**
 * Factory methods for common {@link BufferEncoderDecoder}s.
 */
public final class NettyBufferEncoders {
    private static final CharSequence GZIP = newAsciiString("gzip");
    private static final CharSequence DEFLATE = newAsciiString("deflate");
    private static final BufferEncoderDecoder DEFAULT_GZIP = bufferEncoder(NettyCompression.gzipDefault(),
            gzipDefaultStreaming(), GZIP);
    private static final BufferEncoderDecoder DEFAULT_DEFLATE =
            bufferEncoder(NettyCompression.deflateDefault(), deflateDefaultStreaming(), DEFLATE);

    private NettyBufferEncoders() {
    }

    /**
     * Get a default {@link BufferEncoderDecoder} for gzip encoding.
     * @return a default {@link BufferEncoderDecoder} for gzip encoding.
     */
    public static BufferEncoderDecoder gzipDefault() {
        return DEFAULT_GZIP;
    }

    /**
     * Get a default {@link BufferEncoderDecoder} for deflate encoding.
     * @return a default {@link BufferEncoderDecoder} for deflate encoding.
     */
    public static BufferEncoderDecoder deflateDefault() {
        return DEFAULT_DEFLATE;
    }

    /**
     * Create a {@link BufferEncoderDecoder} given the underlying {@link SerializerDeserializer} and
     * {@link StreamingSerializerDeserializer} implementations.
     * @param compressor Used to provided serialization for aggregated content.
     * @param streamingCompressor Used to provide serialization for stresaming content.
     * @param encodingName The name of the encoding.
     * @return a {@link BufferEncoderDecoder} given the underlying {@link SerializerDeserializer} and
     * {@link StreamingSerializerDeserializer} implementations.
     */
    public static BufferEncoderDecoder bufferEncoder(SerializerDeserializer<Buffer> compressor,
                                                     StreamingSerializerDeserializer<Buffer> streamingCompressor,
                                                     CharSequence encodingName) {
        return new DefaultBufferEncoderDecoder(compressor, streamingCompressor, encodingName);
    }
}
