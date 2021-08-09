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

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ReadOnlyBufferException;
import java.nio.charset.Charset;

/**
 * An immutable zero capacity {@link Buffer}.
 */
public final class EmptyBuffer implements Buffer {

    /**
     * An instance of {@link EmptyBuffer} that can be shared.
     */
    public static final EmptyBuffer EMPTY_BUFFER = new EmptyBuffer();

    private static final ByteBuffer EMPTY_BYTE_BUFFER = ByteBuffer.allocateDirect(0);
    private static final byte[] EMPTY_BYTES = {};

    private EmptyBuffer() {
        // No instances, use the static instance.
    }

    @Override
    public int capacity() {
        return 0;
    }

    @Override
    public Buffer capacity(int newCapacity) {
        throw new ReadOnlyBufferException();
    }

    @Override
    public int maxCapacity() {
        return 0;
    }

    @Override
    public int readerIndex() {
        return 0;
    }

    @Override
    public Buffer readerIndex(int readerIndex) {
        checkIndex(readerIndex);
        return this;
    }

    @Override
    public int writerIndex() {
        return 0;
    }

    @Override
    public Buffer writerIndex(int writerIndex) {
        checkIndex(writerIndex);
        return this;
    }

    @Override
    public int readableBytes() {
        return 0;
    }

    @Override
    public int writableBytes() {
        return 0;
    }

    @Override
    public int maxWritableBytes() {
        return 0;
    }

    @Override
    public Buffer ensureWritable(int minWritableBytes) {
        if (minWritableBytes < 0) {
            throw new IllegalArgumentException("minWritableBytes: " + minWritableBytes + " (expected: >= 0)");
        }
        if (minWritableBytes != 0) {
            throw new IndexOutOfBoundsException();
        }
        return this;
    }

    @Override
    public int ensureWritable(int minWritableBytes, boolean force) {
        if (minWritableBytes < 0) {
            throw new IllegalArgumentException("minWritableBytes: " + minWritableBytes + " (expected: >= 0)");
        }

        return minWritableBytes == 0 ? 0 : 1;
    }

    @Override
    public Buffer clear() {
        return this;
    }

    @Override
    public boolean getBoolean(int index) {
        throw new IndexOutOfBoundsException();
    }

    @Override
    public byte getByte(int index) {
        throw new IndexOutOfBoundsException();
    }

    @Override
    public short getUnsignedByte(int index) {
        throw new IndexOutOfBoundsException();
    }

    @Override
    public short getShort(int index) {
        throw new IndexOutOfBoundsException();
    }

    @Override
    public short getShortLE(int index) {
        throw new IndexOutOfBoundsException();
    }

    @Override
    public int getUnsignedShort(int index) {
        throw new IndexOutOfBoundsException();
    }

    @Override
    public int getUnsignedShortLE(int index) {
        throw new IndexOutOfBoundsException();
    }

    @Override
    public int getMedium(int index) {
        throw new IndexOutOfBoundsException();
    }

    @Override
    public int getMediumLE(int index) {
        throw new IndexOutOfBoundsException();
    }

    @Override
    public int getUnsignedMedium(int index) {
        throw new IndexOutOfBoundsException();
    }

    @Override
    public int getUnsignedMediumLE(int index) {
        throw new IndexOutOfBoundsException();
    }

    @Override
    public int getInt(int index) {
        throw new IndexOutOfBoundsException();
    }

    @Override
    public int getIntLE(int index) {
        throw new IndexOutOfBoundsException();
    }

    @Override
    public long getUnsignedInt(int index) {
        throw new IndexOutOfBoundsException();
    }

    @Override
    public long getUnsignedIntLE(int index) {
        throw new IndexOutOfBoundsException();
    }

    @Override
    public long getLong(int index) {
        throw new IndexOutOfBoundsException();
    }

    @Override
    public long getLongLE(int index) {
        throw new IndexOutOfBoundsException();
    }

    @Override
    public char getChar(int index) {
        throw new IndexOutOfBoundsException();
    }

    @Override
    public float getFloat(int index) {
        throw new IndexOutOfBoundsException();
    }

    @Override
    public double getDouble(int index) {
        throw new IndexOutOfBoundsException();
    }

    @Override
    public Buffer getBytes(int index, Buffer dst) {
        checkIndex(index);
        return this;
    }

    @Override
    public Buffer getBytes(int index, Buffer dst, int length) {
        checkIndex(index, length);
        return this;
    }

    @Override
    public Buffer getBytes(int index, Buffer dst, int dstIndex, int length) {
        checkIndex(index, length);
        return this;
    }

    @Override
    public Buffer getBytes(int index, byte[] dst) {
        checkIndex(index);
        return this;
    }

    @Override
    public Buffer getBytes(int index, byte[] dst, int dstIndex, int length) {
        checkIndex(index, length);
        return this;
    }

    @Override
    public Buffer getBytes(int index, ByteBuffer dst) {
        checkIndex(index);
        return this;
    }

    @Override
    public Buffer setBoolean(int index, boolean value) {
        throw new IndexOutOfBoundsException();
    }

    @Override
    public Buffer setByte(int index, int value) {
        throw new IndexOutOfBoundsException();
    }

    @Override
    public Buffer setShort(int index, int value) {
        throw new IndexOutOfBoundsException();
    }

    @Override
    public Buffer setShortLE(int index, int value) {
        throw new IndexOutOfBoundsException();
    }

    @Override
    public Buffer setMedium(int index, int value) {
        throw new IndexOutOfBoundsException();
    }

    @Override
    public Buffer setMediumLE(int index, int value) {
        throw new IndexOutOfBoundsException();
    }

    @Override
    public Buffer setInt(int index, int value) {
        throw new IndexOutOfBoundsException();
    }

    @Override
    public Buffer setIntLE(int index, int value) {
        throw new IndexOutOfBoundsException();
    }

    @Override
    public Buffer setLong(int index, long value) {
        throw new IndexOutOfBoundsException();
    }

    @Override
    public Buffer setLongLE(int index, long value) {
        throw new IndexOutOfBoundsException();
    }

    @Override
    public Buffer setChar(int index, int value) {
        throw new IndexOutOfBoundsException();
    }

    @Override
    public Buffer setFloat(int index, float value) {
        throw new IndexOutOfBoundsException();
    }

    @Override
    public Buffer setDouble(int index, double value) {
        throw new IndexOutOfBoundsException();
    }

    @Override
    public Buffer setBytes(int index, Buffer src) {
        checkIndex(index, src.readableBytes());
        return this;
    }

    @Override
    public Buffer setBytes(int index, Buffer src, int length) {
        checkIndex(index, length);
        return this;
    }

    @Override
    public Buffer setBytes(int index, Buffer src, int srcIndex, int length) {
        checkIndex(index, length);
        return this;
    }

    @Override
    public Buffer setBytes(int index, byte[] src) {
        checkIndex(index, src.length);
        return this;
    }

    @Override
    public Buffer setBytes(int index, byte[] src, int srcIndex, int length) {
        checkIndex(index, length);
        return this;
    }

    @Override
    public Buffer setBytes(int index, ByteBuffer src) {
        checkIndex(index, src.remaining());
        return this;
    }

    @Override
    public int setBytes(int index, InputStream src, int length) throws IOException {
        checkIndex(index, length);
        return 0;
    }

    @Override
    public int setBytesUntilEndStream(int index, InputStream src, int chunkSize) {
        throw new IndexOutOfBoundsException();
    }

    @Override
    public boolean readBoolean() {
        throw new IndexOutOfBoundsException();
    }

    @Override
    public byte readByte() {
        throw new IndexOutOfBoundsException();
    }

    @Override
    public short readUnsignedByte() {
        throw new IndexOutOfBoundsException();
    }

    @Override
    public short readShort() {
        throw new IndexOutOfBoundsException();
    }

    @Override
    public short readShortLE() {
        throw new IndexOutOfBoundsException();
    }

    @Override
    public int readUnsignedShort() {
        throw new IndexOutOfBoundsException();
    }

    @Override
    public int readUnsignedShortLE() {
        throw new IndexOutOfBoundsException();
    }

    @Override
    public int readMedium() {
        throw new IndexOutOfBoundsException();
    }

    @Override
    public int readMediumLE() {
        throw new IndexOutOfBoundsException();
    }

    @Override
    public int readUnsignedMedium() {
        throw new IndexOutOfBoundsException();
    }

    @Override
    public int readUnsignedMediumLE() {
        throw new IndexOutOfBoundsException();
    }

    @Override
    public int readInt() {
        throw new IndexOutOfBoundsException();
    }

    @Override
    public int readIntLE() {
        throw new IndexOutOfBoundsException();
    }

    @Override
    public long readUnsignedInt() {
        throw new IndexOutOfBoundsException();
    }

    @Override
    public long readUnsignedIntLE() {
        throw new IndexOutOfBoundsException();
    }

    @Override
    public long readLong() {
        throw new IndexOutOfBoundsException();
    }

    @Override
    public long readLongLE() {
        throw new IndexOutOfBoundsException();
    }

    @Override
    public char readChar() {
        throw new IndexOutOfBoundsException();
    }

    @Override
    public float readFloat() {
        throw new IndexOutOfBoundsException();
    }

    @Override
    public double readDouble() {
        throw new IndexOutOfBoundsException();
    }

    @Override
    public Buffer readSlice(int length) {
        checkLength(length);
        return this;
    }

    @Override
    public Buffer readBytes(int length) {
        checkLength(length);
        return this;
    }

    @Override
    public Buffer readBytes(Buffer dst) {
        checkLength(dst.writableBytes());
        return this;
    }

    @Override
    public Buffer readBytes(Buffer dst, int length) {
        checkLength(length);
        return this;
    }

    @Override
    public Buffer readBytes(Buffer dst, int dstIndex, int length) {
        checkLength(length);
        return this;
    }

    @Override
    public Buffer readBytes(byte[] dst) {
        checkLength(dst.length);
        return this;
    }

    @Override
    public Buffer readBytes(byte[] dst, int dstIndex, int length) {
        checkLength(length);
        return this;
    }

    @Override
    public Buffer readBytes(ByteBuffer dst) {
        checkLength(dst.remaining());
        return this;
    }

    @Override
    public Buffer skipBytes(int length) {
        checkLength(length);
        return this;
    }

    @Override
    public Buffer writeBoolean(boolean value) {
        throw new IndexOutOfBoundsException();
    }

    @Override
    public Buffer writeByte(int value) {
        throw new IndexOutOfBoundsException();
    }

    @Override
    public Buffer writeShort(int value) {
        throw new IndexOutOfBoundsException();
    }

    @Override
    public Buffer writeShortLE(int value) {
        throw new IndexOutOfBoundsException();
    }

    @Override
    public Buffer writeMedium(int value) {
        throw new IndexOutOfBoundsException();
    }

    @Override
    public Buffer writeMediumLE(int value) {
        throw new IndexOutOfBoundsException();
    }

    @Override
    public Buffer writeInt(int value) {
        throw new IndexOutOfBoundsException();
    }

    @Override
    public Buffer writeIntLE(int value) {
        throw new IndexOutOfBoundsException();
    }

    @Override
    public Buffer writeLong(long value) {
        throw new IndexOutOfBoundsException();
    }

    @Override
    public Buffer writeLongLE(long value) {
        throw new IndexOutOfBoundsException();
    }

    @Override
    public Buffer writeChar(int value) {
        throw new IndexOutOfBoundsException();
    }

    @Override
    public Buffer writeFloat(float value) {
        throw new IndexOutOfBoundsException();
    }

    @Override
    public Buffer writeDouble(double value) {
        throw new IndexOutOfBoundsException();
    }

    @Override
    public Buffer writeBytes(Buffer src) {
        checkLength(src.readableBytes());
        return this;
    }

    @Override
    public Buffer writeBytes(Buffer src, int length) {
        checkLength(length);
        return this;
    }

    @Override
    public Buffer writeBytes(Buffer src, int srcIndex, int length) {
        checkLength(length);
        return this;
    }

    @Override
    public Buffer writeBytes(byte[] src) {
        checkLength(src.length);
        return this;
    }

    @Override
    public Buffer writeBytes(byte[] src, int srcIndex, int length) {
        checkLength(length);
        return this;
    }

    @Override
    public Buffer writeBytes(ByteBuffer src) {
        checkLength(src.remaining());
        return this;
    }

    @Override
    public int writeBytes(InputStream src, int length) {
        checkLength(length);
        return 0;
    }

    @Override
    public int writeBytesUntilEndStream(InputStream src, int chunkSize) {
        throw new IndexOutOfBoundsException();
    }

    @Override
    public Buffer writeAscii(CharSequence seq) {
        throw new IndexOutOfBoundsException();
    }

    @Override
    public Buffer writeUtf8(CharSequence seq) {
        throw new IndexOutOfBoundsException();
    }

    @Override
    public Buffer writeUtf8(CharSequence seq, int ensureWritable) {
        throw new IndexOutOfBoundsException();
    }

    @Override
    public Buffer writeCharSequence(CharSequence seq, Charset charset) {
        throw new IndexOutOfBoundsException();
    }

    @Override
    public int indexOf(int fromIndex, int toIndex, byte value) {
        checkIndex(fromIndex);
        checkIndex(toIndex);
        return -1;
    }

    @Override
    public int bytesBefore(byte value) {
        return -1;
    }

    @Override
    public int bytesBefore(int length, byte value) {
        checkIndex(length);
        return -1;
    }

    @Override
    public int bytesBefore(int index, int length, byte value) {
        checkIndex(length);
        return -1;
    }

    @Override
    public Buffer copy() {
        return this;
    }

    @Override
    public Buffer copy(int index, int length) {
        checkIndex(index, length);
        return this;
    }

    @Override
    public Buffer slice() {
        return this;
    }

    @Override
    public Buffer slice(int index, int length) {
        checkIndex(index, length);
        return this;
    }

    @Override
    public Buffer duplicate() {
        return this;
    }

    @Override
    public int nioBufferCount() {
        return 1;
    }

    @Override
    public ByteBuffer toNioBuffer() {
        return EMPTY_BYTE_BUFFER;
    }

    @Override
    public ByteBuffer toNioBuffer(int index, int length) {
        checkIndex(index, length);
        return toNioBuffer();
    }

    @Override
    public ByteBuffer[] toNioBuffers() {
        // Instantiate a new array for each call since arrays are mutable.
        return new ByteBuffer[]{EMPTY_BYTE_BUFFER};
    }

    @Override
    public ByteBuffer[] toNioBuffers(int index, int length) {
        checkIndex(index, length);
        return toNioBuffers();
    }

    @Override
    public boolean isReadOnly() {
        return true;
    }

    @Override
    public Buffer asReadOnly() {
        return this;
    }

    @Override
    public boolean isDirect() {
        return true;
    }

    @Override
    public boolean hasArray() {
        return true;
    }

    @Override
    public byte[] array() {
        return EMPTY_BYTES;
    }

    @Override
    public int arrayOffset() {
        return 0;
    }

    @Override
    public String toString(Charset charset) {
        return "";
    }

    @Override
    public String toString(int index, int length, Charset charset) {
        checkIndex(index, length);
        return toString(charset);
    }

    @Override
    public int forEachByte(ByteProcessor processor) {
        return -1;
    }

    @Override
    public int forEachByte(int index, int length, ByteProcessor processor) {
        checkIndex(index, length);
        return -1;
    }

    @Override
    public int forEachByteDesc(ByteProcessor processor) {
        return -1;
    }

    @Override
    public int forEachByteDesc(int index, int length, ByteProcessor processor) {
        checkIndex(index, length);
        return -1;
    }

    private static void checkIndex(int index) {
        if (index != 0) {
            throw new IndexOutOfBoundsException("index: " + index + " (expected: 0)");
        }
    }

    private static void checkIndex(int index, int length) {
        checkIndex(index);
        checkLength(length);
        // allow empty to empty transfer!
    }

    private static void checkLength(int length) {
        if (length < 0) {
            throw new IllegalArgumentException("length: " + length + " (expected: 0)");
        }
        if (length != 0) {
            throw new IndexOutOfBoundsException("length: " + length + " (expected: 0)");
        }
    }
}
