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

import io.servicetalk.http.api.HttpRequestMetaData;

import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;

/**
 * Span name extractor for gRPC requests using stable OpenTelemetry APIs.
 * <p>
 * Extracts span names from gRPC request paths in the format "service.name/MethodName"
 * following gRPC semantic conventions.
 */
final class GrpcSpanNameExtractor implements SpanNameExtractor<RequestInfo> {

    static final SpanNameExtractor<RequestInfo> INSTANCE = new GrpcSpanNameExtractor();

    private GrpcSpanNameExtractor() {
    }

    @Override
    public String extract(RequestInfo requestInfo) {
        String result = baseExtract(requestInfo.request());
        if (requestInfo.usePhysicalSpanName()) {
            result = "Physical " + result;
        }
        return result;
    }

    private String baseExtract(HttpRequestMetaData request) {
        // Note that for grpc, the request target is always origin form.
        String path = request.requestTarget();
        if (path.isEmpty()) {
            return "grpc.request";
        }

        // gRPC path format: /service.name/MethodName
        // Remove leading slash for span name
        if (path.charAt(0) == '/') {
            return path.substring(1);
        }

        return path;
    }
}
