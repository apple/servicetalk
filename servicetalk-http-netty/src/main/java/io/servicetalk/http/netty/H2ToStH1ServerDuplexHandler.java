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
import io.servicetalk.http.api.HttpRequestMethod;
import io.servicetalk.http.api.HttpResponseMetaData;
import io.servicetalk.transport.api.ConnectionObserver.StreamObserver;
import io.servicetalk.transport.netty.internal.CloseHandler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http2.DefaultHttp2HeadersFrame;
import io.netty.handler.codec.http2.Http2DataFrame;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2HeadersFrame;

import javax.annotation.Nullable;

import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_LENGTH;
import static io.netty.handler.codec.http.HttpHeaderNames.HOST;
import static io.netty.handler.codec.http.HttpHeaderNames.TRANSFER_ENCODING;
import static io.netty.handler.codec.http.HttpHeaderValues.CHUNKED;
import static io.netty.handler.codec.http.HttpHeaderValues.ZERO;
import static io.netty.handler.codec.http2.Http2Headers.PseudoHeaderName.AUTHORITY;
import static io.netty.handler.codec.http2.Http2Headers.PseudoHeaderName.METHOD;
import static io.netty.handler.codec.http2.Http2Headers.PseudoHeaderName.PATH;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_2_0;
import static io.servicetalk.http.api.HttpRequestMetaDataFactory.newRequestMetaData;
import static io.servicetalk.http.api.HttpRequestMethod.Properties.NONE;
import static io.servicetalk.http.netty.H2ToStH1Utils.h1HeadersToH2Headers;
import static io.servicetalk.http.netty.H2ToStH1Utils.h2HeadersSanitizeForH1;
import static io.servicetalk.http.netty.HeaderUtils.clientMaySendPayloadBodyFor;

final class H2ToStH1ServerDuplexHandler extends AbstractH2DuplexHandler {
    private boolean readHeaders;

    H2ToStH1ServerDuplexHandler(BufferAllocator allocator, HttpHeadersFactory headersFactory,
                                CloseHandler closeHandler, StreamObserver observer) {
        super(allocator, headersFactory, closeHandler, observer);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
        if (msg instanceof HttpResponseMetaData) {
            closeHandler.protocolPayloadBeginOutbound(ctx);
            HttpResponseMetaData metaData = (HttpResponseMetaData) msg;
            Http2Headers h2Headers = h1HeadersToH2Headers(metaData.headers());
            h2Headers.status(metaData.status().codeAsCharSequence());
            ctx.write(new DefaultHttp2HeadersFrame(h2Headers, false), promise);
        } else if (msg instanceof Buffer) {
            writeBuffer(ctx, msg, promise);
        } else if (msg instanceof HttpHeaders) {
            writeTrailers(ctx, msg, promise);
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
                closeHandler.protocolPayloadBeginInbound(ctx);
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
                closeHandler.protocolPayloadEndInbound(ctx);
            } else if (httpMethod == null) {
                throw new IllegalArgumentException("a request must have " + METHOD + " and " +
                        PATH + " headers");
            } else {
                ctx.fireChannelRead(newRequestMetaData(HTTP_2_0, httpMethod, path,
                        h2HeadersToH1HeadersServer(h2Headers, httpMethod, false)));
            }
        } else if (msg instanceof Http2DataFrame) {
            readDataFrame(ctx, msg);
        } else {
            ctx.fireChannelRead(msg);
        }
    }

    private void fireFullRequest(ChannelHandlerContext ctx, final Http2Headers h2Headers,
                                 HttpRequestMethod httpMethod, String path) {
        ctx.fireChannelRead(newRequestMetaData(HTTP_2_0, httpMethod, path,
                h2HeadersToH1HeadersServer(h2Headers, httpMethod, true)));
        ctx.fireChannelRead(headersFactory.newEmptyTrailers());
    }

    private NettyH2HeadersToHttpHeaders h2HeadersToH1HeadersServer(Http2Headers h2Headers,
                                                                   @Nullable HttpRequestMethod httpMethod,
                                                                   boolean fullRequest) {
        CharSequence value = h2Headers.getAndRemove(AUTHORITY.value());
        if (value != null) {
            h2Headers.set(HOST, value);
        }
        h2Headers.remove(Http2Headers.PseudoHeaderName.SCHEME.value());
        h2HeadersSanitizeForH1(h2Headers);
        if (httpMethod != null) {
            final Long contentLength = h2Headers.getLong(CONTENT_LENGTH);
            if (clientMaySendPayloadBodyFor(httpMethod)) {
                if (contentLength == null) {
                    if (fullRequest) {
                        h2Headers.set(CONTENT_LENGTH, ZERO);
                    } else {
                        h2Headers.add(TRANSFER_ENCODING, CHUNKED);
                    }
                }
            } else if (contentLength >= 0L) {
                throw new IllegalArgumentException("content-length (" + contentLength +
                        ") header is not expected for " + httpMethod.name() + " request");
            }
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
