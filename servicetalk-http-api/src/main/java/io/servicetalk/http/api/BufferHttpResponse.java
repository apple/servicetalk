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
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.concurrent.internal.BlockingIterables.singletonBlockingIterable;
import static java.util.Objects.requireNonNull;

final class BufferHttpResponse extends DefaultHttpResponseMetaData implements HttpResponse {
    private final HttpHeaders trailers;
    private final BufferAllocator allocator;
    private final Buffer payloadBody;

    BufferHttpResponse(final HttpResponseStatus status, final HttpProtocolVersion version,
                       final HttpHeaders headers, final HttpHeaders trailers, final BufferAllocator allocator) {
        this(status, version, headers, trailers, EMPTY_BUFFER, allocator);
    }

    BufferHttpResponse(final HttpResponseStatus status, final HttpProtocolVersion version,
                       final HttpHeaders headers, final HttpHeaders trailers, final Buffer payloadBody,
                       final BufferAllocator allocator) {
        super(status, version, headers);
        this.payloadBody = requireNonNull(payloadBody);
        this.trailers = requireNonNull(trailers);
        this.allocator = requireNonNull(allocator);
    }

    BufferHttpResponse(final DefaultHttpResponseMetaData oldResponse,
                       final BufferAllocator allocator,
                       final HttpHeaders trailers,
                       final Buffer payloadBody) {
        super(oldResponse);
        this.payloadBody = requireNonNull(payloadBody);
        this.trailers = trailers;
        this.allocator = allocator;
    }

    @Override
    public HttpResponse version(final HttpProtocolVersion version) {
        super.version(version);
        return this;
    }

    @Override
    public HttpResponse status(final HttpResponseStatus status) {
        super.status(status);
        return this;
    }

    @Override
    public Buffer payloadBody() {
        return payloadBody;
    }

    @Override
    public HttpHeaders trailers() {
        return trailers;
    }

    @Override
    public HttpResponse payloadBody(final Buffer payloadBody) {
        return new BufferHttpResponse(this, allocator, trailers, payloadBody);
    }

    @Override
    public <T> HttpResponse payloadBody(final T pojo, final HttpSerializer<T> serializer) {
        return new BufferHttpResponse(this, allocator, trailers, serializer.serialize(headers(), pojo,
                allocator));
    }

    @Override
    public StreamingHttpResponse toStreamingResponse() {
        return new BufferStreamingHttpResponse(status(), version(), headers(), succeeded(trailers), allocator,
                just(payloadBody));
    }

    @Override
    public BlockingStreamingHttpResponse toBlockingStreamingResponse() {
        return new BufferBlockingStreamingHttpResponse(status(), version(), headers(), succeeded(trailers),
                allocator, singletonBlockingIterable(payloadBody));
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
        final BufferHttpResponse that = (BufferHttpResponse) o;
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
