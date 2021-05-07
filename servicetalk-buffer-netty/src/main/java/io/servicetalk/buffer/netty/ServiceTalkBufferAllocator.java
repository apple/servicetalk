/*
 * Copyright © 2018-2020 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.buffer.api.CompositeBuffer;

import io.netty.buffer.AbstractByteBufAllocator;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.CompositeByteBuf;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;

import static io.servicetalk.buffer.api.EmptyBuffer.EMPTY_BUFFER;
import static io.servicetalk.utils.internal.PlatformDependent.useDirectBufferWithoutZeroing;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Our own {@link AbstractByteBufAllocator} implementation which will not use leak-detection and depends on the GC
 * to handle the de-allocation of direct memory. All the returned {@link ByteBuf} are also unreleasable.
 */
final class ServiceTalkBufferAllocator extends AbstractByteBufAllocator implements BufferAllocator {
    private final ByteBufAllocator forceHeapAllocator = new ForceTypeByteBufAllocator(this, false);
    private final ByteBufAllocator forceDirectAllocator = new ForceTypeByteBufAllocator(this, true);

    private final boolean noZeroing;

    ServiceTalkBufferAllocator(boolean preferDirect) {
        super(preferDirect);
        this.noZeroing = useDirectBufferWithoutZeroing();
    }

    @Override
    protected ByteBuf newHeapBuffer(int initialCapacity, int maxCapacity) {
        return io.netty.util.internal.PlatformDependent.hasUnsafe() ?
                new UnreleasableNoZeroingHeapByteBuf(this, initialCapacity, maxCapacity) :
                new UnreleasableHeapByteBuf(this, initialCapacity, maxCapacity);
    }

    @Override
    protected ByteBuf newDirectBuffer(int initialCapacity, int maxCapacity) {
        if (noZeroing) {
            return new UnreleasableUnsafeNoZeroingDirectByteBuf(this, initialCapacity, maxCapacity);
        }
        return io.netty.util.internal.PlatformDependent.hasUnsafe() ?
                new UnreleasableUnsafeDirectByteBuf(this, initialCapacity, maxCapacity) :
                new UnreleasableDirectByteBuf(this, initialCapacity, maxCapacity);
    }

    @Override
    public CompositeByteBuf compositeHeapBuffer(int maxNumComponents) {
        return new UnreleasableCompositeByteBuf(this, false, maxNumComponents);
    }

    @Override
    public CompositeByteBuf compositeDirectBuffer(int maxNumComponents) {
        return new UnreleasableCompositeByteBuf(this, true, maxNumComponents);
    }

    @Override
    public boolean isDirectBufferPooled() {
        return false;
    }

    @Override
    public Buffer fromUtf8(CharSequence data) {
        return data.length() == 0 ? EMPTY_BUFFER : new NettyBuffer<>(ByteBufUtil.writeUtf8(this, data));
    }

    @Override
    public Buffer fromUtf8(CharSequence data, boolean direct) {
        return data.length() == 0 ? EMPTY_BUFFER : new NettyBuffer<>(ByteBufUtil.writeUtf8(direct ?
                forceDirectAllocator : forceHeapAllocator, data));
    }

    @Override
    public Buffer fromAscii(CharSequence data) {
        return data.length() == 0 ? EMPTY_BUFFER : new NettyBuffer<>(ByteBufUtil.writeAscii(this, data));
    }

    @Override
    public Buffer fromAscii(CharSequence data, boolean direct) {
        return data.length() == 0 ? EMPTY_BUFFER : new NettyBuffer<>(ByteBufUtil.writeAscii(direct ?
                forceDirectAllocator : forceHeapAllocator, data));
    }

    @Override
    public Buffer fromSequence(CharSequence data, Charset charset) {
        if (charset == US_ASCII) {
            return fromAscii(data);
        }
        if (charset == UTF_8) {
            return fromUtf8(data);
        }
        return data.length() == 0 ? EMPTY_BUFFER : new NettyBuffer<>(ByteBufUtil.encodeString(this,
                data instanceof CharBuffer ? (CharBuffer) data : CharBuffer.wrap(data), charset));
    }

    @Override
    public Buffer fromSequence(CharSequence data, Charset charset, boolean direct) {
        if (charset == US_ASCII) {
            return fromAscii(data, direct);
        }
        if (charset == UTF_8) {
            return fromUtf8(data, direct);
        }
        return data.length() == 0 ? EMPTY_BUFFER : new NettyBuffer<>(ByteBufUtil.encodeString(direct ?
                        forceDirectAllocator : forceHeapAllocator,
                data instanceof CharBuffer ? (CharBuffer) data : CharBuffer.wrap(data), charset));
    }

    @Override
    public Buffer newBuffer(int initialCapacity) {
        return new NettyBuffer<>(buffer(initialCapacity));
    }

    @Override
    public Buffer newBuffer(final int initialCapacity, final int maxCapacity) {
        return new NettyBuffer<>(buffer(initialCapacity, maxCapacity));
    }

    @Override
    public Buffer newBuffer(int initialCapacity, boolean direct) {
        return new NettyBuffer<>(direct ? directBuffer(initialCapacity) : heapBuffer(initialCapacity));
    }

    @Override
    public CompositeBuffer newCompositeBuffer() {
        return new NettyCompositeBuffer(compositeBuffer());
    }

    @Override
    public CompositeBuffer newCompositeBuffer(int maxComponents) {
        return new NettyCompositeBuffer(compositeBuffer(maxComponents));
    }

    @Override
    public Buffer wrap(byte[] bytes) {
        return bytes.length == 0 ? EMPTY_BUFFER : new NettyBuffer<>(new UnreleasableHeapByteBuf(this, bytes,
                bytes.length));
    }

    @Override
    public Buffer wrap(ByteBuffer buffer) {
        final Buffer buf;
        if (buffer.hasArray()) {
            buf = wrap(buffer.array(), buffer.arrayOffset() + buffer.position(), buffer.remaining());
        } else if (buffer.isReadOnly()) {
            buf = new NettyBuffer<>(new UnreleasableReadOnlyByteBufferBuf(this, buffer));
        } else if (buffer.isDirect() && io.netty.util.internal.PlatformDependent.hasUnsafe()) {
            buf = new NettyBuffer<>(new UnreleasableUnsafeDirectByteBuf(this, buffer, buffer.remaining()));
        } else {
            buf = new NettyBuffer<>(new UnreleasableDirectByteBuf(this, buffer, buffer.remaining()));
        }
        return buffer.isReadOnly() ? buf.asReadOnly() : buf;
    }

    private static final class ForceTypeByteBufAllocator implements ByteBufAllocator {

        private final ByteBufAllocator allocator;
        private final boolean direct;

        ForceTypeByteBufAllocator(ByteBufAllocator allocator, boolean direct) {
            this.allocator = allocator;
            this.direct = direct;
        }

        @Override
        public ByteBuf buffer() {
            return direct ? directBuffer() : heapBuffer();
        }

        @Override
        public ByteBuf buffer(int initialCapacity) {
            return direct ? directBuffer(initialCapacity) : heapBuffer(initialCapacity);
        }

        @Override
        public ByteBuf buffer(int initialCapacity, int maxCapacity) {
            return direct ? directBuffer(initialCapacity, maxCapacity) : heapBuffer(initialCapacity, maxCapacity);
        }

        @Override
        public ByteBuf ioBuffer() {
            return allocator.ioBuffer();
        }

        @Override
        public ByteBuf ioBuffer(int initialCapacity) {
            return allocator.ioBuffer(initialCapacity);
        }

        @Override
        public ByteBuf ioBuffer(int initialCapacity, int maxCapacity) {
            return allocator.ioBuffer(initialCapacity, maxCapacity);
        }

        @Override
        public ByteBuf heapBuffer() {
            return allocator.heapBuffer();
        }

        @Override
        public ByteBuf heapBuffer(int initialCapacity) {
            return allocator.heapBuffer(initialCapacity);
        }

        @Override
        public ByteBuf heapBuffer(int initialCapacity, int maxCapacity) {
            return allocator.heapBuffer(initialCapacity, maxCapacity);
        }

        @Override
        public ByteBuf directBuffer() {
            return allocator.directBuffer();
        }

        @Override
        public ByteBuf directBuffer(int initialCapacity) {
            return allocator.directBuffer(initialCapacity);
        }

        @Override
        public ByteBuf directBuffer(int initialCapacity, int maxCapacity) {
            return allocator.directBuffer(initialCapacity, maxCapacity);
        }

        @Override
        public CompositeByteBuf compositeBuffer() {
            return direct ? compositeDirectBuffer() : compositeHeapBuffer();
        }

        @Override
        public CompositeByteBuf compositeBuffer(int maxNumComponents) {
            return direct ? compositeDirectBuffer(maxNumComponents) : compositeHeapBuffer(maxNumComponents);
        }

        @Override
        public CompositeByteBuf compositeHeapBuffer() {
            return allocator.compositeHeapBuffer();
        }

        @Override
        public CompositeByteBuf compositeHeapBuffer(int maxNumComponents) {
            return allocator.compositeHeapBuffer(maxNumComponents);
        }

        @Override
        public CompositeByteBuf compositeDirectBuffer() {
            return allocator.compositeDirectBuffer();
        }

        @Override
        public CompositeByteBuf compositeDirectBuffer(int maxNumComponents) {
            return allocator.compositeDirectBuffer(maxNumComponents);
        }

        @Override
        public boolean isDirectBufferPooled() {
            return allocator.isDirectBufferPooled();
        }

        @Override
        public int calculateNewCapacity(int minNewCapacity, int maxCapacity) {
            return allocator.calculateNewCapacity(minNewCapacity, maxCapacity);
        }
    }
}
