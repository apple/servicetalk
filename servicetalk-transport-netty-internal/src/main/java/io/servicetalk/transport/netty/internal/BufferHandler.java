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
package io.servicetalk.transport.netty.internal;

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.buffer.api.BufferHolder;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufHolder;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;

import static io.servicetalk.buffer.netty.BufferUtils.extractByteBufOrCreate;
import static io.servicetalk.buffer.netty.BufferUtils.newBufferFrom;

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
 *
 * @deprecated This API is going to be removed in the next minor version with no planned replacement. If it cannot be
 *             removed from your application, consider copying it into your codebase.
 */
@Sharable
@Deprecated // FIXME: 0.43 - Remove deprecation and copy into tests
public final class BufferHandler extends ChannelDuplexHandler {
    public static final ChannelDuplexHandler INSTANCE = new BufferHandler();

    private BufferHandler() {
        // singleton
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof ByteBuf) {
            ctx.fireChannelRead(newBufferFrom((ByteBuf) msg));
        } else if (msg instanceof ByteBufHolder) {
            ByteBufHolder holder = (ByteBufHolder) msg;
            ByteBuf byteBuf = holder.content();
            ctx.fireChannelRead(newBufferFrom(byteBuf));
        } else {
            ctx.fireChannelRead(msg);
        }
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
        if (msg instanceof Buffer) {
            ctx.write(extractByteBufOrCreate((Buffer) msg), promise);
        } else if (msg instanceof BufferHolder) {
            ctx.write(extractByteBufOrCreate(((BufferHolder) msg).content()), promise);
        } else {
            ctx.write(msg, promise);
        }
    }
}
