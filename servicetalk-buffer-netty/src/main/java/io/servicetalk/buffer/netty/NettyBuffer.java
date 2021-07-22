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
package io.servicetalk.buffer.netty;

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.buffer.api.ByteProcessor;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;

import static io.servicetalk.buffer.netty.BufferUtils.toByteBufNoThrow;

class NettyBuffer<T extends ByteBuf> implements Buffer {

    final T buffer;

    /**
     * Create a new instance.
     *
     * @param buffer the buffer to wrap.
     */
    NettyBuffer(T buffer) {
        this.buffer = buffer;
    }

    @Override
    public int capacity() {
        return buffer.capacity();
    }

    @Override
    public Buffer capacity(int newCapacity) {
        buffer.capacity(newCapacity);
        return this;
    }

    @Override
    public int maxCapacity() {
        return buffer.maxCapacity();
    }

    @Override
    public int readerIndex() {
        return buffer.readerIndex();
    }

    @Override
    public Buffer readerIndex(int readerIndex) {
        buffer.readerIndex(readerIndex);
        return this;
    }

    @Override
    public int writerIndex() {
        return buffer.writerIndex();
    }

    @Override
    public Buffer writerIndex(int writerIndex) {
        buffer.writerIndex(writerIndex);
        return this;
    }

    @Override
    public int readableBytes() {
        return buffer.readableBytes();
    }

    @Override
    public int writableBytes() {
        return buffer.writableBytes();
    }

    @Override
    public int maxWritableBytes() {
        return buffer.maxWritableBytes();
    }

    @Override
    public Buffer ensureWritable(int minWritableBytes) {
        buffer.ensureWritable(minWritableBytes);
        return this;
    }

    @Override
    public int ensureWritable(int minWritableBytes, boolean force) {
        return buffer.ensureWritable(minWritableBytes, force);
    }

    @Override
    public Buffer clear() {
        buffer.clear();
        return this;
    }

    @Override
    public boolean getBoolean(int index) {
        return buffer.getBoolean(index);
    }

    @Override
    public byte getByte(int index) {
        return buffer.getByte(index);
    }

    @Override
    public short getUnsignedByte(int index) {
        return buffer.getUnsignedByte(index);
    }

    @Override
    public short getShort(int index) {
        return buffer.getShort(index);
    }

    @Override
    public short getShortLE(int index) {
        return buffer.getShortLE(index);
    }

    @Override
    public int getUnsignedShort(int index) {
        return buffer.getUnsignedShort(index);
    }

    @Override
    public int getUnsignedShortLE(int index) {
        return buffer.getUnsignedShortLE(index);
    }

    @Override
    public int getMedium(int index) {
        return buffer.getMedium(index);
    }

    @Override
    public int getMediumLE(int index) {
        return buffer.getMediumLE(index);
    }

    @Override
    public int getUnsignedMedium(int index) {
        return buffer.getUnsignedMedium(index);
    }

    @Override
    public int getUnsignedMediumLE(int index) {
        return buffer.getUnsignedMediumLE(index);
    }

    @Override
    public int getInt(int index) {
        return buffer.getInt(index);
    }

    @Override
    public int getIntLE(int index) {
        return buffer.getIntLE(index);
    }

    @Override
    public long getUnsignedInt(int index) {
        return buffer.getUnsignedInt(index);
    }

    @Override
    public long getUnsignedIntLE(int index) {
        return buffer.getUnsignedIntLE(index);
    }

    @Override
    public long getLong(int index) {
        return buffer.getLong(index);
    }

    @Override
    public long getLongLE(int index) {
        return buffer.getLongLE(index);
    }

    @Override
    public char getChar(int index) {
        return buffer.getChar(index);
    }

    @Override
    public float getFloat(int index) {
        return buffer.getFloat(index);
    }

    @Override
    public double getDouble(int index) {
        return buffer.getDouble(index);
    }

    @Override
    public Buffer getBytes(int index, Buffer dst) {
        return getBytes(index, dst, dst.writableBytes());
    }

    @Override
    public Buffer getBytes(int index, Buffer dst, int length) {
        getBytes(index, dst, dst.writerIndex(), length);
        dst.writerIndex(dst.writerIndex() + length);
        return this;
    }

    @Override
    public Buffer getBytes(int index, Buffer dst, int dstIndex, int length) {
        ByteBuf dstByteBuf = toByteBufNoThrow(dst);
        if (dstByteBuf != null) {
            buffer.getBytes(index, dstByteBuf, dstIndex, length);
        } else if (dst.hasArray()) {
            buffer.getBytes(index, dst.array(), dst.arrayOffset() + dstIndex, length);
        } else {
            ByteBuffer nioBuffer = dst.toNioBuffer(dstIndex, length);
            buffer.getBytes(index, nioBuffer);
        }
        return this;
    }

    @Override
    public Buffer getBytes(int index, byte[] dst) {
        return getBytes(index, dst, 0, dst.length);
    }

    @Override
    public Buffer getBytes(int index, byte[] dst, int dstIndex, int length) {
        buffer.getBytes(index, dst, dstIndex, length);
        return this;
    }

    @Override
    public Buffer getBytes(int index, ByteBuffer dst) {
        buffer.getBytes(index, dst);
        return this;
    }

    @Override
    public Buffer setBoolean(int index, boolean value) {
        buffer.setBoolean(index, value);
        return this;
    }

    @Override
    public Buffer setByte(int index, int value) {
        buffer.setByte(index, value);
        return this;
    }

    @Override
    public Buffer setShort(int index, int value) {
        buffer.setShort(index, value);
        return this;
    }

    @Override
    public Buffer setShortLE(int index, int value) {
        buffer.setShortLE(index, value);
        return this;
    }

    @Override
    public Buffer setMedium(int index, int value) {
        buffer.setMedium(index, value);
        return this;
    }

    @Override
    public Buffer setMediumLE(int index, int value) {
        buffer.setMediumLE(index, value);
        return this;
    }

    @Override
    public Buffer setInt(int index, int value) {
        buffer.setInt(index, value);
        return this;
    }

    @Override
    public Buffer setIntLE(int index, int value) {
        buffer.setIntLE(index, value);
        return this;
    }

    @Override
    public Buffer setLong(int index, long value) {
        buffer.setLong(index, value);
        return this;
    }

    @Override
    public Buffer setLongLE(int index, long value) {
        buffer.setLongLE(index, value);
        return this;
    }

    @Override
    public Buffer setChar(int index, int value) {
        buffer.setChar(index, value);
        return this;
    }

    @Override
    public Buffer setFloat(int index, float value) {
        buffer.setFloat(index, value);
        return this;
    }

    @Override
    public Buffer setDouble(int index, double value) {
        buffer.setDouble(index, value);
        return this;
    }

    @Override
    public Buffer setBytes(int index, Buffer src) {
        return setBytes(index, src, src.readableBytes());
    }

    @Override
    public Buffer setBytes(int index, Buffer src, int length) {
        setBytes(index, src, src.readerIndex(), length);
        src.readerIndex(src.readerIndex() + length);
        return this;
    }

    @Override
    public Buffer setBytes(int index, Buffer src, int srcIndex, int length) {
        ByteBuf srcByteBuf = toByteBufNoThrow(src);
        if (srcByteBuf != null) {
            buffer.setBytes(index, srcByteBuf, srcIndex, length);
        } else if (src.hasArray()) {
            buffer.setBytes(index, src.array(), src.arrayOffset() + srcIndex, length);
        } else {
            ByteBuffer nioBuffer = src.toNioBuffer(srcIndex, length);
            buffer.setBytes(index, nioBuffer);
        }
        return this;
    }

    @Override
    public Buffer setBytes(int index, byte[] src) {
        return setBytes(index, src, 0, src.length);
    }

    @Override
    public Buffer setBytes(int index, byte[] src, int srcIndex, int length) {
        buffer.setBytes(index, src, srcIndex, length);
        return this;
    }

    @Override
    public Buffer setBytes(int index, ByteBuffer src) {
        buffer.setBytes(index, src);
        return this;
    }

    @Override
    public int setBytes(int index, InputStream src, int length) throws IOException {
        return buffer.setBytes(index, src, length);
    }

    @Override
    public int setBytesUntilEndStream(int index, InputStream src, int chunkSize) throws IOException {
        int i = index;
        int writableBytes = chunkSize;
        for (;;) {
            buffer.ensureWritable(writableBytes);
            int result = buffer.setBytes(i, src, chunkSize);
            if (result == -1) {
                break;
            }
            i += result;
            // The setBytes method does not update any indexes on the buffer. So with each successive call we must
            // ensure there is enough space to accommodate more data.
            writableBytes += chunkSize;
        }
        return i - index;
    }

    @Override
    public boolean readBoolean() {
        return buffer.readBoolean();
    }

    @Override
    public byte readByte() {
        return buffer.readByte();
    }

    @Override
    public short readUnsignedByte() {
        return buffer.readUnsignedByte();
    }

    @Override
    public short readShort() {
        return buffer.readShort();
    }

    @Override
    public short readShortLE() {
        return buffer.readShortLE();
    }

    @Override
    public int readUnsignedShort() {
        return buffer.readUnsignedShort();
    }

    @Override
    public int readUnsignedShortLE() {
        return buffer.readUnsignedShortLE();
    }

    @Override
    public int readMedium() {
        return buffer.readMedium();
    }

    @Override
    public int readMediumLE() {
        return buffer.readMediumLE();
    }

    @Override
    public int readUnsignedMedium() {
        return buffer.readUnsignedMedium();
    }

    @Override
    public int readUnsignedMediumLE() {
        return buffer.readUnsignedMediumLE();
    }

    @Override
    public int readInt() {
        return buffer.readInt();
    }

    @Override
    public int readIntLE() {
        return buffer.readIntLE();
    }

    @Override
    public long readUnsignedInt() {
        return buffer.readUnsignedInt();
    }

    @Override
    public long readUnsignedIntLE() {
        return buffer.readUnsignedIntLE();
    }

    @Override
    public long readLong() {
        return buffer.readLong();
    }

    @Override
    public long readLongLE() {
        return buffer.readLongLE();
    }

    @Override
    public char readChar() {
        return buffer.readChar();
    }

    @Override
    public float readFloat() {
        return buffer.readFloat();
    }

    @Override
    public double readDouble() {
        return buffer.readDouble();
    }

    @Override
    public Buffer readSlice(int length) {
        return new NettyBuffer<>(buffer.readSlice(length));
    }

    @Override
    public Buffer readBytes(int length) {
        return new NettyBuffer<>(buffer.readBytes(length));
    }

    @Override
    public Buffer readBytes(Buffer dst) {
        return readBytes(dst, dst.writableBytes());
    }

    @Override
    public Buffer readBytes(Buffer dst, int length) {
        readBytes(dst, dst.writerIndex(), length);
        dst.writerIndex(dst.writerIndex() + length);
        return this;
    }

    @Override
    public Buffer readBytes(Buffer dst, int dstIndex, int length) {
        ByteBuf dstByteBuf = toByteBufNoThrow(dst);
        if (dstByteBuf != null) {
            buffer.readBytes(dstByteBuf, dstIndex, length);
        } else if (dst.hasArray()) {
            buffer.readBytes(dst.array(), dst.arrayOffset() + dstIndex, length);
        } else {
            ByteBuffer nioBuffer = dst.toNioBuffer(dstIndex, length);
            buffer.readBytes(nioBuffer);
        }
        return this;
    }

    @Override
    public Buffer readBytes(byte[] dst) {
        return readBytes(dst, 0, dst.length);
    }

    @Override
    public Buffer readBytes(byte[] dst, int dstIndex, int length) {
        buffer.readBytes(dst, dstIndex, length);
        return this;
    }

    @Override
    public Buffer readBytes(ByteBuffer dst) {
        buffer.readBytes(dst);
        return this;
    }

    @Override
    public Buffer skipBytes(int length) {
        buffer.skipBytes(length);
        return this;
    }

    @Override
    public Buffer writeBoolean(boolean value) {
        buffer.writeBoolean(value);
        return this;
    }

    @Override
    public Buffer writeByte(int value) {
        buffer.writeByte(value);
        return this;
    }

    @Override
    public Buffer writeShort(int value) {
        buffer.writeShort(value);
        return this;
    }

    @Override
    public Buffer writeShortLE(int value) {
        buffer.writeShortLE(value);
        return this;
    }

    @Override
    public Buffer writeMedium(int value) {
        buffer.writeMedium(value);
        return this;
    }

    @Override
    public Buffer writeMediumLE(int value) {
        buffer.writeMediumLE(value);
        return this;
    }

    @Override
    public Buffer writeInt(int value) {
        buffer.writeInt(value);
        return this;
    }

    @Override
    public Buffer writeIntLE(int value) {
        buffer.writeIntLE(value);
        return this;
    }

    @Override
    public Buffer writeLong(long value) {
        buffer.writeLong(value);
        return this;
    }

    @Override
    public Buffer writeLongLE(long value) {
        buffer.writeLongLE(value);
        return this;
    }

    @Override
    public Buffer writeChar(int value) {
        buffer.writeChar(value);
        return this;
    }

    @Override
    public Buffer writeFloat(float value) {
        buffer.writeFloat(value);
        return this;
    }

    @Override
    public Buffer writeDouble(double value) {
        buffer.writeDouble(value);
        return this;
    }

    @Override
    public Buffer writeBytes(Buffer src) {
        return writeBytes(src, src.readableBytes());
    }

    @Override
    public Buffer writeBytes(Buffer src, int length) {
        writeBytes(src, src.readerIndex(), length);
        src.readerIndex(src.readerIndex() + length);
        return this;
    }

    @Override
    public Buffer writeBytes(Buffer src, int srcIndex, int length) {
        ByteBuf srcByteBuf = toByteBufNoThrow(src);
        if (srcByteBuf != null) {
            buffer.writeBytes(srcByteBuf, srcIndex, length);
        } else if (src.hasArray()) {
            buffer.writeBytes(src.array(), src.arrayOffset() + srcIndex, length);
        } else {
            ByteBuffer nioBuffer = src.toNioBuffer(srcIndex, length);
            buffer.writeBytes(nioBuffer);
        }
        return this;
    }

    @Override
    public Buffer writeBytes(byte[] src) {
        return writeBytes(src, 0, src.length);
    }

    @Override
    public Buffer writeBytes(byte[] src, int srcIndex, int length) {
        buffer.writeBytes(src, srcIndex, length);
        return this;
    }

    @Override
    public Buffer writeBytes(ByteBuffer src) {
        buffer.writeBytes(src);
        return this;
    }

    @Override
    public int writeBytes(InputStream src, int length) throws IOException {
        return buffer.writeBytes(src, length);
    }

    @Override
    public int writeBytesUntilEndStream(InputStream src, int chunkSize) throws IOException {
        int amountRead = setBytesUntilEndStream(buffer.writerIndex(), src, chunkSize);
        if (amountRead > 0) {
            buffer.writerIndex(buffer.writerIndex() + amountRead);
        }
        return amountRead;
    }

    @Override
    public Buffer writeAscii(CharSequence seq) {
        ByteBufUtil.writeAscii(buffer, seq);
        return this;
    }

    @Override
    public Buffer writeUtf8(CharSequence seq) {
        ByteBufUtil.writeUtf8(buffer, seq);
        return this;
    }

    @Override
    public Buffer writeUtf8(CharSequence seq, int ensureWritable) {
        ByteBufUtil.reserveAndWriteUtf8(buffer, seq, ensureWritable);
        return this;
    }

    @Override
    public Buffer writeCharSequence(CharSequence seq, Charset charset) {
        buffer.writeCharSequence(seq, charset);
        return this;
    }

    @Override
    public int indexOf(int fromIndex, int toIndex, byte value) {
        return buffer.indexOf(fromIndex, toIndex, value);
    }

    @Override
    public int bytesBefore(byte value) {
        return buffer.bytesBefore(value);
    }

    @Override
    public int bytesBefore(int length, byte value) {
        return buffer.bytesBefore(length, value);
    }

    @Override
    public int bytesBefore(int index, int length, byte value) {
        return buffer.bytesBefore(index, length, value);
    }

    @Override
    public Buffer copy() {
        return new NettyBuffer<>(buffer.copy());
    }

    @Override
    public Buffer copy(int index, int length) {
        return new NettyBuffer<>(buffer.copy(index, length));
    }

    @Override
    public Buffer slice() {
        return new NettyBuffer<>(buffer.slice());
    }

    @Override
    public Buffer slice(int index, int length) {
        return new NettyBuffer<>(buffer.slice(index, length));
    }

    @Override
    public Buffer duplicate() {
        return new NettyBuffer<>(buffer.duplicate());
    }

    @Override
    public int nioBufferCount() {
        return buffer.nioBufferCount();
    }

    @Override
    public ByteBuffer toNioBuffer() {
        return buffer.nioBuffer();
    }

    @Override
    public ByteBuffer toNioBuffer(int index, int length) {
        return buffer.nioBuffer(index, length);
    }

    @Override
    public ByteBuffer[] toNioBuffers() {
        return buffer.nioBuffers();
    }

    @Override
    public ByteBuffer[] toNioBuffers(int index, int length) {
        return buffer.nioBuffers(index, length);
    }

    @Override
    public boolean isReadOnly() {
        return buffer.isReadOnly();
    }

    @Override
    public Buffer asReadOnly() {
        return new ReadOnlyBuffer(this);
    }

    @Override
    public boolean isDirect() {
        return buffer.isDirect();
    }

    @Override
    public boolean hasArray() {
        return buffer.hasArray();
    }

    @Override
    public byte[] array() {
        return buffer.array();
    }

    @Override
    public int arrayOffset() {
        return buffer.arrayOffset();
    }

    @Override
    public int forEachByte(ByteProcessor processor) {
        return buffer.forEachByte(processor::process);
    }

    @Override
    public int forEachByte(int index, int length, ByteProcessor processor) {
        return buffer.forEachByte(index, length, processor::process);
    }

    @Override
    public int forEachByteDesc(ByteProcessor processor) {
        return buffer.forEachByteDesc(processor::process);
    }

    @Override
    public int forEachByteDesc(int index, int length, ByteProcessor processor) {
        return buffer.forEachByteDesc(index, length, processor::process);
    }

    @Override
    public int hashCode() {
        return buffer.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Buffer) {
            Buffer other = (Buffer) obj;
            ByteBuf byteBuf = toByteBufNoThrow(other);
            return byteBuf != null ? buffer.equals(byteBuf) : buffer.nioBuffer().equals(other.toNioBuffer());
        }
        return false;
    }

    @Override
    public String toString() {
        return "NettyBuffer{" +
                "buffer=" + buffer +
                '}';
    }

    @Override
    public String toString(Charset charset) {
        return buffer.toString(charset);
    }

    @Override
    public String toString(int index, int length, Charset charset) {
        return buffer.toString(index, length, charset);
    }
}
