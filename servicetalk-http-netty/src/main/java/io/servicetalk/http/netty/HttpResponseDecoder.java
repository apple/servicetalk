/*
 * Copyright © 2018-2020 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.transport.netty.internal.DefaultNettyConnection;
import io.servicetalk.utils.internal.IllegalCharacterException;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.DecoderException;
import io.netty.util.ByteProcessor;

import java.util.Queue;

import static io.netty.handler.codec.http.HttpConstants.HT;
import static io.netty.util.internal.StringUtil.EMPTY_STRING;
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
import static io.servicetalk.http.api.HttpResponseStatus.StatusClass.SUCCESSFUL_2XX;
import static java.lang.Math.min;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.util.Objects.requireNonNull;

final class HttpResponseDecoder extends HttpObjectDecoder<HttpResponseMetaData> {

    private static final byte[] FIRST_BYTES = "HTTP/".getBytes(US_ASCII);
    private static final byte DEL = 127;

    private static final ByteProcessor ENSURE_REASON_PHRASE = value -> {
        // Any control character (0x00-0x1F) except HT
        if (((value & 0xE0) == 0 && value != HT) || value == DEL) {
            throw new IllegalCharacterException(value, "HTAB / SP / VCHAR / obs-text");
        }
        return true;
    };

    private final Queue<HttpRequestMethod> methodQueue;

    HttpResponseDecoder(final Queue<HttpRequestMethod> methodQueue, final ByteBufAllocator alloc,
                        final HttpHeadersFactory headersFactory, final int maxStartLineLength, int maxHeaderFieldLength,
                        final boolean allowPrematureClosureBeforePayloadBody, final boolean allowLFWithoutCR,
                        final CloseHandler closeHandler) {
        super(alloc, headersFactory, maxStartLineLength, maxHeaderFieldLength, allowPrematureClosureBeforePayloadBody,
                allowLFWithoutCR, closeHandler);
        this.methodQueue = requireNonNull(methodQueue);
    }

    @Override
    protected boolean isDecodingRequest() {
        return false;
    }

    @Override
    protected void handlePartialInitialLine(final ChannelHandlerContext ctx, final ByteBuf buffer) {
        final int len = min(FIRST_BYTES.length, buffer.readableBytes());
        final int rIdx = buffer.readerIndex();
        for (int i = 0; i < len; ++i) {
            final byte b = buffer.getByte(rIdx + i);
            if (b != FIRST_BYTES[i]) {
                throw new StacklessDecoderException(
                        "Invalid start-line: HTTP response must start with HTTP-version: HTTP/1.x",
                        new IllegalCharacterException(b));
            }
        }
    }

    @Override
    protected HttpResponseMetaData createMessage(final ByteBuf buffer, final int firstStart, final int firstLength,
                                                 final int secondStart, final int secondLength,
                                                 final int thirdStart, final int thirdLength) {
        return newResponseMetaData(nettyBufferToHttpVersion(buffer, firstStart, firstLength),
                HttpResponseStatus.of(nettyBufferToStatusCode(buffer, secondStart, secondLength),
                        reasonPhrase(buffer, thirdStart, thirdLength)),
                headersFactory().newHeaders());
    }

    private static String reasonPhrase(final ByteBuf buffer, final int start, final int length) {
        if (length <= 0) {
            return EMPTY_STRING;
        }
        final String reasonPhrase = buffer.toString(start, length, US_ASCII);
        try {
            buffer.forEachByte(start, length, ENSURE_REASON_PHRASE);
        } catch (IllegalCharacterException cause) {
            throw new StacklessDecoderException("Invalid start-line: HTTP reason-phrase contains an illegal character",
                    cause);
        }
        return reasonPhrase;
    }

    @Override
    protected boolean isContentAlwaysEmpty(final HttpResponseMetaData msg) {
        // Don't poll from the queue for informational responses, because the real response is expected next.
        // https://tools.ietf.org/html/rfc7230#section-3.3.3 (1): any response with a 1xx (Informational) status code
        // cannot contain a message body:
        if (msg.status().statusClass() == INFORMATIONAL_1XX) {
            // One exception: Hixie 76 websocket handshake response
            return !(msg.status().code() == SWITCHING_PROTOCOLS.code() &&
                    !msg.headers().contains(SEC_WEBSOCKET_ACCEPT) &&
                    msg.headers().containsIgnoreCase(UPGRADE, WEBSOCKET));
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

        // https://tools.ietf.org/html/rfc7230#section-3.3.3 (2): Any 2xx (Successful) response to a CONNECT request
        // implies that the connection will become a tunnel immediately after the empty line that concludes the header
        // fields:
        if (CONNECT.equals(method) && msg.status().statusClass() == SUCCESSFUL_2XX) {
            return true;
        }

        // https://tools.ietf.org/html/rfc7230#section-3.3.3 (1): Any response to a HEAD request and any response with
        // a 204 (No Content), or 304 (Not Modified) status code cannot contain a message body:
        return HEAD.equals(method)
                || msg.status().code() == NO_CONTENT.code() || msg.status().code() == NOT_MODIFIED.code();
    }

    @Override
    protected boolean isInterim(final HttpResponseMetaData msg) {
        // All known informational status codes are interim and don't need to be propagated to the business logic
        return msg.status().statusClass() == INFORMATIONAL_1XX;
    }

    @Override
    protected void onMetaDataRead(final ChannelHandlerContext ctx, final HttpResponseMetaData msg) {
        if (msg.status().statusClass() == SUCCESSFUL_2XX) {
            // Any 2XX should also let the request payload body to proceed if "Expect: 100-continue" was used.
            ctx.fireUserEventTriggered(DefaultNettyConnection.ContinueUserEvent.INSTANCE);
        } else if (msg.status().statusClass() != INFORMATIONAL_1XX) {
            // All other non-informational responses should cancel ongoing write operation when write waits for
            // continuation.
            ctx.fireUserEventTriggered(DefaultNettyConnection.CancelWriteUserEvent.INSTANCE);
        }
    }

    @Override
    protected void onDataSeen() {
        // noop
    }

    private static int nettyBufferToStatusCode(final ByteBuf buffer, final int start, final int length) {
        ensureStatusCodeLength(length);
        final int medium = buffer.getUnsignedMedium(start);
        try {
            return toDecimal((medium & 0xff0000) >> 16) * 100 +
                    toDecimal((medium & 0xff00) >> 8) * 10 +
                    toDecimal(medium & 0xff);
        } catch (IllegalCharacterException cause) {
            throw new StacklessDecoderException("Invalid start-line: HTTP status-code must contain only 3 digits",
                    cause);
        }
    }

    private static void ensureStatusCodeLength(final int length) {
        if (length != 3) {
            throw new DecoderException("Invalid start-line: HTTP status-code must contain only 3 digits but found: " +
                    length + " characters");
        }
    }
}
