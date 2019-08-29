/*
 * Copyright © 2019 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpHeadersFactory;
import io.servicetalk.http.netty.H2ToStH1Utils.H2StreamRefusedException;
import io.servicetalk.http.netty.H2ToStH1Utils.H2StreamResetException;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http2.DefaultHttp2DataFrame;
import io.netty.handler.codec.http2.DefaultHttp2HeadersFrame;
import io.netty.handler.codec.http2.Http2DataFrame;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2ResetFrame;

import static io.netty.buffer.Unpooled.EMPTY_BUFFER;
import static io.netty.handler.codec.http2.Http2Error.REFUSED_STREAM;
import static io.servicetalk.buffer.netty.BufferUtil.newBufferFrom;
import static io.servicetalk.buffer.netty.BufferUtil.toByteBufNoThrow;
import static io.servicetalk.http.netty.H2ToStH1Utils.h1HeadersToH2Headers;

abstract class AbstractH2DuplexHandler extends ChannelDuplexHandler {
    final HttpHeadersFactory headersFactory;

    AbstractH2DuplexHandler(HttpHeadersFactory headersFactory) {
        this.headersFactory = headersFactory;
    }

    @Override
    public final void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        if (evt instanceof Http2ResetFrame) {
            Http2ResetFrame resetFrame = (Http2ResetFrame) evt;
            if (resetFrame.errorCode() == REFUSED_STREAM.code()) {
                ctx.fireExceptionCaught(new H2StreamRefusedException("RST_STREAM received. stream refused"));
            } else {
                ctx.fireExceptionCaught(new H2StreamResetException("RST_STREAM received with error code: " +
                        resetFrame.errorCode()));
            }
        } else {
            ctx.fireUserEventTriggered(evt);
        }
    }

    final void writeBuffer(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
        ByteBuf byteBuf = toByteBufNoThrow((Buffer) msg);
        if (byteBuf == null) {
            promise.setFailure(new IllegalArgumentException("unsupported Buffer type:" + msg));
            ctx.close();
        } else {
            ctx.write(new DefaultHttp2DataFrame(byteBuf.retain(), false), promise);
        }
    }

    final void writeTrailers(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
        HttpHeaders h1Headers = (HttpHeaders) msg;
        Http2Headers h2Headers = h1HeadersToH2Headers(h1Headers);
        if (h2Headers.isEmpty()) {
            ctx.write(new DefaultHttp2DataFrame(EMPTY_BUFFER, true), promise);
        } else {
            ctx.write(new DefaultHttp2HeadersFrame(h2Headers, true), promise);
        }
    }

    final void readDataFrame(ChannelHandlerContext ctx, Object msg) {
        Http2DataFrame dataFrame = (Http2DataFrame) msg;
        if (dataFrame.content().isReadable()) {
            ctx.fireChannelRead(newBufferFrom(dataFrame.content()));
        } else {
            dataFrame.release();
        }
        if (dataFrame.isEndStream()) {
            ctx.fireChannelRead(headersFactory.newEmptyTrailers());
        }
    }
}
