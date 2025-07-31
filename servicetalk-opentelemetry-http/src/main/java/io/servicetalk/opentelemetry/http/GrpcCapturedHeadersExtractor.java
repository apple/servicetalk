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

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nullable;

import static java.util.Collections.emptyList;

/**
 * Extractor for captured gRPC request and response headers/metadata.
 * <p>
 * This extractor handles user-configured headers that should be captured as span attributes,
 * following gRPC semantic conventions for metadata attributes.
 */
final class GrpcCapturedHeadersExtractor implements AttributesExtractor<RequestInfo, GrpcTelemetryStatus> {

    // https://opentelemetry.io/docs/specs/semconv/rpc/grpc/
    private static final String GRPC_REQUEST_METADATA_PREFIX = "rpc.grpc.request.metadata.";
    private static final String GRPC_RESPONSE_METADATA_PREFIX = "rpc.grpc.response.metadata.";

    private final List<CapturedHeader> capturedRequestHeaders;
    private final List<CapturedHeader> capturedResponseHeaders;

    GrpcCapturedHeadersExtractor(List<String> capturedRequestHeaders, List<String> capturedResponseHeaders) {
        this.capturedRequestHeaders = convert(true, capturedRequestHeaders);
        this.capturedResponseHeaders = convert(false, capturedResponseHeaders);
    }

    @Override
    public void onStart(AttributesBuilder attributesBuilder, Context parentContext, RequestInfo requestInfo) {
        // Capture configured request headers as gRPC metadata attributes
        for (CapturedHeader entry : capturedRequestHeaders) {
            CharSequence headerValue = requestInfo.request().headers().get(entry.headerName);
            if (headerValue != null) {
                attributesBuilder.put(entry.attributeKey, headerValue.toString());
            }
        }
    }

    @Override
    public void onEnd(AttributesBuilder attributesBuilder, Context context, RequestInfo requestInfo,
                      @Nullable GrpcTelemetryStatus response, @Nullable Throwable error) {
        // Capture configured response headers as gRPC metadata attributes
        if (response == null || response.responseMetaData() == null) {
            return;
        }
        for (CapturedHeader entry : capturedResponseHeaders) {
            CharSequence headerValue = response.responseMetaData().headers().get(entry.headerName);
            if (headerValue != null) {
                attributesBuilder.put(entry.attributeKey, headerValue.toString());
            }
        }
    }

    private static List<CapturedHeader> convert(boolean isRequest, @Nullable List<String> headerNames) {
        if (headerNames == null || headerNames.isEmpty()) {
            return emptyList();
        }
        List<CapturedHeader> result = new ArrayList<>(headerNames.size());
        for (String headerName : headerNames) {
            result.add(new CapturedHeader(isRequest, headerName));
        }
        return result;
    }

    private static final class CapturedHeader {
        final String headerName;
        final AttributeKey<String> attributeKey;

        CapturedHeader(boolean isRequest, String headerName) {
            this.headerName = headerName;
            this.attributeKey = AttributeKey.stringKey(
                    (isRequest ? GRPC_REQUEST_METADATA_PREFIX : GRPC_RESPONSE_METADATA_PREFIX) +
                            headerName.toLowerCase(Locale.ENGLISH));
        }
    }
}
