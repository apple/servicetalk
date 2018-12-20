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
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;

final class UnreleasableCompositeByteBuf extends CompositeByteBuf {

    UnreleasableCompositeByteBuf(ByteBufAllocator alloc, boolean direct, int maxNumComponents) {
        super(alloc, direct, maxNumComponents);
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
    public CompositeByteBuf retain(int increment) {
        return this;
    }

    @Override
    public CompositeByteBuf retain() {
        return this;
    }

    @Override
    public CompositeByteBuf touch() {
        return this;
    }

    @Override
    public CompositeByteBuf touch(Object hint) {
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
