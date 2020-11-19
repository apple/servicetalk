/*
 * Copyright © 2018 Apple Inc. and the ServiceTalk project authors
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

import java.nio.ByteBuffer;
import java.nio.charset.Charset;

/**
 * An API to allocate {@link Buffer}s.
 */
public interface BufferAllocator {

    /**
     * Create a new buffer.
     *
     * @return a new buffer.
     */
    default Buffer newBuffer() {
        return newBuffer(128);
    }

    /**
     * Create a new buffer.
     *
     * @param direct {@code true} if the buffer will be direct (off-heap), {@code false} otherwise.
     * @return a new buffer.
     */
    default Buffer newBuffer(boolean direct) {
        return newBuffer(128, direct);
    }

    /**
     * Create a new buffer with the given initial capacity.
     *
     * @param initialCapacity the initial capacity of the buffer.
     * @return a new buffer.
     */
    Buffer newBuffer(int initialCapacity);

    /**
     * Create a new buffer with the given initial capacity and given max capacity.
     *
     * @param initialCapacity the initial capacity of the buffer.
     * @param maxCapacity the maximum capacity of the buffer.
     * @return a new buffer.
     */
    Buffer newBuffer(int initialCapacity, int maxCapacity);

    /**
     * Create a new buffer with the given initial capacity.
     *
     * @param initialCapacity the initial capacity of the allocated buffer.
     * @param direct {@code true} if the buffer will be direct (off-heap), {@code false} otherwise.
     * @return a new buffer.
     */
    Buffer newBuffer(int initialCapacity, boolean direct);

    /**
     * Create a new composite buffer.
     *
     * @return the new buffer.
     */
    CompositeBuffer newCompositeBuffer();

    /**
     * Create a new composite buffer.
     * @param maxComponents The maximum number of top level {@link Buffer} objects that can be contained.
     * @return the new buffer.
     */
    CompositeBuffer newCompositeBuffer(int maxComponents);

    /**
     * Create a new {@link Buffer} from the given {@link CharSequence} using the {@link Charset}.
     *
     * @param data the sequence.
     * @param charset the charset to use.
     * @return a new buffer.
     */
    Buffer fromSequence(CharSequence data, Charset charset);

    /**
     * Create a new {@link Buffer} from the given {@link CharSequence} using the {@link Charset}.
     *
     * @param data the sequence.
     * @param charset the charset to use.
     * @param direct {@code true} if the buffer will be direct (off-heap), {@code false} otherwise.
     * @return a new buffer.
     */
    Buffer fromSequence(CharSequence data, Charset charset, boolean direct);

    /**
     * Create a new {@link Buffer} from the given {@link CharSequence} using UTF-8 encoding.
     *
     * @param data the sequence.
     * @return a new buffer.
     */
    Buffer fromUtf8(CharSequence data);

    /**
     * Create a new {@link Buffer} from the given {@link CharSequence} using UTF-8 encoding.
     *
     * @param data the sequence.
     * @param direct {@code true} if the buffer will be direct (off-heap), {@code false} otherwise.
     * @return a new buffer.
     */
    Buffer fromUtf8(CharSequence data, boolean direct);

    /**
     * Create a new {@link Buffer} from the given {@link CharSequence} using Ascii encoding.
     *
     * @param data the sequence.
     * @return a new buffer.
     */
    Buffer fromAscii(CharSequence data);

    /**
     * Create a new {@link Buffer} from the given {@link CharSequence} using Ascii encoding.
     *
     * @param data the sequence.
     * @param direct {@code true} if the buffer will be direct (off-heap), {@code false} otherwise.
     * @return a new buffer.
     */
    Buffer fromAscii(CharSequence data, boolean direct);

    /**
     * Create a new {@link Buffer} that wraps the given byte array.
     *
     * @param bytes the byte array.
     * @return a new buffer.
     */
    Buffer wrap(byte[] bytes);

    /**
     * Create a new {@link Buffer} that wraps the given byte array.
     *
     * @param bytes the byte array.
     * @param offset the offset index of the array.
     * @param len the numbers of bytes.
     * @return a new buffer.
     */
    default Buffer wrap(byte[] bytes, int offset, int len) {
        return wrap(bytes).slice(offset, len);
    }

    /**
     * Create a new {@link Buffer} that wraps the given {@link ByteBuffer}.
     *
     * @param buffer to wrap.
     * @return a new buffer.
     */
    Buffer wrap(ByteBuffer buffer);
}
