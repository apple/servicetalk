/*
 * Copyright © 2020-2022 Apple Inc. and the ServiceTalk project authors
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

    private final boolean allowPrematureClosureBeforePayloadBody;
    private final boolean allowLFWithoutCR;
    private final boolean allowTransferEncodingWithContentLength;

    private H1SpecExceptions(final boolean allowPrematureClosureBeforePayloadBody,
                             final boolean allowLFWithoutCR,
                             final boolean allowTransferEncodingWithContentLength) {
        this.allowPrematureClosureBeforePayloadBody = allowPrematureClosureBeforePayloadBody;
        this.allowLFWithoutCR = allowLFWithoutCR;
        this.allowTransferEncodingWithContentLength = allowTransferEncodingWithContentLength;
    }

    /**
     * Allows interpreting connection closures as the end of HTTP/1.1 messages if the receiver did not receive any part
     * of the payload body before the connection closure.
     *
     * @return {@code true} if the receiver should interpret connection closures as the end of HTTP/1.1 messages if it
     * did not receive any part of the payload body before the connection closure
     */
    public boolean allowPrematureClosureBeforePayloadBody() {
        return allowPrematureClosureBeforePayloadBody;
    }

    /**
     * Allow {@code LF} without a proceeding {@code CR} as described in
     * <a href="https://tools.ietf.org/html/rfc7230#section-3.5">HTTP/1.x Message Parsing Robustness</a>:
     * <pre>
     *   Although the line terminator for the start-line and header fields is
     *   the sequence CRLF, a recipient MAY recognize a single LF as a line
     *   terminator and ignore any preceding CR.
     * </pre>
     * @return {@code true} to allow {@code LF} without a proceeding {@code CR}.
     */
    public boolean allowLFWithoutCR() {
        return allowLFWithoutCR;
    }

    /**
     * Allow an HTTP/1.1 message to carry both {@code Transfer-Encoding} and
     * {@code Content-Length} headers, which is forbidden by
     * <a href="https://datatracker.ietf.org/doc/html/rfc9112#section-6.1">RFC 9112 section 6.1</a>
     * because it is a request smuggling vector. When {@code true}, the decoder restores
     * the RFC 7230 lenient behaviour and strips {@code Content-Length}; for requests it
     * additionally forces {@code Connection: close} so the connection is not reused for a
     * smuggled follow-up. Intended only as a temporary mitigation against a broken peer.
     *
     * @return {@code true} to accept HTTP/1.1 messages with both {@code Transfer-Encoding}
     * and {@code Content-Length} headers.
     */
    public boolean allowTransferEncodingWithContentLength() {
        return allowTransferEncodingWithContentLength;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() +
                "{allowPrematureClosureBeforePayloadBody=" + allowPrematureClosureBeforePayloadBody +
                ", allowLFWithoutCR=" + allowLFWithoutCR +
                ", allowTransferEncodingWithContentLength=" + allowTransferEncodingWithContentLength +
                '}';
    }

    /**
     * Builder for {@link H1SpecExceptions}.
     */
    public static final class Builder {

        private boolean allowPrematureClosureBeforePayloadBody;
        private boolean allowLFWithoutCR;
        private boolean allowTransferEncodingWithContentLength;

        /**
         * Allows interpreting connection closures as the end of HTTP/1.1 messages if the receiver did not receive any
         * part of the payload body before the connection closure.
         * @param allow {@code true} if the receiver should interpret connection closures as the end of HTTP/1.1
         * messages if it did not receive any part of the payload body before the connection closure.
         * @return {@code this}
         */
        public Builder allowPrematureClosureBeforePayloadBody(boolean allow) {
            this.allowPrematureClosureBeforePayloadBody = allow;
            return this;
        }

        /**
         * Allow {@code LF} without a proceeding {@code CR} as described in
         * <a href="https://tools.ietf.org/html/rfc7230#section-3.5">HTTP/1.x Message Parsing Robustness</a>:
         * <pre>
         *   Although the line terminator for the start-line and header fields is
         *   the sequence CRLF, a recipient MAY recognize a single LF as a line
         *   terminator and ignore any preceding CR.
         * </pre>
         * @param allow {@code true} to allow {@code LF} without a proceeding {@code CR}.
         * @return {@code this}
         */
        public Builder allowLFWithoutCR(boolean allow) {
            this.allowLFWithoutCR = allow;
            return this;
        }

        /**
         * Allow an HTTP/1.1 message to carry both {@code Transfer-Encoding} and
         * {@code Content-Length} headers. See
         * {@link H1SpecExceptions#allowTransferEncodingWithContentLength()}. Default is
         * {@code false}: such messages are rejected per RFC 9112 section 6.1.
         *
         * @param allow {@code true} to accept HTTP/1.1 messages with both
         * {@code Transfer-Encoding} and {@code Content-Length} headers.
         * @return {@code this}
         */
        public Builder allowTransferEncodingWithContentLength(boolean allow) {
            this.allowTransferEncodingWithContentLength = allow;
            return this;
        }

        /**
         * Builds {@link H1SpecExceptions}.
         *
         * @return a new {@link H1SpecExceptions}
         */
        public H1SpecExceptions build() {
            return new H1SpecExceptions(allowPrematureClosureBeforePayloadBody, allowLFWithoutCR,
                    allowTransferEncodingWithContentLength);
        }
    }
}
