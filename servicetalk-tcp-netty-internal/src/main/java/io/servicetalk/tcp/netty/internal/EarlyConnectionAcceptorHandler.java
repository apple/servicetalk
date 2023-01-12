/*
 * Copyright © 2023 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.tcp.netty.internal;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

import java.util.ArrayDeque;
import java.util.NoSuchElementException;
import java.util.Queue;
import javax.annotation.Nullable;

/**
 * Netty handler which queues pipeline events until {@link #releaseEvents()} is called or the handler is removed.
 */
class EarlyConnectionAcceptorHandler extends ChannelInboundHandlerAdapter {

    private final Queue<Runnable> queuedEvents = new ArrayDeque<>();
    private boolean queueingEnabled = true;
    @Nullable
    private ChannelHandlerContext channelHandlerContext;

    /**
     * Releases all queued events and disables further queueing (all further events will be propagated immediately).
     */
    public void releaseEvents() {
        assert channelHandlerContext != null;

        if (queueingEnabled) {
            queueingEnabled = false;

            while (true) {
                Runnable event = queuedEvents.poll();
                if (event == null) {
                    break;
                }
                event.run();
            }

            try {
                channelHandlerContext.pipeline().remove(this);
            } catch (NoSuchElementException ex) {
                // Ignore - the handler has been already removed and called from handlerRemoved
            }
        }
    }

    private void enqueueOrRunEvent(final Runnable event) {
        if (queueingEnabled) {
            if (!queuedEvents.offer(event)) {
                throw new RuntimeException("Failed to enqueue (offer) events for the EarlyConnectionAcceptor");
            }
        } else {
            event.run();
        }
    }

    @Override
    public void handlerAdded(final ChannelHandlerContext ctx) throws Exception {
        channelHandlerContext = ctx;
        super.handlerAdded(ctx);
    }

    @Override
    public void handlerRemoved(final ChannelHandlerContext ctx) throws Exception {
        releaseEvents();
        super.handlerRemoved(ctx);
    }

    @Override
    public void channelRegistered(final ChannelHandlerContext ctx) {
        enqueueOrRunEvent(ctx::fireChannelRegistered);
    }

    @Override
    public void channelUnregistered(final ChannelHandlerContext ctx) {
        enqueueOrRunEvent(ctx::fireChannelUnregistered);
    }

    @Override
    public void channelActive(final ChannelHandlerContext ctx) {
        enqueueOrRunEvent(ctx::fireChannelActive);
    }

    @Override
    public void channelInactive(final ChannelHandlerContext ctx) {
        enqueueOrRunEvent(ctx::fireChannelInactive);
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) throws Exception {
        enqueueOrRunEvent(() -> ctx.fireChannelRead(msg));
    }

    @Override
    public void channelReadComplete(final ChannelHandlerContext ctx) {
        enqueueOrRunEvent(ctx::fireChannelReadComplete);
    }

    @Override
    public void userEventTriggered(final ChannelHandlerContext ctx, final Object evt) throws Exception {
        enqueueOrRunEvent(() -> ctx.fireUserEventTriggered(evt));
    }

    @Override
    public void channelWritabilityChanged(final ChannelHandlerContext ctx) {
        enqueueOrRunEvent(ctx::fireChannelWritabilityChanged);
    }

    @Override
    public void exceptionCaught(final ChannelHandlerContext ctx, final Throwable cause) {
        enqueueOrRunEvent(() -> ctx.fireExceptionCaught(cause));
    }
}
