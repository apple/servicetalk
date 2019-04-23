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

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.buffer.api.BufferAllocator;
import io.servicetalk.concurrent.api.Publisher;

import static io.servicetalk.concurrent.api.Publisher.empty;

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
     * @param initialTrailers the initial state of the
     * <a href="https://tools.ietf.org/html/rfc7230#section-4.4">trailers</a> for this response.
     * @param allocator the allocator used for serialization purposes if necessary.
     * @return a new {@link StreamingHttpResponse}.
     */
    public static StreamingHttpResponse newResponse(
            final HttpResponseStatus status, final HttpProtocolVersion version, final HttpHeaders headers,
            final HttpHeaders initialTrailers, final BufferAllocator allocator) {
        return newResponse(status, version, headers, initialTrailers, allocator, empty());
    }

    /**
     * Create a new instance using HTTP 1.1 with empty payload body.
     *
     * @param status the {@link HttpResponseStatus} of the response.
     * @param version the {@link HttpProtocolVersion} of the response.
     * @param headers the {@link HttpHeaders} of the response.
     * @param initialTrailers the initial state of the
     * <a href="https://tools.ietf.org/html/rfc7230#section-4.4">trailers</a> for this response.
     * @param allocator the allocator used for serialization purposes if necessary.
     * @param payloadBody the payload body {@link Publisher}.
     * @return a new {@link StreamingHttpResponse}.
     */
    public static StreamingHttpResponse newResponse(
            final HttpResponseStatus status, final HttpProtocolVersion version, final HttpHeaders headers,
            final HttpHeaders initialTrailers, final BufferAllocator allocator, Publisher<Buffer> payloadBody) {
        return new BufferStreamingHttpResponse(status, version, headers, initialTrailers, allocator, payloadBody,
                false);
    }

    /**
     * Create a new instance using HTTP 1.1 with empty payload body.
     *
     * @param status the {@link HttpResponseStatus} of the response.
     * @param version the {@link HttpProtocolVersion} of the response.
     * @param headers the {@link HttpHeaders} of the response.
     * @param allocator the allocator used for serialization purposes if necessary.
     * @param payloadAndTrailers a {@link Publisher} of the form [&lt;payload chunk&gt;* {@link HttpHeaders}].
     * @return a new {@link StreamingHttpResponse}.
     */
    public static StreamingHttpResponse newResponseWithTrailers(
            final HttpResponseStatus status, final HttpProtocolVersion version, final HttpHeaders headers,
            final BufferAllocator allocator, final Publisher<Object> payloadAndTrailers) {
        return new TransportStreamingHttpResponse(status, version, headers, allocator, payloadAndTrailers, false);
    }
}
