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

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;

import java.util.function.Consumer;
import javax.annotation.Nullable;

import static io.servicetalk.transport.netty.internal.CloseHandler.CloseEvent.CHANNEL_CLOSED_INBOUND;
import static io.servicetalk.transport.netty.internal.CloseHandler.CloseEvent.CHANNEL_CLOSED_OUTBOUND;
import static io.servicetalk.transport.netty.internal.CloseHandler.CloseEvent.GRACEFUL_USER_CLOSING;
import static io.servicetalk.transport.netty.internal.CloseHandler.CloseEvent.PROTOCOL_CLOSING_INBOUND;
import static io.servicetalk.transport.netty.internal.RequestResponseCloseHandler.State.has;
import static java.util.Objects.requireNonNull;

final class NonPipelinedCloseHandler extends CloseHandler {
    private static final byte READ = 0x1;
    private static final byte WRITE = 0x2;
    private static final byte IN_CLOSED = 0x4;
    private static final byte OUT_CLOSED = 0x8;
    private static final byte CLOSED = 0x10;
    private static final byte GRACEFUL_CLOSE = 0x20;
    private static final byte IS_CLIENT = 0x40;
    private static final byte ALL_CLOSED = IN_CLOSED | OUT_CLOSED | CLOSED;
    private static final byte READ_WRITE = READ | WRITE;
    private static final byte CLIENT_IN_WRITE = IS_CLIENT | WRITE | IN_CLOSED;
    private static final byte GRACEFUL_IN_CLOSED = GRACEFUL_CLOSE | IN_CLOSED;
    private static final byte GRACEFUL_OUT_CLOSED = GRACEFUL_CLOSE | OUT_CLOSED;
    private byte state;
    private Consumer<CloseEvent> eventHandler = __ -> { };
    @Nullable
    private CloseEvent closeEvent;

    NonPipelinedCloseHandler(boolean isClient) {
        if (isClient) {
            state = IS_CLIENT;
        }
    }

    @Override
    public void protocolPayloadBeginInbound(final ChannelHandlerContext ctx) {
        state = set(state, READ);
    }

    @Override
    public void protocolPayloadEndInbound(final ChannelHandlerContext ctx) {
        state = unset(state, READ);
        inboundEventCheckClose(ctx.channel());
    }

    @Override
    public void protocolPayloadBeginOutbound(final ChannelHandlerContext ctx) {
        state = set(state, WRITE);
    }

    @Override
    public void protocolPayloadEndOutbound(final ChannelHandlerContext ctx, final ChannelPromise promise) {
        ctx.pipeline().fireUserEventTriggered(OutboundDataEndEvent.INSTANCE);
        promise.addListener(f -> {
            state = unset(state, WRITE);
            outboundEventCheckClose(ctx.channel());
        });
    }

    @Override
    public void protocolClosingInbound(final ChannelHandlerContext ctx) {
        state = set(state, IN_CLOSED);
        storeCloseRequestAndEmit(PROTOCOL_CLOSING_INBOUND);
        inboundEventCheckClose(ctx.channel());
    }

    @Override
    public void protocolClosingOutbound(final ChannelHandlerContext ctx) {
        state = set(state, OUT_CLOSED);
        storeCloseRequestAndEmit(PROTOCOL_CLOSING_INBOUND);
        outboundEventCheckClose(ctx.channel());
    }

    @Override
    void registerEventHandler(final Channel channel, final Consumer<CloseEvent> eventHandler) {
        this.eventHandler = requireNonNull(eventHandler);
    }

    @Override
    void channelClosedInbound(final ChannelHandlerContext ctx) {
        state = unset(set(state, IN_CLOSED), READ);
        storeCloseRequestAndEmit(CHANNEL_CLOSED_INBOUND);
        inboundEventCheckClose(ctx.channel());
    }

    @Override
    void channelClosedOutbound(final ChannelHandlerContext ctx) {
        state = unset(set(state, OUT_CLOSED), WRITE);
        storeCloseRequestAndEmit(CHANNEL_CLOSED_OUTBOUND);
        outboundEventCheckClose(ctx.channel());
    }

    @Override
    void closeChannelInbound(final Channel channel) {
        state = set(state, IN_CLOSED);
        // todo storeCloseRequestAndEmit ?
        inboundEventCheckClose(channel);
    }

    @Override
    void closeChannelOutbound(final Channel channel) {
        state = set(state, OUT_CLOSED);
        // todo storeCloseRequestAndEmit ?
        outboundEventCheckClose(channel);
    }

    @Override
    void gracefulUserClosing(final Channel channel) {
        state = set(state, GRACEFUL_CLOSE);
        storeCloseRequestAndEmit(GRACEFUL_USER_CLOSING);
        if (!isAllSet(state, READ_WRITE)) {
            closeChannel(channel);
        }
    }

    private void inboundEventCheckClose(final Channel channel) {
        if (isAllSet(state, OUT_CLOSED) || (isAnySet(state, GRACEFUL_IN_CLOSED) && !isAllSet(state, WRITE))) {
            closeChannel(channel);
        } else if (isAllSet(state, CLIENT_IN_WRITE)) {
            // If a client inbound has closed while writing we abort the write because we can't be sure if the write
            // will ever complete or receive any additional feedback form the server.
            state = unset(state, WRITE);
            channel.pipeline().fireUserEventTriggered(AbortWritesEvent.INSTANCE);
        }
    }

    private void outboundEventCheckClose(final Channel channel) {
        if (isAllSet(state, IN_CLOSED) || (isAnySet(state, GRACEFUL_OUT_CLOSED) && !isAllSet(state, READ))) {
            closeChannel(channel);
        }
    }

    private void storeCloseRequestAndEmit(final CloseEvent event) {
        if (this.closeEvent == null) {
            this.closeEvent = event;
        }
        eventHandler.accept(event);
    }

    private void closeChannel(final Channel channel) {
        if (!has(state, CLOSED)) {
            state = set(state, ALL_CLOSED);
            channel.close();
        }
    }

    private static byte set(byte state, byte flags) {
        return (byte) (state | flags);
    }

    private static byte unset(byte state, byte flags) {
        return (byte) (state & ~flags);
    }

    private static boolean isAllSet(byte state, byte flags) {
        return (state & flags) == flags;
    }

    private static boolean isAnySet(byte state, byte flags) {
        return (state & flags) != 0;
    }
}
