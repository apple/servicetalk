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
import io.servicetalk.buffer.api.BufferAllocator;
import io.servicetalk.buffer.api.EmptyBuffer;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;

import javax.annotation.Nullable;

import static io.netty.buffer.Unpooled.EMPTY_BUFFER;

/**
 * Internal utilities for {@link Buffer}s.
 */
public final class BufferUtil {

    public static final BufferAllocator PREFER_HEAP_ALLOCATOR = new ServiceTalkBufferAllocator(false);
    public static final BufferAllocator PREFER_DIRECT_ALLOCATOR = new ServiceTalkBufferAllocator(true);

    private BufferUtil() {
        // no instances
    }

    /**
     * Return a {@link ByteBuf} for the given buffer.
     *
     * @param buffer the buffer.
     * @return a {@link ByteBuf}.
     */
    public static ByteBuf toByteBuf(Buffer buffer) {
        ByteBuf buf = toByteBufNoThrow(buffer);
        if (buf == null) {
            throw new UnsupportedOperationException("Only NettyBuffer is supported");
        }
        return buf;
    }

    /**
     * Converts the passed {@code buffer} to {@link ByteBuf}, creating a new {@link ByteBuf} instance if required.
     *
     * @param buffer the buffer.
     * @return a {@link ByteBuf}.
     */
    public static ByteBuf extractByteBufOrCreate(Buffer buffer) {
        ByteBuf buf = toByteBufNoThrow(buffer);
        if (buf == null) {
            if (buffer.hasArray()) {
                ByteBuf byteBuf = Unpooled.wrappedBuffer(buffer.array(), buffer.arrayOffset(), buffer.capacity());
                byteBuf.readerIndex(buffer.readerIndex()).writerIndex(buffer.writerIndex());
                return byteBuf;
            } else {
                byte[] data = new byte[buffer.capacity()];
                buffer.getBytes(0, data, 0, data.length);
                ByteBuf byteBuf = Unpooled.wrappedBuffer(data);
                byteBuf.readerIndex(buffer.readerIndex()).writerIndex(buffer.writerIndex());
                return byteBuf;
            }
        }
        return buf;
    }

    /**
     * Converts the passed {@code buffer} to {@link ByteBuf}, or returns {@code null} if not possible.
     * @param buffer The {@link Buffer} to convert.
     * @return a {@link ByteBuf} equivalent of {@code buffer}, or {@code null} if no equivalent can be found.
     */
    @Nullable
    public static ByteBuf toByteBufNoThrow(Buffer buffer) {
        if (buffer instanceof NettyBuffer) {
            return ((NettyBuffer) buffer).buffer;
        }
        if (buffer instanceof WrappedBuffer) {
            return toByteBufNoThrow(((WrappedBuffer) buffer).buffer);
        }
        if (buffer instanceof EmptyBuffer) {
            return EMPTY_BUFFER;
        }
        return null;
    }

    /**
     * Returns the {@link ByteBufAllocator} taking the {@link BufferAllocator} into account.
     *
     * @param allocator the {@link BufferAllocator} that is used.
     * @return the {@link ByteBufAllocator} to use.
     */
    public static ByteBufAllocator getByteBufAllocator(BufferAllocator allocator) {
        return allocator instanceof ServiceTalkBufferAllocator ?
                (ServiceTalkBufferAllocator) allocator : ByteBufAllocator.DEFAULT;
    }

    /**
     * Return a {@link Buffer} for the given {@link ByteBuf}.
     *
     * @param buffer the buffer to wrap.
     * @return the created buffer.
     */
    public static Buffer newBufferFrom(ByteBuf buffer) {
        return new NettyBuffer<>(buffer);
    }

    /**
     * Calculate the max bytes length of UTF8 character sequence.
     * @param data the data to be encoded in UTF8.
     * @return max bytes length of UTF8 character sequence.
     */
    public static int maxUtf8Bytes(CharSequence data) {
        return ByteBufUtil.utf8MaxBytes(data);
    }
}
