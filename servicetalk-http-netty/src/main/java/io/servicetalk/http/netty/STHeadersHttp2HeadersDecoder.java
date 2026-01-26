/*
 * Copyright © 2026 Apple Inc. and the ServiceTalk project authors
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

import io.netty.handler.codec.http2.DefaultHttp2HeadersDecoder;
import io.netty.handler.codec.http2.Http2Headers;

import static java.util.Objects.requireNonNull;

final class STHeadersHttp2HeadersDecoder extends DefaultHttp2HeadersDecoder {

    private final HttpHeadersFactory headersFactory;

    STHeadersHttp2HeadersDecoder(HttpHeadersFactory headersFactory,
                                 boolean validateHeaders, long maxHeadersListSize) {
        super(validateHeaders, maxHeadersListSize);
        this.headersFactory = requireNonNull(headersFactory);
    }

    STHeadersHttp2HeadersDecoder(HttpHeadersFactory headersFactory, boolean validateHeaders) {
        super(validateHeaders);
        this.headersFactory = headersFactory;
    }

    @Override
    protected Http2Headers newHeaders() {
        return new ServiceTalkHttpHeadersAsHttp2Headers(headersFactory.newHeaders(), false);
    }
}
