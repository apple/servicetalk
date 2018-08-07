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
package io.servicetalk.redis.internal;

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.buffer.api.BufferAllocator;
import io.servicetalk.buffer.api.CompositeBuffer;

import java.util.List;
import javax.annotation.Nullable;

import static java.nio.ByteOrder.BIG_ENDIAN;
import static java.nio.ByteOrder.nativeOrder;
import static java.nio.charset.StandardCharsets.US_ASCII;

/**
 * A set of utility functions for redis.
 */
public final class RedisUtils {

    private static final boolean BIG_ENDIAN_NATIVE_ORDER = nativeOrder() == BIG_ENDIAN;
    public static final short EOL_SHORT = makeShort('\r', '\n');
    public static final int EOL_LENGTH = 2;
    public static final int TWICE_EOL_LENGTH = EOL_LENGTH << 1;

    private RedisUtils() {
        // no instances
    }

    /**
     * Creates a new buffer for array size.
     *
     * @param size of the array.
     * @param allocator {@link BufferAllocator} to allocate the returned {@link Buffer}.
     * @return {@link Buffer} containing the array size.
     */
    public static Buffer toRespArraySize(final long size, final BufferAllocator allocator) {
        final byte[] sizeBytes = toAsciiBytes(size);
        return allocator.newBuffer(1 + sizeBytes.length + EOL_LENGTH)
                .writeByte('*')
                .writeBytes(sizeBytes)
                .writeShort(EOL_SHORT);
    }

    /**
     * Writes the passed {@link Buffer} as a bulk string.
     *
     * @param buf to write as bulk string.
     * @param allocator {@link BufferAllocator} to allocate the returned {@link Buffer}.
     * @return {@link Buffer} containing the array size.
     */
    public static Buffer toRespBulkString(final Buffer buf, final BufferAllocator allocator) {
        final byte[] size = toAsciiBytes(buf.getReadableBytes());
        return allocator.newBuffer(1 + size.length + TWICE_EOL_LENGTH)
                .writeByte('$')
                .writeBytes(size)
                .writeShort(EOL_SHORT)
                .writeBytes(buf)
                .writeShort(EOL_SHORT);
    }

    /**
     * Writes the passed bytes as a bulk string.
     *
     * @param bytes to write as bulk string.
     * @param allocator {@link BufferAllocator} to allocate the returned {@link Buffer}.
     * @return {@link Buffer} containing the array size.
     */
    public static Buffer toRespBulkString(final byte[] bytes, final BufferAllocator allocator) {
        final byte[] size = toAsciiBytes(bytes.length);
        return allocator.newBuffer(1 + size.length + TWICE_EOL_LENGTH)
                .writeByte('$')
                .writeBytes(size)
                .writeShort(EOL_SHORT)
                .writeBytes(bytes)
                .writeShort(EOL_SHORT);
    }

    /**
     * Writes the passed {@link CharSequence} as a bulk string.
     *
     * @param chars to write as bulk string.
     * @param allocator {@link BufferAllocator} to allocate the returned {@link Buffer}.
     * @return {@link Buffer} containing the array size.
     */
    public static Buffer toRespBulkString(final CharSequence chars, final BufferAllocator allocator) {
        final Buffer buf = allocator.fromUtf8(chars);
        return toRespBulkString(buf, allocator);
    }

    /**
     * Writes the passed {@link Number} as a bulk string.
     *
     * @param number to write as bulk string.
     * @param allocator {@link BufferAllocator} to allocate the returned {@link Buffer}.
     * @return {@link Buffer} containing the array size.
     */
    public static Buffer toRespBulkString(final Number number, final BufferAllocator allocator) {
        final byte[] bytes = number.toString().getBytes(US_ASCII);
        return toRespBulkString(bytes, allocator);
    }

    /**
     * New {@link CompositeBuffer} for the passed {@link Buffer} for the command.
     *
     * @param argCount Number of arguments to the command.
     * @param commandBuffer Redis command as a buffer.
     * @param allocator {@link BufferAllocator} to allocate the returned {@link CompositeBuffer}.
     * @return {@link CompositeBuffer} for the request.
     */
    public static CompositeBuffer newRequestCompositeBuffer(final int argCount, final Buffer commandBuffer, final BufferAllocator allocator) {
        return newRequestCompositeBuffer(argCount, commandBuffer, null, allocator);
    }

    /**
     * New {@link CompositeBuffer} for the passed {@link Buffer}s for the command and sub-command.
     *
     * @param argCount Number of arguments to the command.
     * @param commandBuffer Redis command as a buffer.
     * @param subCommandBuffer Redis sub-command as a buffer.
     * @param allocator {@link BufferAllocator} to allocate the returned {@link CompositeBuffer}.
     * @return {@link CompositeBuffer} for the request.
     */
    public static CompositeBuffer newRequestCompositeBuffer(final int argCount, final Buffer commandBuffer, @Nullable final Buffer subCommandBuffer, final BufferAllocator allocator) {
        final CompositeBuffer cb = allocator.newCompositeBuffer()
                .addBuffer(toRespArraySize(argCount, allocator))
                .addBuffer(commandBuffer);
        if (subCommandBuffer != null) {
            cb.addBuffer(subCommandBuffer);
        }
        return cb;
    }

    /**
     * Appends request argument to the passed {@link CompositeBuffer}.
     *
     * @param arg Number of arguments.
     * @param cb {@link CompositeBuffer} to write.
     * @param allocator {@link BufferAllocator} to allocate used {@link Buffer}s.
     */
    public static void addRequestArgument(final Number arg, final CompositeBuffer cb, final BufferAllocator allocator) {
        cb.addBuffer(toRespBulkString(arg, allocator));
    }

    /**
     * Appends request argument to the passed {@link CompositeBuffer}.
     *
     * @param arg Argument.
     * @param cb {@link CompositeBuffer} to write.
     * @param allocator {@link BufferAllocator} to allocate used {@link Buffer}s.
     */
    public static void addRequestArgument(final CharSequence arg, final CompositeBuffer cb, final BufferAllocator allocator) {
        cb.addBuffer(toRespBulkString(arg, allocator));
    }

    /**
     * Converts the passed {@link Number} to ascii bytes.
     * @param n to convert.
     * @return Bytes.
     */
    public static byte[] toAsciiBytes(final Number n) {
        return n.toString().getBytes(US_ASCII);
    }

    /**
     * Returns a {@code short} value using endian order.
     *
     * @param first the first {@code char} to include in the {@code short}.
     * @param second the second {@code char} to include in the {@code short}.
     * @return a {@code short}.
     */
    public static short makeShort(char first, char second) {
        return BIG_ENDIAN_NATIVE_ORDER ?
                (short) ((second << 8) | first) : (short) ((first << 8) | second);
    }

    /**
     * Marker class which indicates that Redis response should be coerced to the {@link List} of {@link CharSequence}s.
     */
    public static final class ListWithBuffersCoercedToCharSequences {
        private ListWithBuffersCoercedToCharSequences() {
            // no instantiation
        }
    }
}
