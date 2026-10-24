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
package io.servicetalk.utils.internal;

import io.servicetalk.buffer.api.Buffer;

/**
 * Utilities for {@link Buffer}s.
 */
public final class BufferUtils {
    private static final byte[] EMPTY_ARRAY = new byte[0];

    private BufferUtils() {
        // no instances
    }

    /**
     * Convert the readable bytes of a {@link Buffer} to an array, reusing its backing array when possible.
     * This is equivalent to {@link #toByteArray(Buffer, boolean) toByteArray(buffer, false)}.
     *
     * @param buffer the buffer to convert
     * @return an array containing exactly the readable bytes
     */
    public static byte[] toByteArray(final Buffer buffer) {
        return toByteArray(buffer, false);
    }

    /**
     * Convert the readable bytes of a {@link Buffer} to an array.
     * <p>
     * If {@code forceCopy} is {@code false}, an empty buffer returns a shared empty array, and a backing array
     * that exactly matches the readable bytes is returned directly. Neither case advances the reader index.
     * Otherwise, the readable bytes are copied to a new array and the reader index advances to the writer index.
     * A reused backing array shares subsequent modifications with the buffer.
     *
     * @param buffer the buffer to convert
     * @param forceCopy whether to always copy into a new array
     * @return an array containing exactly the readable bytes
     */
    public static byte[] toByteArray(final Buffer buffer, final boolean forceCopy) {
        final int readableBytes = buffer.readableBytes();
        if (!forceCopy) {
            if (readableBytes == 0) {
                return EMPTY_ARRAY;
            }
            if (buffer.hasArray() && buffer.arrayOffset() == 0) {
                final byte[] array = buffer.array();
                if (array.length == readableBytes) {
                    return array;
                }
            }
        }
        final byte[] bytes = new byte[readableBytes];
        buffer.readBytes(bytes);
        return bytes;
    }
}
