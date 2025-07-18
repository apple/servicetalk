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

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;

import javax.annotation.Nullable;

/**
 * gRPC server attributes extractor using stable HTTP-based APIs.
 * <p>
 * This extractor combines stable HTTP server attributes with gRPC-specific
 * semantic conventions, avoiding dependencies on alpha/incubator APIs.
 */
final class GrpcServerAttributesExtractor implements AttributesExtractor<RequestInfo, GrpcTelemetryStatus> {

    private final AttributesExtractor<RequestInfo, GrpcTelemetryStatus> capturedHeadersExtractor;

    GrpcServerAttributesExtractor(OpenTelemetryOptions options) {
        this.capturedHeadersExtractor = new GrpcCapturedHeadersExtractor(
                options.capturedRequestHeaders(),
                options.capturedResponseHeaders()
        );
    }

    @Override
    public void onStart(AttributesBuilder attributes, Context parentContext, RequestInfo request) {
        // Apply pure gRPC/RPC semantic conventions
        GrpcSemanticAttributesExtractor.INSTANCE.onStart(attributes, parentContext, request);

        // Handle captured headers
        capturedHeadersExtractor.onStart(attributes, parentContext, request);
    }

    @Override
    public void onEnd(AttributesBuilder attributes, Context context, RequestInfo request,
                      @Nullable GrpcTelemetryStatus response, @Nullable Throwable error) {
        // Apply pure gRPC/RPC semantic conventions
        GrpcSemanticAttributesExtractor.INSTANCE.onEnd(attributes, context, request, response, error);

        // Handle captured headers
        capturedHeadersExtractor.onEnd(attributes, context, request, response, error);
    }
}
