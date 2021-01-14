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

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.buffer.api.BufferAllocator;
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpHeadersFactory;
import io.servicetalk.transport.api.ConnectionObserver.StreamObserver;
import io.servicetalk.transport.netty.internal.CloseHandler;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http2.DefaultHttp2DataFrame;
import io.netty.handler.codec.http2.DefaultHttp2HeadersFrame;
import io.netty.handler.codec.http2.Http2DataFrame;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2ResetFrame;
import io.netty.util.ReferenceCountUtil;

import javax.annotation.Nullable;

import static io.netty.buffer.Unpooled.EMPTY_BUFFER;
import static io.servicetalk.buffer.netty.BufferUtils.toByteBuf;
import static io.servicetalk.http.netty.H2ToStH1Utils.h1HeadersToH2Headers;
import static io.servicetalk.http.netty.Http2Exception.newStreamResetException;
import static io.servicetalk.http.netty.HttpObjectEncoder.encodeAndRetain;
import static io.servicetalk.transport.netty.internal.ChannelCloseUtils.channelError;

abstract class AbstractH2DuplexHandler extends ChannelDuplexHandler {

    final BufferAllocator allocator;
    final HttpHeadersFactory headersFactory;
    final CloseHandler closeHandler;
    private final StreamObserver observer;

    AbstractH2DuplexHandler(BufferAllocator allocator, HttpHeadersFactory headersFactory, CloseHandler closeHandler,
                            StreamObserver observer) {
        this.allocator = allocator;
        this.headersFactory = headersFactory;
        this.closeHandler = closeHandler;
        this.observer = observer;
    }

    @Override
    public final void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        if (evt instanceof Http2ResetFrame) {
            ctx.fireExceptionCaught(newStreamResetException((Http2ResetFrame) evt));
        } else {
            ctx.fireUserEventTriggered(evt);
        }
    }

    static void writeBuffer(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
        ctx.write(new DefaultHttp2DataFrame(encodeAndRetain((Buffer) msg), false), promise);
    }

    final void writeTrailers(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
        // For H2 we don't need to notify protocolPayloadEndOutboundSuccess(ctx); the codecs takes care of half-closure
        closeHandler.protocolPayloadEndOutbound(ctx);
        HttpHeaders h1Headers = (HttpHeaders) msg;
        Http2Headers h2Headers = h1HeadersToH2Headers(h1Headers);
        if (h2Headers.isEmpty()) {
            ctx.write(new DefaultHttp2DataFrame(EMPTY_BUFFER, true), promise);
        } else {
            ctx.write(new DefaultHttp2HeadersFrame(h2Headers, true), promise);
        }
    }

    final void readDataFrame(ChannelHandlerContext ctx, Object msg) {
        Object toRelease = msg;
        try {
            Http2DataFrame dataFrame = (Http2DataFrame) msg;
            if (dataFrame.content().isReadable()) {
                // Copy to unpooled memory before passing to the user
                Buffer data = allocator.newBuffer(dataFrame.content().readableBytes());
                ByteBuf nettyData = toByteBuf(data);
                nettyData.writeBytes(dataFrame.content());
                toRelease = release(dataFrame);
                ctx.fireChannelRead(data);
            } else {
                toRelease = release(dataFrame);
            }
            if (dataFrame.isEndStream()) {
                ctx.fireChannelRead(headersFactory.newEmptyTrailers());
            }
        } finally {
            if (toRelease != null) {
                ReferenceCountUtil.release(toRelease);
            }
        }
    }

    @Nullable
    private static Http2DataFrame release(Http2DataFrame dataFrame) {
        dataFrame.release();
        return null;
    }

    @Override
    public void channelInactive(final ChannelHandlerContext ctx) {
        final Throwable t = channelError(ctx.channel());
        if (t == null) {
            observer.streamClosed();
        } else {
            observer.streamClosed(t);
        }
        ctx.fireChannelInactive();
    }
}
