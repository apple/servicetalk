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
package io.servicetalk.tcp.netty.internal;

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.internal.SequentialCancellable;
import io.servicetalk.transport.api.ConnectionAcceptor;
import io.servicetalk.transport.api.ConnectionContext;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.EventLoop;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.RejectedExecutionException;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Executors.immediate;
import static io.servicetalk.transport.netty.internal.ContextFilterSuccessful.COMPLETED;

abstract class AbstractContextFilterChannelHandler extends ChannelDuplexHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractContextFilterChannelHandler.class);

    private static final byte READ_SUPPRESSED = 0;
    private static final byte PENDING_READ = 1;
    private static final byte READ_ALLOWED = 2;
    private static final byte CHANNEL_INACTIVE = 3;

    private final ConnectionContext context;
    private final ConnectionAcceptor connectionAcceptor;
    private final Executor executor;
    private final SequentialCancellable sequentialCancellable;
    private byte state;

    AbstractContextFilterChannelHandler(final ConnectionContext context, final ConnectionAcceptor connectionAcceptor,
                                        final Executor executor) {
        this.context = context;
        this.connectionAcceptor = connectionAcceptor;
        this.executor = executor;
        sequentialCancellable = new SequentialCancellable();
    }

    final void executeContextFilter(final ChannelHandlerContext ctx) {
        assert ctx.channel().eventLoop().inEventLoop();
        if (executor == immediate()) {
            runContextFilter(ctx);
        } else {
            try {
                executor.execute(() -> runContextFilter(ctx));
            } catch (RejectedExecutionException e) {
                // If we fail to execute the ContextFilter then nothing else will happen for this connection.
                // Netty will fire exceptionCaught event on the pipeline, but will not close the channel. Since, no
                // ServiceTalk code specific to the connection will be invoked, we will not have visibility of this
                // exception, eg: we will not subscribe to the read stream, neither will we write anything, so we
                // would never know about this connection.
                LOGGER.warn("Failed to enqueue task to the executor {} for context filter {} for context {}.",
                        executor, connectionAcceptor, context, e);
                ctx.close();
            }
        }
    }

    abstract void onContextFilterSuccessful(ChannelHandlerContext ctx);

    private void runContextFilter(final ChannelHandlerContext ctx) {
        try {
            connectionAcceptor.accept(context).subscribe(new Single.Subscriber<Boolean>() {
                @Override
                public void onSubscribe(final Cancellable cancellable) {
                    sequentialCancellable.setNextCancellable(cancellable);
                }

                @Override
                public void onSuccess(@Nullable final Boolean result) {
                    if (result != null && result) {
                        // handleSuccess makes multiple calls that will queue things on the event loop already, so we
                        // offload the whole method to the event loop. This also ensures handleSuccess is only run from
                        // the event loop, which means state doesn't need to be handled concurrently.
                        final EventLoop eventLoop = ctx.channel().eventLoop();
                        if (eventLoop.inEventLoop()) {
                            handleSuccess(ctx);
                        } else {
                            eventLoop.execute(() -> handleSuccess(ctx));
                        }
                    } else {
                        // Getting the remote-address may involve volatile reads and potentially a syscall, so guard it.
                        if (LOGGER.isDebugEnabled()) {
                            LOGGER.debug("Rejected connection from {}", context.remoteAddress());
                        }
                        ctx.close();
                    }
                }

                @Override
                public void onError(final Throwable t) {
                    LOGGER.warn("Error from context filter {} for context {}.", connectionAcceptor, context, t);
                    ctx.close();
                }
            });
        } catch (Throwable t) {
            LOGGER.warn("Exception from context filter {} for context {}.", connectionAcceptor, context, t);
            ctx.close();
        }
    }

    @Override
    public void channelInactive(final ChannelHandlerContext ctx) {
        state = CHANNEL_INACTIVE;
        try {
            sequentialCancellable.cancel();
        } finally {
            ctx.fireChannelInactive();
        }
    }

    private void handleSuccess(final ChannelHandlerContext ctx) {
        final byte oldState = state;
        if (oldState == CHANNEL_INACTIVE) {
            return;
        }

        // Getting the remote-address may involve volatile reads and potentially a syscall, so guard it.
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Accepted connection from {}", context.remoteAddress());
        }

        state = READ_ALLOWED;
        if (oldState == PENDING_READ) {
            ctx.read();
        }

        onContextFilterSuccessful(ctx);
        ctx.fireUserEventTriggered(COMPLETED);

        ctx.pipeline().remove(this);
    }

    @Override
    public void read(final ChannelHandlerContext ctx) {
        switch (state) {
            case READ_ALLOWED:
                ctx.read();
                break;
            case READ_SUPPRESSED:
                state = PENDING_READ;
                break;
            default:
                break;
        }
    }
}
