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

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ReadOnlyBufferException;
import java.nio.charset.Charset;

final class ReadOnlyBuffer extends WrappedBuffer {

    ReadOnlyBuffer(Buffer buffer) {
        super(buffer);
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
    public Buffer capacity(int newCapacity) {
        throw new ReadOnlyBufferException();
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
        return buffer.readSlice(length).asReadOnly();
    }

    @Override
    public Buffer duplicate() {
        return buffer.duplicate().asReadOnly();
    }

    @Override
    public Buffer slice() {
        return buffer.slice().asReadOnly();
    }

    @Override
    public Buffer slice(int index, int length) {
        return buffer.slice(index, length).asReadOnly();
    }

    @Override
    public ByteBuffer toNioBuffer() {
        return asReadOnlyByteBuffer(buffer.toNioBuffer());
    }

    @Override
    public ByteBuffer toNioBuffer(int index, int length) {
        return asReadOnlyByteBuffer(buffer.toNioBuffer(index, length));
    }

    @Override
    public ByteBuffer[] toNioBuffers() {
        return asReadOnlyByteBuffers(buffer.toNioBuffers());
    }

    @Override
    public ByteBuffer[] toNioBuffers(int index, int length) {
        return asReadOnlyByteBuffers(buffer.toNioBuffers(index, length));
    }

    private static ByteBuffer asReadOnlyByteBuffer(ByteBuffer buffer) {
        return buffer.isReadOnly() ? buffer : buffer.asReadOnlyBuffer();
    }

    private static ByteBuffer[] asReadOnlyByteBuffers(ByteBuffer[] buffers) {
        for (int i = 0; i < buffers.length; ++i) {
            buffers[i] = asReadOnlyByteBuffer(buffers[i]);
        }
        return buffers;
    }
}
