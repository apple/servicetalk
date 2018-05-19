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
import static io.servicetalk.http.api.HttpPayloadChunks.newPayloadChunk;
import static io.servicetalk.http.api.HttpProtocolVersions.HTTP_1_1;

/**
 * Factory methods for creating {@link AggregatedHttpResponse}s.
 */
public final class AggregatedHttpResponses {

    private AggregatedHttpResponses() {
        // No instances
    }

    /**
     * Create a new instance using HTTP 1.1 with empty payload body and headers.
     *
     * @param status the {@link HttpResponseStatus} of the response.
     * @return a new {@link AggregatedHttpResponse}.
     */
    public static AggregatedHttpResponse<HttpPayloadChunk> newResponse(final HttpResponseStatus status) {
        return newResponse(HTTP_1_1, status);
    }

    /**
     * Create a new instance using HTTP 1.1 with empty payload body.
     *
     * @param status the {@link HttpResponseStatus} of the response.
     * @param headers the {@link HttpHeaders} of the response.
     * @return a new {@link AggregatedHttpResponse}.
     */
    public static AggregatedHttpResponse<HttpPayloadChunk> newResponse(final HttpResponseStatus status,
                                                                       final HttpHeaders headers) {
        return newResponse(HTTP_1_1, status, headers);
    }

    /**
     * Create a new instance using HTTP 1.1 with empty payload body.
     *
     * @param status the {@link HttpResponseStatus} of the response.
     * @param headers the {@link HttpHeaders} of the response.
     * @param trailers the trailing {@link HttpHeaders} of the response.
     * @return a new {@link AggregatedHttpResponse}.
     */
    public static AggregatedHttpResponse<HttpPayloadChunk> newResponse(final HttpResponseStatus status,
                                                                       final HttpHeaders headers,
                                                                       final HttpHeaders trailers) {
        return newResponse(HTTP_1_1, status, headers, trailers);
    }

    /**
     * Create a new instance with empty payload body and headers.
     *
     * @param version the {@link HttpProtocolVersion} of the response.
     * @param status the {@link HttpResponseStatus} of the response.
     * @return a new {@link AggregatedHttpResponse}.
     */
    public static AggregatedHttpResponse<HttpPayloadChunk> newResponse(final HttpProtocolVersion version,
                                                                       final HttpResponseStatus status) {
        return newResponse(version, status, EMPTY_BUFFER);
    }

    /**
     * Create a new instance with empty payload body.
     *
     * @param version the {@link HttpProtocolVersion} of the response.
     * @param status the {@link HttpResponseStatus} of the response.
     * @param headers the {@link HttpHeaders} of the response.
     * @return a new {@link AggregatedHttpResponse}.
     */
    public static AggregatedHttpResponse<HttpPayloadChunk> newResponse(final HttpProtocolVersion version,
                                                                       final HttpResponseStatus status,
                                                                       final HttpHeaders headers) {
        return newResponse(version, status, EMPTY_BUFFER, headers);
    }

    /**
     * Create a new instance with empty payload body.
     *
     * @param version the {@link HttpProtocolVersion} of the response.
     * @param status the {@link HttpResponseStatus} of the response.
     * @param headers the {@link HttpHeaders} of the response.
     * @param trailers the trailing {@link HttpHeaders} of the response.
     * @return a new {@link AggregatedHttpResponse}.
     */
    public static AggregatedHttpResponse<HttpPayloadChunk> newResponse(final HttpProtocolVersion version,
                                                                       final HttpResponseStatus status,
                                                                       final HttpHeaders headers,
                                                                       final HttpHeaders trailers) {
        return newResponse(version, status, EMPTY_BUFFER, headers, trailers);
    }

    /**
     * Create a new instance using HTTP 1.1 with empty headers.
     *
     * @param status the {@link HttpResponseStatus} of the response.
     * @param payloadBody the payload body of the response.
     * @return a new {@link AggregatedHttpResponse}.
     */
    public static AggregatedHttpResponse<HttpPayloadChunk> newResponse(final HttpResponseStatus status,
                                                                       final Buffer payloadBody) {
        return newResponse(HTTP_1_1, status, payloadBody);
    }

    /**
     * Create a new instance using HTTP 1.1 with empty headers.
     *
     * @param status the {@link HttpResponseStatus} of the response.
     * @param payloadBody the payload body of the response.
     * @param <T> The data type of the response.
     * @return a new {@link AggregatedHttpResponse}.
     */
    public static <T> AggregatedHttpResponse<T> newResponse(final HttpResponseStatus status,
                                                            final T payloadBody) {
        return newResponse(HTTP_1_1, status, payloadBody);
    }

    /**
     * Create a new instance using HTTP 1.1.
     *
     * @param status the {@link HttpResponseStatus} of the response.
     * @param payloadBody the payload body of the response.
     * @param headers the {@link HttpHeaders} of the response.
     * @return a new {@link AggregatedHttpResponse}.
     */
    public static AggregatedHttpResponse<HttpPayloadChunk> newResponse(final HttpResponseStatus status,
                                                                       final Buffer payloadBody,
                                                                       final HttpHeaders headers) {
        return newResponse(HTTP_1_1, status, payloadBody, headers);
    }

    /**
     * Create a new instance using HTTP 1.1.
     *
     * @param status the {@link HttpResponseStatus} of the response.
     * @param payloadBody the payload body of the response.
     * @param headers the {@link HttpHeaders} of the response.
     * @param <T> The data type of the response.
     * @return a new {@link AggregatedHttpResponse}.
     */
    public static <T> AggregatedHttpResponse<T> newResponse(final HttpResponseStatus status,
                                                            final T payloadBody,
                                                            final HttpHeaders headers) {
        return newResponse(HTTP_1_1, status, payloadBody, headers);
    }

    /**
     * Create a new instance using HTTP 1.1.
     *
     * @param status the {@link HttpResponseStatus} of the response.
     * @param payloadBody the payload body of the response.
     * @param headers the {@link HttpHeaders} of the response.
     * @param trailers the trailing {@link HttpHeaders} of the response.
     * @return a new {@link AggregatedHttpResponse}.
     */
    public static AggregatedHttpResponse<HttpPayloadChunk> newResponse(final HttpResponseStatus status,
                                                                       final Buffer payloadBody,
                                                                       final HttpHeaders headers,
                                                                       final HttpHeaders trailers) {
        return newResponse(HTTP_1_1, status, payloadBody, headers, trailers);
    }

    /**
     * Create a new instance using HTTP 1.1.
     *
     * @param status the {@link HttpResponseStatus} of the response.
     * @param payloadBody the payload body of the response.
     * @param headers the {@link HttpHeaders} of the response.
     * @param trailers the trailing {@link HttpHeaders} of the response.
     * @param <T> The data type of the response.
     * @return a new {@link AggregatedHttpResponse}.
     */
    public static <T> AggregatedHttpResponse<T> newResponse(final HttpResponseStatus status,
                                                            final T payloadBody,
                                                            final HttpHeaders headers,
                                                            final HttpHeaders trailers) {
        return newResponse(HTTP_1_1, status, payloadBody, headers, trailers);
    }

    /**
     * Create a new instance with empty headers.
     *
     * @param version the {@link HttpProtocolVersion} of the response.
     * @param status the {@link HttpResponseStatus} of the response.
     * @param payloadBody the payload body of the response.
     * @return a new {@link AggregatedHttpResponse}.
     */
    public static AggregatedHttpResponse<HttpPayloadChunk> newResponse(final HttpProtocolVersion version,
                                                                       final HttpResponseStatus status,
                                                                       final Buffer payloadBody) {
        return newResponse(version, status, payloadBody, INSTANCE.newHeaders());
    }

    /**
     * Create a new instance with empty headers.
     *
     * @param version the {@link HttpProtocolVersion} of the response.
     * @param status the {@link HttpResponseStatus} of the response.
     * @param payloadBody the payload body of the response.
     * @param <T> The data type of the response.
     * @return a new {@link AggregatedHttpResponse}.
     */
    public static <T> AggregatedHttpResponse<T> newResponse(final HttpProtocolVersion version,
                                                            final HttpResponseStatus status,
                                                            final T payloadBody) {
        return newResponse(version, status, payloadBody, INSTANCE.newHeaders());
    }

    /**
     * Create a new instance.
     *
     * @param version the {@link HttpProtocolVersion} of the response.
     * @param status the {@link HttpResponseStatus} of the response.
     * @param payloadBody the payload body of the response.
     * @param headers the {@link HttpHeaders} of the response.
     * @return a new {@link AggregatedHttpResponse}.
     */
    public static AggregatedHttpResponse<HttpPayloadChunk> newResponse(final HttpProtocolVersion version,
                                                                       final HttpResponseStatus status,
                                                                       final Buffer payloadBody,
                                                                       final HttpHeaders headers) {
        return newResponse(version, status, payloadBody, headers, INSTANCE.newEmptyTrailers());
    }

    /**
     * Create a new instance.
     *
     * @param version the {@link HttpProtocolVersion} of the response.
     * @param status the {@link HttpResponseStatus} of the response.
     * @param payloadBody the payload body of the response.
     * @param headers the {@link HttpHeaders} of the response.
     * @param <T> The data type of the response.
     * @return a new {@link AggregatedHttpResponse}.
     */
    public static <T> AggregatedHttpResponse<T> newResponse(final HttpProtocolVersion version,
                                                            final HttpResponseStatus status,
                                                            final T payloadBody,
                                                            final HttpHeaders headers) {
        return newResponse(version, status, payloadBody, headers, INSTANCE.newEmptyTrailers());
    }

    /**
     * Create a new instance.
     *
     * @param version the {@link HttpProtocolVersion} of the response.
     * @param status the {@link HttpResponseStatus} of the response.
     * @param payloadBody the payload body of the response.
     * @param headers the {@link HttpHeaders} of the response.
     * @param trailers the trailing {@link HttpHeaders} of the response.
     * @return a new {@link AggregatedHttpResponse}.
     */
    public static AggregatedHttpResponse<HttpPayloadChunk> newResponse(final HttpProtocolVersion version,
                                                                       final HttpResponseStatus status,
                                                                       final Buffer payloadBody,
                                                                       final HttpHeaders headers,
                                                                       final HttpHeaders trailers) {
        return new DefaultAggregatedHttpResponse<>(new DefaultHttpResponseMetaData(status, version, headers),
                newPayloadChunk(payloadBody), trailers);
    }

    /**
     * Create a new instance.
     *
     * @param version the {@link HttpProtocolVersion} of the response.
     * @param status the {@link HttpResponseStatus} of the response.
     * @param payloadBody the payload body of the response.
     * @param headers the {@link HttpHeaders} of the response.
     * @param trailers the trailing {@link HttpHeaders} of the response.
     * @param <T> The data type of the response.
     * @return a new {@link AggregatedHttpResponse}.
     */
    public static <T> AggregatedHttpResponse<T> newResponse(final HttpProtocolVersion version,
                                                            final HttpResponseStatus status,
                                                            final T payloadBody,
                                                            final HttpHeaders headers,
                                                            final HttpHeaders trailers) {
        return new DefaultAggregatedHttpResponse<>(new DefaultHttpResponseMetaData(status, version, headers),
                payloadBody, trailers);
    }
}
