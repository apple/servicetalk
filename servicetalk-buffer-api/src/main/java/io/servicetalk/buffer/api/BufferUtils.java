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
package io.servicetalk.buffer.api;

import java.util.Arrays;

import static io.servicetalk.buffer.api.EmptyBuffer.EMPTY_BUFFER;

final class BufferUtils {
    private BufferUtils() {
        // No instances.
    }

    static byte[] toByteArray(Buffer buffer, int index, int length, boolean forceCopy) {
        final int capacity = buffer.capacity();
        if (AbstractBuffer.isOutOfBounds(index, length, capacity)) {
            throw new IndexOutOfBoundsException("index: " + index + ", length: " + length +
                    " (expected: 0 <= index <= index + length <= capacity(" + capacity + "))");
        }
        if (length == 0) {
            return EMPTY_BUFFER.array();
        }
        if (buffer.hasArray()) {
            final byte[] array = buffer.array();
            final int start = buffer.arrayOffset() + index;
            if (!forceCopy && start == 0 && array.length == length) {
                return array;
            }
            return Arrays.copyOfRange(array, start, start + length);
        }
        final byte[] bytes = new byte[length];
        buffer.getBytes(index, bytes);
        return bytes;
    }
}
