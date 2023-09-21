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
import io.servicetalk.http.api.Http2Exception;
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpHeadersFactory;
import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.http.api.HttpRequestMethod;
import io.servicetalk.http.api.HttpResponseStatus;
import io.servicetalk.transport.api.ConnectionObserver.StreamObserver;
import io.servicetalk.transport.netty.internal.CloseHandler;
import io.servicetalk.transport.netty.internal.DefaultNettyConnection.CancelWriteUserEvent;
import io.servicetalk.transport.netty.internal.DefaultNettyConnection.ContinueUserEvent;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.HttpScheme;
import io.netty.handler.codec.http2.Http2DataFrame;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2HeadersFrame;
import io.netty.handler.codec.http2.Http2StreamChannel;

import javax.annotation.Nullable;

import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_LENGTH;
import static io.netty.handler.codec.http.HttpHeaderNames.HOST;
import static io.netty.handler.codec.http.HttpHeaderValues.ZERO;
import static io.netty.handler.codec.http2.Http2Headers.PseudoHeaderName.STATUS;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_2_0;
import static io.servicetalk.http.api.HttpRequestMethod.CONNECT;
import static io.servicetalk.http.api.HttpResponseMetaDataFactory.newResponseMetaData;
import static io.servicetalk.http.api.HttpResponseStatus.StatusClass.INFORMATIONAL_1XX;
import static io.servicetalk.http.api.HttpResponseStatus.StatusClass.SUCCESSFUL_2XX;
import static io.servicetalk.http.netty.H2ToStH1Utils.h1HeadersToH2Headers;
import static io.servicetalk.http.netty.H2ToStH1Utils.h2HeadersSanitizeForH1;
import static io.servicetalk.http.netty.HeaderUtils.REQ_EXPECT_CONTINUE;
import static io.servicetalk.http.netty.HeaderUtils.responseMayHaveContent;
import static io.servicetalk.http.netty.HeaderUtils.serverMaySendPayloadBodyFor;

final class H2ToStH1ClientDuplexHandler extends AbstractH2DuplexHandler {
    private boolean readHeaders;
    private final HttpScheme scheme;
    @Nullable
    private HttpRequestMethod method;
    private boolean waitForContinuation;

    H2ToStH1ClientDuplexHandler(boolean sslEnabled, BufferAllocator allocator, HttpHeadersFactory headersFactory,
                                CloseHandler closeHandler, StreamObserver observer) {
        super(allocator, headersFactory, closeHandler, observer);
        this.scheme = sslEnabled ? HttpScheme.HTTPS : HttpScheme.HTTP;
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
        if (msg instanceof HttpRequestMetaData) {
            closeHandler.protocolPayloadBeginOutbound(ctx);
            HttpRequestMetaData metaData = (HttpRequestMetaData) msg;
            HttpHeaders h1Headers = metaData.headers();
            waitForContinuation = REQ_EXPECT_CONTINUE.test(metaData);
            CharSequence host = h1Headers.getAndRemove(HOST);
            Http2Headers h2Headers = h1HeadersToH2Headers(h1Headers);
            if (host == null) {
                host = metaData.host();
                if (host != null) {
                    h2Headers.authority(host);
                }
            } else {
                h2Headers.authority(host);
            }
            method = metaData.method();
            h2Headers.method(method.name());
            if (!CONNECT.equals(method)) {
                // The ":scheme" and ":path" pseudo-header fields MUST be omitted for CONNECT.
                // https://tools.ietf.org/html/rfc7540#section-8.3
                h2Headers.scheme(scheme.name());
                h2Headers.path(metaData.requestTarget());
            }
            try {
                writeMetaData(ctx, metaData, h2Headers, true, promise);
            } finally {
                final Http2StreamChannel streamChannel = (Http2StreamChannel) ctx.channel();
                final int streamId = streamChannel.stream().id();
                if (streamId > 0) {
                    observer.streamIdAssigned(streamId);
                }
            }
        } else if (msg instanceof Buffer) {
            writeBuffer(ctx, (Buffer) msg, promise);
        } else if (msg instanceof HttpHeaders) {
            writeTrailers(ctx, msg, promise);
        } else {
            ctx.write(msg, promise);
        }
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) throws Http2Exception {
        if (msg instanceof Http2HeadersFrame) {
            final Http2HeadersFrame headersFrame = (Http2HeadersFrame) msg;
            final int streamId = headersFrame.stream().id();
            final Http2Headers h2Headers = headersFrame.headers();
            final HttpResponseStatus httpStatus;
            if (!readHeaders) {
                final CharSequence status = h2Headers.getAndRemove(STATUS.value());
                if (status == null) {
                    throw noStatus(ctx, streamId);
                }
                httpStatus = HttpResponseStatus.of(status);
                boolean realResponse = !isInterim(httpStatus);
                if (realResponse) {
                    // Don't notify CloseHandler if it's interim 100 (Continue) response
                    closeHandler.protocolPayloadBeginInbound(ctx);
                }
                if (waitForContinuation) {
                    if (httpStatus.code() == HttpResponseStatus.CONTINUE.code() ||
                            httpStatus.statusClass() == SUCCESSFUL_2XX) {
                        waitForContinuation = false;
                        // Write of payload body can continue for either 100 or 2XX response code:
                        ctx.fireUserEventTriggered(ContinueUserEvent.INSTANCE);
                    } else if (realResponse) {
                        waitForContinuation = false;
                        // All other non-interim responses should cancel ongoing write operation when write waits for
                        // continuation.
                        ctx.fireUserEventTriggered(CancelWriteUserEvent.INSTANCE);
                    }
                }
                if (!realResponse) {
                    // We don't expose 1xx "interim responses" [2] to the user, and discard them to make way for the
                    // "real" response.
                    //
                    // for a response only, zero or more HEADERS frames (each followed
                    //        by zero or more CONTINUATION frames) containing the message
                    //        headers of informational (1xx) HTTP responses. [1]
                    // A client MUST be able to parse one or more 1xx responses received
                    //    prior to a final response, even if the client does not expect one.  A
                    //    user agent MAY ignore unexpected 1xx responses. [2]
                    // 1xx responses are terminated by the first empty line after
                    //    the status-line (the empty line signaling the end of the header
                    //    section). [2]
                    // [1] https://tools.ietf.org/html/rfc7540#section-8.1
                    // [2] https://tools.ietf.org/html/rfc7231#section-6.2
                    return;
                }
                readHeaders = true;
            } else {
                httpStatus = null;
            }

            if (headersFrame.isEndStream()) {
                if (httpStatus != null) {
                    fireFullResponse(ctx, h2Headers, httpStatus, streamId);
                } else {
                    ctx.fireChannelRead(h2HeadersToH1HeadersClient(ctx, h2Headers, null, false, streamId));
                }
                closeHandler.protocolPayloadEndInbound(ctx);
            } else if (httpStatus == null) {
                throw noStatus(ctx, streamId);
            } else {
                ctx.fireChannelRead(newResponseMetaData(HTTP_2_0, httpStatus,
                        h2HeadersToH1HeadersClient(ctx, h2Headers, httpStatus, false, streamId)));
            }
        } else if (msg instanceof Http2DataFrame) {
            readDataFrame(ctx, msg);
        } else {
            ctx.fireChannelRead(msg);
        }
    }

    private static Http2Exception noStatus(final ChannelHandlerContext ctx, final int streamId) {
        return protocolError(ctx, streamId, false,
                "Incoming response must have '" + STATUS.value() + "' header");
    }

    private void fireFullResponse(final ChannelHandlerContext ctx, final Http2Headers h2Headers,
                                  final HttpResponseStatus httpStatus, final int streamId) throws Http2Exception {
        assert method != null;
        ctx.fireChannelRead(newResponseMetaData(HTTP_2_0, httpStatus,
                h2HeadersToH1HeadersClient(ctx, h2Headers, httpStatus, true, streamId)));
    }

    private NettyH2HeadersToHttpHeaders h2HeadersToH1HeadersClient(final ChannelHandlerContext ctx,
                                                                   final Http2Headers h2Headers,
                                                                   @Nullable final HttpResponseStatus httpStatus,
                                                                   final boolean fullResponse,
                                                                   final int streamId) throws Http2Exception {
        assert method != null;
        h2HeadersSanitizeForH1(h2Headers);
        if (httpStatus != null) {
            final int statusCode = httpStatus.code();
            if (!h2Headers.contains(CONTENT_LENGTH)) {
                if (serverMaySendPayloadBodyFor(statusCode, method) && fullResponse) {
                    h2Headers.set(CONTENT_LENGTH, ZERO);
                }
            } else if (!responseMayHaveContent(statusCode, method)) {
                throw protocolError(ctx, streamId, fullResponse, "content-length (" + h2Headers.get(CONTENT_LENGTH) +
                        ") header is not expected for status code " + statusCode + " in response to " + method.name() +
                        " request");
            }
        }
        return new NettyH2HeadersToHttpHeaders(h2Headers, headersFactory.validateCookies(),
                headersFactory.validateValues());
    }

    static boolean isInterim(final HttpResponseStatus status) {
        // All known informational status codes are interim and don't need to be propagated to the business logic
        return status.statusClass() == INFORMATIONAL_1XX;
    }
}
