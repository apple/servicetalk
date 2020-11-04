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
import javax.annotation.Nullable;

import static io.servicetalk.http.api.ContentCodings.identity;
import static io.servicetalk.http.api.HeaderUtils.addContentEncoding;
import static io.servicetalk.http.api.HeaderUtils.hasContentEncoding;
import static io.servicetalk.http.api.HeaderUtils.identifyContentEncodingOrNone;
import static io.servicetalk.http.api.HeaderUtils.negotiateAcceptedEncoding;

/**
 * Filter responsible for encoding/decoding content according to content-codings configured.
 */
class ContentCodingHttpServiceFilter implements StreamingHttpServiceFilterFactory {

    private final List<StreamingContentCodec> requestCodings;
    private final List<StreamingContentCodec> responseCodings;

    ContentCodingHttpServiceFilter(final List<StreamingContentCodec> requestCodings,
                                          final List<StreamingContentCodec> responseCodings) {
        this.requestCodings = requestCodings;
        this.responseCodings = responseCodings;
    }

    @Override
    public StreamingHttpServiceFilter create(final StreamingHttpService service) {
        return new StreamingHttpServiceFilter(service) {
            @Override
            public Single<StreamingHttpResponse> handle(final HttpServiceContext ctx,
                                                        final StreamingHttpRequest request,
                                                        final StreamingHttpResponseFactory responseFactory) {

                BufferAllocator allocator = ctx.executionContext().bufferAllocator();
                StreamingContentCodec coding = identifyContentEncodingOrNone(request.headers(), requestCodings);
                request.transformPayloadBody(bufferPublisher -> coding.decode(bufferPublisher, allocator));

                return super.handle(ctx, request, responseFactory).map(response -> {
                    encodePayloadContentIfAvailable(request.headers(), responseCodings, response, allocator);
                    return response;
                });
            }
        };
    }

    private static void encodePayloadContentIfAvailable(final HttpHeaders requestHeaders,
                                                        final List<StreamingContentCodec> supportedEncodings,
                                                        final StreamingHttpResponse response,
                                                        final BufferAllocator allocator) {
        if (supportedEncodings.isEmpty() || hasContentEncoding(response.headers())) {
            return;
        }

        StreamingContentCodec coding = codingForResponse(requestHeaders, response, supportedEncodings);
        if (coding != null && !coding.equals(identity())) {
            addContentEncoding(response.headers(), coding.name());
            response.transformPayloadBody(bufferPublisher -> coding.encode(bufferPublisher, allocator));
        }
    }

    @Nullable
    private static StreamingContentCodec codingForResponse(final HttpHeaders requestHeaders,
                                                           final StreamingHttpResponse response,
                                                           final List<StreamingContentCodec> supportedEncodings) {
        if (response.encoding() != null) {
            // Enforced selection
            return response.encoding();
        }

        // Negotiate encoding according to Accept-Encodings header and server supported ones.
        return negotiateAcceptedEncoding(requestHeaders, supportedEncodings);
    }
}
