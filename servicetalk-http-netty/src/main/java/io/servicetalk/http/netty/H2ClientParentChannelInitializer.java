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

import io.servicetalk.transport.netty.internal.ChannelInitializer;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http2.DefaultHttp2GoAwayFrame;
import io.netty.handler.codec.http2.Http2FrameCodecBuilder;
import io.netty.handler.codec.http2.Http2FrameLogger;
import io.netty.handler.codec.http2.Http2MultiplexHandler;
import io.netty.handler.codec.http2.Http2Settings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.BiPredicate;

import static io.netty.handler.codec.http2.Http2Error.PROTOCOL_ERROR;
import static io.netty.handler.codec.http2.Http2FrameCodecBuilder.forClient;
import static io.netty.handler.logging.LogLevel.TRACE;

final class H2ClientParentChannelInitializer implements ChannelInitializer {

    private final H2ProtocolConfig config;

    H2ClientParentChannelInitializer(final H2ProtocolConfig config) {
        this.config = config;
    }

    @Override
    public void init(final Channel channel) {
        final Http2FrameCodecBuilder multiplexCodecBuilder = forClient()
                // The max concurrent streams is made available via a publisher and may be consumed asynchronously
                // (e.g. when offloading is enabled), so we manually control the SETTINGS ACK frames.
                .autoAckSettingsFrame(false)
                // Notify server that this client does not support server push and request it to be disabled.
                .initialSettings(Http2Settings.defaultSettings().pushEnabled(false).maxConcurrentStreams(0L))
                // We don't want to rely upon Netty to manage the graceful close timeout, because we expect
                // the user to apply their own timeout at the call site.
                .gracefulShutdownTimeoutMillis(-1);

        final BiPredicate<CharSequence, CharSequence> headersSensitivityDetector =
                config.headersSensitivityDetector();
        multiplexCodecBuilder.headerSensitivityDetector(headersSensitivityDetector::test);

        final String frameLoggerName = config.frameLoggerName();
        if (frameLoggerName != null) {
            multiplexCodecBuilder.frameLogger(new Http2FrameLogger(TRACE, frameLoggerName));
        }

        // TODO(scott): more configuration. header validation, settings stream, etc...

        channel.pipeline().addLast(multiplexCodecBuilder.build(),
                new Http2MultiplexHandler(H2PushStreamHandler.INSTANCE));
    }

    @ChannelHandler.Sharable
    private static final class H2PushStreamHandler extends ChannelInboundHandlerAdapter {

        private static final Logger LOGGER = LoggerFactory.getLogger(H2PushStreamHandler.class);

        static final ChannelInboundHandlerAdapter INSTANCE = new H2PushStreamHandler();

        private H2PushStreamHandler() {
            // singleton
        }

        @Override
        public void channelRegistered(final ChannelHandlerContext ctx) {
            // See SETTINGS_ENABLE_PUSH in https://tools.ietf.org/html/rfc7540#section-6.5.2
            ctx.writeAndFlush(new DefaultHttp2GoAwayFrame(PROTOCOL_ERROR))
                    .addListener((ChannelFutureListener) future -> {
                if (!future.isSuccess()) {
                    LOGGER.debug("Failed to send a GO_AWAY frame for received PUSH_PROMISE frame", future.cause());
                }
                ctx.close(); // push streams are not supported
            });
        }
    }
}
