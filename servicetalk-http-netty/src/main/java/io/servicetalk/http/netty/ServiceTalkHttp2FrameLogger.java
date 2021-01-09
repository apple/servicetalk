/*
 * Copyright © 2020-2021 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.logging.slf4j.internal.FixedLevelLogger;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http2.Http2Flags;
import io.netty.handler.codec.http2.Http2FrameLogger;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.handler.logging.LogLevel;

import java.util.function.BooleanSupplier;

import static io.servicetalk.logging.api.LogLevel.TRACE;

final class ServiceTalkHttp2FrameLogger extends Http2FrameLogger {
    private static final int BUFFER_LENGTH_THRESHOLD = 64;
    private final FixedLevelLogger logger;
    private final BooleanSupplier logUserDataSupplier;

    ServiceTalkHttp2FrameLogger(final FixedLevelLogger logger, final BooleanSupplier logUserDataSupplier) {
        super(LogLevel.ERROR);
        this.logger = logger;
        this.logUserDataSupplier = logUserDataSupplier;
    }

    @Override
    public boolean isEnabled() {
        return logger.isEnabled();
    }

    @Override
    public void logData(Direction direction, ChannelHandlerContext ctx, int streamId, ByteBuf data, int padding,
                        boolean endStream) {
        if (logger.isEnabled()) {
            if (logUserDataSupplier.getAsBoolean()) {
                logger.log("{} {} DATA: streamId={} padding={} endStream={} length={} bytes={}", ctx.channel(),
                        direction.name(), streamId, padding, endStream, data.readableBytes(), toString(data));
            } else {
                logger.log("{} {} DATA: streamId={} padding={} endStream={} length={}", ctx.channel(),
                        direction.name(), streamId, padding, endStream, data.readableBytes());
            }
        }
    }

    @Override
    public void logHeaders(Direction direction, ChannelHandlerContext ctx, int streamId, Http2Headers headers,
                           int padding, boolean endStream) {
        if (logger.isEnabled()) {
            logger.log("{} {} HEADERS: streamId={} headers={} padding={} endStream={}", ctx.channel(),
                    direction.name(), streamId, toString(headers), padding, endStream);
        }
    }

    @Override
    public void logHeaders(Direction direction, ChannelHandlerContext ctx, int streamId, Http2Headers headers,
                           int streamDependency, short weight, boolean exclusive, int padding, boolean endStream) {
        if (logger.isEnabled()) {
            logger.log(
            "{} {} HEADERS: streamId={} headers={} streamDependency={} weight={} exclusive={} padding={} endStream={}",
                    ctx.channel(), direction.name(), streamId, toString(headers), streamDependency, weight, exclusive,
                    padding, endStream);
        }
    }

    @Override
    public void logPriority(Direction direction, ChannelHandlerContext ctx, int streamId, int streamDependency,
                            short weight, boolean exclusive) {
        if (logger.isEnabled()) {
            logger.log("{} {} PRIORITY: streamId={} streamDependency={} weight={} exclusive={}", ctx.channel(),
                    direction.name(), streamId, streamDependency, weight, exclusive);
        }
    }

    @Override
    public void logRstStream(Direction direction, ChannelHandlerContext ctx, int streamId, long errorCode) {
        if (logger.isEnabled()) {
            logger.log("{} {} RST_STREAM: streamId={} errorCode={}", ctx.channel(), direction.name(), streamId,
                    errorCode);
        }
    }

    @Override
    public void logSettingsAck(Direction direction, ChannelHandlerContext ctx) {
        if (logger.isEnabled()) {
            logger.log("{} {} SETTINGS: ack=true", ctx.channel(), direction.name());
        }
    }

    @Override
    public void logSettings(Direction direction, ChannelHandlerContext ctx, Http2Settings settings) {
        if (logger.isEnabled()) {
            // Invoke settings.toString(). Otherwise, some loggers may use it as a Map for manual key=value encoding:
            logger.log("{} {} SETTINGS: ack=false settings={}", ctx.channel(), direction.name(), settings.toString());
        }
    }

    @Override
    public void logPing(Direction direction, ChannelHandlerContext ctx, long data) {
        if (logger.isEnabled()) {
            logger.log("{} {} PING: ack=false bytes={}", ctx.channel(), direction.name(), data);
        }
    }

    @Override
    public void logPingAck(Direction direction, ChannelHandlerContext ctx, long data) {
        if (logger.isEnabled()) {
            logger.log("{} {} PING: ack=true bytes={}", ctx.channel(), direction.name(), data);
        }
    }

    @Override
    public void logPushPromise(Direction direction, ChannelHandlerContext ctx, int streamId, int promisedStreamId,
                               Http2Headers headers, int padding) {
        if (logger.isEnabled()) {
            logger.log("{} {} PUSH_PROMISE: streamId={} promisedStreamId={} headers={} padding={}",
                    ctx.channel(), direction.name(), streamId, promisedStreamId, toString(headers), padding);
        }
    }

    @Override
    public void logGoAway(Direction direction, ChannelHandlerContext ctx, int lastStreamId, long errorCode,
                          ByteBuf debugData) {
        if (logger.isEnabled()) {
            if (logUserDataSupplier.getAsBoolean()) {
                logger.log("{} {} GO_AWAY: lastStreamId={} errorCode={} length={} bytes={}", ctx.channel(),
                        direction.name(), lastStreamId, errorCode, debugData.readableBytes(), toString(debugData));
            } else {
                logger.log("{} {} GO_AWAY: lastStreamId={} errorCode={} length={}", ctx.channel(),
                        direction.name(), lastStreamId, errorCode, debugData.readableBytes());
            }
        }
    }

    @Override
    public void logWindowsUpdate(Direction direction, ChannelHandlerContext ctx, int streamId,
                                 int windowSizeIncrement) {
        if (logger.isEnabled()) {
            logger.log("{} {} WINDOW_UPDATE: streamId={} windowSizeIncrement={}", ctx.channel(),
                    direction.name(), streamId, windowSizeIncrement);
        }
    }

    @Override
    public void logUnknownFrame(Direction direction, ChannelHandlerContext ctx, byte frameType, int streamId,
                                Http2Flags flags, ByteBuf data) {
        if (logger.isEnabled()) {
            if (logUserDataSupplier.getAsBoolean()) {
                logger.log("{} {} UNKNOWN: frameType={} streamId={} flags={} length={} bytes={}", ctx.channel(),
                        direction.name(), frameType & 0xFF, streamId, flags.value(), data.readableBytes(),
                        toString(data));
            } else {
                logger.log("{} {} UNKNOWN: frameType={} streamId={} flags={} length={}", ctx.channel(),
                        direction.name(), frameType & 0xFF, streamId, flags.value(), data.readableBytes());
            }
        }
    }

    private String toString(Http2Headers headers) {
        return logUserDataSupplier.getAsBoolean() ? headers.toString() : String.valueOf(headers.size());
    }

    private String toString(ByteBuf buf) {
        if (logger.logLevel() == TRACE || buf.readableBytes() <= BUFFER_LENGTH_THRESHOLD) {
            // Log the entire buffer.
            return ByteBufUtil.hexDump(buf);
        }
        // Otherwise just log the first bytes.
        return ByteBufUtil.hexDump(buf, buf.readerIndex(), BUFFER_LENGTH_THRESHOLD) + "...";
    }
}
