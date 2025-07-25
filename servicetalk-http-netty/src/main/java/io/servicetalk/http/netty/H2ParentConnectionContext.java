/*
 * Copyright © 2019-2021 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.SingleSource;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.internal.DelayedCancellable;
import io.servicetalk.http.api.HttpConnectionContext;
import io.servicetalk.http.api.HttpExecutionContext;
import io.servicetalk.http.api.HttpProtocolVersion;
import io.servicetalk.transport.api.ConnectionContext;
import io.servicetalk.transport.api.ConnectionObserver;
import io.servicetalk.transport.api.SslConfig;
import io.servicetalk.transport.netty.internal.ChannelCloseUtils;
import io.servicetalk.transport.netty.internal.FlushStrategy;
import io.servicetalk.transport.netty.internal.FlushStrategyHolder;
import io.servicetalk.transport.netty.internal.NettyChannelListenableAsyncCloseable;
import io.servicetalk.transport.netty.internal.NettyConnectionContext;
import io.servicetalk.transport.netty.internal.StacklessClosedChannelException;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.socket.ChannelInputShutdownReadComplete;
import io.netty.channel.socket.ChannelOutputShutdownEvent;
import io.netty.handler.codec.http2.Http2GoAwayFrame;
import io.netty.handler.codec.http2.Http2PingFrame;
import io.netty.handler.codec.http2.Http2SettingsAckFrame;
import io.netty.handler.codec.http2.Http2SettingsFrame;
import io.netty.handler.codec.http2.Http2StreamChannel;
import io.netty.handler.ssl.SslCloseCompletionEvent;
import io.netty.handler.ssl.SslHandshakeCompletionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.SocketAddress;
import java.net.SocketOption;
import javax.annotation.Nullable;
import javax.net.ssl.SSLSession;

import static io.netty.util.ReferenceCountUtil.release;
import static io.servicetalk.concurrent.api.Processors.newSingleProcessor;
import static io.servicetalk.concurrent.api.SourceAdapters.fromSource;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_2_0;
import static io.servicetalk.http.netty.HttpExecutionContextUtils.channelExecutionContext;
import static io.servicetalk.http.netty.NettyHttp2ExceptionUtils.wrapIfNecessary;
import static io.servicetalk.transport.netty.internal.NettyPipelineSslUtils.extractSslSession;
import static io.servicetalk.transport.netty.internal.SocketOptionUtils.getOption;

class H2ParentConnectionContext extends NettyChannelListenableAsyncCloseable implements NettyConnectionContext,
                                                                                        HttpConnectionContext {

    private static final Logger LOGGER = LoggerFactory.getLogger(H2ParentConnectionContext.class);

    final FlushStrategyHolder flushStrategyHolder;
    private final HttpExecutionContext executionContext;
    private final SingleSource.Processor<Throwable, Throwable> transportError = newSingleProcessor();
    private final KeepAliveManager keepAliveManager;
    @Nullable
    private final SslConfig sslConfig;
    final long idleTimeoutMs;
    @Nullable
    private SSLSession sslSession;

    H2ParentConnectionContext(final Channel channel, final HttpExecutionContext executionContext,
                              final FlushStrategy flushStrategy, final long idleTimeoutMs,
                              @Nullable final SslConfig sslConfig, @Nullable final SSLSession sslSession,
                              final KeepAliveManager keepAliveManager) {
        super(channel, executionContext.executor());
        this.executionContext = channelExecutionContext(channel, executionContext);
        this.flushStrategyHolder = new FlushStrategyHolder(flushStrategy);
        this.sslConfig = sslConfig;
        this.sslSession = sslSession;
        this.idleTimeoutMs = idleTimeoutMs;
        this.keepAliveManager = keepAliveManager;
    }

    @Override
    public final Cancellable updateFlushStrategy(final FlushStrategyProvider strategyProvider) {
        return flushStrategyHolder.updateFlushStrategy(strategyProvider);
    }

    @Override
    public final FlushStrategy defaultFlushStrategy() {
        return flushStrategyHolder.currentStrategy();
    }

    @Override
    public final Single<Throwable> transportError() {
        return fromSource(transportError);
    }

    @Override
    public final String connectionId() {
        return "0x" + channel().id().asShortText();
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
    public final SslConfig sslConfig() {
        return sslConfig;
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
    public final <T> T socketOption(final SocketOption<T> option) {
        return getOption(option, channel().config(), idleTimeoutMs);
    }

    @Override
    public final HttpProtocolVersion protocol() {
        return HTTP_2_0;
    }

    @Nullable
    @Override
    public final ConnectionContext parent() {
        return null;
    }

    @Override
    public final Channel nettyChannel() {
        return channel();
    }

    @Override
    public final String toString() {
        return channel().toString();
    }

    @Override
    protected final void doCloseAsyncGracefully() {
        keepAliveManager.initiateGracefulClose(() -> {
            // no need to notifyOnClosing bcz it's already notified in NettyChannelListenableAsyncCloseable before
            // invoking this method
        }, true);
    }

    /**
     * This method is required to access notifyOnClosing() from AbstractH2ParentConnection, because usage of
     * {@code parentContext.notifyOnClosing()} directly triggers {@link java.lang.IllegalAccessError}.
     */
    private void notifyOnClosingImpl() {
        notifyOnClosing();
    }

    final void trackActiveStream(Http2StreamChannel streamChannel) {
        keepAliveManager.trackActiveStream(streamChannel);
    }

    static boolean shouldWaitForSslHandshake(@Nullable final SSLSession sslSession,
                                             @Nullable final SslConfig sslConfig) {
        return sslConfig != null && sslSession == null;
    }

    abstract static class AbstractH2ParentConnection extends ChannelInboundHandlerAdapter {
        final H2ParentConnectionContext parentContext;
        final boolean waitForSslHandshake;
        private final DelayedCancellable delayedCancellable;
        final ConnectionObserver observer;

        AbstractH2ParentConnection(H2ParentConnectionContext parentContext,
                                   DelayedCancellable delayedCancellable,
                                   boolean waitForSslHandshake,
                                   ConnectionObserver observer) {
            this.parentContext = parentContext;
            this.delayedCancellable = delayedCancellable;
            this.waitForSslHandshake = waitForSslHandshake;
            this.observer = observer;
        }

        abstract void tryCompleteSubscriber();

        abstract boolean tryFailSubscriber(Throwable cause);

        /**
         * Receive a settings frame and optionally handle the acknowledgement of the frame.
         *
         * @param ctx the channel context
         * @param settingsFrame the received settings frame
         * @return true if caller should send ack or false if receiver has or will send ack.
         */
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
            doChannelClosed("channelInactive(...)");
        }

        @Override
        public final void handlerRemoved(ChannelHandlerContext ctx) {
            doChannelClosed("handlerRemoved(...)");
        }

        private void doChannelClosed(final String method) {
            parentContext.notifyOnClosingImpl();

            tryFailSubscriber(StacklessClosedChannelException.newInstance(H2ParentConnectionContext.class, method));
            parentContext.keepAliveManager.channelClosed();
        }

        @Override
        public final void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            // The parent channel will be closed in case of exception after the cause is propagated to subscriber.
            // In case users don't have offloading, there is a risk to retry on the same IO thread.
            // We should notify LoadBalancer that this connection is closing to avoid retrying on the same connection.
            parentContext.notifyOnClosingImpl();
            cause = wrapIfNecessary(cause);
            parentContext.transportError.onSuccess(cause);
            if (!tryFailSubscriber(cause)) {
                LOGGER.debug("{} closing h2 parent channel on exception caught", parentContext.nettyChannel(), cause);
                ChannelCloseUtils.close(ctx, cause);
            }
        }

        @Override
        public final void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
            try {
                if (evt instanceof SslHandshakeCompletionEvent) {
                    parentContext.sslSession = extractSslSession(ctx.pipeline(),
                            (SslHandshakeCompletionEvent) evt, this::tryFailSubscriber);
                    tryCompleteSubscriber();
                } else if (evt == ChannelInputShutdownReadComplete.INSTANCE || evt == SslCloseCompletionEvent.SUCCESS) {
                    parentContext.keepAliveManager.channelInputShutdown();
                } else if (evt == ChannelOutputShutdownEvent.INSTANCE) {
                    parentContext.keepAliveManager.channelOutputShutdown();
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

                // We trigger the graceful close process here (with no timeout) to make sure the socket is closed once
                // the existing streams are closed. The MultiplexCodec may simulate a GOAWAY when the stream IDs are
                // exhausted so we shouldn't rely upon our peer to close the transport.
                parentContext.keepAliveManager.initiateGracefulClose(parentContext::notifyOnClosingImpl, false);
            } else if (msg instanceof Http2PingFrame) {
                parentContext.keepAliveManager.pingReceived((Http2PingFrame) msg);
            } else if (!(msg instanceof Http2SettingsAckFrame)) { // we ignore SETTINGS(ACK)
                ctx.fireChannelRead(msg);
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
}
