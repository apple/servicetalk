/**
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
package io.servicetalk.transport.netty.internal;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufHolder;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoop;
import io.servicetalk.buffer.Buffer;
import io.servicetalk.buffer.BufferAllocator;
import io.servicetalk.buffer.BufferHolder;

import static io.servicetalk.buffer.netty.BufferUtil.extractByteBufOrCreate;
import static io.servicetalk.buffer.netty.BufferUtil.newBufferFrom;

/**
 * A {@link ChannelHandler} that converts does the following conversions:
 *
 * <ul>
 *     <li>{@link Buffer} to {@link ByteBuf} for writes.</li>
 *     <li>{@link BufferHolder} to {@link ByteBuf} for writes.</li>
 *     <li>{@link ByteBuf} to {@link Buffer} for reads.</li>
 *     <li>{@link ByteBufHolder} to {@link Buffer} for reads.</li>
 * </ul>
 *
 * This also releases any {@link ByteBuf} once converted to {@link Buffer}.
 */
public final class BufferHandler extends RefCountedTrapper {

    /**
     * New instance.
     *
     * @param allocator to use for any new {@link Buffer}s.
     */
    public BufferHandler(BufferAllocator allocator) {
        super(allocator);
    }

    @Override
    protected Object decode(EventLoop eventLoop, BufferAllocator allocator, Object msg) {
        if (msg instanceof ByteBuf) {
            return newBufferFrom((ByteBuf) msg);
        }
        if (msg instanceof ByteBufHolder) {
            ByteBufHolder holder = (ByteBufHolder) msg;
            ByteBuf byteBuf = holder.content();
            return newBufferFrom(byteBuf);
        }
        return msg;
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
        if (msg instanceof Buffer) {
            ctx.write(extractByteBufOrCreate((Buffer) msg), promise);
        } else if (msg instanceof BufferHolder) {
            ctx.write(extractByteBufOrCreate(((BufferHolder) msg).getContent()), promise);
        } else {
            ctx.write(msg, promise);
        }
    }
}
