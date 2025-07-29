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

package io.servicetalk.opentelemetry.http;

import io.servicetalk.http.api.HttpResponseStatus;

import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.instrumentation.api.instrumenter.SpanStatusBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanStatusExtractor;

import javax.annotation.Nullable;

/**
 * Span status extractor for gRPC requests using gRPC status codes.
 * <p>
 * This extractor follows gRPC semantic conventions for determining span status
 * based on gRPC status codes rather than HTTP status codes.
 */
final class GrpcSpanStatusExtractor implements SpanStatusExtractor<RequestInfo, GrpcTelemetryStatus> {

    static final GrpcSpanStatusExtractor CLIENT_INSTANCE = new GrpcSpanStatusExtractor(true);
    static final GrpcSpanStatusExtractor SERVER_INSTANCE = new GrpcSpanStatusExtractor(false);

    private final boolean isClient;

    private GrpcSpanStatusExtractor(boolean isClient) {
        this.isClient = isClient;
    }

    @Override
    public void extract(
            SpanStatusBuilder spanStatusBuilder,
            RequestInfo requestInfo,
            @Nullable GrpcTelemetryStatus grpcTelemetryStatus,
            @Nullable Throwable error) {
        if (error != null || grpcTelemetryStatus == null ||
                grpcTelemetryStatus.responseMetaData() == null ||
                grpcTelemetryStatus.responseMetaData().status() != HttpResponseStatus.OK ||
                !grpcTelemetryStatus.hasGrpcStatusCode()) {
            // We consider it an error without worry about grpc-status if:
            // 1. we have an explicit error
            // 2. didn't receive a response metadata
            // 3. we didn't get a 200 HTTP status
            // 4. didn't get a grpc-status header
            spanStatusBuilder.setStatus(StatusCode.ERROR);
        } else {
            // Follow gRPC semantic conventions for span status
            // See: https://opentelemetry.io/docs/specs/semconv/rpc/rpc-spans/#status
            // and https://github.com/grpc/grpc/blob/master/doc/statuscodes.md
            int grpcStatusCode = grpcTelemetryStatus.grpcStatusCode();
            // If the status is known to be an error, set that status code. Otherwise, leave unset.
            if (isClient ? isClientError(grpcStatusCode) : isServerError(grpcStatusCode)) {
                spanStatusBuilder.setStatus(StatusCode.ERROR);
            }
        }
    }

    private static boolean isClientError(int grpcStatus) {
        // all but status OK (0) are considered errors for client span purposes.
        return grpcStatus != 0;
    }

    private static boolean isServerError(int grpcStatus) {
        switch (grpcStatus) {
            case 2:  // UNKNOWN
            case 4:  // DEADLINE_EXCEEDED
            case 12: // UNIMPLEMENTED
            case 13: // INTERNAL
            case 14: // UNAVAILABLE
            case 15: // DATA_LOSS
                return true;
            default:
                return false;
        }
    }
}
