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

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import javax.annotation.Nullable;

import static io.servicetalk.grpc.api.GrpcStatusCode.UNKNOWN;
import static java.lang.Integer.parseInt;
import static java.util.Objects.requireNonNull;

/**
 * Class representing gRPC statuses. Basically a re-implementation of {@code io.grpc.Status}.
 */
public final class GrpcStatus {
    static final Map<Integer, GrpcStatus> CACHED_INSTANCES;
    static {
        CACHED_INSTANCES = new HashMap<>(GrpcStatusCode.values().length, 1f);
        for (GrpcStatusCode code : GrpcStatusCode.values()) {
            GrpcStatus replaced = CACHED_INSTANCES.put(code.value(), new GrpcStatus(code));
            if (replaced != null) {
                throw new IllegalStateException(String.format("GrpcStatusCode value %d used by both %s and %s",
                        code.value(), replaced.code, code));
            }
        }
    }

    private final GrpcStatusCode code;
    @Nullable
    private final Throwable cause;
    @Nullable
    private final String description;

    /**
     * Constructs a status with no cause or description.
     * @param code status code.
     */
    public GrpcStatus(GrpcStatusCode code) {
        this(code, null);
    }

    /**
     * Constructs a status with cause but no additional description.
     * @param code status code.
     * @param cause cause.
     */
    public GrpcStatus(GrpcStatusCode code, @Nullable Throwable cause) {
        this(code, cause, null);
    }

    /**
     * Constructs a status with cause and additional description.
     * @param code status code.
     * @param cause cause.
     * @param description additional description.
     */
    public GrpcStatus(GrpcStatusCode code, @Nullable Throwable cause, @Nullable CharSequence description) {
        this.code = requireNonNull(code);
        this.cause = cause;
        this.description = description == null ? null : description.toString();
    }

    /**
     * Obtains the status given a code value string.
     * @param codeValue code value string.
     * @return status associated with the code value, or {@link GrpcStatusCode#UNKNOWN}.
     */
    @SuppressWarnings("unused")
    public static GrpcStatus fromCodeValue(String codeValue) {
        try {
            return fromCodeValue(parseInt(codeValue));
        } catch (NumberFormatException e) {
            return UNKNOWN.status();
        }
    }

    /**
     * Obtains the status given an integer code value.
     * @param codeValue integer code value.
     * @return status associated with the code value, or {@link GrpcStatusCode#UNKNOWN}.
     */
    public static GrpcStatus fromCodeValue(int codeValue) {
        GrpcStatus status = CACHED_INSTANCES.get(codeValue); // avoid getOrDefault to save some work
        return status != null ? status : UNKNOWN.status();
    }

    /**
     * Translates a throwable into a status.
     * @param t the throwable.
     * @return embedded status if the throwable is a {@link GrpcStatusException}, or an
     *         {@link GrpcStatusCode#UNKNOWN} status with the throwable as the cause.
     */
    public static GrpcStatus fromThrowable(Throwable t) {
        GrpcStatus status = fromThrowableNullable(t);
        return status == null ? new GrpcStatus(UNKNOWN, t) : status;
    }

    /**
     * Translates a throwable into a status.
     * @param t the throwable.
     * @return embedded status if the throwable is a {@link GrpcStatusException}, or {@code null}.
     */
    @Nullable
    public static GrpcStatus fromThrowableNullable(Throwable t) {
        GrpcStatusException exception = unwrapGrpcStatusException(t);
        return exception == null ? null : exception.status();
    }

    /**
     * Returns the current status wrapped in a {@link GrpcStatusException}.
     * @return the current status wrapped in a {@link GrpcStatusException}.
     */
    public GrpcStatusException asException() {
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
     */
    public GrpcStatusException asException(Supplier<Status> applicationStatusSupplier) {
        return new GrpcStatusException(this, applicationStatusSupplier);
    }

    /**
     * Returns whether this status is a success.
     * @return whether this status is a success.
     */
    public boolean isSuccess() {
        return code.value() == GrpcStatusCode.OK.value();
    }

    /**
     * Returns the status code.
     * @return the status code.
     */
    public GrpcStatusCode code() {
        return code;
    }

    /**
     * Returns the code value.
     * @return the code value.
     */
    public int codeValue() {
        return code.value();
    }

    /**
     * Returns the cause, can be null.
     * @return the cause, can be null.
     */
    @Nullable
    public Throwable cause() {
        return cause;
    }

    /**
     * Returns additional descriptions, can be null.
     * @return additional descriptions, can be null.
     */
    @Nullable
    public String description() {
        return description;
    }

    /**
     * Unwraps the given {@link Throwable} until a {@link GrpcStatusException} was found and return it. If none could be
     * found it will return {@code null}.
     *
     * @param error the error.
     * @return unwrapped {@link GrpcStatusException}.
     */
    @Nullable
    static GrpcStatusException unwrapGrpcStatusException(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof GrpcStatusException) {
                return (GrpcStatusException) cause;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return "GrpcStatus{" +
                "code=" + code +
                ", cause=" + cause +
                ", description='" + description + '\'' +
                '}';
    }
}
