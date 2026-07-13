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

import io.servicetalk.http.api.Http2Exception;
import io.servicetalk.http.api.HttpResponseMetaData;
import io.servicetalk.http.api.PayloadTooLargeException;
import io.servicetalk.http.api.ProxyConnectResponseException;
import io.servicetalk.serializer.api.MaxMessageSizeExceededException;
import io.servicetalk.serializer.api.SerializationException;

import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import javax.annotation.Nullable;

import static io.servicetalk.grpc.api.GrpcStatusCode.CANCELLED;
import static io.servicetalk.grpc.api.GrpcStatusCode.DEADLINE_EXCEEDED;
import static io.servicetalk.grpc.api.GrpcStatusCode.RESOURCE_EXHAUSTED;
import static io.servicetalk.grpc.api.GrpcStatusCode.UNIMPLEMENTED;
import static io.servicetalk.grpc.api.GrpcStatusCode.UNKNOWN;
import static io.servicetalk.grpc.api.GrpcStatusCode.fromHttp2ErrorCode;
import static io.servicetalk.grpc.api.GrpcUtils.fromHttpStatus;
import static java.util.Objects.requireNonNull;

/**
 * A {@link GrpcStatus} in exception form.
 */
public final class GrpcStatusException extends RuntimeException {
    private static final long serialVersionUID = -1882895535544626915L;

    /**
     * Prefix of the opaque description sent to the peer for unmapped server-side exceptions. The detail is not echoed
     * to the peer to avoid leaking internal information (CWE-209 / CWE-200); the full exception is logged server-side
     * with the same reference for correlation.
     */
    static final String UNKNOWN_DESCRIPTION_PREFIX = "internal error";

    /**
     * Prefix of the opaque description sent to the peer for server-side serialization failures. See
     * {@link #UNKNOWN_DESCRIPTION_PREFIX}.
     */
    static final String SERIALIZATION_DESCRIPTION_PREFIX = "Serialization error";

    private final GrpcStatus status;
    private final Supplier<com.google.rpc.Status> applicationStatusSupplier;

    /**
     * Constructs an instance with the given {@link GrpcStatus}.
     * @param status status to be wrapped.
     */
    public GrpcStatusException(GrpcStatus status) {
        this(status, () -> null);
    }

    /**
     * Constructs an instance with the given {@link GrpcStatus}.
     * @param status status to be wrapped.
     * @param cause the cause of this exception.
     */
    public GrpcStatusException(GrpcStatus status, Throwable cause) {
        this(status, () -> null, cause);
    }

    /**
     * Constructs an instance with the given {@link GrpcStatus}.
     * @param status status to be wrapped.
     * @param applicationStatusSupplier the {@link Supplier} for the {@link com.google.rpc.Status}.
     */
    @SuppressWarnings("deprecation")
    GrpcStatusException(GrpcStatus status, Supplier<com.google.rpc.Status> applicationStatusSupplier) {
        this(status, applicationStatusSupplier, status.cause());
    }

    /**
     * Constructs an instance with the given {@link GrpcStatus}.
     * @param status status to be wrapped.
     * @param applicationStatusSupplier the {@link Supplier} for the {@link com.google.rpc.Status}.
     * @param cause the cause of this exception, or {@code null} if no cause.
     */
    GrpcStatusException(GrpcStatus status, Supplier<com.google.rpc.Status> applicationStatusSupplier,
                        @Nullable Throwable cause) {
        super(toMessage(status), cause);
        this.status = status;
        this.applicationStatusSupplier = requireNonNull(applicationStatusSupplier);
    }

    /**
     * Returns the wrapped {@link GrpcStatus}.
     *
     * @return the wrapped {@link GrpcStatus}.
     */
    public GrpcStatus status() {
        return status;
    }

    /**
     * Returns the status details if any was included or {@code null}.
     *
     * @return the wrapped {@link com.google.rpc.Status}.
     */
    @Nullable
    public com.google.rpc.Status applicationStatus() {
        return applicationStatusSupplier.get();
    }

    /**
     * Returns a new {@link GrpcStatusException} for the given {@link com.google.rpc.Status}.
     *
     * @param status the status
     * @return the exception created.
     */
    public static GrpcStatusException of(com.google.rpc.Status status) {
        return new GrpcStatusException(new GrpcStatus(GrpcStatusCode.fromCodeValue(status.getCode()),
                status.getMessage()), () -> status);
    }

    /**
     * Translates a {@link Throwable} into a {@link GrpcStatusException}.
     *
     * @param t the throwable.
     * @return {@link GrpcStatusException} with mapped {@link GrpcStatus} or {@link GrpcStatusCode#UNKNOWN}
     * status with the throwable as the cause.
     */
    public static GrpcStatusException fromThrowable(Throwable t) {
        return t instanceof GrpcStatusException ? (GrpcStatusException) t : new GrpcStatusException(toGrpcStatus(t), t);
    }

    private static String toMessage(GrpcStatus status) {
        return status.description() == null ? status.code().toString() : status.code() + ": " + status.description();
    }

    @SuppressWarnings("deprecation")
    static GrpcStatus toGrpcStatus(Throwable cause) {
        final GrpcStatus status;
        if (cause instanceof Http2Exception) {
            Http2Exception h2Exception = (Http2Exception) cause;
            status = new GrpcStatus(fromHttp2ErrorCode(h2Exception.errorCode()), cause);
        } else if (cause instanceof MessageEncodingException) {
            MessageEncodingException msgEncException = (MessageEncodingException) cause;
            status = new GrpcStatus(UNIMPLEMENTED, cause, "Message encoding '" + msgEncException.encoding()
                    + "' not supported ");
        } else if (cause instanceof MaxMessageSizeExceededException || cause instanceof PayloadTooLargeException) {
            // A size limit was exceeded: the gRPC message-size limiter (frame length or decompressed size), a
            // length-prefixed serializer, or the coordinated HTTP aggregation bound for unary. Map all of these to
            // RESOURCE_EXHAUSTED (matching grpc-java). MaxMessageSizeExceededException extends SerializationException,
            // so this branch must precede the SerializationException branch below. The size figures in the cause are
            // safe to convey, but keep the description generic and stable.
            status = new GrpcStatus(RESOURCE_EXHAUSTED, cause, "Message exceeds maximum inbound message size");
        } else if (cause instanceof SerializationException) {
            // Avoid leaking serializer internals (the message may contain partial payload content or internal type
            // names) to the remote peer. The category is still conveyed; the detailed message is logged server-side
            // with the same reference.
            status = new GrpcStatus(UNKNOWN, cause, redactedDescription(SERIALIZATION_DESCRIPTION_PREFIX));
        } else if (cause instanceof CancellationException) {
            status = new GrpcStatus(CANCELLED, cause);
        } else if (cause instanceof TimeoutException) {
            status = new GrpcStatus(DEADLINE_EXCEEDED, cause);
        } else if (cause instanceof ProxyConnectResponseException) {
            final HttpResponseMetaData response = ((ProxyConnectResponseException) cause).response();
            status = new GrpcStatus(fromHttpStatus(response.status()), cause);
        } else {
            // Avoid leaking internal exception details to the remote peer (CWE-209 / CWE-200). Instead, send an opaque
            // reference that operators can correlate with the full exception logged server-side.
            status = new GrpcStatus(UNKNOWN, cause, redactedDescription(UNKNOWN_DESCRIPTION_PREFIX));
        }

        return status;
    }

    // An opaque, non-identifying description with a reference that operators can correlate with the full exception
    // logged server-side (which logs the same reference). See serverCatchAllShouldLog and its callers.
    private static String redactedDescription(String prefix) {
        return prefix + " (ref: " + UUID.randomUUID() + ')';
    }

    static boolean serverCatchAllShouldLog(Throwable cause) {
        return !(cause instanceof TimeoutException || cause instanceof CancellationException ||
                cause instanceof GrpcStatusException);
    }
}
