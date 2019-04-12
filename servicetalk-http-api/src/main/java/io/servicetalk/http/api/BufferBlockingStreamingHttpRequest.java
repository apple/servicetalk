/*
 * Copyright © 2018-2019 Apple Inc. and the ServiceTalk project authors
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

import static io.servicetalk.concurrent.api.Publisher.fromIterable;

final class BufferBlockingStreamingHttpRequest extends DefaultBlockingStreamingHttpRequest<Buffer> {
    BufferBlockingStreamingHttpRequest(final HttpRequestMethod method, final String requestTarget,
                                       final HttpProtocolVersion version, final HttpHeaders headers,
                                       final HttpHeaders initialTrailers, final BufferAllocator allocator,
                                       final BlockingIterable<Buffer> payloadBody, final ApiType effectiveApiType) {
        super(method, requestTarget, version, headers, initialTrailers, allocator, payloadBody, effectiveApiType);
    }

    BufferBlockingStreamingHttpRequest(
            final HttpRequestMethod method, final String requestTarget, final HttpProtocolVersion version,
            final HttpHeaders headers, final Single<HttpHeaders> trailersSingle, final BufferAllocator allocator,
            final BlockingIterable<Buffer> payloadBody, final ApiType effectiveApiType) {
        super(method, requestTarget, version, headers, trailersSingle, allocator, payloadBody, effectiveApiType);
    }

    BufferBlockingStreamingHttpRequest(final DefaultHttpRequestMetaData oldRequest,
                                       final BufferAllocator allocator,
                                       final BlockingIterable<Buffer> payloadBody,
                                       final Single<HttpHeaders> trailersSingle, final ApiType effectiveApiType) {
        super(oldRequest, allocator, payloadBody, trailersSingle, effectiveApiType);
    }

    @Override
    public BlockingIterable<Buffer> payloadBody() {
        return payloadBody;
    }

    @Override
    public StreamingHttpRequest toStreamingRequest() {
        return new BufferStreamingHttpRequest(method(), requestTarget(), version(), headers(),
                trailersSingle, allocator, fromIterable(payloadBody), effectiveApiType);
    }
}
