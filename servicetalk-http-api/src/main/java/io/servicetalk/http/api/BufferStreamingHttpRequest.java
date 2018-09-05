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
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;

final class BufferStreamingHttpRequest extends DefaultStreamingHttpRequest<Buffer> {

    BufferStreamingHttpRequest(final HttpRequestMethod method, final String requestTarget,
                               final HttpProtocolVersion version, final HttpHeaders headers,
                               final BufferAllocator allocator, final HttpHeaders initialTrailers) {
        super(method, requestTarget, version, headers, allocator, initialTrailers);
    }

    /**
     * Create a new instance.
     * @param method The {@link HttpRequestMethod}.
     * @param requestTarget The request-target.
     * @param version The {@link HttpProtocolVersion}.
     * @param headers The initial {@link HttpHeaders}.
     * @param allocator The {@link BufferAllocator} to use for serialization (if required).
     * @param payloadBody A {@link Publisher} that provide only the payload body. The trailers <strong>must</strong>
     * not be included, and instead are represented by {@code trailersSingle}.
     * @param trailersSingle The {@link Single} <strong>must</strong> support multiple subscribes, and it is assumed to
     * provide the original data if re-used over transformation operations.
     */
    BufferStreamingHttpRequest(final HttpRequestMethod method, final String requestTarget,
                               final HttpProtocolVersion version, final HttpHeaders headers,
                               final BufferAllocator allocator, final Publisher<Buffer> payloadBody,
                               final Single<HttpHeaders> trailersSingle) {
        super(method, requestTarget, version, headers, allocator, payloadBody, trailersSingle);
    }

    BufferStreamingHttpRequest(final DefaultHttpRequestMetaData oldRequest,
                               final BufferAllocator allocator,
                               final Publisher<Buffer> payloadBody,
                               final Single<HttpHeaders> trailersSingle) {
        super(oldRequest, allocator, payloadBody, trailersSingle);
    }

    @Override
    public Publisher<Buffer> getPayloadBody() {
        return payloadBody;
    }

    @Override
    public BlockingStreamingHttpRequest toBlockingStreamingRequest() {
        return new BufferBlockingStreamingHttpRequest(this, allocator, payloadBody.toIterable(), trailersSingle);
    }
}
