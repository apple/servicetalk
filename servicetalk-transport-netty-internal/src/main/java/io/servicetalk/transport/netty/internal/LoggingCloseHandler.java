/*
 * Copyright © 2021 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.transport.netty.internal;

import io.servicetalk.logging.api.LogLevel;
import io.servicetalk.logging.slf4j.internal.FixedLevelLogger;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;

import java.util.function.Consumer;

import static io.servicetalk.logging.slf4j.internal.Slf4jFixedLevelLoggers.newLogger;

final class LoggingCloseHandler extends CloseHandler {
    private final CloseHandler delegate;
    private final FixedLevelLogger logger;

    LoggingCloseHandler(final CloseHandler delegate, final String loggerName, final LogLevel logLevel) {
        this.delegate = delegate;
        this.logger = newLogger(loggerName, logLevel);
    }

    @Override
    public void protocolPayloadBeginInbound(ChannelHandlerContext ctx) {
        logger.log("{} protocolPayloadBeginInbound {}", ctx.channel(), delegate);
        delegate.protocolPayloadBeginInbound(ctx);
    }

    @Override
    public void protocolPayloadEndInbound(ChannelHandlerContext ctx) {
        logger.log("{} protocolPayloadEndInbound {}", ctx.channel(), delegate);
        delegate.protocolPayloadEndInbound(ctx);
    }

    @Override
    public void protocolPayloadBeginOutbound(ChannelHandlerContext ctx) {
        logger.log("{} protocolPayloadBeginOutbound {}", ctx.channel(), delegate);
        delegate.protocolPayloadBeginOutbound(ctx);
    }

    @Override
    public void protocolPayloadEndOutbound(ChannelHandlerContext ctx, final ChannelPromise promise) {
        logger.log("{} protocolPayloadEndOutbound {}", ctx.channel(), delegate);
        delegate.protocolPayloadEndOutbound(ctx, promise);
    }

    @Override
    public void protocolClosingInbound(ChannelHandlerContext ctx) {
        logger.log("{} protocolClosingInbound {}", ctx.channel(), delegate);
        delegate.protocolClosingInbound(ctx);
    }

    @Override
    public void protocolClosingOutbound(ChannelHandlerContext ctx) {
        logger.log("{} protocolClosingOutbound {}", ctx.channel(), delegate);
        delegate.protocolClosingOutbound(ctx);
    }

    @Override
    void registerEventHandler(final Channel channel, final Consumer<CloseEvent> eventHandler) {
        logger.log("{} registerEventHandler {}", channel, delegate);
        delegate.registerEventHandler(channel, eventHandler);
    }

    @Override
    void channelClosedInbound(ChannelHandlerContext ctx) {
        logger.log("{} channelClosedInbound {}", ctx.channel(), delegate);
        delegate.channelClosedInbound(ctx);
    }

    @Override
    void channelClosedOutbound(ChannelHandlerContext ctx) {
        logger.log("{} channelClosedOutbound {}", ctx.channel(), delegate);
        delegate.channelClosedOutbound(ctx);
    }

    @Override
    void channelCloseNotify(ChannelHandlerContext ctx) {
        logger.log("{} channelCloseNotify {}", ctx.channel(), delegate);
        delegate.channelCloseNotify(ctx);
    }

    @Override
    void closeChannelInbound(Channel channel) {
        logger.log("{} closeChannelInbound {}", channel, delegate);
        delegate.closeChannelInbound(channel);
    }

    @Override
    void closeChannelOutbound(Channel channel) {
        logger.log("{} closeChannelOutbound {}", channel, delegate);
        delegate.closeChannelOutbound(channel);
    }

    @Override
    void gracefulUserClosing(Channel channel) {
        logger.log("{} gracefulUserClosing {}", channel, delegate);
        delegate.gracefulUserClosing(channel);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "(" + delegate + ")";
    }
}
