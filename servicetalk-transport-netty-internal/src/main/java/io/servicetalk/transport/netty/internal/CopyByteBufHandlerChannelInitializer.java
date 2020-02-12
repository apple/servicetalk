/*
 * Copyright © 2019 Apple Inc. and the ServiceTalk project authors
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
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandler;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.ReferenceCountUtil;

/**
 * Initializer to configure {@link ChannelInboundHandler} that will ensure no pooled {@link ByteBuf}s are passed to
 * the user and so no leaks are produced if the user does not call {@link ReferenceCountUtil#release(Object)}.
 */
public final class CopyByteBufHandlerChannelInitializer implements ChannelInitializer {

    /**
     * {@link PooledByteBufAllocator} to use internally when we know memory won't be leaked.
     */
    public static final PooledByteBufAllocator POOLED_ALLOCATOR = PooledByteBufAllocator.DEFAULT;

    private final CopyByteBufHandler copyHandler;

    /**
     * Creates a new instance.
     *
     * @param alloc {@link ByteBufAllocator} to allocate unpooled memory.
     */
    public CopyByteBufHandlerChannelInitializer(final ByteBufAllocator alloc) {
        copyHandler = new CopyByteBufHandler(alloc);
    }

    @Override
    public void init(final Channel channel) {
        channel.pipeline().addLast(copyHandler);
    }

    /**
     * This handler has to be added to the {@link ChannelPipeline} when {@link PooledByteBufAllocator} is used for
     * reading data from the socket. The allocated {@link ByteBuf}s must be copied and released before handed over to
     * the user.
     */
    @Sharable
    private static final class CopyByteBufHandler extends SimpleChannelInboundHandler<ByteBuf> {

        private final ByteBufAllocator alloc;

        private CopyByteBufHandler(final ByteBufAllocator alloc) {
            if (alloc.isDirectBufferPooled()) {
                throw new IllegalArgumentException("ByteBufAllocator must be unpooled");
            }
            this.alloc = alloc;
        }

        @Override
        protected void channelRead0(final ChannelHandlerContext ctx, final ByteBuf buf) {
            // We must not release the incoming buf here because it will be released by SimpleChannelInboundHandler
            ctx.fireChannelRead(alloc.buffer(buf.readableBytes()).writeBytes(buf));
        }
    }
}
