/*
 * Copyright © 2018 Apple Inc. and the ServiceTalk project authors
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
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.SocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;
import javax.annotation.Nullable;

import static io.servicetalk.transport.netty.internal.CloseHandler.CloseEvent.CHANNEL_CLOSED_INBOUND;
import static io.servicetalk.transport.netty.internal.CloseHandler.CloseEvent.CHANNEL_CLOSED_OUTBOUND;
import static io.servicetalk.transport.netty.internal.CloseHandler.CloseEvent.PROTOCOL_CLOSING_INBOUND;
import static io.servicetalk.transport.netty.internal.CloseHandler.CloseEvent.PROTOCOL_CLOSING_OUTBOUND;
import static io.servicetalk.transport.netty.internal.CloseHandler.CloseEvent.USER_CLOSING;
import static io.servicetalk.transport.netty.internal.RequestResponseCloseHandler.State.ALL_CLOSED;
import static io.servicetalk.transport.netty.internal.RequestResponseCloseHandler.State.CLOSED;
import static io.servicetalk.transport.netty.internal.RequestResponseCloseHandler.State.CLOSING;
import static io.servicetalk.transport.netty.internal.RequestResponseCloseHandler.State.IN_CLOSED;
import static io.servicetalk.transport.netty.internal.RequestResponseCloseHandler.State.OUT_CLOSED;
import static io.servicetalk.transport.netty.internal.RequestResponseCloseHandler.State.READ;
import static io.servicetalk.transport.netty.internal.RequestResponseCloseHandler.State.WRITE;
import static io.servicetalk.transport.netty.internal.RequestResponseCloseHandler.State.has;
import static io.servicetalk.transport.netty.internal.RequestResponseCloseHandler.State.idle;
import static io.servicetalk.transport.netty.internal.RequestResponseCloseHandler.State.set;
import static io.servicetalk.transport.netty.internal.RequestResponseCloseHandler.State.unset;

/**
 * Intercepts request/response protocol level close commands, eg. HTTP header {@code Connection: close} or
 * {@link SocketChannel} shutdown events and attempts to gracefully close the {@link Channel}.
 * <p>
 * This handler is sufficient to implement <a href="https://tools.ietf.org/html/rfc7230#section-6.6">
 * https://tools.ietf.org/html/rfc7230#section-6.6</a> but is protocol-independent.
 */
class RequestResponseCloseHandler extends CloseHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(RequestResponseCloseHandler.class);

    private final boolean isClient;

    /**
     * Holds the protocol and {@link Channel} state flags.
     */
    private byte state;

    /**
     * Number of queued up requests or responses.
     * <p> This counter is incremented when initiating a request and decremented when initiating a response. Which means
     * that a {@code pending} count of {@code 0} can indicate that a request is still busy reading or writing as
     * indicated by the {@link #state} flags above. A {@code pending} count of {@code 1} can indicate a request was
     * initiated (and potentially finished writing), but a response was not yet initiated.
     */
    private int pending;

    protected interface State {
        byte READ = 0x01;
        byte WRITE = 0x02;
        byte CLOSING = 0x08;
        byte IN_CLOSED = 0x10;
        byte OUT_CLOSED = 0x20;
        byte CLOSED = 0x40;

        byte READ_WRITE = READ | WRITE;
        byte ALL_CLOSED = CLOSED | IN_CLOSED | OUT_CLOSED;
        byte MASK_IDLE = READ_WRITE;

        static boolean idle(int pending, byte state) {
            return (state & MASK_IDLE) == 0 && pending == 0;
        }

        static boolean has(byte state, byte mask) {
            return (state & mask) == mask;
        }

        static byte set(byte state, byte flags) {
            return (byte) (state | flags);
        }

        static byte unset(byte state, byte flags) {
            return (byte) (state & ~flags);
        }
    }

    /**
     * Feed back events to {@link NettyConnection} bypassing the pipeline.
     */
    private Consumer<CloseEvent> eventHandler = $ -> { };

    RequestResponseCloseHandler(final boolean client) {
        isClient = client;
    }

    // Visible for testing
    int getState() {
        return state;
    }

    // Visible for testing
    int getPending() {
        return pending;
    }

    @Override
    void registerEventHandler(final Channel channel, Consumer<CloseEvent> eventHandler) {
        assert channel.eventLoop().inEventLoop();
        assert ((SocketChannel) channel).config().isAllowHalfClosure() :
                "Socket Half-Close DISABLED, this may violate some protocols";
        this.eventHandler = eventHandler;
    }

    private void storeCloseRequestAndEmit(final CloseEvent event) {
        eventHandler.accept(event);
        state = set(state, CLOSING);
    }

    @Override
    public void protocolPayloadBeginInbound(final ChannelHandlerContext ctx) {
        assert ctx.executor().inEventLoop();
        pending = isClient ? pending - 1 : pending + 1;
        state = set(state, READ);
    }

    @Override
    public void protocolPayloadEndInbound(final ChannelHandlerContext ctx) {
        assert ctx.executor().inEventLoop();
        state = unset(state, READ);
        if (has(state, CLOSING)) {
            closeChannelHalfOrFullyOnPayloadEnd(ctx.channel(), PROTOCOL_CLOSING_INBOUND);
        }
    }

    @Override
    public void protocolPayloadBeginOutbound(final ChannelHandlerContext ctx) {
        assert ctx.executor().inEventLoop();
        pending = isClient ? pending + 1 : pending - 1;
        state = set(state, WRITE);
    }

    @Override
    public void protocolPayloadEndOutbound(final ChannelHandlerContext ctx) {
        assert ctx.executor().inEventLoop();
        state = unset(state, WRITE);
        if (has(state, CLOSING)) {
            closeChannelHalfOrFullyOnPayloadEnd(ctx.channel(), PROTOCOL_CLOSING_OUTBOUND);
        }
    }

    @Override
    public void protocolClosingInbound(final ChannelHandlerContext ctx) {
        assert ctx.executor().inEventLoop();
        storeCloseRequestAndEmit(PROTOCOL_CLOSING_INBOUND);
        maybeCloseChannelHalfOrFullyOnClosing(ctx.channel(), PROTOCOL_CLOSING_INBOUND);
    }

    @Override
    public void protocolClosingOutbound(final ChannelHandlerContext ctx) {
        assert ctx.executor().inEventLoop();
        storeCloseRequestAndEmit(PROTOCOL_CLOSING_OUTBOUND);
        maybeCloseChannelHalfOrFullyOnClosing(ctx.channel(), PROTOCOL_CLOSING_OUTBOUND);
    }

    @Override
    void channelClosedInbound(final ChannelHandlerContext ctx) {
        assert ctx.executor().inEventLoop();
        state = set(state, IN_CLOSED);
        storeCloseRequestAndEmit(CHANNEL_CLOSED_INBOUND);
        maybeCloseChannelOnHalfClosed(ctx.channel(), CHANNEL_CLOSED_INBOUND);
        state = unset(state, READ);
    }

    @Override
    void channelClosedOutbound(final ChannelHandlerContext ctx) {
        assert ctx.executor().inEventLoop();
        state = set(state, OUT_CLOSED);
        storeCloseRequestAndEmit(CHANNEL_CLOSED_OUTBOUND);
        maybeCloseChannelOnHalfClosed(ctx.channel(), CHANNEL_CLOSED_OUTBOUND);
        state = unset(state, WRITE);
    }

    @Override
    void userClosing(final Channel channel) {
        assert channel.eventLoop().inEventLoop();
        storeCloseRequestAndEmit(USER_CLOSING);
        maybeCloseChannelHalfOrFullyOnClosing(channel, USER_CLOSING);
    }

    // This closes the channel either completely when there are no more outstanding requests to drain or half-closes
    // when a deferred request was encountered.
    private void closeChannelHalfOrFullyOnPayloadEnd(final Channel channel, final CloseEvent evt) {
        if (idle(pending, state)) {
            // close when all pending requests drained
            closeChannel(channel, evt);
        } else if (isClient && evt == PROTOCOL_CLOSING_OUTBOUND) {
            // deferred half close after current request is done
            halfCloseOutbound(channel, evt);
        } else if (!isClient && evt == PROTOCOL_CLOSING_INBOUND) {
            // deferred half close after current request is done
            halfCloseInbound(channel, evt);
        }
    }

    // Eagerly close on a closing event rather than deferring
    private void maybeCloseChannelHalfOrFullyOnClosing(final Channel channel, final CloseEvent evt) {
        if (idle(pending, state)) {
            closeChannel(channel, evt);
        } else if (isClient) {
            if (evt == PROTOCOL_CLOSING_INBOUND) {
                if (!(has(state, WRITE) && pending == 0)) {
                    // eagerly close the outbound channel unless we are still writing the current request
                    halfCloseOutbound(channel, evt);
                }
                // discards extra pending requests when closing, ensures an eventual "idle" state
                pending = 0;
            } else if (!has(state, WRITE)) { // only USER_CLOSING - Don't abort any request
                halfCloseOutbound(channel, evt);
            }
        } else { // Server
            // eagerly close inbound channel on an outbound close command, unless we are still reading
            // the current request, no eager close on PROTOCOL_CLOSING_INBOUND
            if (evt == PROTOCOL_CLOSING_OUTBOUND) {
                if (!(has(state, READ) && pending == 0)) { // Don't abort current request
                    halfCloseInbound(channel, evt);
                }
                // discards extra pending requests when closing, ensures an eventual "idle" state
                pending = 0;
            } else if (!has(state, READ)) { // only USER_CLOSING - Don't abort any request
                halfCloseInbound(channel, evt);
            }
        }
    }

    // Eagerly close on a closed event rather than deferring
    private void maybeCloseChannelOnHalfClosed(final Channel channel, final CloseEvent evt) {
        if (idle(pending, state)) {
            closeChannel(channel, evt);
        } else if (isClient) {
            if (evt == CHANNEL_CLOSED_INBOUND) {
                // abort if we can't read the current response to completion
                // continue writing current request if read response completed
                // READ == true => current request is cut off, abort
                // pending > 0 + WRITE == true => next request for which we can't respond, abort
                // pending == 0 + WRITE == true => current request still ongoing, defer close
                if (has(state, READ) || (has(state, WRITE) && pending > 0)) {
                    closeChannel(channel, evt, true);
                }
            } else { // CHANNEL_CLOSED_OUTBOUND
                if (has(state, WRITE)) {
                    // ensure we finish reading pending responses, abort others
                    if (pending > 0 && !has(state, READ)) {
                        closeChannel(channel, evt, true);
                    } else {
                        // discards current request, ensures an eventual "idle" state
                        pending--;
                        setSocketResetOnClose(channel);
                    }
                }
            }
        } else { // Server
            if (evt == CHANNEL_CLOSED_INBOUND) {
                if (has(state, READ)) {
                    // abort if we can't read the current request to completion
                    // continue writing pending responses
                    if (pending == 0 || !has(state, WRITE)) {
                        closeChannel(channel, evt, true);
                    } else {
                        // discards current request, ensures an eventual "idle" state
                        pending--;
                        setSocketResetOnClose(channel);
                    }
                }
            } else { // CHANNEL_CLOSED_OUTBOUND
                // ensure we finish reading pending request, abort others
                // WRITE == true => current response is cut off, abort
                // pending == 0 + READ = true => current request still ongoing, defer close
                // pending > 0 => none of the pending requests can be responded to, abort
                if (has(state, WRITE) || !(pending == 0 && has(state, READ))) {
                    closeChannel(channel, evt, true);
                }
            }
        }
    }

    private void closeChannel(final Channel channel, @Nullable final CloseEvent evt) {
        closeChannel(channel, evt, false);
    }

    private void closeChannel(final Channel channel, @Nullable final CloseEvent evt, final boolean forceReset) {
        if (!has(state, CLOSED)) {
            state = set(state, ALL_CLOSED);
            LOGGER.debug("{} Closing channel – evt: {} - reset: {}", channel,
                    evt == null ? "FullCloseAfterHalfClose" : evt, forceReset);
            if (forceReset) {
                setSocketResetOnClose(channel);
            }
            channel.close();
        }
    }

    /**
     * drops send/recv buffers on `close()` and will be perceived by the peer as a connection reset.
     *
     * @param channel sets options if this is a {@link SocketChannel}
     */
    private void setSocketResetOnClose(final Channel channel) {
        if (channel instanceof SocketChannel) {
            ((SocketChannel) channel).config().setSoLinger(0);
        }
    }

    private void halfCloseOutbound(final Channel channel, final CloseEvent evt) {
        assert isClient;
        if (channel instanceof SocketChannel) {
            SocketChannel sChannel = (SocketChannel) channel;
            if (!has(state, OUT_CLOSED)) {
                LOGGER.debug("{} Half-Closing: {}", channel, evt);
                state = unset(state, WRITE);
                sChannel.shutdownOutput().addListener((ChannelFutureListener) this::onHalfClosed);
            }
        }
    }

    private void halfCloseInbound(final Channel channel, final CloseEvent evt) {
        assert !isClient;
        if (channel instanceof SocketChannel) {
            SocketChannel sChannel = (SocketChannel) channel;
            if (!has(state, IN_CLOSED)) {
                LOGGER.debug("{} Half-Closing: {}", channel, evt);
                state = unset(state, READ);
                sChannel.shutdownInput().addListener((ChannelFutureListener) this::onHalfClosed);
            }
        }
    }

    private void onHalfClosed(ChannelFuture future) {
        SocketChannel socketChannel = (SocketChannel) future.channel();
        if (socketChannel.isInputShutdown() && socketChannel.isOutputShutdown()) {
            LOGGER.debug("{} Fully closing socket channel, both input and output shutdown", socketChannel);
            closeChannel(socketChannel, null);
        }
    }
}
