/*
 * Copyright © 2019-2020 Apple Inc. and the ServiceTalk project authors
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
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.ReferenceCounted;

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
     * @param unpooledAllocator {@link ByteBufAllocator} to allocate unpooled memory.
     * @throws IllegalArgumentException if the provided {@code unpooledAllocator} is not unpooled.
     */
    public CopyByteBufHandlerChannelInitializer(final ByteBufAllocator unpooledAllocator) {
        copyHandler = new CopyByteBufHandler(unpooledAllocator);
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
    static final class CopyByteBufHandler extends ChannelInboundHandlerAdapter {

        private final ByteBufAllocator unpooledAllocator;

        CopyByteBufHandler(final ByteBufAllocator unpooledAllocator) {
            if (unpooledAllocator.isDirectBufferPooled()) {
                throw new IllegalArgumentException("ByteBufAllocator must be unpooled");
            }
            this.unpooledAllocator = unpooledAllocator;
        }

        @Override
        public void channelRead(final ChannelHandlerContext ctx, final Object msg) {
            boolean release = true;
            try {
                if (msg instanceof ByteBuf) {
                    final ByteBuf original = (ByteBuf) msg;
                    assert original.alloc().isDirectBufferPooled();

                    final ByteBuf unpooled = unpooledAllocator.buffer(original.readableBytes()).writeBytes(original);
                    original.release();
                    release = false;
                    ctx.fireChannelRead(unpooled);
                } else if (msg instanceof ReferenceCounted) {
                    throw new IllegalArgumentException("Unexpected ReferenceCounted msg: " + msg.getClass() +
                            ", expected: io.netty.buffer.ByteBuf");
                } else {
                    release = false;
                    ctx.fireChannelRead(msg);
                }
            } finally {
                if (release) {
                    ReferenceCountUtil.release(msg);
                }
            }
        }
    }
}
