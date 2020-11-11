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
import io.servicetalk.encoding.api.ContentCodec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.encoding.api.ContentCodings.identity;
import static io.servicetalk.http.api.HeaderUtils.hasContentEncoding;
import static io.servicetalk.http.api.HeaderUtils.identifyContentEncodingOrNullIfIdentity;
import static io.servicetalk.http.api.HeaderUtils.negotiateAcceptedEncoding;
import static io.servicetalk.http.api.HeaderUtils.setContentEncoding;
import static java.util.Collections.unmodifiableList;

/**
 * A {@link StreamingHttpService} that adds encoding / decoding functionality for responses and requests respectively,
 * as these are specified by the spec
 * <a href="https://tools.ietf.org/html/rfc7231#section-3.1.2.2">Content-Encoding</a>.
 *
 * <p>
 * Append this filter before others that are expected to to see compressed content for this request/response, and after
 * other filters that expect to see/manipulate the original payload.
 */
public final class ContentCodingHttpServiceFilter
        implements StreamingHttpServiceFilterFactory, HttpExecutionStrategyInfluencer {

    private static final Logger LOGGER = LoggerFactory.getLogger(ContentCodingHttpServiceFilter.class);

    private final List<ContentCodec> requestCodings;
    private final List<ContentCodec> responseCodings;

    /**
     * Enable support of the provided encodings for this server's requests and responses.
     * The encodings will be used for both client request decompression where needed and server responses compression
     * where enabled and matched.
     * <p>
     * To disable support of compressed requests, see {@link #ContentCodingHttpServiceFilter(List, List)}.
     * <p>
     * The order of the codings provided, affect selection priority alongside the order of the incoming
     * <a href="https://tools.ietf.org/html/rfc7231#section-5.3.4">accept-encoding</a> header from the client.
     *
     * @param supportedCodings the codecs used to compress server responses and decompress client requests when needed.
     */
    public ContentCodingHttpServiceFilter(final List<ContentCodec> supportedCodings) {
        final List<ContentCodec> unmodifiable = unmodifiableList(supportedCodings);
        this.requestCodings = unmodifiable;
        this.responseCodings = unmodifiable;
    }

    /**
     * Enable support of the provided encodings for this server's requests and responses.
     * The encodings can differ for requests and responses, allowing a server that supports compressed responses,
     * but allows no compressed requests.
     * <p>
     * To disable support of compressed requests use an {@link Collections#emptyList()} for the
     * <code>supportedRequestCodings</code> param.
     * <p>
     * The order of the codecs provided, affect selection priority alongside the order of the incoming
     * <a href="https://tools.ietf.org/html/rfc7231#section-5.3.4">accept-encoding</a> header from the client.
     *
     * @param supportedRequestCodings the codecs used to decompress client requests if compressed.
     * @param supportedResponseCodings the codecs used to compress server responses if client accepts them.
     */
    public ContentCodingHttpServiceFilter(final List<ContentCodec> supportedRequestCodings,
                                          final List<ContentCodec> supportedResponseCodings) {
        this.requestCodings = unmodifiableList(supportedRequestCodings);
        this.responseCodings = unmodifiableList(supportedResponseCodings);
    }

    @Override
    public StreamingHttpServiceFilter create(final StreamingHttpService service) {
        return new StreamingHttpServiceFilter(service) {
            @Override
            public Single<StreamingHttpResponse> handle(final HttpServiceContext ctx,
                                                        final StreamingHttpRequest request,
                                                        final StreamingHttpResponseFactory responseFactory) {

                return Single.defer(() -> {
                    BufferAllocator allocator = ctx.executionContext().bufferAllocator();
                    try {
                        ContentCodec coding =
                                identifyContentEncodingOrNullIfIdentity(request.headers(), requestCodings);
                        if (coding != null) {
                            request.transformPayloadBody(bufferPublisher -> coding.decode(bufferPublisher, allocator));
                        }

                        return super.handle(ctx, request, responseFactory).map(response -> {
                            encodePayloadContentIfAvailable(request.headers(), responseCodings, response, allocator);
                            return response;
                        });
                    } catch (UnsupportedContentEncodingException cause) {
                        LOGGER.error("Request failed for service={}, connection={}", service, this, cause);
                        // see https://tools.ietf.org/html/rfc7231#section-3.1.2.2
                        return succeeded(responseFactory.unsupportedMediaType());
                    }
                });
            }
        };
    }

    @Override
    public HttpExecutionStrategy influenceStrategy(final HttpExecutionStrategy strategy) {
        // No influence - no blocking
        return strategy;
    }

    private static void encodePayloadContentIfAvailable(final HttpHeaders requestHeaders,
                                                        final List<ContentCodec> supportedEncodings,
                                                        final StreamingHttpResponse response,
                                                        final BufferAllocator allocator) {
        if (supportedEncodings.isEmpty() || hasContentEncoding(response.headers())) {
            return;
        }

        ContentCodec coding = codingForResponse(requestHeaders, response, supportedEncodings);
        if (coding != null) {
            setContentEncoding(response.headers(), coding.name());
            response.transformPayloadBody(bufferPublisher -> coding.encode(bufferPublisher, allocator));
        }
    }

    @Nullable
    private static ContentCodec codingForResponse(final HttpHeaders requestHeaders,
                                                  final StreamingHttpResponse response,
                                                  final List<ContentCodec> supportedEncodings) {
        // Enforced selection
        ContentCodec encoding = response.encoding();
        if (encoding == null) {
            // Negotiated from client headers and server config
            encoding = negotiateAcceptedEncoding(requestHeaders, supportedEncodings);
        }

        return encoding == identity() ? null : encoding;
    }
}
