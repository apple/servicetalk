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
/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.servicetalk.buffer.api;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ReadOnlyBufferException;
import java.nio.charset.Charset;
import java.nio.charset.UnsupportedCharsetException;

/**
 * An arbitrary and sequential accessible sequence of zero or more bytes (octets). This interface provides an abstract
 * view for one or more primitive byte arrays ({@code byte[]}) and {@link ByteBuffer NIO buffers}.
 */
public interface Buffer {
    /**
     * Returns the number of bytes (octets) this buffer can contain.
     *
     * @return the number of bytes (octets) this buffer can contain.
     */
    int capacity();

    /**
     * Sets the capacity of this buffer.  If the {@code newCapacity} is less than the current
     * capacity, the content of this buffer is truncated.  If the {@code newCapacity} is greater
     * than the current capacity, the buffer is appended with unspecified data whose length is
     * {@code (newCapacity - currentCapacity)}.
     *
     * @param newCapacity the new capacity.
     * @return itself.
     * @throws IllegalArgumentException if the {@code newCapacity} is greater than {@link #maxCapacity()}
     * @throws ReadOnlyBufferException if this buffer is read-only
     */
    Buffer capacity(int newCapacity);

    /**
     * Returns the maximum allowed capacity of this buffer. This value provides an upper
     * bound on {@link #capacity()}.
     *
     * @return the max capacity of this buffer.
     */
    int maxCapacity();

    /**
     * Returns the {@code readerIndex} of this buffer.
     *
     * @return the {@code readerIndex} of this buffer.
     */
    int readerIndex();

    /**
     * Sets the {@code readerIndex} of this buffer.
     *
     * @param readerIndex the new readerIndex of this buffer.
     * @return itself.
     * @throws IndexOutOfBoundsException
     *         if the specified {@code readerIndex} is
     *            less than {@code 0} or
     *            greater than {@code this.writerIndex}
     */
    Buffer readerIndex(int readerIndex);

    /**
     * Returns the {@code writerIndex} of this buffer.
     *
     * @return the {@code writerIndex} of this buffer.
     */
    int writerIndex();

    /**
     * Sets the {@code writerIndex} of this buffer.
     *
     * @param writerIndex the new writerIndex of this buffer.
     * @return itself.
     * @throws IndexOutOfBoundsException
     *         if the specified {@code writerIndex} is
     *            less than {@code this.readerIndex} or
     *            greater than {@code this.capacity}
     */
    Buffer writerIndex(int writerIndex);

    /**
     * Returns the number of readable bytes which is equal to
     * {@code (this.writerIndex - this.readerIndex)}.
     *
     * @return the number of readables bytes in this buffer.
     */
    int readableBytes();

    /**
     * Returns the number of writable bytes which is equal to
     * {@code (this.capacity - this.writerIndex)}.
     *
     * @return the number of writable bytes in this buffer.
     */
    int writableBytes();

    /**
     * Returns the maximum possible number of writable bytes, which is equal to
     * {@code (this.maxCapacity - this.writerIndex)}.
     *
     * @return the maximum possible number of writable bytes in this buffer.
     */
    int maxWritableBytes();

    /**
     * Expands the buffer {@link #capacity()} to make sure the number of
     * {@linkplain #writableBytes() writable bytes} is equal to or greater than the
     * specified value.  If there are enough writable bytes in this buffer, this method
     * returns with no side effect.
     *
     * @param minWritableBytes
     *        the expected minimum number of writable bytes
     * @return this object.
     * @throws IndexOutOfBoundsException
     *         if {@link #writerIndex()} + {@code minWritableBytes} &gt; {@link #maxCapacity()}
     * @throws ReadOnlyBufferException if this buffer is read-only
     */
    Buffer ensureWritable(int minWritableBytes);

    /**
     * Expands the buffer {@link #capacity()} to make sure the number of
     * {@linkplain #writableBytes() writable bytes} is equal to or greater than the
     * specified value. Unlike {@link #ensureWritable(int)}, this method returns a status code.
     *
     * @param minWritableBytes
     *        the expected minimum number of writable bytes
     * @param force
     *        When {@link #writerIndex()} + {@code minWritableBytes} &gt; {@link #maxCapacity()}:
     *        <ul>
     *        <li>{@code true} - the capacity of the buffer is expanded to {@link #maxCapacity()}</li>
     *        <li>{@code false} - the capacity of the buffer is unchanged</li>
     *        </ul>
     * @return {@code 0} if the buffer has enough writable bytes, and its capacity is unchanged.
     *         {@code 1} if the buffer does not have enough bytes, and its capacity is unchanged.
     *         {@code 2} if the buffer has enough writable bytes, and its capacity has been increased.
     *         {@code 3} if the buffer does not have enough bytes, but its capacity has been
     *                   increased to its maximum.
     */
    int ensureWritable(int minWritableBytes, boolean force);

    /**
     * Tries to make sure the number of {@linkplain #writableBytes() writable bytes}
     * is equal to or greater than the specified value. Unlike {@link #ensureWritable(int)},
     * this method does not raise an exception but returns a status code.
     *
     * @param minWritableBytes
     *        the expected minimum number of writable bytes
     * @param force
     *        When {@link #writerIndex()} + {@code minWritableBytes} &gt; {@link #maxCapacity()}:
     *        <ul>
     *        <li>{@code true} - the capacity of the buffer is expanded to {@link #maxCapacity()}</li>
     *        <li>{@code false} - the capacity of the buffer is unchanged</li>
     *        </ul>
     * @return {@code true} if this {@link Buffer} has at least {@code minWritableBytes} writable bytes after this call.
     */
    default boolean tryEnsureWritable(int minWritableBytes, boolean force) {
        switch (ensureWritable(minWritableBytes, force)) {
            case 0:
            case 2:
                return true;
            default:
                return false;
        }
    }

    /**
     * Sets the {@code readerIndex} and {@code writerIndex} of this buffer to
     * {@code 0}.
     * <p>
     * Please note that the behavior of this method is different
     * from that of NIO buffer, which sets the {@code limit} to
     * the {@code capacity} of the buffer.
     * @return this.
     */
    Buffer clear();

    /**
     * Gets a boolean at the specified absolute (@code index) in this buffer.
     * This method does not modify the {@code readerIndex} or {@code writerIndex}
     * of this buffer.
     *
     * @param index absolute (@code index) in this buffer.
     * @return a boolean.
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         {@code index + 1} is greater than {@code this.capacity}
     */
    boolean getBoolean(int index);

    /**
     * Gets a byte at the specified absolute {@code index} in this buffer.
     * This method does not modify {@code readerIndex} or {@code writerIndex} of
     * this buffer.
     *
     * @param index absolute (@code index) in this buffer.
     * @return a byte.
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         {@code index + 1} is greater than {@code this.capacity}
     */
    byte getByte(int index);

    /**
     * Gets an unsigned byte at the specified absolute {@code index} in this
     * buffer.  This method does not modify {@code readerIndex} or
     * {@code writerIndex} of this buffer.
     *
     * @param index absolute (@code index) in this buffer.
     * @return an unsigned byte.
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         {@code index + 1} is greater than {@code this.capacity}
     */
    short getUnsignedByte(int index);

    /**
     * Gets a 16-bit short integer at the specified absolute {@code index} in
     * this buffer.  This method does not modify {@code readerIndex} or
     * {@code writerIndex} of this buffer.
     *
     * @param index absolute (@code index) in this buffer.
     * @return a short.
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         {@code index + 2} is greater than {@code this.capacity}
     */
    short getShort(int index);

    /**
     * Gets a 16-bit short integer at the specified absolute {@code index} in
     * this buffer in Little Endian Byte Order. This method does not modify
     * {@code readerIndex} or {@code writerIndex} of this buffer.
     *
     * @param index absolute (@code index) in this buffer.
     * @return a short.
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         {@code index + 2} is greater than {@code this.capacity}
     */
    short getShortLE(int index);

    /**
     * Gets an unsigned 16-bit short integer at the specified absolute
     * {@code index} in this buffer.  This method does not modify
     * {@code readerIndex} or {@code writerIndex} of this buffer.
     *
     * @param index absolute (@code index) in this buffer.
     * @return a short.
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         {@code index + 2} is greater than {@code this.capacity}
     */
    int getUnsignedShort(int index);

    /**
     * Gets an unsigned 16-bit short integer at the specified absolute
     * {@code index} in this buffer in Little Endian Byte Order.
     * This method does not modify {@code readerIndex} or
     * {@code writerIndex} of this buffer.
     *
     * @param index absolute (@code index) in this buffer.
     * @return a short.
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         {@code index + 2} is greater than {@code this.capacity}
     */
    int getUnsignedShortLE(int index);

    /**
     * Gets a 24-bit medium integer at the specified absolute {@code index} in
     * this buffer.  This method does not modify {@code readerIndex} or
     * {@code writerIndex} of this buffer.
     *
     * @param index absolute (@code index) in this buffer.
     * @return a medium int.
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         {@code index + 3} is greater than {@code this.capacity}
     */
    int getMedium(int index);

    /**
     * Gets a 24-bit medium integer at the specified absolute {@code index} in
     * this buffer in the Little Endian Byte Order. This method does not
     * modify {@code readerIndex} or {@code writerIndex} of this buffer.
     *
     * @param index absolute (@code index) in this buffer.
     * @return a medium int.
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         {@code index + 3} is greater than {@code this.capacity}
     */
    int getMediumLE(int index);

    /**
     * Gets an unsigned 24-bit medium integer at the specified absolute
     * {@code index} in this buffer.  This method does not modify
     * {@code readerIndex} or {@code writerIndex} of this buffer.
     *
     * @param index absolute (@code index) in this buffer.
     * @return a medium in.
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         {@code index + 3} is greater than {@code this.capacity}
     */
    int getUnsignedMedium(int index);

    /**
     * Gets an unsigned 24-bit medium integer at the specified absolute
     * {@code index} in this buffer in Little Endian Byte Order.
     * This method does not modify {@code readerIndex} or
     * {@code writerIndex} of this buffer.
     *
     * @param index absolute (@code index) in this buffer.
     * @return a medium int.
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         {@code index + 3} is greater than {@code this.capacity}
     */
    int getUnsignedMediumLE(int index);

    /**
     * Gets a 32-bit integer at the specified absolute {@code index} in
     * this buffer.  This method does not modify {@code readerIndex} or
     * {@code writerIndex} of this buffer.
     *
     * @param index absolute (@code index) in this buffer.
     * @return a int.
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         {@code index + 4} is greater than {@code this.capacity}
     */
    int getInt(int index);

    /**
     * Gets a 32-bit integer at the specified absolute {@code index} in
     * this buffer with Little Endian Byte Order. This method does not
     * modify {@code readerIndex} or {@code writerIndex} of this buffer.
     *
     * @param index absolute (@code index) in this buffer.
     * @return a int.
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         {@code index + 4} is greater than {@code this.capacity}
     */
    int getIntLE(int index);

    /**
     * Gets an unsigned 32-bit integer at the specified absolute {@code index}
     * in this buffer.  This method does not modify {@code readerIndex} or
     * {@code writerIndex} of this buffer.
     *
     * @param index absolute (@code index) in this buffer.
     * @return a unsigned int.
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         {@code index + 4} is greater than {@code this.capacity}
     */
    long getUnsignedInt(int index);

    /**
     * Gets an unsigned 32-bit integer at the specified absolute {@code index}
     * in this buffer in Little Endian Byte Order. This method does not
     * modify {@code readerIndex} or {@code writerIndex} of this buffer.
     *
     * @param index absolute (@code index) in this buffer.
     * @return a unsigned int.
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         {@code index + 4} is greater than {@code this.capacity}
     */
    long getUnsignedIntLE(int index);

    /**
     * Gets a 64-bit long integer at the specified absolute {@code index} in
     * this buffer.  This method does not modify {@code readerIndex} or
     * {@code writerIndex} of this buffer.
     *
     * @param index absolute (@code index) in this buffer.
     * @return a long.
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         {@code index + 8} is greater than {@code this.capacity}
     */
    long getLong(int index);

    /**
     * Gets a 64-bit long integer at the specified absolute {@code index} in
     * this buffer in Little Endian Byte Order. This method does not
     * modify {@code readerIndex} or {@code writerIndex} of this buffer.
     *
     * @param index absolute (@code index) in this buffer.
     * @return a long.
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         {@code index + 8} is greater than {@code this.capacity}
     */
    long getLongLE(int index);

    /**
     * Gets a 2-byte UTF-16 character at the specified absolute
     * {@code index} in this buffer.  This method does not modify
     * {@code readerIndex} or {@code writerIndex} of this buffer.
     *
     * @param index absolute (@code index) in this buffer.
     * @return a char.
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         {@code index + 2} is greater than {@code this.capacity}
     */
    char getChar(int index);

    /**
     * Gets a 32-bit floating point number at the specified absolute
     * {@code index} in this buffer.  This method does not modify
     * {@code readerIndex} or {@code writerIndex} of this buffer.
     *
     * @param index absolute (@code index) in this buffer.
     * @return a float.
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         {@code index + 4} is greater than {@code this.capacity}
     */
    float getFloat(int index);

    /**
     * Gets a 64-bit floating point number at the specified absolute
     * {@code index} in this buffer.  This method does not modify
     * {@code readerIndex} or {@code writerIndex} of this buffer.
     *
     * @param index absolute (@code index) in this buffer.
     * @return a double.
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         {@code index + 8} is greater than {@code this.capacity}
     */
    double getDouble(int index);

    /**
     * Transfers this buffer's data to the specified destination starting at
     * the specified absolute {@code index} until the destination becomes
     * non-writable.  This method is basically same with
     * {@link #getBytes(int, Buffer, int, int)}, except that this
     * method increases the {@code writerIndex} of the destination by the
     * number of the transferred bytes while
     * {@link #getBytes(int, Buffer, int, int)} does not.
     * This method does not modify {@code readerIndex} or {@code writerIndex} of
     * the source buffer (i.e. {@code this}).
     *
     * @param index absolute (@code index) in this buffer.
     * @param dst the destination buffer.
     * @return itself.
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         if {@code index + dst.writableBytes} is greater than
     *            {@code this.capacity}
     */
    Buffer getBytes(int index, Buffer dst);

    /**
     * Transfers this buffer's data to the specified destination starting at
     * the specified absolute {@code index}.  This method is basically same
     * with {@link #getBytes(int, Buffer, int, int)}, except that this
     * method increases the {@code writerIndex} of the destination by the
     * number of the transferred bytes while
     * {@link #getBytes(int, Buffer, int, int)} does not.
     * This method does not modify {@code readerIndex} or {@code writerIndex} of
     * the source buffer (i.e. {@code this}).
     *
     * @param index absolute (@code index) in this buffer.
     * @param dst the destination buffer.
     * @param length the number of bytes to transfer
     * @return itself.
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0},
     *         if {@code index + length} is greater than
     *            {@code this.capacity}, or
     *         if {@code length} is greater than {@code dst.writableBytes}
     */
    Buffer getBytes(int index, Buffer dst, int length);

    /**
     * Transfers this buffer's data to the specified destination starting at
     * the specified absolute {@code index}.
     * This method does not modify {@code readerIndex} or {@code writerIndex}
     * of both the source (i.e. {@code this}) and the destination.
     *
     * @param index absolute (@code index) in this buffer.
     * @param dst the destination buffer.
     * @param dstIndex the first index of the destination
     * @param length   the number of bytes to transfer
     * @return itself.
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0},
     *         if the specified {@code dstIndex} is less than {@code 0},
     *         if {@code index + length} is greater than
     *            {@code this.capacity}, or
     *         if {@code dstIndex + length} is greater than
     *            {@code dst.capacity}
     */
    Buffer getBytes(int index, Buffer dst, int dstIndex, int length);

    /**
     * Transfers this buffer's data to the specified destination starting at
     * the specified absolute {@code index}.
     * This method does not modify {@code readerIndex} or {@code writerIndex} of
     * this buffer
     *
     * @param index absolute (@code index) in this buffer.
     * @param dst the destination array.
     * @return itself.
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         if {@code index + dst.length} is greater than
     *            {@code this.capacity}
     */
    Buffer getBytes(int index, byte[] dst);

    /**
     * Transfers this buffer's data to the specified destination starting at
     * the specified absolute {@code index}.
     * This method does not modify {@code readerIndex} or {@code writerIndex}
     * of this buffer.
     *
     * @param index absolute (@code index) in this buffer.
     * @param dst the destination array.
     * @param dstIndex the first index of the destination
     * @param length   the number of bytes to transfer
     * @return itself.
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0},
     *         if the specified {@code dstIndex} is less than {@code 0},
     *         if {@code index + length} is greater than
     *            {@code this.capacity}, or
     *         if {@code dstIndex + length} is greater than
     *            {@code dst.length}
     */
    Buffer getBytes(int index, byte[] dst, int dstIndex, int length);

    /**
     * Transfers this buffer's data to the specified destination starting at
     * the specified absolute {@code index} until the destination's position
     * reaches its limit.
     * This method does not modify {@code readerIndex} or {@code writerIndex} of
     * this buffer while the destination's {@code position} will be increased.
     *
     * @param index absolute (@code index) in this buffer.
     * @param dst the destination buffer.
     * @return itself.
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         if {@code index + dst.remaining()} is greater than
     *            {@code this.capacity}
     */
    Buffer getBytes(int index, ByteBuffer dst);

    /**
     * Sets the specified boolean at the specified absolute {@code index} in this
     * buffer.
     * This method does not modify {@code readerIndex} or {@code writerIndex} of
     * this buffer.
     *
     * @param index absolute (@code index) in this buffer.
     * @param value the value.
     * @return itself.
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         {@code index + 1} is greater than {@code this.capacity}
     * @throws ReadOnlyBufferException if this buffer is read-only
     */
    Buffer setBoolean(int index, boolean value);

    /**
     * Sets the specified byte at the specified absolute {@code index} in this
     * buffer.  The 24 high-order bits of the specified value are ignored.
     * This method does not modify {@code readerIndex} or {@code writerIndex} of
     * this buffer.
     *
     * @param index absolute (@code index) in this buffer.
     * @param value the value.
     * @return itself.
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         {@code index + 1} is greater than {@code this.capacity}
     * @throws ReadOnlyBufferException if this buffer is read-only
     */
    Buffer setByte(int index, int value);

    /**
     * Sets the specified 16-bit short integer at the specified absolute
     * {@code index} in this buffer.  The 16 high-order bits of the specified
     * value are ignored.
     * This method does not modify {@code readerIndex} or {@code writerIndex} of
     * this buffer.
     *
     * @param index absolute (@code index) in this buffer.
     * @param value the value.
     * @return itself.
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         {@code index + 2} is greater than {@code this.capacity}
     * @throws ReadOnlyBufferException if this buffer is read-only
     */
    Buffer setShort(int index, int value);

    /**
     * Sets the specified 16-bit short integer at the specified absolute
     * {@code index} in this buffer with the Little Endian Byte Order.
     * The 16 high-order bits of the specified value are ignored.
     * This method does not modify {@code readerIndex} or {@code writerIndex} of
     * this buffer.
     *
     * @param index absolute (@code index) in this buffer.
     * @param value the value.
     * @return itself.
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         {@code index + 2} is greater than {@code this.capacity}
     * @throws ReadOnlyBufferException if this buffer is read-only
     */
    Buffer setShortLE(int index, int value);

    /**
     * Sets the specified 24-bit medium integer at the specified absolute
     * {@code index} in this buffer.  Please note that the most significant
     * byte is ignored in the specified value.
     * This method does not modify {@code readerIndex} or {@code writerIndex} of
     * this buffer.
     *
     * @param index absolute (@code index) in this buffer.
     * @param value the value.
     * @return itself.
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         {@code index + 3} is greater than {@code this.capacity}
     * @throws ReadOnlyBufferException if this buffer is read-only
     */
    Buffer setMedium(int index, int value);

    /**
     * Sets the specified 24-bit medium integer at the specified absolute
     * {@code index} in this buffer in the Little Endian Byte Order.
     * Please note that the most significant byte is ignored in the
     * specified value.
     * This method does not modify {@code readerIndex} or {@code writerIndex} of
     * this buffer.
     *
     * @param index absolute (@code index) in this buffer.
     * @param value the value.
     * @return itself.
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         {@code index + 3} is greater than {@code this.capacity}
     * @throws ReadOnlyBufferException if this buffer is read-only
     */
    Buffer setMediumLE(int index, int value);

    /**
     * Sets the specified 32-bit integer at the specified absolute
     * {@code index} in this buffer.
     * This method does not modify {@code readerIndex} or {@code writerIndex} of
     * this buffer.
     *
     * @param index absolute (@code index) in this buffer.
     * @param value the value.
     * @return itself.
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         {@code index + 4} is greater than {@code this.capacity}
     * @throws ReadOnlyBufferException if this buffer is read-only
     */
    Buffer setInt(int index, int value);

    /**
     * Sets the specified 32-bit integer at the specified absolute
     * {@code index} in this buffer with Little Endian byte order
     * .
     * This method does not modify {@code readerIndex} or {@code writerIndex} of
     * this buffer.
     *
     * @param index absolute (@code index) in this buffer.
     * @param value the value.
     * @return itself.
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         {@code index + 4} is greater than {@code this.capacity}
     * @throws ReadOnlyBufferException if this buffer is read-only
     */
    Buffer setIntLE(int index, int value);

    /**
     * Sets the specified 64-bit long integer at the specified absolute
     * {@code index} in this buffer.
     * This method does not modify {@code readerIndex} or {@code writerIndex} of
     * this buffer.
     *
     * @param index absolute (@code index) in this buffer.
     * @param value the value.
     * @return itself.
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         {@code index + 8} is greater than {@code this.capacity}
     * @throws ReadOnlyBufferException if this buffer is read-only
     */
    Buffer setLong(int index, long value);

    /**
     * Sets the specified 64-bit long integer at the specified absolute
     * {@code index} in this buffer in Little Endian Byte Order.
     * This method does not modify {@code readerIndex} or {@code writerIndex} of
     * this buffer.
     *
     * @param index absolute (@code index) in this buffer.
     * @param value the value.
     * @return itself.
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         {@code index + 8} is greater than {@code this.capacity}
     * @throws ReadOnlyBufferException if this buffer is read-only
     */
    Buffer setLongLE(int index, long value);

    /**
     * Sets the specified 2-byte UTF-16 character at the specified absolute
     * {@code index} in this buffer.
     * The 16 high-order bits of the specified value are ignored.
     * This method does not modify {@code readerIndex} or {@code writerIndex} of
     * this buffer.
     *
     * @param index absolute (@code index) in this buffer.
     * @param value the value.
     * @return itself.
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         {@code index + 2} is greater than {@code this.capacity}
     * @throws ReadOnlyBufferException if this buffer is read-only
     */
    Buffer setChar(int index, int value);

    /**
     * Sets the specified 32-bit floating-point number at the specified
     * absolute {@code index} in this buffer.
     * This method does not modify {@code readerIndex} or {@code writerIndex} of
     * this buffer.
     *
     * @param index absolute (@code index) in this buffer.
     * @param value the value.
     * @return itself.
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         {@code index + 4} is greater than {@code this.capacity}
     * @throws ReadOnlyBufferException if this buffer is read-only
     */
    Buffer setFloat(int index, float value);

    /**
     * Sets the specified 64-bit floating-point number at the specified
     * absolute {@code index} in this buffer.
     * This method does not modify {@code readerIndex} or {@code writerIndex} of
     * this buffer.
     *
     * @param index absolute (@code index) in this buffer.
     * @param value the value.
     * @return itself.
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         {@code index + 8} is greater than {@code this.capacity}
     * @throws ReadOnlyBufferException if this buffer is read-only
     */
    Buffer setDouble(int index, double value);

    /**
     * Transfers the specified source buffer's data to this buffer starting at
     * the specified absolute {@code index} until the source buffer becomes
     * unreadable.  This method is basically same with
     * {@link #setBytes(int, Buffer, int, int)}, except that this
     * method increases the {@code readerIndex} of the source buffer by
     * the number of the transferred bytes while
     * {@link #setBytes(int, Buffer, int, int)} does not.
     * This method does not modify {@code readerIndex} or {@code writerIndex} of
     * the source buffer (i.e. {@code this}).
     *
     * @param index absolute (@code index) in this buffer.
     * @param src the source buffer.
     * @return itself.
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         if {@code index + src.readableBytes} is greater than
     *            {@code this.capacity}
     * @throws ReadOnlyBufferException if this buffer is read-only
     */
    Buffer setBytes(int index, Buffer src);

    /**
     * Transfers the specified source buffer's data to this buffer starting at
     * the specified absolute {@code index}.  This method is basically same
     * with {@link #setBytes(int, Buffer, int, int)}, except that this
     * method increases the {@code readerIndex} of the source buffer by
     * the number of the transferred bytes while
     * {@link #setBytes(int, Buffer, int, int)} does not.
     * This method does not modify {@code readerIndex} or {@code writerIndex} of
     * the source buffer (i.e. {@code this}).
     *
     * @param index absolute (@code index) in this buffer.
     * @param src the source buffer.
     * @param length the number of bytes to transfer
     * @return itself.
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0},
     *         if {@code index + length} is greater than
     *            {@code this.capacity}, or
     *         if {@code length} is greater than {@code src.readableBytes}
     * @throws ReadOnlyBufferException if this buffer is read-only
     */
    Buffer setBytes(int index, Buffer src, int length);

    /**
     * Transfers the specified source buffer's data to this buffer starting at
     * the specified absolute {@code index}.
     * This method does not modify {@code readerIndex} or {@code writerIndex}
     * of both the source (i.e. {@code this}) and the destination.
     *
     * @param index absolute (@code index) in this buffer.
     * @param src the source buffer.
     * @param srcIndex the first index of the source
     * @param length   the number of bytes to transfer
     * @return itself.
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0},
     *         if the specified {@code srcIndex} is less than {@code 0},
     *         if {@code index + length} is greater than
     *            {@code this.capacity}, or
     *         if {@code srcIndex + length} is greater than
     *            {@code src.capacity}
     * @throws ReadOnlyBufferException if this buffer is read-only
     */
    Buffer setBytes(int index, Buffer src, int srcIndex, int length);

    /**
     * Transfers the specified source array's data to this buffer starting at
     * the specified absolute {@code index}.
     * This method does not modify {@code readerIndex} or {@code writerIndex} of
     * this buffer.
     *
     * @param index absolute (@code index) in this buffer.
     * @param src the source array.
     * @return itself.
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         if {@code index + src.length} is greater than
     *            {@code this.capacity}
     * @throws ReadOnlyBufferException if this buffer is read-only
     */
    Buffer setBytes(int index, byte[] src);

    /**
     * Transfers the specified source array's data to this buffer starting at
     * the specified absolute {@code index}.
     * This method does not modify {@code readerIndex} or {@code writerIndex} of
     * this buffer.
     *
     * @param index absolute (@code index) in this buffer.
     * @param src the source array.
     * @param srcIndex the first index of the source
     * @param length   the number of bytes to transfer
     * @return itself.
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0},
     *         if the specified {@code srcIndex} is less than {@code 0},
     *         if {@code index + length} is greater than
     *            {@code this.capacity}, or
     *         if {@code srcIndex + length} is greater than {@code src.length}
     * @throws ReadOnlyBufferException if this buffer is read-only
     */
    Buffer setBytes(int index, byte[] src, int srcIndex, int length);

    /**
     * Transfers the specified source buffer's data to this buffer starting at
     * the specified absolute {@code index} until the source buffer's position
     * reaches its limit.
     * This method does not modify {@code readerIndex} or {@code writerIndex} of
     * this buffer.
     *
     * @param index absolute (@code index) in this buffer.
     * @param src the source buffer.
     * @return itself.
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         if {@code index + src.remaining()} is greater than
     *            {@code this.capacity}
     * @throws ReadOnlyBufferException if this buffer is read-only
     */
    Buffer setBytes(int index, ByteBuffer src);

    /**
     * Transfers a fixed amount from the specified source InputStream's data to this buffer starting at
     * the specified absolute {@code index} until {@code length} bytes have been read, the end of stream
     * is reached, or an exception is thrown.
     * <p>
     * This method does not modify {@code readerIndex} or {@code writerIndex} of this buffer.
     *
     * @param index absolute (@code index) in this buffer.
     * @param src the source InputStream.
     * @param length the maximum number of bytes to transfer. The buffer may be resized to accommodate this amount of
     * data.
     * @return the actual number of bytes read in from {@code src}.
     *         {@code -1} if the specified channel is closed.
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         if {@code index + src.remaining()} is greater than
     *            {@code this.capacity}
     * @throws IOException if the InputStream throws an exception while being read from.
     * @throws ReadOnlyBufferException if this buffer is read-only
     */
    int setBytes(int index, InputStream src, int length) throws IOException;

    /**
     * Transfers all the specified source InputStream's data to this buffer starting at
     * the specified absolute {@code index} until the end of stream is reached or an exception is thrown.
     * <p>
     * This method does not modify {@code readerIndex} or {@code writerIndex} of this buffer.
     * <p>
     * This method may modify the underlying storage size of this array to accomidate for reading data.
     *
     * @param index absolute (@code index) in this buffer.
     * @param src the source InputStream.
     * @param chunkSize chunkSize the amount of data that will be read from {@code src} on each read attempt.
     * @return the actual total number of bytes read in from {@code src}.
     * {@code -1} if no bytes were read because the specified InputStream was closed when this method was called.
     * @throws IndexOutOfBoundsException
     *         if the specified {@code index} is less than {@code 0} or
     *         if {@code index + src.remaining()} is greater than
     *            {@code this.capacity}
     * @throws IOException if the InputStream throws an exception while being read from.
     * @throws ReadOnlyBufferException if this buffer is read-only
     */
    int setBytesUntilEndStream(int index, InputStream src, int chunkSize) throws IOException;

    /**
     * Gets a boolean at the current {@code readerIndex} and increases
     * the {@code readerIndex} by {@code 1} in this buffer.
     *
     * @return a boolean.
     * @throws IndexOutOfBoundsException
     *         if {@code this.readableBytes} is less than {@code 1}
     */
    boolean readBoolean();

    /**
     * Gets a byte at the current {@code readerIndex} and increases
     * the {@code readerIndex} by {@code 1} in this buffer.
     *
     * @return a byte.
     * @throws IndexOutOfBoundsException
     *         if {@code this.readableBytes} is less than {@code 1}
     */
    byte readByte();

    /**
     * Gets an unsigned byte at the current {@code readerIndex} and increases
     * the {@code readerIndex} by {@code 1} in this buffer.
     *
     * @return a byte.
     * @throws IndexOutOfBoundsException
     *         if {@code this.readableBytes} is less than {@code 1}
     */
    short readUnsignedByte();

    /**
     * Gets a 16-bit short integer at the current {@code readerIndex}
     * and increases the {@code readerIndex} by {@code 2} in this buffer.
     *
     * @return a short.
     * @throws IndexOutOfBoundsException
     *         if {@code this.readableBytes} is less than {@code 2}
     */
    short readShort();

    /**
     * Gets a 16-bit short integer at the current {@code readerIndex}
     * in the Little Endian Byte Order and increases the {@code readerIndex}
     * by {@code 2} in this buffer.
     *
     * @return a short.
     * @throws IndexOutOfBoundsException
     *         if {@code this.readableBytes} is less than {@code 2}
     */
    short readShortLE();

    /**
     * Gets an unsigned 16-bit short integer at the current {@code readerIndex}
     * and increases the {@code readerIndex} by {@code 2} in this buffer.
     *
     * @return a short.
     * @throws IndexOutOfBoundsException
     *         if {@code this.readableBytes} is less than {@code 2}
     */
    int readUnsignedShort();

    /**
     * Gets an unsigned 16-bit short integer at the current {@code readerIndex}
     * in the Little Endian Byte Order and increases the {@code readerIndex}
     * by {@code 2} in this buffer.
     *
     * @return a short.
     * @throws IndexOutOfBoundsException
     *         if {@code this.readableBytes} is less than {@code 2}
     */
    int readUnsignedShortLE();

    /**
     * Gets a 24-bit medium integer at the current {@code readerIndex}
     * and increases the {@code readerIndex} by {@code 3} in this buffer.
     *
     * @return a medium int.
     * @throws IndexOutOfBoundsException
     *         if {@code this.readableBytes} is less than {@code 3}
     */
    int readMedium();

    /**
     * Gets a 24-bit medium integer at the current {@code readerIndex}
     * in the Little Endian Byte Order and increases the
     * {@code readerIndex} by {@code 3} in this buffer.
     *
     * @return a medium int.
     * @throws IndexOutOfBoundsException
     *         if {@code this.readableBytes} is less than {@code 3}
     */
    int readMediumLE();

    /**
     * Gets an unsigned 24-bit medium integer at the current {@code readerIndex}
     * and increases the {@code readerIndex} by {@code 3} in this buffer.
     *
     * @return a medium int.
     * @throws IndexOutOfBoundsException
     *         if {@code this.readableBytes} is less than {@code 3}
     */
    int readUnsignedMedium();

    /**
     * Gets an unsigned 24-bit medium integer at the current {@code readerIndex}
     * in the Little Endian Byte Order and increases the {@code readerIndex}
     * by {@code 3} in this buffer.
     *
     * @return a medium int.
     * @throws IndexOutOfBoundsException
     *         if {@code this.readableBytes} is less than {@code 3}
     */
    int readUnsignedMediumLE();

    /**
     * Gets a 32-bit integer at the current {@code readerIndex}
     * and increases the {@code readerIndex} by {@code 4} in this buffer.
     *
     * @return a int.
     * @throws IndexOutOfBoundsException
     *         if {@code this.readableBytes} is less than {@code 4}
     */
    int readInt();

    /**
     * Gets a 32-bit integer at the current {@code readerIndex}
     * in the Little Endian Byte Order and increases the {@code readerIndex}
     * by {@code 4} in this buffer.
     *
     * @return a int.
     * @throws IndexOutOfBoundsException
     *         if {@code this.readableBytes} is less than {@code 4}
     */
    int readIntLE();

    /**
     * Gets an unsigned 32-bit integer at the current {@code readerIndex}
     * and increases the {@code readerIndex} by {@code 4} in this buffer.
     *
     * @return a int.
     * @throws IndexOutOfBoundsException
     *         if {@code this.readableBytes} is less than {@code 4}
     */
    long readUnsignedInt();

    /**
     * Gets an unsigned 32-bit integer at the current {@code readerIndex}
     * in the Little Endian Byte Order and increases the {@code readerIndex}
     * by {@code 4} in this buffer.
     *
     * @return a int.
     * @throws IndexOutOfBoundsException
     *         if {@code this.readableBytes} is less than {@code 4}
     */
    long readUnsignedIntLE();

    /**
     * Gets a 64-bit integer at the current {@code readerIndex}
     * and increases the {@code readerIndex} by {@code 8} in this buffer.
     *
     * @return a long.
     * @throws IndexOutOfBoundsException
     *         if {@code this.readableBytes} is less than {@code 8}
     */
    long readLong();

    /**
     * Gets a 64-bit integer at the current {@code readerIndex}
     * in the Little Endian Byte Order and increases the {@code readerIndex}
     * by {@code 8} in this buffer.
     *
     * @return a long.
     * @throws IndexOutOfBoundsException
     *         if {@code this.readableBytes} is less than {@code 8}
     */
    long readLongLE();

    /**
     * Gets a 2-byte UTF-16 character at the current {@code readerIndex}
     * and increases the {@code readerIndex} by {@code 2} in this buffer.
     *
     * @return a char.
     * @throws IndexOutOfBoundsException
     *         if {@code this.readableBytes} is less than {@code 2}
     */
    char readChar();

    /**
     * Gets a 32-bit floating point number at the current {@code readerIndex}
     * and increases the {@code readerIndex} by {@code 4} in this buffer.
     *
     * @return a float.
     * @throws IndexOutOfBoundsException
     *         if {@code this.readableBytes} is less than {@code 4}
     */
    float readFloat();

    /**
     * Gets a 64-bit floating point number at the current {@code readerIndex}
     * and increases the {@code readerIndex} by {@code 8} in this buffer.
     *
     * @return a double.
     * @throws IndexOutOfBoundsException
     *         if {@code this.readableBytes} is less than {@code 8}
     */
    double readDouble();

    /**
     * Returns a new slice of this buffer's sub-region starting at the current
     * {@code readerIndex} and increases the {@code readerIndex} by the size
     * of the new slice (= {@code length}).
     *
     * @param length the size of the new slice
     * @return the newly created slice
     * @throws IndexOutOfBoundsException
     *         if {@code length} is greater than {@code this.readableBytes}
     */
    Buffer readSlice(int length);

    /**
     * Transfers this buffer's data to a newly created buffer starting at
     * the current {@code readerIndex} and increases the {@code readerIndex}
     * by the number of the transferred bytes (= {@code length}).
     * The returned buffer's {@code readerIndex} and {@code writerIndex} are
     * {@code 0} and {@code length} respectively.
     *
     * @param length the number of bytes to transfer
     * @return the newly created buffer which contains the transferred bytes
     * @throws IndexOutOfBoundsException
     *         if {@code length} is greater than {@code this.readableBytes}
     */
    Buffer readBytes(int length);

    /**
     * Transfers this buffer's data to the specified destination starting at
     * the current {@code readerIndex} until the destination becomes
     * non-writable, and increases the {@code readerIndex} by the number of the
     * transferred bytes.  This method is basically same with
     * {@link #readBytes(Buffer, int, int)}, except that this method
     * increases the {@code writerIndex} of the destination by the number of
     * the transferred bytes while {@link #readBytes(Buffer, int, int)}
     * does not.
     *
     * @param dst the destination buffer.
     * @return self.
     * @throws IndexOutOfBoundsException
     *         if {@code dst.writableBytes} is greater than
     *            {@code this.readableBytes}
     */
    Buffer readBytes(Buffer dst);

    /**
     * Transfers this buffer's data to the specified destination starting at
     * the current {@code readerIndex} and increases the {@code readerIndex}
     * by the number of the transferred bytes (= {@code length}).  This method
     * is basically same with {@link #readBytes(Buffer, int, int)},
     * except that this method increases the {@code writerIndex} of the
     * destination by the number of the transferred bytes (= {@code length})
     * while {@link #readBytes(Buffer, int, int)} does not.
     *
     * @param dst the destination buffer.
     * @param length the number of bytes.
     * @return self.
     * @throws IndexOutOfBoundsException
     *         if {@code length} is greater than {@code this.readableBytes} or
     *         if {@code length} is greater than {@code dst.writableBytes}
     */
    Buffer readBytes(Buffer dst, int length);

    /**
     * Transfers this buffer's data to the specified destination starting at
     * the current {@code readerIndex} and increases the {@code readerIndex}
     * by the number of the transferred bytes (= {@code length}).
     *
     * @param dst the destination buffer.
     * @param dstIndex the first index of the destination
     * @param length   the number of bytes to transfer
     * @return self.
     * @throws IndexOutOfBoundsException
     *         if the specified {@code dstIndex} is less than {@code 0},
     *         if {@code length} is greater than {@code this.readableBytes}, or
     *         if {@code dstIndex + length} is greater than
     *            {@code dst.capacity}
     */
    Buffer readBytes(Buffer dst, int dstIndex, int length);

    /**
     * Transfers this buffer's data to the specified destination starting at
     * the current {@code readerIndex} and increases the {@code readerIndex}
     * by the number of the transferred bytes (= {@code dst.length}).
     *
     * @param dst the destination array.
     * @return self.
     * @throws IndexOutOfBoundsException
     *         if {@code dst.length} is greater than {@code this.readableBytes}
     */
    Buffer readBytes(byte[] dst);

    /**
     * Transfers this buffer's data to the specified destination starting at
     * the current {@code readerIndex} and increases the {@code readerIndex}
     * by the number of the transferred bytes (= {@code length}).
     *
     * @param dst the destination array.
     * @param dstIndex the first index of the destination
     * @param length   the number of bytes to transfer
     * @return self.
     * @throws IndexOutOfBoundsException
     *         if the specified {@code dstIndex} is less than {@code 0},
     *         if {@code length} is greater than {@code this.readableBytes}, or
     *         if {@code dstIndex + length} is greater than {@code dst.length}
     */
    Buffer readBytes(byte[] dst, int dstIndex, int length);

    /**
     * Transfers this buffer's data to the specified destination starting at
     * the current {@code readerIndex} until the destination's position
     * reaches its limit, and increases the {@code readerIndex} by the
     * number of the transferred bytes.
     *
     * @param dst the destination buffer.
     * @return self.
     * @throws IndexOutOfBoundsException
     *         if {@code dst.remaining()} is less than
     *            {@code this.readableBytes}
     */
    Buffer readBytes(ByteBuffer dst);

    /**
     * Increases the current {@code readerIndex} by the specified
     * {@code length} in this buffer.
     *
     * @param length number of bytes.
     * @return self.
     * @throws IndexOutOfBoundsException
     *         if {@code length} is greater than {@code this.readableBytes}
     */
    Buffer skipBytes(int length);

    /**
     * Sets the specified boolean at the current {@code writerIndex}
     * and increases the {@code writerIndex} by {@code 1} in this buffer.
     * If {@code this.writableBytes} is less than {@code 1}, {@link #ensureWritable(int)}
     * will be called in an attempt to expand capacity to accommodate.
     *
     * @param value the value to write.
     * @return self.
     * @throws ReadOnlyBufferException if this buffer is read-only
     */
    Buffer writeBoolean(boolean value);

    /**
     * Sets the specified byte at the current {@code writerIndex}
     * and increases the {@code writerIndex} by {@code 1} in this buffer.
     * The 24 high-order bits of the specified value are ignored.
     * If {@code this.writableBytes} is less than {@code 1}, {@link #ensureWritable(int)}
     * will be called in an attempt to expand capacity to accommodate.
     *
     * @param value the value to write.
     * @return self.
     * @throws ReadOnlyBufferException if this buffer is read-only
     */
    Buffer writeByte(int value);

    /**
     * Sets the specified 16-bit short integer at the current
     * {@code writerIndex} and increases the {@code writerIndex} by {@code 2}
     * in this buffer.  The 16 high-order bits of the specified value are ignored.
     * If {@code this.writableBytes} is less than {@code 2}, {@link #ensureWritable(int)}
     * will be called in an attempt to expand capacity to accommodate.
     *
     * @param value the value to write.
     * @return self.
     * @throws ReadOnlyBufferException if this buffer is read-only
     */
    Buffer writeShort(int value);

    /**
     * Sets the specified 16-bit short integer in the Little Endian Byte
     * Order at the current {@code writerIndex} and increases the
     * {@code writerIndex} by {@code 2} in this buffer.
     * The 16 high-order bits of the specified value are ignored.
     * If {@code this.writableBytes} is less than {@code 2}, {@link #ensureWritable(int)}
     * will be called in an attempt to expand capacity to accommodate.
     *
     * @param value the value to write.
     * @return self.
     * @throws ReadOnlyBufferException if this buffer is read-only
     */
    Buffer writeShortLE(int value);

    /**
     * Sets the specified 24-bit medium integer at the current
     * {@code writerIndex} and increases the {@code writerIndex} by {@code 3}
     * in this buffer.
     * If {@code this.writableBytes} is less than {@code 3}, {@link #ensureWritable(int)}
     * will be called in an attempt to expand capacity to accommodate.
     *
     * @param value the value to write.
     * @return self.
     * @throws ReadOnlyBufferException if this buffer is read-only
     */
    Buffer writeMedium(int value);

    /**
     * Sets the specified 24-bit medium integer at the current
     * {@code writerIndex} in the Little Endian Byte Order and
     * increases the {@code writerIndex} by {@code 3} in this
     * buffer.
     * If {@code this.writableBytes} is less than {@code 3}, {@link #ensureWritable(int)}
     * will be called in an attempt to expand capacity to accommodate.
     *
     * @param value the value to write.
     * @return self.
     * @throws ReadOnlyBufferException if this buffer is read-only
     */
    Buffer writeMediumLE(int value);

    /**
     * Sets the specified 32-bit integer at the current {@code writerIndex}
     * and increases the {@code writerIndex} by {@code 4} in this buffer.
     * If {@code this.writableBytes} is less than {@code 4}, {@link #ensureWritable(int)}
     * will be called in an attempt to expand capacity to accommodate.
     *
     * @param value the value to write.
     * @return self.
     * @throws ReadOnlyBufferException if this buffer is read-only
     */
    Buffer writeInt(int value);

    /**
     * Sets the specified 32-bit integer at the current {@code writerIndex}
     * in the Little Endian Byte Order and increases the {@code writerIndex}
     * by {@code 4} in this buffer.
     * If {@code this.writableBytes} is less than {@code 4}, {@link #ensureWritable(int)}
     * will be called in an attempt to expand capacity to accommodate.
     *
     * @param value the value to write.
     * @return self.
     * @throws ReadOnlyBufferException if this buffer is read-only
     */
    Buffer writeIntLE(int value);

    /**
     * Sets the specified 64-bit long integer at the current
     * {@code writerIndex} and increases the {@code writerIndex} by {@code 8}
     * in this buffer.
     * If {@code this.writableBytes} is less than {@code 8}, {@link #ensureWritable(int)}
     * will be called in an attempt to expand capacity to accommodate.
     *
     * @param value the value to write.
     * @return self.
     * @throws ReadOnlyBufferException if this buffer is read-only
     */
    Buffer writeLong(long value);

    /**
     * Sets the specified 64-bit long integer at the current
     * {@code writerIndex} in the Little Endian Byte Order and
     * increases the {@code writerIndex} by {@code 8}
     * in this buffer.
     * If {@code this.writableBytes} is less than {@code 8}, {@link #ensureWritable(int)}
     * will be called in an attempt to expand capacity to accommodate.
     *
     * @param value the value to write.
     * @return self.
     * @throws ReadOnlyBufferException if this buffer is read-only
     */
    Buffer writeLongLE(long value);

    /**
     * Sets the specified 2-byte UTF-16 character at the current
     * {@code writerIndex} and increases the {@code writerIndex} by {@code 2}
     * in this buffer.  The 16 high-order bits of the specified value are ignored.
     * If {@code this.writableBytes} is less than {@code 2}, {@link #ensureWritable(int)}
     * will be called in an attempt to expand capacity to accommodate.
     *
     * @param value the value to write.
     * @return self.
     * @throws ReadOnlyBufferException if this buffer is read-only
     */
    Buffer writeChar(int value);

    /**
     * Sets the specified 32-bit floating point number at the current
     * {@code writerIndex} and increases the {@code writerIndex} by {@code 4}
     * in this buffer.
     * If {@code this.writableBytes} is less than {@code 4}, {@link #ensureWritable(int)}
     * will be called in an attempt to expand capacity to accommodate.
     *
     * @param value the value to write.
     * @return self.
     * @throws ReadOnlyBufferException if this buffer is read-only
     */
    Buffer writeFloat(float value);

    /**
     * Sets the specified 64-bit floating point number at the current
     * {@code writerIndex} and increases the {@code writerIndex} by {@code 8}
     * in this buffer.
     * If {@code this.writableBytes} is less than {@code 8}, {@link #ensureWritable(int)}
     * will be called in an attempt to expand capacity to accommodate.
     *
     * @param value the value to write.
     * @return self.
     * @throws ReadOnlyBufferException if this buffer is read-only
     */
    Buffer writeDouble(double value);

    /**
     * Transfers the specified source buffer's data to this buffer starting at
     * the current {@code writerIndex} until the source buffer becomes
     * unreadable, and increases the {@code writerIndex} by the number of
     * the transferred bytes.  This method is basically same with
     * {@link #writeBytes(Buffer, int, int)}, except that this method
     * increases the {@code readerIndex} of the source buffer by the number of
     * the transferred bytes while {@link #writeBytes(Buffer, int, int)}
     * does not.
     * If {@code this.writableBytes} is less than {@code src.readableBytes},
     * {@link #ensureWritable(int)} will be called in an attempt to expand
     * capacity to accommodate.
     *
     * @param src the buffer to write.
     * @return self.
     * @throws ReadOnlyBufferException if this buffer is read-only
     */
    Buffer writeBytes(Buffer src);

    /**
     * Transfers the specified source buffer's data to this buffer starting at
     * the current {@code writerIndex} and increases the {@code writerIndex}
     * by the number of the transferred bytes (= {@code length}).  This method
     * is basically same with {@link #writeBytes(Buffer, int, int)},
     * except that this method increases the {@code readerIndex} of the source
     * buffer by the number of the transferred bytes (= {@code length}) while
     * {@link #writeBytes(Buffer, int, int)} does not.
     * If {@code this.writableBytes} is less than {@code length}, {@link #ensureWritable(int)}
     * will be called in an attempt to expand capacity to accommodate.
     *
     * @param src the buffer to write.
     * @param length the number of bytes to transfer
     * @return self.
     * @throws IndexOutOfBoundsException if {@code length} is greater then {@code src.readableBytes}
     * @throws ReadOnlyBufferException if this buffer is read-only
     */
    Buffer writeBytes(Buffer src, int length);

    /**
     * Transfers the specified source buffer's data to this buffer starting at
     * the current {@code writerIndex} and increases the {@code writerIndex}
     * by the number of the transferred bytes (= {@code length}).
     * If {@code this.writableBytes} is less than {@code length}, {@link #ensureWritable(int)}
     * will be called in an attempt to expand capacity to accommodate.
     *
     * @param src the buffer to write.
     * @param srcIndex the first index of the source
     * @param length   the number of bytes to transfer
     * @return self.
     * @throws IndexOutOfBoundsException
     *         if the specified {@code srcIndex} is less than {@code 0}, or
     *         if {@code srcIndex + length} is greater than {@code src.capacity}
     * @throws ReadOnlyBufferException if this buffer is read-only
     */
    Buffer writeBytes(Buffer src, int srcIndex, int length);

    /**
     * Transfers the specified source array's data to this buffer starting at
     * the current {@code writerIndex} and increases the {@code writerIndex}
     * by the number of the transferred bytes (= {@code src.length}).
     * If {@code this.writableBytes} is less than {@code src.length}, {@link #ensureWritable(int)}
     * will be called in an attempt to expand capacity to accommodate.
     *
     * @param src the array to write.
     * @return self.
     * @throws ReadOnlyBufferException if this buffer is read-only
     */
    Buffer writeBytes(byte[] src);

    /**
     * Transfers the specified source array's data to this buffer starting at
     * the current {@code writerIndex} and increases the {@code writerIndex}
     * by the number of the transferred bytes (= {@code length}).
     * If {@code this.writableBytes} is less than {@code length}, {@link #ensureWritable(int)}
     * will be called in an attempt to expand capacity to accommodate.
     *
     * @param src the array to write.
     * @param srcIndex the first index of the source
     * @param length   the number of bytes to transfer
     * @return self.
     * @throws IndexOutOfBoundsException
     *         if the specified {@code srcIndex} is less than {@code 0}, or
     *         if {@code srcIndex + length} is greater than {@code src.length}
     * @throws ReadOnlyBufferException if this buffer is read-only
     */
    Buffer writeBytes(byte[] src, int srcIndex, int length);

    /**
     * Transfers the specified source buffer's data to this buffer starting at
     * the current {@code writerIndex} until the source buffer's position
     * reaches its limit, and increases the {@code writerIndex} by the
     * number of the transferred bytes.
     * If {@code this.writableBytes} is less than {@code src.remaining()},
     * {@link #ensureWritable(int)} will be called in an attempt to expand
     * capacity to accommodate.
     *
     * @param src the source buffer to write.
     * @return self.
     * @throws ReadOnlyBufferException if this buffer is read-only
     */
    Buffer writeBytes(ByteBuffer src);

    /**
     * Transfers ta fixed amount from the specified source {@link InputStream}'s data to this buffer starting at
     * the current {@code writerIndex} until {@code length} bytes have been read, the end of stream
     * is reached, or an exception is thrown.
     * If {@code this.writableBytes} is less than {@code length}, {@link #ensureWritable(int)}
     * will be called in an attempt to expand capacity to accommodate.
     * <p>
     * This method will increase the {@code writerIndex} by the number of the transferred bytes if the write operation
     * was successful.
     *
     * @param src the source {@link InputStream} to write.
     * @param length the maximum number of bytes to transfer. The buffer may be resized to accommodate this amount of
     *        data.
     * @return the actual number of bytes read in from {@code src}.
     *         {@code -1} if the specified channel is closed.
     * @throws IOException if the {@link InputStream} throws an exception while being read from.
     * @throws ReadOnlyBufferException if this buffer is read-only
     */
    int writeBytes(InputStream src, int length) throws IOException;

    /**
     * Transfers all the specified source {@link InputStream}'s data to this buffer starting at
     * the current {@code writerIndex} until the end of stream is reached or an exception is thrown.
     * If {@code this.writableBytes} is less than the number of bytes in the {@link InputStream},
     * {@link #ensureWritable(int)} will be called in an attempt to expand capacity to accommodate.
     * Note that because {@link InputStream} does not provide a reliable way to get the remaining bytes,
     * this method may over allocate by a factor of {@code chunkSize}.
     * <p>
     * This method will increase the {@code writerIndex} by the number of the transferred bytes if the write operation
     * was successful.
     *
     * @param src the source {@link InputStream} to write.
     * @param chunkSize the amount of data that will be read from {@code src} on each read attempt.
     * @return the actual total number of bytes read in from {@code src}.
     *         {@code -1} if no bytes were read because the specified {@link InputStream} was closed when this method
     *         was called.
     * @throws IOException if the {@link InputStream} throws an exception while being read from.
     * @throws ReadOnlyBufferException if this buffer is read-only
     */
    int writeBytesUntilEndStream(InputStream src, int chunkSize) throws IOException;

    /**
     * Encode a {@link CharSequence} in <a href="http://en.wikipedia.org/wiki/ASCII">ASCII</a> and write it
     * to this buffer starting at {@code writerIndex} and increases the {@code writerIndex} by the
     * number of the transferred bytes.
     * If {@code this.writableBytes} is not large enough to write the whole sequence,
     * {@link #ensureWritable(int)} will be called in an attempt to expand capacity to accommodate.

     * @param seq the source of the data.
     * @return self.
     * @throws ReadOnlyBufferException if this buffer is read-only
     */
    Buffer writeAscii(CharSequence seq);

    /**
     * Encode a {@link CharSequence} in <a href="http://en.wikipedia.org/wiki/UTF-8">UTF-8</a> and write it
     * to this buffer starting at {@code writerIndex} and increases the {@code writerIndex} by the
     * number of the transferred bytes.
     * If {@code this.writableBytes} is not large enough to write the whole sequence,
     * {@link #ensureWritable(int)} will be called in an attempt to expand capacity to accommodate.

     * @param seq the source of the data.
     * @return self.
     * @throws ReadOnlyBufferException if this buffer is read-only
     */
    Buffer writeUtf8(CharSequence seq);

    /**
     * Encode a {@link CharSequence} in <a href="http://en.wikipedia.org/wiki/UTF-8">UTF-8</a> and write it
     * to this buffer starting at {@code writerIndex} and increases the {@code writerIndex} by the
     * number of the transferred bytes.
     *
     * @param seq the source of the data.
     * @param ensureWritable the number of bytes to ensure are writeable.
     * @return self.
     * @throws ReadOnlyBufferException if this buffer is read-only
     */
    Buffer writeUtf8(CharSequence seq, int ensureWritable);

    /**
     * Encode a {@link CharSequence} encoded in {@link Charset} and write it to this buffer starting at
     * {@code writerIndex} and increases the {@code writerIndex} by the number of the transferred bytes.
     *
     * @param seq the source of the data.
     * @param charset the charset used for encoding.
     * @return self.
     * @throws ReadOnlyBufferException if this buffer is read-only
     */
    default Buffer writeCharSequence(CharSequence seq, Charset charset) {
        byte[] bytes = seq.toString().getBytes(charset);
        writeBytes(bytes);
        return this;
    }

    /**
     * Locates the first occurrence of the specified {@code value} in this
     * buffer.  The search takes place from the specified {@code fromIndex}
     * (inclusive)  to the specified {@code toIndex} (exclusive).
     * <p>
     * If {@code fromIndex} is greater than {@code toIndex}, the search is
     * performed in a reversed order.
     * <p>
     * This method does not modify {@code readerIndex} or {@code writerIndex} of
     * this buffer.
     *
     * @param fromIndex the start index.
     * @param toIndex the end index.
     * @param value the value to search.
     * @return the absolute index of the first occurrence if found.
     *         {@code -1} otherwise.
     */
    int indexOf(int fromIndex, int toIndex, byte value);

    /**
     * Locates the first occurrence of the specified {@code value} in this
     * buffer.  The search takes place from the current {@code readerIndex}
     * (inclusive) to the current {@code writerIndex} (exclusive).
     * <p>
     * This method does not modify {@code readerIndex} or {@code writerIndex} of
     * this buffer.
     *
     * @param value the value to search.
     * @return the number of bytes between the current {@code readerIndex}
     *         and the first occurrence if found. {@code -1} otherwise.
     */
    int bytesBefore(byte value);

    /**
     * Locates the first occurrence of the specified {@code value} in this
     * buffer.  The search starts from the current {@code readerIndex}
     * (inclusive) and lasts for the specified {@code length}.
     * <p>
     * This method does not modify {@code readerIndex} or {@code writerIndex} of
     * this buffer.
     *
     * @param length the length.
     * @param value the value to search.
     * @return the number of bytes between the current {@code readerIndex}
     *         and the first occurrence if found. {@code -1} otherwise.
     * @throws IndexOutOfBoundsException
     *         if {@code length} is greater than {@code this.readableBytes}
     */
    int bytesBefore(int length, byte value);

    /**
     * Locates the first occurrence of the specified {@code value} in this
     * buffer.  The search starts from the specified {@code index} (inclusive)
     * and lasts for the specified {@code length}.
     * <p>
     * This method does not modify {@code readerIndex} or {@code writerIndex} of
     * this buffer.
     *
     * @param index the index.
     * @param length the length.
     * @param value the value to search.
     * @return the number of bytes between the specified {@code index}
     *         and the first occurrence if found. {@code -1} otherwise.
     * @throws IndexOutOfBoundsException
     *         if {@code index + length} is greater than {@code this.capacity}
     */
    int bytesBefore(int index, int length, byte value);

    /**
     * Returns a copy of this buffer's readable bytes.  Modifying the content
     * of the returned buffer or this buffer does not affect each other at all.
     * This method is identical to {@code buf.copy(buf.readerIndex(), buf.readableBytes())}.
     * This method does not modify {@code readerIndex} or {@code writerIndex} of
     * this buffer.
     *
     * @return a copy of the buffer.
     */
    Buffer copy();

    /**
     * Returns a copy of this buffer's sub-region.  Modifying the content of
     * the returned buffer or this buffer does not affect each other at all.
     * This method does not modify {@code readerIndex} or {@code writerIndex} of
     * this buffer.
     *
     * @param index the start index.
     * @param length the length.
     * @return a copy of the buffer.
     */
    Buffer copy(int index, int length);

    /**
     * Returns a slice of this buffer's readable bytes. Modifying the content
     * of the returned buffer or this buffer affects each other's content
     * while they maintain separate indexes.  This method is
     * identical to {@code buf.slice(buf.readerIndex(), buf.readableBytes())}.
     * This method does not modify {@code readerIndex} or {@code writerIndex} of
     * this buffer.
     *
     * @return a sliced buffer.
     */
    Buffer slice();

    /**
     * Returns a slice of this buffer's sub-region. Modifying the content of
     * the returned buffer or this buffer affects each other's content while
     * they maintain separate indexes.
     * This method does not modify {@code readerIndex} or {@code writerIndex} of
     * this buffer.
     *
     * @param index the start index.
     * @param length the length.
     * @return a sliced buffer.
     */
    Buffer slice(int index, int length);

    /**
     * Returns a buffer which shares the whole region of this buffer.
     * Modifying the content of the returned buffer or this buffer affects
     * each other's content while they maintain separate indexes.
     * This method is identical to {@code buf.slice(0, buf.capacity())}.
     * This method does not modify {@code readerIndex} or {@code writerIndex} of
     * this buffer.
     *
     * @return a duplicated buffer.
     */
    Buffer duplicate();

    /**
     * Returns the maximum number of NIO {@link ByteBuffer}s that consist this buffer.  Note that
     * {@link #toNioBuffers()} or {@link #toNioBuffers(int, int)} might return a less number of {@link ByteBuffer}s.
     *
     * @return {@code -1} if this buffer has no underlying {@link ByteBuffer}.
     *         the number of the underlying {@link ByteBuffer}s if this buffer has at least one underlying
     *         {@link ByteBuffer}.  Note that this method does not return {@code 0} to avoid confusion.
     *
     * @see #toNioBuffer()
     * @see #toNioBuffer(int, int)
     * @see #toNioBuffers()
     * @see #toNioBuffers(int, int)
     */
    int nioBufferCount();

    /**
     * Exposes this buffer's readable bytes as an NIO {@link ByteBuffer}.  The returned buffer
     * shares the content with this buffer, while changing the position and limit of the returned
     * NIO buffer does not affect the indexes of this buffer.  This method is identical
     * to {@code buf.nioBuffer(buf.readerIndex(), buf.readableBytes())}.  This method does not
     * modify {@code readerIndex} or {@code writerIndex} of this buffer.  Please note that the
     * returned NIO buffer will not see the changes of this buffer if this buffer is a dynamic
     * buffer and it adjusted its capacity.
     *
     * @return the nio buffer.
     * @throws UnsupportedOperationException
     *         if this buffer cannot create a {@link ByteBuffer} that shares the content with itself
     *
     * @see #nioBufferCount()
     * @see #toNioBuffers()
     * @see #toNioBuffers(int, int)
     */
    ByteBuffer toNioBuffer();

    /**
     * Exposes this buffer's sub-region as an NIO {@link ByteBuffer}.  The returned buffer
     * shares the content with this buffer, while changing the position and limit of the returned
     * NIO buffer does not affect the indexes of this buffer.  This method does not
     * modify {@code readerIndex} or {@code writerIndex} of this buffer.  Please note that the
     * returned NIO buffer will not see the changes of this buffer if this buffer is a dynamic
     * buffer and it adjusted its capacity.
     *
     * @param index the start index.
     * @param length the length.
     * @return the nio buffer.
     * @throws UnsupportedOperationException
     *         if this buffer cannot create a {@link ByteBuffer} that shares the content with itself
     *
     * @see #nioBufferCount()
     * @see #toNioBuffers()
     * @see #toNioBuffers(int, int)
     */
    ByteBuffer toNioBuffer(int index, int length);

    /**
     * Exposes this buffer's readable bytes as an NIO {@link ByteBuffer}'s.  The returned buffer
     * shares the content with this buffer, while changing the position and limit of the returned
     * NIO buffer does not affect the indexes of this buffer. This method does not
     * modify {@code readerIndex} or {@code writerIndex} of this buffer.  Please note that the
     * returned NIO buffer will not see the changes of this buffer if this buffer is a dynamic
     * buffer and it adjusted its capacity.
     *
     *
     * @return the nio buffers.
     * @throws UnsupportedOperationException
     *         if this buffer cannot create a {@link ByteBuffer} that shares the content with itself
     *
     * @see #nioBufferCount()
     * @see #toNioBuffer()
     * @see #toNioBuffer(int, int)
     */
    ByteBuffer[] toNioBuffers();

    /**
     * Exposes this buffer's bytes as an NIO {@link ByteBuffer}'s for the specified index and length
     * The returned buffer shares the content with this buffer, while changing the position and limit
     * of the returned NIO buffer does not affect the indexes of this buffer. This method does
     * not modify {@code readerIndex} or {@code writerIndex} of this buffer.  Please note that the
     * returned NIO buffer will not see the changes of this buffer if this buffer is a dynamic
     * buffer and it adjusted its capacity.
     *
     * @param index the start index.
     * @param length the length.
     * @return the nio buffers.
     * @throws UnsupportedOperationException
     *         if this buffer cannot create a {@link ByteBuffer} that shares the content with itself
     *
     * @see #nioBufferCount()
     * @see #toNioBuffer()
     * @see #toNioBuffer(int, int)
     */
    ByteBuffer[] toNioBuffers(int index, int length);

    /**
     * Determine if this buffer is read-only.
     * @return {@code true} if and only if this buffer is read-only.
     */
    boolean isReadOnly();

    /**
     * Create a read-only view for this buffer.
     * <p>
     * The returned read-only view shares indexes and content with the original {@link Buffer}. All methods that try
     * to change the content will throw {@link ReadOnlyBufferException}. Modifications for indexes are allowed and will
     * be propagated to the original {@link Buffer}. To prevent changes of indexes for the original {@link Buffer} use
     * {@link #duplicate()} before converting to a read-only view.
     *
     * @return a buffer whose contents cannot be modified.
     */
    Buffer asReadOnly();

    /**
     * Returns {@code true} if the buffer is direct and so not allocated on the heap.
     *
     * @return {@code true} if direct.
     */
    boolean isDirect();

    /**
     * Returns {@code true} if and only if this buffer has a backing byte array.
     * If this method returns true, you can safely call {@link #array()} and
     * {@link #arrayOffset()}.
     *
     * @return {@code true} if backed by an byte array and is not read-only
     * @see #array()
     * @see #arrayOffset()
     */
    boolean hasArray();

    /**
     * Returns the backing byte array of this buffer.
     * <p>
     * The caller must check {@link #hasArray()} returns {@code true} before calling this method or
     * {@link UnsupportedOperationException} maybe thrown.
     * <p>
     * Use {@link #arrayOffset()} to get the starting point of data for this buffer. The returned array maybe shared and
     * this {@link Buffer}'s data may reside in a sub-section.
     * @return byte array.
     * @throws UnsupportedOperationException
     *         if there no accessible backing byte array
     * @throws ReadOnlyBufferException if this buffer is read-only
     * @see #hasArray()
     * @see #arrayOffset()
     */
    byte[] array();

    /**
     * Returns the offset of the first byte within the backing byte array of this buffer.
     * <p>
     * The caller must check {@link #hasArray()} returns {@code true} before calling this method or
     * {@link UnsupportedOperationException} maybe thrown.
     *
     * @return the offset in the array.
     * @throws UnsupportedOperationException
     *         if there no accessible backing byte array
     * @throws ReadOnlyBufferException if this buffer is read-only
     * @see #hasArray()
     * @see #array()
     */
    int arrayOffset();

    /**
     * Iterates over the readable bytes of this buffer with the specified {@code processor} in ascending order.
     *
     * @param processor the {@link ByteProcessor} to use.
     * @return {@code -1} if the processor iterated to or beyond the end of the readable bytes.
     *         The last-visited index If the {@link ByteProcessor#process(byte)} returned {@code false}.
     */
    int forEachByte(ByteProcessor processor);

    /**
     * Iterates over the specified area of this buffer with the specified {@code processor} in ascending order.
     * (i.e. {@code index}, {@code (index + 1)},  .. {@code (index + length - 1)})
     *
     * @param index The index to start iterating from.
     * @param length The amount of bytes to iterate over.
     * @param processor the {@link ByteProcessor} to use.
     * @return {@code -1} if the processor iterated to or beyond the end of the specified area.
     *         The last-visited index If the {@link ByteProcessor#process(byte)} returned {@code false}.
     */
    int forEachByte(int index, int length, ByteProcessor processor);

    /**
     * Iterates over the readable bytes of this buffer with the specified {@code processor} in descending order.
     *
     * @param processor the {@link ByteProcessor} to use.
     * @return {@code -1} if the processor iterated to or beyond the beginning of the readable bytes.
     *         The last-visited index If the {@link ByteProcessor#process(byte)} returned {@code false}.
     */
    int forEachByteDesc(ByteProcessor processor);

    /**
     * Iterates over the specified area of this buffer with the specified {@code processor} in descending order.
     * (i.e. {@code (index + length - 1)}, {@code (index + length - 2)}, ... {@code index})
     *
     * @param index The index to start iterating from.
     * @param length The amount of bytes to iterate over.
     * @param processor the {@link ByteProcessor} to use.
     * @return {@code -1} if the processor iterated to or beyond the beginning of the specified area.
     *         The last-visited index If the {@link ByteProcessor#process(byte)} returned {@code false}.
     */
    int forEachByteDesc(int index, int length, ByteProcessor processor);

    /**
     * Returns a hash code which was calculated from the content of this
     * buffer.  If there's a byte array which is
     * {@linkplain #equals(Object) equal to} this array, both arrays should
     * return the same value.
     */
    @Override
    int hashCode();

    /**
     * Determines if the content of the specified buffer is identical to the
     * content of this array.  'Identical' here means:
     * <ul>
     * <li>the size of the contents of the two buffers are same and</li>
     * <li>every single byte of the content of the two buffers are same.</li>
     * </ul>
     * Please note that it does not compare {@link #readerIndex()} nor
     * {@link #writerIndex()}.  This method also returns {@code false} for
     * {@code null} and an object which is not an instance of
     * {@link Buffer} type.
     */
    @Override
    boolean equals(Object obj);

    /**
     * Returns the string representation of this buffer.  This method does not
     * necessarily return the whole content of the buffer but returns
     * the values of the key properties such as {@link #readerIndex()},
     * {@link #writerIndex()} and {@link #capacity()}.
     */
    @Override
    String toString();

    /**
     * Decodes this buffer's readable bytes into a string with the specified
     * character set name. This method is identical to
     * {@code buf.toString(buf.readerIndex(), buf.readableBytes(), charsetName)}.
     * This method does not modify {@code readerIndex} or {@code writerIndex} of
     * this buffer.
     *
     * @param charset the charset to use.
     * @return the string.
     * @throws UnsupportedCharsetException
     *         if the specified character set name is not supported by the
     *         current VM
     */
    String toString(Charset charset);

    /**
     * Decodes this buffer's sub-region into a string with the specified
     * character set. This method does not modify {@code readerIndex} or
     * {@code writerIndex} of this buffer.
     *
     * @param index the start index.
     * @param length the length.
     * @param charset the charset to use.
     * @return the string.
     */
    String toString(int index, int length, Charset charset);

    /**
     * Return an {@link OutputStream} that wraps the given {@link Buffer}. The writerIndex will be increased when
     * writing to the buffer.
     *
     * @param buffer the buffer to wrap.
     * @return a new {@link OutputStream}.
     */
    static OutputStream asOutputStream(Buffer buffer) {
        return new BufferOutputStream(buffer);
    }

    /**
     * Return an {@link InputStream} that wraps the given {@link Buffer}. The readerIndex will be increased when reading
     * from the buffer.
     * @param buffer the buffer to wrap.
     * @return a new {@link InputStream}.
     */
    static InputStream asInputStream(Buffer buffer) {
        return new BufferInputStream(buffer);
    }
}
