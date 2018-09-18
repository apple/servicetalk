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
import io.servicetalk.buffer.api.BufferAllocator;
import io.servicetalk.concurrent.BlockingIterable;

import static io.servicetalk.concurrent.internal.BlockingIterables.emptyBlockingIterable;

/**
 * Factory methods for creating {@link BlockingStreamingHttpRequest}s.
 */
public final class BlockingStreamingHttpRequests {
    private BlockingStreamingHttpRequests() {
        // no instances
    }

    /**
     * Create a new instance using HTTP 1.1 with empty payload body.
     *
     * @param method the {@link HttpRequestMethod} of the request.
     * @param requestTarget the <a href="https://tools.ietf.org/html/rfc7230#section-3.1.1">request-target</a> of the
     * request.
     * @param version the {@link HttpProtocolVersion} of the request.
     * @param headers the {@link HttpHeaders} of the request.
     * @param initialTrailers the initial state of the
     * <a href="https://tools.ietf.org/html/rfc7230#section-4.4">trailers</a> for this request.
     * @param allocator the allocator used for serialization purposes if necessary.
     * @return a new {@link StreamingHttpRequest}.
     */
    public static BlockingStreamingHttpRequest newRequest(
            final HttpRequestMethod method, final String requestTarget, final HttpProtocolVersion version,
            final HttpHeaders headers, final HttpHeaders initialTrailers, final BufferAllocator allocator) {
        return newRequest(method, requestTarget, version, headers, initialTrailers, allocator, emptyBlockingIterable());
    }

    /**
     * Create a new instance using HTTP 1.1 with empty payload body.
     *
     * @param method the {@link HttpRequestMethod} of the request.
     * @param requestTarget the <a href="https://tools.ietf.org/html/rfc7230#section-3.1.1">request-target</a> of the
     * request.
     * @param version the {@link HttpProtocolVersion} of the request.
     * @param headers the {@link HttpHeaders} of the request.
     * @param initialTrailers the initial state of the
     * <a href="https://tools.ietf.org/html/rfc7230#section-4.4">trailers</a> for this request.
     * @param allocator the allocator used for serialization purposes if necessary.
     * @param payloadBody The {@link BlockingIterable} payload body.
     * @return a new {@link StreamingHttpRequest}.
     */
    public static BlockingStreamingHttpRequest newRequest(
            final HttpRequestMethod method, final String requestTarget, final HttpProtocolVersion version,
            final HttpHeaders headers, final HttpHeaders initialTrailers, final BufferAllocator allocator,
            final BlockingIterable<Buffer> payloadBody) {
        return new BufferBlockingStreamingHttpRequest(method, requestTarget, version, headers, initialTrailers,
                allocator, payloadBody);
    }
}
