/*
 * Copyright © 2018-2019 Apple Inc. and the ServiceTalk project authors
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

    ReadOnlyByteBuffer(ByteBuffer buffer) {
        super(buffer.position(), buffer.limit());
        this.buffer = buffer;
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
        return 1;
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
            getBytes0(index, dst.array(), dst.arrayOffset() + dstIndex, length);
        } else if (buffer.hasArray()) {
            dst.setBytes(dstIndex, buffer.array(), buffer.arrayOffset() + index, length);
        } else {
            dst.setBytes(dstIndex, (ByteBuffer) buffer.duplicate().position(index).limit(index + length));
        }
        return this;
    }

    @Override
    public Buffer getBytes(int index, byte[] dst, int dstIndex, int length) {
        checkDstIndex(index, length, dstIndex, dst.length);
        getBytes0(index, dst, dstIndex, length);
        return this;
    }

    private Buffer getBytes0(int index, byte[] dst, int dstIndex, int length) {
        if (buffer.hasArray()) {
            System.arraycopy(buffer.array(), buffer.arrayOffset() + index, dst, dstIndex, length);
        } else {
            ByteBuffer tmpBuf = buffer.duplicate();
            tmpBuf.position(index).limit(index + length);
            tmpBuf.get(dst, dstIndex, length);
        }
        return this;
    }

    @Override
    public Buffer getBytes(int index, ByteBuffer dst) {
        int bytesToCopy = Math.min(capacity() - index, dst.remaining());
        checkIndex0(index, bytesToCopy);

        if (buffer.hasArray()) {
            dst.put(buffer.array(), buffer.arrayOffset() + index, bytesToCopy);
        } else {
            ByteBuffer tmpBuf = buffer.duplicate();
            tmpBuf.position(index).limit(index + bytesToCopy);
            dst.put(tmpBuf);
        }
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
    public Buffer writeCharSequence(CharSequence seq, Charset charset) {
        throw new ReadOnlyBufferException();
    }

    @Override
    public Buffer readSlice(int length) {
        checkReadableBytes0(length);
        Buffer buf = new ReadOnlyByteBuffer(sliceByteBuffer0(readerIndex(), length));
        skipBytes0(length);
        return buf;
    }

    @Override
    public Buffer readBytes(int length) {
        if (length == 0) {
            return EMPTY_BUFFER;
        }
        // Return readSlice(length) instead of allocating a new `Buffer` because for a read-only `Buffer` it doesn't
        // mater if the underlying bytes storage will be copied or shared.
        return readSlice(length);
    }

    @Override
    public Buffer copy(int index, int length) {
        return copy(sliceByteBuffer(index, length), length);
    }

    private Buffer copy(ByteBuffer byteBufferSlice, int length) {
        ByteBuffer tmpBuf = isDirect() ? allocateDirect(length) : allocate(length);
        tmpBuf.put(byteBufferSlice);
        tmpBuf.flip();
        return new ReadOnlyByteBuffer(tmpBuf);
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
        return asReadOnlyByteBuffer(sliceByteBuffer0(readerIndex(), readableBytes()));
    }

    @Override
    public ByteBuffer toNioBuffer(int index, int length) {
        return asReadOnlyByteBuffer(sliceByteBuffer(index, length));
    }

    private static ByteBuffer asReadOnlyByteBuffer(ByteBuffer buffer) {
        return buffer.isReadOnly() ? buffer : buffer.asReadOnlyBuffer();
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
