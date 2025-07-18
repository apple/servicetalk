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

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import javax.annotation.Nullable;

/**
 * Extractor for gRPC semantic convention attributes using stable OpenTelemetry APIs.
 * <p>
 * This class manually implements RPC semantic conventions as defined by OpenTelemetry,
 * avoiding dependencies on alpha/incubator APIs.
 */
final class GrpcSemanticAttributesExtractor implements AttributesExtractor<RequestInfo, GrpcTelemetryStatus> {

    // RPC semantic convention attribute keys
    private static final AttributeKey<String> RPC_SYSTEM = AttributeKey.stringKey("rpc.system");
    private static final AttributeKey<String> RPC_SERVICE = AttributeKey.stringKey("rpc.service");
    private static final AttributeKey<String> RPC_METHOD = AttributeKey.stringKey("rpc.method");
    private static final AttributeKey<Long> RPC_GRPC_STATUS_CODE = AttributeKey.longKey("rpc.grpc.status_code");

    static final AttributeKey<String> NETWORK_PEER_ADDRESS = AttributeKey.stringKey("network.peer.address");
    static final AttributeKey<Long> NETWORK_PEER_PORT = AttributeKey.longKey("network.peer.port");

    static final GrpcSemanticAttributesExtractor INSTANCE = new GrpcSemanticAttributesExtractor();

    private GrpcSemanticAttributesExtractor() {
    }

    @Override
    public void onStart(AttributesBuilder attributes, Context parentContext, RequestInfo request) {
        // Set the RPC system to gRPC
        attributes.put(RPC_SYSTEM, "grpc");

        // Extract service and method from gRPC path: /service.name/MethodName
        String path = request.getMetadata().path();
        extractServiceAndMethod(path, attributes);
        extractPeerAddress(request, attributes);
    }

    @Override
    public void onEnd(AttributesBuilder attributes, Context context, RequestInfo request,
                      @Nullable GrpcTelemetryStatus response, @Nullable Throwable error) {
        // Extract gRPC status code from response headers/trailers (trailers first per gRPC spec)
        if (response != null) {
            extractGrpcStatusCode(response, attributes);
        }
    }

    private static void extractServiceAndMethod(String path, AttributesBuilder attributes) {
        if (path == null || path.isEmpty()) {
            return;
        }

        // gRPC path format: /service.name/MethodName
        // Remove leading slash if present
        String normalizedPath = path.startsWith("/") ? path.substring(1) : path;

        int lastSlashIndex = normalizedPath.lastIndexOf('/');
        if (lastSlashIndex > 0) {
            String service = normalizedPath.substring(0, lastSlashIndex);
            String method = normalizedPath.substring(lastSlashIndex + 1);

            attributes.put(RPC_SERVICE, service);
            attributes.put(RPC_METHOD, method);
        }
    }

    private static void extractGrpcStatusCode(GrpcTelemetryStatus response, AttributesBuilder attributes) {
        if (response.hasGrpcStatusCode()) {
            long grpcStatusCode = response.getGrpcStatusCode();
            attributes.put(RPC_GRPC_STATUS_CODE, grpcStatusCode);
        }
    }

    private static void extractPeerAddress(RequestInfo requestInfo, AttributesBuilder attributes) {
        ConnectionInfo connectionInfo = requestInfo.getConnectionInfo();
        if (connectionInfo != null) {
            SocketAddress peerResolvedAddress = connectionInfo.remoteAddress();
            if (peerResolvedAddress instanceof InetSocketAddress) {
                InetSocketAddress inetSocketAddress = (InetSocketAddress) peerResolvedAddress;
                attributes.put(
                        NETWORK_PEER_ADDRESS, inetSocketAddress.getAddress().getHostAddress());
                attributes.put(NETWORK_PEER_PORT, inetSocketAddress.getPort());
            } else {
                // This is unlikely since the resolved form is almost always an `InetSocketAddress`, and
                // we don't know what the format is, but at least we can try to give users the string
                // representation.
                attributes.put(NETWORK_PEER_ADDRESS, peerResolvedAddress.toString());
            }
        }
    }
}
