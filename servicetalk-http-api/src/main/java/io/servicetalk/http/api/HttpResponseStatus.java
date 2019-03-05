/*
 * Copyright © 2018-2019 Apple Inc. and the ServiceTalk project authors
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
 *
 * @see HttpResponseStatuses
 */
public interface HttpResponseStatus {
    /**
     * Get the three digit <a href="https://tools.ietf.org/html/rfc7231#section-6">status-code</a> indicating status of
     * the response.
     *
     * @return the three digit <a href="https://tools.ietf.org/html/rfc7231#section-6">status-code</a> indicating status
     * of the response
     */
    int code();

    /**
     * Write the equivalent of {@link #code()} to a {@link Buffer}.
     *
     * @param buffer The {@link Buffer} to write to
     */
    void writeCodeTo(Buffer buffer);

    /**
     * Write the <a href="https://tools.ietf.org/html/rfc7230.html#section-3.1.2">reason-phrase</a> portion of the
     * response to a {@link Buffer}.
     * <pre>
     *     The reason-phrase element exists for the sole purpose of providing a
     *     textual description associated with the numeric status code, mostly
     *     out of deference to earlier Internet application protocols that were
     *     more frequently used with interactive text clients.  A client SHOULD
     *     ignore the reason-phrase content.
     * </pre>
     *
     * @param buffer The {@link Buffer} to write to
     */
    void writeReasonPhraseTo(Buffer buffer);

    /**
     * Get the {@link StatusClass} for this {@link HttpResponseStatus}.
     *
     * @return the {@link StatusClass} for this {@link HttpResponseStatus}
     */
    StatusClass statusClass();

    /**
     * Compares the specified object with this {@link HttpResponseStatus} for equality.
     * <p>
     * Returns {@code true} if and only if the specified object is also a {@link HttpResponseStatus}, and both objects
     * have the same {@link #code()} value. A reason-phrase is ignored because a client SHOULD ignore the reason-phrase
     * content, according to the
     * <a href="https://tools.ietf.org/html/rfc7230.html#section-3.1.2">RFC 7230, section 3.1.2</a>.
     * This definition ensures that the equals method works properly across different implementations of the
     * {@link HttpProtocolVersion} interface.
     *
     * @param o the object to be compared for equality with this {@link HttpResponseStatus}
     * @return {@code true} if the specified object is equal to this {@link HttpResponseStatus}
     */
    @Override
    boolean equals(Object o);

    /**
     * Returns the hash code value for this {@link HttpResponseStatus}.
     * <p>
     * The hash code of an {@link HttpResponseStatus} MUST be consistent with {@link #equals(Object)} implementation
     * and is defined to be the result of the following calculation:
     * <pre>{@code
     *     public int hashCode() {
     *         return 31 * code();
     *     }
     * }</pre>
     * This ensures that {@code status1.equals(status2)} implies that {@code status1.hashCode() == status2.hashCode()}
     * for any two {@link HttpResponseStatus}es, {@code status1} and {@code status2}, as required by the general
     * contract of {@link Object#hashCode}.
     *
     * @return the hash code value for this {@link HttpResponseStatus}
     * @see Object#equals(Object)
     * @see #equals(Object)
     */
    @Override
    int hashCode();

    /**
     * The class of <a href="https://tools.ietf.org/html/rfc7231#section-6">response status codes</a>.
     */
    enum StatusClass {
        /**
         * <a href="https://tools.ietf.org/html/rfc7231#section-6.2">Informational 1xx</a>.
         */
        INFORMATIONAL_1XX(100, 199),

        /**
         * <a href="https://tools.ietf.org/html/rfc7231#section-6.3">Successful 2xx</a>.
         */
        SUCCESSFUL_2XX(200, 299),

        /**
         * <a href="https://tools.ietf.org/html/rfc7231#section-6.4">Redirection 3xx</a>.
         */
        REDIRECTION_3XX(300, 399),

        /**
         * <a href="https://tools.ietf.org/html/rfc7231#section-6.5">Client Error 4xx</a>.
         */
        CLIENT_ERROR_4XX(400, 499),

        /**
         * <a href="https://tools.ietf.org/html/rfc7231#section-6.6">Server Error 5xx</a>.
         */
        SERVER_ERROR_5XX(500, 599),

        /**
         * Unknown. 3-digit status codes outside of the defined range of
         * <a href="https://tools.ietf.org/html/rfc7231#section-6">response status codes</a>.
         */
        UNKNOWN(600, 999);

        private final int minStatus;
        private final int maxStatus;

        StatusClass(final int minStatus, final int maxStatus) {
            this.minStatus = minStatus;
            this.maxStatus = maxStatus;
        }

        /**
         * Determine if {@code code} falls into this {@link StatusClass}.
         *
         * @param statusCode the status code to test
         * @return {@code true} if and only if the specified HTTP status code falls into this class
         */
        public boolean contains(final int statusCode) {
            return minStatus <= statusCode && statusCode <= maxStatus;
        }

        /**
         * Determine if {@code status} code falls into this {@link StatusClass}.
         *
         * @param status the status to test
         * @return {@code true} if and only if the specified HTTP status code falls into this class
         */
        public boolean contains(final HttpResponseStatus status) {
            return contains(status.code());
        }

        /**
         * Determines the {@link StatusClass} from the {@code statusCode}.
         *
         * @param statusCode the status code to use for determining the {@link StatusClass}
         * @return one of the {@link StatusClass} enum values
         * @throws IllegalArgumentException if {@code statusCode} is not a 3-digit integer
         */
        public static StatusClass fromStatusCode(final int statusCode) {
            if (statusCode < 100 || statusCode > 999) {
                throw new IllegalArgumentException("Illegal status code: " + statusCode + ", expected [100-999]");
            }

            if (INFORMATIONAL_1XX.contains(statusCode)) {
                return INFORMATIONAL_1XX;
            }
            if (SUCCESSFUL_2XX.contains(statusCode)) {
                return SUCCESSFUL_2XX;
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
