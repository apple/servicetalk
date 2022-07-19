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

import io.netty.handler.codec.http2.DefaultHttp2Headers;

/**
 * A {@link HttpHeadersFactory} optimized for HTTP/2.
 */
public final class H2HeadersFactory implements HttpHeadersFactory {

    private static final boolean DEFAULT_VALIDATE_VALUES = false;
    public static final HttpHeadersFactory INSTANCE = new H2HeadersFactory(true, true, DEFAULT_VALIDATE_VALUES);

    private final boolean validateNames;
    private final boolean validateCookies;
    private final boolean validateValues;
    private final int headersArraySizeHint;
    private final int trailersArraySizeHint;

    /**
     * Create an instance of the factory with the default array size hint.
     *
     * @param validateNames {@code true} to validate header/trailer names.
     * @param validateCookies {@code true} to validate cookie contents when parsing.
     * @param validateValues {@code true} to validate header values.
     */
    public H2HeadersFactory(final boolean validateNames, final boolean validateCookies,
                            final boolean validateValues) {
        this(validateNames, validateCookies, validateValues, 16, 4);
    }

    /**
     * Create an instance of the factory.
     *
     * @param validateNames {@code true} to validate header/trailer names.
     * @param validateCookies {@code true} to validate cookie contents when parsing.
     * @param validateValues {@code true} to validate header values.
     * @param headersArraySizeHint A hint as to how large the hash data structure should be for the headers.
     * @param trailersArraySizeHint A hint as to how large the hash data structure should be for the trailers.
     */
    public H2HeadersFactory(final boolean validateNames, final boolean validateCookies,
                            final boolean validateValues,
                            final int headersArraySizeHint, final int trailersArraySizeHint) {
        this.validateNames = validateNames;
        this.validateCookies = validateCookies;
        this.validateValues = validateValues;
        this.headersArraySizeHint = headersArraySizeHint;
        this.trailersArraySizeHint = trailersArraySizeHint;
    }

    @Override
    public HttpHeaders newHeaders() {
        return new NettyH2HeadersToHttpHeaders(new DefaultHttp2Headers(validateNames, headersArraySizeHint),
                validateCookies, validateValues);
    }

    @Override
    public HttpHeaders newTrailers() {
        return new NettyH2HeadersToHttpHeaders(new DefaultHttp2Headers(validateNames, trailersArraySizeHint),
                validateCookies, validateValues);
    }

    @Override
    public HttpHeaders newEmptyTrailers() {
        return new NettyH2HeadersToHttpHeaders(new DefaultHttp2Headers(validateNames, 0),
                validateCookies, validateValues);
    }

    @Override
    public boolean validateCookies() {
        return validateCookies;
    }

    @Override
    public boolean validateValues() {
        return validateValues;
    }

    @Override
    public String toString() {
        return "H2HeadersFactory{" +
                "validateNames=" + validateNames +
                ", validateCookies=" + validateCookies +
                ", validateValues=" + validateValues +
                ", headersArraySizeHint=" + headersArraySizeHint +
                ", trailersArraySizeHint=" + trailersArraySizeHint +
                '}';
    }
}
