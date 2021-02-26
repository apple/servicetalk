/*
 * Copyright © 2020-2021 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.encoding.api.ContentCodec;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

import static io.servicetalk.buffer.api.CharSequences.newAsciiString;
import static io.servicetalk.encoding.api.Identity.identity;
import static io.servicetalk.http.api.HeaderUtils.addContentEncoding;
import static io.servicetalk.http.api.HeaderUtils.identifyContentEncodingOrNullIfIdentity;
import static io.servicetalk.http.api.HeaderUtils.setAcceptEncoding;

/**
 * A {@link StreamingHttpClientFilter} that adds encoding / decoding functionality for requests and responses
 * respectively, as these are specified by the spec
 * <a href="https://tools.ietf.org/html/rfc7231#section-3.1.2.2">Content-Encoding</a>.
 * <p>
 * Append this filter before others that are expected to to see compressed content for this request/response, and after
 * other filters that expect to manipulate the original payload.
 * @deprecated Use {@link ContentEncodingHttpRequesterFilter}.
 */
@Deprecated
public final class ContentCodingHttpRequesterFilter
        implements StreamingHttpClientFilterFactory, StreamingHttpConnectionFilterFactory,
                   HttpExecutionStrategyInfluencer {

    private final List<ContentCodec> supportedCodings;
    @Nullable
    private final CharSequence acceptedEncodingsHeader;

    /**
     * Enable support of the provided encodings for requests and responses.
     * The order of the codecs provided, matters for the presentation of the header, and may affect selection priority
     * on the receiving endpoint.
     *
     * @param supportedCodings the codecs this clients supports to encode/decode requests and responses accordingly
     * and also used to advertise to the server.
     */
    public ContentCodingHttpRequesterFilter(final List<ContentCodec> supportedCodings) {
        this.supportedCodings = new ArrayList<>(supportedCodings);
        this.acceptedEncodingsHeader = buildAcceptEncodingsHeader(supportedCodings);
    }

    @Override
    public StreamingHttpClientFilter create(final FilterableStreamingHttpClient client) {
        return new StreamingHttpClientFilter(client) {
            @Override
            protected Single<StreamingHttpResponse> request(final StreamingHttpRequester delegate,
                                                            final HttpExecutionStrategy strategy,
                                                            final StreamingHttpRequest request) {
                return Single.defer(() -> codecTransformBidirectionalIfNeeded(delegate(), strategy, request));
            }
        };
    }

    @Override
    public StreamingHttpConnectionFilter create(final FilterableStreamingHttpConnection connection) {
        return new StreamingHttpConnectionFilter(connection) {
            @Override
            public Single<StreamingHttpResponse> request(final HttpExecutionStrategy strategy,
                                                         final StreamingHttpRequest request) {
                return Single.defer(() -> codecTransformBidirectionalIfNeeded(delegate(), strategy, request));
            }
        };
    }

    @Override
    public HttpExecutionStrategy influenceStrategy(final HttpExecutionStrategy strategy) {
        // No influence since we do not block.
        return strategy;
    }

    private Single<StreamingHttpResponse> codecTransformBidirectionalIfNeeded(final StreamingHttpRequester delegate,
                                                                              final HttpExecutionStrategy strategy,
                                                                              final StreamingHttpRequest request) {
        final BufferAllocator alloc = delegate.executionContext().bufferAllocator();
        setAcceptEncoding(request.headers(), acceptedEncodingsHeader);
        encodePayloadContentIfAvailable(request, alloc);

        return decodePayloadContentIfEncoded(delegate.request(strategy, request), alloc);
    }

    private Single<StreamingHttpResponse> decodePayloadContentIfEncoded(
            final Single<StreamingHttpResponse> responseSingle, final BufferAllocator allocator) {

        return responseSingle.map(response -> {
            ContentCodec coding = identifyContentEncodingOrNullIfIdentity(response.headers(), supportedCodings);
            if (coding != null) {
                response.transformPayloadBody(bufferPublisher -> coding.decode(bufferPublisher, allocator));
            }

            return response;
        });
    }

    @Nullable
    private static CharSequence buildAcceptEncodingsHeader(final List<ContentCodec> codecs) {
        StringBuilder builder = new StringBuilder();
        for (ContentCodec enc : codecs) {
            if (identity().equals(enc)) {
                continue;
            }

            if (builder.length() > 0) {
                builder.append(", ");
            }

            builder.append(enc.name());
        }

        return builder.length() > 0 ? newAsciiString(builder) : null;
    }

    private static void encodePayloadContentIfAvailable(final StreamingHttpRequest request,
                                                        final BufferAllocator allocator) {
        ContentCodec coding = request.encoding();
        if (coding != null && !identity().equals(coding)) {
            addContentEncoding(request.headers(), coding.name());
            request.transformPayloadBody(pub -> coding.encode(pub, allocator));
        }
    }
}
