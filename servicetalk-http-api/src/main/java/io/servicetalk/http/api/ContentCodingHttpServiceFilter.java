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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.encoding.api.Identity.identity;
import static io.servicetalk.encoding.api.internal.HeaderUtils.negotiateAcceptedEncoding;
import static io.servicetalk.http.api.HeaderUtils.addContentEncoding;
import static io.servicetalk.http.api.HeaderUtils.hasContentEncoding;
import static io.servicetalk.http.api.HeaderUtils.identifyContentEncodingOrNullIfIdentity;
import static io.servicetalk.http.api.HttpHeaderNames.ACCEPT_ENCODING;
import static io.servicetalk.http.api.HttpRequestMethod.CONNECT;
import static io.servicetalk.http.api.HttpRequestMethod.HEAD;
import static io.servicetalk.http.api.HttpResponseStatus.NOT_MODIFIED;
import static io.servicetalk.http.api.HttpResponseStatus.NO_CONTENT;
import static io.servicetalk.http.api.HttpResponseStatus.StatusClass.INFORMATIONAL_1XX;
import static io.servicetalk.http.api.HttpResponseStatus.StatusClass.SUCCESSFUL_2XX;
import static java.util.Collections.emptyList;

/**
 * A {@link StreamingHttpService} that adds encoding / decoding functionality for responses and requests respectively,
 * as these are specified by the spec
 * <a href="https://tools.ietf.org/html/rfc7231#section-3.1.2.2">Content-Encoding</a>.
 *
 * <p>
 * Append this filter before others that are expected to to see compressed content for this request/response, and after
 * other filters that expect to see/manipulate the original payload.
 * @deprecated Use {@link ContentEncodingHttpServiceFilter}.
 */
@Deprecated
public final class ContentCodingHttpServiceFilter
        implements StreamingHttpServiceFilterFactory, HttpExecutionStrategyInfluencer {

    private static final Logger LOGGER = LoggerFactory.getLogger(ContentCodingHttpServiceFilter.class);

    private final List<ContentCodec> requestCodings;
    private final List<ContentCodec> responseCodings;

    /**
     * Enable support of the provided encodings for this server's responses.
     * The encodings will be used for server responses compression where enabled and matched with client ones.
     * <p>
     * Client requests that have compressed payloads will be rejected.
     * To enable support of compressed requests, see {@link #ContentCodingHttpServiceFilter(List, List)}.
     *
     * @param supportedCodings the codecs used to compress responses when allowed.
     */
    public ContentCodingHttpServiceFilter(final List<ContentCodec> supportedCodings) {
        this.requestCodings = emptyList();
        this.responseCodings = new ArrayList<>(supportedCodings);
    }

    /**
     * Enable support of the provided encodings for both client requests and server responses.
     * The encodings can differ for requests and responses, allowing a server that supports different compressions for
     * requests and different ones for responses.
     * <p>
     * To disable support of compressed requests use an {@link Collections#emptyList()} for the
     * <code>supportedRequestCodings</code> param or use {@link #ContentCodingHttpServiceFilter(List)} constructor
     * instead.
     * <p>
     * The order of the codecs provided, affect selection priority alongside the order of the incoming
     * <a href="https://tools.ietf.org/html/rfc7231#section-5.3.4">accept-encoding</a> header from the client.
     *
     * @param supportedRequestCodings the codecs used to decompress client requests if compressed.
     * @param supportedResponseCodings the codecs used to compress server responses if client accepts them.
     */
    public ContentCodingHttpServiceFilter(final List<ContentCodec> supportedRequestCodings,
                                          final List<ContentCodec> supportedResponseCodings) {
        this.requestCodings = new ArrayList<>(supportedRequestCodings);
        this.responseCodings = new ArrayList<>(supportedResponseCodings);
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
                            encodePayloadContentIfAvailable(request.headers(), request.method(), responseCodings,
                                    response, allocator);
                            return response;
                        }).subscribeShareContext();
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
                                                        final HttpRequestMethod method,
                                                        final List<ContentCodec> supportedEncodings,
                                                        final StreamingHttpResponse response,
                                                        final BufferAllocator allocator) {
        if (supportedEncodings.isEmpty() || hasContentEncoding(response.headers()) ||
                isPassThrough(method, response)) {
            return;
        }

        ContentCodec coding = codingForResponse(requestHeaders, response, supportedEncodings);
        if (coding != null) {
            addContentEncoding(response.headers(), coding.name());
            response.transformPayloadBody(bufferPublisher -> coding.encode(bufferPublisher, allocator));
        }
    }

    private static boolean isPassThrough(final HttpRequestMethod method, final StreamingHttpResponse response) {
        // see. https://tools.ietf.org/html/rfc7230#section-3.3.3
        // The length of a message body is determined by one of the following
        //         (in order of precedence):
        //
        // 1.  Any response to a HEAD request and any response with a 1xx
        //         (Informational), 204 (No Content), or 304 (Not Modified) status
        // code is always terminated by the first empty line after the
        // header fields, regardless of the header fields present in the
        // message, and thus cannot contain a message body.
        //
        // 2.  Any 2xx (Successful) response to a CONNECT request implies that
        // the connection will become a tunnel immediately after the empty
        // line that concludes the header fields.  A client MUST ignore any
        // Content-Length or Transfer-Encoding header fields received in
        // such a message.
        // ...

        int code = response.status().code();
        return INFORMATIONAL_1XX.contains(code) || code == NO_CONTENT.code() || code == NOT_MODIFIED.code() ||
                (method == HEAD || (method == CONNECT && SUCCESSFUL_2XX.contains(code)));
    }

    @Nullable
    private static ContentCodec codingForResponse(final HttpHeaders requestHeaders,
                                                  final StreamingHttpResponse response,
                                                  final List<ContentCodec> supportedEncodings) {
        // Enforced selection
        ContentCodec encoding = response.encoding();
        if (encoding == null) {
            // Negotiated from client headers and server config
            encoding = negotiateAcceptedEncoding(requestHeaders.get(ACCEPT_ENCODING), supportedEncodings);
        }

        return identity().equals(encoding) ? null : encoding;
    }
}
