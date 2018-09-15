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
package io.servicetalk.http.netty;

import io.servicetalk.buffer.api.BufferAllocator;
import io.servicetalk.http.api.HttpHeadersFactory;
import io.servicetalk.http.api.HttpRequestMethod;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequestFactory;
import io.servicetalk.http.api.StreamingHttpRequests;
import io.servicetalk.http.api.StreamingHttpResponseFactory;

import static io.servicetalk.http.api.HttpProtocolVersions.HTTP_1_1;

final class DefaultStreamingHttpRequestFactory implements StreamingHttpRequestFactory {

    private final HttpHeadersFactory headersFactory;
    private final BufferAllocator allocator;

    DefaultStreamingHttpRequestFactory(HttpHeadersFactory headersFactory, BufferAllocator allocator) {
        this.headersFactory = headersFactory;
        this.allocator = allocator;
    }

    @Override
    public StreamingHttpRequest newRequest(final HttpRequestMethod method, final String requestTarget) {
        return StreamingHttpRequests.newRequest(method, requestTarget, HTTP_1_1,
                headersFactory.newHeaders(), headersFactory.newTrailers(), allocator);
    }
}
