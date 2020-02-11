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

import io.servicetalk.buffer.api.BufferAllocator;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandler;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.DefaultMaxMessagesRecvByteBufAllocator;
import io.netty.channel.MaxMessagesRecvByteBufAllocator;
import io.netty.channel.RecvByteBufAllocator;
import io.netty.channel.RecvByteBufAllocator.DelegatingHandle;
import io.netty.channel.RecvByteBufAllocator.ExtendedHandle;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.UncheckedBooleanSupplier;

import java.util.function.Function;

import static io.servicetalk.buffer.netty.BufferUtils.getByteBufAllocator;
import static java.util.Objects.requireNonNull;

/**
 * Utilities to configure pooled {@link RecvByteBufAllocator} for reading data from the socket.
 */
public final class PooledRecvByteBufAllocatorInitializers {

    /**
     * {@link PooledByteBufAllocator} to use internally when we know memory won't be leaked.
     */
    public static final PooledByteBufAllocator POOLED_ALLOCATOR = PooledByteBufAllocator.DEFAULT;

    /**
     * Initializer to configure {@link RecvByteBufAllocator} backed by a pooled {@link ByteBufAllocator}.
     */
    public static final ChannelInitializer POOLED_RECV_ALLOCATOR_INITIALIZER = channel ->
            channel.config().setRecvByteBufAllocator(new PooledRecvByteBufAllocator(
                    channel.config().getRecvByteBufAllocator()));

    /**
     * Initializer to configure {@link ChannelInboundHandler} that will ensure no pooled {@link ByteBuf}s are passed to
     * the user and so no leaks are produced if the user does not call {@link ReferenceCountUtil#release(Object)}.
     */
    public static final Function<BufferAllocator, ChannelInitializer> COPY_HANDLER_INITIALIZER =
            CopyByteBufHandlerChannelInitializer::new;

    private PooledRecvByteBufAllocatorInitializers() {
        // No instances
    }

    /**
     * {@link MaxMessagesRecvByteBufAllocator} that makes sure a
     * {@link ByteBufAllocator#isDirectBufferPooled() direct pooled allocator} will be used to allocate
     * {@link ByteBuf}s.
     */
    private static final class PooledRecvByteBufAllocator implements MaxMessagesRecvByteBufAllocator {

        private final MaxMessagesRecvByteBufAllocator alloc;

        PooledRecvByteBufAllocator(final RecvByteBufAllocator alloc) {
            if (alloc instanceof MaxMessagesRecvByteBufAllocator) {
                this.alloc = (MaxMessagesRecvByteBufAllocator) alloc;
            } else {
                this.alloc = new MaxMessagesRecvByteBufAllocatorAdapter(alloc);
            }
        }

        @Override
        public int maxMessagesPerRead() {
            return alloc.maxMessagesPerRead();
        }

        @Override
        public MaxMessagesRecvByteBufAllocator maxMessagesPerRead(final int maxMessagesPerRead) {
            alloc.maxMessagesPerRead(maxMessagesPerRead);
            return this;
        }

        @Override
        public ExtendedHandle newHandle() {
            return new PooledHandle((ExtendedHandle) alloc.newHandle());
        }
    }

    private static final class MaxMessagesRecvByteBufAllocatorAdapter extends DefaultMaxMessagesRecvByteBufAllocator {

        private final RecvByteBufAllocator delegate;

        MaxMessagesRecvByteBufAllocatorAdapter(final RecvByteBufAllocator delegate) {
            this.delegate = requireNonNull(delegate);
        }

        @SuppressWarnings("deprecation")
        @Override
        public Handle newHandle() {
            return delegate.newHandle();
        }
    }

    private static final class PooledHandle extends DelegatingHandle implements ExtendedHandle {

        PooledHandle(final ExtendedHandle handle) {
            super(handle);
        }

        @Override
        public ByteBuf allocate(final ByteBufAllocator alloc) {
            return delegate().allocate(alloc.isDirectBufferPooled() ? alloc : POOLED_ALLOCATOR);
        }

        @Override
        public boolean continueReading(final UncheckedBooleanSupplier uncheckedBooleanSupplier) {
            return ((ExtendedHandle) delegate()).continueReading(uncheckedBooleanSupplier);
        }
    }

    private static final class CopyByteBufHandlerChannelInitializer implements ChannelInitializer {

        private final CopyByteBufHandler handler;

        private CopyByteBufHandlerChannelInitializer(final BufferAllocator alloc) {
            handler = new CopyByteBufHandler(getByteBufAllocator(alloc));
        }

        @Override
        public void init(final Channel channel) {
            channel.pipeline().addLast(handler);
        }
    }

    /**
     * This handler has to be added to the {@link ChannelPipeline} when {@link PooledRecvByteBufAllocator} is used for
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
            ctx.fireChannelRead(alloc.heapBuffer(buf.readableBytes()).writeBytes(buf));
        }
    }
}
