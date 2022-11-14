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

import com.google.rpc.Status;

import java.util.Objects;
import java.util.function.Supplier;
import javax.annotation.Nullable;

import static io.servicetalk.grpc.api.GrpcStatusCode.UNKNOWN;
import static io.servicetalk.grpc.api.GrpcStatusException.toGrpcStatus;
import static java.lang.Integer.parseInt;
import static java.util.Objects.requireNonNull;

/**
 * Class representing gRPC statuses.
 *
 * @see GrpcStatusCode
 */
public final class GrpcStatus {
    private static final GrpcStatus[] INT_TO_GRPC_STATUS_MAP;
    static {
        final GrpcStatusCode[] statusCodes = GrpcStatusCode.values();
        INT_TO_GRPC_STATUS_MAP = new GrpcStatus[statusCodes.length];
        for (GrpcStatusCode code : statusCodes) {
            INT_TO_GRPC_STATUS_MAP[code.value()] = new GrpcStatus(code);
        }
    }

    private final GrpcStatusCode code;
    /**
     * @deprecated FIXME: 0.43 - remove deprecated member
     */
    @Nullable
    @Deprecated
    private final Throwable cause;
    @Nullable
    private final String description;

    /**
     * Constructs a status with no cause or description.
     *
     * @param code status code.
     */
    public GrpcStatus(GrpcStatusCode code) {
        this(code, (String) null);
    }

    /**
     * Constructs a status with cause but no additional description.
     *
     * @param code status code.
     * @param cause cause.
     * @deprecated Use {@link GrpcStatusException} if there is a {@link Throwable} cause.
     */
    @Deprecated
    public GrpcStatus(GrpcStatusCode code, @Nullable Throwable cause) {
        this(code, cause, null);
    }

    /**
     * Constructs a status with cause and additional description.
     *
     * @param code status code.
     * @param cause cause.
     * @param description additional description.
     * @deprecated Use {@link GrpcStatusException} if there is a cause or
     * {@link GrpcStatus#GrpcStatus(GrpcStatusCode, String)}.
     */
    @Deprecated
    public GrpcStatus(GrpcStatusCode code, @Nullable Throwable cause, @Nullable CharSequence description) {
        this.code = requireNonNull(code);
        this.cause = cause;
        this.description = description == null ? null : description.toString();
    }

    /**
     * Constructs a status with cause and additional description.
     * @param code status code.
     * @param description additional description.
     */
    public GrpcStatus(GrpcStatusCode code, @Nullable String description) {
        this.code = requireNonNull(code);
        this.cause = null;
        this.description = description;
    }

    /**
     * Obtains the status given a code value string.
     *
     * @param codeValue code value string.
     * @return status associated with the code value, or {@link GrpcStatusCode#UNKNOWN}.
     */
    public static GrpcStatus fromCodeValue(String codeValue) {
        try {
            return fromCodeValue(parseInt(codeValue));
        } catch (NumberFormatException e) {
            return new GrpcStatus(UNKNOWN, "Status code value not a number: " + codeValue);
        }
    }

    /**
     * Obtains the status given an integer code value.
     *
     * @param codeValue integer code value.
     * @return status associated with the code value, or {@link GrpcStatusCode#UNKNOWN}.
     */
    public static GrpcStatus fromCodeValue(int codeValue) {
        return codeValue < 0 || codeValue >= INT_TO_GRPC_STATUS_MAP.length ?
                new GrpcStatus(UNKNOWN, "Unknown code: " + codeValue) : INT_TO_GRPC_STATUS_MAP[codeValue];
    }

    /**
     * Translates a throwable into a status.
     *
     * @param t the throwable.
     * @return embedded status if the throwable is a {@link GrpcStatusException}, or an {@link GrpcStatusCode#UNKNOWN}
     * status with the throwable as the cause.
     * @deprecated Use {@link GrpcStatusException#fromThrowable(Throwable)}.
     */
    @Deprecated
    public static GrpcStatus fromThrowable(Throwable t) { // FIXME: 0.43 - remove deprecated method
        GrpcStatus status = fromThrowableNullable(t);
        return status == null ? toGrpcStatus(t) : status;
    }

    /**
     * Translates a throwable into a status.
     *
     * @param t the throwable.
     * @return embedded status if the throwable is a {@link GrpcStatusException}, or {@code null}.
     * @deprecated Use {@link GrpcStatusException#fromThrowable(Throwable)}.
     */
    @Nullable
    @Deprecated
    public static GrpcStatus fromThrowableNullable(Throwable t) { // FIXME: 0.43 - remove deprecated method
        GrpcStatusException exception = unwrapGrpcStatusException(t);
        return exception == null ? null : exception.status();
    }

    /**
     * Returns the current status wrapped in a {@link GrpcStatusException}.
     *
     * @return the current status wrapped in a {@link GrpcStatusException}.
     * @deprecated Use {@link GrpcStatusException#GrpcStatusException(GrpcStatus)}.
     */
    @Deprecated
    public GrpcStatusException asException() { // FIXME: 0.43 - remove deprecated method
        return new GrpcStatusException(this, () -> null);
    }

    /**
     * Returns the current status wrapped in a {@link GrpcStatusException} including the supplied details.
     * The status code used by {@link Status} and the one of the {@link GrpcStatus} must be the same.
     *
     * Users should usually use {@link GrpcStatusException#of(Status)}.
     *
     * @param applicationStatusSupplier the {@link Supplier} for the {@link Status}.
     * @return the current status wrapped in a {@link GrpcStatusException}.
     * @deprecated Use {@link GrpcStatusException#of(Status)}.
     */
    @Deprecated
    public GrpcStatusException asException(Supplier<Status> applicationStatusSupplier) {
        // FIXME: 0.43 - remove deprecated method
        return new GrpcStatusException(this, applicationStatusSupplier);
    }

    /**
     * Returns the status code.
     *
     * @return the status code.
     */
    public GrpcStatusCode code() {
        return code;
    }

    /**
     * Returns the cause, can be null.
     *
     * @return the cause, can be null.
     * @deprecated Use {@link GrpcStatusException#fromThrowable(Throwable)} to create a {@link GrpcStatusException}
     * then {@link GrpcStatusException#getCause()}.
     */
    @Nullable
    @Deprecated
    public Throwable cause() { // FIXME: 0.43 - remove deprecated method
        return cause;
    }

    /**
     * Returns additional descriptions, can be null.
     *
     * @return additional descriptions, can be null.
     */
    @Nullable
    public String description() {
        return description;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final GrpcStatus that = (GrpcStatus) o;

        if (code != that.code) {
            return false;
        }
        return Objects.equals(cause, that.cause) && Objects.equals(description, that.description);
    }

    @Override
    public int hashCode() {
        int result = code.hashCode();
        result = 31 * result + (cause != null ? cause.hashCode() : 0);
        result = 31 * result + (description != null ? description.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "GrpcStatus{" +
                "code=" + code +
                ", cause=" + cause +
                ", description='" + description + '\'' +
                '}';
    }

    /**
     * Unwraps the given {@link Throwable} until a {@link GrpcStatusException} was found and return it. If none could be
     * found it will return {@code null}.
     *
     * @param error the error.
     * @return unwrapped {@link GrpcStatusException}.
     */
    @Nullable
    private static GrpcStatusException unwrapGrpcStatusException(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof GrpcStatusException) {
                return (GrpcStatusException) cause;
            }
        }
        return null;
    }
}
