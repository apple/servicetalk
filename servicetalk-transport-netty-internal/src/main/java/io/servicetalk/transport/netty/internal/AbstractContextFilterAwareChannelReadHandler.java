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

import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.transport.api.ContextFilter;

import io.netty.channel.ChannelHandlerContext;
import org.reactivestreams.Subscriber;

import java.util.function.Predicate;

import static io.servicetalk.transport.netty.internal.ContextFilterSuccessful.COMPLETED;

/**
 * Extends {@link AbstractChannelReadHandler} to provide notification of when the {@link ContextFilter} is done.
 *
 * @param <T> Type of elements emitted by the {@link Publisher} created by this handler.
 */
public abstract class AbstractContextFilterAwareChannelReadHandler<T> extends AbstractChannelReadHandler<T> {

    private boolean contextFilterSuccessful;

    /**
     * New instance.
     * See {@link AbstractChannelReadHandler#AbstractChannelReadHandler(Predicate, CloseHandler)}.
     *
     * @param isTerminal {@link Predicate} for detecting terminal events per {@link Subscriber} of the emitted
     * {@link Publisher}.
     * @param closeHandler {@link CloseHandler} used for this channel.
     */
    protected AbstractContextFilterAwareChannelReadHandler(Predicate<T> isTerminal, final CloseHandler closeHandler) {
        super(isTerminal, closeHandler);
    }

    @Override
    final void createPublisher(final ChannelHandlerContext ctx) {
        super.createPublisher(ctx);
        if (contextFilterSuccessful) {
            onContextFilterSuccess(ctx);
        }
    }

    @Override
    public void userEventTriggered(final ChannelHandlerContext ctx, final Object evt) throws Exception {
        if (evt == COMPLETED) {
            contextFilterSuccessful = true;
            if (ctx.channel().isActive()) {
                onContextFilterSuccess(ctx);
            }
        }
        super.userEventTriggered(ctx, evt);
    }

    /**
     * Callback to indicate the {@link ContextFilter} completed successfully, and the channel can now be read from and
     * written to.
     *
     * @param ctx Netty's {@link ChannelHandlerContext}.
     */
    protected abstract void onContextFilterSuccess(ChannelHandlerContext ctx);
}
