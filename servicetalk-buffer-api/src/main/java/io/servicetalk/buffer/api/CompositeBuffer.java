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

/**
 * A virtual {@link Buffer} which shows multiple buffers as a single merged buffer.
 */
public interface CompositeBuffer extends Buffer {

    /**
     * Add the given {@link Buffer} to this buffer and increment the writerIndex.
     *
     * @param buffer the buffer to add.
     * @return self.
     */
    default CompositeBuffer addBuffer(Buffer buffer) {
        return addBuffer(buffer, true);
    }

    /**
     * Add the given {@link Buffer} to this buffer.
     *
     * @param buffer the buffer to add.
     * @param incrementWriterIndex if {@code true} the writerIndex will be increment by the number of readableBytes of the given buffer.
     * @return self.
     */
    CompositeBuffer addBuffer(Buffer buffer, boolean incrementWriterIndex);

    /**
     * Consolidate the composed {@code Buffer}s.
     *
     * @return self.
     */
    CompositeBuffer consolidate();

    /**
     * Consolidate the composed {@code Buffer}s.
     *
     * @param index the index on which to start to compose.
     * @param count the number of contained buffers to compose.
     * @return self.
     */
    CompositeBuffer consolidate(int index, int count);

    /**
     * Discard all {@link Buffer}s which have been read.
     * @return self.
     */
    CompositeBuffer discardSomeReadBytes();

    @Override
    CompositeBuffer capacity(int newCapacity);

    @Override
    CompositeBuffer readerIndex(int readerIndex);

    @Override
    CompositeBuffer writerIndex(int writerIndex);

    @Override
    CompositeBuffer clear();

    @Override
    CompositeBuffer getBytes(int index, Buffer dst);

    @Override
    CompositeBuffer getBytes(int index, Buffer dst, int length);

    @Override
    CompositeBuffer getBytes(int index, Buffer dst, int dstIndex, int length);

    @Override
    CompositeBuffer getBytes(int index, byte[] dst);

    @Override
    CompositeBuffer getBytes(int index, byte[] dst, int dstIndex, int length);

    @Override
    CompositeBuffer getBytes(int index, ByteBuffer dst);

    @Override
    CompositeBuffer setBoolean(int index, boolean value);

    @Override
    CompositeBuffer setByte(int index, int value);

    @Override
    CompositeBuffer setShort(int index, int value);

    @Override
    CompositeBuffer setShortLE(int index, int value);

    @Override
    CompositeBuffer setMedium(int index, int value);

    @Override
    CompositeBuffer setMediumLE(int index, int value);

    @Override
    CompositeBuffer setInt(int index, int value);

    @Override
    CompositeBuffer setIntLE(int index, int value);

    @Override
    CompositeBuffer setLong(int index, long value);

    @Override
    CompositeBuffer setLongLE(int index, long value);

    @Override
    CompositeBuffer setChar(int index, int value);

    @Override
    CompositeBuffer setFloat(int index, float value);

    @Override
    CompositeBuffer setDouble(int index, double value);

    @Override
    CompositeBuffer setBytes(int index, Buffer src);

    @Override
    CompositeBuffer setBytes(int index, Buffer src, int length);

    @Override
    CompositeBuffer setBytes(int index, Buffer src, int srcIndex, int length);

    @Override
    CompositeBuffer setBytes(int index, byte[] src);

    @Override
    CompositeBuffer setBytes(int index, byte[] src, int srcIndex, int length);

    @Override
    CompositeBuffer setBytes(int index, ByteBuffer src);

    @Override
    CompositeBuffer skipBytes(int length);

    @Override
    CompositeBuffer writeBoolean(boolean value);

    @Override
    CompositeBuffer writeByte(int value);

    @Override
    CompositeBuffer writeShort(int value);

    @Override
    CompositeBuffer writeShortLE(int value);

    @Override
    CompositeBuffer writeMedium(int value);

    @Override
    CompositeBuffer writeMediumLE(int value);

    @Override
    CompositeBuffer writeInt(int value);

    @Override
    CompositeBuffer writeIntLE(int value);

    @Override
    CompositeBuffer writeLong(long value);

    @Override
    CompositeBuffer writeLongLE(long value);

    @Override
    CompositeBuffer writeChar(int value);

    @Override
    CompositeBuffer writeFloat(float value);

    @Override
    CompositeBuffer writeDouble(double value);

    @Override
    CompositeBuffer writeBytes(Buffer src);

    @Override
    CompositeBuffer writeBytes(Buffer src, int length);

    @Override
    CompositeBuffer writeBytes(Buffer src, int srcIndex, int length);

    @Override
    CompositeBuffer writeBytes(byte[] src);

    @Override
    CompositeBuffer writeBytes(byte[] src, int srcIndex, int length);

    @Override
    CompositeBuffer writeBytes(ByteBuffer src);

    @Override
    CompositeBuffer writeAscii(CharSequence seq);

    @Override
    CompositeBuffer writeUtf8(CharSequence seq);
}
