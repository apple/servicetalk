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
import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.EventLoop;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.SocketChannelConfig;

import java.nio.channels.ClosedChannelException;
import java.util.function.Consumer;
import javax.annotation.Nullable;

/**
 * Contract between protocol codecs and a close handler.
 */
public abstract class CloseHandler {

    public static final CloseHandler UNSUPPORTED_PROTOCOL_CLOSE_HANDLER = new UnsupportedProtocolHandler();
    public static final CloseHandler PROTOCOL_OUTBOUND_CLOSE_HANDLER = new ProtocolOutboundCloseEventHandler();

    /**
     * New {@link CloseHandler} instance.
     *
     * @param client operation mode, {@code TRUE} for {@code client} or {@code FALSE} for {@code server}
     * @param config The {@link ChannelConfig} associated with the channel to create the {@link CloseHandler} for.
     * This {@link ChannelConfig} maybe modified to ensure the underlying options allow for half-closure.
     * @return a new connection close handler with behavior for a pipelined request/response client or server
     */
    public static CloseHandler forPipelinedRequestResponse(boolean client, ChannelConfig config) {
        if (config instanceof SocketChannelConfig) {
            ((SocketChannelConfig) config).setAllowHalfClosure(true);
        }
        config.setAutoClose(false);
        return new RequestResponseCloseHandler(client);
    }

    /**
     * Signal begin of inbound payload, to be emitted from the {@link EventLoop} for the {@link Channel}.
     *
     * @param ctx {@link ChannelHandlerContext}
     */
    public abstract void protocolPayloadBeginInbound(ChannelHandlerContext ctx);

    /**
     * Signal end of inbound payload, to be emitted from the {@link EventLoop} for the {@link Channel}.
     *
     * @param ctx {@link ChannelHandlerContext}
     */
    public abstract void protocolPayloadEndInbound(ChannelHandlerContext ctx);

    /**
     * Signal begin of outbound payload, to be emitted from the {@link EventLoop} for the {@link Channel}.
     *
     * @param ctx {@link ChannelHandlerContext}
     */
    public abstract void protocolPayloadBeginOutbound(ChannelHandlerContext ctx);

    /**
     * Signal end of outbound payload, to be emitted from the {@link EventLoop} for the {@link Channel}.
     *
     * @param ctx {@link ChannelHandlerContext}
     */
    public abstract void protocolPayloadEndOutbound(ChannelHandlerContext ctx);

    /**
     * Signal end of outbound payload, once successfully written to the {@link Channel}.
     *
     * @param ctx {@link ChannelHandlerContext}
     */
    public abstract void protocolPayloadEndOutboundSuccess(ChannelHandlerContext ctx);

    /**
     * Signal inbound close command observed, to be emitted from the {@link EventLoop} for the {@link Channel}.
     *
     * @param ctx {@link ChannelHandlerContext}
     */
    public abstract void protocolClosingInbound(ChannelHandlerContext ctx);

    /**
     * Signal outbound close command observed, to be emitted from the {@link EventLoop} for the {@link Channel}.
     *
     * @param ctx {@link ChannelHandlerContext}
     */
    public abstract void protocolClosingOutbound(ChannelHandlerContext ctx);

    /**
     * @param channel the {@link Channel} for which this event handler is registering
     * @param eventHandler receives {@link CloseEvent}, to be emitted from the {@link EventLoop} for the {@link Channel}
     */
    abstract void registerEventHandler(Channel channel, Consumer<CloseEvent> eventHandler);

    /**
     * Signal {@link Channel} inbound close command observed, to be emitted from the {@link EventLoop} for the channel.
     *
     * @param ctx {@link ChannelHandlerContext}
     */
    abstract void channelClosedInbound(ChannelHandlerContext ctx);

    /**
     * Signal {@link Channel} outbound close command observed, to be emitted from the {@link EventLoop} for the channel.
     *
     * @param ctx {@link ChannelHandlerContext}
     */
    abstract void channelClosedOutbound(ChannelHandlerContext ctx);

    /**
     * Request {@link Channel} inbound close, to be emitted from the {@link EventLoop} for the channel.
     * <p>
     * This method will not ensure graceful closure of the channel inbound and may abort reads.
     *
     * @param channel {@link Channel}
     */
    abstract void closeChannelInbound(Channel channel);

    /**
     * Request {@link Channel} outbound close, to be emitted from the {@link EventLoop} for the channel.
     * <p>
     * This method will not ensure graceful closure of the channel outbound and may abort reads.
     *
     * @param channel {@link Channel}
     */
    abstract void closeChannelOutbound(Channel channel);

    /**
     * Signal a user requested close of the {@link Channel}, to be emitted from the {@link EventLoop} for the channel.
     * <p>
     * This translates to a protocol level close command, but is initiated by the user.
     *
     * @param channel {@link Channel}
     */
    abstract void userClosing(Channel channel);

    /**
     * These events indicate an event was observed from the protocol or {@link Channel} that indicates the end of the
     * {@link Channel} and no further requests should be attempted.
     */
    public enum CloseEvent {
        /**
         * Outbound protocol close command observed eg. HTTP header: {@code Connection: close}.
         */
        PROTOCOL_CLOSING_OUTBOUND("The application protocol closed the write side of this connection. " +
                "This maybe the result of sending an HTTP header such as Connection: close."),
        /**
         * Inbound protocol close command observed eg. HTTP header: {@code Connection: close}.
         */
        PROTOCOL_CLOSING_INBOUND("The application protocol closed the read side of this connection. " +
                "This maybe the result of sending an HTTP header such as Connection: close."),
        /**
         * User initiated close command, depends on the implementation but usually resembles outbound protocol close.
         */
        USER_CLOSING("The close* method was called in the local application."),
        /**
         * Outbound {@link SocketChannel} shutdown observed.
         */
        CHANNEL_CLOSED_OUTBOUND("The transport backing this connection has been shutdown (write)"),
        /**
         * Inbound {@link SocketChannel} shutdown observed.
         */
        CHANNEL_CLOSED_INBOUND("The transport backing this connection has been shutdown (read)");

        private final String description;

        CloseEvent(final String description) {
            this.description = description;
        }

        Throwable wrapError(@Nullable Throwable cause, Channel channel) {
            return new CloseEventObservedException(cause, this, channel);
        }
    }

    /**
     * {@link ClosedChannelException} with additional meta-data to provide more context on what side initiated the close
     * event.
     */
    public static final class CloseEventObservedException extends ClosedChannelException {
        private static final long serialVersionUID = -4181001701486049092L;

        private final CloseEvent event;
        private final String channelDetails;

        private CloseEventObservedException(@Nullable Throwable cause,
                                            final CloseEvent closeEvent,
                                            final Channel channel) {
            this.event = closeEvent;
            this.channelDetails = channel.toString();
            initCause(cause);
        }

        /**
         * {@link CloseEvent} was observed from the protocol or {@link Channel}.
         *
         * @return {@link CloseEvent} was observed
         */
        public CloseEvent event() {
            return event;
        }

        @Override
        public String getMessage() {
            return event.name() + "(" + event.description + ") " + channelDetails;
        }

        @Override
        public synchronized Throwable fillInStackTrace() {
            // we have the option to not provide an additional stack trace if it is too expensive.
            // return super.fillInStackTrace();
            return this;
        }
    }

    private static final class UnsupportedProtocolHandler extends CloseHandler {

        @Override
        void registerEventHandler(final Channel channel, final Consumer<CloseEvent> eventHandler) {
        }

        @Override
        void channelClosedInbound(final ChannelHandlerContext ctx) {
        }

        @Override
        void channelClosedOutbound(final ChannelHandlerContext ctx) {
        }

        @Override
        void closeChannelInbound(final Channel channel) {
            channel.close();
        }

        @Override
        void closeChannelOutbound(final Channel channel) {
            channel.close();
        }

        @Override
        void userClosing(final Channel channel) {
            channel.close();
        }

        @Override
        public void protocolPayloadBeginInbound(final ChannelHandlerContext ctx) {
        }

        @Override
        public void protocolPayloadEndInbound(final ChannelHandlerContext ctx) {
        }

        @Override
        public void protocolPayloadBeginOutbound(final ChannelHandlerContext ctx) {
        }

        @Override
        public void protocolPayloadEndOutbound(final ChannelHandlerContext ctx) {
        }

        @Override
        public void protocolPayloadEndOutboundSuccess(final ChannelHandlerContext ctx) {
        }

        @Override
        public void protocolClosingInbound(final ChannelHandlerContext ctx) {
        }

        @Override
        public void protocolClosingOutbound(final ChannelHandlerContext ctx) {
        }
    }

    private static final class ProtocolOutboundCloseEventHandler extends CloseHandler {

        @Override
        void registerEventHandler(final Channel channel, final Consumer<CloseEvent> eventHandler) {
        }

        @Override
        void channelClosedInbound(final ChannelHandlerContext ctx) {
        }

        @Override
        void channelClosedOutbound(final ChannelHandlerContext ctx) {
        }

        @Override
        void closeChannelInbound(final Channel channel) {
            channel.close();
        }

        @Override
        void closeChannelOutbound(final Channel channel) {
            channel.close();
        }

        @Override
        void userClosing(final Channel channel) {
            channel.close();
        }

        @Override
        public void protocolPayloadBeginInbound(final ChannelHandlerContext ctx) {
        }

        @Override
        public void protocolPayloadEndInbound(final ChannelHandlerContext ctx) {
        }

        @Override
        public void protocolPayloadBeginOutbound(final ChannelHandlerContext ctx) {
        }

        @Override
        public void protocolPayloadEndOutbound(final ChannelHandlerContext ctx) {
            ctx.pipeline().fireUserEventTriggered(ProtocolPayloadEndEvent.OUTBOUND);
        }

        @Override
        public void protocolPayloadEndOutboundSuccess(final ChannelHandlerContext ctx) {
        }

        @Override
        public void protocolClosingInbound(final ChannelHandlerContext ctx) {
        }

        @Override
        public void protocolClosingOutbound(final ChannelHandlerContext ctx) {
        }
    }

    /**
     * Netty UserEvent to indicate the end of a payload was observed at the transport.
     */
    static final class ProtocolPayloadEndEvent {
        /**
         * Netty UserEvent instance to indicate an outbound end of payload.
         */
        static final ProtocolPayloadEndEvent OUTBOUND = new ProtocolPayloadEndEvent();

        private ProtocolPayloadEndEvent() {
            // No instances.
        }
    }

    /**
     * Netty UserEvent to indicate the output writes should be aborted because the channel is closing.
     */
    static final class AbortWritesEvent {
        static final AbortWritesEvent INSTANCE = new AbortWritesEvent();

        private AbortWritesEvent() {
            // No instances.
        }
    }
}
