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
package io.servicetalk.buffer.netty;

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.buffer.api.ByteProcessor;

import io.netty.util.internal.StringUtil;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Objects;

class WrappedBuffer implements Buffer {
    final Buffer buffer;

    WrappedBuffer(Buffer buffer) {
        this.buffer = Objects.requireNonNull(buffer);
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
        buffer.getBytes(index, dst);
        return this;
    }

    @Override
    public Buffer getBytes(int index, Buffer dst, int length) {
        buffer.getBytes(index, dst, length);
        return this;
    }

    @Override
    public Buffer getBytes(int index, Buffer dst, int dstIndex, int length) {
        buffer.getBytes(index, dst, dstIndex, length);

        return this;
    }

    @Override
    public Buffer getBytes(int index, byte[] dst) {
        buffer.getBytes(index, dst);
        return this;
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
        buffer.setBytes(index, src);
        return this;
    }

    @Override
    public Buffer setBytes(int index, Buffer src, int length) {
        buffer.setBytes(index, src, length);
        return this;
    }

    @Override
    public Buffer setBytes(int index, Buffer src, int srcIndex, int length) {
        buffer.setBytes(index, src, srcIndex, length);
        return this;
    }

    @Override
    public Buffer setBytes(int index, byte[] src) {
        buffer.setBytes(index, src);
        return this;
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
        return buffer.setBytesUntilEndStream(index, src, chunkSize);
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
        return buffer.readSlice(length);
    }

    @Override
    public Buffer readBytes(int length) {
        return buffer.readBytes(length);
    }

    @Override
    public Buffer readBytes(Buffer dst) {
        buffer.readBytes(dst);
        return this;
    }

    @Override
    public Buffer readBytes(Buffer dst, int length) {
        buffer.readBytes(dst, length);
        return this;
    }

    @Override
    public Buffer readBytes(Buffer dst, int dstIndex, int length) {
        buffer.readBytes(dst, dstIndex, length);
        return this;
    }

    @Override
    public Buffer readBytes(byte[] dst) {
        buffer.readBytes(dst);
        return this;
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
        buffer.writeBytes(src);
        return this;
    }

    @Override
    public Buffer writeBytes(Buffer src, int length) {
        buffer.writeBytes(src, length);
        return this;
    }

    @Override
    public Buffer writeBytes(Buffer src, int srcIndex, int length) {
        buffer.writeBytes(src, srcIndex, length);
        return this;
    }

    @Override
    public Buffer writeBytes(byte[] src) {
        buffer.writeBytes(src);
        return this;
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
        return buffer.writeBytesUntilEndStream(src, chunkSize);
    }

    @Override
    public Buffer writeAscii(CharSequence seq) {
        buffer.writeAscii(seq);
        return this;
    }

    @Override
    public Buffer writeUtf8(CharSequence seq) {
        buffer.writeUtf8(seq);
        return this;
    }

    @Override
    public Buffer writeUtf8(CharSequence seq, int ensureWritable) {
        buffer.writeUtf8(seq, ensureWritable);
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
        return buffer.copy();
    }

    @Override
    public Buffer copy(int index, int length) {
        return buffer.copy(index, length);
    }

    @Override
    public Buffer slice() {
        return buffer.slice();
    }

    @Override
    public Buffer slice(int index, int length) {
        return buffer.slice(index, length);
    }

    @Override
    public Buffer duplicate() {
        return buffer.duplicate();
    }

    @Override
    public int nioBufferCount() {
        return buffer.nioBufferCount();
    }

    @Override
    public ByteBuffer toNioBuffer() {
        return buffer.toNioBuffer();
    }

    @Override
    public ByteBuffer toNioBuffer(int index, int length) {
        return buffer.toNioBuffer(index, length);
    }

    @Override
    public ByteBuffer[] toNioBuffers() {
        return buffer.toNioBuffers();
    }

    @Override
    public ByteBuffer[] toNioBuffers(int index, int length) {
        return buffer.toNioBuffers(index, length);
    }

    @Override
    public boolean isReadOnly() {
        return buffer.isReadOnly();
    }

    @Override
    public Buffer asReadOnly() {
        return buffer.asReadOnly();
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
    public String toString(Charset charset) {
        return buffer.toString(charset);
    }

    @Override
    public String toString(int index, int length, Charset charset) {
        return buffer.toString(index, length, charset);
    }

    @Override
    public String toString() {
        return StringUtil.simpleClassName(this) + '(' + buffer.toString() + ')';
    }

    @Override
    public int forEachByte(ByteProcessor processor) {
        return buffer.forEachByte(processor);
    }

    @Override
    public int forEachByte(int index, int length, ByteProcessor processor) {
        return buffer.forEachByte(index, length, processor);
    }

    @Override
    public int forEachByteDesc(ByteProcessor processor) {
        return buffer.forEachByteDesc(processor);
    }

    @Override
    public int forEachByteDesc(int index, int length, ByteProcessor processor) {
        return buffer.forEachByteDesc(index, length, processor);
    }

    @Override
    public boolean equals(Object o) {
        return buffer.equals(o);
    }

    @Override
    public int hashCode() {
        return buffer.hashCode();
    }
}
