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
package io.servicetalk.encoding.api;

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.serializer.api.Deserializer;
import io.servicetalk.serializer.api.StreamingDeserializer;

/**
 * Used to decode buffers for aggregated and streaming use cases.
 */
public interface BufferDecoder {
    /**
     * Get the {@link Deserializer} to use for aggregated content.
     * @return the {@link Deserializer} to use for aggregated content.
     */
    Deserializer<Buffer> decoder();

    /**
     * Get the {@link StreamingDeserializer} to use for streaming content.
     * @return the {@link StreamingDeserializer} to use for streaming content.
     */
    StreamingDeserializer<Buffer> streamingDecoder();

    /**
     * Get the name of the encoding.
     * @return the name of the encoding.
     */
    CharSequence encodingName();

    /**
     * Get a {@link BufferDecoder} equivalent to this one but whose decompression is bounded by the more restrictive of
     * {@code maxDecompressedBytes} and any limit this decoder already applies, aborting (rather than fully buffering) a
     * decode that would inflate beyond that bound.
     * <p>
     * This is <strong>best-effort</strong>: an implementation that cannot bound decompression (including this default)
     * returns {@code this}, in which case decompression remains bounded only by the decoder's own configuration. A
     * delegating {@link BufferDecoder} should override this to forward to its delegate so the bound is not dropped.
     *
     * @param maxDecompressedBytes the maximum decompressed bytes to produce from a single decode; should be positive
     * (an implementation may ignore a non-positive value).
     * @return a {@link BufferDecoder} bounded to at most {@code maxDecompressedBytes}, or {@code this} if this decoder
     * cannot apply the bound.
     */
    // FIXME: 0.43 - remove default, force implementations to implement.
    default BufferDecoder withMaxDecompressedBytes(int maxDecompressedBytes) {
        return this;
    }
}
