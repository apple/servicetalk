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

    public static final HttpHeadersFactory INSTANCE = new DefaultHttpHeadersFactory(true);

    private final boolean validateNames;
    private final int arraySizeHint;

    /**
     * Create an instance of the factory with the default array size hint.
     *
     * @param validateNames {@code true} to validate header names.
     */
    public DefaultHttpHeadersFactory(final boolean validateNames) {
        this(validateNames, 16);
    }

    /**
     * Create an instance of the factory.
     *
     * @param arraySizeHint A hint as to how large the hash data structure should be.
     * The next positive power of two will be used. An upper bound may be enforced.
     * @param validateNames {@code true} to validate header names.
     */
    public DefaultHttpHeadersFactory(final boolean validateNames, final int arraySizeHint) {
        this.validateNames = validateNames;
        this.arraySizeHint = arraySizeHint;
    }

    @Override
    public HttpHeaders newHeaders() {
        return new DefaultHttpHeaders(arraySizeHint, validateNames);
    }
}
