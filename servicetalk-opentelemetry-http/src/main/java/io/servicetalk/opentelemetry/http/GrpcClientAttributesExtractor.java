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

import io.servicetalk.transport.api.HostAndPort;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;

/**
 * gRPC client attributes extractor using stable HTTP-based APIs.
 * <p>
 * This extractor combines stable HTTP client attributes with gRPC-specific
 * semantic conventions, avoiding dependencies on alpha/incubator APIs.
 */
final class GrpcClientAttributesExtractor extends GrpcSemanticAttributesExtractor {

    // RPC semantic convention attribute keys
    // See https://opentelemetry.io/docs/specs/semconv/rpc/rpc-spans/#rpc-client-span
    private static final AttributeKey<String> SERVER_ADDRESS = AttributeKey.stringKey("server.address");
    private static final AttributeKey<Long> SERVER_PORT = AttributeKey.longKey("server.port");

    GrpcClientAttributesExtractor(OpenTelemetryHttpRequesterFilter.Builder builder) {
        super(builder.capturedRequestHeaders, builder.capturedResponseHeaders);
    }

    @Override
    public void onStart(AttributesBuilder attributesBuilder, Context parentContext, RequestInfo requestInfo) {
        super.onStart(attributesBuilder, parentContext, requestInfo);
        addServerAddressAndPort(attributesBuilder, requestInfo);
    }

    private static void addServerAddressAndPort(AttributesBuilder attributesBuilder, RequestInfo requestInfo) {
        // Add server.address and server.port for client spans (per RPC semantic conventions)
        HostAndPort hostAndPort = HttpAttributesGetter.effectiveHostAndPort(requestInfo);
        if (hostAndPort != null) {
            attributesBuilder.put(SERVER_ADDRESS, hostAndPort.hostName());
            if (hostAndPort.port() >= 0) {
                attributesBuilder.put(SERVER_PORT, (long) hostAndPort.port());
            }
        }
    }
}
