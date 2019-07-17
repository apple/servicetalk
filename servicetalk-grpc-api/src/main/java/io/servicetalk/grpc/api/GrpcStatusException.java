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

import java.util.function.Supplier;
import javax.annotation.Nullable;

/**
 * A {@link GrpcStatus} in exception form.
 */
public final class GrpcStatusException extends RuntimeException {
    private static final long serialVersionUID = -1882895535544626915L;

    private final GrpcStatus status;
    private final Supplier<com.google.rpc.Status> applicationStatusSupplier;

    /**
     * Constructs an instance with the given {@link GrpcStatus}.
     * @param status status to be wrapped.
     * @param applicationStatusSupplier the {@link Supplier} for the {@link com.google.rpc.Status}.
     */
    GrpcStatusException(GrpcStatus status, Supplier<com.google.rpc.Status> applicationStatusSupplier) {
        super(toMessage(status), status.cause());
        this.status = status;
        this.applicationStatusSupplier = applicationStatusSupplier;
    }

    /**
     * Returns the wrapped {@link GrpcStatus}.
     * @return the wrapped {@link GrpcStatus}.
     */
    public GrpcStatus status() {
        return status;
    }

    /**
     * Returns the status details if any was included or {@code null}.
     * @return the wrapped {@link com.google.rpc.Status}.
     */
    @Nullable
    public com.google.rpc.Status applicationStatus() {
        return applicationStatusSupplier.get();
    }

    /**
     * Returns a new {@link GrpcStatusException} for the given {@link com.google.rpc.Status}.
     * @param status the status
     * @return the exception created.
     */
    public static GrpcStatusException of(com.google.rpc.Status status) {
        return new GrpcStatusException(new GrpcStatus(GrpcStatusCode.fromCodeValue(status.getCode()),
                null, status.getMessage()), () -> status);
    }

    private static String toMessage(GrpcStatus status) {
        return status.description() == null ? status.code().toString() : status.code() + ": " + status.description();
    }
}
