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
import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.http.api.HttpRequestMethod;
import io.servicetalk.transport.netty.internal.CloseHandler;

import io.netty.buffer.ByteBuf;

import java.util.Queue;

import static io.servicetalk.http.api.HttpRequestMetaDataFactory.newRequestMetaData;
import static io.servicetalk.http.api.HttpRequestMethod.Properties.NONE;
import static io.servicetalk.transport.netty.internal.CloseHandler.UNSUPPORTED_PROTOCOL_CLOSE_HANDLER;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.util.Objects.requireNonNull;

final class HttpRequestDecoder extends HttpObjectDecoder<HttpRequestMetaData> {

    private final Queue<HttpRequestMethod> methodQueue;

    HttpRequestDecoder(Queue<HttpRequestMethod> methodQueue,
                       HttpHeadersFactory headersFactory, int maxInitialLineLength, int maxHeaderSize) {
        this(methodQueue, headersFactory, maxInitialLineLength, maxHeaderSize, UNSUPPORTED_PROTOCOL_CLOSE_HANDLER);
    }

    HttpRequestDecoder(Queue<HttpRequestMethod> methodQueue, HttpHeadersFactory headersFactory,
                       int maxInitialLineLength, int maxHeaderSize, CloseHandler closeHandler) {
        super(headersFactory, maxInitialLineLength, maxHeaderSize, closeHandler);
        this.methodQueue = requireNonNull(methodQueue);
    }

    @Override
    protected boolean isDecodingRequest() {
        return true;
    }

    @Override
    protected HttpRequestMetaData createMessage(final ByteBuf buffer,
                                                final int firstStart, final int firstEnd,
                                                final int secondStart, final int secondEnd,
                                                final int thirdStart, final int thirdEnd) {
        if (thirdEnd < 0) {
            splitInitialLineError();
        }

        return newRequestMetaData(nettyBufferToHttpVersion(buffer, thirdStart, thirdEnd),
                parseHttpMethod(buffer.toString(firstStart, firstEnd - firstStart, US_ASCII)),
                buffer.toString(secondStart, secondEnd - secondStart, US_ASCII),
                headersFactory().newHeaders());
    }

    private static HttpRequestMethod parseHttpMethod(final String methodName) {
        final HttpRequestMethod method = HttpRequestMethod.of(methodName);
        return method != null ? method : HttpRequestMethod.of(methodName, NONE);
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
}
