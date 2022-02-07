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
import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.http.api.HttpRequestMethod;
import io.servicetalk.http.api.HttpResponseStatus;
import io.servicetalk.http.netty.HttpResponseEncoder.OnResponse;
import io.servicetalk.transport.netty.internal.CloseHandler;
import io.servicetalk.utils.internal.IllegalCharacterException;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.DecoderException;
import io.netty.util.ByteProcessor;

import java.util.Queue;

import static io.servicetalk.http.api.HttpRequestMetaDataFactory.newRequestMetaData;
import static io.servicetalk.http.api.HttpRequestMethod.Properties.NONE;
import static io.servicetalk.http.api.HttpResponseStatus.CONTINUE;
import static io.servicetalk.http.api.HttpResponseStatus.StatusClass.INFORMATIONAL_1XX;
import static io.servicetalk.http.api.HttpResponseStatus.StatusClass.SUCCESSFUL_2XX;
import static io.servicetalk.http.netty.HeaderUtils.REQ_EXPECT_CONTINUE;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.util.Objects.requireNonNull;

final class HttpRequestDecoder extends HttpObjectDecoder<HttpRequestMetaData> implements OnResponse {
    private static final ByteProcessor FIND_WS_AFTER_METHOD_NAME = value -> {
        if (isWS(value)) {
            return false;
        }
        ensureUpperCase(value);
        return true;
    };
    private static final ByteProcessor ENSURE_UPPER_CASE = value -> {
        ensureUpperCase(value);
        return true;
    };

    private final Queue<HttpRequestMethod> methodQueue;
    private final CloseHandler closeHandler;
    private boolean expectContinue;
    private boolean seenPayloadBody;

    HttpRequestDecoder(final Queue<HttpRequestMethod> methodQueue, final ByteBufAllocator alloc,
                       final HttpHeadersFactory headersFactory, final int maxStartLineLength,
                       final int maxHeaderFieldLength, final boolean allowPrematureClosureBeforePayloadBody,
                       final boolean allowLFWithoutCR, final CloseHandler closeHandler) {
        super(alloc, headersFactory, maxStartLineLength, maxHeaderFieldLength, allowPrematureClosureBeforePayloadBody,
                allowLFWithoutCR, closeHandler);
        this.methodQueue = requireNonNull(methodQueue);
        this.closeHandler = closeHandler;
    }

    @Override
    protected boolean isDecodingRequest() {
        return true;
    }

    @Override
    protected void handlePartialInitialLine(final ChannelHandlerContext ctx, final ByteBuf buffer) {
        try {
            buffer.forEachByte(FIND_WS_AFTER_METHOD_NAME);
        } catch (IllegalCharacterException cause) {
            throw newInvalidMethodException(cause);
        }
    }

    private static DecoderException newInvalidMethodException(final IllegalCharacterException cause) {
        return new StacklessDecoderException(
                "Invalid start-line: HTTP request method must contain only upper case letters", cause);
    }

    private static void ensureUpperCase(final byte value) {
        // As per the RFC, request method is case-sensitive, and all valid methods are uppercase.
        // https://tools.ietf.org/html/rfc7231#section-4.1
        if (value < 'A' || value > 'Z') {
            throw new IllegalCharacterException(value, "A-Z (0x41-0x5a)");
        }
    }

    @Override
    protected HttpRequestMetaData createMessage(final ByteBuf buffer,
                                                final int firstStart, final int firstLength,
                                                final int secondStart, final int secondLength,
                                                final int thirdStart, final int thirdLength) {
        return newRequestMetaData(nettyBufferToHttpVersion(buffer, thirdStart, thirdLength),
                decodeHttpMethod(buffer, firstStart, firstLength),
                buffer.toString(secondStart, secondLength, US_ASCII),
                headersFactory().newHeaders());
    }

    private static HttpRequestMethod decodeHttpMethod(final ByteBuf buffer, final int start, final int length) {
        final String methodName = buffer.toString(start, length, US_ASCII);
        final HttpRequestMethod method = HttpRequestMethod.of(methodName);
        if (method != null) {
            return method;
        }
        try {
            buffer.forEachByte(start, length, ENSURE_UPPER_CASE);
        } catch (IllegalCharacterException cause) {
            throw newInvalidMethodException(cause);
        }
        return HttpRequestMethod.of(methodName, NONE);
    }

    @Override
    protected boolean isContentAlwaysEmpty(HttpRequestMetaData msg) {
        // This method has side effects on the methodQueue for the following reasons:
        // - createMessage will not necessary fire a message up the pipeline.
        // - the trigger points on the queue are currently symmetric for the request/response decoder and
        // request/response encoder. We may use header information on the response decoder side, and the queue
        // interaction is conditional (1xx responses don't touch the queue).
        // - unit tests exist which verify these side effects occur, so if behavior of the internal classes changes the
        // unit test should catch it.
        // - this is the rough equivalent of what is done in Netty in terms of sequencing. Instead of trying to
        // iterate a decoded list it makes some assumptions about the base class ordering of events.
        methodQueue.add(msg.method());
        return false;
    }

    @Override
    protected boolean isInterim(final HttpRequestMetaData msg) {
        return false;   // Only response decoder may encounter interim messages
    }

    @Override
    protected void onMetaDataRead(final ChannelHandlerContext ctx, final HttpRequestMetaData msg) {
        expectContinue = REQ_EXPECT_CONTINUE.test(msg);
    }

    @Override
    protected void onDataSeen() {
        seenPayloadBody = true;
    }

    @Override
    protected void resetNow() {
        super.resetNow();
        onStateReset();
    }

    private void onStateReset() {
        expectContinue = false;
        seenPayloadBody = false;
    }

    @Override
    public void onResponse(final ChannelHandlerContext ctx, final HttpResponseStatus status) {
        if (expectContinue && !seenPayloadBody) {
            if (status == CONTINUE) {
                // If server responds with 100 (Continue), no need to trigger resetNow() anymore because server expects
                // to receive a payload body.
                onStateReset();
            } else if (status.statusClass() != SUCCESSFUL_2XX && status.statusClass() != INFORMATIONAL_1XX) {
                // If a request expects continuation, but server responds with an error or redirect before it sees a
                // request payload body, notify CloseHandler and reset the state to prepare for receiving a new request
                // on the same connection.
                closeHandler.protocolPayloadEndInbound(ctx);
                resetNow();
            }
        }
    }
}
