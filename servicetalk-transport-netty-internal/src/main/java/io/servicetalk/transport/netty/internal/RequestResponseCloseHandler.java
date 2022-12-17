/*
 * Copyright © 2018, 2020 Apple Inc. and the ServiceTalk project authors
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
import io.netty.channel.ChannelPromise;
import io.netty.channel.socket.DuplexChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.ssl.SslHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;
import javax.annotation.Nullable;

import static io.netty.channel.ChannelOption.ALLOW_HALF_CLOSURE;
import static io.servicetalk.transport.netty.internal.ByteMaskUtils.isAllSet;
import static io.servicetalk.transport.netty.internal.ByteMaskUtils.isAnySet;
import static io.servicetalk.transport.netty.internal.ByteMaskUtils.set;
import static io.servicetalk.transport.netty.internal.ByteMaskUtils.unset;
import static io.servicetalk.transport.netty.internal.CloseHandler.CloseEvent.CHANNEL_CLOSED_INBOUND;
import static io.servicetalk.transport.netty.internal.CloseHandler.CloseEvent.CHANNEL_CLOSED_OUTBOUND;
import static io.servicetalk.transport.netty.internal.CloseHandler.CloseEvent.GRACEFUL_USER_CLOSING;
import static io.servicetalk.transport.netty.internal.CloseHandler.CloseEvent.PROTOCOL_CLOSING_INBOUND;
import static io.servicetalk.transport.netty.internal.CloseHandler.CloseEvent.PROTOCOL_CLOSING_OUTBOUND;
import static java.lang.Boolean.TRUE;
import static java.util.Objects.requireNonNull;

/**
 * Intercepts request/response protocol level close commands, eg. HTTP header {@code Connection: close} or
 * {@link SocketChannel} shutdown events and attempts to gracefully close the {@link Channel}.
 * <p>
 * This handler is sufficient to implement <a href="https://tools.ietf.org/html/rfc7230#section-6.6">
 * https://tools.ietf.org/html/rfc7230#section-6.6</a> but is protocol-independent.
 */
final class RequestResponseCloseHandler extends CloseHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(RequestResponseCloseHandler.class);
    private static final byte READ = 0x01;
    private static final byte WRITE = 0x02;
    private static final byte DISCARDING_SERVER_INPUT = 0x04;
    private static final byte CLOSING_SERVER_GRACEFULLY = 0x08;
    private static final byte IN_CLOSED = 0x10;
    private static final byte OUT_CLOSED = 0x20;
    private static final byte CLOSED = 0x40;
    private static final byte ALL_CLOSED = CLOSED | IN_CLOSED | OUT_CLOSED;
    private static final byte IN_OR_OUT_CLOSED = IN_CLOSED | OUT_CLOSED;
    private static final byte READ_OR_WRITE = READ | WRITE;
    private static final byte DISCARDING_SERVER_OR_IN_CLOSED = DISCARDING_SERVER_INPUT | IN_CLOSED;
    private static final byte GRACEFUL_SERVER_OR_OUT_CLOSED = OUT_CLOSED | CLOSING_SERVER_GRACEFULLY;
    private static final byte GRACEFUL_SERVER_OR_IN_CLOSED = IN_CLOSED | CLOSING_SERVER_GRACEFULLY;

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

    /**
     * Original {@link CloseEvent} that initiated closing.
     */
    @Nullable
    private CloseEvent closeEvent;

    /**
     * Feed back events to {@link DefaultNettyConnection} bypassing the pipeline.
     */
    private Consumer<CloseEvent> eventHandler = __ -> { };

    RequestResponseCloseHandler(final boolean client) {
        isClient = client;
    }

    // Visible for testing
    int state() {
        return state;
    }

    // Visible for testing
    int pending() {
        return pending;
    }

    @Override
    void registerEventHandler(final Channel channel, Consumer<CloseEvent> eventHandler) {
        assert channel.eventLoop().inEventLoop();
        assert channel instanceof DuplexChannel : "Channel does not implement DuplexChannel";
        assert TRUE.equals(channel.config().getOption(ALLOW_HALF_CLOSURE)) :
                "Half-Closure DISABLED, this may violate some protocols";
        this.eventHandler = requireNonNull(eventHandler);
    }

    private void storeCloseRequestAndEmit(final CloseEvent event) {
        if (this.closeEvent == null) {
            this.closeEvent = event;
        }
        eventHandler.accept(event);
    }

    @Override
    public void protocolPayloadBeginInbound(final ChannelHandlerContext ctx) {
        assert ctx.executor().inEventLoop();
        pending = isClient ? pending - 1 : pending + 1;
        assert pending >= 0 : "Negative pending counter";
        state = set(state, READ);
    }

    @Override
    public void protocolPayloadEndInbound(final ChannelHandlerContext ctx) {
        assert ctx.executor().inEventLoop();
        ctx.pipeline().fireUserEventTriggered(InboundDataEndEvent.INSTANCE);
        state = unset(state, READ);
        if (closeEvent != null) {
            closeChannelHalfOrFullyOnPayloadEnd(ctx.channel(), closeEvent, true);
        }
    }

    @Override
    public void protocolPayloadBeginOutbound(final ChannelHandlerContext ctx) {
        assert ctx.executor().inEventLoop();
        pending = isClient ? pending + 1 : pending - 1;
        assert pending >= 0 : "Negative pending counter";
        state = set(state, WRITE);
    }

    @Override
    public void protocolPayloadEndOutbound(final ChannelHandlerContext ctx, @Nullable final ChannelPromise promise) {
        if (promise == null) {
            protocolPayloadEndOutbound0(ctx);
            return;
        }
        ctx.pipeline().fireUserEventTriggered(OutboundDataEndEvent.INSTANCE);
        promise.addListener(f -> protocolPayloadEndOutbound0(ctx));
    }

    private void protocolPayloadEndOutbound0(final ChannelHandlerContext ctx) {
        state = unset(state, WRITE);
        final CloseEvent evt = this.closeEvent;
        if (evt != null) {
            closeChannelHalfOrFullyOnPayloadEnd(ctx.channel(), evt, false);
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
        if (!isAllSet(state, IN_CLOSED)) {
            state = set(state, IN_CLOSED);
            // Use the actual event that initiated graceful closure:
            final CloseEvent evt = isAllSet(state, CLOSING_SERVER_GRACEFULLY) ? closeEvent : CHANNEL_CLOSED_INBOUND;
            assert evt != null;
            storeCloseRequestAndEmit(evt);
            maybeCloseChannelOnHalfClosed(ctx.channel(), evt);
            state = unset(state, READ);
        }
    }

    @Override
    void channelClosedOutbound(final ChannelHandlerContext ctx) {
        assert ctx.executor().inEventLoop();
        if (!isAllSet(state, OUT_CLOSED)) {
            state = set(state, OUT_CLOSED);
            storeCloseRequestAndEmit(CHANNEL_CLOSED_OUTBOUND);
            if (!isAllSet(state, CLOSING_SERVER_GRACEFULLY)) {
                // Only try to close when we are not closing server gracefully
                maybeCloseChannelOnHalfClosed(ctx.channel(), CHANNEL_CLOSED_OUTBOUND);
            }
            state = unset(state, WRITE);
        }
    }

    @Override
    void channelCloseNotify(final ChannelHandlerContext ctx) {
        if (isAnySet(state, GRACEFUL_SERVER_OR_OUT_CLOSED)) {
            // We already closed outbound side of the channel, which triggers closure of SSLEngine and results in
            // SslCloseCompletionEvent#SUCCESS event generated immediately. Connection is already in a closing state,
            // we should ignore this event and wait for ChannelInputShutdownReadComplete from the remote peer.
            return;
        }

        // Notify close handler first to enhance error reporting and prevent LB from selecting this connection.
        channelClosedInbound(ctx);
        // We MUST respond with a "close_notify" alert and close down the connection immediately, discarding any pending
        // writes.
        closeChannelOutbound(ctx.channel());
    }

    @Override
    void closeChannelInbound(final Channel channel) {
        // Do not reset INBOUND when server is closing gracefully. This event is triggered during processing of
        // ChannelOutputShutdownEvent if the USER_CLOSE was initiated after response was written.
        if (!isAnySet(state, GRACEFUL_SERVER_OR_IN_CLOSED)) {
            LOGGER.debug("{} Half-Closing INBOUND (reset)", channel);
            setSocketResetOnClose(channel);
            ((DuplexChannel) channel).shutdownInput().addListener((ChannelFutureListener) this::onHalfClosed);
        }
    }

    @Override
    void closeChannelOutbound(final Channel channel) {
        if (!isAllSet(state, OUT_CLOSED)) {
            LOGGER.debug("{} Half-Closing OUTBOUND (reset)", channel);
            setSocketResetOnClose(channel);
            halfCloseOutbound(channel, true);
        }
    }

    @Override
    void gracefulUserClosing(final Channel channel) {
        assert channel.eventLoop().inEventLoop();
        storeCloseRequestAndEmit(GRACEFUL_USER_CLOSING);
        maybeCloseChannelHalfOrFullyOnClosing(channel, GRACEFUL_USER_CLOSING);
    }

    @Override
    void channelClose(final Channel channel) {
        // We need to set SO_LINGER before we invoke close if we are not writing and still reading. This forces sending
        // RST (instead of FIN) which the peer will interpret as "full close" and any future writes by the peer will
        // fail (if the peer is half closed, not reading, but still writing). Otherwise, the peer write attempts may
        // be accepted by the OS, only to generate RST when the local peer receives the data, but the peer has finished
        // reading which may prevent processing the RST and be hung.
        if (isAllSet(state, READ) && !isAllSet(state, WRITE) && channel instanceof SocketChannel) {
            setSocketResetOnClose((SocketChannel) channel);
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(32);
        if (isClient) {
            sb.append("CLIENT,");
        } else {
            sb.append("SERVER,");
        }
        sb.append(pending);
        if (isAllSet(state, READ)) {
            sb.append(",READ");
        }
        if (isAllSet(state, WRITE)) {
            sb.append(",WRITE");
        }
        if (isAllSet(state, IN_CLOSED)) {
            sb.append(",IN_CLOSED");
        }
        if (isAllSet(state, OUT_CLOSED)) {
            sb.append(",OUT_CLOSED");
        }
        if (isAllSet(state, DISCARDING_SERVER_INPUT)) {
            sb.append(",DISCARDING_SERVER_INPUT");
        }
        if (isAllSet(state, CLOSING_SERVER_GRACEFULLY)) {
            sb.append(",CLOSING_SERVER_GRACEFULLY");
        }
        if (isAllSet(state, CLOSED)) {
            sb.append(",CLOSED");
        }
        if (closeEvent != null) {
            sb.append(',').append(closeEvent);
        }
        return sb.toString();
    }

    // This closes the channel either completely when there are no more outstanding requests to drain or half-closes
    // when a deferred request was encountered.
    private void closeChannelHalfOrFullyOnPayloadEnd(final Channel channel, final CloseEvent evt,
                                                     final boolean endInbound) {

        if (isIdle(pending, state)) {
            if (isClient || isAllSet(state, IN_CLOSED) ||
                    (evt != GRACEFUL_USER_CLOSING && evt != PROTOCOL_CLOSING_OUTBOUND)) {
                closeChannel(channel, evt);
            } else {
                serverCloseGracefully(channel);
            }
        } else if (!isClient && endInbound) {
            // current request is complete, discard further inbound
            serverHalfCloseInbound(channel);
        }
        // do not perform half-closure on the client to prevent a server from premature connection closure
    }

    // Eagerly close on a closing event rather than deferring
    private void maybeCloseChannelHalfOrFullyOnClosing(final Channel channel, final CloseEvent evt) {
        if (isIdle(pending, state)) { // Only GRACEFUL_USER_CLOSING
            assert evt == GRACEFUL_USER_CLOSING;
            if (isClient) {
                closeChannel(channel, evt);
            } else {
                serverCloseGracefully(channel);
            }
        } else if (isClient) {
            if (evt == PROTOCOL_CLOSING_INBOUND && pending != 0) {
                // Protocol inbound closing for a client is when a response is read, which decrements the pending
                // count before reading the inbound closure signal. This means if pending > 0 there are more
                // requests pending responses but the peer has signalled close. We need to abort write for pending
                // requests:
                if (isAllSet(state, WRITE)) {
                    channel.pipeline().fireUserEventTriggered(AbortWritesEvent.INSTANCE);
                    state = unset(state, WRITE);
                }
                pending = 0;
            }
        } else if (evt == PROTOCOL_CLOSING_OUTBOUND) { // Server
            // eagerly close inbound channel on an outbound close command, unless we are still reading
            // the current request, no eager close on PROTOCOL_CLOSING_INBOUND
            if (pending != 0 || !isAllSet(state, READ)) { // Don't abort current request
                serverHalfCloseInbound(channel);
            }
            // discards extra pending requests when closing, ensures an eventual "idle" state
            pending = 0;
        } else if (!isAllSet(state, READ)) { // Server && GRACEFUL_USER_CLOSING - Don't abort any request
            assert evt == GRACEFUL_USER_CLOSING;
            serverHalfCloseInbound(channel);
        }
    }

    // Eagerly close on a closed event rather than deferring
    private void maybeCloseChannelOnHalfClosed(final Channel channel, final CloseEvent evt) {
        if (isIdle(pending, state)) {
            closeChannel(channel, evt);
        } else if (isClient) {
            if (evt == CHANNEL_CLOSED_INBOUND) {
                // pending > 0 + WRITE => next request for which we can't respond, abort
                if (pending != 0) {
                    if (isAllSet(state, WRITE)) {
                        closeAndResetChannel(channel, evt);
                    } else {
                        closeChannel(channel, evt);
                    }
                } else { // current request still ongoing, defer close, but unset READ flag
                    state = unset(state, READ);
                    if (isIdle(pending, state)) {
                        closeChannel(channel, evt);
                    }
                }
            } else if (isAllSet(state, WRITE)) { // evt == CHANNEL_CLOSED_OUTBOUND
                assert evt == CHANNEL_CLOSED_OUTBOUND;
                // ensure we finish reading pending responses, abort others
                setSocketResetOnClose(channel);
                if (pending <= 1 && !isAllSet(state, READ)) {
                    closeChannel(channel, evt);
                } else if (pending != 0) {
                    // discards current request, ensures an eventual "idle" state
                    --pending;
                }
            }
        } else if (evt == CHANNEL_CLOSED_INBOUND) { // Server
            if (isAllSet(state, READ)) {
                // defer close to allow server error response, but unset READ flag
                state = unset(state, READ);
                setSocketResetOnClose(channel);
                if (isIdle(pending, state)) {
                    closeChannel(channel, evt);
                }
            }
        } else if (pending != 0) { // Server && CHANNEL_CLOSED_OUTBOUND
            assert evt == CHANNEL_CLOSED_OUTBOUND;
            // pending > 0 => ensures we finish reading current request, abort others we can't respond to anyway
            closeAndResetChannel(channel, evt);
        } else if (!isAllSet(state, READ)) { // Server && CHANNEL_CLOSED_OUTBOUND && pending == 0
            assert evt == CHANNEL_CLOSED_OUTBOUND;
            // last response, we are not reading and OUTBOUND is closed, so just close the channel.
            closeChannel(channel, evt);
        }
    }

    private void closeChannel(final Channel channel, @Nullable final CloseEvent evt) {
        if (!isAllSet(state, CLOSED)) {
            state = set(state, ALL_CLOSED);
            LOGGER.debug("{} Closing channel – evt: {}", channel, evt == null ? "FullCloseAfterHalfClose" : evt);
            channel.close();
        }
    }

    private void closeAndResetChannel(final Channel channel, @Nullable final CloseEvent evt) {
        if (!isAllSet(state, CLOSED)) {
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
        if (!isAllSet(state, IN_OR_OUT_CLOSED) && channel instanceof SocketChannel) {
            setSocketResetOnClose((SocketChannel) channel);
        }
    }

    private void serverCloseGracefully(final Channel channel) {
        // Perform half-closure as described in https://tools.ietf.org/html/rfc7230#section-6.6
        serverHalfCloseInbound(channel);
        serverHalfCloseOutbound(channel);
    }

    private void serverHalfCloseInbound(final Channel channel) {
        assert !isClient;
        if (!isAnySet(state, DISCARDING_SERVER_OR_IN_CLOSED)) {
            // Instead of actual half-closure via DuplexChannel.shutdownInput() we request the pipeline to discard all
            // further inbound data until the FIN is received. Incoming FIN from the client-side
            // (ChannelInputShutdownReadComplete event) notifies server that client received the last response and is
            // also closing the connection. Therefore, we can complete graceful closure and close server's connection.
            // DuplexChannel.shutdownInput() silently discards all incoming data at OS level and does not notify netty
            // when the FIN is received.
            LOGGER.debug("{} Discarding further INBOUND", channel);
            state = set(unset(state, READ), DISCARDING_SERVER_INPUT);
            channel.pipeline().fireUserEventTriggered(DiscardFurtherInboundEvent.INSTANCE);
        }
    }

    private void serverHalfCloseOutbound(final Channel channel) {
        assert !isClient && isIdle(pending, state);
        if (!isAllSet(state, OUT_CLOSED)) {
            state = set(state, CLOSING_SERVER_GRACEFULLY);
            LOGGER.debug("{} Half-Closing OUTBOUND", channel);
            halfCloseOutbound(channel, false);
            // Final channel.close() will happen when FIN (ChannelInputShutdownReadComplete) is received
        }
    }

    private void halfCloseOutbound(final Channel channel, final boolean registerOnHalfClosed) {
        SslHandler sslHandler = channel.pipeline().get(SslHandler.class);
        if (sslHandler != null) {
            // send close_notify: https://tools.ietf.org/html/rfc5246#section-7.2.1
            sslHandler.closeOutbound().addListener(f -> {
                final ChannelFuture cf = ((DuplexChannel) channel).shutdownOutput();
                if (registerOnHalfClosed) {
                    cf.addListener((ChannelFutureListener) this::onHalfClosed);
                }
            });
        } else {
            final ChannelFuture cf = ((DuplexChannel) channel).shutdownOutput();
            if (registerOnHalfClosed) {
                cf.addListener((ChannelFutureListener) this::onHalfClosed);
            }
        }
    }

    private void onHalfClosed(ChannelFuture future) {
        DuplexChannel dplxChannel = (DuplexChannel) future.channel();
        if (dplxChannel.isInputShutdown() && dplxChannel.isOutputShutdown()) {
            LOGGER.debug("{} Fully closing socket channel, both input and output shutdown", dplxChannel);
            closeChannel(dplxChannel, null);
        }
    }

    private static boolean isIdle(int pending, byte state) {
        return pending == 0 && !isAnySet(state, READ_OR_WRITE);
    }
}
