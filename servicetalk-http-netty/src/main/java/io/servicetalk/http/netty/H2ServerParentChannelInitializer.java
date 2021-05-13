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

import io.servicetalk.logging.api.UserDataLoggerConfig;
import io.servicetalk.transport.netty.internal.ChannelInitializer;

import io.netty.channel.Channel;
import io.netty.handler.codec.http2.Http2FrameCodecBuilder;
import io.netty.handler.codec.http2.Http2MultiplexHandler;
import io.netty.handler.codec.http2.Http2StreamChannel;

import java.util.function.BiPredicate;
import javax.annotation.Nullable;

import static io.netty.handler.codec.http2.Http2FrameCodecBuilder.forServer;
import static io.servicetalk.logging.slf4j.internal.Slf4jFixedLevelLoggers.newLogger;

final class H2ServerParentChannelInitializer implements ChannelInitializer {
    private final H2ProtocolConfig config;
    private final io.netty.channel.ChannelInitializer<Http2StreamChannel> streamChannelInitializer;

    H2ServerParentChannelInitializer(
            final H2ProtocolConfig config,
            final io.netty.channel.ChannelInitializer<Http2StreamChannel> streamChannelInitializer) {
        this.config = config;
        this.streamChannelInitializer = streamChannelInitializer;
    }

    @Override
    public void init(final Channel channel) {
        final Http2FrameCodecBuilder multiplexCodecBuilder = forServer()
                // We do not want close to trigger graceful closure (go away), instead when user triggers a graceful
                // close, we do the appropriate go away handling.
                .decoupleCloseAndGoAway(true)
                // We ack PING frames in KeepAliveManager#pingReceived.
                .autoAckPingFrame(false)
                // We don't want to rely upon Netty to manage the graceful close timeout, because we expect
                // the user to apply their own timeout at the call site.
                .gracefulShutdownTimeoutMillis(-1);

        final BiPredicate<CharSequence, CharSequence> headersSensitivityDetector =
                config.headersSensitivityDetector();
        multiplexCodecBuilder.headerSensitivityDetector(headersSensitivityDetector::test);

        initFrameLogger(multiplexCodecBuilder, config.frameLoggerConfig());

        // TODO(scott): more configuration. header validation, settings stream, etc...

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
}
