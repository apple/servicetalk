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
package io.servicetalk.http.netty;

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.CharSequences;
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpHeadersFactory;
import io.servicetalk.http.api.HttpMetaData;
import io.servicetalk.http.api.HttpRequestMethod;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;

import io.netty.util.AsciiString;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.api.Publisher.fromIterable;
import static io.servicetalk.http.api.HttpApiConversions.isSafeToAggregate;
import static io.servicetalk.http.api.HttpApiConversions.mayHaveTrailers;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_LENGTH;
import static io.servicetalk.http.api.HttpHeaderNames.TRANSFER_ENCODING;
import static io.servicetalk.http.api.HttpHeaderValues.CHUNKED;
import static io.servicetalk.http.api.HttpRequestMethod.CONNECT;
import static io.servicetalk.http.api.HttpRequestMethod.HEAD;
import static io.servicetalk.http.api.HttpRequestMethod.PATCH;
import static io.servicetalk.http.api.HttpRequestMethod.POST;
import static io.servicetalk.http.api.HttpRequestMethod.PUT;
import static io.servicetalk.http.api.HttpRequestMethod.TRACE;
import static io.servicetalk.http.api.HttpResponseStatus.NO_CONTENT;
import static io.servicetalk.http.api.HttpResponseStatus.StatusClass.INFORMATIONAL_1XX;
import static io.servicetalk.http.api.HttpResponseStatus.StatusClass.SUCCESSFUL_2XX;

final class HeaderUtils {

    private HeaderUtils() {
        // no instances
    }

    static int indexOf(CharSequence sequence, char c, int fromIndex) {
        return sequence instanceof AsciiString ? ((AsciiString) sequence).indexOf(c, fromIndex) :
                CharSequences.indexOf(sequence, c, fromIndex);
    }

    static boolean isTransferEncodingChunked(final HttpHeaders headers) {
        return headers.contains(TRANSFER_ENCODING, CHUNKED, true);
    }

    static void removeTransferEncodingChunked(final HttpHeaders headers) {
        final Iterator<? extends CharSequence> itr = headers.values(TRANSFER_ENCODING);
        while (itr.hasNext()) {
            if (io.netty.handler.codec.http.HttpHeaderValues.CHUNKED.contentEqualsIgnoreCase(itr.next())) {
                itr.remove();
            }
        }
    }

    static boolean canAddRequestContentLength(final StreamingHttpRequest request) {
        if (!canAddContentLength(request)) {
            return false;
        }
        return canAddRequestTransferEncodingProtocol(request.method());
    }

    static boolean canAddResponseContentLength(final StreamingHttpResponse response,
                                               final HttpRequestMethod requestMethod) {
        return canAddContentLength(response) && shouldAddZeroContentLength(response.status().code(), requestMethod);
    }

    static boolean canAddRequestTransferEncoding(final StreamingHttpRequest request) {
        return !hasContentHeaders(request.headers()) && canAddRequestTransferEncodingProtocol(request.method());
    }

    static boolean canAddRequestTransferEncodingProtocol(final HttpRequestMethod requestMethod) {
        // A client MUST NOT send a message body in a TRACE request.
        // https://tools.ietf.org/html/rfc7231#section-4.3.8
        return !TRACE.name().equals(requestMethod.name());
    }

    static boolean canAddResponseTransferEncoding(final StreamingHttpResponse response,
                                                  final HttpRequestMethod requestMethod) {
        return !hasContentHeaders(response.headers()) &&
                canAddResponseTransferEncodingProtocol(response.status().code(), requestMethod);
    }

    static boolean canAddResponseTransferEncodingProtocol(final int statusCode,
                                                          final HttpRequestMethod requestMethod) {
        // (for HEAD) the server MUST NOT send a message body in the response.
        // https://tools.ietf.org/html/rfc7231#section-4.3.2
        return !HEAD.name().equals(requestMethod.name()) && !isEmptyResponseStatus(statusCode)
                && !isEmptyConnectResponse(requestMethod, statusCode);
    }

    private static boolean canAddContentLength(final HttpMetaData metadata) {
        return !hasContentHeaders(metadata.headers()) &&
                isSafeToAggregate(metadata) && !mayHaveTrailers(metadata);
    }

    static Single<Publisher<Object>> setRequestContentLength(final StreamingHttpRequest request,
                                                             final HttpHeadersFactory headersFactory) {
        return setContentLength(request, request.payloadBodyAndTrailers(), headersFactory,
                shouldAddZeroContentLength(request.method()) ? HeaderUtils::updateRequestContentLength :
                        HeaderUtils::updateRequestContentLengthNonZero);
    }

    static Single<Publisher<Object>> setResponseContentLength(final StreamingHttpResponse response,
                                                              final HttpHeadersFactory headersFactory) {
        return setContentLength(response, response.payloadBodyAndTrailers(), headersFactory,
                HeaderUtils::updateResponseContentLength);
    }

    private static void updateRequestContentLengthNonZero(final HttpHeaders headers, final int contentLength) {
        if (contentLength > 0) {
            headers.set(CONTENT_LENGTH, Integer.toString(contentLength));
        }
    }

    private static void updateRequestContentLength(final HttpHeaders headers, final int contentLength) {
        assert contentLength >= 0;
        headers.set(CONTENT_LENGTH, Integer.toString(contentLength));
    }

    static boolean shouldAddZeroContentLength(final HttpRequestMethod requestMethod) {
        // A user agent SHOULD NOT send a Content-Length header field when the request message does not contain a
        // payload body and the method semantics do not anticipate such a body.
        // https://tools.ietf.org/html/rfc7230#section-3.3.2
        return POST.equals(requestMethod) || PUT.equals(requestMethod) || PATCH.equals(requestMethod);
    }

    static boolean shouldAddZeroContentLength(final int statusCode,
                                              final HttpRequestMethod requestMethod) {
        return !isEmptyResponseStatus(statusCode) && !isEmptyConnectResponse(requestMethod, statusCode);
    }

    private static void updateResponseContentLength(final HttpHeaders headers, final int contentLength) {
        headers.set(CONTENT_LENGTH, Integer.toString(contentLength));
    }

    private static Single<Publisher<Object>> setContentLength(final HttpMetaData metadata,
                                                              final Publisher<Object> originalPayloadAndTrailers,
                                                              final HttpHeadersFactory headersFactory,
                                                              final ContentLengthUpdater contentLengthUpdater) {
        return originalPayloadAndTrailers.collect(() -> null, (reduction, item) -> {
            if (reduction == null) {
                // avoid allocating a list if the Publisher emits only a single Buffer
                return item;
            }
            List<Object> items;
            if (reduction instanceof List) {
                @SuppressWarnings("unchecked")
                List<Object> itemsUnchecked = (List<Object>) reduction;
                items = itemsUnchecked;
            } else {
                items = new ArrayList<>();
                items.add(reduction);
            }
            items.add(item);
            return items;
        }).map(reduction -> {
            int contentLength = 0;
            final Publisher<Object> payloadAndTrailer;
            if (reduction == null) {
                payloadAndTrailer = from(metadata, headersFactory.newEmptyTrailers());
            } else if (reduction instanceof List) {
                @SuppressWarnings("unchecked")
                final List<Object> items = (List<Object>) reduction;
                for (int i = 0; i < items.size(); i++) {
                    Object item = items.get(i);
                    if (item instanceof Buffer) {
                        contentLength += ((Buffer) item).readableBytes();
                    }
                }
                payloadAndTrailer = Publisher.<Object>from(metadata)
                        .concat(fromIterable(items));
            } else if (reduction instanceof Buffer) {
                final Buffer buffer = (Buffer) reduction;
                contentLength = buffer.readableBytes();
                payloadAndTrailer = from(metadata, buffer, headersFactory.newEmptyTrailers());
            } else {
                contentLength = -1; // We have seen unknown entities, so skip adding content-length.
                payloadAndTrailer = from(metadata, reduction);
            }
            if (contentLength >= 0) {
                contentLengthUpdater.apply(metadata.headers(), contentLength);
            }
            return payloadAndTrailer;
        });
    }

    static StreamingHttpResponse addResponseTransferEncodingIfNecessary(final StreamingHttpResponse response,
                                                                        final HttpRequestMethod requestMethod) {
        if (canAddResponseTransferEncoding(response, requestMethod)) {
            response.headers().add(TRANSFER_ENCODING, CHUNKED);
        }
        return response;
    }

    static void addRequestTransferEncodingIfNecessary(final StreamingHttpRequest request) {
        if (canAddRequestTransferEncoding(request)) {
            request.headers().add(TRANSFER_ENCODING, CHUNKED);
        }
    }

    private static boolean hasContentHeaders(final HttpHeaders headers) {
        return headers.contains(CONTENT_LENGTH) || isTransferEncodingChunked(headers);
    }

    private static boolean isEmptyConnectResponse(final HttpRequestMethod requestMethod, final int statusCode) {
        // A server MUST NOT send any Transfer-Encoding or Content-Length header fields in a 2xx (Successful) response
        // to CONNECT.
        // https://tools.ietf.org/html/rfc7231#section-4.3.6
        return CONNECT.name().equals(requestMethod.name()) && SUCCESSFUL_2XX.contains(statusCode);
    }

    private static boolean isEmptyResponseStatus(final int statusCode) {
        // A server MUST NOT send a Content-Length header field in any response with a status code of
        // 1xx (Informational) or 204 (No Content).
        // https://tools.ietf.org/html/rfc7230#section-3.3.2
        return INFORMATIONAL_1XX.contains(statusCode) || statusCode == NO_CONTENT.code();
    }

    @FunctionalInterface
    private interface ContentLengthUpdater {
        void apply(HttpHeaders headers, int contentLength);
    }
}
