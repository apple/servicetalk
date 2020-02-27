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
package io.servicetalk.http.netty;

import io.servicetalk.buffer.api.BufferAllocator;
import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.CompletableSource;
import io.servicetalk.concurrent.SingleSource;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Executor;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.internal.DelayedCancellable;
import io.servicetalk.http.api.DefaultHttpExecutionContext;
import io.servicetalk.http.api.HttpExecutionContext;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.transport.netty.internal.FlushStrategy;
import io.servicetalk.transport.netty.internal.FlushStrategyHolder;
import io.servicetalk.transport.netty.internal.NettyChannelListenableAsyncCloseable;
import io.servicetalk.transport.netty.internal.NettyConnectionContext;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOutboundInvoker;
import io.netty.channel.EventLoop;
import io.netty.handler.codec.http2.DefaultHttp2GoAwayFrame;
import io.netty.handler.codec.http2.DefaultHttp2PingFrame;
import io.netty.handler.codec.http2.Http2GoAwayFrame;
import io.netty.handler.codec.http2.Http2PingFrame;
import io.netty.handler.codec.http2.Http2SettingsAckFrame;
import io.netty.handler.codec.http2.Http2SettingsFrame;
import io.netty.handler.ssl.SslHandshakeCompletionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.SocketAddress;
import java.net.SocketOption;
import java.nio.channels.ClosedChannelException;
import java.util.concurrent.Delayed;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import javax.annotation.Nullable;
import javax.net.ssl.SSLSession;

import static io.netty.handler.codec.http2.Http2Error.NO_ERROR;
import static io.netty.util.ReferenceCountUtil.release;
import static io.servicetalk.concurrent.api.Processors.newCompletableProcessor;
import static io.servicetalk.concurrent.api.Processors.newSingleProcessor;
import static io.servicetalk.concurrent.api.SourceAdapters.fromSource;
import static io.servicetalk.concurrent.internal.ThrowableUtils.unknownStackTrace;
import static io.servicetalk.http.netty.H2ToStH1Utils.DEFAULT_GRACEFUL_SHUTDOWN_TIMEOUT_MILLIS;
import static io.servicetalk.transport.netty.internal.NettyIoExecutors.fromNettyEventLoop;
import static io.servicetalk.transport.netty.internal.NettyPipelineSslUtils.extractSslSession;
import static io.servicetalk.transport.netty.internal.SocketOptionUtils.getOption;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

class H2ParentConnectionContext extends NettyChannelListenableAsyncCloseable implements
                                                                             NettyConnectionContext {
    private static final ClosedChannelException CLOSED_CHANNEL_INACTIVE = unknownStackTrace(
            new ClosedChannelException(), H2ClientParentConnectionContext.class, "channelInactive(..)");
    private static final ClosedChannelException CLOSED_HANDLER_REMOVED =
            unknownStackTrace(new ClosedChannelException(), H2ClientParentConnectionContext.class,
                    "handlerRemoved(..)");
    private static final AtomicIntegerFieldUpdater<H2ParentConnectionContext> activeChildChannelsUpdater =
            AtomicIntegerFieldUpdater.newUpdater(H2ParentConnectionContext.class, "activeChildChannels");
    private static final Logger LOGGER = LoggerFactory.getLogger(H2ParentConnectionContext.class);
    private static final ScheduledFuture<?> GRACEFUL_CLOSE_PING_PENDING = new NoopScheduledFuture();
    private static final ScheduledFuture<?> GRACEFUL_CLOSE_PING_ACK_RECV = new NoopScheduledFuture();
    private static final long GRACEFUL_CLOSE_PING_CONTENT = ThreadLocalRandom.current().nextLong();
    private static final long GRACEFUL_CLOSE_PING_ACK_TIMEOUT_MS = 10000;
    final FlushStrategyHolder flushStrategyHolder;
    private final HttpExecutionContext executionContext;
    private final SingleSource.Processor<Throwable, Throwable> transportError = newSingleProcessor();
    private final CompletableSource.Processor onClosing = newCompletableProcessor();
    @Nullable
    final Long idleTimeoutMs;
    @Nullable
    private SSLSession sslSession;
    @Nullable
    private ScheduledFuture<?> gracefulCloseTimeoutFuture;
    private volatile int activeChildChannels;

    H2ParentConnectionContext(Channel channel, BufferAllocator allocator, Executor executor,
                              FlushStrategy flushStrategy, @Nullable Long idleTimeoutMs,
                              HttpExecutionStrategy executionStrategy) {
        super(channel, executor);
        this.executionContext = new DefaultHttpExecutionContext(allocator, fromNettyEventLoop(channel.eventLoop()),
                executor, executionStrategy);
        this.flushStrategyHolder = new FlushStrategyHolder(flushStrategy);
        this.idleTimeoutMs = idleTimeoutMs;
        // Just in case the channel abruptly closes, we should complete the onClosing Completable.
        onClose().subscribe(onClosing::onComplete);
    }

    @Override
    public final Cancellable updateFlushStrategy(final FlushStrategyProvider strategyProvider) {
        return flushStrategyHolder.updateFlushStrategy(strategyProvider);
    }

    @Override
    public FlushStrategy defaultFlushStrategy() {
        return flushStrategyHolder.currentStrategy();
    }

    @Override
    public final Single<Throwable> transportError() {
        return fromSource(transportError).publishOn(executionContext().executor());
    }

    @Override
    public final Completable onClosing() {
        return fromSource(onClosing).publishOn(executionContext().executor());
    }

    @Override
    public final SocketAddress localAddress() {
        return channel().localAddress();
    }

    @Override
    public final SocketAddress remoteAddress() {
        return channel().remoteAddress();
    }

    @Nullable
    @Override
    public final SSLSession sslSession() {
        return sslSession;
    }

    @Override
    public final HttpExecutionContext executionContext() {
        return executionContext;
    }

    @Nullable
    @Override
    public <T> T socketOption(final SocketOption<T> option) {
        return getOption(option, channel().config(), idleTimeoutMs);
    }

    @Override
    public final Channel nettyChannel() {
        return channel();
    }

    @Override
    protected final void doCloseAsyncGracefully() {
        EventLoop eventLoop = channel().eventLoop();
        if (eventLoop.inEventLoop()) {
            doCloseAsyncGracefully0();
        } else {
            try {
                eventLoop.execute(this::doCloseAsyncGracefully0);
            } catch (Throwable cause) {
                channel().close();
                LOGGER.warn("channel={} EventLoop rejected a task for graceful shutdown, force closing connection",
                        channel(), cause);
            }
        }
    }

    final void doCloseAsyncGracefully0() {
        if (gracefulCloseTimeoutFuture == null) {
            // Set the gracefulCloseTimeoutFuture before doing the write, because we will reference the state
            // when we receive the PING(ACK) to determine if action is necessary, and it is conceivable that the
            // write future may not be executed which sets the timer.
            gracefulCloseTimeoutFuture = GRACEFUL_CLOSE_PING_PENDING;

            onClosing.onComplete();

            // The graceful close process is described in [1]. In general it involves sending 2 GOAWAY frames. The first
            // GOAWAY has last-stream-id=<maximum stream ID> to indicate no new streams can be created, wait for 2 RTT
            // time duration for inflight frames to land, and the second GOAWAY includes the maximum known stream ID.
            // To account for 2 RTTs we can send a PING and when the PING(ACK) comes back we can send the second GOAWAY.
            // https://tools.ietf.org/html/rfc7540#section-6.8
            DefaultHttp2GoAwayFrame goAwayFrame = new DefaultHttp2GoAwayFrame(NO_ERROR);
            goAwayFrame.setExtraStreamIds(Integer.MAX_VALUE);
            channel().write(goAwayFrame);
            channel().writeAndFlush(new DefaultHttp2PingFrame(GRACEFUL_CLOSE_PING_CONTENT)).addListener(future -> {
                // If gracefulCloseTimeoutFuture is not GRACEFUL_CLOSE_PING_PENDING that means we have
                // already received the PING(ACK) and there is no need to apply the timeout.
                if (future.isSuccess() && gracefulCloseTimeoutFuture == GRACEFUL_CLOSE_PING_PENDING) {
                    gracefulCloseTimeoutFuture = channel().eventLoop().schedule(() -> {
                        // If the PING(ACK) times out we may have under estimated the 2RTT time so we
                        // optimistically keep the connection open and rely upon higher level timeouts to tear
                        // down the connection.
                        gracefulCloseWriteSecondGoAway(channel());
                        LOGGER.debug("channel={} timeout {}ms waiting for PING(ACK) during graceful close",
                                channel(), GRACEFUL_CLOSE_PING_ACK_TIMEOUT_MS);
                    }, GRACEFUL_CLOSE_PING_ACK_TIMEOUT_MS, MILLISECONDS);
                }
            });
        }
    }

    final void gracefulCloseWriteSecondGoAway(ChannelOutboundInvoker ctx) {
        ctx.writeAndFlush(new DefaultHttp2GoAwayFrame(NO_ERROR)).addListener(future -> {
            if (activeChildChannels == 0) {
                channel().close();
            } else if (future.isSuccess()) {
                gracefulCloseTimeoutFuture = channel().eventLoop().schedule(() -> {
                    LOGGER.debug("channel={} timeout {}ms waiting for graceful close with {} active streams",
                            channel(), DEFAULT_GRACEFUL_SHUTDOWN_TIMEOUT_MILLIS, activeChildChannels);
                    channel().close();
                }, DEFAULT_GRACEFUL_SHUTDOWN_TIMEOUT_MILLIS, MILLISECONDS);
            }
        });
    }

    final void tryFinishGracefulClose() {
        if (activeChildChannels == 0 && gracefulCloseTimeoutFuture != null &&
                gracefulCloseTimeoutFuture != GRACEFUL_CLOSE_PING_PENDING) {
            // gracefulCloseTimeoutFuture will be cancelled during connection closure elsewhere.
            channel().close();
        }
    }

    final void trackActiveStream(Channel streamChannel) {
        activeChildChannelsUpdater.incrementAndGet(this);
        streamChannel.closeFuture().addListener(future1 -> {
            activeChildChannelsUpdater.decrementAndGet(this);
            tryFinishGracefulClose();
        });
    }

    abstract static class AbstractH2ParentConnection extends ChannelInboundHandlerAdapter {
        final H2ParentConnectionContext parentContext;
        final boolean waitForSslHandshake;
        private final DelayedCancellable delayedCancellable;

        AbstractH2ParentConnection(H2ParentConnectionContext parentContext,
                                   DelayedCancellable delayedCancellable,
                                   boolean waitForSslHandshake) {
            this.parentContext = parentContext;
            this.delayedCancellable = delayedCancellable;
            this.waitForSslHandshake = waitForSslHandshake;
        }

        abstract void tryCompleteSubscriber();

        abstract void tryFailSubscriber(Throwable cause);

        abstract boolean ackSettings(ChannelHandlerContext ctx, Http2SettingsFrame settingsFrame);

        @Override
        public final void handlerAdded(ChannelHandlerContext ctx) {
            final Channel channel = ctx.channel();
            delayedCancellable.delayedCancellable(channel::close);
            // Double check In the event of a late handler (or test utility like EmbeddedChannel) check activeness.
            if (channel.isActive()) {
                doChannelActive(ctx);
            }
            if (!channel.config().isAutoRead()) {
                // auto read is required for h2
                channel.config().setAutoRead(true);
            }
        }

        @Override
        public final void channelActive(ChannelHandlerContext ctx) {
            doChannelActive(ctx);
        }

        @Override
        public final void channelInactive(ChannelHandlerContext ctx) {
            tryFailSubscriber(CLOSED_CHANNEL_INACTIVE);
            doConnectionCleanup();
        }

        @Override
        public final void handlerRemoved(ChannelHandlerContext ctx) {
            tryFailSubscriber(CLOSED_HANDLER_REMOVED);
            doConnectionCleanup();
        }

        @Override
        public final void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            parentContext.transportError.onSuccess(cause);
        }

        @Override
        public final void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
            try {
                if (evt instanceof SslHandshakeCompletionEvent) {
                    parentContext.sslSession = extractSslSession(ctx.pipeline(), (SslHandshakeCompletionEvent) evt,
                            this::tryFailSubscriber);
                    tryCompleteSubscriber();
                }
            } finally {
                release(evt);
            }
        }

        @Override
        public final void channelRead(ChannelHandlerContext ctx, Object msg) {
            if (msg instanceof Http2SettingsFrame) {
                if (ackSettings(ctx, (Http2SettingsFrame) msg)) {
                    ctx.writeAndFlush(Http2SettingsAckFrame.INSTANCE);
                }
            } else if (msg instanceof Http2GoAwayFrame) {
                Http2GoAwayFrame goAwayFrame = (Http2GoAwayFrame) msg;
                goAwayFrame.release();
                parentContext.onClosing.onComplete();

                // We trigger the graceful close process here (with no timeout) to make sure the socket is closed once
                // the existing streams are closed. The MultiplexCodec may simulate a GOAWAY when the stream IDs are
                // exhausted so we shouldn't rely upon our peer to close the transport.
                parentContext.doCloseAsyncGracefully0();
            } else if (msg instanceof Http2PingFrame) {
                Http2PingFrame pingFrame = (Http2PingFrame) msg;
                if (pingFrame.ack() && pingFrame.content() == GRACEFUL_CLOSE_PING_CONTENT &&
                        parentContext.gracefulCloseTimeoutFuture != null) {
                    parentContext.gracefulCloseTimeoutFuture.cancel(true);
                    parentContext.gracefulCloseTimeoutFuture = GRACEFUL_CLOSE_PING_ACK_RECV;
                    parentContext.gracefulCloseWriteSecondGoAway(ctx);
                }
            } else {
                ctx.fireChannelRead(msg);
            }
        }

        private void doConnectionCleanup() {
            if (parentContext.gracefulCloseTimeoutFuture != null) {
                parentContext.gracefulCloseTimeoutFuture.cancel(true);
            }
        }

        private void doChannelActive(ChannelHandlerContext ctx) {
            if (waitForSslHandshake) {
                // Force a read to get the SSL handshake started, any application data that makes it past the SslHandler
                // will be queued in the NettyChannelPublisher.
                ctx.read();
            } else {
                tryCompleteSubscriber();
            }
        }
    }

    private static final class NoopScheduledFuture implements ScheduledFuture<Object> {
        @Override
        public long getDelay(final TimeUnit unit) {
            return 0;
        }

        @Override
        public int compareTo(final Delayed o) {
            return o == this ? 0 : 1;
        }

        @Override
        public boolean cancel(final boolean mayInterruptIfRunning) {
            return false;
        }

        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public boolean isDone() {
            return false;
        }

        @Override
        public Object get() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Object get(final long timeout, final TimeUnit unit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int hashCode() {
            return 0;
        }

        @Override
        public boolean equals(Object o) {
            return o == this;
        }
    }
}
