/*
 * Copyright © 2020 Apple Inc. and the ServiceTalk project authors
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

/**
 * Additional exceptions for <a href="https://tools.ietf.org/html/rfc7230">HTTP/1.1</a> specification.
 */
public final class H1SpecExceptions {

    private final boolean allowChunkedResponseWithoutBody;

    H1SpecExceptions(final boolean allowChunkedResponseWithoutBody) {
        this.allowChunkedResponseWithoutBody = allowChunkedResponseWithoutBody;
    }

    /**
     * Defines if an HTTP/1.1 response with <a href="https://tools.ietf.org/html/rfc7230#section-6.1">
     * Connection: close</a> and <a href="https://tools.ietf.org/html/rfc7230#section-3.3.1">
     * Transfer-Encoding: chunked</a> headers that does not start reading the
     * <a href="https://tools.ietf.org/html/rfc7230#section-4.1">chunked-body</a> before server closes the connection
     * should be considered as a legit response.
     * <p>
     * While this use-case is not supported by <a href="https://tools.ietf.org/html/rfc7230#section-3.3.3">RFC 7230</a>,
     * some older server implementations may use connection closure as an indicator of message completion even if
     * {@code Transfer-Encoding: chunked} header is present:
     * <pre>{@code
     *     HTTP/1.1 200 OK
     *     Content-Type: text/plain
     *     Transfer-Encoding: chunked
     *     Connection: close
     * }</pre>
     *
     * @return {@code true} if response decoder should complete responses without
     * <a href="https://tools.ietf.org/html/rfc7230#section-4.1">chunked-body</a> when server closes the connection.
     */
    public boolean allowChunkedResponseWithoutBody() {
        return allowChunkedResponseWithoutBody;
    }

    public static final class Builder {

        private boolean allowChunkedResponseWithoutBody;

        /**
         * Defines if an HTTP/1.1 response with <a href="https://tools.ietf.org/html/rfc7230#section-6.1">
         * Connection: close</a> and <a href="https://tools.ietf.org/html/rfc7230#section-3.3.1">
         * Transfer-Encoding: chunked</a> headers that does not start reading the
         * <a href="https://tools.ietf.org/html/rfc7230#section-4.1">chunked-body</a> before server closes the connection
         * should be considered as a legit response.
         * <p>
         * While this use-case is not supported by <a href="https://tools.ietf.org/html/rfc7230#section-3.3.3">RFC 7230</a>,
         * some older server implementations may use connection closure as an indicator of message completion even if
         * {@code Transfer-Encoding: chunked} header is present:
         * <pre>{@code
         *     HTTP/1.1 200 OK
         *     Content-Type: text/plain
         *     Transfer-Encoding: chunked
         *     Connection: close
         * }</pre>
         *
         * @return {@code this}
         */
        public Builder allowChunkedResponseWithoutBody() {
            this.allowChunkedResponseWithoutBody = true;
            return this;
        }

        /**
         * Builds {@link H1SpecExceptions}.
         *
         * @return a new {@link H1SpecExceptions}
         */
        public H1SpecExceptions build() {
            return new H1SpecExceptions(allowChunkedResponseWithoutBody);
        }
    }
}
