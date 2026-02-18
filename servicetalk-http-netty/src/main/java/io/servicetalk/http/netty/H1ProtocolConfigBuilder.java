/*
 * Copyright © 2019-2020, 2022 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.http.api.Http2Settings;
import io.servicetalk.http.api.HttpClient;
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpHeadersFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.servicetalk.utils.internal.NumberUtils.ensurePositive;
import static java.lang.Integer.getInteger;
import static java.util.Objects.requireNonNull;

/**
 * Builder for {@link H1ProtocolConfig}.
 *
 * @see HttpProtocolConfigs#h1()
 */
public final class H1ProtocolConfigBuilder {
    private static final Logger LOGGER = LoggerFactory.getLogger(H1ProtocolConfigBuilder.class);
    private static final H1SpecExceptions DEFAULT_H1_SPEC_EXCEPTIONS = new H1SpecExceptions.Builder().build();
    // Keep this value in sync with Http2SettingsBuilder#DEFAULT_MAX_HEADER_LIST_SIZE.
    private static final int DEFAULT_MAX_TOTAL_HEADER_FIELDS_LENGTH_VALUE = 32 * 1024;
    static final int DEFAULT_MAX_START_LINE_LENGTH = 8 * 1024;
    static final int DEFAULT_MAX_HEADER_FIELD_LENGTH = 16 * 1024;
    // FIXME: 0.43 - remove this temporary property
    static final String DEFAULT_MAX_TOTAL_HEADER_FIELDS_LENGTH_PROPERTY =
            "io.servicetalk.http.netty.temporaryDefaultMaxTotalHeaderFieldsLength";
    static final int DEFAULT_MAX_TOTAL_HEADER_FIELDS_LENGTH =
            getInteger(DEFAULT_MAX_TOTAL_HEADER_FIELDS_LENGTH_PROPERTY, DEFAULT_MAX_TOTAL_HEADER_FIELDS_LENGTH_VALUE);

    static {
        if (DEFAULT_MAX_TOTAL_HEADER_FIELDS_LENGTH != DEFAULT_MAX_TOTAL_HEADER_FIELDS_LENGTH_VALUE) {
            LOGGER.warn("-D{}: {}. This property will be removed in the future releases. " +
                            "Configure this value via H1ProtocolConfigBuilder#maxTotalHeaderFieldsLength(int) instead.",
                    DEFAULT_MAX_TOTAL_HEADER_FIELDS_LENGTH_PROPERTY, DEFAULT_MAX_TOTAL_HEADER_FIELDS_LENGTH);
        }
    }

    private int maxPipelinedRequests = 1;
    private int maxStartLineLength = DEFAULT_MAX_START_LINE_LENGTH;
    private int maxHeaderFieldLength = DEFAULT_MAX_HEADER_FIELD_LENGTH;
    private int maxTotalHeaderFieldsLength = DEFAULT_MAX_TOTAL_HEADER_FIELDS_LENGTH;
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
     * Sets the maximum number of <a href="https://datatracker.ietf.org/doc/html/rfc7230#section-6.3.2">pipelined</a>
     * HTTP requests to queue up.
     * <p>
     * Anything above this value will be rejected, {@code 1} means pipelining is disabled and requests/responses are
     * processed sequentially. Default value is {@code 1}.
     * <p>
     * <b>Note:</b> {@link HttpClient#reserveConnection reserved connections} will not be restricted by this setting.
     *
     * @param maxPipelinedRequests maximum number of pipelined requests to queue up
     * @return {@code this}
     */
    public H1ProtocolConfigBuilder maxPipelinedRequests(final int maxPipelinedRequests) {
        this.maxPipelinedRequests = ensurePositive(maxPipelinedRequests, "maxPipelinedRequests");
        return this;
    }

    /**
     * Sets the maximum length (size in bytes) of the HTTP
     * <a href="https://tools.ietf.org/html/rfc7230#section-3.1">start line</a> for an HTTP message.
     * <p>
     * Default value is {@value #DEFAULT_MAX_START_LINE_LENGTH} bytes.
     * <p>
     * <b>Note:</b> a decoder will close the connection with {@code TooLongFrameException} if the start line exceeds
     * this value.
     *
     * @param maxStartLineLength maximum length (size in bytes) of the HTTP
     * <a href="https://tools.ietf.org/html/rfc7230#section-3.1">start line</a> for an HTTP message
     * @return {@code this}
     */
    public H1ProtocolConfigBuilder maxStartLineLength(final int maxStartLineLength) {
        this.maxStartLineLength = ensurePositive(maxStartLineLength, "maxStartLineLength");
        return this;
    }

    /**
     * Sets the maximum total allowed length (size in bytes) of all HTTP
     * <a href="https://tools.ietf.org/html/rfc7230#section-3.2">header fields</a> or all
     * <a href="https://tools.ietf.org/html/rfc7230#section-4.1.2">trailer fields</a>.
     * <p>
     * This limit protects against memory exhaustion attacks where an attacker sends many small headers or trailers
     * that individually pass {@link #maxHeaderFieldLength(int) field validation} but collectively consume excessive
     * memory.
     * <p>
     * <b>Note:</b> a decoder will close the connection with {@code TooLongFrameException} if the total headers or
     * trailers block size exceeds this value.
     * <p>
     * This is an HTTP/1.x equivalent of HTTP/2's
     * <a href="https://tools.ietf.org/html/rfc7540#section-6.5.2">SETTINGS_MAX_HEADER_LIST_SIZE</a> that can be
     * configured via {@link Http2Settings#maxHeaderListSize()} for
     * {@link H2ProtocolConfigBuilder#initialSettings(Http2Settings)}.
     * <p>
     * The default value is {@value #DEFAULT_MAX_TOTAL_HEADER_FIELDS_LENGTH_VALUE} bytes. Users who unexpectedly hit the
     * default limit can temporarily (until they can adjust the limit via this method) set
     * {@code io.servicetalk.http.netty.temporaryDefaultMaxTotalHeaderFieldsLength} to a new value. However, this is a
     * temporary property that will be removed in the future releases.
     *
     * @param maxTotalHeaderFieldsLength maximum total allowed length (size in bytes) of all headers or all trailers
     * @return {@code this}
     * @see #maxHeaderFieldLength(int)
     * @see H2ProtocolConfigBuilder#initialSettings(Http2Settings)
     */
    public H1ProtocolConfigBuilder maxTotalHeaderFieldsLength(final int maxTotalHeaderFieldsLength) {
        this.maxTotalHeaderFieldsLength = ensurePositive(maxTotalHeaderFieldsLength, "maxTotalHeaderFieldsLength");
        return this;
    }

    /**
     * Sets the maximum length (size in bytes) of an individual HTTP
     * <a href="https://tools.ietf.org/html/rfc7230#section-3.2">header field</a> or
     * <a href="https://tools.ietf.org/html/rfc7230#section-4.1.2">trailer field</a> to parse.
     * <p>
     * Default value is {@value #DEFAULT_MAX_HEADER_FIELD_LENGTH} bytes.
     * <p>
     * <b>Note:</b> a decoder will close the connection with {@code TooLongFrameException} if the length of a header or
     * trailer field exceeds this value.
     *
     * @param maxHeaderFieldLength maximum length (size in bytes) of an individual HTTP
     * <a href="https://tools.ietf.org/html/rfc7230#section-3.2">header field</a> or
     * <a href="https://tools.ietf.org/html/rfc7230#section-4.1.2">trailer field</a> to parse
     * @return {@code this}
     * @see #maxTotalHeaderFieldsLength(int)
     */
    public H1ProtocolConfigBuilder maxHeaderFieldLength(final int maxHeaderFieldLength) {
        this.maxHeaderFieldLength = ensurePositive(maxHeaderFieldLength, "maxHeaderFieldLength");
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
        this.headersEncodedSizeEstimate = ensurePositive(headersEncodedSizeEstimate, "headersEncodedSizeEstimate");
        return this;
    }

    /**
     * Sets the value used to calculate an exponential moving average of the encoded size of the HTTP
     * <a href="https://tools.ietf.org/html/rfc7230#section-4.1.2">trailer fields</a> for a guess for future
     * buffer allocations.
     *
     * @param trailersEncodedSizeEstimate value used to calculate an exponential moving average of the encoded size of
     * the HTTP <a href="https://tools.ietf.org/html/rfc7230#section-4.1.2">trailer fields</a>
     * @return {@code this}
     */
    public H1ProtocolConfigBuilder trailersEncodedSizeEstimate(final int trailersEncodedSizeEstimate) {
        this.trailersEncodedSizeEstimate = ensurePositive(trailersEncodedSizeEstimate, "trailersEncodedSizeEstimate");
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
                maxHeaderFieldLength, headersEncodedSizeEstimate, trailersEncodedSizeEstimate, specExceptions,
                maxTotalHeaderFieldsLength);
    }

    private static final class DefaultH1ProtocolConfig implements H1ProtocolConfig {

        private final HttpHeadersFactory headersFactory;
        private final int maxPipelinedRequests;
        private final int maxStartLineLength;
        private final int maxHeaderFieldLength;
        private final int maxTotalHeaderFieldsLength;
        private final int headersEncodedSizeEstimate;
        private final int trailersEncodedSizeEstimate;
        private final H1SpecExceptions specExceptions;

        DefaultH1ProtocolConfig(final HttpHeadersFactory headersFactory, final int maxPipelinedRequests,
                                final int maxStartLineLength, final int maxHeaderFieldLength,
                                final int headersEncodedSizeEstimate, final int trailersEncodedSizeEstimate,
                                final H1SpecExceptions specExceptions, final int maxTotalHeaderFieldsLength) {
            this.headersFactory = headersFactory;
            this.maxPipelinedRequests = maxPipelinedRequests;
            this.maxStartLineLength = maxStartLineLength;
            this.maxHeaderFieldLength = maxHeaderFieldLength;
            this.headersEncodedSizeEstimate = headersEncodedSizeEstimate;
            this.trailersEncodedSizeEstimate = trailersEncodedSizeEstimate;
            this.specExceptions = specExceptions;
            this.maxTotalHeaderFieldsLength = maxTotalHeaderFieldsLength;
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
        public int maxTotalHeaderFieldsLength() {
            return maxTotalHeaderFieldsLength;
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
            return getClass().getSimpleName() +
                    "{alpnId=" + alpnId() +
                    ", headersFactory=" + headersFactory +
                    ", maxPipelinedRequests=" + maxPipelinedRequests +
                    ", maxStartLineLength=" + maxStartLineLength +
                    ", maxHeaderFieldLength=" + maxHeaderFieldLength +
                    ", maxTotalHeaderFieldsLength=" + maxTotalHeaderFieldsLength +
                    ", headersEncodedSizeEstimate=" + headersEncodedSizeEstimate +
                    ", trailersEncodedSizeEstimate=" + trailersEncodedSizeEstimate +
                    ", specExceptions=" + specExceptions +
                    '}';
        }
    }
}
