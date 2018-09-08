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
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;

import static io.servicetalk.concurrent.api.Publisher.empty;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.api.Publisher.just;
import static io.servicetalk.http.api.DefaultHttpHeadersFactory.INSTANCE;
import static io.servicetalk.http.api.HttpPayloadChunks.newLastPayloadChunk;
import static io.servicetalk.http.api.HttpProtocolVersions.HTTP_1_1;

/**
 * Factory methods for creating {@link StreamingHttpResponse}s.
 */
public final class StreamingHttpResponses {

    private StreamingHttpResponses() {
        // No instances
    }

    /**
     * Create a new instance using HTTP 1.1 with empty payload body and headers.
     *
     * @param status the {@link HttpResponseStatus} of the response.
     * @param <O> Type of the content of the response.
     * @return a new {@link StreamingHttpResponse}.
     */
    public static <O> StreamingHttpResponse<O> newResponse(final HttpResponseStatus status) {
        return newResponse(HTTP_1_1, status);
    }

    /**
     * Create a new instance using HTTP 1.1 with empty payload body.
     *
     * @param status the {@link HttpResponseStatus} of the response.
     * @param headers the {@link HttpHeaders} of the response.
     * @param <O> Type of the content of the response.
     * @return a new {@link StreamingHttpResponse}.
     */
    public static <O> StreamingHttpResponse<O> newResponse(final HttpResponseStatus status,
                                                           final HttpHeaders headers) {
        return newResponse(HTTP_1_1, status, headers);
    }

    /**
     * Create a new instance with empty payload body and headers.
     *
     * @param version the {@link HttpProtocolVersion} of the response.
     * @param status the {@link HttpResponseStatus} of the response.
     * @param <O> Type of the content of the response.
     * @return a new {@link StreamingHttpResponse}.
     */
    public static <O> StreamingHttpResponse<O> newResponse(final HttpProtocolVersion version,
                                                           final HttpResponseStatus status) {
        return newResponse(version, status, empty());
    }

    /**
     * Create a new instance with empty payload body.
     *
     * @param version the {@link HttpProtocolVersion} of the response.
     * @param status the {@link HttpResponseStatus} of the response.
     * @param headers the {@link HttpHeaders} of the response.
     * @param <O> Type of the content of the response.
     * @return a new {@link StreamingHttpResponse}.
     */
    public static <O> StreamingHttpResponse<O> newResponse(final HttpProtocolVersion version,
                                                           final HttpResponseStatus status,
                                                           final HttpHeaders headers) {
        return newResponse(version, status, empty(), headers);
    }

    /**
     * Create a new instance using HTTP 1.1 with empty headers.
     *
     * @param status the {@link HttpResponseStatus} of the response.
     * @param payloadBody the payload body of the response.
     * @param <O> Type of the content of the response.
     * @return a new {@link StreamingHttpResponse}.
     */
    public static <O> StreamingHttpResponse<O> newResponse(final HttpResponseStatus status,
                                                           final O payloadBody) {
        return newResponse(HTTP_1_1, status, payloadBody);
    }

    /**
     * Create a new instance using HTTP 1.1 with empty headers.
     *
     * @param status the {@link HttpResponseStatus} of the response.
     * @param payloadBody the payload body of the response.
     * @return a new {@link StreamingHttpResponse}.
     */
    public static StreamingHttpResponse<HttpPayloadChunk> newResponse(final HttpResponseStatus status,
                                                                      final Buffer payloadBody) {
        return newResponse(HTTP_1_1, status, payloadBody);
    }

    /**
     * Create a new instance using HTTP 1.1.
     *
     * @param status the {@link HttpResponseStatus} of the response.
     * @param payloadBody the payload body of the response.
     * @param headers the {@link HttpHeaders} of the response.
     * @param <O> Type of the content of the response.
     * @return a new {@link StreamingHttpResponse}.
     */
    public static <O> StreamingHttpResponse<O> newResponse(final HttpResponseStatus status,
                                                           final O payloadBody,
                                                           final HttpHeaders headers) {
        return newResponse(HTTP_1_1, status, payloadBody, headers);
    }

    /**
     * Create a new instance using HTTP 1.1.
     *
     * @param status the {@link HttpResponseStatus} of the response.
     * @param payloadBody the payload body of the response.
     * @param headers the {@link HttpHeaders} of the response.
     * @return a new {@link StreamingHttpResponse}.
     */
    public static StreamingHttpResponse<HttpPayloadChunk> newResponse(final HttpResponseStatus status,
                                                                      final Buffer payloadBody,
                                                                      final HttpHeaders headers) {
        return newResponse(HTTP_1_1, status, payloadBody, headers);
    }

    /**
     * Create a new instance with empty headers.
     *
     * @param <O> Type of the content of the response.
     * @param version the {@link HttpProtocolVersion} of the response.
     * @param status the {@link HttpResponseStatus} of the response.
     * @param payloadBody the payload body of the response.
     * @return a new {@link StreamingHttpResponse}.
     */
    public static <O> StreamingHttpResponse<O> newResponse(final HttpProtocolVersion version,
                                                           final HttpResponseStatus status,
                                                           final O payloadBody) {
        return newResponse(version, status, just(payloadBody));
    }

    /**
     * Create a new instance with empty headers.
     *
     * @param version the {@link HttpProtocolVersion} of the response.
     * @param status the {@link HttpResponseStatus} of the response.
     * @param payloadBody the payload body of the response.
     * @return a new {@link StreamingHttpResponse}.
     */
    public static StreamingHttpResponse<HttpPayloadChunk> newResponse(final HttpProtocolVersion version,
                                                                      final HttpResponseStatus status,
                                                                      final Buffer payloadBody) {
        return newResponse(version, status, just(newLastPayloadChunk(payloadBody, INSTANCE.newEmptyTrailers())));
    }

    /**
     * Create a new instance.
     *
     * @param <O> Type of the content of the response.
     * @param version the {@link HttpProtocolVersion} of the response.
     * @param status the {@link HttpResponseStatus} of the response.
     * @param payloadBody the payload body of the response.
     * @param headers the {@link HttpHeaders} of the response.
     * @return a new {@link StreamingHttpResponse}.
     */
    public static <O> StreamingHttpResponse<O> newResponse(final HttpProtocolVersion version,
                                                           final HttpResponseStatus status,
                                                           final O payloadBody,
                                                           final HttpHeaders headers) {
        return newResponse(version, status, just(payloadBody), headers);
    }

    /**
     * Create a new instance.
     *
     * @param version the {@link HttpProtocolVersion} of the response.
     * @param status the {@link HttpResponseStatus} of the response.
     * @param payloadBody the payload body of the response.
     * @param headers the {@link HttpHeaders} of the response.
     * @return a new {@link StreamingHttpResponse}.
     */
    public static StreamingHttpResponse<HttpPayloadChunk> newResponse(final HttpProtocolVersion version,
                                                                      final HttpResponseStatus status,
                                                                      final Buffer payloadBody,
                                                                      final HttpHeaders headers) {
        return newResponse(version, status, just(newLastPayloadChunk(payloadBody, INSTANCE.newEmptyTrailers())),
                headers);
    }

    /**
     * Create a new instance using HTTP 1.1 with empty headers.
     *
     * @param status the {@link HttpResponseStatus} of the response.
     * @param payloadBody a {@link Single} of the payload body of the response.
     * @param <O> Type of the content of the response.
     * @return a new {@link StreamingHttpResponse}.
     */
    public static <O> StreamingHttpResponse<O> newResponse(final HttpResponseStatus status,
                                                           final Single<O> payloadBody) {
        return newResponse(HTTP_1_1, status, payloadBody);
    }

    /**
     * Create a new instance using HTTP 1.1 with empty headers.
     *
     * @param status the {@link HttpResponseStatus} of the response.
     * @param payloadBody a {@link Single} of the payload body of the response.
     * @return a new {@link StreamingHttpResponse}.
     */
    public static StreamingHttpResponse<HttpPayloadChunk> newResponseFromBuffer(final HttpResponseStatus status,
                                                                                final Single<Buffer> payloadBody) {
        return newResponseFromBuffer(HTTP_1_1, status, payloadBody);
    }

    /**
     * Create a new instance using HTTP 1.1.
     *
     * @param status the {@link HttpResponseStatus} of the response.
     * @param payloadBody a {@link Single} of the payload body of the response.
     * @param headers the {@link HttpHeaders} of the response.
     * @param <O> Type of the content of the response.
     * @return a new {@link StreamingHttpResponse}.
     */
    public static <O> StreamingHttpResponse<O> newResponse(final HttpResponseStatus status,
                                                           final Single<O> payloadBody,
                                                           final HttpHeaders headers) {
        return newResponse(HTTP_1_1, status, payloadBody, headers);
    }

    /**
     * Create a new instance using HTTP 1.1.
     *
     * @param status the {@link HttpResponseStatus} of the response.
     * @param payloadBody a {@link Single} of the payload body of the response.
     * @param headers the {@link HttpHeaders} of the response.
     * @return a new {@link StreamingHttpResponse}.
     */
    public static StreamingHttpResponse<HttpPayloadChunk> newResponseFromBuffer(final HttpResponseStatus status,
                                                                                final Single<Buffer> payloadBody,
                                                                                final HttpHeaders headers) {
        return newResponseFromBuffer(HTTP_1_1, status, payloadBody, headers);
    }

    /**
     * Create a new instance with empty headers.
     *
     * @param status the {@link HttpResponseStatus} of the response.
     * @param version the {@link HttpProtocolVersion} of the response.
     * @param payloadBody a {@link Single} of the payload body of the response.
     * @param <O> Type of the content of the response.
     * @return a new {@link StreamingHttpResponse}.
     */
    public static <O> StreamingHttpResponse<O> newResponse(final HttpProtocolVersion version,
                                                           final HttpResponseStatus status,
                                                           final Single<O> payloadBody) {
        return newResponse(version, status, payloadBody, INSTANCE.newHeaders());
    }

    /**
     * Create a new instance with empty headers.
     *
     * @param status the {@link HttpResponseStatus} of the response.
     * @param version the {@link HttpProtocolVersion} of the response.
     * @param payloadBody a {@link Single} of the payload body of the response.
     * @return a new {@link StreamingHttpResponse}.
     */
    public static StreamingHttpResponse<HttpPayloadChunk> newResponseFromBuffer(final HttpProtocolVersion version,
                                                                                final HttpResponseStatus status,
                                                                                final Single<Buffer> payloadBody) {
        return newResponseFromBuffer(version, status, payloadBody, INSTANCE.newHeaders());
    }

    /**
     * Create a new instance.
     *
     * @param version the {@link HttpProtocolVersion} of the response.
     * @param status the {@link HttpResponseStatus} of the response.
     * @param payloadBody a {@link Single} of the payload body of the response.
     * @param headers the {@link HttpHeaders} of the response.
     * @param <O> Type of the content of the response.
     * @return a new {@link StreamingHttpResponse}.
     */
    public static <O> StreamingHttpResponse<O> newResponse(final HttpProtocolVersion version,
                                                           final HttpResponseStatus status,
                                                           final Single<O> payloadBody,
                                                           final HttpHeaders headers) {
        return new DefaultStreamingHttpResponse<>(status, version, headers, payloadBody.toPublisher());
    }

    /**
     * Create a new instance.
     *
     * @param version the {@link HttpProtocolVersion} of the response.
     * @param status the {@link HttpResponseStatus} of the response.
     * @param payloadBody a {@link Single} of the payload body of the response.
     * @param headers the {@link HttpHeaders} of the response.
     * @return a new {@link StreamingHttpResponse}.
     */
    public static StreamingHttpResponse<HttpPayloadChunk> newResponseFromBuffer(final HttpProtocolVersion version,
                                                                                final HttpResponseStatus status,
                                                                                final Single<Buffer> payloadBody,
                                                                                final HttpHeaders headers) {
        return new DefaultStreamingHttpResponse<>(status, version, headers, payloadBody.toPublisher()
                .map(buf -> newLastPayloadChunk(buf, INSTANCE.newEmptyTrailers())));
    }

    /**
     * Create a new instance using HTTP 1.1 with empty headers.
     *
     * @param status the {@link HttpResponseStatus} of the response.
     * @param payloadBody a {@link Publisher} of the payload body of the response.
     * @param <O> Type of the content of the response.
     * @return a new {@link StreamingHttpResponse}.
     */
    public static <O> StreamingHttpResponse<O> newResponse(final HttpResponseStatus status,
                                                           final Publisher<O> payloadBody) {
        return newResponse(HTTP_1_1, status, payloadBody);
    }

    /**
     * Create a new instance using HTTP 1.1 with empty headers.
     *
     * @param status the {@link HttpResponseStatus} of the response.
     * @param payloadBody a {@link Publisher} of the payload body of the response.
     * @return a new {@link StreamingHttpResponse}.
     */
    public static StreamingHttpResponse<HttpPayloadChunk> newResponseFromBuffer(final HttpResponseStatus status,
                                                                                final Publisher<Buffer> payloadBody) {
        return newResponseFromBuffer(HTTP_1_1, status, payloadBody);
    }

    /**
     * Create a new instance using HTTP 1.1.
     *
     * @param status the {@link HttpResponseStatus} of the response.
     * @param payloadBody a {@link Publisher} of the payload body of the response.
     * @param headers the {@link HttpHeaders} of the response.
     * @param <O> Type of the content of the response.
     * @return a new {@link StreamingHttpResponse}.
     */
    public static <O> StreamingHttpResponse<O> newResponse(final HttpResponseStatus status,
                                                           final Publisher<O> payloadBody,
                                                           final HttpHeaders headers) {
        return newResponse(HTTP_1_1, status, payloadBody, headers);
    }

    /**
     * Create a new instance using HTTP 1.1.
     *
     * @param status the {@link HttpResponseStatus} of the response.
     * @param payloadBody a {@link Publisher} of the payload body of the response.
     * @param headers the {@link HttpHeaders} of the response.
     * @return a new {@link StreamingHttpResponse}.
     */
    public static StreamingHttpResponse<HttpPayloadChunk> newResponseFromBuffer(final HttpResponseStatus status,
                                                                                final Publisher<Buffer> payloadBody,
                                                                                final HttpHeaders headers) {
        return newResponseFromBuffer(HTTP_1_1, status, payloadBody, headers);
    }

    /**
     * Create a new instance with empty headers.
     *
     * @param status the {@link HttpResponseStatus} of the response.
     * @param version the {@link HttpProtocolVersion} of the response.
     * @param payloadBody a {@link Publisher} of the payload body of the response.
     * @param <O> Type of the content of the response.
     * @return a new {@link StreamingHttpResponse}.
     */
    public static <O> StreamingHttpResponse<O> newResponse(final HttpProtocolVersion version,
                                                           final HttpResponseStatus status,
                                                           final Publisher<O> payloadBody) {
        return newResponse(version, status, payloadBody, INSTANCE.newHeaders());
    }

    /**
     * Create a new instance with empty headers.
     *
     * @param status the {@link HttpResponseStatus} of the response.
     * @param version the {@link HttpProtocolVersion} of the response.
     * @param payloadBody a {@link Publisher} of the payload body of the response.
     * @return a new {@link StreamingHttpResponse}.
     */
    public static StreamingHttpResponse<HttpPayloadChunk> newResponseFromBuffer(final HttpProtocolVersion version,
                                                                                final HttpResponseStatus status,
                                                                                final Publisher<Buffer> payloadBody) {
        return newResponseFromBuffer(version, status, payloadBody, INSTANCE.newHeaders());
    }

    /**
     * Create a new instance.
     *
     * @param version the {@link HttpProtocolVersion} of the response.
     * @param status the {@link HttpResponseStatus} of the response.
     * @param payloadBody a {@link Publisher} of the payload body of the response.
     * @param headers the {@link HttpHeaders} of the response.
     * @param <O> Type of the content of the response.
     * @return a new {@link StreamingHttpResponse}.
     */
    public static <O> StreamingHttpResponse<O> newResponse(final HttpProtocolVersion version,
                                                           final HttpResponseStatus status,
                                                           final Publisher<O> payloadBody,
                                                           final HttpHeaders headers) {
        return new DefaultStreamingHttpResponse<>(status, version, headers, payloadBody);
    }

    /**
     * Create a new instance.
     *
     * @param version the {@link HttpProtocolVersion} of the response.
     * @param status the {@link HttpResponseStatus} of the response.
     * @param payloadBody a {@link Publisher} of the payload body of the response.
     * @param headers the {@link HttpHeaders} of the response.
     * @return a new {@link StreamingHttpResponse}.
     */
    public static StreamingHttpResponse<HttpPayloadChunk> newResponseFromBuffer(final HttpProtocolVersion version,
                                                                                final HttpResponseStatus status,
                                                                                final Publisher<Buffer> payloadBody,
                                                                                final HttpHeaders headers) {
        return new DefaultStreamingHttpResponse<>(
                status, version, headers, payloadBody.map(HttpPayloadChunks::newPayloadChunk));
    }

    static <O> StreamingHttpResponse<O> fromBlockingResponse(BlockingStreamingHttpResponse<O> response) {
        return new DefaultStreamingHttpResponse<>(response.getStatus(), response.getVersion(), response.getHeaders(),
                // The from(..) operator will take care of propagating cancel.
                from(response.getPayloadBody()));
    }
}
