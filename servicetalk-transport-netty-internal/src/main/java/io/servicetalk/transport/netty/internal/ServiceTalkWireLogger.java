/*
 * Copyright © 2020 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.buffer.api.BufferHolder;
import io.servicetalk.logging.slf4j.internal.FixedLevelLogger;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufHolder;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;

import java.net.SocketAddress;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.IntSupplier;
import javax.annotation.Nullable;

import static io.netty.buffer.ByteBufUtil.appendPrettyHexDump;
import static io.netty.util.internal.StringUtil.NEWLINE;
import static io.servicetalk.buffer.netty.BufferUtils.toByteBuf;
import static java.util.Objects.requireNonNull;

@Sharable
final class ServiceTalkWireLogger extends ChannelDuplexHandler {
    private final FixedLevelLogger logger;
    private final BooleanSupplier logUserDataSupplier;

    ServiceTalkWireLogger(final FixedLevelLogger logger, final BooleanSupplier logUserDataSupplier) {
        this.logger = requireNonNull(logger);
        this.logUserDataSupplier = requireNonNull(logUserDataSupplier);
    }

    @Override
    public void channelRegistered(ChannelHandlerContext ctx) {
        if (logger.isEnabled()) {
            logger.log(contextToString(ctx) + " REGISTERED");
        }
        ctx.fireChannelRegistered();
    }

    @Override
    public void channelUnregistered(ChannelHandlerContext ctx) {
        if (logger.isEnabled()) {
            logger.log(contextToString(ctx) + " UNREGISTERED");
        }
        ctx.fireChannelUnregistered();
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        if (logger.isEnabled()) {
            logger.log(contextToString(ctx) + " ACTIVE");
        }
        ctx.fireChannelActive();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        if (logger.isEnabled()) {
            logger.log(contextToString(ctx) + " INACTIVE");
        }
        ctx.fireChannelInactive();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (logger.isEnabled()) {
            logger.log(contextToString(ctx) + " EXCEPTION", cause);
        }
        ctx.fireExceptionCaught(cause);
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        if (logger.isEnabled()) {
            logger.log(contextToString(ctx) + " USER_EVENT " + evt);
        }
        ctx.fireUserEventTriggered(evt);
    }

    @Override
    public void bind(ChannelHandlerContext ctx, SocketAddress localAddress, ChannelPromise promise) {
        if (logger.isEnabled()) {
            logger.log(contextToString(ctx) + " BIND " + localAddress);
        }
        ctx.bind(localAddress, promise);
    }

    @Override
    public void connect(
            ChannelHandlerContext ctx,
            SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) {
        if (logger.isEnabled()) {
            logger.log(contextToString(ctx) + " CONNECT " + localAddress + " -> " + remoteAddress);
        }
        ctx.connect(remoteAddress, localAddress, promise);
    }

    @Override
    public void disconnect(ChannelHandlerContext ctx, ChannelPromise promise) {
        if (logger.isEnabled()) {
            logger.log(contextToString(ctx) + " DISCONNECT");
        }
        ctx.disconnect(promise);
    }

    @Override
    public void close(ChannelHandlerContext ctx, ChannelPromise promise) {
        if (logger.isEnabled()) {
            logger.log(contextToString(ctx) + " CLOSE");
        }
        ctx.close(promise);
    }

    @Override
    public void deregister(ChannelHandlerContext ctx, ChannelPromise promise) {
        if (logger.isEnabled()) {
            logger.log(contextToString(ctx) + " DEREGISTER");
        }
        ctx.deregister(promise);
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        if (logger.isEnabled()) {
            logger.log(contextToString(ctx) + " READ_COMPLETE");
        }
        ctx.fireChannelReadComplete();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (logger.isEnabled()) {
            logger.log(format(ctx, "READ", msg));
        }
        ctx.fireChannelRead(msg);
    }

    @Override
    public void read(ChannelHandlerContext ctx) {
        if (logger.isEnabled()) {
            logger.log(contextToString(ctx) + " READ_REQUEST");
        }
        ctx.read();
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
        if (logger.isEnabled()) {
            logger.log(format(ctx, "WRITE", msg));
        }
        ctx.write(msg, promise);
    }

    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) {
        if (logger.isEnabled()) {
            logger.log(contextToString(ctx) + " WRITABILITY_CHANGED");
        }
        ctx.fireChannelWritabilityChanged();
    }

    @Override
    public void flush(ChannelHandlerContext ctx) {
        if (logger.isEnabled()) {
            logger.log(contextToString(ctx) + " FLUSH");
        }
        ctx.flush();
    }

    private String formatByteBuf(ChannelHandlerContext ctx, String eventName, ByteBuf msg) {
        return formatByteBuf(ctx, eventName, null, msg);
    }

    private String formatByteBufNoData(ChannelHandlerContext ctx, String eventName, ByteBuf msg) {
        return contextToString(ctx) + ' ' + eventName + ' ' + msg.readableBytes() + 'B';
    }

    private <T> String formatByteBufHolder(ChannelHandlerContext ctx, String eventName, T msg,
                                           Function<T, ByteBuf> byteBufExtractor) {
        return formatByteBuf(ctx, eventName, msgToString(msg), byteBufExtractor.apply(msg));
    }

    private String formatByteBufHolderNoData(ChannelHandlerContext ctx, String eventName, Object msg,
                                             IntSupplier readableBytes) {
        return contextToString(ctx) + ' ' + eventName + ' ' + msgToString(msg) + ' ' + readableBytes.getAsInt() + 'B';
    }

    private String formatByteBuf(ChannelHandlerContext ctx, String eventName, @Nullable String prefix, ByteBuf msg) {
        String channelString = contextToString(ctx);
        int length = msg.readableBytes();
        int outputLength = channelString.length() + 1 + eventName.length() + 1 +
                (prefix != null ? prefix.length() + 1 : 0) + 10 + 1;
        int rows = length / 16 + (length % 15 == 0 ? 0 : 1) + 4;
        int hexDumpLength = 2 + rows * 80;
        outputLength += hexDumpLength;
        StringBuilder buf = new StringBuilder(outputLength);
        buf.append(channelString).append(' ').append(eventName).append(' ');
        if (prefix != null) {
            buf.append(prefix).append(' ');
        }
        buf.append(length).append('B');
        buf.append(NEWLINE);
        appendPrettyHexDump(buf, msg);
        return buf.toString();
    }

    private String format(ChannelHandlerContext ctx, String eventName, Object msg) {
        final boolean logUserData = logUserDataSupplier.getAsBoolean();
        if (msg instanceof ByteBuf) {
            ByteBuf byteBuf = (ByteBuf) msg;
            return logUserData ? formatByteBuf(ctx, eventName, byteBuf) : formatByteBufNoData(ctx, eventName, byteBuf);
        } else if (msg instanceof ByteBufHolder) {
            ByteBufHolder holder = (ByteBufHolder) msg;
            return logUserData ? formatByteBufHolder(ctx, eventName, holder, ByteBufHolder::content) :
                    formatByteBufHolderNoData(ctx, eventName, holder, holder.content()::readableBytes);
        } else if (msg instanceof Buffer) {
            ByteBuf byteBuf = toByteBuf((Buffer) msg);
            return logUserData ? formatByteBuf(ctx, eventName, byteBuf) : formatByteBufNoData(ctx, eventName, byteBuf);
        } else if (msg instanceof BufferHolder) {
            BufferHolder holder = (BufferHolder) msg;
            return logUserData ? formatByteBufHolder(ctx, eventName, holder, h -> toByteBuf(h.content())) :
                    formatByteBufHolderNoData(ctx, eventName, holder, holder.content()::readableBytes);
        }
        return logUserData ? msg.toString() : msgToString(msg);
    }

    private static String contextToString(ChannelHandlerContext ctx) {
        return ctx.channel().toString();
    }

    private static String msgToString(Object msg) {
        return msg.getClass().toString();
    }
}
