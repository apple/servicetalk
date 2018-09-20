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

import static io.servicetalk.buffer.api.EmptyBuffer.EMPTY_BUFFER;
import static io.servicetalk.concurrent.api.Publisher.just;
import static io.servicetalk.concurrent.api.Single.success;
import static io.servicetalk.concurrent.internal.BlockingIterables.singletonBlockingIterable;
import static java.util.Objects.requireNonNull;

final class BufferHttpRequest extends DefaultHttpRequestMetaData implements HttpRequest {
    private final HttpHeaders trailers;
    private final BufferAllocator allocator;
    private final Buffer payloadBody;

    BufferHttpRequest(final HttpRequestMethod method, final String requestTarget, final HttpProtocolVersion version,
                      final HttpHeaders headers, final HttpHeaders trailers, final BufferAllocator allocator) {
        this(method, requestTarget, version, headers, trailers, EMPTY_BUFFER, allocator);
    }

    BufferHttpRequest(final HttpRequestMethod method, final String requestTarget, final HttpProtocolVersion version,
                      final HttpHeaders headers, final HttpHeaders trailers, final Buffer payloadBody,
                      final BufferAllocator allocator) {
        super(method, requestTarget, version, headers);
        this.payloadBody = requireNonNull(payloadBody);
        this.trailers = requireNonNull(trailers);
        this.allocator = requireNonNull(allocator);
    }

    BufferHttpRequest(final DefaultHttpRequestMetaData oldRequest,
                      final BufferAllocator allocator,
                      final HttpHeaders trailers,
                      final Buffer payloadBody) {
        super(oldRequest);
        this.payloadBody = requireNonNull(payloadBody);
        this.trailers = trailers;
        this.allocator = allocator;
    }

    @Override
    public Buffer getPayloadBody() {
        return payloadBody;
    }

    @Override
    public HttpHeaders getTrailers() {
        return trailers;
    }

    @Override
    public HttpRequest setPayloadBody(final Buffer payloadBody) {
        return new BufferHttpRequest(this, allocator, trailers, payloadBody);
    }

    @Override
    public <T> HttpRequest setPayloadBody(final T pojo, final HttpSerializer<T> serializer) {
        return new BufferHttpRequest(this, allocator, trailers, serializer.serialize(getHeaders(), pojo, allocator));
    }

    @Override
    public HttpRequest setRawPath(final String path) {
        super.setRawPath(path);
        return this;
    }

    @Override
    public HttpRequest setPath(final String path) {
        super.setPath(path);
        return this;
    }

    @Override
    public HttpRequest setRawQuery(final String query) {
        super.setRawQuery(query);
        return this;
    }

    @Override
    public HttpRequest setVersion(final HttpProtocolVersion version) {
        super.setVersion(version);
        return this;
    }

    @Override
    public HttpRequest setMethod(final HttpRequestMethod method) {
        super.setMethod(method);
        return this;
    }

    @Override
    public HttpRequest setRequestTarget(final String requestTarget) {
        super.setRequestTarget(requestTarget);
        return this;
    }

    @Override
    public StreamingHttpRequest toStreamingRequest() {
        return new BufferStreamingHttpRequest(getMethod(), getRequestTarget(), getVersion(),
                getHeaders(), success(trailers), allocator, just(payloadBody));
    }

    @Override
    public BlockingStreamingHttpRequest toBlockingStreamingRequest() {
        return new BufferBlockingStreamingHttpRequest(getMethod(), getRequestTarget(), getVersion(), getHeaders(),
                success(trailers), allocator, singletonBlockingIterable(payloadBody));
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        final BufferHttpRequest that = (BufferHttpRequest) o;
        return trailers.equals(that.trailers) &&
                payloadBody.equals(that.payloadBody);
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + trailers.hashCode();
        result = 31 * result + payloadBody.hashCode();
        return result;
    }
}
