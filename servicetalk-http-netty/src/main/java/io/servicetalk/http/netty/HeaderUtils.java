/*
 * Copyright © 2018-2021 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.buffer.api.CharSequences;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.ScanWithMapper;
import io.servicetalk.http.api.EmptyHttpHeaders;
import io.servicetalk.http.api.HttpHeaderNames;
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpMetaData;
import io.servicetalk.http.api.HttpProtocolVersion;
import io.servicetalk.http.api.HttpRequestMethod;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;

import io.netty.util.AsciiString;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Predicate;
import javax.annotation.Nullable;

import static io.servicetalk.buffer.api.CharSequences.parseLong;
import static io.servicetalk.concurrent.api.Publisher.empty;
import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.concurrent.api.Publisher.fromIterable;
import static io.servicetalk.http.api.HeaderUtils.isTransferEncodingChunked;
import static io.servicetalk.http.api.HttpApiConversions.isPayloadEmpty;
import static io.servicetalk.http.api.HttpApiConversions.isSafeToAggregate;
import static io.servicetalk.http.api.HttpApiConversions.mayHaveTrailers;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_LENGTH;
import static io.servicetalk.http.api.HttpHeaderNames.TRANSFER_ENCODING;
import static io.servicetalk.http.api.HttpHeaderValues.CHUNKED;
import static io.servicetalk.http.api.HttpHeaderValues.ZERO;
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
        return canAddContentLength(request) && clientMaySendPayloadBodyFor(request.method());
    }

    static boolean canAddResponseContentLength(final StreamingHttpResponse response,
                                               final HttpRequestMethod requestMethod) {
        return canAddContentLength(response) && serverMaySendPayloadBodyFor(response.status().code(), requestMethod);
    }

    static boolean clientMaySendPayloadBodyFor(final HttpRequestMethod requestMethod) {
        // A client MUST NOT send a message body in a TRACE request.
        // https://tools.ietf.org/html/rfc7231#section-4.3.8
        return !TRACE.equals(requestMethod);
    }

    static boolean serverMaySendPayloadBodyFor(final int statusCode, final HttpRequestMethod requestMethod) {
        // (for HEAD) the server MUST NOT send a message body in the response.
        // https://tools.ietf.org/html/rfc7231#section-4.3.2
        return !HEAD.equals(requestMethod) && !isEmptyResponseStatus(statusCode)
                && !isEmptyConnectResponse(requestMethod, statusCode);
    }

    private static boolean canAddContentLength(final HttpMetaData metadata) {
        return (isPayloadEmpty(metadata) || isSafeToAggregate(metadata)) &&
                (metadata.version().major() > 1 || !mayHaveTrailers(metadata)) &&
                !hasContentHeaders(metadata.headers());
    }

    static Publisher<Object> setRequestContentLength(final HttpProtocolVersion protocolVersion,
                                                     final StreamingHttpRequest request) {
        return setContentLength(request, request.messageBody(),
                shouldAddZeroContentLength(request.method()) ? HeaderUtils::updateContentLength :
                        HeaderUtils::updateRequestContentLengthNonZero, protocolVersion);
    }

    static Publisher<Object> setResponseContentLength(final HttpProtocolVersion protocolVersion,
                                                      final StreamingHttpResponse response) {
        return setContentLength(response, response.messageBody(), HeaderUtils::updateContentLength, protocolVersion);
    }

    private static void updateRequestContentLengthNonZero(final int contentLength, final HttpHeaders headers) {
        if (contentLength > 0) {
            headers.set(CONTENT_LENGTH, Integer.toString(contentLength));
        }
    }

    private static void updateContentLength(final int contentLength, final HttpHeaders headers) {
        assert contentLength >= 0;
        headers.set(CONTENT_LENGTH, contentLength == 0 ? ZERO : Integer.toString(contentLength));
    }

    static boolean shouldAddZeroContentLength(final HttpRequestMethod requestMethod) {
        // A user agent SHOULD NOT send a Content-Length header field when the request message does not contain a
        // payload body and the method semantics do not anticipate such a body.
        // https://tools.ietf.org/html/rfc7230#section-3.3.2
        return POST.equals(requestMethod) || PUT.equals(requestMethod) || PATCH.equals(requestMethod);
    }

    static boolean responseMayHaveContent(final int statusCode,
                                          final HttpRequestMethod requestMethod) {
        return !isEmptyResponseStatus(statusCode) && !isEmptyConnectResponse(requestMethod, statusCode);
    }

    static ScanWithMapper<Object, Object> insertTrailersMapper() {
        return new ScanWithMapper<Object, Object>() {

            private boolean sawHeaders;

            @Nullable
            @Override
            public Object mapOnNext(@Nullable final Object next) {
                if (next instanceof HttpHeaders) {
                    sawHeaders = true;
                }
                return next;
            }

            @Nullable
            @Override
            public Object mapOnError(final Throwable t) throws Throwable {
                throw t;
            }

            @Override
            public Object mapOnComplete() {
                return EmptyHttpHeaders.INSTANCE;
            }

            @Override
            public boolean mapTerminal() {
                return !sawHeaders;
            }
        };
    }

    static boolean emptyMessageBody(final HttpMetaData metadata, final Publisher<Object> messageBody) {
        return messageBody == empty() || emptyMessageBody(metadata);
    }

    static boolean emptyMessageBody(final HttpMetaData metadata) {
        return isPayloadEmpty(metadata) && !mayHaveTrailers(metadata);
    }

    static Publisher<Object> flatEmptyMessage(final HttpProtocolVersion protocolVersion,
                                              final HttpMetaData metadata, final Publisher<Object> messageBody) {
        assert emptyMessageBody(metadata, messageBody);
        // HTTP/2 and above can write meta-data as a single frame with endStream=true flag. To check the version, use
        // HttpProtocolVersion from ConnectionInfo because HttpMetaData may have different version.
        final Publisher<Object> flatMessage = protocolVersion.major() > 1 ? from(metadata) :
                from(metadata, EmptyHttpHeaders.INSTANCE);
        return messageBody == empty() ? flatMessage :
                // Subscribe to the messageBody publisher to trigger any applied transformations, but ignore its
                // content because the PayloadInfo indicated it's effectively empty and does not contain trailers
                flatMessage.concat(messageBody.ignoreElements());
    }

    private static Publisher<Object> setContentLength(final HttpMetaData metadata,
                                                      final Publisher<Object> messageBody,
                                                      final BiIntConsumer<HttpHeaders> contentLengthUpdater,
                                                      final HttpProtocolVersion protocolVersion) {
        if (emptyMessageBody(metadata, messageBody)) {
            contentLengthUpdater.apply(0, metadata.headers());
            return flatEmptyMessage(protocolVersion, metadata, messageBody);
        }
        return messageBody.collect(() -> null, (reduction, item) -> {
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
                // this method is called if the payload has been aggregated, we expect <buffer*, trailers?>.
                items = new ArrayList<>(2);
                items.add(reduction);
            }
            items.add(item);
            return items;
        }).flatMapPublisher(reduction -> {
            int contentLength = 0;
            final Publisher<Object> flatRequest;
            if (reduction == null) {
                flatRequest = from(metadata, EmptyHttpHeaders.INSTANCE);
            } else if (reduction instanceof Buffer) {
                final Buffer buffer = (Buffer) reduction;
                contentLength = buffer.readableBytes();
                flatRequest = from(metadata, buffer, EmptyHttpHeaders.INSTANCE);
            } else if (reduction instanceof List) {
                @SuppressWarnings("unchecked")
                final List<Object> items = (List<Object>) reduction;
                for (Object item : items) {
                    if (item instanceof Buffer) {
                        contentLength += ((Buffer) item).readableBytes();
                    }
                }
                if (!(items.get(items.size() - 1) instanceof HttpHeaders)) {
                    items.add(EmptyHttpHeaders.INSTANCE);
                }
                flatRequest = Publisher.<Object>from(metadata).concat(fromIterable(items));
            } else if (reduction instanceof HttpHeaders) {
                flatRequest = from(metadata, reduction);
            } else {
                throw new IllegalArgumentException("unsupported payload chunk type: " + reduction);
            }
            contentLengthUpdater.apply(contentLength, metadata.headers());
            return flatRequest;
        });
    }

    static void addResponseTransferEncodingIfNecessary(final StreamingHttpResponse response,
                                                       final HttpRequestMethod requestMethod) {
        if (serverMaySendPayloadBodyFor(response.status().code(), requestMethod) &&
                canAddTransferEncodingChunked(response)) {
            response.headers().add(TRANSFER_ENCODING, CHUNKED);
        }
    }

    static void addRequestTransferEncodingIfNecessary(final StreamingHttpRequest request) {
        if (clientMaySendPayloadBodyFor(request.method()) && canAddTransferEncodingChunked(request)) {
            request.headers().add(TRANSFER_ENCODING, CHUNKED);
        }
    }

    private static boolean canAddTransferEncodingChunked(final HttpMetaData metaData) {
        final HttpHeaders headers = metaData.headers();
        return ((chunkedSupported(metaData.version()) && mayHaveTrailers(metaData)) ||
                !headers.contains(CONTENT_LENGTH)) && !isTransferEncodingChunked(headers);
    }

    private static boolean chunkedSupported(final HttpProtocolVersion version) {
        return version.major() == 1 && version.minor() > 0;
    }

    static boolean hasContentHeaders(final HttpHeaders headers) {
        return headers.contains(CONTENT_LENGTH) || isTransferEncodingChunked(headers);
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

    /**
     * Extracts the {@link HttpHeaderNames#CONTENT_LENGTH content-length} value from the passed values iterator.
     * <p>
     * This utility validates that there is no more than one {@link HttpHeaderNames#CONTENT_LENGTH content-length}
     * header present and it has a valid number format.
     *
     * @param iterator the {@link Iterator} over content-length header values
     * @return the normalized content-length from the headers or {@code -1} if no content-length header is found
     * @throws IllegalArgumentException if multiple content-length header values are present
     */
    static long contentLength(final Iterator<? extends CharSequence> iterator) {
        if (!iterator.hasNext()) {
            return -1;
        }

        // Guard against multiple Content-Length headers as stated in
        // https://tools.ietf.org/html/rfc7230#section-3.3.2:
        //
        //   If a message is received that has multiple Content-Length header
        //   fields with field-values consisting of the same decimal value, or a
        //   single Content-Length header field with a field value containing a
        //   list of identical decimal values (e.g., "Content-Length: 42, 42"),
        //   indicating that duplicate Content-Length header fields have been
        //   generated or combined by an upstream message processor, then the
        //   recipient MUST either reject the message as invalid or replace the
        //   duplicated field-values with a single valid Content-Length field
        //   containing that decimal value prior to determining the message body
        //   length or forwarding the message.
        CharSequence firstValue = iterator.next();
        if (iterator.hasNext()) {
            throw multipleCL(firstValue, iterator);
        }

        char firstChar = firstValue.charAt(0);
        if (firstChar < '0' || firstChar > '9') {   // allow numbers only in ASCII or ISO-8859-1 encoding
            // prevent signed content-length values: -digit or +digit
            throw malformedCL(firstValue);
        }
        final long value;
        try {   // optimistically assume the value can be parsed to skip indexOf check
             value = parseLong(firstValue);
        } catch (NumberFormatException e) {
            if (CharSequences.indexOf(firstValue, ',', 0) >= 0) {
                throw multipleCL(firstValue, null);
            }
            throw malformedCL(firstValue);
        }
        return value;
    }

    private static IllegalArgumentException malformedCL(final CharSequence value) {
        return new IllegalArgumentException("Malformed 'content-length' value: " + value);
    }

    private static IllegalArgumentException multipleCL(final CharSequence firstValue,
                                                       @Nullable final Iterator<? extends CharSequence> iterator) {
        final CharSequence allClValues;
        if (iterator == null) {
            allClValues = firstValue;
        } else {
            final StringBuilder sb = new StringBuilder(firstValue.length() + 8).append(firstValue);
            while (iterator.hasNext()) {
                sb.append(", ").append(iterator.next());
            }
            allClValues = sb;
        }
        return new IllegalArgumentException("Multiple content-length values found: " + allClValues);
    }

    /**
     * A special consumer that takes an {@code int} and a custom argument and returns the result.
     *
     * @param <T> The other argument to this function.
     */
    @FunctionalInterface
    private interface BiIntConsumer<T> {
        /**
         * Evaluates this consumer on the given arguments.
         *
         * @param i The {@code int} argument.
         * @param t The {@link T} argument.
         */
        void apply(int i, T t);
    }
}
