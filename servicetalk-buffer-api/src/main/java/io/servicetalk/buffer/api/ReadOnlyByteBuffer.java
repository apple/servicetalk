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
 * Copyright 2013 The Netty Project
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

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ReadOnlyBufferException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;

import static io.servicetalk.buffer.api.EmptyBuffer.EMPTY_BUFFER;
import static java.nio.ByteBuffer.allocate;
import static java.nio.ByteBuffer.allocateDirect;

final class ReadOnlyByteBuffer extends AbstractBuffer {
    private final ByteBuffer buffer;

    private ReadOnlyByteBuffer(ByteBuffer buffer) {
        super(buffer.position(), buffer.limit());
        this.buffer = buffer;
    }

    static ReadOnlyByteBuffer newBuffer(ByteBuffer buffer) {
        return new ReadOnlyByteBuffer(buffer.isReadOnly() ? buffer : buffer.asReadOnlyBuffer());
    }

    static ReadOnlyByteBuffer newBufferFromModifiable(ByteBuffer buffer) {
        return new ReadOnlyByteBuffer(buffer.asReadOnlyBuffer());
    }

    @Override
    public int capacity() {
        return buffer.capacity();
    }

    @Override
    public Buffer capacity(int newCapacity) {
        throw new ReadOnlyBufferException();
    }

    @Override
    public int maxCapacity() {
        return capacity();
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
        throw new ReadOnlyBufferException();
    }

    @Override
    public int ensureWritable(int minWritableBytes, boolean force) {
        return minWritableBytes == 0 ? 0 : 1;
    }

    @Override
    protected byte getByteNoBounds(int index) {
        return buffer.get(index);
    }

    @Override
    protected short getShortNoBounds(int index) {
        return buffer.getShort(index);
    }

    @Override
    protected int getIntNoBounds(int index) {
        return buffer.getInt(index);
    }

    @Override
    protected long getLongNoBounds(int index) {
        return buffer.getLong(index);
    }

    @Override
    public Buffer getBytes(int index, Buffer dst, int dstIndex, int length) {
        checkDstIndex(index, length, dstIndex, dst.capacity());
        if (dst.hasArray()) {
            getBytes(index, dst.array(), dst.arrayOffset() + dstIndex, length);
        } else if (dst.nioBufferCount() > 0) {
            for (ByteBuffer bb : dst.toNioBuffers(dstIndex, length)) {
                int bbLen = bb.remaining();
                getBytes(index, bb);
                index += bbLen;
            }
        } else {
            dst.setBytes(dstIndex, this, index, length);
        }
        return this;
    }

    @Override
    public Buffer getBytes(int index, byte[] dst, int dstIndex, int length) {
        checkDstIndex(index, length, dstIndex, dst.length);

        if (dstIndex < 0 || dstIndex > dst.length - length) {
            throw new IndexOutOfBoundsException(String.format(
                    "dstIndex: %d, length: %d (expected: range(0, %d))", dstIndex, length, dst.length));
        }

        ByteBuffer tmpBuf = buffer.duplicate();
        tmpBuf.position(index).limit(index + length);
        tmpBuf.get(dst, dstIndex, length);
        return this;
    }

    @Override
    public Buffer getBytes(int index, ByteBuffer dst) {
        checkIndex0(index, dst.capacity());
        if (dst == null) {
            throw new NullPointerException("dst");
        }

        int bytesToCopy = Math.min(capacity() - index, dst.remaining());
        ByteBuffer tmpBuf = buffer.duplicate();
        tmpBuf.position(index).limit(index + bytesToCopy);
        dst.put(tmpBuf);
        return this;
    }

    @Override
    public Buffer setBoolean(int index, boolean value) {
        throw new ReadOnlyBufferException();
    }

    @Override
    public Buffer setByte(int index, int value) {
        throw new ReadOnlyBufferException();
    }

    @Override
    public Buffer setShort(int index, int value) {
        throw new ReadOnlyBufferException();
    }

    @Override
    public Buffer setShortLE(int index, int value) {
        throw new ReadOnlyBufferException();
    }

    @Override
    public Buffer setMedium(int index, int value) {
        throw new ReadOnlyBufferException();
    }

    @Override
    public Buffer setMediumLE(int index, int value) {
        throw new ReadOnlyBufferException();
    }

    @Override
    public Buffer setInt(int index, int value) {
        throw new ReadOnlyBufferException();
    }

    @Override
    public Buffer setIntLE(int index, int value) {
        throw new ReadOnlyBufferException();
    }

    @Override
    public Buffer setLong(int index, long value) {
        throw new ReadOnlyBufferException();
    }

    @Override
    public Buffer setLongLE(int index, long value) {
        throw new ReadOnlyBufferException();
    }

    @Override
    public Buffer setChar(int index, int value) {
        throw new ReadOnlyBufferException();
    }

    @Override
    public Buffer setFloat(int index, float value) {
        throw new ReadOnlyBufferException();
    }

    @Override
    public Buffer setDouble(int index, double value) {
        throw new ReadOnlyBufferException();
    }

    @Override
    public Buffer setBytes(int index, Buffer src) {
        throw new ReadOnlyBufferException();
    }

    @Override
    public Buffer setBytes(int index, Buffer src, int length) {
        throw new ReadOnlyBufferException();
    }

    @Override
    public Buffer setBytes(int index, Buffer src, int srcIndex, int length) {
        throw new ReadOnlyBufferException();
    }

    @Override
    public Buffer setBytes(int index, byte[] src) {
        throw new ReadOnlyBufferException();
    }

    @Override
    public Buffer setBytes(int index, byte[] src, int srcIndex, int length) {
        throw new ReadOnlyBufferException();
    }

    @Override
    public Buffer setBytes(int index, ByteBuffer src) {
        throw new ReadOnlyBufferException();
    }

    @Override
    public int setBytes(int index, InputStream src, int length) {
        throw new ReadOnlyBufferException();
    }

    @Override
    public int setBytesUntilEndStream(int index, InputStream src, int chunkSize) {
        throw new ReadOnlyBufferException();
    }

    @Override
    public Buffer writeBoolean(boolean value) {
        throw new ReadOnlyBufferException();
    }

    @Override
    public Buffer writeByte(int value) {
        throw new ReadOnlyBufferException();
    }

    @Override
    public Buffer writeShort(int value) {
        throw new ReadOnlyBufferException();
    }

    @Override
    public Buffer writeShortLE(int value) {
        throw new ReadOnlyBufferException();
    }

    @Override
    public Buffer writeMedium(int value) {
        throw new ReadOnlyBufferException();
    }

    @Override
    public Buffer writeMediumLE(int value) {
        throw new ReadOnlyBufferException();
    }

    @Override
    public Buffer writeInt(int value) {
        throw new ReadOnlyBufferException();
    }

    @Override
    public Buffer writeIntLE(int value) {
        throw new ReadOnlyBufferException();
    }

    @Override
    public Buffer writeLong(long value) {
        throw new ReadOnlyBufferException();
    }

    @Override
    public Buffer writeLongLE(long value) {
        throw new ReadOnlyBufferException();
    }

    @Override
    public Buffer writeChar(int value) {
        throw new ReadOnlyBufferException();
    }

    @Override
    public Buffer writeFloat(float value) {
        throw new ReadOnlyBufferException();
    }

    @Override
    public Buffer writeDouble(double value) {
        throw new ReadOnlyBufferException();
    }

    @Override
    public Buffer writeBytes(Buffer src) {
        throw new ReadOnlyBufferException();
    }

    @Override
    public Buffer writeBytes(Buffer src, int length) {
        throw new ReadOnlyBufferException();
    }

    @Override
    public Buffer writeBytes(Buffer src, int srcIndex, int length) {
        throw new ReadOnlyBufferException();
    }

    @Override
    public Buffer writeBytes(byte[] src) {
        throw new ReadOnlyBufferException();
    }

    @Override
    public Buffer writeBytes(byte[] src, int srcIndex, int length) {
        throw new ReadOnlyBufferException();
    }

    @Override
    public Buffer writeBytes(ByteBuffer src) {
        throw new ReadOnlyBufferException();
    }

    @Override
    public int writeBytes(InputStream src, int length) {
        throw new ReadOnlyBufferException();
    }

    @Override
    public int writeBytesUntilEndStream(InputStream src, int chunkSize) {
        throw new ReadOnlyBufferException();
    }

    @Override
    public Buffer writeAscii(CharSequence seq) {
        throw new ReadOnlyBufferException();
    }

    @Override
    public Buffer writeUtf8(CharSequence seq) {
        throw new ReadOnlyBufferException();
    }

    @Override
    public Buffer writeUtf8(CharSequence seq, int ensureWritable) {
        throw new ReadOnlyBufferException();
    }

    @Override
    public Buffer readBytes(int length) {
        if (length == 0) {
            return EMPTY_BUFFER;
        }
        checkReadableBytes0(length);
        Buffer buf = new ReadOnlyByteBuffer(sliceByteBuffer0(readerIndex(), length));
        skipBytes0(length);
        return buf;
    }

    @Override
    public Buffer copy(int index, int length) {
        return copy(sliceByteBuffer(index, length), length);
    }

    private Buffer copy(ByteBuffer byteBufferSlice, int length) {
        ByteBuffer tmpBuf = isDirect() ? allocateDirect(length) : allocate(length);
        tmpBuf.put(byteBufferSlice);
        return new ReadOnlyByteBuffer(tmpBuf.asReadOnlyBuffer());
    }

    @Override
    public Buffer slice(int index, int length) {
        return new ReadOnlyByteBuffer(sliceByteBuffer(index, length));
    }

    private ByteBuffer sliceByteBuffer(int index, int length) {
        checkIndex0(index, length);
        return sliceByteBuffer0(index, length);
    }

    private ByteBuffer sliceByteBuffer0(int index, int length) {
        return (ByteBuffer) buffer.duplicate().position(index).limit(index + length);
    }

    @Override
    public Buffer duplicate() {
        return new ReadOnlyByteBuffer(sliceByteBuffer0(0, capacity()));
    }

    @Override
    public int nioBufferCount() {
        return 1;
    }

    @Override
    public ByteBuffer toNioBuffer() {
        return sliceByteBuffer0(readerIndex(), readableBytes());
    }

    @Override
    public ByteBuffer toNioBuffer(int index, int length) {
        return sliceByteBuffer(index, length);
    }

    @Override
    public ByteBuffer[] toNioBuffers(int index, int length) {
        return new ByteBuffer[] {toNioBuffer(index, length)};
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
        return buffer.isDirect();
    }

    @Override
    public boolean hasArray() {
        return false;
    }

    @Override
    public byte[] array() {
        throw new ReadOnlyBufferException();
    }

    @Override
    public int arrayOffset() {
        throw new ReadOnlyBufferException();
    }

    @Override
    public String toString(int index, int length, Charset charset) {
        ByteBuffer slice = sliceByteBuffer(index, length);
        try {
            // TODO(scott): thread local for the decoder?
            return charset.newDecoder().decode(slice).toString();
        } catch (CharacterCodingException e) {
            throw new IllegalStateException(e);
        }
    }
}
