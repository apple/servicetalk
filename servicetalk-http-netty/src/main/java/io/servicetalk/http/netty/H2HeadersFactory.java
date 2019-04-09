/*
 * Copyright © 2019 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.http.api.HttpHeadersFactory;
import io.servicetalk.http.api.HttpProtocolVersion;

import io.netty.handler.codec.http2.DefaultHttp2Headers;

/**
 * A {@link HttpHeadersFactory} optimized for {@link HttpProtocolVersion#HTTP_2_0}.
 */
public final class H2HeadersFactory implements HttpHeadersFactory {
    public static final HttpHeadersFactory INSTANCE = new H2HeadersFactory(true, true);

    private final boolean validateNames;
    private final boolean validateCookies;
    private final int headersArraySizeHint;
    private final int trailersArraySizeHint;

    /**
     * Create an instance of the factory with the default array size hint.
     *
     * @param validateNames {@code true} to validate header/trailer names.
     * @param validateCookies {@code true} to validate cookie contents when parsing.
     */
    public H2HeadersFactory(final boolean validateNames, final boolean validateCookies) {
        this(validateNames, validateCookies, 16, 4);
    }

    /**
     * Create an instance of the factory.
     *
     * @param validateNames {@code true} to validate header/trailer names.
     * @param validateCookies {@code true} to validate cookie contents when parsing.
     * @param headersArraySizeHint A hint as to how large the hash data structure should be for the headers.
     * @param trailersArraySizeHint A hint as to how large the hash data structure should be for the trailers.
     */
    public H2HeadersFactory(final boolean validateNames, final boolean validateCookies,
                            final int headersArraySizeHint, final int trailersArraySizeHint) {
        this.validateNames = validateNames;
        this.validateCookies = validateCookies;
        this.headersArraySizeHint = headersArraySizeHint;
        this.trailersArraySizeHint = trailersArraySizeHint;
    }

    @Override
    public HttpHeaders newHeaders() {
        return new NettyH2HeadersToHttpHeaders(new DefaultHttp2Headers(validateNames, headersArraySizeHint),
                validateCookies);
    }

    @Override
    public HttpHeaders newTrailers() {
        return new NettyH2HeadersToHttpHeaders(new DefaultHttp2Headers(validateNames, trailersArraySizeHint),
                validateCookies);
    }

    @Override
    public HttpHeaders newEmptyTrailers() {
        return new NettyH2HeadersToHttpHeaders(new DefaultHttp2Headers(validateNames, 0), validateCookies);
    }

    @Override
    public boolean validateCookies() {
        return validateCookies;
    }
}
