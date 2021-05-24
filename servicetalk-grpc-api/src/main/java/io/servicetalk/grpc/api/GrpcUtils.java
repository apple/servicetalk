/*
 * Copyright © 2019-2021 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.grpc.api;

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.buffer.api.BufferAllocator;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.encoding.api.ContentCodec;
import io.servicetalk.encoding.api.Identity;
import io.servicetalk.encoding.api.internal.HeaderUtils;
import io.servicetalk.http.api.HttpDeserializer;
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpMetaData;
import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.HttpResponseFactory;
import io.servicetalk.http.api.HttpResponseMetaData;
import io.servicetalk.http.api.HttpResponseStatus;
import io.servicetalk.http.api.HttpSerializer;
import io.servicetalk.http.api.StatelessTrailersTransformer;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.api.TrailersTransformer;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.rpc.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import javax.annotation.Nullable;

import static io.servicetalk.buffer.api.CharSequences.newAsciiString;
import static io.servicetalk.encoding.api.Identity.identity;
import static io.servicetalk.encoding.api.internal.HeaderUtils.encodingFor;
import static io.servicetalk.grpc.api.GrpcStatusCode.CANCELLED;
import static io.servicetalk.grpc.api.GrpcStatusCode.DEADLINE_EXCEEDED;
import static io.servicetalk.grpc.api.GrpcStatusCode.INTERNAL;
import static io.servicetalk.grpc.api.GrpcStatusCode.UNIMPLEMENTED;
import static io.servicetalk.grpc.api.GrpcStatusCode.UNKNOWN;
import static io.servicetalk.grpc.internal.DeadlineUtils.GRPC_TIMEOUT_HEADER_KEY;
import static io.servicetalk.grpc.internal.DeadlineUtils.makeTimeoutHeader;
import static io.servicetalk.http.api.HeaderUtils.hasContentType;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_TYPE;
import static io.servicetalk.http.api.HttpHeaderNames.SERVER;
import static io.servicetalk.http.api.HttpHeaderNames.TE;
import static io.servicetalk.http.api.HttpHeaderNames.USER_AGENT;
import static io.servicetalk.http.api.HttpHeaderValues.TRAILERS;
import static io.servicetalk.http.api.HttpRequestMethod.POST;
import static java.lang.String.valueOf;

final class GrpcUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(GrpcUtils.class);

    // TODO could/should add "+proto"
    private static final CharSequence GRPC_CONTENT_TYPE = newAsciiString("application/grpc");
    private static final CharSequence GRPC_STATUS_CODE_TRAILER = newAsciiString("grpc-status");
    private static final CharSequence GRPC_STATUS_DETAILS_TRAILER = newAsciiString("grpc-status-details-bin");
    private static final CharSequence GRPC_STATUS_MESSAGE_TRAILER = newAsciiString("grpc-message");
    // TODO (nkant): add project version
    private static final CharSequence GRPC_USER_AGENT = newAsciiString("grpc-service-talk/");
    private static final CharSequence GRPC_MESSAGE_ENCODING_KEY = newAsciiString("grpc-encoding");
    private static final CharSequence GRPC_ACCEPT_ENCODING_KEY = newAsciiString("grpc-accept-encoding");
    private static final GrpcStatus STATUS_OK = GrpcStatus.fromCodeValue(GrpcStatusCode.OK.value());
    private static final ConcurrentMap<List<ContentCodec>, CharSequence> ENCODINGS_HEADER_CACHE =
            new ConcurrentHashMap<>();
    private static final CharSequence CONTENT_ENCODING_SEPARATOR = ", ";

    private static final TrailersTransformer<Object, Buffer> ENSURE_GRPC_STATUS_RECEIVED =
            new StatelessTrailersTransformer<Buffer>() {
                @Override
                protected HttpHeaders payloadComplete(final HttpHeaders trailers) {
                    ensureGrpcStatusReceived(trailers);
                    return trailers;
                }

                @Override
                protected HttpHeaders payloadFailed(final Throwable cause, final HttpHeaders trailers)
                        throws Throwable {

                    // local cancel
                    if (cause instanceof CancellationException) {
                        // include the cause so that caller can determine who cancelled request
                        throw new GrpcStatusException(new GrpcStatus(CANCELLED, cause), () -> null);
                    }

                    // local timeout
                    if (cause instanceof TimeoutException) {
                        // omit the cause unless DEBUG logging has been configured
                        throw new GrpcStatusException(
                                LOGGER.isDebugEnabled() ?
                                    new GrpcStatus(DEADLINE_EXCEEDED, cause) :
                                    DEADLINE_EXCEEDED.status(),
                                () -> null);
                    }

                    throw cause;
                }
            };

    private GrpcUtils() {
        // No instances.
    }

    static void initRequest(final HttpRequestMetaData request,
                            final List<ContentCodec> supportedEncodings,
                            @Nullable final Duration timeout) {
        assert POST.equals(request.method());
        final HttpHeaders headers = request.headers();
        final CharSequence timeoutValue = makeTimeoutHeader(timeout);
        if (null != timeoutValue) {
            headers.set(GRPC_TIMEOUT_HEADER_KEY, timeoutValue);
        }
        headers.set(USER_AGENT, GRPC_USER_AGENT);
        headers.set(TE, TRAILERS);
        headers.set(CONTENT_TYPE, GRPC_CONTENT_TYPE);
        final CharSequence acceptedEncoding = acceptedEncodingsHeaderValueOrCached(supportedEncodings);
        if (acceptedEncoding != null) {
            headers.set(GRPC_ACCEPT_ENCODING_KEY, acceptedEncoding);
        }
    }

    static <T> StreamingHttpResponse newResponse(final StreamingHttpResponseFactory responseFactory,
                                                 @Nullable final GrpcServiceContext context,
                                                 final Publisher<T> payload,
                                                 final HttpSerializer<T> serializer,
                                                 final BufferAllocator allocator) {
        return newStreamingResponse(responseFactory, context).payloadBody(payload, serializer)
                .transform(new GrpcStatusUpdater(allocator, STATUS_OK));
    }

    static StreamingHttpResponse newResponse(final StreamingHttpResponseFactory responseFactory,
                                             @Nullable final GrpcServiceContext context,
                                             final GrpcStatus status,
                                             final BufferAllocator allocator) {
        return newStreamingResponse(responseFactory, context).transform(new GrpcStatusUpdater(allocator, status));
    }

    static HttpResponse newResponse(final HttpResponseFactory responseFactory,
                                    @Nullable final GrpcServiceContext context,
                                    final BufferAllocator allocator) {
        final HttpResponse response = responseFactory.ok();
        initResponse(response, context);
        setStatusOk(response.trailers(), allocator);
        return response;
    }

    static StreamingHttpResponse newTrailersOnlyErrorResponse(final StreamingHttpResponseFactory responseFactory,
                                                     final GrpcStatus status,
                                                     final BufferAllocator allocator) {
        final StreamingHttpResponse response = responseFactory.ok();
        initResponse(response, null);
        setStatus(response.headers(), status, null, allocator);
        return response;
    }

    static HttpResponse newTrailersOnlyErrorResponse(final HttpResponseFactory responseFactory,
                                                     @Nullable final GrpcServiceContext context,
                                                     final Throwable cause, final BufferAllocator allocator) {
        final HttpResponse response = responseFactory.ok();
        initResponse(response, context);
        setStatus(response.headers(), cause, allocator);
        return response;
    }

    static StreamingHttpResponse newTrailersOnlyErrorResponse(final StreamingHttpResponseFactory responseFactory,
                                                  @Nullable final GrpcServiceContext context, final Throwable cause,
                                                  final BufferAllocator allocator) {
        final StreamingHttpResponse response = responseFactory.ok();
        initResponse(response, context);
        setStatus(response.headers(), cause, allocator);
        return response;
    }

    private static StreamingHttpResponse newStreamingResponse(final StreamingHttpResponseFactory responseFactory,
                                                              @Nullable final GrpcServiceContext context) {
        final StreamingHttpResponse response = responseFactory.ok();
        initResponse(response, context);
        return response;
    }

    static void setStatusOk(final HttpHeaders trailers, final BufferAllocator allocator) {
        setStatus(trailers, STATUS_OK, null, allocator);
    }

    static void setStatus(final HttpHeaders trailers, final GrpcStatus status, @Nullable final Status details,
                          final BufferAllocator allocator) {
        trailers.set(GRPC_STATUS_CODE_TRAILER, valueOf(status.code().value()));
        if (status.description() != null) {
            trailers.set(GRPC_STATUS_MESSAGE_TRAILER, status.description());
        }
        if (details != null) {
            trailers.set(GRPC_STATUS_DETAILS_TRAILER,
                    newAsciiString(allocator.wrap(Base64.getEncoder().encode(details.toByteArray()))));
        }
    }

    static void setStatus(final HttpHeaders trailers, final Throwable cause, final BufferAllocator allocator) {
        if (cause instanceof GrpcStatusException) {
            GrpcStatusException grpcStatusException = (GrpcStatusException) cause;
            setStatus(trailers, grpcStatusException.status(), grpcStatusException.applicationStatus(), allocator);
        } else {
            setStatus(trailers, toGrpcStatus(cause), null, allocator);
        }
    }

    static GrpcStatus toGrpcStatus(Throwable cause) {
        GrpcStatus status;
        if (cause instanceof MessageEncodingException) {
            MessageEncodingException msgEncException = (MessageEncodingException) cause;
            status = new GrpcStatus(UNIMPLEMENTED, cause, "Message encoding '" + msgEncException.encoding()
                    + "' not supported ");
        } else if (cause instanceof CancellationException) {
            status = new GrpcStatus(CANCELLED, cause);
        } else if (cause instanceof TimeoutException) {
            // omit cause unless logging configured for debug
            status = LOGGER.isDebugEnabled() ? new GrpcStatus(DEADLINE_EXCEEDED, cause) : DEADLINE_EXCEEDED.status();
        } else {
            // Initialize detail because cause is often lost
            status = new GrpcStatus(UNKNOWN, cause, cause.toString());
        }

        return status;
    }

    static GrpcStatusException toGrpcException(Throwable cause) {
        return cause instanceof GrpcStatusException ? (GrpcStatusException) cause
                : new GrpcStatusException(toGrpcStatus(cause), () -> null);
    }

    static <Resp> Publisher<Resp> validateResponseAndGetPayload(final StreamingHttpResponse response,
                                                                final HttpDeserializer<Resp> deserializer) {
        // In case of an empty response, gRPC-server may return only one HEADER frame with endStream=true. Our
        // HTTP1-based implementation translates them into response headers so we need to look for a grpc-status in both
        // headers and trailers. Since this is streaming response and we have the headers now, we check for the
        // grpc-status here first. If there is no grpc-status in headers, we look for it in trailers later.

        final HttpHeaders headers = response.headers();
        ensureGrpcContentType(response.status(), headers);
        final GrpcStatusCode grpcStatusCode = extractGrpcStatusCodeFromHeaders(headers);
        if (grpcStatusCode != null) {
            final GrpcStatusException grpcStatusException = convertToGrpcStatusException(grpcStatusCode, headers);
            if (grpcStatusException != null) {
                // Give priority to the error if it happens, to allow delayed requests or streams to terminate.
                return Publisher.<Resp>failed(grpcStatusException)
                        .concat(response.messageBody().ignoreElements());
            } else {
                return response.messageBody().ignoreElements().toPublisher();
            }
        }

        response.transform(ENSURE_GRPC_STATUS_RECEIVED);
        return deserializer.deserialize(headers, response.payloadBody());
    }

    static <Resp> Resp validateResponseAndGetPayload(final HttpResponse response,
                                                     final HttpDeserializer<Resp> deserializer) {
        // In case of an empty response, gRPC-server may return only one HEADER frame with endStream=true. Our
        // HTTP1-based implementation translates them into response headers so we need to look for a grpc-status in both
        // headers and trailers.
        final HttpHeaders headers = response.headers();
        final HttpHeaders trailers = response.trailers();
        ensureGrpcContentType(response.status(), headers);

        // We will try the trailers first as this is the most likely place to find the gRPC-related headers.
        final GrpcStatusCode grpcStatusCode = extractGrpcStatusCodeFromHeaders(trailers);
        if (grpcStatusCode != null) {
            final GrpcStatusException grpcStatusException = convertToGrpcStatusException(grpcStatusCode, trailers);
            if (grpcStatusException != null) {
                throw grpcStatusException;
            }
            return response.payloadBody(deserializer);
        }

        // There was no grpc-status in the trailers, so it must be in headers.
        ensureGrpcStatusReceived(headers);
        return response.payloadBody(deserializer);
    }

    private static void ensureGrpcContentType(final HttpResponseStatus status,
                                              final HttpHeaders headers) {
        final CharSequence contentTypeHeader = headers.get(CONTENT_TYPE);
        if (!hasContentType(headers, GRPC_CONTENT_TYPE, null)) {
            throw new GrpcStatus(INTERNAL, null,
                    "HTTP status code: " + status + "\n" +
                            "\tinvalid " + CONTENT_TYPE + ": " + contentTypeHeader + "\n" +
                            "\theaders: " + headers).asException();
        }
    }

    private static void ensureGrpcStatusReceived(final HttpHeaders headers) {
        final GrpcStatusCode statusCode = extractGrpcStatusCodeFromHeaders(headers);
        if (statusCode == null) {
            // This is a protocol violation as we expect to receive grpc-status.
            throw new GrpcStatus(UNKNOWN, null, "Response does not contain " +
                    GRPC_STATUS_CODE_TRAILER + " header or trailer").asException();
        }
        final GrpcStatusException grpcStatusException = convertToGrpcStatusException(statusCode, headers);
        if (grpcStatusException != null) {
            throw grpcStatusException;
        }
    }

    static ContentCodec readGrpcMessageEncoding(final HttpMetaData httpMetaData,
                                                final List<ContentCodec> allowedEncodings) {
        final CharSequence encoding = httpMetaData.headers().get(GRPC_MESSAGE_ENCODING_KEY);
        if (encoding == null) {
            return identity();
        }

        ContentCodec enc = encodingFor(allowedEncodings, encoding);
        if (enc == null) {
            throw new MessageEncodingException(encoding.toString());
        }

        return enc;
    }

    /**
     * Establish a commonly accepted encoding between server and client, according to the supported-codings
     * on the server side and the {@code 'Accepted-Encoding'} incoming header on the request.
     * <p>
     * If no supported codings are configured then the result is always {@code identity}
     * If no accepted codings are present in the request then the result is always {@code identity}
     * In all other cases, the first matching encoding (that is NOT {@link Identity#identity()}) is preferred,
     * otherwise {@code identity} is returned.
     *
     * @param httpMetaData The client metadata to extract relevant headers from.
     * @param allowedCodings The server supported codings as configured.
     * @return The {@link ContentCodec} that satisfies both client and server needs, identity otherwise.
     */
    static ContentCodec negotiateAcceptedEncoding(
            final HttpMetaData httpMetaData,
            final List<ContentCodec> allowedCodings) {

        CharSequence acceptEncHeaderValue = httpMetaData.headers().get(GRPC_ACCEPT_ENCODING_KEY);
        ContentCodec encoding = HeaderUtils.negotiateAcceptedEncoding(acceptEncHeaderValue, allowedCodings);
        return encoding == null ? identity() : encoding;
    }

    private static void initResponse(final HttpResponseMetaData response, @Nullable final GrpcServiceContext context) {
        // The response status is 200 no matter what. Actual status is put in trailers.
        final HttpHeaders headers = response.headers();
        headers.set(SERVER, GRPC_USER_AGENT);
        headers.set(CONTENT_TYPE, GRPC_CONTENT_TYPE);
        if (context != null) {
            final CharSequence acceptedEncoding =
                    acceptedEncodingsHeaderValueOrCached(context.supportedMessageCodings());
            if (acceptedEncoding != null) {
                headers.set(GRPC_ACCEPT_ENCODING_KEY, acceptedEncoding);
            }
        }
    }

    @Nullable
    private static GrpcStatusCode extractGrpcStatusCodeFromHeaders(final HttpHeaders headers) {
        final CharSequence statusCode = headers.get(GRPC_STATUS_CODE_TRAILER);
        if (statusCode == null) {
            return null;
        }
        return GrpcStatusCode.fromCodeValue(statusCode);
    }

    @Nullable
    private static GrpcStatusException convertToGrpcStatusException(final GrpcStatusCode grpcStatusCode,
                                                                    final HttpHeaders headers) {
        if (grpcStatusCode.value() == GrpcStatusCode.OK.value()) {
            return null;
        }
        final GrpcStatus grpcStatus = new GrpcStatus(grpcStatusCode, null, headers.get(GRPC_STATUS_MESSAGE_TRAILER));
        return grpcStatus.asException(new StatusSupplier(headers, grpcStatus));
    }

    @Nullable
    private static Status getStatusDetails(final HttpHeaders headers) {
        final CharSequence details = headers.get(GRPC_STATUS_DETAILS_TRAILER);
        if (details == null) {
            return null;
        }

        try {
            return Status.parser().parseFrom(Base64.getDecoder().decode(details.toString()));
        } catch (InvalidProtocolBufferException e) {
            throw new IllegalStateException("Could not decode grpc status details", e);
        }
    }

    /**
     * Construct the gRPC header {@code grpc-accept-encoding} representation of the given context.
     *
     * @param codings the list of codings to be used in the string representation.
     * @return a comma separated string representation of the codings for use as a header value or {@code null} if
     * the only supported coding is {@link Identity#identity()}.
     */
    @Nullable
    private static CharSequence acceptedEncodingsHeaderValueOrCached(final List<ContentCodec> codings) {
        return ENCODINGS_HEADER_CACHE.computeIfAbsent(codings, (__) -> acceptedEncodingsHeaderValue0(codings));
    }

    @Nullable
    private static CharSequence acceptedEncodingsHeaderValue0(final List<ContentCodec> codings) {
        StringBuilder builder = new StringBuilder(codings.size() * (12 + CONTENT_ENCODING_SEPARATOR.length()));
        for (ContentCodec codec : codings) {
            if (identity().equals(codec)) {
                continue;
            }
            builder.append(codec.name()).append(CONTENT_ENCODING_SEPARATOR);
        }

        if (builder.length() > CONTENT_ENCODING_SEPARATOR.length()) {
            builder.setLength(builder.length() - CONTENT_ENCODING_SEPARATOR.length());
            return newAsciiString(builder);
        }

        return null;
    }

    @SuppressWarnings("unchecked")
    static <T> T uncheckedCast(Object o) {
        return (T) o;
    }

    /**
     * Supplies the {@link Status} by parsing (and caching) it in a lazy fashion once requested the
     * first time.
     */
    private static final class StatusSupplier implements Supplier<Status> {

        private final HttpHeaders headers;
        private final GrpcStatus fallbackStatus;
        @Nullable
        private volatile StatusHolder statusHolder;

        StatusSupplier(HttpHeaders headers, final GrpcStatus fallbackStatus) {
            this.headers = headers;
            this.fallbackStatus = fallbackStatus;
        }

        @Nullable
        @Override
        public Status get() {
            StatusHolder statusHolder = this.statusHolder;
            if (statusHolder == null) {
                // Cache the status (we don't bother caching any errors tho). Also its fine to only use a volatile here
                // as at worse this will just update to the "same" status again.
                final Status statusFromHeaders = getStatusDetails(headers);
                if (statusFromHeaders == null) {
                    final Status.Builder builder = Status.newBuilder().setCode(fallbackStatus.code().value());
                    if (fallbackStatus.description() != null) {
                        builder.setMessage(fallbackStatus.description());
                    }
                    this.statusHolder = statusHolder = new StatusHolder(builder.build());
                } else {
                    this.statusHolder = statusHolder = new StatusHolder(statusFromHeaders);
                }
            }
            return statusHolder.status;
        }

        /**
         * This class differentiates between the {@code null} application status if none is present to avoid
         * continuously retrying to parse status when it isn't present.
         */
        private static final class StatusHolder {
            @Nullable
            final Status status;

            StatusHolder(@Nullable Status status) {
                this.status = status;
            }
        }
    }

    static final class GrpcStatusUpdater extends StatelessTrailersTransformer<Buffer> {
        private final BufferAllocator allocator;
        private final GrpcStatus successStatus;

        GrpcStatusUpdater(final BufferAllocator allocator, final GrpcStatus successStatus) {
            this.allocator = allocator;
            this.successStatus = successStatus;
        }

        @Override
        protected HttpHeaders payloadComplete(final HttpHeaders trailers) {
            setStatus(trailers, successStatus, null, allocator);
            return trailers;
        }

        @Override
        protected HttpHeaders payloadFailed(final Throwable cause, final HttpHeaders trailers) {
            setStatus(trailers, cause, allocator);
            // Swallow exception as we are converting it to the trailers.
            return trailers;
        }
    }
}
