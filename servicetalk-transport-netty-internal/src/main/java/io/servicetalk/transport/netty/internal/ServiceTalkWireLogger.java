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
import io.servicetalk.logging.api.FixedLevelLogger;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufHolder;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;

import java.net.SocketAddress;
import javax.annotation.Nullable;

import static io.netty.buffer.ByteBufUtil.appendPrettyHexDump;
import static io.netty.util.internal.StringUtil.NEWLINE;
import static io.servicetalk.buffer.netty.BufferUtils.toByteBuf;
import static java.util.Objects.requireNonNull;

final class ServiceTalkWireLogger extends ChannelDuplexHandler {
    private final FixedLevelLogger logger;
    private final boolean logUserData;

    ServiceTalkWireLogger(final FixedLevelLogger logger, final boolean logUserData) {
        this.logger = requireNonNull(logger);
        this.logUserData = logUserData;
    }

    @Override
    public void channelRegistered(ChannelHandlerContext ctx) {
        if (logger.isEnabled()) {
            logger.log("{} REGISTERED", ctx.channel());
        }
        ctx.fireChannelRegistered();
    }

    @Override
    public void channelUnregistered(ChannelHandlerContext ctx) {
        if (logger.isEnabled()) {
            logger.log("{} UNREGISTERED", ctx.channel());
        }
        ctx.fireChannelUnregistered();
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        if (logger.isEnabled()) {
            logger.log("{} ACTIVE", ctx.channel());
        }
        ctx.fireChannelActive();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        if (logger.isEnabled()) {
            logger.log("{} INACTIVE", ctx.channel());
        }
        ctx.fireChannelInactive();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (logger.isEnabled()) {
            logger.log("{} EXCEPTION", ctx.channel(), cause);
        }
        ctx.fireExceptionCaught(cause);
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        if (logger.isEnabled()) {
            logger.log("{} USER_EVENT {}", ctx.channel(), evt);
        }
        ctx.fireUserEventTriggered(evt);
    }

    @Override
    public void bind(ChannelHandlerContext ctx, SocketAddress localAddress, ChannelPromise promise) {
        if (logger.isEnabled()) {
            logger.log("{} USER_EVENT {}", ctx.channel(), localAddress);
        }
        ctx.bind(localAddress, promise);
    }

    @Override
    public void connect(
            ChannelHandlerContext ctx,
            SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) {
        if (logger.isEnabled()) {
            logger.log("{} CONNECT {} -> {}", ctx.channel(), localAddress, remoteAddress);
        }
        ctx.connect(remoteAddress, localAddress, promise);
    }

    @Override
    public void disconnect(ChannelHandlerContext ctx, ChannelPromise promise) {
        if (logger.isEnabled()) {
            logger.log("{} DISCONNECT", ctx.channel());
        }
        ctx.disconnect(promise);
    }

    @Override
    public void close(ChannelHandlerContext ctx, ChannelPromise promise) {
        if (logger.isEnabled()) {
            logger.log("{} CLOSE", ctx.channel());
        }
        ctx.close(promise);
    }

    @Override
    public void deregister(ChannelHandlerContext ctx, ChannelPromise promise) {
        if (logger.isEnabled()) {
            logger.log("{} DEREGISTER", ctx.channel());
        }
        ctx.deregister(promise);
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        if (logger.isEnabled()) {
            logger.log("{} READ_COMPLETE", ctx.channel());
        }
        ctx.fireChannelReadComplete();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (logger.isEnabled()) {
            logger.log("{} READ {}", ctx.channel(), toString(msg));
        }
        ctx.fireChannelRead(msg);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
        if (logger.isEnabled()) {
            logger.log("{} WRITE {}", ctx.channel(), toString(msg));
        }
        ctx.write(msg, promise);
    }

    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) {
        if (logger.isEnabled()) {
            logger.log("{} WRITABILITY_CHANGED", ctx.channel());
        }
        ctx.fireChannelWritabilityChanged();
    }

    @Override
    public void flush(ChannelHandlerContext ctx) {
        if (logger.isEnabled()) {
            logger.log("{} FLUSH", ctx.channel());
        }
        ctx.flush();
    }

    private String formatByteBuf(ByteBuf msg) {
        return msg.readableBytes() == 0 ? "0B" : formatNonZeroByteBuf(null, msg);
    }

    private String formatByteBufNoData(ByteBuf msg) {
        return String.valueOf(msg.readableBytes()) + 'B';
    }

    private String formatByteBufHolder(ByteBufHolder msg) {
        ByteBuf content = msg.content();
        int length = content.readableBytes();
        if (length == 0) {
            return msg.getClass() + " 0B";
        } else {
            return formatNonZeroByteBuf(msg.getClass().toString(), content);
        }
    }

    private String formatBufferHolder(BufferHolder msg) {
        Buffer content = msg.content();
        int length = content.readableBytes();
        if (length == 0) {
            return msg.getClass() + " 0B";
        } else {
            return formatNonZeroByteBuf(msg.getClass().toString(), toByteBuf(content));
        }
    }

    private String formatByteBufHolderNoData(ByteBufHolder msg) {
        return msg.getClass().toString() + ' ' + msg.content().readableBytes() + 'B';
    }

    private String formatBufferHolderNoData(BufferHolder msg) {
        return msg.getClass().toString() + ' ' + msg.content().readableBytes() + 'B';
    }

    private String formatNonZeroByteBuf(@Nullable String prefix, ByteBuf msg) {
        int length = msg.readableBytes();
        int outputLength = 10 + 1;
        int rows = length / 16 + (length % 15 == 0 ? 0 : 1) + 4;
        int hexDumpLength = 2 + rows * 80;
        outputLength += hexDumpLength;
        StringBuilder buf = new StringBuilder(outputLength);
        if (prefix != null) {
            buf.append(prefix).append(' ');
        }
        buf.append(length).append('B');
        buf.append(NEWLINE);
        appendPrettyHexDump(buf, msg);
        return buf.toString();
    }

    private String toString(Object msg) {
        if (msg instanceof ByteBuf) {
            ByteBuf byteBuf = (ByteBuf) msg;
            return logUserData ? formatByteBuf(byteBuf) : formatByteBufNoData(byteBuf);
        } else if (msg instanceof ByteBufHolder) {
            ByteBufHolder holder = (ByteBufHolder) msg;
            return logUserData ? formatByteBufHolder(holder) : formatByteBufHolderNoData(holder);
        } else if (msg instanceof Buffer) {
            ByteBuf byteBuf = toByteBuf((Buffer) msg);
            return logUserData ? formatByteBuf(byteBuf) : formatByteBufNoData(byteBuf);
        } else if (msg instanceof BufferHolder) {
            BufferHolder holder = (BufferHolder) msg;
            return logUserData ? formatBufferHolder(holder) : formatBufferHolderNoData(holder);
        }
        return logUserData ? msg.toString() : msg.getClass().getName();
    }
}
