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
 * Factory method for creating {@link HttpHeaders}.
 */
public interface HttpHeadersFactory {
    /**
     * Create an {@link HttpHeaders} instance.
     *
     * @return an {@link HttpHeaders} instance.
     */
    HttpHeaders newHeaders();

    /**
     * Create an {@link HttpHeaders} instance designed to hold
     * <a href="https://tools.ietf.org/html/rfc7230#section-4.4">trailers</a>.
     *
     * @return an {@link HttpHeaders} instance.
     */
    HttpHeaders newTrailers();

    /**
     * Create an {@link HttpHeaders} instance designed to hold
     * <a href="https://tools.ietf.org/html/rfc7230#section-4.4">trailers</a>, possibly optimized for being empty.
     * <p>
     * Note: this should not return an immutable instance unless it is known that no code will need to mutate the
     * trailers.
     *
     * @return an {@link HttpHeaders} instance.
     */
    default HttpHeaders newEmptyTrailers() {
        return newTrailers();
    }

    /**
     * Determine if cookies should be validated during parsing into {@link HttpSetCookie}s.
     *
     * @return {@code true} if a cookies should be validated during parsing into {@link HttpSetCookie}s.
     */
    boolean validateCookies();

    /**
     * Determine if header values should be validated during parsing into {@link HttpHeaders}s.
     *
     * @return {@code true} if header values should be validated during parsing into {@link HttpHeaders}s.
     */
    boolean validateValues();
}
