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

import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpMetaData;
import io.servicetalk.http.api.HttpRequestFactory;
import io.servicetalk.http.api.HttpRequestMethod;

import io.netty.buffer.ByteBuf;

import java.util.HashMap;
import java.util.Map;

import static io.netty.buffer.Unpooled.copiedBuffer;
import static io.servicetalk.buffer.netty.BufferUtil.newBufferFrom;
import static io.servicetalk.http.api.HttpRequestMethods.CONNECT;
import static io.servicetalk.http.api.HttpRequestMethods.DELETE;
import static io.servicetalk.http.api.HttpRequestMethods.GET;
import static io.servicetalk.http.api.HttpRequestMethods.HEAD;
import static io.servicetalk.http.api.HttpRequestMethods.OPTIONS;
import static io.servicetalk.http.api.HttpRequestMethods.PATCH;
import static io.servicetalk.http.api.HttpRequestMethods.POST;
import static io.servicetalk.http.api.HttpRequestMethods.PUT;
import static io.servicetalk.http.api.HttpRequestMethods.TRACE;
import static io.servicetalk.http.api.HttpRequestMethods.newRequestMethod;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.util.Objects.requireNonNull;

final class HttpRequestDecoder extends HttpObjectDecoder {
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
    private final HttpRequestFactory requestFactory;

    HttpRequestDecoder(HttpRequestFactory requestFactory,
                       int maxInitialLineLength, int maxHeaderSize, int maxChunkSize, boolean chunkedSupported) {
        super(maxInitialLineLength, maxHeaderSize, maxChunkSize, chunkedSupported);
        this.requestFactory = requireNonNull(requestFactory);
    }

    @Override
    protected boolean isDecodingRequest() {
        return true;
    }

    @Override
    protected HttpMetaData createMessage(ByteBuf first, ByteBuf second, ByteBuf third) {
        return requestFactory.newRequestMetaData(nettyBufferToHttpVersion(third),
                                         nettyBufferToHttpMethod(first),
                                         second.toString(US_ASCII));
    }

    @Override
    protected HttpHeaders newTrailers() {
        return requestFactory.newTrailers();
    }

    @Override
    protected HttpHeaders newEmptyTrailers() {
        return requestFactory.newEmptyTrailers();
    }

    private static HttpRequestMethod nettyBufferToHttpMethod(ByteBuf buf) {
        HttpRequestMethod method = BUF_TO_METHOD_MAP.get(buf);
        return method != null ? method : newRequestMethod(newBufferFrom(buf.retain()));
    }
}
