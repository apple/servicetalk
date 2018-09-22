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
import io.servicetalk.concurrent.api.Single;

import static io.servicetalk.concurrent.api.Publisher.from;

final class BufferBlockingStreamingHttpResponse extends DefaultBlockingStreamingHttpResponse<Buffer> {
    BufferBlockingStreamingHttpResponse(final HttpResponseStatus status, final HttpProtocolVersion version,
                                        final HttpHeaders headers, final HttpHeaders initialTrailers,
                                        final BufferAllocator allocator, final BlockingIterable<Buffer> payloadBody) {
        super(status, version, headers, initialTrailers, allocator, payloadBody);
    }

    BufferBlockingStreamingHttpResponse(final HttpResponseStatus status, final HttpProtocolVersion version,
                                         final HttpHeaders headers, final Single<HttpHeaders> trailersSingle,
                                         final BufferAllocator allocator, final BlockingIterable<Buffer> payloadBody) {
        super(status, version, headers, trailersSingle, allocator, payloadBody);
    }

    BufferBlockingStreamingHttpResponse(final DefaultHttpResponseMetaData oldRequest,
                                        final BufferAllocator allocator,
                                        final BlockingIterable<Buffer> payloadBody,
                                        final Single<HttpHeaders> trailersSingle) {
        super(oldRequest, allocator, payloadBody, trailersSingle);
    }

    @Override
    public BlockingIterable<Buffer> payloadBody() {
        return payloadBody;
    }

    @Override
    public StreamingHttpResponse toStreamingResponse() {
        return new BufferStreamingHttpResponse(status(), version(), headers(), trailersSingle, allocator,
                from(payloadBody));
    }
}
