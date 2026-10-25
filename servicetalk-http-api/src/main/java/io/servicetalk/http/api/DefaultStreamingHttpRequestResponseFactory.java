/*
 * Copyright © 2018-2026 Apple Inc. and the ServiceTalk project authors
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

import javax.annotation.Nullable;

import static java.util.Locale.ENGLISH;
import static java.util.Objects.requireNonNull;

/**
 * A default implementation for {@link StreamingHttpRequestFactory} and {@link StreamingHttpResponseFactory}.
 */
public final class DefaultStreamingHttpRequestResponseFactory implements StreamingHttpRequestResponseFactory {
    private final BufferAllocator allocator;
    private final HttpHeadersFactory headersFactory;
    private final HttpProtocolVersion protocolVersion;
    @Nullable
    private final String defaultScheme;

    /**
     * Create a new instance.
     * @param allocator The {@link BufferAllocator} to use for serialization.
     * @param headersFactory The {@link HttpHeadersFactory} to use for request/response creation.
     * @param protocolVersion The {@link HttpProtocolVersion} to use for new requests/responses.
     */
    public DefaultStreamingHttpRequestResponseFactory(final BufferAllocator allocator,
                                                      final HttpHeadersFactory headersFactory,
                                                      final HttpProtocolVersion protocolVersion) {
        this(allocator, headersFactory, protocolVersion, null);
    }

    /**
     * Create a new instance with a default request scheme.
     *
     * @param allocator The {@link BufferAllocator} to use for serialization.
     * @param headersFactory The {@link HttpHeadersFactory} to use for request/response creation.
     * @param protocolVersion The {@link HttpProtocolVersion} to use for new requests/responses.
     * @param defaultScheme The default {@link HttpRequestMetaData#scheme() scheme} for requests whose request target
     * does not contain a scheme, or {@code null} if no default is available. Converted to lower case.
     */
    public DefaultStreamingHttpRequestResponseFactory(final BufferAllocator allocator,
                                                      final HttpHeadersFactory headersFactory,
                                                      final HttpProtocolVersion protocolVersion,
                                                      @Nullable final String defaultScheme) {
        this.allocator = requireNonNull(allocator);
        this.headersFactory = requireNonNull(headersFactory);
        this.protocolVersion = requireNonNull(protocolVersion);
        this.defaultScheme = defaultScheme == null ? null : defaultScheme.toLowerCase(ENGLISH);
    }

    @Override
    public StreamingHttpRequest newRequest(final HttpRequestMethod method, final String requestTarget) {
        return StreamingHttpRequests.newRequest(method, requestTarget, protocolVersion, headersFactory.newHeaders(),
                allocator, headersFactory, defaultScheme);
    }

    @Override
    public StreamingHttpResponse newResponse(final HttpResponseStatus status) {
        return StreamingHttpResponses.newResponse(status, protocolVersion, headersFactory.newHeaders(),
                allocator, headersFactory);
    }
}
