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

import static io.servicetalk.buffer.api.EmptyBuffer.EMPTY_BUFFER;
import static io.servicetalk.http.api.DefaultHttpHeadersFactory.INSTANCE;
import static io.servicetalk.http.api.HttpProtocolVersions.HTTP_1_1;

/**
 * Factory methods for creating {@link FullHttpRequest}s.
 */
public final class FullHttpRequests {

    private FullHttpRequests() {
        // No instances
    }

    /**
     * Create a new instance using HTTP 1.1 with empty payload body and headers.
     *
     * @param method the {@link HttpRequestMethod} of the request.
     * @param requestTarget the <a href="https://tools.ietf.org/html/rfc7230#section-3.1.1">request-target</a> of the
     * request.
     * @return a new {@link HttpRequest}.
     */
    public static FullHttpRequest newRequest(final HttpRequestMethod method,
                                             final String requestTarget) {
        return newRequest(HTTP_1_1, method, requestTarget);
    }

    /**
     * Create a new instance using HTTP 1.1 with empty payload body.
     *
     * @param method the {@link HttpRequestMethod} of the request.
     * @param requestTarget the <a href="https://tools.ietf.org/html/rfc7230#section-3.1.1">request-target</a> of the
     * request.
     * @param headers the {@link HttpHeaders} of the request.
     * @return a new {@link FullHttpRequest}.
     */
    public static FullHttpRequest newRequest(final HttpRequestMethod method,
                                             final String requestTarget,
                                             final HttpHeaders headers) {
        return newRequest(HTTP_1_1, method, requestTarget, headers);
    }

    /**
     * Create a new instance using HTTP 1.1 with empty payload body.
     *
     * @param method the {@link HttpRequestMethod} of the request.
     * @param requestTarget the <a href="https://tools.ietf.org/html/rfc7230#section-3.1.1">request-target</a> of the
     * request.
     * @param headers the {@link HttpHeaders} of the request.
     * @param trailers the trailing {@link HttpHeaders} of the request.
     * @return a new {@link FullHttpRequest}.
     */
    public static FullHttpRequest newRequest(final HttpRequestMethod method,
                                             final String requestTarget,
                                             final HttpHeaders headers,
                                             final HttpHeaders trailers) {
        return newRequest(HTTP_1_1, method, requestTarget, headers, trailers);
    }

    /**
     * Create a new instance with empty payload body and headers.
     *
     * @param version the {@link HttpProtocolVersion} of the request.
     * @param method the {@link HttpRequestMethod} of the request.
     * @param requestTarget the <a href="https://tools.ietf.org/html/rfc7230#section-3.1.1">request-target</a> of the
     * request.
     * @return a new {@link FullHttpRequest}.
     */
    public static FullHttpRequest newRequest(final HttpProtocolVersion version,
                                             final HttpRequestMethod method,
                                             final String requestTarget) {
        return newRequest(version, method, requestTarget, EMPTY_BUFFER);
    }

    /**
     * Create a new instance with empty payload body.
     *
     * @param version the {@link HttpProtocolVersion} of the request.
     * @param method the {@link HttpRequestMethod} of the request.
     * @param requestTarget the <a href="https://tools.ietf.org/html/rfc7230#section-3.1.1">request-target</a> of the
     * request.
     * @param headers the {@link HttpHeaders} of the request.
     * @return a new {@link FullHttpRequest}.
     */
    public static FullHttpRequest newRequest(final HttpProtocolVersion version,
                                                final HttpRequestMethod method,
                                                final String requestTarget,
                                                final HttpHeaders headers) {
        return newRequest(version, method, requestTarget, EMPTY_BUFFER, headers);
    }

    /**
     * Create a new instance with empty payload body.
     *
     * @param version the {@link HttpProtocolVersion} of the request.
     * @param method the {@link HttpRequestMethod} of the request.
     * @param requestTarget the <a href="https://tools.ietf.org/html/rfc7230#section-3.1.1">request-target</a> of the
     * request.
     * @param headers the {@link HttpHeaders} of the request.
     * @param trailers the trailing {@link HttpHeaders} of the request.
     * @return a new {@link FullHttpRequest}.
     */
    public static FullHttpRequest newRequest(final HttpProtocolVersion version,
                                                final HttpRequestMethod method,
                                                final String requestTarget,
                                                final HttpHeaders headers,
                                                final HttpHeaders trailers) {
        return newRequest(version, method, requestTarget, EMPTY_BUFFER, headers, trailers);
    }

    /**
     * Create a new instance using HTTP 1.1 with empty headers.
     *
     * @param method the {@link HttpRequestMethod} of the request.
     * @param requestTarget the <a href="https://tools.ietf.org/html/rfc7230#section-3.1.1">request-target</a> of the
     * request.
     * @param payloadBody the payload body of the request.
     * @return a new {@link FullHttpRequest}.
     */
    public static FullHttpRequest newRequest(final HttpRequestMethod method,
                                             final String requestTarget,
                                             final Buffer payloadBody) {
        return newRequest(HTTP_1_1, method, requestTarget, payloadBody);
    }

    /**
     * Create a new instance using HTTP 1.1.
     *
     * @param method the {@link HttpRequestMethod} of the request.
     * @param requestTarget the <a href="https://tools.ietf.org/html/rfc7230#section-3.1.1">request-target</a> of the
     * request.
     * @param payloadBody the payload body of the request.
     * @param headers the {@link HttpHeaders} of the request.
     * @return a new {@link FullHttpRequest}.
     */
    public static FullHttpRequest newRequest(final HttpRequestMethod method,
                                                final String requestTarget,
                                                final Buffer payloadBody,
                                                final HttpHeaders headers) {
        return newRequest(HTTP_1_1, method, requestTarget, payloadBody, headers);
    }

    /**
     * Create a new instance using HTTP 1.1.
     *
     * @param method the {@link HttpRequestMethod} of the request.
     * @param requestTarget the <a href="https://tools.ietf.org/html/rfc7230#section-3.1.1">request-target</a> of the
     * request.
     * @param payloadBody the payload body of the request.
     * @param headers the {@link HttpHeaders} of the request.
     * @param trailers the trailing {@link HttpHeaders} of the request.
     * @return a new {@link FullHttpRequest}.
     */
    public static FullHttpRequest newRequest(final HttpRequestMethod method,
                                                final String requestTarget,
                                                final Buffer payloadBody,
                                                final HttpHeaders headers,
                                                final HttpHeaders trailers) {
        return newRequest(HTTP_1_1, method, requestTarget, payloadBody, headers, trailers);
    }

    /**
     * Create a new instance with empty headers.
     *
     * @param version the {@link HttpProtocolVersion} of the request.
     * @param method the {@link HttpRequestMethod} of the request.
     * @param requestTarget the <a href="https://tools.ietf.org/html/rfc7230#section-3.1.1">request-target</a> of the
     * request.
     * @param payloadBody the payload body of the request.
     * @return a new {@link FullHttpRequest}.
     */
    public static FullHttpRequest newRequest(final HttpProtocolVersion version,
                                             final HttpRequestMethod method,
                                             final String requestTarget,
                                             final Buffer payloadBody) {
        return newRequest(version, method, requestTarget, payloadBody, INSTANCE.newHeaders());
    }


    /**
     * Create a new instance.
     *
     * @param version the {@link HttpProtocolVersion} of the request.
     * @param method the {@link HttpRequestMethod} of the request.
     * @param requestTarget the <a href="https://tools.ietf.org/html/rfc7230#section-3.1.1">request-target</a> of the
     * request.
     * @param payloadBody the payload body of the request.
     * @param headers the {@link HttpHeaders} of the request.
     * @return a new {@link FullHttpRequest}.
     */
    public static FullHttpRequest newRequest(final HttpProtocolVersion version,
                                                final HttpRequestMethod method,
                                                final String requestTarget,
                                                final Buffer payloadBody,
                                                final HttpHeaders headers) {
        return new DefaultFullHttpRequest(new DefaultHttpRequestMetaData(method, requestTarget, version, headers),
                payloadBody, INSTANCE.newEmptyTrailers());
    }

    /**
     * Create a new instance.
     *
     * @param version the {@link HttpProtocolVersion} of the request.
     * @param method the {@link HttpRequestMethod} of the request.
     * @param requestTarget the <a href="https://tools.ietf.org/html/rfc7230#section-3.1.1">request-target</a> of the
     * request.
     * @param payloadBody the payload body of the request.
     * @param headers the {@link HttpHeaders} of the request.
     * @param trailers the trailing {@link HttpHeaders} of the request.
     * @return a new {@link FullHttpRequest}.
     */
    public static FullHttpRequest newRequest(final HttpProtocolVersion version,
                                                final HttpRequestMethod method,
                                                final String requestTarget,
                                                final Buffer payloadBody,
                                                final HttpHeaders headers,
                                                final HttpHeaders trailers) {
        return new DefaultFullHttpRequest(new DefaultHttpRequestMetaData(method, requestTarget, version, headers),
                payloadBody, trailers);
    }
}
