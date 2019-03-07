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
package io.servicetalk.http.netty;

import io.servicetalk.http.api.HttpHeadersFactory;
import io.servicetalk.http.api.HttpRequestMethod;
import io.servicetalk.http.api.HttpResponseMetaData;
import io.servicetalk.http.api.HttpResponseStatus;
import io.servicetalk.transport.netty.internal.CloseHandler;

import io.netty.buffer.ByteBuf;

import java.util.Queue;

import static io.servicetalk.buffer.netty.BufferUtil.newBufferFrom;
import static io.servicetalk.http.api.HttpHeaderNames.SEC_WEBSOCKET_ACCEPT;
import static io.servicetalk.http.api.HttpHeaderNames.UPGRADE;
import static io.servicetalk.http.api.HttpHeaderValues.WEBSOCKET;
import static io.servicetalk.http.api.HttpRequestMethod.CONNECT;
import static io.servicetalk.http.api.HttpRequestMethod.HEAD;
import static io.servicetalk.http.api.HttpResponseMetaDataFactory.newResponseMetaData;
import static io.servicetalk.http.api.HttpResponseStatus.NOT_MODIFIED;
import static io.servicetalk.http.api.HttpResponseStatus.NO_CONTENT;
import static io.servicetalk.http.api.HttpResponseStatus.SWITCHING_PROTOCOLS;
import static io.servicetalk.http.api.HttpResponseStatus.StatusClass.INFORMATIONAL_1XX;
import static io.servicetalk.http.api.HttpResponseStatus.getResponseStatus;
import static io.servicetalk.transport.netty.internal.CloseHandler.UNSUPPORTED_PROTOCOL_CLOSE_HANDLER;
import static java.lang.Integer.parseInt;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.util.Objects.requireNonNull;

final class HttpResponseDecoder extends HttpObjectDecoder<HttpResponseMetaData> {
    private final Queue<HttpRequestMethod> methodQueue;

    HttpResponseDecoder(Queue<HttpRequestMethod> methodQueue, HttpHeadersFactory headersFactory,
                        int maxInitialLineLength, int maxHeaderSize) {
        this(methodQueue, headersFactory, maxInitialLineLength, maxHeaderSize, UNSUPPORTED_PROTOCOL_CLOSE_HANDLER);
    }

    HttpResponseDecoder(Queue<HttpRequestMethod> methodQueue, HttpHeadersFactory headersFactory,
                        int maxInitialLineLength, int maxHeaderSize, final CloseHandler closeHandler) {
        super(headersFactory, maxInitialLineLength, maxHeaderSize, closeHandler);
        this.methodQueue = requireNonNull(methodQueue);
    }

    @Override
    protected boolean isDecodingRequest() {
        return false;
    }

    @Override
    protected HttpResponseMetaData createMessage(ByteBuf first, ByteBuf second, ByteBuf third) {
        return newResponseMetaData(nettyBufferToHttpVersion(first),
                nettyBufferToHttpStatus(second, third),
                headersFactory().newHeaders());
    }

    @Override
    protected boolean isContentAlwaysEmpty(final HttpResponseMetaData msg) {
        // Don't poll from the queue for informational responses, because the real response is expected next.
        if (msg.status().statusClass() == INFORMATIONAL_1XX) {
            // One exception: Hixie 76 websocket handshake response
            return !(msg.status().code() == SWITCHING_PROTOCOLS.code() &&
                    !msg.headers().contains(SEC_WEBSOCKET_ACCEPT) &&
                     msg.headers().contains(UPGRADE, WEBSOCKET, true));
        }

        // This method has side effects on the methodQueue for the following reasons:
        // - createMessage will not necessary fire a message up the pipeline.
        // - the trigger points on the queue are currently symmetric for the request/response decoder and
        // request/response encoder. We may use header information on the response decoder side, and the queue
        // interaction is conditional (1xx responses don't touch the queue).
        // - unit tests exist which verify these side effects occur, so if behavior of the internal classes changes the
        // unit test should catch it.
        // - this is the rough equivalent of what is done in Netty in terms of sequencing. Instead of trying to
        // iterate a decoded list it makes some assumptions about the base class ordering of events.
        HttpRequestMethod method = methodQueue.poll();

        // We are either switching protocols, and we will no longer process any more HTTP/1.x responses, or the protocol
        // rules prevent a content body. Also 204 and 304 are always empty.
        // https://tools.ietf.org/html/rfc7230#section-3.3.3
        // Note that we are using ServiceTalk constants for HttpRequestMethod here, and assume the decoders will
        // also use ServiceTalk constants which allows us to use reference check here:
        return method == HEAD || method == CONNECT
                || msg.status().code() == NO_CONTENT.code() || msg.status().code() == NOT_MODIFIED.code();
    }

    private static HttpResponseStatus nettyBufferToHttpStatus(ByteBuf statusCode, ByteBuf reasonPhrase) {
        // Most status codes are 3 bytes long, and so it is worth a special case to optimize the conversion from bytes
        // to integer and avoid String conversion and generic parseInt.
        if (statusCode.readableBytes() == 3) {
            final int medium = statusCode.getUnsignedMedium(statusCode.readerIndex());
            return getResponseStatus(
                    toDecimal((medium & 0xff0000) >> 16) * 100 +
                            toDecimal((medium & 0xff00) >> 8) * 10 +
                            toDecimal(medium & 0xff),
                    newBufferFrom(reasonPhrase));
        } else {
            return getResponseStatus(parseInt(statusCode.toString(US_ASCII)), newBufferFrom(reasonPhrase));
        }
    }

    private static int toDecimal(final int c) {
        if (c < 48 || c > 57) {
            throw new IllegalArgumentException("invalid status code");
        }
        return c - 48;
    }
}
