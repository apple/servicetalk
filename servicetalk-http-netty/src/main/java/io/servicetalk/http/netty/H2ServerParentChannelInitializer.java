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
import io.netty.handler.codec.http2.Http2FrameCodecBuilder;
import io.netty.handler.codec.http2.Http2MultiplexHandler;
import io.netty.handler.codec.http2.Http2StreamChannel;

import java.util.Map;
import javax.annotation.Nullable;

import static io.servicetalk.logging.slf4j.internal.Slf4jFixedLevelLoggers.newLogger;

final class H2ServerParentChannelInitializer implements ChannelInitializer {
    private static final io.netty.handler.codec.http2.Http2Settings DEFAULT_NETTY_SETTINGS =
            io.netty.handler.codec.http2.Http2Settings.defaultSettings();
    private final H2ProtocolConfig config;
    private final io.netty.channel.ChannelInitializer<Http2StreamChannel> streamChannelInitializer;
    private final io.netty.handler.codec.http2.Http2Settings nettySettings;

    H2ServerParentChannelInitializer(
            final H2ProtocolConfig config,
            final io.netty.channel.ChannelInitializer<Http2StreamChannel> streamChannelInitializer) {
        this.config = config;
        this.streamChannelInitializer = streamChannelInitializer;
        final Map<Character, Integer> h2Settings = config.initialSettings();
        nettySettings = h2Settings.isEmpty() ? DEFAULT_NETTY_SETTINGS : toNettySettings(h2Settings);
    }

    @Override
    public void init(final Channel channel) {
        final Http2FrameCodecBuilder multiplexCodecBuilder =
                new OptimizedHttp2FrameCodecBuilder(true, config.flowControlQuantum())
                // We do not want close to trigger graceful closure (go away), instead when user triggers a graceful
                // close, we do the appropriate go away handling.
                .decoupleCloseAndGoAway(true)
                // We ack PING frames in KeepAliveManager#pingReceived.
                .autoAckPingFrame(false)
                // We don't want to rely upon Netty to manage the graceful close timeout, because we expect
                // the user to apply their own timeout at the call site.
                .gracefulShutdownTimeoutMillis(-1)
                .initialSettings(nettySettings)
                .headerSensitivityDetector(config.headersSensitivityDetector()::test);

        initFrameLogger(multiplexCodecBuilder, config.frameLoggerConfig());

        // TODO(scott): more configuration. header validation, etc...

        channel.pipeline().addLast(multiplexCodecBuilder.build(), new Http2MultiplexHandler(streamChannelInitializer));
    }

    static void initFrameLogger(final Http2FrameCodecBuilder multiplexCodecBuilder,
                                @Nullable final UserDataLoggerConfig frameLoggerConfig) {
        if (frameLoggerConfig != null) {
            multiplexCodecBuilder.frameLogger(
                    new ServiceTalkHttp2FrameLogger(newLogger(frameLoggerConfig.loggerName(),
                            frameLoggerConfig.logLevel()), frameLoggerConfig.logUserData()));
        }
    }

    static io.netty.handler.codec.http2.Http2Settings toNettySettings(Map<Character, Integer> h2Settings) {
        io.netty.handler.codec.http2.Http2Settings nettySettings = new io.netty.handler.codec.http2.Http2Settings();
        h2Settings.forEach((identifier, value) -> {
            switch (identifier) {
                case Http2Settings.HEADER_TABLE_SIZE:
                    nettySettings.headerTableSize(value);
                    break;
                case Http2Settings.ENABLE_PUSH:
                    nettySettings.pushEnabled(value != 0);
                    break;
                case Http2Settings.MAX_CONCURRENT_STREAMS:
                    nettySettings.maxConcurrentStreams(value);
                    break;
                case Http2Settings.INITIAL_WINDOW_SIZE:
                    nettySettings.initialWindowSize(value);
                    break;
                case Http2Settings.MAX_FRAME_SIZE:
                    nettySettings.maxFrameSize(value);
                    break;
                case Http2Settings.MAX_HEADER_LIST_SIZE:
                    nettySettings.maxHeaderListSize(value);
                    break;
                default:
                    nettySettings.put(identifier, Long.valueOf(value));
                    break;
            }
        });
        return nettySettings;
    }
}
