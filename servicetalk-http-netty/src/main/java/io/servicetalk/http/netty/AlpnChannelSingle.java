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
package io.servicetalk.http.netty;

import io.servicetalk.concurrent.SingleSource;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.concurrent.api.internal.SubscribableSingle;
import io.servicetalk.http.api.HttpExecutionContext;
import io.servicetalk.tcp.netty.internal.ReadOnlyTcpServerConfig;
import io.servicetalk.transport.api.ConnectionContext;
import io.servicetalk.transport.api.ExecutionContext;
import io.servicetalk.transport.netty.internal.ChannelInitializer;
import io.servicetalk.transport.netty.internal.NettyChannelListenableAsyncCloseable;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.ssl.ApplicationProtocolNegotiationHandler;
import io.netty.handler.ssl.ApplicationProtocolNegotiator;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.SocketAddress;
import javax.annotation.Nullable;
import javax.net.ssl.SSLSession;

import static io.servicetalk.http.netty.ApplicationProtocolNames.HTTP_1_1;
import static io.servicetalk.concurrent.Cancellable.IGNORE_CANCEL;

/**
 * A {@link Single} that initializes ALPN handler and completes after protocol negotiation.
 */
final class AlpnChannelSingle extends SubscribableSingle<AlpnChannelSingle.AlpnConnectionContext> {

    private final Channel channel;
    private final HttpExecutionContext executionContext;
    private final ChannelInitializer channelInitializer;
    private final boolean forceChannelRead;

    AlpnChannelSingle(final Channel channel, final HttpExecutionContext executionContext,
                      final ChannelInitializer channelInitializer, final boolean forceChannelRead) {
        this.channel = channel;
        this.executionContext = executionContext;
        this.channelInitializer = channelInitializer;
        this.forceChannelRead = forceChannelRead;
    }

    @Override
    protected void handleSubscribe(final Subscriber<? super AlpnConnectionContext> subscriber) {
        final AlpnConnectionContext context;
        try {
            context = new AlpnConnectionContext(channel, executionContext);
            channelInitializer.init(channel, context);
        } catch (Throwable cause) {
            channel.close();
            subscriber.onSubscribe(IGNORE_CANCEL);
            subscriber.onError(cause);
            return;
        }
        subscriber.onSubscribe(channel::close);
        // We have to add to the pipeline AFTER we call onSubscribe, because adding to the pipeline may invoke
        // callbacks that interact with the subscriber.
        channel.pipeline().addLast(new AlpnChannelHandler(context, subscriber, forceChannelRead));
    }

    /**
     * Configures a {@link ChannelPipeline} depending on the application-level protocol negotiation result of
     * {@link SslHandler}.
     */
    private static final class AlpnChannelHandler extends ApplicationProtocolNegotiationHandler {

        private static final Logger LOGGER = LoggerFactory.getLogger(AlpnChannelHandler.class);

        private final AlpnConnectionContext connectionContext;
        @Nullable
        private SingleSource.Subscriber<? super AlpnConnectionContext> subscriber;
        private final boolean forceRead;

        AlpnChannelHandler(final AlpnConnectionContext connectionContext,
                           final SingleSource.Subscriber<? super AlpnConnectionContext> subscriber,
                           final boolean forceRead) {
            super(HTTP_1_1);
            this.connectionContext = connectionContext;
            this.subscriber = subscriber;
            this.forceRead = forceRead;
        }

        @Override
        public void handlerAdded(final ChannelHandlerContext ctx) throws Exception {
            super.handlerAdded(ctx);
            if (forceRead) {
                // Force a read to get the SSL handshake started. We initialize pipeline before SslHandshakeCompletionEvent
                // will complete, therefore, no data will be propagated before we finish initialization.
                ctx.read();
            }
        }

        @Override
        protected void configurePipeline(final ChannelHandlerContext ctx, final String protocol) {
            LOGGER.debug("{} ALPN negotiated {} protocol", ctx.channel(), protocol);
            connectionContext.protocol = protocol;

            assert subscriber != null;
            final SingleSource.Subscriber<? super AlpnConnectionContext> subscriberCopy = subscriber;
            subscriber = null;
            subscriberCopy.onSuccess(connectionContext);
        }



        @Override
        protected void handshakeFailure(final ChannelHandlerContext ctx, final Throwable cause) {
            LOGGER.warn("{} TLS handshake failed:", ctx.channel(), cause);
            failSubscriber(cause);
        }

        @Override
        public void exceptionCaught(final ChannelHandlerContext ctx, final Throwable cause) {
            LOGGER.warn("{} Failed to select the application-level protocol:", ctx.channel(), cause);
            if (!failSubscriber(cause)) {
                // Propagate exception in the pipeline if subscribed is already complete
                ctx.fireExceptionCaught(cause);
                ctx.close();
            }
        }

        private boolean failSubscriber(final Throwable cause) {
            if (subscriber != null) {
                final SingleSource.Subscriber<? super AlpnConnectionContext> subscriberCopy = subscriber;
                subscriber = null;
                subscriberCopy.onError(cause);
                return true;
            }
            return false;
        }
    }


    /**
     * A {@link ConnectionContext} used for {@link ChannelInitializer} before ALPN completes.
     */
    static final class AlpnConnectionContext extends NettyChannelListenableAsyncCloseable implements ConnectionContext {

        private final ExecutionContext executionContext;
        @Nullable
        private String protocol;

        AlpnConnectionContext(final Channel channel, final ExecutionContext executionContext) {
            super(channel, executionContext.executor());
            this.executionContext = executionContext;
        }

        @Override
        public SocketAddress localAddress() {
            return channel().localAddress();
        }

        @Override
        public SocketAddress remoteAddress() {
            return channel().remoteAddress();
        }

        @Nullable
        @Override
        public SSLSession sslSession() {
            return null;
        }

        @Override
        public ExecutionContext executionContext() {
            return executionContext;
        }

        @Nullable
        String protocol() {
            return protocol;
        }
    }

    /**
     * Tells if ALPN should be used based on {@link ReadOnlyTcpServerConfig}.
     *
     * @param config {@link ReadOnlyTcpServerConfig} to make a decision
     * @return {@code true} if ALPN should be used
     */
    static boolean useAlpn(final ReadOnlyTcpServerConfig config) {
        if (config.isSniEnabled()) {
            for (final SslContext sslContext : config.domainNameMapping().asMap().values()) {
                if (useAlpn(sslContext)) {
                    return true;
                }
            }
            return false;
        }
        return useAlpn(config.sslContext());
    }

    /**
     * Tells if ALPN should be used based on {@link SslContext}.
     *
     * @param sslContext {@link SslContext} to make a decision
     * @return {@code true} if ALPN should be used
     */
    static boolean useAlpn(@Nullable final SslContext sslContext) {
        if (sslContext == null) {
            return false;
        }
        @SuppressWarnings("deprecation")
        final ApplicationProtocolNegotiator apn = sslContext.applicationProtocolNegotiator();
        return apn != null && !apn.protocols().isEmpty();
    }

    /**
     * {@link ChannelInitializer} that does not do anything.
     */
    static final class NoopChannelInitializer implements ChannelInitializer {

        static final ChannelInitializer INSTANCE = new NoopChannelInitializer();

        private NoopChannelInitializer() {
            // Singleton
        }

        @Override
        public ConnectionContext init(final Channel channel, final ConnectionContext context) {
            return context;
        }

        @Override
        public ChannelInitializer andThen(final ChannelInitializer after) {
            return after;
        }
    }
}
