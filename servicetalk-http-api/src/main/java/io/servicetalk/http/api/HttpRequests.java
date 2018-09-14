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

import io.servicetalk.buffer.api.BufferAllocator;

/**
 * Factory methods for creating {@link HttpRequest}s.
 */
public final class HttpRequests {
    private HttpRequests() {
        // No instances
    }

    /**
     * Create a new instance using HTTP 1.1 with empty payload body and headers.
     *
     * @param method the {@link HttpRequestMethod} of the request.
     * @param requestTarget the <a href="https://tools.ietf.org/html/rfc7230#section-3.1.1">request-target</a> of the
     * request.
     * @param version the {@link HttpProtocolVersion}.
     * @param headers the headers.
     * @param trailers the <a href="https://tools.ietf.org/html/rfc7230#section-4.4">trailers</a>.
     * @param allocator The {@link BufferAllocator} used for serialization.
     * @return a new {@link HttpRequest}.
     */
    public static HttpRequest newRequest(final HttpRequestMethod method, final String requestTarget,
                                         final HttpProtocolVersion version, final HttpHeaders headers,
                                         final HttpHeaders trailers, final BufferAllocator allocator) {
        return new BufferHttpRequest(method, requestTarget, version, headers, trailers, allocator);
    }
}
