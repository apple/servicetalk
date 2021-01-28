/*
 * Copyright © 2019-2020 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.encoding.api.ContentCodings;
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

import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Publisher.empty;
import static io.servicetalk.concurrent.api.Publisher.failed;
import static io.servicetalk.encoding.api.ContentCodings.identity;
import static io.servicetalk.grpc.api.GrpcStatusCode.INTERNAL;
import static io.servicetalk.grpc.api.GrpcStatusCode.UNIMPLEMENTED;
import static io.servicetalk.http.api.CharSequences.contentEqualsIgnoreCase;
import static io.servicetalk.http.api.CharSequences.newAsciiString;
import static io.servicetalk.http.api.CharSequences.regionMatches;
import static io.servicetalk.http.api.CharSequences.split;
import static io.servicetalk.http.api.HeaderUtils.hasContentType;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_TYPE;
import static io.servicetalk.http.api.HttpHeaderNames.SERVER;
import static io.servicetalk.http.api.HttpHeaderNames.TE;
import static io.servicetalk.http.api.HttpHeaderNames.USER_AGENT;
import static io.servicetalk.http.api.HttpHeaderValues.TRAILERS;
import static io.servicetalk.http.api.HttpRequestMethod.POST;
import static java.lang.String.valueOf;
import static java.util.Collections.singletonList;

final class GrpcUtils {
    private static final CharSequence GRPC_CONTENT_TYPE = newAsciiString("application/grpc");
    private static final CharSequence GRPC_STATUS_CODE_TRAILER = newAsciiString("grpc-status");
    private static final CharSequence GRPC_STATUS_DETAILS_TRAILER = newAsciiString("grpc-status-details-bin");
    private static final CharSequence GRPC_STATUS_MESSAGE_TRAILER = newAsciiString("grpc-message");
    // TODO (nkant): add project version
    private static final CharSequence GRPC_USER_AGENT = newAsciiString("grpc-service-talk/");
    private static final CharSequence GRPC_MESSAGE_ENCODING_KEY = newAsciiString("grpc-encoding");
    private static final CharSequence GRPC_ACCEPT_ENCODING_KEY = newAsciiString("grpc-accept-encoding");
    private static final GrpcStatus STATUS_OK = GrpcStatus.fromCodeValue(GrpcStatusCode.OK.value());
    private static final List<ContentCodec> GRPC_ACCEPT_ENCODING_NONE = singletonList(identity());
    private static final ConcurrentMap<List<ContentCodec>, CharSequence> ENCODINGS_HEADER_CACHE =
            new ConcurrentHashMap<>();

    private static final TrailersTransformer<Object, Buffer> ENSURE_GRPC_STATUS_RECEIVED =
            new StatelessTrailersTransformer<Buffer>() {
                @Override
                protected HttpHeaders payloadComplete(final HttpHeaders trailers) {
                    ensureGrpcStatusReceived(trailers);
                    return trailers;
                }
            };

    private GrpcUtils() {
        // No instances.
    }

    static void initRequest(final HttpRequestMetaData request, final List<ContentCodec> supportedEncodings) {
        assert POST.equals(request.method());
        final HttpHeaders headers = request.headers();
        headers.set(USER_AGENT, GRPC_USER_AGENT);
        headers.set(TE, TRAILERS);
        headers.set(CONTENT_TYPE, GRPC_CONTENT_TYPE);
        headers.set(GRPC_ACCEPT_ENCODING_KEY, acceptedEncodingsHeaderValueOrCached(supportedEncodings));
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

    static HttpResponse newErrorResponse(final HttpResponseFactory responseFactory,
                                         @Nullable final GrpcServiceContext context,
                                         final Throwable cause, final BufferAllocator allocator) {
        HttpResponse response = newResponse(responseFactory, context, allocator);
        setStatus(response.trailers(), cause, allocator);
        return response;
    }

    static StreamingHttpResponse newErrorResponse(final StreamingHttpResponseFactory responseFactory,
                                                  @Nullable final GrpcServiceContext context, final Throwable cause,
                                                  final BufferAllocator allocator) {
        return newStreamingResponse(responseFactory, context).transform(new ErrorUpdater(cause, allocator));
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
        } else if (cause instanceof MessageEncodingException) {
            MessageEncodingException msgEncException = (MessageEncodingException) cause;
            GrpcStatus status = new GrpcStatus(UNIMPLEMENTED, cause, "Message encoding '" +
                    msgEncException.encoding() + "' not supported ");
            setStatus(trailers, status, null, allocator);
        } else {
            setStatus(trailers, GrpcStatus.fromCodeValue(GrpcStatusCode.UNKNOWN.value()), null, allocator);
        }
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
            return response.messageBody().ignoreElements()
                    .concat(grpcStatusException != null ? failed(grpcStatusException) : empty());
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
                            "\theaders: " + headers.toString()).asException();
        }
    }

    private static void ensureGrpcStatusReceived(final HttpHeaders headers) {
        final GrpcStatusCode statusCode = extractGrpcStatusCodeFromHeaders(headers);
        if (statusCode == null) {
            // This is a protocol violation as we expect to receive grpc-status.
            throw new GrpcStatus(INTERNAL, null, "Response does not contain " +
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

    static List<ContentCodec> readGrpcAcceptMessageEncoding(final HttpMetaData httpMetaData,
                                                            final List<ContentCodec> allowedCodings) {
        final CharSequence acceptEncodingsHeaderVal = httpMetaData.headers().get(GRPC_ACCEPT_ENCODING_KEY);

        if (acceptEncodingsHeaderVal == null || acceptEncodingsHeaderVal.length() == 0) {
            return GRPC_ACCEPT_ENCODING_NONE;
        }

        List<ContentCodec> knownEncodings = new ArrayList<>(allowedCodings.size());
        List<CharSequence> acceptEncodingValues = split(acceptEncodingsHeaderVal, ',');
        for (CharSequence val : acceptEncodingValues) {
            ContentCodec enc = encodingFor(allowedCodings, val);
            if (enc != null) {
                knownEncodings.add(enc);
            }
        }

        return knownEncodings;
    }

    /**
     * Establish a commonly accepted encoding between server and client, according to the supported-codings
     * on the server side and the {@code 'Accepted-Encoding'} incoming header on the request.
     * <p>
     * If no supported codings are configured then the result is always {@code identity}
     * If no accepted codings are present in the request then the result is always {@code identity}
     * In all other cases, the first matching encoding (that is NOT {@link ContentCodings#identity()}) is preferred,
     * otherwise {@code identity} is returned.
     *
     * @param httpMetaData The client metadata to extract relevant headers from.
     * @param allowedCodings The server supported codings as configured.
     * @return The {@link ContentCodec} that satisfies both client and server needs, identity otherwise.
     */
    static ContentCodec negotiateAcceptedEncoding(
            final HttpMetaData httpMetaData,
            final List<ContentCodec> allowedCodings) {

        // Fast path, server has no codings configured or has only identity configured as encoding
        if (allowedCodings.isEmpty() ||
                (allowedCodings.size() == 1 && allowedCodings.contains(identity()))) {
            return identity();
        }

        List<ContentCodec> clientSupportedEncodings =
                readGrpcAcceptMessageEncoding(httpMetaData, allowedCodings);
        return negotiateAcceptedEncoding(clientSupportedEncodings, allowedCodings);
    }

    static ContentCodec negotiateAcceptedEncoding(final List<ContentCodec> clientSupportedEncodings,
                                                  final List<ContentCodec> allowedEncodings) {
        // Fast path, Client has no codings configured, or has identity as the only encoding configured
        if (clientSupportedEncodings == GRPC_ACCEPT_ENCODING_NONE ||
                (clientSupportedEncodings.size() == 1 && clientSupportedEncodings.contains(identity()))) {
            return identity();
        }

        for (ContentCodec encoding : allowedEncodings) {
            if (encoding != identity() && clientSupportedEncodings.contains(encoding)) {
                return encoding;
            }
        }

        return identity();
    }

    /**
     * Returns the {@link ContentCodec} that matches the {@code name} within the {@code allowedList}.
     * if {@code name} is {@code null} or empty it results in {@code null} .
     * If {@code name} is {@code 'identity'} this will always result in
     * {@link ContentCodings#identity()} regardless of its presence in the {@code allowedList}.
     *
     * @param allowedList the source list to find a matching codec from.
     * @param name the codec name used for the equality predicate.
     * @return a codec from the allowed-list that name matches the {@code name}.
     */
    @Nullable
    static ContentCodec encodingFor(final Collection<ContentCodec> allowedList,
                                    @Nullable final CharSequence name) {
        if (name == null || name.length() == 0) {
            return null;
        }

        // Identity is always supported, regardless of its presence in the allowed-list
        if (contentEqualsIgnoreCase(name, identity().name())) {
            return identity();
        }

        for (ContentCodec enumEnc : allowedList) {
            // Encoding values can potentially included compression configurations, we only match on the type
            if (startsWith(name, enumEnc.name())) {
                return enumEnc;
            }
        }

        return null;
    }

    private static boolean startsWith(final CharSequence string, final CharSequence prefix) {
        return regionMatches(string, true, 0, prefix, 0, prefix.length());
    }

    private static void initResponse(final HttpResponseMetaData response, @Nullable final GrpcServiceContext context) {
        // The response status is 200 no matter what. Actual status is put in trailers.
        final HttpHeaders headers = response.headers();
        headers.set(SERVER, GRPC_USER_AGENT);
        headers.set(CONTENT_TYPE, GRPC_CONTENT_TYPE);
        if (context != null) {
            headers.set(GRPC_ACCEPT_ENCODING_KEY,
                    acceptedEncodingsHeaderValueOrCached(context.supportedMessageCodings()));
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
     * Construct the gRPC header {@code grpc-accept-encoding} representation of the given codings.
     *
     * @param codings the list of codings to be used in the string representation.
     * @return a comma separated string representation of the codings for use as a header value
     */
    private static CharSequence acceptedEncodingsHeaderValueOrCached(final List<ContentCodec> codings) {
        return ENCODINGS_HEADER_CACHE.computeIfAbsent(codings, (__) -> acceptedEncodingsHeaderValue0(codings));
    }

    private static CharSequence acceptedEncodingsHeaderValue0(final List<ContentCodec> codings) {
        StringBuilder builder = new StringBuilder();
        for (ContentCodec codec : codings) {
            if (codec == identity()) {
                continue;
            }

            if (builder.length() > 0) {
                builder.append(", ");
            }

            builder.append(codec.name());
        }

        return newAsciiString(builder.toString());
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

    private static final class ErrorUpdater extends StatelessTrailersTransformer<Buffer> {
        private final Throwable cause;
        private final BufferAllocator allocator;

        ErrorUpdater(final Throwable cause, final BufferAllocator allocator) {
            this.cause = cause;
            this.allocator = allocator;
        }

        @Override
        protected HttpHeaders payloadComplete(final HttpHeaders trailers) {
            setStatus(trailers, cause, allocator);
            return trailers;
        }
    }
}
