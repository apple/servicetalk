/*
 * Copyright © 2019-2021 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.http.api.HttpMetaData;
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

import static io.servicetalk.buffer.netty.BufferUtils.toByteBuf;
import static io.servicetalk.http.netty.H2ToStH1Utils.h1HeadersToH2Headers;
import static io.servicetalk.http.netty.HeaderUtils.emptyMessageBody;
import static io.servicetalk.http.netty.HttpObjectEncoder.encodeAndRetain;
import static io.servicetalk.http.netty.NettyHttp2ExceptionUtils.newStreamResetException;
import static io.servicetalk.transport.netty.internal.ChannelCloseUtils.channelError;

abstract class AbstractH2DuplexHandler extends ChannelDuplexHandler {
    final BufferAllocator allocator;
    final HttpHeadersFactory headersFactory;
    final CloseHandler closeHandler;
    final StreamObserver observer;

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

    final void writeMetaData(ChannelHandlerContext ctx, HttpMetaData metaData, Http2Headers h2Headers,
                             boolean maybeEndStream, ChannelPromise promise) {
        final boolean endStream = maybeEndStream && emptyMessageBody(metaData);
        if (endStream) {
            closeHandler.protocolPayloadEndOutbound(ctx, promise);
        }
        ctx.write(new DefaultHttp2HeadersFrame(h2Headers, endStream), promise);
    }

    static void writeBuffer(ChannelHandlerContext ctx, Buffer buffer, ChannelPromise promise) {
        // Even if the Buffer is empty we still want to write it. This is to preserve order of notification for
        // when writing empty payload in order to know when prior content is written (and then close the channel).
        ctx.write(new DefaultHttp2DataFrame(encodeAndRetain(buffer), false), promise);
    }

    final void writeTrailers(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
        closeHandler.protocolPayloadEndOutbound(ctx, promise);
        HttpHeaders trailers = (HttpHeaders) msg;
        if (trailers.isEmpty()) {
            writeEmptyEndStream(ctx, promise);
        } else {
            Http2Headers h2Headers = h1HeadersToH2Headers(trailers);
            if (h2Headers.isEmpty()) {
                writeEmptyEndStream(ctx, promise);
            } else {
                ctx.write(new DefaultHttp2HeadersFrame(h2Headers, true), promise);
            }
        }
    }

    private static void writeEmptyEndStream(ChannelHandlerContext ctx, ChannelPromise promise) {
        ctx.write(new DefaultHttp2DataFrame(true), promise);
    }

    final void readDataFrame(ChannelHandlerContext ctx, Object msg) {
        Object toRelease = msg;
        try {
            Http2DataFrame dataFrame = (Http2DataFrame) msg;
            final int readableBytes = dataFrame.content().readableBytes();
            if (readableBytes > 0) {
                // Copy to unpooled memory before passing to the user
                Buffer data = allocator.newBuffer(readableBytes);
                ByteBuf nettyData = toByteBuf(data);
                nettyData.writeBytes(dataFrame.content());
                toRelease = release(dataFrame);
                ctx.fireChannelRead(data);
            } else {
                toRelease = release(dataFrame);
            }
            if (dataFrame.isEndStream()) {
                closeHandler.protocolPayloadEndInbound(ctx);
            }
        } finally {
            if (toRelease != null) {
                ReferenceCountUtil.release(toRelease);
            }
        }
    }

    /**
     * Used to releases the frame as soon as we don't need it anymore (before firing an event on the pipeline) to
     * reduce time we hold pooled direct memory.
     */
    @Nullable
    private static Http2DataFrame release(Http2DataFrame dataFrame) {
        dataFrame.release();
        return null;
    }

    @Override
    public final void channelInactive(final ChannelHandlerContext ctx) {
        final Throwable t = channelError(ctx.channel());
        if (t == null) {
            observer.streamClosed();
        } else {
            observer.streamClosed(t);
        }
        ctx.fireChannelInactive();
    }
}
