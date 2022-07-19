/*
 * Copyright © 2019-2020 Apple Inc. and the ServiceTalk project authors
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

    private static final H1SpecExceptions DEFAULT_H1_SPEC_EXCEPTIONS = new H1SpecExceptions.Builder().build();

    private int maxPipelinedRequests = 1;
    private int maxStartLineLength = 4096;
    private int maxHeaderFieldLength = 8192;
    private HttpHeadersFactory headersFactory = DefaultHttpHeadersFactory.INSTANCE;
    private int headersEncodedSizeEstimate = 256;
    private int trailersEncodedSizeEstimate = 256;
    private H1SpecExceptions specExceptions = DEFAULT_H1_SPEC_EXCEPTIONS;

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
     * Sets additional exceptions for <a href="https://tools.ietf.org/html/rfc7230">HTTP/1.1</a> specification.
     *
     * @param specExceptions exceptions for <a href="https://tools.ietf.org/html/rfc7230">HTTP/1.1</a> specification
     * @return {@code this}
     */
    public H1ProtocolConfigBuilder specExceptions(final H1SpecExceptions specExceptions) {
        this.specExceptions = requireNonNull(specExceptions);
        return this;
    }

    /**
     * Builds {@link H1ProtocolConfig}.
     *
     * @return a new {@link H1ProtocolConfig}
     */
    public H1ProtocolConfig build() {
        return new DefaultH1ProtocolConfig(headersFactory, maxPipelinedRequests, maxStartLineLength,
                maxHeaderFieldLength, headersEncodedSizeEstimate, trailersEncodedSizeEstimate, specExceptions);
    }

    private static final class DefaultH1ProtocolConfig implements H1ProtocolConfig {

        private final HttpHeadersFactory headersFactory;
        private final int maxPipelinedRequests;
        private final int maxStartLineLength;
        private final int maxHeaderFieldLength;
        private final int headersEncodedSizeEstimate;
        private final int trailersEncodedSizeEstimate;
        private final H1SpecExceptions specExceptions;

        DefaultH1ProtocolConfig(final HttpHeadersFactory headersFactory, final int maxPipelinedRequests,
                                final int maxStartLineLength, final int maxHeaderFieldLength,
                                final int headersEncodedSizeEstimate, final int trailersEncodedSizeEstimate,
                                final H1SpecExceptions specExceptions) {
            this.headersFactory = headersFactory;
            this.maxPipelinedRequests = maxPipelinedRequests;
            this.maxStartLineLength = maxStartLineLength;
            this.maxHeaderFieldLength = maxHeaderFieldLength;
            this.headersEncodedSizeEstimate = headersEncodedSizeEstimate;
            this.trailersEncodedSizeEstimate = trailersEncodedSizeEstimate;
            this.specExceptions = specExceptions;
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
        public H1SpecExceptions specExceptions() {
            return specExceptions;
        }

        @Override
        public String toString() {
            return "DefaultH1ProtocolConfig{" +
                    "alpnId=" + alpnId() +
                    ", headersFactory=" + headersFactory +
                    ", maxPipelinedRequests=" + maxPipelinedRequests +
                    ", maxStartLineLength=" + maxStartLineLength +
                    ", maxHeaderFieldLength=" + maxHeaderFieldLength +
                    ", headersEncodedSizeEstimate=" + headersEncodedSizeEstimate +
                    ", trailersEncodedSizeEstimate=" + trailersEncodedSizeEstimate +
                    ", specExceptions=" + specExceptions +
                    '}';
        }
    }
}
