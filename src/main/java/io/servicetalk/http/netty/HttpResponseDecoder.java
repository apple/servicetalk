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
import io.servicetalk.http.api.HttpResponseFactory;
import io.servicetalk.http.api.HttpResponseStatus;

import io.netty.buffer.ByteBuf;

import static io.servicetalk.buffer.netty.BufferUtil.newBufferFrom;
import static io.servicetalk.http.api.HttpResponseStatuses.getResponseStatus;
import static java.lang.Integer.parseInt;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.util.Objects.requireNonNull;

final class HttpResponseDecoder extends HttpObjectDecoder {
    private final HttpResponseFactory responseFactory;

    HttpResponseDecoder(HttpResponseFactory requestFactory,
                        int maxInitialLineLength, int maxHeaderSize, int maxChunkSize, boolean chunkedSupported) {
        super(maxInitialLineLength, maxHeaderSize, maxChunkSize, chunkedSupported);
        this.responseFactory = requireNonNull(requestFactory);
    }

    @Override
    protected boolean isDecodingRequest() {
        return false;
    }

    @Override
    protected HttpMetaData createMessage(ByteBuf first, ByteBuf second, ByteBuf third) {
        return responseFactory.newResponseMetaData(nettyBufferToHttpVersion(first),
                                           nettyBufferToHttpStatus(second, third));
    }

    @Override
    protected HttpHeaders newTrailers() {
        return responseFactory.newTrailers();
    }

    @Override
    protected HttpHeaders newEmptyTrailers() {
        return responseFactory.newEmptyTrailers();
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
