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

import io.servicetalk.transport.api.ConnectionContext;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.AdaptiveRecvByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandler;
import io.netty.channel.MaxMessagesRecvByteBufAllocator;
import io.netty.channel.RecvByteBufAllocator;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.UncheckedBooleanSupplier;
import io.netty.util.concurrent.FastThreadLocal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.lang.Math.max;
import static java.util.Objects.requireNonNull;

/**
 * Initializes the channel with pooled {@link RecvByteBufAllocator} for reading from the socket.
 */
public final class PooledRecvByteBufAllocatorInitializer implements ChannelInitializer {
    @Override
    public ConnectionContext init(final Channel channel, final ConnectionContext context) {
        channel.config().setRecvByteBufAllocator(new ThreadLocalRecvByteBufAllocator(
                new AdaptiveRecvByteBufAllocator(512, 32768, 65536)
                        .respectMaybeMoreData(false)
                        .maxMessagesPerRead(4)));
        channel.pipeline().addFirst(CopyByteBufHandler.INSTANCE);
        return context;
    }

    /**
     * {@link MaxMessagesRecvByteBufAllocator} which allocates {@link ByteBuf}s that are cached in a {@link ThreadLocal}
     * if the {@link ByteBufAllocator} used for allocation does not pool direct {@link ByteBuf}s.
     */
    private static final class ThreadLocalRecvByteBufAllocator implements MaxMessagesRecvByteBufAllocator {

        private static final Logger LOGGER = LoggerFactory.getLogger(ThreadLocalRecvByteBufAllocator.class);

        private static final int CAPACITY;

        static {
            int capacity = Integer.getInteger("io.servicetalk.threadLocalRecvCapacity", 128 * 1024);
            LOGGER.debug("-Dio.servicetalk.threadLocalRecvCapacity: {}", capacity);
            CAPACITY = max(capacity, 1024);
        }

        private static final FastThreadLocal<ByteBuf> BUFFER = new FastThreadLocal<ByteBuf>() {
            @Override
            protected ByteBuf initialValue() {
                // We don't allow releasing the buffer by default.
                return Unpooled.unreleasableBuffer(UnpooledByteBufAllocator.DEFAULT.directBuffer(CAPACITY, CAPACITY));
            }

            @Override
            protected void onRemoval(final ByteBuf value) {
                // Unwrap so we can release it.
                final ByteBuf wrapped = value.unwrap();
                if (wrapped != null) {
                    wrapped.release();
                }
            }
        };

        private final MaxMessagesRecvByteBufAllocator alloc;

        /**
         * Create a new instance.
         *
         * @param alloc the {@link MaxMessagesRecvByteBufAllocator}
         */
        ThreadLocalRecvByteBufAllocator(final MaxMessagesRecvByteBufAllocator alloc) {
            this.alloc = requireNonNull(alloc);
        }

        @Override
        public int maxMessagesPerRead() {
            return alloc.maxMessagesPerRead();
        }

        @Override
        public ThreadLocalRecvByteBufAllocator maxMessagesPerRead(final int maxMessagesPerRead) {
            alloc.maxMessagesPerRead(maxMessagesPerRead);
            return this;
        }

        @Override
        public ExtendedHandle newHandle() {
            return new ThreadLocalHandle((ExtendedHandle) alloc.newHandle());
        }

        private static final class ThreadLocalHandle extends DelegatingHandle implements ExtendedHandle {

            ThreadLocalHandle(final ExtendedHandle handle) {
                super(handle);
            }

            @Override
            public ByteBuf allocate(final ByteBufAllocator alloc) {
                if (alloc.isDirectBufferPooled()) {
                    return delegate().allocate(alloc);
                }

                final ByteBuf buffer = BUFFER.get();

                final int size = guess();
                if (buffer.capacity() > size && size > 0) {
                    // Slice out a section of the buffer that match what we guessed in terms of size and clear the
                    // readerIndex and writerIndex.
                    return buffer.slice(0, size).clear();
                }
                // Clear the reader and writer index.
                return buffer.clear();
            }

            @Override
            public boolean continueReading(final UncheckedBooleanSupplier uncheckedBooleanSupplier) {
                return ((ExtendedHandle) delegate()).continueReading(uncheckedBooleanSupplier);
            }
        }
    }

    /**
     * {@link ChannelInboundHandler} implementation that will ensure no pooled {@link ByteBuf}s are passed to the user
     * and so no leaks are produced if the user not call {@link ReferenceCountUtil#release(Object)}.
     * <p>
     * {@link ThreadLocalRecvByteBufAllocator} will use thread-local based or pooled direct {@link ByteBuf}s for reading
     * from the socket. These buffers must be copied and released before handed over to the user. This handler ensures
     * the user not need to care about releasing buffers at all.
     */
    @Sharable
    private static final class CopyByteBufHandler extends SimpleChannelInboundHandler<ByteBuf> {

        public static final ChannelHandler INSTANCE = new CopyByteBufHandler();

        private CopyByteBufHandler() {
            // Singleton
        }

        @Override
        protected void channelRead0(final ChannelHandlerContext ctx, final ByteBuf buf) {
            // We must not release the incoming message here because it will be released by SimpleChannelInboundHandler
            ctx.fireChannelRead(copy(ctx.alloc(), buf));
        }

        /**
         * Return a non pooled copy of the message.
         *
         * @param allocator the {@link ByteBufAllocator} to use
         * @param buf the {@link ByteBuf} to copy
         * @return an unpooled copy of the {@link ByteBuf}
         */
        private static ByteBuf copy(final ByteBufAllocator allocator, final ByteBuf buf) {
            final ByteBuf buffer = allocator.heapBuffer(buf.readableBytes());
            return buffer.writeBytes(buf);
        }
    }
}
