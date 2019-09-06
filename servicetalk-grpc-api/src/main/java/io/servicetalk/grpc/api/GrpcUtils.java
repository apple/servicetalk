/*
 * Copyright © 2019 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.http.api.HttpDeserializer;
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpMetaData;
import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.HttpResponseFactory;
import io.servicetalk.http.api.HttpResponseMetaData;
import io.servicetalk.http.api.HttpSerializer;
import io.servicetalk.http.api.StatelessTrailersTransformer;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.serialization.api.SerializationException;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.rpc.Status;

import java.util.Base64;
import java.util.function.Supplier;
import javax.annotation.Nullable;

import static io.servicetalk.grpc.api.GrpcMessageEncoding.None;
import static io.servicetalk.http.api.CharSequences.contentEqualsIgnoreCase;
import static io.servicetalk.http.api.CharSequences.newAsciiString;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_TYPE;
import static io.servicetalk.http.api.HttpHeaderNames.SERVER;
import static io.servicetalk.http.api.HttpHeaderNames.TE;
import static io.servicetalk.http.api.HttpHeaderNames.USER_AGENT;
import static io.servicetalk.http.api.HttpHeaderValues.TRAILERS;
import static io.servicetalk.http.api.HttpRequestMethod.POST;
import static java.lang.String.valueOf;

final class GrpcUtils {
    private static final CharSequence GRPC_CONTENT_TYPE = newAsciiString("application/grpc");
    private static final CharSequence GRPC_STATUS_CODE_TRAILER = newAsciiString("grpc-status");
    private static final CharSequence GRPC_STATUS_DETAILS_TRAILER = newAsciiString("grpc-status-details-bin");
    private static final CharSequence GRPC_STATUS_MESSAGE_TRAILER = newAsciiString("grpc-message");
    // TODO (nkant): add project version
    private static final CharSequence GRPC_USER_AGENT = newAsciiString("grpc-service-talk/");
    private static final CharSequence IDENTITY = newAsciiString(None.encoding());
    private static final CharSequence GRPC_MESSAGE_ENCODING_KEY = newAsciiString("grpc-encoding");
    private static final GrpcStatus STATUS_OK = GrpcStatus.fromCodeValue(GrpcStatusCode.OK.value());

    private GrpcUtils() {
        // No instances.
    }

    static void initRequest(final HttpRequestMetaData request) {
        assert request.method() == POST;
        final HttpHeaders headers = request.headers();
        headers.set(USER_AGENT, GRPC_USER_AGENT);
        headers.set(TE, TRAILERS);
        headers.set(CONTENT_TYPE, GRPC_CONTENT_TYPE);
    }

    static <T> StreamingHttpResponse newResponse(final StreamingHttpResponseFactory responseFactory,
                                                 @Nullable final Publisher<T> payload,
                                                 @Nullable final HttpSerializer<T> serializer,
                                                 final BufferAllocator allocator) {
        final StreamingHttpResponse response = responseFactory.ok();
        initResponse(response);
        if (payload != null) {
            assert serializer != null;
            response.payloadBody(payload, serializer);
        }
        return response.transformRaw(new GrpcStatusUpdater(allocator, STATUS_OK));
    }

    static HttpResponse newResponse(final HttpResponseFactory responseFactory, final BufferAllocator allocator) {
        final HttpResponse response = responseFactory.ok();
        initResponse(response);
        setStatus(response.trailers(), STATUS_OK, null, allocator);
        return response;
    }

    static HttpResponse newErrorResponse(final HttpResponseFactory responseFactory, final Throwable cause,
                                         final BufferAllocator allocator) {
        HttpResponse response = newResponse(responseFactory, allocator);
        setStatus(response.trailers(), cause, allocator);
        return response;
    }

    static StreamingHttpResponse newErrorResponse(final StreamingHttpResponseFactory responseFactory,
                                                  final Throwable cause, final BufferAllocator allocator) {
        StreamingHttpResponse response = newResponse(responseFactory, null, null, allocator);
        response.transformRaw(new ErrorUpdater(cause, allocator));
        return response;
    }

    static void setStatus(final HttpHeaders trailers, final GrpcStatus status, final @Nullable Status details,
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
            GrpcUtils.setStatus(trailers, grpcStatusException.status(), grpcStatusException.applicationStatus(),
                    allocator);
        } else {
            GrpcUtils.setStatus(trailers, GrpcStatus.fromCodeValue(GrpcStatusCode.UNKNOWN.value()), null,
                    allocator);
        }
    }

    static <Resp> Publisher<Resp> validateResponseAndGetPayload(final StreamingHttpResponse response,
                                                                final HttpDeserializer<Resp> deserializer) {
        return deserializer.deserialize(response.headers(), response.payloadBodyAndTrailers().map(o -> {
            if (o instanceof HttpHeaders) {
                validateGrpcStatus((HttpHeaders) o, response.headers());
            } else if (!(o instanceof Buffer)) {
                throw new IllegalArgumentException("Unexpected payload type: " + o.getClass());
            }
            return o;
        }).filter(o -> !(o instanceof HttpHeaders)).map(o -> (Buffer) o));
    }

    static <Resp> Resp validateResponseAndGetPayload(final HttpResponse response,
                                                     final HttpDeserializer<Resp> deserializer) {
        validateGrpcStatus(response.trailers(), response.headers());
        return response.payloadBody(deserializer);
    }

    static GrpcMessageEncoding readGrpcMessageEncoding(final HttpMetaData httpMetaData) {
        final CharSequence encoding = httpMetaData.headers().get(GRPC_MESSAGE_ENCODING_KEY);
        // identity is a special header for no compression
        if (encoding != null && !contentEqualsIgnoreCase(encoding, IDENTITY)) {
            String lowercaseEncoding = encoding.toString().toLowerCase();
            throw new SerializationException("Compression " + lowercaseEncoding + " not supported");
        } else {
            return None;
        }
    }

    private static void initResponse(final HttpResponseMetaData response) {
        // The response status is 200 no matter what. Actual status is put in trailers.
        final HttpHeaders headers = response.headers();
        headers.set(SERVER, GRPC_USER_AGENT);
        headers.set(CONTENT_TYPE, GRPC_CONTENT_TYPE);
    }

    private static void validateGrpcStatus(final HttpHeaders trailers, final HttpHeaders headers) {
        // In case of server error, gRPC may return only one HEADER frame with endStream=true. Our
        // HTTP1-based implementation translates them into response headers so we need to look for
        // the status in both headers and trailers.

        // We will try the trailers first as this is the most likely place to find the GRPC related headers.
        CharSequence statusCode = trailers.get(GRPC_STATUS_CODE_TRAILER);
        final HttpHeaders grpcHeaders;

        if (statusCode == null) {
            // There was no grpc-status in the trailers, so everything must be in the headers.
            statusCode = headers.get(GRPC_STATUS_CODE_TRAILER);
            grpcHeaders = headers;
        } else {
            // We found the statusCode in the trailers so the message also must be in the trailers if
            // there is any.
            grpcHeaders = trailers;
        }

        if (statusCode != null) {
            final GrpcStatusCode grpcStatusCode = GrpcStatusCode.fromCodeValue(statusCode);
            if (grpcStatusCode.value() != GrpcStatusCode.OK.value()) {
                throw new GrpcStatus(grpcStatusCode, null, grpcHeaders.get(GRPC_STATUS_MESSAGE_TRAILER))
                        .asException(new StatusSupplier(grpcHeaders));
            }
        }
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
        @Nullable
        private volatile StatusHolder statusHolder;

        StatusSupplier(HttpHeaders headers) {
            this.headers = headers;
        }

        @Nullable
        @Override
        public Status get() {
            StatusHolder statusHolder = this.statusHolder;
            if (statusHolder == null) {
                // Cache the status (we don't bother caching any errors tho). Also its fine to only use a volatile here
                // as at worse this will just update to the "same" status again.
                this.statusHolder = statusHolder = new StatusHolder(getStatusDetails(headers));
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

    static final class GrpcStatusUpdater extends StatelessTrailersTransformer<Object> {
        private final BufferAllocator allocator;
        private final GrpcStatus successStatus;

        GrpcStatusUpdater(final BufferAllocator allocator, final GrpcStatus successStatus) {
            this.allocator = allocator;
            this.successStatus = successStatus;
        }

        @Override
        protected HttpHeaders payloadComplete(final HttpHeaders trailers) {
            GrpcUtils.setStatus(trailers, successStatus, null, allocator);
            return trailers;
        }

        @Override
        protected HttpHeaders payloadFailed(final Throwable cause, final HttpHeaders trailers) {
            setStatus(trailers, cause, allocator);
            // Swallow exception as we are converting it to the trailers.
            return trailers;
        }
    }

    private static final class ErrorUpdater extends StatelessTrailersTransformer<Object> {
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
