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
import io.servicetalk.buffer.api.BufferAllocator;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.EventLoop;
import io.netty.util.ReferenceCounted;

import static io.netty.util.ReferenceCountUtil.release;
import static io.netty.util.ReferenceCountUtil.safeRelease;
import static java.util.Objects.requireNonNull;

/**
 * A {@link ChannelDuplexHandler} that makes sure no {@link ReferenceCounted} objects leak from netty's pipeline.
 */
public abstract class RefCountedTrapper extends ChannelDuplexHandler {

    protected final BufferAllocator allocator;

    /**
     * Creates a new instance.
     *
     * @param allocator {@link BufferAllocator} to allocate new {@link Buffer}s for read messages.
     */
    protected RefCountedTrapper(BufferAllocator allocator) {
        this.allocator = requireNonNull(allocator);
    }

    @Override
    public final void channelRead(ChannelHandlerContext ctx, Object msg) {
        try {
            msg = decode(ctx.channel().eventLoop(), allocator, msg);
        } catch (Throwable throwable) {
            safeRelease(msg);
            ctx.fireExceptionCaught(throwable);
            ctx.close();
            return;
        }

        if (msg instanceof ReferenceCounted) {
            release(msg);
            ctx.close();
            throw new IllegalStateException("No reference counted objects are allowed to leave netty's pipeline, closing the channel. Object found: " + msg.getClass());
        } else {
            ctx.fireChannelRead(msg);
        }
    }

    /**
     * Decodes {@code msg} read from the pipeline into a non-ref-counted object.
     *
     * @param eventLoop Eventloop for this channel.
     * @param allocator Buffer allocator.
     * @param msg to handle.
     * @return Message to send to the rest of the pipeline.
     *
     * @throws Exception Any failure while decoding.
     */
    protected abstract Object decode(EventLoop eventLoop, BufferAllocator allocator, Object msg) throws Exception;
}
