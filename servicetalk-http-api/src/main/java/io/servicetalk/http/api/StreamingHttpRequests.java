/*
 * Copyright © 2018, 2021 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.context.api.ContextMap;

import javax.annotation.Nullable;

import static io.servicetalk.http.api.DefaultPayloadInfo.forTransportReceive;
import static io.servicetalk.http.api.DefaultPayloadInfo.forUserCreated;

/**
 * Factory methods for creating {@link StreamingHttpRequest}s.
 */
public final class StreamingHttpRequests {
    private StreamingHttpRequests() {
        // No instances
    }

    /**
     * Creates a new {@link StreamingHttpRequest}.
     *
     * @param method the {@link HttpRequestMethod} of the request.
     * @param requestTarget the <a href="https://tools.ietf.org/html/rfc7230#section-3.1.1">request-target</a> of the
     * request.
     * @param version the {@link HttpProtocolVersion} of the request.
     * @param headers the {@link HttpHeaders} of the request. Note that newly created and returned
     * {@link StreamingHttpRequest} will use this {@link HttpHeaders} directly, which means later mutation of
     * {@link HttpHeaders} will have side effects on returned request and should be avoided as these operations are not
     * thread safe.
     * @param allocator the allocator used for serialization purposes if necessary.
     * @param headersFactory {@link HttpHeadersFactory} to use.
     * @return a new {@link StreamingHttpRequest}.
     */
    public static StreamingHttpRequest newRequest(
            final HttpRequestMethod method, final String requestTarget, final HttpProtocolVersion version,
            final HttpHeaders headers, final BufferAllocator allocator, final HttpHeadersFactory headersFactory) {
        return new DefaultStreamingHttpRequest(method, requestTarget, version, headers, null, null, null, allocator,
                null, forUserCreated(), headersFactory);
    }

    /**
     * Creates a new {@link StreamingHttpRequest} which is read from the transport. If the request contains
     * <a href="https://tools.ietf.org/html/rfc7230#section-4.4">trailers</a> then the passed {@code payload}
     * {@link Publisher} should also emit {@link HttpHeaders} before completion.
     *
     * @param method the {@link HttpRequestMethod} of the request.
     * @param requestTarget the <a href="https://tools.ietf.org/html/rfc7230#section-3.1.1">request-target</a> of the
     * request.
     * @param version the {@link HttpProtocolVersion} of the request.
     * @param headers the {@link HttpHeaders} of the request. Note that newly created and returned
     * {@link StreamingHttpRequest} will use this {@link HttpHeaders} directly, which means later mutation of
     * {@link HttpHeaders} will have side effects on returned request and should be avoided as these operations are not
     * thread safe.
     * @param allocator the allocator used for serialization purposes if necessary.
     * @param payload a {@link Publisher} for payload that optionally emits {@link HttpHeaders} if the request contains
     * <a href="https://tools.ietf.org/html/rfc7230#section-4.4">trailers</a>.
     * @param requireTrailerHeader {@code true} if <a href="https://tools.ietf.org/html/rfc7230#section-4.4">Trailer</a>
     * header is required to accept trailers. {@code false} assumes trailers may be present if other criteria allows.
     * @param headersFactory {@link HttpHeadersFactory} to use.
     * @return a new {@link StreamingHttpRequest}.
     * @deprecated Use {@link #newTransportRequest(HttpRequestMetaData, BufferAllocator, Publisher, boolean,
     * HttpHeadersFactory)} instead.
     */
    @Deprecated
    public static StreamingHttpRequest newTransportRequest(
            final HttpRequestMethod method, final String requestTarget, final HttpProtocolVersion version,
            final HttpHeaders headers, final BufferAllocator allocator, final Publisher<Object> payload,
            final boolean requireTrailerHeader, final HttpHeadersFactory headersFactory) {
        return new DefaultStreamingHttpRequest(method, requestTarget, version, headers, null, null, null, allocator,
                payload, forTransportReceive(requireTrailerHeader, version, headers), headersFactory);
    }

    /**
     * Creates a new {@link StreamingHttpRequest} which is read from the transport.
     * <p>
     * If the request contains <a href="https://tools.ietf.org/html/rfc7230#section-4.4">trailers</a> then the passed
     * {@code payload} {@link Publisher} must also emit {@link HttpHeaders} before completion.
     *
     * @param metaData the {@link HttpRequestMetaData} of the request parsed by the transport layer. Note that newly
     * created and returned {@link StreamingHttpRequest} will use this {@link HttpRequestMetaData} directly and share
     * its parts, which means later mutation of {@link HttpRequestMetaData} will have side effects on returned request
     * and should be avoided as these operations are not thread safe.
     * @param allocator the allocator to use for serialization purposes if necessary.
     * @param payload a {@link Publisher} for payload that optionally emits {@link HttpHeaders} if the request contains
     * <a href="https://tools.ietf.org/html/rfc7230#section-4.4">trailers</a>.
     * @param requireTrailerHeader {@code true} if <a href="https://tools.ietf.org/html/rfc7230#section-4.4">Trailer</a>
     * header is required to accept trailers. {@code false} assumes trailers may be present if other criteria allows.
     * @param headersFactory {@link HttpHeadersFactory} to use to allocate trailers if necessary.
     * @return a new {@link StreamingHttpRequest}.
     */
    public static StreamingHttpRequest newTransportRequest(
            final HttpRequestMetaData metaData, final BufferAllocator allocator, final Publisher<Object> payload,
            final boolean requireTrailerHeader, final HttpHeadersFactory headersFactory) {
        @Nullable
        ContextMap context = metaData instanceof DefaultHttpRequestMetaData ?
                ((DefaultHttpRequestMetaData) metaData).context0() : metaData.context();
        return new DefaultStreamingHttpRequest(metaData.method(), metaData.requestTarget(), metaData.version(),
                metaData.headers(), context, metaData.encoding(), metaData.contentEncoding(), allocator, payload,
                forTransportReceive(requireTrailerHeader, metaData.version(), metaData.headers()), headersFactory);
    }
}
