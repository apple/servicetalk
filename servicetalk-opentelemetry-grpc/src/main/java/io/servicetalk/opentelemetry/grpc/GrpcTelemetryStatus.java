/*
 * Copyright © 2025 Apple Inc. and the ServiceTalk project authors
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

package io.servicetalk.opentelemetry.grpc;

import io.servicetalk.grpc.api.GrpcStatus;
import io.servicetalk.grpc.api.GrpcStatusCode;
import io.servicetalk.http.api.HttpResponseMetaData;

import javax.annotation.Nullable;

final class GrpcTelemetryStatus {
    private final GrpcStatus grpcStatus;

    @Nullable
    private final HttpResponseMetaData responseMetaData;
    @Nullable
    private final Throwable error;

    private GrpcTelemetryStatus(GrpcStatus grpcStatus,
                                @Nullable HttpResponseMetaData responseMetaData,
                                @Nullable Throwable error) {
        this.grpcStatus = grpcStatus;
        this.responseMetaData = responseMetaData;
        this.error = error;
    }

    public GrpcStatus getGrpcStatus() {
        return grpcStatus;
    }

    @Nullable
    public HttpResponseMetaData getResponseMetaData() {
        return responseMetaData;
    }

    @Nullable
    public Throwable getError() {
        return error;
    }

    static Builder newBuilder() {
        return new Builder();
    }

    static final class Builder {

        private GrpcStatus grpcStatus = GrpcStatus.fromCodeValue(GrpcStatusCode.UNKNOWN.value());

        @Nullable
        private HttpResponseMetaData responseMetaData;

        @Nullable
        private Throwable error;

        Builder setGrpcStatus(GrpcStatus grpcStatus) {
            this.grpcStatus = grpcStatus;
            return this;
        }

        Builder setResponseMetaData(HttpResponseMetaData responseMetaData) {
            this.responseMetaData = responseMetaData;
            return this;
        }

        Builder setError(@Nullable Throwable error) {
            this.error = error;
            return this;
        }

        GrpcTelemetryStatus build() {
            return new GrpcTelemetryStatus(grpcStatus, responseMetaData, error);
        }
    }
}
