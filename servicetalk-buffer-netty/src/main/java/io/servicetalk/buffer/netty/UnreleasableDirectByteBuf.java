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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.buffer.UnpooledDirectByteBuf;

import java.nio.ByteBuffer;

final class UnreleasableDirectByteBuf extends UnpooledDirectByteBuf {

    UnreleasableDirectByteBuf(ByteBufAllocator alloc, int initialCapacity, int maxCapacity) {
        super(alloc, initialCapacity, maxCapacity);
        // ServiceTalk buffers are unreleasable. There are some optimizations in Netty which use `refCnt() > 1` to
        // judge if a ByteBuf maybe shared, and if not shared Netty may assume is is safe to make changes to the
        // underlying storage (e.g. write reallocation, compact data in place) of the ByteBuf which may lead to
        // visibility issues across threads and data corruption. We retain() here to imply the ByteBuf maybe shared and
        // these optimizations are not safe.
        super.retain();
    }

    UnreleasableDirectByteBuf(ByteBufAllocator alloc, ByteBuffer initialBuffer, int maxCapacity) {
        super(alloc, initialBuffer, maxCapacity);
        // ServiceTalk buffers are unreleasable. There are some optimizations in Netty which use `refCnt() > 1` to
        // judge if a ByteBuf maybe shared, and if not shared Netty may assume is is safe to make changes to the
        // underlying storage (e.g. write reallocation, compact data in place) of the ByteBuf which may lead to
        // visibility issues across threads and data corruption. We retain() here to imply the ByteBuf maybe shared and
        // these optimizations are not safe.
        super.retain();
    }

    @Override
    public ByteBuf asReadOnly() {
        return Unpooled.unreleasableBuffer(super.asReadOnly());
    }

    @Override
    public ByteBuf readSlice(int length) {
        return Unpooled.unreleasableBuffer(super.readSlice(length));
    }

    @Override
    public ByteBuf readRetainedSlice(int length) {
        return readSlice(length);
    }

    @Override
    public ByteBuf slice() {
        return Unpooled.unreleasableBuffer(super.slice());
    }

    @Override
    public ByteBuf slice(int index, int length) {
        return Unpooled.unreleasableBuffer(super.slice(index, length));
    }

    @Override
    public ByteBuf retainedSlice() {
        return slice();
    }

    @Override
    public ByteBuf retainedSlice(int index, int length) {
        return slice(index, length);
    }

    @Override
    public ByteBuf duplicate() {
        return Unpooled.unreleasableBuffer(super.duplicate());
    }

    @Override
    public ByteBuf retainedDuplicate() {
        return duplicate();
    }

    @Override
    public ByteBuf retain(int increment) {
        return this;
    }

    @Override
    public ByteBuf retain() {
        return this;
    }

    @Override
    public ByteBuf touch() {
        return this;
    }

    @Override
    public ByteBuf touch(Object hint) {
        return this;
    }

    @Override
    public boolean release() {
        return false;
    }

    @Override
    public boolean release(int decrement) {
        return false;
    }
}
