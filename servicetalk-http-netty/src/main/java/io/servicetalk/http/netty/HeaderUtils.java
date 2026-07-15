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
import io.servicetalk.concurrent.api.ScanMapper;
import io.servicetalk.concurrent.api.ScanMapper.MappedTerminal;
import io.servicetalk.http.api.EmptyHttpHeaders;
import io.servicetalk.http.api.HttpHeaderNames;
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpMetaData;
import io.servicetalk.http.api.HttpProtocolVersion;
import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.http.api.HttpRequestMethod;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;

import io.netty.util.AsciiString;

import java.util.ArrayList;
import java.util.Iterator;
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
import static io.servicetalk.http.api.HttpHeaderNames.EXPECT;
import static io.servicetalk.http.api.HttpHeaderNames.TRANSFER_ENCODING;
import static io.servicetalk.http.api.HttpHeaderValues.CHUNKED;
import static io.servicetalk.http.api.HttpHeaderValues.CONTINUE;
import static io.servicetalk.http.api.HttpHeaderValues.ZERO;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_1;
import static io.servicetalk.http.api.HttpRequestMethod.CONNECT;
import static io.servicetalk.http.api.HttpRequestMethod.HEAD;
import static io.servicetalk.http.api.HttpRequestMethod.PATCH;
import static io.servicetalk.http.api.HttpRequestMethod.POST;
import static io.servicetalk.http.api.HttpRequestMethod.PUT;
import static io.servicetalk.http.api.HttpRequestMethod.TRACE;
import static io.servicetalk.http.api.HttpResponseStatus.NOT_MODIFIED;
import static io.servicetalk.http.api.HttpResponseStatus.NO_CONTENT;
import static io.servicetalk.http.api.HttpResponseStatus.StatusClass.INFORMATIONAL_1XX;
import static io.servicetalk.http.api.HttpResponseStatus.StatusClass.SUCCESSFUL_2XX;

final class HeaderUtils {

    // Predicates that validate when `expect: 100-continue` feature has to be handled.
    static final Predicate<HttpRequestMetaData> REQ_EXPECT_CONTINUE = reqMetaData -> {
        // Versions prior HTTP/1.1 do not support Expect-Continue
        return reqMetaData.version().compareTo(HTTP_1_1) >= 0 &&
                reqMetaData.headers().containsIgnoreCase(EXPECT, CONTINUE);
    };

    static final Predicate<Object> OBJ_EXPECT_CONTINUE = msg ->
            msg instanceof HttpRequestMetaData && REQ_EXPECT_CONTINUE.test((HttpRequestMetaData) msg);

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
        // Body-derived auto-CL only applies when a body is allowed. For body-forbidden responses
        // (HEAD, 1xx, 204, 304, 2xx-CONNECT), CL=0 from an empty payload publisher would be
        // fabricated (e.g. HEAD's CL should mirror GET's, not be 0).
        return canAddContentLength(response) &&
                serverMaySendPayloadBodyFor(response.status().code(), requestMethod);
    }

    static boolean clientMaySendPayloadBodyFor(final HttpRequestMethod requestMethod) {
        // A client MUST NOT send a message body in a TRACE request.
        // https://tools.ietf.org/html/rfc7231#section-4.3.8
        return !TRACE.equals(requestMethod);
    }

    /**
     * Whether RFC framing rules permit a message body in this response.
     * <p>
     * False for HEAD requests and for status codes 1xx, 204, and 304, plus 2xx responses to CONNECT.
     * <p>
     * Used to gate framework auto-derivation of framing (CL from an aggregated payload, or
     * {@code TE: chunked} from a streaming payload). Even though RFC 7230 §3.3.1 permits a
     * server to send {@code Transfer-Encoding} on a 304/HEAD as a projection of what GET would
     * have applied, ServiceTalk doesn't know what coding GET would use, so auto-fabricating it
     * is meaningless.
     * <p>
     * See {@link #responseMayHaveContentLength} for the distinct CL-permitted question: HEAD and
     * 304 forbid bodies but permit {@code Content-Length} (and {@code Transfer-Encoding}, by the
     * same RFC).
     */
    static boolean serverMaySendPayloadBodyFor(final int statusCode, final HttpRequestMethod requestMethod) {
        // See PayloadSizeLimitingHttpRequesterFilter.serverMaySendPayloadBodyFor in servicetalk-http-utils; keep the
        // two in sync when changing status-code/method exclusions.
        //
        // A server MUST NOT send a message body in a HEAD response.
        // https://tools.ietf.org/html/rfc7231#section-4.3.2
        // A server MUST NOT send a message body in a 1xx (Informational), 204 (No Content), or
        // 304 (Not Modified) response. https://tools.ietf.org/html/rfc7230#section-3.3.3
        return !HEAD.equals(requestMethod)
                && statusCode != NOT_MODIFIED.code()
                && responseMayHaveContentLength(statusCode, requestMethod);
    }

    /**
     * Whether RFC framing rules permit a {@code Content-Length} header on this response.
     * <p>
     * False only for 1xx, 204, and 2xx responses to CONNECT. Notably 304 and HEAD permit CL:
     * both are body-forbidden but CL is permitted (and meaningful). See
     * {@link #serverMaySendPayloadBodyFor} for the body-allowed question.
     */
    static boolean responseMayHaveContentLength(final int statusCode,
                                                final HttpRequestMethod requestMethod) {
        // A server MUST NOT send a Content-Length header field in any response with a status code
        // of 1xx (Informational) or 204 (No Content).
        // https://tools.ietf.org/html/rfc7230#section-3.3.2
        return !INFORMATIONAL_1XX.contains(statusCode)
                && statusCode != NO_CONTENT.code()
                && !isEmptyConnectResponse(requestMethod, statusCode);
    }

    private static boolean canAddContentLength(final HttpMetaData metadata) {
        return (isPayloadEmpty(metadata) || isSafeToAggregate(metadata)) &&
                (metadata.version().major() > 1 || !mayHaveTrailers(metadata)) &&
                !hasContentHeaders(metadata.headers());
    }

    private static boolean alwaysAppendTrailers(final HttpProtocolVersion protocolVersion) {
        // Always include trailers for h2 because trailers are allowed even if content-length is present, so we use
        // trailers as a token to know the stream is done (even if they are empty).
        return protocolVersion.major() > 1;
    }

    static boolean shouldAppendTrailers(final HttpProtocolVersion protocolVersion, final HttpMetaData metaData) {
        return alwaysAppendTrailers(protocolVersion) ||
                (chunkedSupported(protocolVersion) && (mayHaveTrailers(metaData) ||
                        !metaData.headers().contains(CONTENT_LENGTH)));
    }

    static Publisher<Object> setRequestContentLength(final HttpProtocolVersion protocolVersion,
                                                     final StreamingHttpRequest request) {
        return setContentLength(request, request.messageBody(),
                shouldAddZeroContentLength(request.method()) ? HeaderUtils::updateContentLength :
                        HeaderUtils::updateRequestContentLengthNonZero, protocolVersion, /* propagateCancel */ false);
    }

    static Publisher<Object> setResponseContentLength(final HttpProtocolVersion protocolVersion,
                                                      final StreamingHttpResponse response) {
        return setContentLength(response, response.messageBody(), HeaderUtils::updateContentLength, protocolVersion,
                /* propagateCancel */ true);
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

    static ScanMapper<Object, Object> appendTrailersMapper() {
        return new ScanMapper<Object, Object>() {
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
            public MappedTerminal<Object> mapOnError(final Throwable cause) throws Throwable {
                throw cause;
            }

            @Nullable
            @Override
            public MappedTerminal<Object> mapOnComplete() {
                return sawHeaders ? null : EmptyHeadersComplete.INSTANCE;
            }
        };
    }

    private static final class EmptyHeadersComplete implements MappedTerminal<Object> {
        private static final MappedTerminal<Object> INSTANCE = new EmptyHeadersComplete();

        private EmptyHeadersComplete() {
        }

        @Override
        public HttpHeaders onNext() {
            return EmptyHttpHeaders.INSTANCE;
        }

        @Override
        public boolean onNextValid() {
            return true;
        }

        @Nullable
        @Override
        public Throwable terminal() {
            return null;
        }
    }

    static boolean emptyMessageBody(final HttpMetaData metadata, final Publisher<Object> messageBody) {
        return messageBody == empty() || emptyMessageBody(metadata);
    }

    static boolean emptyMessageBody(final HttpMetaData metadata) {
        return isPayloadEmpty(metadata) && !mayHaveTrailers(metadata);
    }

    static Publisher<Object> flatEmptyMessage(final HttpProtocolVersion protocolVersion,
                                              final HttpMetaData metadata,
                                              final Publisher<Object> messageBody,
                                              final boolean propagateCancel) {
        assert emptyMessageBody(metadata, messageBody);
        // HTTP/2 and above can write meta-data as a single frame with endStream=true flag. To check the version, use
        // HttpProtocolVersion from ConnectionInfo because HttpMetaData may have different version.
        final Publisher<Object> flatMessage =
                protocolVersion.major() > 1 || !shouldAppendTrailers(protocolVersion, metadata) ? from(metadata) :
                        from(metadata, EmptyHttpHeaders.INSTANCE);
        if (messageBody == empty()) {
            return flatMessage;
        }
        // Subscribe to the messageBody publisher to trigger any applied transformations, but ignore its content because
        // the PayloadInfo indicated it's effectively empty and does not contain trailers.
        return propagateCancel ?
                flatMessage.concatPropagateCancel(messageBody.ignoreElements().shareContextOnSubscribe()) :
                flatMessage.concat(messageBody.ignoreElements().shareContextOnSubscribe());
    }

    private static final class ContentLengthList<T> extends ArrayList<T> {
        private static final long serialVersionUID = -6593491776432933503L;
        int contentLength;

        ContentLengthList(int contentLength, int arraySize) {
            super(arraySize);
            this.contentLength = contentLength;
        }

        @Override
        public int hashCode() {
            return 31 * contentLength + super.hashCode();
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof ContentLengthList && ((ContentLengthList<?>) o).contentLength == contentLength &&
                    super.equals(o);
        }
    }

    @SuppressWarnings("PMD.LooseCoupling") // internal ContentLengthList is required to access its contentLength field
    private static Publisher<Object> setContentLength(final HttpMetaData metadata,
                                                      final Publisher<Object> messageBody,
                                                      final BiIntConsumer<HttpHeaders> contentLengthUpdater,
                                                      final HttpProtocolVersion protocolVersion,
                                                      final boolean propagateCancel) {
        if (emptyMessageBody(metadata, messageBody)) {
            contentLengthUpdater.apply(0, metadata.headers());
            return flatEmptyMessage(protocolVersion, metadata, messageBody, propagateCancel);
        }
        return messageBody.collect(() -> null, (reduction, item) -> {
            if (reduction == null) {
                // avoid allocating a list if the Publisher emits only a single Buffer
                return item;
            }
            final ContentLengthList<Object> items;
            if (reduction instanceof ContentLengthList) {
                @SuppressWarnings("unchecked")
                ContentLengthList<Object> itemsUnchecked = (ContentLengthList<Object>) reduction;
                items = itemsUnchecked;
            } else {
                // this method is called if the payload has been aggregated, we expect <buffer*, trailers?>.
                items = new ContentLengthList<>(
                        reduction instanceof Buffer ? ((Buffer) reduction).readableBytes() : 0, 2);
                items.add(reduction);
            }
            if (item instanceof Buffer) {
                items.contentLength += ((Buffer) item).readableBytes();
            }

            items.add(item);
            return items;
        }).flatMapPublisher(reduction -> {
            int contentLength = 0;
            final Publisher<Object> flatRequest;
            // We will insert content-length header but haven't yet because we need to compute the value. So no need
            // to pass headers to determine if trailers should be appended.
            final boolean appendTrailers = alwaysAppendTrailers(protocolVersion);
            if (reduction == null) {
                flatRequest = appendTrailers ? from(metadata, EmptyHttpHeaders.INSTANCE) : from(metadata);
            } else if (reduction instanceof Buffer) {
                final Buffer buffer = (Buffer) reduction;
                contentLength = buffer.readableBytes();
                if (contentLength == 0) {
                    flatRequest = appendTrailers ? from(metadata, EmptyHttpHeaders.INSTANCE) : from(metadata);
                } else {
                    flatRequest = appendTrailers ? from(metadata, buffer, EmptyHttpHeaders.INSTANCE) :
                            from(metadata, buffer);
                }
            } else if (reduction instanceof ContentLengthList) {
                @SuppressWarnings("unchecked")
                final ContentLengthList<Object> items = (ContentLengthList<Object>) reduction;
                contentLength = items.contentLength;
                if (appendTrailers && !(items.get(items.size() - 1) instanceof HttpHeaders)) {
                    items.add(EmptyHttpHeaders.INSTANCE);
                }
                flatRequest = Publisher.<Object>from(metadata).concat(fromIterable(items).shareContextOnSubscribe());
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
                                                       final HttpRequestMethod requestMethod,
                                                       final HttpProtocolVersion protocolVersion) {
        // We can only add TE: chunked if we can actually send a payload.
        if (serverMaySendPayloadBodyFor(response.status().code(), requestMethod) &&
                canAddTransferEncodingChunked(response, encodedVersion(response.version(), protocolVersion))) {
            response.headers().add(TRANSFER_ENCODING, CHUNKED);
        }
    }

    static void addRequestTransferEncodingIfNecessary(final StreamingHttpRequest request,
                                                      final HttpProtocolVersion protocolVersion) {
        if (clientMaySendPayloadBodyFor(request.method()) &&
                canAddTransferEncodingChunked(request, encodedVersion(request.version(), protocolVersion))) {
            request.headers().add(TRANSFER_ENCODING, CHUNKED);
        }
    }

    /**
     * The protocol version a message is actually encoded at, which is not necessarily {@code metaData.version()}: a
     * message can carry the client's preferred version (e.g. h2) yet be written on a connection ALPN negotiated down
     * to h1. An h2 transport always encodes as h2; otherwise the message is encoded at the lower of its own version
     * and the transport, matching {@link HttpRequestEncoder}'s request-line coercion so the transfer-encoding decision
     * agrees with the wire (chunked for an h2 message sent over h1, none for a message pinned to HTTP/1.0).
     */
    private static HttpProtocolVersion encodedVersion(final HttpProtocolVersion messageVersion,
                                                      final HttpProtocolVersion protocolVersion) {
        return protocolVersion.major() >= 2 || messageVersion.compareTo(protocolVersion) >= 0 ?
                protocolVersion : messageVersion;
    }

    private static boolean canAddTransferEncodingChunked(final HttpMetaData metaData,
                                                         final HttpProtocolVersion protocolVersion) {
        final HttpHeaders headers = metaData.headers();
        return chunkedSupported(protocolVersion) &&
                (mayHaveTrailers(metaData) || !headers.contains(CONTENT_LENGTH)) &&
                !isTransferEncodingChunked(headers);
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
    @SuppressWarnings("PMD.PreserveStackTrace")
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
