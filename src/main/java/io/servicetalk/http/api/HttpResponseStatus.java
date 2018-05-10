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

import io.servicetalk.buffer.api.Buffer;

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
     * Get a {@link Buffer} version of {@link #getCode()} that can be used for encoding.
     * @return a {@link Buffer} version of {@link #getCode()} that can be used for encoding.
     */
    Buffer getCodeBuffer();

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
    Buffer getReasonPhrase();

    /**
     * Get the {@link StatusClass} for this {@link HttpResponseStatus}.
     * @return the {@link StatusClass} for this {@link HttpResponseStatus}.
     */
    StatusClass getStatusClass();

    /**
     * The <a href="https://tools.ietf.org/html/rfc7231#section-6">class of response</a>.
     */
    enum StatusClass {
        /**
         * <a href="https://tools.ietf.org/html/rfc7231#section-6.2">1xx Informational responses</a>.
         */
        INFORMATIONAL_1XX(100, 199),

        /**
         * <a href="https://tools.ietf.org/html/rfc7231#section-6.3">2xx Success</a>.
         */
        SUCCESS_2XX(200, 299),

        /**
         * <a href="https://tools.ietf.org/html/rfc7231#section-6.4">3xx Redirection</a>.
         */
        REDIRECTION_3XX(300, 399),

        /**
         * <a href="https://tools.ietf.org/html/rfc7231#section-6.5">4xx Client errors</a>.
         */
        CLIENT_ERROR_4XX(400, 499),

        /**
         * <a href="https://tools.ietf.org/html/rfc7231#section-6.6">5xx Server errors</a>.
         */
        SERVER_ERROR_5XX(500, 599),

        /**
         * Unknown. Statuses outside of the <a href="https://tools.ietf.org/html/rfc7231#section-6">defined range of
         * response codes</a>.
         */
        UNKNOWN(0, 0) {
            @Override
            public boolean contains(final int statusCode) {
                return statusCode < 100 || statusCode >= 600;
            }
        };

        private final int minStatus;
        private final int maxStatus;

        StatusClass(final int minStatus, final int maxStatus) {
            this.minStatus = minStatus;
            this.maxStatus = maxStatus;
        }

        /**
         * Determine if {@code code} falls into this class.
         * @param statusCode the status code to test.
         * @return {@code true} if and only if the specified HTTP status code falls into this class.
         */
        public boolean contains(final int statusCode) {
            return minStatus <= statusCode && statusCode <= maxStatus;
        }

        /**
         * Determine if {@code status} code falls into this class.
         * @param status the status to test.
         * @return {@code true} if and only if the specified HTTP status code falls into this class.
         */
        public boolean contains(final HttpResponseStatus status) {
            return contains(status.getCode());
        }

        /**
         * Determines the {@link StatusClass} from the {@code statusCode}.
         *
         * @param statusCode the status code to use for determining the {@link StatusClass}.
         * @return One of the {@link StatusClass} enum values.
         */
        public static StatusClass toStatusClass(final int statusCode) {
            if (INFORMATIONAL_1XX.contains(statusCode)) {
                return INFORMATIONAL_1XX;
            }
            if (SUCCESS_2XX.contains(statusCode)) {
                return SUCCESS_2XX;
            }
            if (REDIRECTION_3XX.contains(statusCode)) {
                return REDIRECTION_3XX;
            }
            if (CLIENT_ERROR_4XX.contains(statusCode)) {
                return CLIENT_ERROR_4XX;
            }
            if (SERVER_ERROR_5XX.contains(statusCode)) {
                return SERVER_ERROR_5XX;
            }
            return UNKNOWN;
        }
    }
}
