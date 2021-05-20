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

import io.servicetalk.transport.netty.internal.ChannelInitializer;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http2.DefaultHttp2GoAwayFrame;
import io.netty.handler.codec.http2.Http2FrameCodecBuilder;
import io.netty.handler.codec.http2.Http2MultiplexHandler;

import java.util.function.BiPredicate;

import static io.netty.handler.codec.http2.Http2Error.PROTOCOL_ERROR;
import static io.servicetalk.http.netty.H2ServerParentChannelInitializer.initFrameLogger;

final class H2ClientParentChannelInitializer implements ChannelInitializer {

    private final H2ProtocolConfig config;

    H2ClientParentChannelInitializer(final H2ProtocolConfig config) {
        this.config = config;
    }

    @Override
    public void init(final Channel channel) {
        final Http2FrameCodecBuilder multiplexCodecBuilder = new OptimizedHttp2FrameCodecBuilder(false)
                // We do not want close to trigger graceful closure (go away), instead when user triggers a graceful
                // close, we do the appropriate go away handling.
                .decoupleCloseAndGoAway(true)
                // The max concurrent streams is made available via a publisher and may be consumed asynchronously
                // (e.g. when offloading is enabled), so we manually control the SETTINGS ACK frames.
                .autoAckSettingsFrame(false)
                // We ack PING frames in KeepAliveManager#pingReceived.
                .autoAckPingFrame(false)
                // We don't want to rely upon Netty to manage the graceful close timeout, because we expect
                // the user to apply their own timeout at the call site.
                .gracefulShutdownTimeoutMillis(-1);

        // Notify server that this client does not support server push and request it to be disabled.
        multiplexCodecBuilder.initialSettings().pushEnabled(false).maxConcurrentStreams(0L);

        final BiPredicate<CharSequence, CharSequence> headersSensitivityDetector =
                config.headersSensitivityDetector();
        multiplexCodecBuilder.headerSensitivityDetector(headersSensitivityDetector::test);

        initFrameLogger(multiplexCodecBuilder, config.frameLoggerConfig());

        // TODO(scott): more configuration. header validation, settings stream, etc...

        channel.pipeline().addLast(multiplexCodecBuilder.build(),
                new Http2MultiplexHandler(H2PushStreamHandler.INSTANCE));
    }

    @ChannelHandler.Sharable
    private static final class H2PushStreamHandler extends ChannelInboundHandlerAdapter {

        static final ChannelInboundHandlerAdapter INSTANCE = new H2PushStreamHandler();

        private H2PushStreamHandler() {
            // singleton
        }

        @Override
        public void channelRegistered(final ChannelHandlerContext ctx) {
            // See SETTINGS_ENABLE_PUSH in https://tools.ietf.org/html/rfc7540#section-6.5.2
            ctx.writeAndFlush(new DefaultHttp2GoAwayFrame(PROTOCOL_ERROR));
            // Http2ConnectionHandler.processGoAwayWriteResult will close the connection after GO_AWAY is flushed
        }
    }
}
