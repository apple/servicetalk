/*
 * Copyright © 2020 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.concurrent.api.Single;

import java.util.List;

import static io.servicetalk.http.api.ContentCodings.identity;
import static io.servicetalk.http.api.HeaderUtils.addContentEncoding;
import static io.servicetalk.http.api.HeaderUtils.advertiseAcceptedEncodingsIfAvailable;
import static io.servicetalk.http.api.HeaderUtils.identifyContentEncodingOrNone;

/**
 * Filter responsible for encoding/decoding content according to content-codings configured.
 */
public class ContentCodingHttpClientFilter implements StreamingHttpClientFilterFactory {

    private final List<StreamingContentCodec> supportedEncodings;

    public ContentCodingHttpClientFilter(final List<StreamingContentCodec> supportedEncodings) {
        this.supportedEncodings = supportedEncodings;
    }

    @Override
    public StreamingHttpClientFilter create(final FilterableStreamingHttpClient client) {
        return new StreamingHttpClientFilter(client) {
            @Override
            protected Single<StreamingHttpResponse> request(final StreamingHttpRequester delegate,
                                                            final HttpExecutionStrategy strategy,
                                                            final StreamingHttpRequest request) {
                final BufferAllocator alloc = delegate.executionContext().bufferAllocator();
                advertiseAcceptedEncodingsIfAvailable(request, supportedEncodings);
                encodePayloadContentIfAvailable(request, alloc);

                return decodePayloadContentIfEncoded(super.request(delegate, strategy, request), alloc);
            }
        };
    }

    private void encodePayloadContentIfAvailable(final StreamingHttpRequest request, final BufferAllocator allocator) {
        StreamingContentCodec coding = request.encoding();
        if (coding != null) {
            addContentEncoding(request.headers(), coding.name());
            request.transformPayloadBody(pub -> coding.encode(pub, allocator));
        }
    }

    private Single<StreamingHttpResponse> decodePayloadContentIfEncoded(
            final Single<StreamingHttpResponse> responseSingle, final BufferAllocator allocator) {

        return responseSingle.map((response -> {
            StreamingContentCodec coding = identifyContentEncodingOrNone(response.headers(), supportedEncodings);
            if (!coding.equals(identity())) {
                response.transformPayloadBody(bufferPublisher -> coding.decode(bufferPublisher, allocator));
            }

            return response;
        }));
    }
}
