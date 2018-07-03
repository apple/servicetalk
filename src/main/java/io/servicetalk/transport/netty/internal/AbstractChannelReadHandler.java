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

import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Publisher;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandler;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.ReferenceCounted;
import org.reactivestreams.Subscriber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Predicate;
import javax.annotation.Nullable;

import static java.util.Objects.requireNonNull;

/**
 * An abstract {@link ChannelInboundHandler} that can be used to read off a {@link Channel} as a {@link Publisher}.
 *
 * @param <T> Type of elements emitted by the {@link Publisher} created by this handler.
 */
public abstract class AbstractChannelReadHandler<T> extends ChannelInboundHandlerAdapter {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractChannelReadHandler.class);

    @Nullable
    private NettyChannelPublisher<T> publisher;
    private final Predicate<T> isTerminal;
    private final Executor executor;

    /**
     * New instance.
     * It auto-releases any netty {@link ReferenceCounted} objects after emitting it from the {@link Publisher}.
     *
     * @param isTerminal {@link Predicate} for detecting terminal events per {@link Subscriber} of the emitted
     * {@link Publisher}.
     * @param executor {@link Executor} to create the {@link Publisher} passed to
     * {@link #onPublisherCreation(ChannelHandlerContext, Publisher)}.
     */
    protected AbstractChannelReadHandler(Predicate<T> isTerminal, Executor executor) {
        this.isTerminal = requireNonNull(isTerminal);
        this.executor = executor;
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        if (ctx.channel().isActive()) {
            assert publisher == null : "Publisher must be null";
            createPublisher(ctx);
        }
    }

    @Override
    public final void channelActive(ChannelHandlerContext ctx) {
        // The publisher may have been created by handlerAdded(...) already.
        if (publisher == null) {
            createPublisher(ctx);
        }
        ctx.fireChannelActive();
    }

    private void createPublisher(ChannelHandlerContext ctx) {
        publisher = new NettyChannelPublisher<>(ctx.channel(), isTerminal);
        onPublisherCreation(ctx, publisher);
    }

    @Override
    public final void channelInactive(ChannelHandlerContext ctx) {
        if (publisher != null) {
            publisher.channelInactive();
            publisher = null;
        }
        ctx.fireChannelInactive();
    }

    @Override
    public final void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (publisher != null) {
            publisher.exceptionCaught(cause);
        } else {
            LOGGER.error("Exception caught, but no publisher exists. Force closing channel={}", ctx.channel(), cause);
            ctx.close();
        }
    }

    @Override
    public final void channelRead(ChannelHandlerContext ctx, Object msg) {
        @SuppressWarnings("unchecked")
        T t = (T) msg;
        assert publisher != null;
        publisher.channelRead(t);
        ctx.fireChannelRead(msg);
    }

    @Override
    public final void channelReadComplete(ChannelHandlerContext ctx) {
        assert publisher != null;
        publisher.onReadComplete();
        ctx.fireChannelReadComplete();
    }

    /**
     * Callback to consume {@link Publisher} created for reading data from this {@link Channel}.
     * This method will be invoked at most once, for each time this channel becomes active.
     *
     * @param ctx Netty's {@link ChannelHandlerContext}.
     * @param newPublisher A newly created {@link Publisher} for current {@link Channel}.
     */
    protected abstract void onPublisherCreation(ChannelHandlerContext ctx, Publisher<T> newPublisher);
}
