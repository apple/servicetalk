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

import io.servicetalk.buffer.api.BufferAllocator;
import io.servicetalk.concurrent.api.Publisher;

import static io.servicetalk.http.api.DefaultPayloadInfo.forTransportReceive;
import static io.servicetalk.http.api.DefaultPayloadInfo.forUserCreated;

/**
 * Factory methods for creating {@link StreamingHttpResponse}s.
 */
public final class StreamingHttpResponses {
    private StreamingHttpResponses() {
        // No instances
    }

    /**
     * Create a new instance using HTTP 1.1 with empty payload body.
     *
     * @param status the {@link HttpResponseStatus} of the response.
     * @param version the {@link HttpProtocolVersion} of the response.
     * @param headers the {@link HttpHeaders} of the response.
     * @param allocator the allocator used for serialization purposes if necessary.
     * @param headersFactory {@link HttpHeadersFactory} to use.
     * @return a new {@link StreamingHttpResponse}.
     */
    public static StreamingHttpResponse newResponse(
            final HttpResponseStatus status, final HttpProtocolVersion version, final HttpHeaders headers,
            final BufferAllocator allocator, final HttpHeadersFactory headersFactory) {
        return new DefaultStreamingHttpResponse(status, version, headers, allocator, null, forUserCreated(),
                headersFactory);
    }

    /**
     * Creates a new {@link StreamingHttpResponse} which is read from the transport. If the response contains
     * <a href="https://tools.ietf.org/html/rfc7230#section-4.4">trailers</a> then the passed {@code payload}
     * {@link Publisher} should also emit {@link HttpHeaders} before completion.
     *
     * @param status the {@link HttpResponseStatus} of the response.
     * @param version the {@link HttpProtocolVersion} of the response.
     * @param headers the {@link HttpHeaders} of the response.
     * @param allocator the allocator used for serialization purposes if necessary.
     * @param messageBody a {@link Publisher} for payload that optionally emits {@link HttpHeaders} if the
     * response contains <a href="https://tools.ietf.org/html/rfc7230#section-4.4">trailers</a>.
     * @param requireTrailerHeader {@code true} if <a href="https://tools.ietf.org/html/rfc7230#section-4.4">Trailer</a>
     * header is required to accept trailers. {@code false} assumes trailers may be present if other criteria allows.
     * @param headersFactory {@link HttpHeadersFactory} to use.
     * @return a new {@link StreamingHttpResponse}.
     */
    public static StreamingHttpResponse newTransportResponse(
            final HttpResponseStatus status, final HttpProtocolVersion version, final HttpHeaders headers,
            final BufferAllocator allocator, final Publisher<Object> messageBody,
            final boolean requireTrailerHeader, final HttpHeadersFactory headersFactory) {
        return new DefaultStreamingHttpResponse(status, version, headers, allocator, messageBody,
                forTransportReceive(requireTrailerHeader, version, headers), headersFactory);
    }
}
