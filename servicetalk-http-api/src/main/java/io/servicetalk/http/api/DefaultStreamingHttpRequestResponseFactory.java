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

import static io.servicetalk.http.api.HttpProtocolVersions.HTTP_1_1;
import static java.util.Objects.requireNonNull;

/**
 * A default implementation for {@link StreamingHttpRequestFactory} and {@link StreamingHttpResponseFactory}.
 */
public final class DefaultStreamingHttpRequestResponseFactory implements StreamingHttpRequestResponseFactory {
    private final BufferAllocator allocator;
    private final HttpHeadersFactory headersFactory;

    /**
     * Create a new instance.
     * @param allocator The {@link BufferAllocator} to use for serialization.
     * @param headersFactory The {@link HttpHeadersFactory} to use for request/response creation.
     */
    public DefaultStreamingHttpRequestResponseFactory(final BufferAllocator allocator,
                                                      final HttpHeadersFactory headersFactory) {
        this.allocator = requireNonNull(allocator);
        this.headersFactory = requireNonNull(headersFactory);
    }

    @Override
    public StreamingHttpRequest newRequest(final HttpRequestMethod method, final String requestTarget) {
        return StreamingHttpRequests.newRequest(method, requestTarget, HTTP_1_1, headersFactory.newHeaders(),
                headersFactory.newTrailers(), allocator);
    }

    @Override
    public StreamingHttpResponse newResponse(final HttpResponseStatus status) {
        return StreamingHttpResponses.newResponse(status, HTTP_1_1, headersFactory.newHeaders(),
                headersFactory.newTrailers(), allocator);
    }
}
