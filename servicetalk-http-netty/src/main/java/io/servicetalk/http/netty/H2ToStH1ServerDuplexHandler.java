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
import io.servicetalk.buffer.api.BufferAllocator;
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpHeadersFactory;
import io.servicetalk.http.api.HttpRequestMethod;
import io.servicetalk.http.api.HttpResponseMetaData;
import io.servicetalk.http.api.StreamingHttpRequest;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpScheme;
import io.netty.handler.codec.http2.DefaultHttp2DataFrame;
import io.netty.handler.codec.http2.DefaultHttp2HeadersFrame;
import io.netty.handler.codec.http2.Http2DataFrame;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2HeadersFrame;

import javax.annotation.Nullable;

import static io.netty.buffer.Unpooled.EMPTY_BUFFER;
import static io.netty.handler.codec.http2.Http2Headers.PseudoHeaderName.AUTHORITY;
import static io.netty.handler.codec.http2.Http2Headers.PseudoHeaderName.METHOD;
import static io.netty.handler.codec.http2.Http2Headers.PseudoHeaderName.PATH;
import static io.servicetalk.buffer.netty.BufferUtil.newBufferFrom;
import static io.servicetalk.buffer.netty.BufferUtil.toByteBufNoThrow;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_LENGTH;
import static io.servicetalk.http.api.HttpHeaderNames.HOST;
import static io.servicetalk.http.api.HttpHeaderValues.ZERO;
import static io.servicetalk.http.api.HttpRequestMethod.Properties.NONE;
import static io.servicetalk.http.api.StreamingHttpRequests.newRequest;
import static io.servicetalk.http.netty.H2ToStH1Utils.HTTP_2_0;
import static io.servicetalk.http.netty.H2ToStH1Utils.h1HeadersToH2Headers;
import static io.servicetalk.http.netty.H2ToStH1Utils.h2HeadersSanitizeForH1;
import static io.servicetalk.http.netty.HeaderUtils.canAddRequestTransferEncodingProtocol;
import static io.servicetalk.http.netty.HeaderUtils.shouldAddZeroContentLength;

final class H2ToStH1ServerDuplexHandler extends ChannelDuplexHandler {
    private boolean readHeaders;
    private final HttpScheme scheme;
    private final HttpHeadersFactory headersFactory;
    private final BufferAllocator allocator;

    H2ToStH1ServerDuplexHandler(boolean sslEnabled, BufferAllocator allocator, HttpHeadersFactory headersFactory) {
        this.scheme = sslEnabled ? HttpScheme.HTTPS : HttpScheme.HTTP;
        this.allocator = allocator;
        this.headersFactory = headersFactory;
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
        if (msg instanceof HttpResponseMetaData) {
            HttpResponseMetaData metaData = (HttpResponseMetaData) msg;
            HttpHeaders h1Headers = metaData.headers();
            Http2Headers h2Headers = h1HeadersToH2Headers(h1Headers);
            h2Headers.status(metaData.status().toString());
            h2Headers.scheme(scheme.name());
            ctx.write(new DefaultHttp2HeadersFrame(h2Headers, false), promise);
        } else if (msg instanceof Buffer) {
            ByteBuf byteBuf = toByteBufNoThrow((Buffer) msg);
            if (byteBuf == null) {
                promise.setFailure(new IllegalArgumentException("unsupported Buffer type:" + msg));
                ctx.close();
            } else {
                ctx.write(new DefaultHttp2DataFrame(byteBuf.retain(), false), promise);
            }
        } else if (msg instanceof HttpHeaders) {
            HttpHeaders h1Headers = (HttpHeaders) msg;
            Http2Headers h2Headers = h1HeadersToH2Headers(h1Headers);
            if (h2Headers.isEmpty()) {
                ctx.write(new DefaultHttp2DataFrame(EMPTY_BUFFER, true), promise);
            } else {
                ctx.write(new DefaultHttp2HeadersFrame(h2Headers, true), promise);
            }
        } else {
            ctx.write(msg, promise);
        }
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof Http2HeadersFrame) {
            Http2HeadersFrame headersFrame = (Http2HeadersFrame) msg;
            Http2Headers h2Headers = headersFrame.headers();
            final HttpRequestMethod httpMethod;
            final String path;
            if (!readHeaders) {
                CharSequence method = h2Headers.getAndRemove(METHOD.value());
                CharSequence pathSequence = h2Headers.getAndRemove(PATH.value());
                if (pathSequence == null || method == null) {
                    throw new IllegalArgumentException("a request must have " + METHOD + " and " +
                            PATH + " headers");
                }
                path = pathSequence.toString();
                httpMethod = sequenceToHttpRequestMethod(method);
                readHeaders = true;
            } else {
                httpMethod = null;
                path = null;
            }

            if (headersFrame.isEndStream()) {
                if (httpMethod != null) {
                    fireFullRequest(ctx, h2Headers, httpMethod, path);
                } else {
                    ctx.fireChannelRead(h2TrailersToH1TrailersServer(h2Headers));
                }
            } else if (httpMethod == null) {
                throw new IllegalArgumentException("a request must have " + METHOD + " and " +
                        PATH + " headers");
            } else {
                StreamingHttpRequest request = newRequest(httpMethod, path, HTTP_2_0,
                        h2HeadersToH1HeadersServer(h2Headers, httpMethod), headersFactory.newEmptyTrailers(),
                        allocator);
                ctx.fireChannelRead(request);
            }
        } else if (msg instanceof Http2DataFrame) {
            Http2DataFrame dataFrame = (Http2DataFrame) msg;
            if (dataFrame.content().isReadable()) {
                ctx.fireChannelRead(newBufferFrom(dataFrame.content()));
            } else {
                dataFrame.release();
            }
            if (dataFrame.isEndStream()) {
                ctx.fireChannelRead(headersFactory.newEmptyTrailers());
            }
        } else {
            ctx.fireChannelRead(msg);
        }
    }

    private void fireFullRequest(ChannelHandlerContext ctx, final Http2Headers h2Headers,
                                 HttpRequestMethod httpMethod, String path) {
        if (shouldAddZeroContentLength(httpMethod)) {
            h2Headers.set(CONTENT_LENGTH, ZERO);
        }
        StreamingHttpRequest request = newRequest(httpMethod, path, HTTP_2_0,
                h2HeadersToH1HeadersServer(h2Headers, httpMethod), headersFactory.newEmptyTrailers(), allocator);
        ctx.fireChannelRead(request);
        ctx.fireChannelRead(headersFactory.newEmptyTrailers());
    }

    private NettyH2HeadersToHttpHeaders h2HeadersToH1HeadersServer(Http2Headers h2Headers,
                                                                   @Nullable HttpRequestMethod httpMethod) {
        CharSequence value = h2Headers.getAndRemove(AUTHORITY.value());
        if (value != null) {
            h2Headers.set(HOST, value);
        }
        h2Headers.remove(Http2Headers.PseudoHeaderName.SCHEME.value());
        h2HeadersSanitizeForH1(h2Headers);
        if (httpMethod != null && !h2Headers.contains(HttpHeaderNames.CONTENT_LENGTH) &&
                canAddRequestTransferEncodingProtocol(httpMethod)) {
            h2Headers.add(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);
        }
        return new NettyH2HeadersToHttpHeaders(h2Headers, headersFactory.validateCookies());
    }

    private NettyH2HeadersToHttpHeaders h2TrailersToH1TrailersServer(Http2Headers h2Headers) {
        return new NettyH2HeadersToHttpHeaders(h2Headers, headersFactory.validateCookies());
    }

    private static HttpRequestMethod sequenceToHttpRequestMethod(CharSequence sequence) {
        String strMethod = sequence.toString();
        HttpRequestMethod reqMethod = HttpRequestMethod.of(strMethod);
        return reqMethod != null ? reqMethod : HttpRequestMethod.of(strMethod, NONE);
    }
}
