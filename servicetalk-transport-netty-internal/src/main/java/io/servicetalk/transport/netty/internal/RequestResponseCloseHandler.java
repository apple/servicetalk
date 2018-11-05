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
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.socket.DuplexChannel;
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
import static io.servicetalk.transport.netty.internal.RequestResponseCloseHandler.State.IN_OUT_CLOSED;
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
        byte CLOSING = 0x04;
        byte IN_CLOSED = 0x08;
        byte OUT_CLOSED = 0x10;
        byte CLOSED = 0x20;

        byte ALL_CLOSED = CLOSED | IN_CLOSED | OUT_CLOSED;
        byte IN_OUT_CLOSED = IN_CLOSED | OUT_CLOSED;
        byte MASK_IDLE = READ | WRITE;

        static boolean idle(int pending, byte state) {
            return pending == 0 && (state & MASK_IDLE) == 0;
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
     * Feed back events to {@link DefaultNettyConnection} bypassing the pipeline.
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

    private static boolean isAllowHalfClosure(final Channel channel) {
        return (channel instanceof SocketChannel) ? ((SocketChannel) channel).config().isAllowHalfClosure() :
                channel instanceof EmbeddedChannel; // Exceptionally used in unit tests
    }

    @Override
    void registerEventHandler(final Channel channel, Consumer<CloseEvent> eventHandler) {
        assert channel.eventLoop().inEventLoop();
        assert isAllowHalfClosure(channel) : "Socket Half-Close DISABLED, this may violate some protocols";
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
    void closeChannelInbound(final Channel channel) {
        if (!has(state, IN_CLOSED)) {
            LOGGER.debug("{} Half-Closing INBOUND (reset)", channel);
            setSocketResetOnClose(channel);
            ((DuplexChannel) channel).shutdownInput().addListener((ChannelFutureListener) this::onHalfClosed);
        }
    }

    @Override
    void closeChannelOutbound(final Channel channel) {
        if (!has(state, OUT_CLOSED)) {
            LOGGER.debug("{} Half-Closing OUTBOUND (reset)", channel);
            setSocketResetOnClose(channel);
            ((DuplexChannel) channel).shutdownOutput().addListener((ChannelFutureListener) this::onHalfClosed);
        }
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
            clientHalfCloseOutbound(channel);
        } else if (!isClient && evt == PROTOCOL_CLOSING_INBOUND) {
            // deferred half close after current request is done
            serverHalfCloseInbound(channel);
        }
    }

    // Eagerly close on a closing event rather than deferring
    private void maybeCloseChannelHalfOrFullyOnClosing(final Channel channel, final CloseEvent evt) {
        if (idle(pending, state)) {
            closeChannel(channel, evt);
        } else if (isClient) {
            if (evt == PROTOCOL_CLOSING_INBOUND) {
                if (pending != 0 || !has(state, WRITE)) {
                    // eagerly close the outbound channel unless we are still writing the current request
                    if (has(state, WRITE)) {
                        setSocketResetOnClose(channel);
                    }
                    clientHalfCloseOutbound(channel);
                }
                // discards extra pending requests when closing, ensures an eventual "idle" state
                pending = 0;
            } else if (!has(state, WRITE)) { // only USER_CLOSING - Don't abort any request
                clientHalfCloseOutbound(channel);
            }
        } else if (evt == PROTOCOL_CLOSING_OUTBOUND) { // Server
            // eagerly close inbound channel on an outbound close command, unless we are still reading
            // the current request, no eager close on PROTOCOL_CLOSING_INBOUND
            if (pending != 0 || !has(state, READ)) { // Don't abort current request
                serverHalfCloseInbound(channel);
            }
            // discards extra pending requests when closing, ensures an eventual "idle" state
            pending = 0;
        } else if (!has(state, READ)) { // Server && USER_CLOSING - Don't abort any request
            serverHalfCloseInbound(channel);
        }
    }

    // Eagerly close on a closed event rather than deferring
    private void maybeCloseChannelOnHalfClosed(final Channel channel, final CloseEvent evt) {
        if (idle(pending, state)) {
            closeChannel(channel, evt);
        } else if (isClient) {
            if (evt == CHANNEL_CLOSED_INBOUND) {
                // pending > 0 + WRITE => next request for which we can't respond, abort
                if (pending != 0) {
                    if (has(state, WRITE)) {
                        closeAndResetChannel(channel, evt);
                    } else {
                        closeChannel(channel, evt);
                    }
                } else { // current request still ongoing, defer close, but unset READ flag
                    state = unset(state, READ);
                    if (idle(pending, state)) {
                        closeChannel(channel, evt);
                    }
                }
            } else if (has(state, WRITE)) { // evt == CHANNEL_CLOSED_OUTBOUND
                // ensure we finish reading pending responses, abort others
                setSocketResetOnClose(channel);
                if (pending <= 1 && !has(state, READ)) {
                    closeChannel(channel, evt);
                } else if (pending != 0) {
                    // discards current request, ensures an eventual "idle" state
                    --pending;
                }
            }
        } else if (evt == CHANNEL_CLOSED_INBOUND) { // Server
            if (has(state, READ)) {
                // defer close to allow server error response, but unset READ flag
                state = unset(state, READ);
                setSocketResetOnClose(channel);
                if (idle(pending, state)) {
                    closeChannel(channel, evt);
                }
            }
        } else if (pending != 0) { // Server && CHANNEL_CLOSED_OUTBOUND
            // pending > 0 => ensures we finish reading current request, abort others we can't respond to anyway
            closeAndResetChannel(channel, evt);
        } else if (!has(state, READ)) { // Server && CHANNEL_CLOSED_OUTBOUND && pending == 0
            // last response, we are not reading and OUTBOUND is closed, so just close the channel.
            closeChannel(channel, evt);
        }
    }

    private void closeChannel(final Channel channel, @Nullable final CloseEvent evt) {
        if (!has(state, CLOSED)) {
            state = set(state, ALL_CLOSED);
            LOGGER.debug("{} Closing channel – evt: {}", channel, evt == null ? "FullCloseAfterHalfClose" : evt);
            channel.close();
        }
    }

    private void closeAndResetChannel(final Channel channel, @Nullable final CloseEvent evt) {
        if (!has(state, CLOSED)) {
            LOGGER.debug("{} Closing channel – evt: {} - reset",
                    channel, evt == null ? "FullCloseAfterHalfClose" : evt);
            setSocketResetOnClose(channel);
            state = set(state, ALL_CLOSED); // needs to be set after setting RST
            channel.close();
        }
    }

    /**
     * drops send/recv buffers on `close()` and will be perceived by the peer as a connection reset.
     *
     * @param channel sets options if this is a {@link SocketChannel}
     */
    private void setSocketResetOnClose(final Channel channel) {
        // When both IN_CLOSED and OUT_CLOSED have been observed we should NOT attempt to set socket options. However
        // when only IN_CLOSED is observed as part of a TCP RST we also shouldn't attempt to set, but there is no
        // reliable event for this (in netty/JDK) so the best we can do is catch and log the exception.
        if (channel instanceof SocketChannel && !has(state, IN_OUT_CLOSED)) {
            try {
                ((SocketChannel) channel).config().setSoLinger(0);
            } catch (Exception e) {
                LOGGER.trace("{} set SO_LINGER=0 failed (expected when IN+OUT or IN+RST closed channel): {}",
                        channel, e.getMessage());
            }
        }
    }

    private void clientHalfCloseOutbound(final Channel channel) {
        assert isClient;
        if (!has(state, OUT_CLOSED) && channel instanceof DuplexChannel) {
            LOGGER.debug("{} Half-Closing OUTBOUND", channel);
            state = unset(state, WRITE);
            ((DuplexChannel) channel).shutdownOutput().addListener((ChannelFutureListener) this::onHalfClosed);
        }
    }

    private void serverHalfCloseInbound(final Channel channel) {
        assert !isClient;
        if (!has(state, IN_CLOSED) && channel instanceof DuplexChannel) {
            LOGGER.debug("{} Half-Closing INBOUND", channel);
            state = unset(state, READ);
            ((DuplexChannel) channel).shutdownInput().addListener((ChannelFutureListener) this::onHalfClosed);
        }
    }

    private void onHalfClosed(ChannelFuture future) {
        DuplexChannel dplxChannel = (DuplexChannel) future.channel();
        if (dplxChannel.isInputShutdown() && dplxChannel.isOutputShutdown()) {
            LOGGER.debug("{} Fully closing socket channel, both input and output shutdown", dplxChannel);
            closeChannel(dplxChannel, null);
        }
    }
}
