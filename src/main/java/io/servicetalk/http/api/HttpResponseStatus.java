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
 * <a href="https://tools.ietf.org/html/rfc7231#section-6">Response Status Code</a>.
 */
public interface HttpResponseStatus {
    /**
     * Get the three digit <a href="https://tools.ietf.org/html/rfc7231#section-6">status-code</a> indicating status of the response.
     * @return the three digit <a href="https://tools.ietf.org/html/rfc7231#section-6">status-code</a> indicating status of the response.
     */
    int getCode();

    /**
     * Get the <a href="https://tools.ietf.org/html/rfc7230.html#section-3.1.2">reason-phrase</a> portion of the response.
     * <pre>
     *     The reason-phrase element exists for the sole purpose of providing a
     *     textual description associated with the numeric status code, mostly
     *     out of deference to earlier Internet application protocols that were
     *     more frequently used with interactive text clients.  A client SHOULD
     *     ignore the reason-phrase content.
     * </pre>
     * @return the <a href="https://tools.ietf.org/html/rfc7230.html#section-3.1.2">reason-phrase</a> portion of the response.
     */
    String getReasonPhrase();

    /**
     * Get the {@link StatusClass} for this {@link HttpResponseStatus}.
     * @return the {@link StatusClass} for this {@link HttpResponseStatus}.
     */
    StatusClass getStatusClass();

    /**
     * The <a href="https://tools.ietf.org/html/rfc7231#section-6">class of response</a>.
     */
    interface StatusClass {
        /**
         * Determine if {@code code} falls into this class.
         * @param statusCode the status code to test.
         * @return {@code true} if and only if the specified HTTP status code falls into this class.
         */
        boolean contains(int statusCode);

        /**
         * Determine if {@code code} falls into this class.
         * @param statusCode the status code to test.
         * @return {@code true} if and only if the specified HTTP status code falls into this class.
         */
        default boolean contains(HttpResponseStatus statusCode) {
            return contains(statusCode.getCode());
        }
    }
}
