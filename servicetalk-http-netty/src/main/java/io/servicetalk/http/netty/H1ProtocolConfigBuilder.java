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

import io.servicetalk.http.api.DefaultHttpHeadersFactory;
import io.servicetalk.http.api.HttpClient;
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpHeadersFactory;

import static java.util.Objects.requireNonNull;

/**
 * Builder for {@link H1ProtocolConfig}.
 *
 * @see HttpProtocolConfigs#h1()
 */
public final class H1ProtocolConfigBuilder {

    private int maxPipelinedRequests = 1;
    private int maxStartLineLength = 4096;
    private int maxHeaderFieldLength = 8192;
    private HttpHeadersFactory headersFactory = DefaultHttpHeadersFactory.INSTANCE;
    private int headersEncodedSizeEstimate = 256;
    private int trailersEncodedSizeEstimate = 256;
    private boolean allowChunkedResponseWithoutBody;

    H1ProtocolConfigBuilder() {
    }

    /**
     * Sets the {@link HttpHeadersFactory} to be used for creating {@link HttpHeaders} when decoding HTTP messages.
     *
     * @param headersFactory {@link HttpHeadersFactory} to be used for creating {@link HttpHeaders} when decoding HTTP
     * messages
     * @return {@code this}
     */
    public H1ProtocolConfigBuilder headersFactory(final HttpHeadersFactory headersFactory) {
        this.headersFactory = requireNonNull(headersFactory);
        return this;
    }

    /**
     * Sets the maximum number of pipelined HTTP requests to queue up.
     * <p>
     * Anything above this value will be rejected, {@code 1} means pipelining is disabled and requests/responses are
     * processed sequentially.
     * <p>
     * <b>Note:</b> {@link HttpClient#reserveConnection reserved connections} will not be restricted by this setting.
     *
     * @param maxPipelinedRequests maximum number of pipelined requests to queue up
     * @return {@code this}
     */
    public H1ProtocolConfigBuilder maxPipelinedRequests(final int maxPipelinedRequests) {
        this.maxPipelinedRequests = maxPipelinedRequests;
        return this;
    }

    /**
     * Sets the maximum length of the HTTP <a href="https://tools.ietf.org/html/rfc7230#section-3.1">start line</a> for
     * an HTTP message.
     * <p>
     * <b>Note:</b> a decoder will close the connection with {@code TooLongFrameException} if the start line exceeds
     * this value.
     *
     * @param maxStartLineLength maximum size of the HTTP
     * <a href="https://tools.ietf.org/html/rfc7230#section-3.1">start line</a> for an HTTP message
     * @return {@code this}
     */
    public H1ProtocolConfigBuilder maxStartLineLength(final int maxStartLineLength) {
        this.maxStartLineLength = maxStartLineLength;
        return this;
    }

    /**
     * Sets the maximum length of the HTTP <a href="https://tools.ietf.org/html/rfc7230#section-3.2">header fields</a>
     * and <a href="https://tools.ietf.org/html/rfc7230#section-4.1.2trailers">trailer fields</a> to parse.
     * <p>
     * <b>Note:</b> a decoder will close the connection with {@code TooLongFrameException} if the length of a header or
     * trailer field exceeds this value.
     *
     * @param maxHeaderFieldLength maximum length of HTTP
     * <a href="https://tools.ietf.org/html/rfc7230#section-3.2">header fields</a> and
     * <a href="https://tools.ietf.org/html/rfc7230#section-4.1.2trailers">trailer fields</a> to parse
     * @return {@code this}
     */
    public H1ProtocolConfigBuilder maxHeaderFieldLength(final int maxHeaderFieldLength) {
        this.maxHeaderFieldLength = maxHeaderFieldLength;
        return this;
    }

    /**
     * Sets the value used to calculate an exponential moving average of the encoded size of the HTTP
     * <a href="https://tools.ietf.org/html/rfc7230#section-3.1">start line</a> and
     * <a href="https://tools.ietf.org/html/rfc7230#section-3.2">header fields</a> for a guess for future buffer
     * allocations.
     *
     * @param headersEncodedSizeEstimate value used to calculate an exponential moving average of the encoded size of
     * the HTTP <a href="https://tools.ietf.org/html/rfc7230#section-3.1">start line</a> and
     * <a href="https://tools.ietf.org/html/rfc7230#section-3.2">header fields</a>
     * @return {@code this}
     */
    public H1ProtocolConfigBuilder headersEncodedSizeEstimate(final int headersEncodedSizeEstimate) {
        this.headersEncodedSizeEstimate = headersEncodedSizeEstimate;
        return this;
    }

    /**
     * Sets the value used to calculate an exponential moving average of the encoded size of the HTTP
     * <a href="https://tools.ietf.org/html/rfc7230#section-4.1.2trailers">trailer fields</a> for a guess for future
     * buffer allocations.
     *
     * @param trailersEncodedSizeEstimate value used to calculate an exponential moving average of the encoded size of
     * the HTTP <a href="https://tools.ietf.org/html/rfc7230#section-4.1.2trailers">trailer fields</a>
     * @return {@code this}
     */
    public H1ProtocolConfigBuilder trailersEncodedSizeEstimate(final int trailersEncodedSizeEstimate) {
        this.trailersEncodedSizeEstimate = trailersEncodedSizeEstimate;
        return this;
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
     * @return {@code this}
     */
    public H1ProtocolConfigBuilder allowChunkedResponseWithoutBody() {
        this.allowChunkedResponseWithoutBody = true;
        return this;
    }

    /**
     * Builds {@link H1ProtocolConfig}.
     *
     * @return a new {@link H1ProtocolConfig}
     */
    public H1ProtocolConfig build() {
        return new DefaultH1ProtocolConfig(headersFactory, maxPipelinedRequests, maxStartLineLength,
                maxHeaderFieldLength, headersEncodedSizeEstimate, trailersEncodedSizeEstimate,
                allowChunkedResponseWithoutBody);
    }

    private static final class DefaultH1ProtocolConfig implements H1ProtocolConfig {

        private final HttpHeadersFactory headersFactory;
        private final int maxPipelinedRequests;
        private final int maxStartLineLength;
        private final int maxHeaderFieldLength;
        private final int headersEncodedSizeEstimate;
        private final int trailersEncodedSizeEstimate;
        private final boolean allowChunkedResponseWithoutBody;

        DefaultH1ProtocolConfig(final HttpHeadersFactory headersFactory, final int maxPipelinedRequests,
                                final int maxStartLineLength, final int maxHeaderFieldLength,
                                final int headersEncodedSizeEstimate, final int trailersEncodedSizeEstimate,
                                final boolean allowChunkedResponseWithoutBody) {
            this.headersFactory = headersFactory;
            this.maxPipelinedRequests = maxPipelinedRequests;
            this.maxStartLineLength = maxStartLineLength;
            this.maxHeaderFieldLength = maxHeaderFieldLength;
            this.headersEncodedSizeEstimate = headersEncodedSizeEstimate;
            this.trailersEncodedSizeEstimate = trailersEncodedSizeEstimate;
            this.allowChunkedResponseWithoutBody = allowChunkedResponseWithoutBody;
        }

        @Override
        public HttpHeadersFactory headersFactory() {
            return headersFactory;
        }

        @Override
        public int maxPipelinedRequests() {
            return maxPipelinedRequests;
        }

        @Override
        public int maxStartLineLength() {
            return maxStartLineLength;
        }

        @Override
        public int maxHeaderFieldLength() {
            return maxHeaderFieldLength;
        }

        @Override
        public int headersEncodedSizeEstimate() {
            return headersEncodedSizeEstimate;
        }

        @Override
        public int trailersEncodedSizeEstimate() {
            return trailersEncodedSizeEstimate;
        }

        @Override
        public boolean allowChunkedResponseWithoutBody() {
            return allowChunkedResponseWithoutBody;
        }
    }
}
