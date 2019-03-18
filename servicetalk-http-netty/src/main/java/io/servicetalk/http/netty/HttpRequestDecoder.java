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

import java.util.HashMap;
import java.util.Map;
import java.util.Queue;

import static io.netty.buffer.Unpooled.copiedBuffer;
import static io.servicetalk.buffer.netty.BufferUtil.newBufferFrom;
import static io.servicetalk.http.api.HttpRequestMetaDataFactory.newRequestMetaData;
import static io.servicetalk.http.api.HttpRequestMethod.CONNECT;
import static io.servicetalk.http.api.HttpRequestMethod.DELETE;
import static io.servicetalk.http.api.HttpRequestMethod.GET;
import static io.servicetalk.http.api.HttpRequestMethod.HEAD;
import static io.servicetalk.http.api.HttpRequestMethod.OPTIONS;
import static io.servicetalk.http.api.HttpRequestMethod.PATCH;
import static io.servicetalk.http.api.HttpRequestMethod.POST;
import static io.servicetalk.http.api.HttpRequestMethod.PUT;
import static io.servicetalk.http.api.HttpRequestMethod.Properties.NONE;
import static io.servicetalk.http.api.HttpRequestMethod.TRACE;
import static io.servicetalk.transport.netty.internal.CloseHandler.UNSUPPORTED_PROTOCOL_CLOSE_HANDLER;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.util.Objects.requireNonNull;

final class HttpRequestDecoder extends HttpObjectDecoder<HttpRequestMetaData> {
    private static final Map<ByteBuf, HttpRequestMethod> BUF_TO_METHOD_MAP = new HashMap<ByteBuf, HttpRequestMethod>() {
        {
            put(copiedBuffer("GET", US_ASCII), GET);
            put(copiedBuffer("HEAD", US_ASCII), HEAD);
            put(copiedBuffer("OPTIONS", US_ASCII), OPTIONS);
            put(copiedBuffer("TRACE", US_ASCII), TRACE);
            put(copiedBuffer("PUT", US_ASCII), PUT);
            put(copiedBuffer("DELETE", US_ASCII), DELETE);
            put(copiedBuffer("POST", US_ASCII), POST);
            put(copiedBuffer("PATCH", US_ASCII), PATCH);
            put(copiedBuffer("CONNECT", US_ASCII), CONNECT);
        }
    };

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
    protected HttpRequestMetaData createMessage(ByteBuf first, ByteBuf second, ByteBuf third) {
        return newRequestMetaData(nettyBufferToHttpVersion(third),
                                  nettyBufferToHttpMethod(first),
                                  second.toString(US_ASCII),
                                  headersFactory().newHeaders());
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

    private static HttpRequestMethod nettyBufferToHttpMethod(ByteBuf buf) {
        HttpRequestMethod method = BUF_TO_METHOD_MAP.get(buf);
        return method != null ? method : HttpRequestMethod.of(newBufferFrom(buf.retain()), NONE);
    }
}
