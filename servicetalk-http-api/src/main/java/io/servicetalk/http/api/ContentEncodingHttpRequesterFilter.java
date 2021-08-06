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

import io.servicetalk.concurrent.api.Single;
import io.servicetalk.encoding.api.BufferDecoder;
import io.servicetalk.encoding.api.BufferDecoderGroup;
import io.servicetalk.encoding.api.BufferEncoder;

import java.util.Iterator;

import static io.servicetalk.encoding.api.Identity.identityEncoder;
import static io.servicetalk.http.api.ContentEncodingHttpServiceFilter.matchAndRemoveEncoding;
import static io.servicetalk.http.api.HeaderUtils.addContentEncoding;
import static io.servicetalk.http.api.HttpHeaderNames.ACCEPT_ENCODING;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_ENCODING;
import static java.util.Objects.requireNonNull;

/**
 * A {@link StreamingHttpClientFilter} that adds encoding / decoding functionality for requests and responses
 * respectively, as these are specified by the spec
 * <a href="https://tools.ietf.org/html/rfc7231#section-3.1.2.2">Content-Encoding</a>.
 *
 * <p>
 * Append this filter before others that are expected to to see compressed content for this request/response, and after
 * other filters that expect to manipulate the original payload.
 */
public final class ContentEncodingHttpRequesterFilter implements
          StreamingHttpClientFilterFactory, StreamingHttpConnectionFilterFactory, HttpExecutionStrategyInfluencer {
    private final BufferDecoderGroup decompressors;

    /**
     * Create a new instance and specify the supported decompression (advertised in
     * {@link HttpHeaderNames#ACCEPT_ENCODING}). The compression is specified via
     * {@link HttpRequestMetaData#contentEncoding()}. The order of entries may impact the selection preference.
     *
     * @param decompressors the decompression supported to decode responses accordingly and also used to advertise
     * {@link HttpHeaderNames#ACCEPT_ENCODING} to the server.
     */
    public ContentEncodingHttpRequesterFilter(final BufferDecoderGroup decompressors) {
        this.decompressors = requireNonNull(decompressors);
    }

    @Override
    public StreamingHttpClientFilter create(final FilterableStreamingHttpClient client) {
        return new StreamingHttpClientFilter(client) {
            @Override
            protected Single<StreamingHttpResponse> request(final StreamingHttpRequester delegate,
                                                            final HttpExecutionStrategy strategy,
                                                            final StreamingHttpRequest request) {
                return applyEncodingAndDecoding(delegate, strategy, request);
            }
        };
    }

    @Override
    public StreamingHttpConnectionFilter create(final FilterableStreamingHttpConnection connection) {
        return new StreamingHttpConnectionFilter(connection) {
            @Override
            public Single<StreamingHttpResponse> request(final HttpExecutionStrategy strategy,
                                                         final StreamingHttpRequest request) {
                return applyEncodingAndDecoding(delegate(), strategy, request);
            }
        };
    }

    @Override
    public HttpExecutionStrategy influenceStrategy(final HttpExecutionStrategy strategy) {
        // No influence since we do not block.
        return strategy;
    }

    private Single<StreamingHttpResponse> applyEncodingAndDecoding(final StreamingHttpRequester delegate,
                                                                   final HttpExecutionStrategy strategy,
                                                                   final StreamingHttpRequest request) {
        return Single.defer(() -> {
            boolean decompressResponse = false;
            CharSequence encodings = decompressors.advertisedMessageEncoding();
            if (encodings != null && !request.headers().contains(ACCEPT_ENCODING)) {
                request.headers().set(ACCEPT_ENCODING, encodings);
                decompressResponse = true;
            }
            BufferEncoder encoder = request.contentEncoding();
            final StreamingHttpRequest encodedRequest;
            if (encoder != null && !identityEncoder().equals(encoder)) {
                addContentEncoding(request.headers(), encoder.encodingName());
                encodedRequest = request.transformPayloadBody(pub -> encoder.streamingEncoder().serialize(pub,
                        delegate.executionContext().bufferAllocator()));
            } else {
                encodedRequest = request;
            }

            Single<StreamingHttpResponse> respSingle = delegate.request(strategy, encodedRequest);
            return (decompressResponse ? respSingle.map(response -> {
                Iterator<? extends CharSequence> contentEncodingItr =
                        response.headers().valuesIterator(CONTENT_ENCODING);
                final boolean hasContentEncoding = contentEncodingItr.hasNext();
                if (!hasContentEncoding) {
                    return response;
                }
                BufferDecoder decoder = matchAndRemoveEncoding(decompressors.decoders(),
                        BufferDecoder::encodingName, contentEncodingItr, response.headers());
                if (decoder == null) {
                    throw new UnsupportedContentEncodingException(response.headers().get(CONTENT_ENCODING,
                            "<null>").toString());
                }

                return response.transformPayloadBody(pub -> decoder.streamingDecoder().deserialize(pub,
                        delegate.executionContext().bufferAllocator()));
            }) : respSingle).subscribeShareContext();
        });
    }
}
