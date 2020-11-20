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
import static io.servicetalk.transport.netty.internal.CloseHandler.UNSUPPORTED_PROTOCOL_CLOSE_HANDLER;
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
                        final HttpHeadersFactory headersFactory, int maxStartLineLength, int maxHeaderFieldLength) {
        this(methodQueue, alloc, headersFactory, maxStartLineLength, maxHeaderFieldLength,
                false, UNSUPPORTED_PROTOCOL_CLOSE_HANDLER);
    }

    HttpResponseDecoder(final Queue<HttpRequestMethod> methodQueue, final ByteBufAllocator alloc,
                        final HttpHeadersFactory headersFactory, final int maxStartLineLength, int maxHeaderFieldLength,
                        final boolean allowPrematureClosureBeforePayloadBody, final CloseHandler closeHandler) {
        super(alloc, headersFactory, maxStartLineLength, maxHeaderFieldLength, allowPrematureClosureBeforePayloadBody,
                closeHandler);
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
                throw new DecoderException("Invalid start-line: HTTP response must start with HTTP-version, found: " +
                        buffer.toString(US_ASCII) + ", expected: HTTP/", new IllegalCharacterException(b));
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
            throw new DecoderException("Invalid start-line: HTTP reason-phrase contains an illegal character: " +
                    reasonPhrase, cause);
        }
        return reasonPhrase;
    }

    @Override
    protected boolean isContentAlwaysEmpty(final HttpResponseMetaData msg) {
        // Don't poll from the queue for informational responses, because the real response is expected next.
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

        // We are either switching protocols, and we will no longer process any more HTTP/1.x responses, or the protocol
        // rules prevent a content body. Also 204 and 304 are always empty.
        // https://tools.ietf.org/html/rfc7230#section-3.3.3
        // Note that we are using ServiceTalk constants for HttpRequestMethod here, and assume the decoders will
        // also use ServiceTalk constants which allows us to use reference check here:
        return method == HEAD || method == CONNECT
                || msg.status().code() == NO_CONTENT.code() || msg.status().code() == NOT_MODIFIED.code();
    }

    private static int nettyBufferToStatusCode(final ByteBuf buffer, final int start, final int length) {
        if (length != 3) {
            throw new DecoderException("Invalid start-line: HTTP status-code must contain only 3 digits, found: " +
                    buffer.toString(start, length, US_ASCII));
        }

        final int medium = buffer.getUnsignedMedium(start);
        try {
            return toDecimal((medium & 0xff0000) >> 16) * 100 +
                    toDecimal((medium & 0xff00) >> 8) * 10 +
                    toDecimal(medium & 0xff);
        } catch (IllegalCharacterException cause) {
            throw new DecoderException("Invalid start-line: HTTP status-code must contain only 3 digits, found: " +
                    buffer.toString(start, length, US_ASCII), cause);
        }
    }
}
