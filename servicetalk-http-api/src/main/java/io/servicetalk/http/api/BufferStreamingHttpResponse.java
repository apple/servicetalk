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

final class BufferStreamingHttpResponse extends DefaultStreamingHttpResponse<Buffer> {
    BufferStreamingHttpResponse(final HttpResponseStatus status, final HttpProtocolVersion version,
                                final HttpHeaders headers, final HttpHeaders initialTrailers,
                                final BufferAllocator allocator, Publisher<Buffer> payloadBody) {
        super(status, version, headers, initialTrailers, allocator, payloadBody);
    }

    BufferStreamingHttpResponse(final HttpResponseStatus status, final HttpProtocolVersion version,
                                final HttpHeaders headers, final Single<HttpHeaders> trailersSingle,
                                final BufferAllocator allocator, Publisher<Buffer> payloadBody) {
        super(status, version, headers, trailersSingle, allocator, payloadBody);
    }

    BufferStreamingHttpResponse(final DefaultHttpResponseMetaData oldRequest,
                                final BufferAllocator allocator,
                                final Publisher<Buffer> payloadBody,
                                final Single<HttpHeaders> trailersSingle) {
        super(oldRequest, allocator, payloadBody, trailersSingle);
    }

    @Override
    public Publisher<Buffer> payloadBody() {
        return payloadBody;
    }

    @Override
    public BlockingStreamingHttpResponse toBlockingStreamingResponse() {
        return new BufferBlockingStreamingHttpResponse(this, allocator, payloadBody.toIterable(), trailersSingle);
    }
}
