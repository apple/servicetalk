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
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpMetaData;
import io.servicetalk.http.api.HttpRequestMethod;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static io.servicetalk.http.api.HttpApiConversions.isSafeToAggregate;
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
        final HttpRequestMethod requestMethod = request.method();
        // A client MUST NOT send a message body in a TRACE request.
        // https://tools.ietf.org/html/rfc7231#section-4.3.8
        return !TRACE.name().equals(requestMethod.name());
    }

    static boolean canAddResponseContentLength(final StreamingHttpResponse response,
                                               final HttpRequestMethod requestMethod) {
        if (!canAddContentLength(response)) {
            return false;
        }
        final int statusCode = response.status().code();
        return !isEmptyResponseStatus(statusCode) && !isEmptyConnectResponse(requestMethod, statusCode);
    }

    static boolean canAddRequestTransferEncoding(final StreamingHttpRequest request) {
        if (hasContentHeaders(request.headers())) {
            return false;
        }
        final HttpRequestMethod requestMethod = request.method();
        // A client MUST NOT send a message body in a TRACE request.
        // https://tools.ietf.org/html/rfc7231#section-4.3.8
        return !TRACE.name().equals(requestMethod.name());
    }

    static boolean canAddResponseTransferEncoding(final StreamingHttpResponse response,
                                                  final HttpRequestMethod requestMethod) {
        if (hasContentHeaders(response.headers())) {
            return false;
        }
        final int statusCode = response.status().code();
        // (for HEAD) the server MUST NOT send a message body in the response.
        // https://tools.ietf.org/html/rfc7231#section-4.3.2
        return !HEAD.name().equals(requestMethod.name()) && !isEmptyResponseStatus(statusCode)
                && !isEmptyConnectResponse(requestMethod, statusCode);
    }

    private static boolean canAddContentLength(final HttpMetaData metadata) {
        return !hasContentHeaders(metadata.headers()) &&
                isSafeToAggregate(metadata);
    }

    static Single<StreamingHttpRequest> setRequestContentLength(final StreamingHttpRequest request) {
        return setContentLength(request, request.payloadBody(), HeaderUtils::requestContentLengthPayloadHandler);
    }

    static Single<StreamingHttpResponse> setResponseContentLength(final StreamingHttpResponse response) {
        return setContentLength(response, response.payloadBody(), HeaderUtils::responseContentLengthPayloadHandler);
    }

    private static StreamingHttpRequest requestContentLengthPayloadHandler(final StreamingHttpRequest request,
                                                                           final int contentLength,
                                                                           final Publisher<Buffer> payload) {
        if (contentLength > 0 || shouldAddZeroContentLength(request)) {
            request.headers().set(CONTENT_LENGTH, Integer.toString(contentLength));
        }
        return request.payloadBody(payload);
    }

    private static boolean shouldAddZeroContentLength(final StreamingHttpRequest request) {
        final HttpRequestMethod requestMethod = request.method();
        final String requestMethodName = requestMethod.name();
        // A user agent SHOULD NOT send a Content-Length header field when the request message does not contain a
        // payload body and the method semantics do not anticipate such a body.
        // https://tools.ietf.org/html/rfc7230#section-3.3.2
        return (POST.name().equals(requestMethodName) ||
                PUT.name().equals(requestMethodName) ||
                PATCH.name().equals(requestMethodName));
    }

    private static StreamingHttpResponse responseContentLengthPayloadHandler(final StreamingHttpResponse response,
                                                                             final int contentLength,
                                                                             final Publisher<Buffer> payload) {
        response.headers().set(CONTENT_LENGTH, Integer.toString(contentLength));
        return response.payloadBody(payload);
    }

    @SuppressWarnings("unchecked")
    private static <T> Single<T> setContentLength(final T metadata, final Publisher<Buffer> originalPayload,
                                                  final ContentLengthPayloadHandler<T> contentLengthPayloadHandler) {
        return originalPayload.collect(() -> null, (reduction, item) -> {
            final List<Buffer> buffers;
            if (reduction == null) {
                // avoid allocating a list if the Publisher emits only a single Buffer
                return item;
            } else if (reduction instanceof Buffer) {
                buffers = new ArrayList<>();
                buffers.add((Buffer) reduction);
            } else {
                buffers = (List<Buffer>) reduction;
            }
            buffers.add(item);
            return buffers;
        }).map(reduction -> {
            int contentLength = 0;
            final Publisher<Buffer> payload;
            if (reduction == null) {
                payload = Publisher.empty();
            } else if (reduction instanceof Buffer) {
                final Buffer buffer = (Buffer) reduction;
                contentLength = buffer.readableBytes();
                payload = Publisher.from(buffer);
            } else {
                final List<Buffer> buffers = (List<Buffer>) reduction;
                for (int i = 0; i < buffers.size(); i++) {
                    contentLength += buffers.get(i).readableBytes();
                }
                payload = Publisher.fromIterable(buffers);
            }
            return contentLengthPayloadHandler.apply(metadata, contentLength, payload);
        });
    }

    static StreamingHttpResponse addResponseTransferEncodingIfNecessary(final StreamingHttpResponse response,
                                                                        final HttpRequestMethod requestMethod) {
        if (canAddResponseTransferEncoding(response, requestMethod)) {
            response.headers().add(TRANSFER_ENCODING, CHUNKED);
        }
        return response;
    }

    static StreamingHttpRequest addRequestTransferEncodingIfNecessary(final StreamingHttpRequest request) {
        if (canAddRequestTransferEncoding(request)) {
            request.headers().add(TRANSFER_ENCODING, CHUNKED);
        }
        return request;
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
    private interface ContentLengthPayloadHandler<T> {
        T apply(T request, int contentLength, Publisher<Buffer> payload);
    }
}
