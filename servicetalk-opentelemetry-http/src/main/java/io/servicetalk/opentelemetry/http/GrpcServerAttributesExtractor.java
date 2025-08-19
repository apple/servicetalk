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

import io.servicetalk.transport.api.ConnectionInfo;
import io.servicetalk.transport.api.DomainSocketAddress;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

/**
 * gRPC server attributes extractor using stable HTTP-based APIs.
 * <p>
 * This extractor combines stable HTTP server attributes with gRPC-specific
 * semantic conventions, avoiding dependencies on alpha/incubator APIs.
 */
final class GrpcServerAttributesExtractor extends GrpcSemanticAttributesExtractor {

    // RPC semantic convention attribute keys
    // See https://opentelemetry.io/docs/specs/semconv/rpc/rpc-spans/#rpc-server-span
    private static final AttributeKey<String> CLIENT_ADDRESS = AttributeKey.stringKey("client.address");
    private static final AttributeKey<Long> CLIENT_PORT = AttributeKey.longKey("client.port");

    GrpcServerAttributesExtractor(OpenTelemetryHttpServiceFilter.Builder builder) {
        super(builder.capturedRequestHeaders, builder.capturedResponseHeaders);
    }

    @Override
    public void onStart(AttributesBuilder attributesBuilder, Context parentContext, RequestInfo requestInfo) {
        // Apply pure gRPC/RPC semantic conventions
        super.onStart(attributesBuilder, parentContext, requestInfo);
        addClientAddressAndPort(attributesBuilder, requestInfo);
    }

    // the 'server.address' and 'server.port' attributes.
    private static void addClientAddressAndPort(AttributesBuilder attributesBuilder, RequestInfo requestInfo) {
        ConnectionInfo connectionInfo = requestInfo.connectionInfo();
        if (connectionInfo == null) {
            return;
        }
        SocketAddress address = connectionInfo.remoteAddress();
        if (address instanceof InetSocketAddress) {
            InetSocketAddress inetSocketAddress = (InetSocketAddress) address;
            attributesBuilder.put(CLIENT_ADDRESS, inetSocketAddress.getHostString());
            attributesBuilder.put(CLIENT_PORT, inetSocketAddress.getPort());
        } else if (address instanceof DomainSocketAddress) {
            attributesBuilder.put(CLIENT_ADDRESS, ((DomainSocketAddress) address).path());
        } else {
            // Try to turn it into something meaningful.
            attributesBuilder.put(CLIENT_ADDRESS, address.toString());
        }
    }
}
