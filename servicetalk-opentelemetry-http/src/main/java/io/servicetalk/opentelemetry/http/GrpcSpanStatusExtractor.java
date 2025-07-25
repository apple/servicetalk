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

    static final GrpcSpanStatusExtractor INSTANCE = new GrpcSpanStatusExtractor();

    private GrpcSpanStatusExtractor() {
    }

    @Override
    public void extract(
            SpanStatusBuilder spanStatusBuilder,
            RequestInfo requestInfo,
            @Nullable GrpcTelemetryStatus response,
            @Nullable Throwable error) {
        if (error != null) {
            spanStatusBuilder.setStatus(StatusCode.ERROR);
        } else if (response != null && response.hasGrpcStatusCode()) {
            // Follow gRPC semantic conventions for span status
            // See: https://opentelemetry.io/docs/specs/semconv/rpc/rpc-spans/#status
            long grpcStatusCode = response.getGrpcStatusCode();

            // gRPC status codes that indicate ERROR span status:
            // - CANCELLED (1)
            // - UNKNOWN (2)
            // - INVALID_ARGUMENT (3)
            // - DEADLINE_EXCEEDED (4)
            // - NOT_FOUND (5)
            // - ALREADY_EXISTS (6)
            // - PERMISSION_DENIED (7)
            // - RESOURCE_EXHAUSTED (8)
            // - FAILED_PRECONDITION (9)
            // - ABORTED (10)
            // - OUT_OF_RANGE (11)
            // - UNIMPLEMENTED (12)
            // - INTERNAL (13)
            // - UNAVAILABLE (14)
            // - DATA_LOSS (15)
            // - UNAUTHENTICATED (16)

            if (grpcStatusCode == 0) {
                // OK (0) - leave span status unset (default OK)
            } else {
                // Any non-OK gRPC status is considered an error
                spanStatusBuilder.setStatus(StatusCode.ERROR);
            }
        } else {
            // Fallback to default behavior if no gRPC status available
            SpanStatusExtractor.getDefault().extract(spanStatusBuilder, requestInfo, response, null);
        }
    }
}
