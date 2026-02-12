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

import io.servicetalk.http.api.Http2Settings;
import io.servicetalk.http.api.HttpClient;
import io.servicetalk.http.api.HttpProtocolConfig;

import static io.servicetalk.http.netty.H1ProtocolConfigBuilder.defaultMaxTotalHeaderFieldsLength;

/**
 * Configuration for <a href="https://tools.ietf.org/html/rfc7230">HTTP/1.1</a> protocol.
 *
 * @see HttpProtocolConfigs#h1Default()
 */
public interface H1ProtocolConfig extends HttpProtocolConfig {

    @Override
    default String alpnId() {
        return AlpnIds.HTTP_1_1;
    }

    /**
     * Maximum number of pipelined HTTP requests to queue up.
     * <p>
     * Anything above this value will be rejected, {@code 1} means pipelining is disabled and requests/responses are
     * processed sequentially.
     * <p>
     * <b>Note:</b> {@link HttpClient#reserveConnection reserved connections} will not be restricted by this setting.
     *
     * @return maximum number of pipelined HTTP requests to queue up
     */
    int maxPipelinedRequests();

    /**
     * Maximum length (size in bytes) of the HTTP
     * <a href="https://tools.ietf.org/html/rfc7230#section-3.1">start line</a> for an HTTP message.
     * <p>
     * <b>Note:</b> a decoder will close the connection with {@code TooLongFrameException} if the start line exceeds
     * this value.
     *
     * @return maximum size of the HTTP <a href="https://tools.ietf.org/html/rfc7230#section-3.1">start line</a> for an
     * HTTP message
     */
    int maxStartLineLength();

    /**
     * Get the maximum total allowed length (size in bytes) of all HTTP
     * <a href="https://tools.ietf.org/html/rfc7230#section-3.2">header fields</a> or
     * <a href="https://tools.ietf.org/html/rfc7230#section-4.1.2">trailer fields</a> combined.
     * <p>
     * This limit protects against memory exhaustion attacks where an attacker sends many small headers or trailers
     * that individually pass {@link #maxHeaderFieldLength() field validation} but collectively consume excessive
     * memory.
     * <p>
     * <b>Note:</b> a decoder will close the connection with {@code TooLongFrameException} if the total headers or
     * trailers block size exceeds this value.
     * <p>
     * This is an HTTP/1.x equivalent of HTTP/2's
     * <a href="https://tools.ietf.org/html/rfc7540#section-6.5.2">SETTINGS_MAX_HEADER_LIST_SIZE</a>.
     *
     * @return maximum total allowed length (size in bytes) of all headers or trailers combined
     * @see #maxHeaderFieldLength()
     * @see Http2Settings#maxHeaderListSize() how to configure it for HTTP/2
     */
    default int maxTotalHeaderFieldsLength() { // TODO 0.43 - remove default method
        return defaultMaxTotalHeaderFieldsLength(maxHeaderFieldLength());
    }

    /**
     * Maximum length (size in bytes) of an individual HTTP
     * <a href="https://tools.ietf.org/html/rfc7230#section-3.2">header fields</a> or
     * <a href="https://tools.ietf.org/html/rfc7230#section-4.1.2">trailer fields</a> to parse.
     * <p>
     * <b>Note:</b> a decoder will close the connection with {@code TooLongFrameException} if the length of a header or
     * trailer field exceeds this value.
     *
     * @return maximum length (size in bytes) of HTTP
     * <a href="https://tools.ietf.org/html/rfc7230#section-3.2">header fields</a> or
     * <a href="https://tools.ietf.org/html/rfc7230#section-4.1.2">trailer fields</a> to parse
     * @see #maxTotalHeaderFieldsLength()
     */
    int maxHeaderFieldLength();

    /**
     * Value used to calculate an exponential moving average of the encoded size of the HTTP
     * <a href="https://tools.ietf.org/html/rfc7230#section-3.1">start line</a> and
     * <a href="https://tools.ietf.org/html/rfc7230#section-3.2">header fields</a> for a guess for future buffer
     * allocations.
     *
     * @return value used to calculate an exponential moving average of the encoded size of the HTTP
     * <a href="https://tools.ietf.org/html/rfc7230#section-3.1">start line</a> and
     * <a href="https://tools.ietf.org/html/rfc7230#section-3.2">header fields</a>
     */
    int headersEncodedSizeEstimate();

    /**
     * Value used to calculate an exponential moving average of the encoded size of the HTTP
     * <a href="https://tools.ietf.org/html/rfc7230#section-4.1.2">trailer fields</a> for a guess for future
     * buffer allocations.
     *
     * @return value used to calculate an exponential moving average of the encoded size of the HTTP
     * <a href="https://tools.ietf.org/html/rfc7230#section-4.1.2">trailer fields</a>
     */
    int trailersEncodedSizeEstimate();

    /**
     * Additional exceptions for <a href="https://tools.ietf.org/html/rfc7230">HTTP/1.1</a> specification.
     *
     * @return exceptions for <a href="https://tools.ietf.org/html/rfc7230">HTTP/1.1</a> specification
     */
    H1SpecExceptions specExceptions();
}
