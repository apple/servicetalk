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

import io.servicetalk.http.api.Http2Settings;
import io.servicetalk.logging.api.UserDataLoggerConfig;
import io.servicetalk.transport.netty.internal.ChannelInitializer;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http2.DefaultHttp2WindowUpdateFrame;
import io.netty.handler.codec.http2.Http2CodecUtil;
import io.netty.handler.codec.http2.Http2FrameCodecBuilder;
import io.netty.handler.codec.http2.Http2MultiplexHandler;
import io.netty.handler.codec.http2.Http2StreamChannel;

import javax.annotation.Nullable;

import static io.servicetalk.logging.slf4j.internal.Slf4jFixedLevelLoggers.newLogger;

final class H2ServerParentChannelInitializer implements ChannelInitializer {
    private final H2ProtocolConfig config;
    private final io.netty.channel.ChannelInitializer<Http2StreamChannel> streamChannelInitializer;
    private final io.netty.handler.codec.http2.Http2Settings nettySettings;

    H2ServerParentChannelInitializer(
            final H2ProtocolConfig config,
            final io.netty.channel.ChannelInitializer<Http2StreamChannel> streamChannelInitializer) {
        this.config = config;
        this.streamChannelInitializer = streamChannelInitializer;
        final Http2Settings h2Settings = config.initialSettings();
        nettySettings = toNettySettings(h2Settings);
    }

    @Override
    public void init(final Channel channel) {
        final Http2FrameCodecBuilder multiplexCodecBuilder =
                new OptimizedHttp2FrameCodecBuilder(true, config.flowControlQuantum())
                // We do not want close to trigger graceful closure (go away), instead when user triggers a graceful
                // close, we do the appropriate go away handling.
                .decoupleCloseAndGoAway(true)
                // H2ServerParentConnectionContext.ackSettings(...) expects Netty to ack settings frame.
                // While the default value is `true`, set this explicitly to avoid any unexpected changes.
                .autoAckSettingsFrame(true)
                // We ack PING frames in KeepAliveManager#pingReceived.
                .autoAckPingFrame(false)
                // We don't want to rely upon Netty to manage the graceful close timeout, because we expect
                // the user to apply their own timeout at the call site.
                .gracefulShutdownTimeoutMillis(-1)
                .initialSettings(nettySettings)
                // Inherit headers validation setting from the HttpHeadersFactory.
                .validateHeaders(config.headersFactory().validateNames())
                .headerSensitivityDetector(config.headersSensitivityDetector()::test);

        initFrameLogger(multiplexCodecBuilder, config.frameLoggerConfig());

        // TODO(scott): more configuration. header validation, etc...

        channel.pipeline().addLast(multiplexCodecBuilder.build(), new Http2MultiplexHandler(streamChannelInitializer));
        if (config.flowControlWindowIncrement() > 0) {
            // Must be after Http2ConnectionHandler does its initialization in handlerAdded above.
            // The server will not send a connection preface so we are good to send a window update.
            channel.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                @Override
                public void channelActive(ChannelHandlerContext ctx) {
                    ctx.write(new DefaultHttp2WindowUpdateFrame(config.flowControlWindowIncrement()));
                    ctx.pipeline().remove(this);
                }
            });
        }
    }

    static void initFrameLogger(final Http2FrameCodecBuilder multiplexCodecBuilder,
                                @Nullable final UserDataLoggerConfig frameLoggerConfig) {
        if (frameLoggerConfig != null) {
            multiplexCodecBuilder.frameLogger(
                    new ServiceTalkHttp2FrameLogger(newLogger(frameLoggerConfig.loggerName(),
                            frameLoggerConfig.logLevel()), frameLoggerConfig.logUserData()));
        }
    }

    static io.netty.handler.codec.http2.Http2Settings toNettySettings(Http2Settings h2Settings) {
        io.netty.handler.codec.http2.Http2Settings nettySettings = new io.netty.handler.codec.http2.Http2Settings();
        h2Settings.forEach((identifier, value) -> {
            switch (identifier) {
                case Http2CodecUtil.SETTINGS_HEADER_TABLE_SIZE:
                    nettySettings.headerTableSize(value);
                    break;
                case Http2CodecUtil.SETTINGS_ENABLE_PUSH:
                    nettySettings.pushEnabled(value != 0);
                    break;
                case Http2CodecUtil.SETTINGS_MAX_CONCURRENT_STREAMS:
                    nettySettings.maxConcurrentStreams(value);
                    break;
                case Http2CodecUtil.SETTINGS_INITIAL_WINDOW_SIZE:
                    nettySettings.initialWindowSize(value.intValue());
                    break;
                case Http2CodecUtil.SETTINGS_MAX_FRAME_SIZE:
                    nettySettings.maxFrameSize(value.intValue());
                    break;
                case Http2CodecUtil.SETTINGS_MAX_HEADER_LIST_SIZE:
                    nettySettings.maxHeaderListSize(value);
                    break;
                default:
                    nettySettings.put(identifier, value);
                    break;
            }
        });
        return nettySettings;
    }
}
