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
import io.servicetalk.http.api.CharSequences;
import io.servicetalk.http.api.EmptyHttpHeaders;
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpMetaData;
import io.servicetalk.http.api.HttpRequestMethod;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;

import io.netty.util.AsciiString;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Predicate;

import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.api.Publisher.fromIterable;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.http.api.HeaderUtils.isTransferEncodingChunked;
import static io.servicetalk.http.api.HttpApiConversions.isSafeToAggregate;
import static io.servicetalk.http.api.HttpApiConversions.mayHaveTrailers;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_ENCODING;
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
    static final Predicate<Object> LAST_CHUNK_PREDICATE = p -> p instanceof HttpHeaders;

    private HeaderUtils() {
        // no instances
    }

    static int indexOf(CharSequence sequence, char c, int fromIndex) {
        return sequence instanceof AsciiString ? ((AsciiString) sequence).indexOf(c, fromIndex) :
                CharSequences.indexOf(sequence, c, fromIndex);
    }

    static void removeTransferEncodingChunked(final HttpHeaders headers) {
        final Iterator<? extends CharSequence> itr = headers.valuesIterator(TRANSFER_ENCODING);
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
        return clientMaySendPayloadBodyFor(request.method());
    }

    static boolean canAddResponseContentLength(final StreamingHttpResponse response,
                                               final HttpRequestMethod requestMethod) {
        return canAddContentLength(response) && shouldAddZeroContentLength(response.status().code(), requestMethod)
                // HEAD requests should either have the content-length already set (= what GET will return) or
                // have the header omitted when unknown, but never have any payload anyway so don't try to infer it
                && !isHeadResponse(requestMethod);
    }

    static boolean canAddRequestTransferEncoding(final StreamingHttpRequest request) {
        return !hasContentHeaders(request.headers()) && clientMaySendPayloadBodyFor(request.method());
    }

    static boolean clientMaySendPayloadBodyFor(final HttpRequestMethod requestMethod) {
        // A client MUST NOT send a message body in a TRACE request.
        // https://tools.ietf.org/html/rfc7231#section-4.3.8
        return !TRACE.equals(requestMethod);
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
        return !HEAD.equals(requestMethod) && !isEmptyResponseStatus(statusCode)
                && !isEmptyConnectResponse(requestMethod, statusCode);
    }

    private static boolean canAddContentLength(final HttpMetaData metadata) {
        // TODO once this bug is addressed (https://github.com/apple/servicetalk/pull/1213)
        // we should relax the check here, remove content-encoding clause
        return !hasContentHeaders(metadata.headers()) && !hasContentEncoding(metadata.headers()) &&
                isSafeToAggregate(metadata) && !mayHaveTrailers(metadata);
    }

    static Publisher<Object> setRequestContentLength(final StreamingHttpRequest request) {
        return setContentLength(request, request.payloadBodyAndTrailers(),
                shouldAddZeroContentLength(request.method()) ? HeaderUtils::updateRequestContentLength :
                        HeaderUtils::updateRequestContentLengthNonZero);
    }

    static Publisher<Object> setResponseContentLength(final StreamingHttpResponse response) {
        return setContentLength(response, response.payloadBodyAndTrailers(), HeaderUtils::updateResponseContentLength);
    }

    private static void updateRequestContentLengthNonZero(final int contentLength, final HttpHeaders headers) {
        if (contentLength > 0) {
            headers.set(CONTENT_LENGTH, Integer.toString(contentLength));
        }
    }

    private static void updateRequestContentLength(final int contentLength, final HttpHeaders headers) {
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

    private static boolean isHeadResponse(final HttpRequestMethod requestMethod) {
        return HEAD.equals(requestMethod);
    }

    private static void updateResponseContentLength(final int contentLength, final HttpHeaders headers) {
        headers.set(CONTENT_LENGTH, Integer.toString(contentLength));
    }

    private static Publisher<Object> setContentLength(final HttpMetaData metadata,
                                                      final Publisher<Object> originalPayloadAndTrailers,
                                                      final BiIntConsumer<HttpHeaders> contentLengthUpdater) {
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
        }).flatMapPublisher(reduction -> {
            int contentLength = 0;
            final Publisher<Object> flatRequest;
            if (reduction == null) {
                flatRequest = from(metadata, EmptyHttpHeaders.INSTANCE);
            } else if (reduction instanceof List) {
                final List<?> items = (List<?>) reduction;
                for (Object item : items) {
                    contentLength += calculateContentLength(item);
                }
                flatRequest = Publisher.<Object>from(metadata)
                        .concat(fromIterable(items))
                        .concat(succeeded(EmptyHttpHeaders.INSTANCE));
            } else if (reduction instanceof Buffer) {
                final Buffer buffer = (Buffer) reduction;
                contentLength = buffer.readableBytes();
                flatRequest = from(metadata, buffer, EmptyHttpHeaders.INSTANCE);
            } else if (reduction instanceof HttpHeaders) {
                flatRequest = from(metadata, reduction);
            } else {
                throw new IllegalArgumentException("Unknown object " + reduction + " found as payload");
            }
            contentLengthUpdater.apply(contentLength, metadata.headers());
            return flatRequest;
        });
    }

    static int calculateContentLength(Object item) {
        // TODO(scott): add support for file region
        if (item instanceof Buffer) {
            return calculateContentLength((Buffer) item);
        }
        throw new IllegalArgumentException("Unknown object " + item + " found as payload");
    }

    static int calculateContentLength(Buffer item) {
        return item.readableBytes();
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

    private static boolean hasContentEncoding(final HttpHeaders headers) {
        return headers.contains(CONTENT_ENCODING);
    }

    private static boolean isEmptyConnectResponse(final HttpRequestMethod requestMethod, final int statusCode) {
        // A server MUST NOT send any Transfer-Encoding or Content-Length header fields in a 2xx (Successful) response
        // to CONNECT.
        // https://tools.ietf.org/html/rfc7231#section-4.3.6
        return CONNECT.equals(requestMethod) && SUCCESSFUL_2XX.contains(statusCode);
    }

    private static boolean isEmptyResponseStatus(final int statusCode) {
        // A server MUST NOT send a Content-Length header field in any response with a status code of
        // 1xx (Informational) or 204 (No Content).
        // https://tools.ietf.org/html/rfc7230#section-3.3.2
        return INFORMATIONAL_1XX.contains(statusCode) || statusCode == NO_CONTENT.code();
    }

    @FunctionalInterface
    private interface BiIntConsumer<T> {
        void apply(int contentLength, T headers);
    }
}
