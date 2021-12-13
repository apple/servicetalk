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
package io.servicetalk.http.api;

/**
 * Default implementation of {@link HttpHeadersFactory}.
 */
public final class DefaultHttpHeadersFactory implements HttpHeadersFactory {

    private static final boolean DEFAULT_VALIDATE_VALUES = false;
    public static final HttpHeadersFactory INSTANCE = new DefaultHttpHeadersFactory(true, true,
            DEFAULT_VALIDATE_VALUES);

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
     * @param validateValues {@code true} to validate header/trailer values.
     */
    public DefaultHttpHeadersFactory(final boolean validateNames, final boolean validateCookies,
                                     final boolean validateValues) {
        this(validateNames, validateCookies, validateValues, 16, 4);
    }

    /**
     * Create an instance of the factory.
     *
     * @param validateNames {@code true} to validate header/trailer names.
     * @param validateCookies {@code true} to validate cookie contents when parsing.
     * @param validateValues {@code true} to validate header/trailer values.
     * @param headersArraySizeHint A hint as to how large the hash data structure should be for the headers.
     * @param trailersArraySizeHint A hint as to how large the hash data structure should be for the trailers.
     */
    public DefaultHttpHeadersFactory(final boolean validateNames, final boolean validateCookies,
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
        return new DefaultHttpHeaders(headersArraySizeHint, validateNames, validateCookies, validateValues);
    }

    @Override
    public HttpHeaders newTrailers() {
        return new DefaultHttpHeaders(trailersArraySizeHint, validateNames, validateCookies, validateValues);
    }

    @Override
    public HttpHeaders newEmptyTrailers() {
        return new DefaultHttpHeaders(0, validateNames, validateCookies, validateValues);
    }

    @Override
    public boolean validateCookies() {
        return validateCookies;
    }

    @Override
    public boolean validateValues() {
        return validateValues;
    }
}
