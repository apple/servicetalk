/*
 * Copyright © 2019-2021, 2023 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.SingleSource;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.transport.netty.internal.ChannelCloseUtils;
import io.servicetalk.transport.netty.internal.ChannelInitializer;
import io.servicetalk.transport.netty.internal.NettyPipelineSslUtils;
import io.servicetalk.transport.netty.internal.StacklessClosedChannelException;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.ssl.ApplicationProtocolNegotiationHandler;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.ssl.SslHandshakeCompletionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;
import javax.annotation.Nullable;
import javax.net.ssl.SSLException;

import static io.servicetalk.http.netty.AlpnIds.HTTP_1_1;
import static io.servicetalk.transport.netty.internal.ChannelCloseUtils.assignConnectionError;
import static java.util.Objects.requireNonNull;

/**
 * A {@link Single} that initializes ALPN handler and completes after protocol negotiation.
 */
final class AlpnChannelSingle extends ChannelInitSingle<String> {
    private final Consumer<ChannelHandlerContext> onHandlerAdded;

    AlpnChannelSingle(final Channel channel,
                      final ChannelInitializer channelInitializer,
                      final Consumer<ChannelHandlerContext> onHandlerAdded) {
        super(channel, channelInitializer);
        this.onHandlerAdded = requireNonNull(onHandlerAdded);
    }

    @Override
    protected ChannelHandler newChannelHandler(final Subscriber<? super String> subscriber) {
        return new AlpnChannelHandler(subscriber, onHandlerAdded);
    }

    /**
     * Configures a {@link ChannelPipeline} depending on the application-level protocol negotiation result of
     * {@link SslHandler}.
     */
    private static final class AlpnChannelHandler extends ApplicationProtocolNegotiationHandler {

        private static final Logger LOGGER = LoggerFactory.getLogger(AlpnChannelHandler.class);

        private static final String FALLBACK_PROTOCOL = HTTP_1_1;

        @Nullable
        private SingleSource.Subscriber<? super String> subscriber;
        private final Consumer<ChannelHandlerContext> onHandlerAdded;

        AlpnChannelHandler(final SingleSource.Subscriber<? super String> subscriber,
                           final Consumer<ChannelHandlerContext> onHandlerAdded) {
            super(FALLBACK_PROTOCOL);
            this.subscriber = subscriber;
            this.onHandlerAdded = onHandlerAdded;
        }

        @Override
        public void handlerAdded(final ChannelHandlerContext ctx) throws Exception {
            super.handlerAdded(ctx);
            onHandlerAdded.accept(ctx);
        }

        @Override
        @SuppressWarnings("PMD.ExceptionAsFlowControl")
        public void userEventTriggered(final ChannelHandlerContext ctx, final Object evt) throws Exception {
            // Intercept only the *successful* SslHandshakeCompletionEvent. The parent's implementation looks up
            // the SslHandler via pipeline.get(SslHandler.class), which in a layered-TLS pipeline returns the
            // head-most (proxy) handler whose applicationProtocol() is null, causing a fallback to HTTP/1.1
            // even when the inner handshake actually negotiated h2. Everything else — failure events,
            // ChannelInputShutdownEvent, unrelated user events — delegates to super to inherit parent behavior.
            if (evt instanceof SslHandshakeCompletionEvent && ((SslHandshakeCompletionEvent) evt).isSuccess()) {
                try {
                    final SslHandler sslHandler = NettyPipelineSslUtils.applicationSslHandler(ctx.pipeline());
                    if (sslHandler == null) {
                        throw new IllegalStateException("cannot find an SslHandler in the pipeline (required for "
                                + "application-level protocol negotiation)");
                    }
                    final String protocol = sslHandler.applicationProtocol();
                    configurePipeline(ctx, protocol != null ? protocol : FALLBACK_PROTOCOL);
                } catch (Throwable cause) {
                    exceptionCaught(ctx, cause);
                } finally {
                    if (!ctx.isRemoved()) {
                        ctx.pipeline().remove(this);
                    }
                }
                ctx.fireUserEventTriggered(evt);
            } else {
                // The super call is deliberate as we leverage the logic in the super implementation to handle
                // the unsuccessful path.
                super.userEventTriggered(ctx, evt);
            }
        }

        @Override
        protected void configurePipeline(final ChannelHandlerContext ctx, final String protocol) {
            LOGGER.debug("{} ALPN negotiated {} protocol", ctx.channel(), protocol);
            assert subscriber != null;
            final SingleSource.Subscriber<? super String> subscriberCopy = subscriber;
            subscriber = null;
            subscriberCopy.onSuccess(protocol);
        }

        @Override
        protected void handshakeFailure(final ChannelHandlerContext ctx, final Throwable cause) {
            LOGGER.warn("{} TLS handshake failed:", ctx.channel(), cause);
            if (!failSubscriber(cause, ctx.channel())) {
                ChannelCloseUtils.close(ctx, cause);
            }
        }

        @Override
        public void exceptionCaught(final ChannelHandlerContext ctx, final Throwable cause) {
            Throwable wrapped;
            // This unwrapping logic is copied from the parent ApplicationProtocolNegotiationHandler
            if (cause instanceof DecoderException && ((wrapped = cause.getCause()) instanceof SSLException)) {
                handshakeFailure(ctx, wrapped);
                return;
            }
            LOGGER.warn("{} Failed to select the application-level protocol:", ctx.channel(), cause);
            if (!failSubscriber(cause, ctx.channel())) {
                // Propagate exception in the pipeline if subscriber is already complete
                ctx.fireExceptionCaught(cause);
                ChannelCloseUtils.close(ctx, cause);
            }
        }

        @Override
        public void channelInactive(final ChannelHandlerContext ctx) throws Exception {
            if (subscriber != null) {
                failSubscriber(StacklessClosedChannelException.newInstance(
                        AlpnChannelHandler.class, "channelInactive(...)"), ctx.channel());
            }
            super.channelInactive(ctx);
        }

        private boolean failSubscriber(final Throwable cause, final Channel channel) {
            assignConnectionError(channel, cause);
            if (subscriber != null) {
                final SingleSource.Subscriber<? super String> subscriberCopy = subscriber;
                subscriber = null;
                subscriberCopy.onError(cause);
                return true;
            }
            return false;
        }
    }
}
