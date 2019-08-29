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
import io.netty.channel.ChannelHandler;
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

import javax.annotation.Nullable;

import static java.util.Objects.requireNonNull;

/**
 * Utilities to configure pooled {@link RecvByteBufAllocator} for reading data from the socket.
 */
public final class PooledRecvByteBufAllocatorUtils {

    private static final ChannelInitializer POOLED_RECV_ALLOCATOR_INITIALIZER = (channel, context) -> {
        final RecvByteBufAllocator recvByteBufAllocator = channel.config().getRecvByteBufAllocator();
        channel.config().setRecvByteBufAllocator(new PooledRecvByteBufAllocator(recvByteBufAllocator));
        return context;
    };

    private static final ChannelInitializer COPY_HANDLER_INITIALIZER = (channel, context) -> {
        channel.pipeline().addLast(CopyByteBufHandler.INSTANCE);
        return context;
    };

    private static final ChannelInitializer POOLED_RECV_ALLOCATOR_THEN_COPY_INITIALIZER =
            POOLED_RECV_ALLOCATOR_INITIALIZER.andThen(COPY_HANDLER_INITIALIZER);

    private PooledRecvByteBufAllocatorUtils() {
        // No instances
    }

    /**
     * Wraps provided {@link ChannelInitializer} with properly configured pooled {@link RecvByteBufAllocator}.
     *
     * @param channelInitializer the {@link ChannelInitializer} to wrap
     * @return {@link ChannelInitializer} that properly configures pooled {@link RecvByteBufAllocator}
     */
    public static ChannelInitializer wrap(@Nullable final ChannelInitializer channelInitializer) {
        return channelInitializer == null ? POOLED_RECV_ALLOCATOR_THEN_COPY_INITIALIZER :
                POOLED_RECV_ALLOCATOR_INITIALIZER.andThen(channelInitializer).andThen(COPY_HANDLER_INITIALIZER);
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
            return delegate().allocate(alloc.isDirectBufferPooled() ? alloc : PooledByteBufAllocator.DEFAULT);
        }

        @Override
        public boolean continueReading(final UncheckedBooleanSupplier uncheckedBooleanSupplier) {
            return ((ExtendedHandle) delegate()).continueReading(uncheckedBooleanSupplier);
        }
    }

    /**
     * {@link ChannelInboundHandler} implementation that will ensure no pooled {@link ByteBuf}s are passed to the user
     * and so no leaks are produced if the user does not call {@link ReferenceCountUtil#release(Object)}.
     * <p>
     * This handler has to be added to the {@link ChannelPipeline} when {@link PooledRecvByteBufAllocator} is used for
     * reading data from the socket. The allocated {@link ByteBuf}s must be copied and released before handed over to
     * the user.
     */
    @Sharable
    private static final class CopyByteBufHandler extends SimpleChannelInboundHandler<ByteBuf> {

        static final ChannelHandler INSTANCE = new CopyByteBufHandler();

        private CopyByteBufHandler() {
            // Singleton
        }

        @Override
        protected void channelRead0(final ChannelHandlerContext ctx, final ByteBuf buf) {
            final ByteBuf buffer = ctx.alloc().heapBuffer(buf.readableBytes());
            // We must not release the incoming buf here because it will be released by SimpleChannelInboundHandler
            ctx.fireChannelRead(buffer.writeBytes(buf));
        }
    }
}
