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
import io.servicetalk.buffer.api.CompositeBuffer;

import io.netty.buffer.CompositeByteBuf;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;

final class NettyCompositeBuffer extends NettyBuffer<CompositeByteBuf> implements CompositeBuffer {

    NettyCompositeBuffer(CompositeByteBuf buffer) {
        super(buffer);
    }

    @Override
    public CompositeBuffer addBuffer(Buffer buf, boolean incrementWriterIndex) {
        buffer.addComponent(incrementWriterIndex, BufferUtils.toByteBuf(buf));
        return this;
    }

    @Override
    public CompositeBuffer consolidate() {
        buffer.consolidate();
        return this;
    }

    @Override
    public CompositeBuffer consolidate(int index, int count) {
        buffer.consolidate(index, count);
        return this;
    }

    @Override
    public CompositeBuffer discardSomeReadBytes() {
        buffer.discardSomeReadBytes();
        return this;
    }

    @Override
    public CompositeBuffer capacity(int newCapacity) {
        super.capacity(newCapacity);
        return this;
    }

    @Override
    public CompositeBuffer readerIndex(int readerIndex) {
        super.readerIndex(readerIndex);
        return this;
    }

    @Override
    public CompositeBuffer writerIndex(int writerIndex) {
        super.writerIndex(writerIndex);
        return this;
    }

    @Override
    public CompositeBuffer clear() {
        super.clear();
        return this;
    }

    @Override
    public CompositeBuffer getBytes(int index, Buffer dst) {
        super.getBytes(index, dst);
        return this;
    }

    @Override
    public CompositeBuffer getBytes(int index, Buffer dst, int length) {
        super.getBytes(index, dst, length);
        return this;
    }

    @Override
    public CompositeBuffer getBytes(int index, Buffer dst, int dstIndex, int length) {
        super.getBytes(index, dst, dstIndex, length);
        return this;
    }

    @Override
    public CompositeBuffer getBytes(int index, byte[] dst) {
        super.getBytes(index, dst);
        return this;
    }

    @Override
    public CompositeBuffer getBytes(int index, byte[] dst, int dstIndex, int length) {
        super.getBytes(index, dst, dstIndex, length);
        return this;
    }

    @Override
    public CompositeBuffer getBytes(int index, ByteBuffer dst) {
        super.getBytes(index, dst);
        return this;
    }

    @Override
    public CompositeBuffer setBoolean(int index, boolean value) {
        super.setBoolean(index, value);
        return this;
    }

    @Override
    public CompositeBuffer setByte(int index, int value) {
        super.setByte(index, value);
        return this;
    }

    @Override
    public CompositeBuffer setShort(int index, int value) {
        super.setShort(index, value);
        return this;
    }

    @Override
    public CompositeBuffer setShortLE(int index, int value) {
        super.setShortLE(index, value);
        return this;
    }

    @Override
    public CompositeBuffer setMedium(int index, int value) {
        super.setMedium(index, value);
        return this;
    }

    @Override
    public CompositeBuffer setMediumLE(int index, int value) {
        super.setMediumLE(index, value);
        return this;
    }

    @Override
    public CompositeBuffer setInt(int index, int value) {
        super.setInt(index, value);
        return this;
    }

    @Override
    public CompositeBuffer setIntLE(int index, int value) {
        super.setIntLE(index, value);
        return this;
    }

    @Override
    public CompositeBuffer setLong(int index, long value) {
        super.setLong(index, value);
        return this;
    }

    @Override
    public CompositeBuffer setLongLE(int index, long value) {
        super.setLongLE(index, value);
        return this;
    }

    @Override
    public CompositeBuffer setChar(int index, int value) {
        super.setChar(index, value);
        return this;
    }

    @Override
    public CompositeBuffer setFloat(int index, float value) {
        super.setFloat(index, value);
        return this;
    }

    @Override
    public CompositeBuffer setDouble(int index, double value) {
        super.setDouble(index, value);
        return this;
    }

    @Override
    public CompositeBuffer setBytes(int index, Buffer src) {
        super.setBytes(index, src);
        return this;
    }

    @Override
    public CompositeBuffer setBytes(int index, Buffer src, int length) {
        super.setBytes(index, src, length);
        return this;
    }

    @Override
    public CompositeBuffer setBytes(int index, Buffer src, int srcIndex, int length) {
        super.setBytes(index, src, srcIndex, length);
        return this;
    }

    @Override
    public CompositeBuffer setBytes(int index, byte[] src) {
        super.setBytes(index, src);
        return this;
    }

    @Override
    public CompositeBuffer setBytes(int index, byte[] src, int srcIndex, int length) {
        super.setBytes(index, src, srcIndex, length);
        return this;
    }

    @Override
    public CompositeBuffer setBytes(int index, ByteBuffer src) {
        super.setBytes(index, src);
        return this;
    }

    @Override
    public CompositeBuffer skipBytes(int length) {
        super.skipBytes(length);
        return this;
    }

    @Override
    public CompositeBuffer writeBoolean(boolean value) {
        super.writeBoolean(value);
        return this;
    }

    @Override
    public CompositeBuffer writeByte(int value) {
        super.writeByte(value);
        return this;
    }

    @Override
    public CompositeBuffer writeShort(int value) {
        super.writeShort(value);
        return this;
    }

    @Override
    public CompositeBuffer writeShortLE(int value) {
        super.writeShortLE(value);
        return this;
    }

    @Override
    public CompositeBuffer writeMedium(int value) {
        super.writeMedium(value);
        return this;
    }

    @Override
    public CompositeBuffer writeMediumLE(int value) {
        super.writeMediumLE(value);
        return this;
    }

    @Override
    public CompositeBuffer writeInt(int value) {
        super.writeInt(value);
        return this;
    }

    @Override
    public CompositeBuffer writeIntLE(int value) {
        super.writeIntLE(value);
        return this;
    }

    @Override
    public CompositeBuffer writeLong(long value) {
        super.writeLong(value);
        return this;
    }

    @Override
    public CompositeBuffer writeLongLE(long value) {
        super.writeLongLE(value);
        return this;
    }

    @Override
    public CompositeBuffer writeChar(int value) {
        super.writeChar(value);
        return this;
    }

    @Override
    public CompositeBuffer writeFloat(float value) {
        super.writeFloat(value);
        return this;
    }

    @Override
    public CompositeBuffer writeDouble(double value) {
        super.writeDouble(value);
        return this;
    }

    @Override
    public CompositeBuffer writeBytes(Buffer src) {
        super.writeBytes(src);
        return this;
    }

    @Override
    public CompositeBuffer writeBytes(Buffer src, int length) {
        super.writeBytes(src, length);
        return this;
    }

    @Override
    public CompositeBuffer writeBytes(Buffer src, int srcIndex, int length) {
        super.writeBytes(src, srcIndex, length);
        return this;
    }

    @Override
    public CompositeBuffer writeBytes(byte[] src) {
        super.writeBytes(src);
        return this;
    }

    @Override
    public CompositeBuffer writeBytes(byte[] src, int srcIndex, int length) {
        super.writeBytes(src, srcIndex, length);
        return this;
    }

    @Override
    public CompositeBuffer writeBytes(ByteBuffer src) {
        super.writeBytes(src);
        return this;
    }

    @Override
    public CompositeBuffer writeAscii(CharSequence seq) {
        super.writeAscii(seq);
        return this;
    }

    @Override
    public CompositeBuffer writeUtf8(CharSequence seq) {
        super.writeUtf8(seq);
        return this;
    }

    @Override
    public CompositeBuffer writeUtf8(CharSequence seq, int ensureWritable) {
        super.writeUtf8(seq, ensureWritable);
        return this;
    }

    @Override
    public CompositeBuffer writeCharSequence(CharSequence seq, Charset charset) {
        super.writeCharSequence(seq, charset);
        return this;
    }
}
