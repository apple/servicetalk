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

/**
 * gRPC server attributes extractor using stable HTTP-based APIs.
 * <p>
 * This extractor combines stable HTTP server attributes with gRPC-specific
 * semantic conventions, avoiding dependencies on alpha/incubator APIs.
 */
final class GrpcServerAttributesExtractor extends GrpcSemanticAttributesExtractor {

    // https://opentelemetry.io/docs/specs/semconv/rpc/rpc-spans/#rpc-server-span
    private static final AttributeKey<String> CLIENT_ADDRESS = AttributeKey.stringKey("client.address");
    private static final AttributeKey<Long> CLIENT_PORT = AttributeKey.longKey("client.port");

    GrpcServerAttributesExtractor(OpenTelemetryOptions openTelemetryOptions) {
        super(openTelemetryOptions);
    }

    @Override
    public void onStart(AttributesBuilder attributesBuilder, Context parentContext, RequestInfo requestInfo) {
        // Apply pure gRPC/RPC semantic conventions
        super.onStart(attributesBuilder, parentContext, requestInfo);
        extractAddress(requestInfo, attributesBuilder, CLIENT_ADDRESS, CLIENT_PORT, false);
    }
}
