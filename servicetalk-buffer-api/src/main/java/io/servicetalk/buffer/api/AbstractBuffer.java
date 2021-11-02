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

import java.nio.ByteBuffer;
import java.nio.charset.Charset;

import static io.servicetalk.buffer.api.EmptyBuffer.EMPTY_BUFFER_HASH_CODE;
import static java.lang.Double.longBitsToDouble;
import static java.lang.Float.intBitsToFloat;
import static java.lang.Short.reverseBytes;

abstract class AbstractBuffer implements Buffer {
    private int readerIndex;
    private int writerIndex;

    AbstractBuffer(int readerIndex, int writerIndex) {
        this.readerIndex = readerIndex;
        this.writerIndex = writerIndex;
    }

    @Override
    public final int readerIndex() {
        return readerIndex;
    }

    @Override
    public final Buffer readerIndex(int readerIndex) {
        if (readerIndex < 0 || readerIndex > writerIndex) {
            throw new IndexOutOfBoundsException("readerIndex must be in the range[0," + writerIndex + ") but was: " +
                    readerIndex);
        }
        this.readerIndex = readerIndex;
        return this;
    }

    @Override
    public final int writerIndex() {
        return writerIndex;
    }

    @Override
    public final Buffer writerIndex(int writerIndex) {
        if (writerIndex < readerIndex || writerIndex > capacity()) {
            throw new IndexOutOfBoundsException("writerIndex must be in the range[" + readerIndex + "," +
                    capacity() + "] but was: " + writerIndex);
        }
        this.writerIndex = writerIndex;
        return this;
    }

    @Override
    public final int readableBytes() {
        return writerIndex - readerIndex;
    }

    @Override
    public final Buffer clear() {
        writerIndex = readerIndex = 0;
        return this;
    }

    @Override
    public final byte getByte(int index) {
        checkIndex0(index, 1);
        return getByteNoBounds(index);
    }

    protected abstract byte getByteNoBounds(int index);

    @Override
    public final boolean getBoolean(int index) {
        return getByte(index) != 0;
    }

    @Override
    public final short getShort(int index) {
        checkIndex0(index, 2);
        return getShortNoBounds(index);
    }

    protected abstract short getShortNoBounds(int index);

    @Override
    public final char getChar(int index) {
        return (char) getShort(index);
    }

    @Override
    public final short getUnsignedByte(int index) {
        return (short) (getByte(index) & 0xff);
    }

    @Override
    public final short getShortLE(int index) {
        checkIndex0(index, 2);
        return getShortLENoBounds(index);
    }

    protected short getShortLENoBounds(int index) {
        return reverseBytes(getShortNoBounds(index));
    }

    @Override
    public final int getUnsignedShort(int index) {
        return getShort(index) & 0xfff;
    }

    @Override
    public final int getUnsignedShortLE(int index) {
        return getShortLE(index) & 0xffff;
    }

    @Override
    public final int getUnsignedMedium(int index) {
        checkIndex0(index, 3);
        return getUnsignedMediumNoBounds(index);
    }

    protected int getUnsignedMediumNoBounds(int index) {
        return (getByteNoBounds(index) & 0xff) << 16 |
                (getByteNoBounds(index + 1) & 0xff) << 8 |
                getByteNoBounds(index + 2) & 0xff;
    }

    @Override
    public int getUnsignedMediumLE(int index) {
        checkIndex0(index, 3);
        return getUnsignedMediumLENoBounds(index);
    }

    protected int getUnsignedMediumLENoBounds(int index) {
        return getByteNoBounds(index) & 0xff |
                (getByteNoBounds(index + 1) & 0xff) << 8 |
                (getByteNoBounds(index + 2) & 0xff) << 16;
    }

    @Override
    public final int getMedium(int index) {
        int value = getUnsignedMedium(index);
        if ((value & 0x800000) != 0) {
            value |= 0xff000000;
        }
        return value;
    }

    @Override
    public final int getMediumLE(int index) {
        int value = getUnsignedMediumLE(index);
        if ((value & 0x800000) != 0) {
            value |= 0xff000000;
        }
        return value;
    }

    @Override
    public final int getInt(int index) {
        checkIndex0(index, 4);
        return getIntNoBounds(index);
    }

    protected abstract int getIntNoBounds(int index);

    @Override
    public final int getIntLE(int index) {
        checkIndex0(index, 4);
        return getIntLENoBounds(index);
    }

    protected final int getIntLENoBounds(int index) {
        return Integer.reverseBytes(getIntNoBounds(index));
    }

    @Override
    public final long getUnsignedInt(int index) {
        return getInt(index) & 0xffffffffL;
    }

    @Override
    public final long getUnsignedIntLE(int index) {
        return getIntLE(index) & 0xffffffffL;
    }

    @Override
    public final long getLong(int index) {
        checkIndex0(index, 8);
        return getLongNoBounds(index);
    }

    protected abstract long getLongNoBounds(int index);

    @Override
    public final long getLongLE(int index) {
        checkIndex0(index, 8);
        return getLongLENoBounds(index);
    }

    protected final long getLongLENoBounds(int index) {
        return Long.reverseBytes(getLongNoBounds(index));
    }

    @Override
    public final float getFloat(int index) {
        return intBitsToFloat(getInt(index));
    }

    @Override
    public final double getDouble(int index) {
        return longBitsToDouble(getLong(index));
    }

    @Override
    public final Buffer getBytes(int index, Buffer dst) {
        getBytes(index, dst, dst.writableBytes());
        return this;
    }

    @Override
    public final Buffer getBytes(int index, Buffer dst, int length) {
        getBytes(index, dst, dst.writerIndex(), length);
        dst.writerIndex(dst.writerIndex() + length);
        return this;
    }

    @Override
    public final Buffer getBytes(int index, byte[] dst) {
        getBytes(index, dst, 0, dst.length);
        return this;
    }

    @Override
    public final byte readByte() {
        checkReadableBytes0(1);
        int i = readerIndex;
        byte b = getByteNoBounds(i);
        readerIndex = i + 1;
        return b;
    }

    @Override
    public final boolean readBoolean() {
        return readByte() != 0;
    }

    @Override
    public final short readUnsignedByte() {
        return (short) (readByte() & 0xff);
    }

    @Override
    public final short readShort() {
        checkReadableBytes0(2);
        short v = getShortNoBounds(readerIndex);
        readerIndex += 2;
        return v;
    }

    @Override
    public final char readChar() {
        return (char) readShort();
    }

    @Override
    public final short readShortLE() {
        checkReadableBytes0(2);
        short v = getShortLENoBounds(readerIndex);
        readerIndex += 2;
        return v;
    }

    @Override
    public final int readUnsignedShort() {
        return readShort() & 0xffff;
    }

    @Override
    public final int readUnsignedShortLE() {
        return readShortLE() & 0xffff;
    }

    @Override
    public final int readMedium() {
        int value = readUnsignedMedium();
        if ((value & 0x800000) != 0) {
            value |= 0xff000000;
        }
        return value;
    }

    @Override
    public final int readMediumLE() {
        int value = readUnsignedMediumLE();
        if ((value & 0x800000) != 0) {
            value |= 0xff000000;
        }
        return value;
    }

    @Override
    public final int readUnsignedMedium() {
        checkReadableBytes0(3);
        int v = getUnsignedMediumNoBounds(readerIndex);
        readerIndex += 3;
        return v;
    }

    @Override
    public final int readUnsignedMediumLE() {
        checkReadableBytes0(3);
        int v = getUnsignedMediumLENoBounds(readerIndex);
        readerIndex += 3;
        return v;
    }

    @Override
    public final int readInt() {
        checkReadableBytes0(4);
        int v = getIntNoBounds(readerIndex);
        readerIndex += 4;
        return v;
    }

    @Override
    public final int readIntLE() {
        checkReadableBytes0(4);
        int v = getIntLENoBounds(readerIndex);
        readerIndex += 4;
        return v;
    }

    @Override
    public final long readUnsignedInt() {
        return readInt() & 0xffffffffL;
    }

    @Override
    public final long readUnsignedIntLE() {
        return readIntLE() & 0xffffffffL;
    }

    @Override
    public final long readLong() {
        checkReadableBytes0(8);
        long v = getLongNoBounds(readerIndex);
        readerIndex += 8;
        return v;
    }

    @Override
    public final long readLongLE() {
        checkReadableBytes0(8);
        long v = getLongLENoBounds(readerIndex);
        readerIndex += 8;
        return v;
    }

    @Override
    public final float readFloat() {
        return intBitsToFloat(readInt());
    }

    @Override
    public final double readDouble() {
        return longBitsToDouble(readLong());
    }

    @Override
    public final Buffer readBytes(byte[] dst, int dstIndex, int length) {
        checkReadableBytes0(length);
        getBytes(readerIndex, dst, dstIndex, length);
        readerIndex += length;
        return this;
    }

    @Override
    public final Buffer readBytes(byte[] dst) {
        readBytes(dst, 0, dst.length);
        return this;
    }

    @Override
    public final Buffer readBytes(Buffer dst) {
        readBytes(dst, dst.writableBytes());
        return this;
    }

    @Override
    public final Buffer readBytes(Buffer dst, int length) {
        if (length > dst.writableBytes()) {
            throw new IndexOutOfBoundsException(String.format(
                    "length(%d) exceeds dst.writableBytes(%d) where dst is: %s", length, dst.writableBytes(), dst));
        }
        readBytes(dst, dst.writerIndex(), length);
        dst.writerIndex(dst.writerIndex() + length);
        return this;
    }

    @Override
    public final Buffer readBytes(Buffer dst, int dstIndex, int length) {
        checkReadableBytes0(length);
        getBytes(readerIndex, dst, dstIndex, length);
        readerIndex += length;
        return this;
    }

    @Override
    public final Buffer readBytes(ByteBuffer dst) {
        int length = dst.remaining();
        checkReadableBytes0(length);
        getBytes(readerIndex, dst);
        readerIndex += length;
        return this;
    }

    @Override
    public final Buffer skipBytes(int length) {
        checkReadableBytes0(length);
        skipBytes0(length);
        return this;
    }

    final void skipBytes0(int length) {
        readerIndex += length;
    }

    @Override
    public final int bytesBefore(byte value) {
        return bytesBefore(readerIndex, readableBytes(), value);
    }

    @Override
    public final int bytesBefore(int length, byte value) {
        checkReadableBytes0(length);
        return bytesBefore(readerIndex, length, value);
    }

    @Override
    public final int bytesBefore(int index, int length, byte value) {
        int endIndex = indexOf(index, index + length, value);
        if (endIndex < 0) {
            return -1;
        }
        return endIndex - index;
    }

    @Override
    public final int forEachByte(ByteProcessor processor) {
        return forEachByteAsc0(readerIndex, writerIndex, processor);
    }

    @Override
    public final int forEachByte(int index, int length, ByteProcessor processor) {
        checkIndex0(index, length);
        return forEachByteAsc0(index, index + length, processor);
    }

    private int forEachByteAsc0(int start, int end, ByteProcessor processor) {
        for (; start < end; ++start) {
            if (!processor.process(getByteNoBounds(start))) {
                return start;
            }
        }

        return -1;
    }

    @Override
    public final int forEachByteDesc(ByteProcessor processor) {
        return forEachByteDesc0(writerIndex - 1, readerIndex, processor);
    }

    @Override
    public final int forEachByteDesc(int index, int length, ByteProcessor processor) {
        checkIndex0(index, length);
        return forEachByteDesc0(index + length - 1, index, processor);
    }

    @Override
    public final Buffer copy() {
        return copy(readerIndex, readableBytes());
    }

    @Override
    public final Buffer slice() {
        return slice(readerIndex, readableBytes());
    }

    private int forEachByteDesc0(int rStart, final int rEnd, ByteProcessor processor) {
        for (; rStart >= rEnd; --rStart) {
            if (!processor.process(getByteNoBounds(rStart))) {
                return rStart;
            }
        }
        return -1;
    }

    @Override
    public final int indexOf(int fromIndex, int toIndex, byte value) {
        return fromIndex < toIndex ? firstIndexOf(fromIndex, toIndex, value) :
                lastIndexOf(fromIndex, toIndex, value);
    }

    protected int firstIndexOf(int fromIndex, int toIndex, byte value) {
        return forEachByte(fromIndex, toIndex - fromIndex, new IndexOfByteProcessor(value));
    }

    protected int lastIndexOf(int fromIndex, int toIndex, byte value) {
        return forEachByteDesc(toIndex, fromIndex - toIndex, new IndexOfByteProcessor(value));
    }

    @Override
    public ByteBuffer toNioBuffer() {
        return toNioBuffer(readerIndex, readableBytes());
    }

    @Override
    public final ByteBuffer[] toNioBuffers() {
        return toNioBuffers(readerIndex, readableBytes());
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Buffer)) {
            return false;
        }
        if (o == this) {
            return true;
        }

        Buffer that = (Buffer) o;
        final int readableBytes = readableBytes();
        if (readableBytes != that.readableBytes()) {
            return false;
        }

        int aStartIndex = readerIndex();
        int bStartIndex = that.readerIndex();
        final int longCount = readableBytes >>> 3;
        final int byteCount = readableBytes & 7;
        // TODO(scott): take into account endianness? we currently don't have order() on Buffer.
        for (int i = longCount; i > 0; --i) {
            if (getLong(aStartIndex) != that.getLong(bStartIndex)) {
                return false;
            }
            aStartIndex += 8;
            bStartIndex += 8;
        }

        for (int i = byteCount; i > 0; --i) {
            if (getByte(aStartIndex) != that.getByte(bStartIndex)) {
                return false;
            }
            ++aStartIndex;
            ++bStartIndex;
        }

        return true;
    }

    @Override
    public int hashCode() {
        final int aLen = readableBytes();
        final int longCount = aLen >>> 3;
        final int byteCount = aLen & 3;

        int hashCode = EMPTY_BUFFER_HASH_CODE;
        int arrayIndex = readerIndex;
        // TODO(scott): take into account endianness? we currently don't have order() on Buffer.
        for (int i = longCount; i > 0; --i) {
            hashCode = 31 * hashCode + Long.hashCode(getLong(arrayIndex));
            arrayIndex += 8;
        }

        for (int i = byteCount; i > 0; --i) {
            hashCode = 31 * hashCode + getByte(arrayIndex++);
        }

        if (hashCode == 0) {
            hashCode = 1;
        }

        return hashCode;
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder()
                .append(getClass().getSimpleName())
                .append("(ridx: ").append(readerIndex)
                .append(", widx: ").append(writerIndex)
                .append(", cap: ").append(capacity());
        if (maxCapacity() != Integer.MAX_VALUE) {
            buf.append('/').append(maxCapacity());
        }

        buf.append(')');
        return buf.toString();
    }

    @Override
    public final String toString(Charset charset) {
        return toString(readerIndex, readableBytes(), charset);
    }

    final void checkReadableBytes0(int minimumReadableBytes) {
        if (readerIndex > writerIndex - minimumReadableBytes) {
            throw new IndexOutOfBoundsException(String.format(
                    "readerIndex(%d) + length(%d) exceeds writerIndex(%d): %s",
                    readerIndex, minimumReadableBytes, writerIndex, this));
        }
    }

    final void checkIndex0(int index, int fieldLength) {
        if (isOutOfBounds(index, fieldLength, capacity())) {
            throw new IndexOutOfBoundsException(String.format(
                    "index: %d, length: %d (expected: range(0, %d))", index, fieldLength, capacity()));
        }
    }

    final void checkDstIndex(int index, int length, int dstIndex, int dstCapacity) {
        checkIndex0(index, length);
        if (isOutOfBounds(dstIndex, length, dstCapacity)) {
            throw new IndexOutOfBoundsException(String.format(
                    "dstIndex: %d, length: %d (expected: range(0, %d))", dstIndex, length, dstCapacity));
        }
    }

    /**
     * Determine if the requested {@code index} and {@code length} will fit within {@code capacity}.
     * @param index The starting index.
     * @param length The length which will be utilized (starting from {@code index}).
     * @param capacity The capacity that {@code index + length} is allowed to be within.
     * @return {@code true} if the requested {@code index} and {@code length} will fit within {@code capacity}.
     * {@code false} if this would result in an index out of bounds exception.
     */
    static boolean isOutOfBounds(int index, int length, int capacity) {
        return (index | length | (index + length) | (capacity - (index + length))) < 0;
    }
}
