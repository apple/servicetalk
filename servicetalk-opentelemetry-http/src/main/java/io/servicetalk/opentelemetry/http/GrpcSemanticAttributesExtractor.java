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
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Extractor for gRPC semantic convention attributes using stable OpenTelemetry APIs.
 * <p>
 * This class manually implements RPC semantic conventions as defined by OpenTelemetry,
 * avoiding dependencies on alpha/incubator APIs.
 */
abstract class GrpcSemanticAttributesExtractor implements AttributesExtractor<RequestInfo, GrpcTelemetryStatus> {

    // RPC semantic convention attribute keys
    // https://opentelemetry.io/docs/specs/semconv/rpc/rpc-spans/
    private static final AttributeKey<String> NETWORK_PEER_ADDRESS = AttributeKey.stringKey("network.peer.address");
    private static final AttributeKey<String> NETWORK_TRANSPORT = AttributeKey.stringKey("network.transport");
    private static final AttributeKey<String> NETWORK_TYPE = AttributeKey.stringKey("network.type");
    private static final AttributeKey<Long> NETWORK_PEER_PORT = AttributeKey.longKey("network.peer.port");
    private static final AttributeKey<String> RPC_SYSTEM = AttributeKey.stringKey("rpc.system");
    private static final AttributeKey<String> RPC_SERVICE = AttributeKey.stringKey("rpc.service");
    private static final AttributeKey<String> RPC_METHOD = AttributeKey.stringKey("rpc.method");
    // gRPC attribute keys
    // https://opentelemetry.io/docs/specs/semconv/rpc/grpc/
    private static final AttributeKey<Long> RPC_GRPC_STATUS_CODE = AttributeKey.longKey("rpc.grpc.status_code");

    private final AttributesExtractor<RequestInfo, GrpcTelemetryStatus> capturedHeadersExtractor;

    GrpcSemanticAttributesExtractor(List<String> capturedRequestHeaders, List<String> capturedResponseHeaders) {
        this.capturedHeadersExtractor = new GrpcCapturedHeadersExtractor(
                capturedRequestHeaders, capturedResponseHeaders);
    }

    @Override
    public void onStart(AttributesBuilder attributesBuilder, Context parentContext, RequestInfo requestInfo) {
        // Set the RPC system to gRPC
        attributesBuilder.put(RPC_SYSTEM, "grpc");

        // Extract service and method from gRPC path: /service.name/MethodName
        // Note that for grpc, the request target is always origin form.
        String path = requestInfo.request().requestTarget();
        extractServiceAndMethod(path, attributesBuilder);
        extractNetworkAttributes(requestInfo, attributesBuilder);

        // Handle captured headers
        capturedHeadersExtractor.onStart(attributesBuilder, parentContext, requestInfo);
    }

    @Override
    public final void onEnd(AttributesBuilder attributesBuilder, Context context, RequestInfo requestInfo,
                      @Nullable GrpcTelemetryStatus grpcTelemetryStatus, @Nullable Throwable error) {
        // Extract gRPC status code from response headers/trailers (trailers first per gRPC spec)
        if (grpcTelemetryStatus != null) {
            extractGrpcStatusCode(grpcTelemetryStatus, attributesBuilder);
        }

        // Handle captured headers
        capturedHeadersExtractor.onEnd(attributesBuilder, context, requestInfo, grpcTelemetryStatus, error);
    }

    private static void extractServiceAndMethod(String path, AttributesBuilder attributesBuilder) {
        if (path.isEmpty()) {
            return;
        }

        // gRPC path format: /service.name/MethodName
        int lastSlashIndex = path.lastIndexOf('/');
        if (lastSlashIndex > 0) {
            String service = path.substring(path.charAt(0) == '/' ? 1 : 0, lastSlashIndex);
            String method = path.substring(lastSlashIndex + 1);

            attributesBuilder.put(RPC_SERVICE, service);
            attributesBuilder.put(RPC_METHOD, method);
        }
    }

    private static void extractGrpcStatusCode(GrpcTelemetryStatus grpcTelemetryStatus,
                                              AttributesBuilder attributesBuilder) {
        if (grpcTelemetryStatus.hasGrpcStatusCode()) {
            long grpcStatusCode = grpcTelemetryStatus.grpcStatusCode();
            attributesBuilder.put(RPC_GRPC_STATUS_CODE, grpcStatusCode);
        }
    }

    private static void extractNetworkAttributes(RequestInfo requestInfo,
                                         AttributesBuilder attributesBuilder) {
        ConnectionInfo connectionInfo = requestInfo.connectionInfo();
        if (connectionInfo == null) {
            return;
        }
        SocketAddress peerResolvedAddress = connectionInfo.remoteAddress();
        if (peerResolvedAddress instanceof InetSocketAddress) {
            InetSocketAddress inetSocketAddress = (InetSocketAddress) peerResolvedAddress;
            attributesBuilder.put(NETWORK_PEER_ADDRESS, inetSocketAddress.getAddress().getHostAddress());
            attributesBuilder.put(NETWORK_PEER_PORT, inetSocketAddress.getPort());
                attributesBuilder.put(NETWORK_TRANSPORT, Constants.TCP);
                if (inetSocketAddress.getAddress() instanceof Inet4Address) {
                    attributesBuilder.put(NETWORK_TYPE, Constants.IPV4);
                } else if (inetSocketAddress.getAddress() instanceof Inet6Address) {
                    attributesBuilder.put(NETWORK_TYPE, Constants.IPV6);
                }
        } else if (peerResolvedAddress instanceof DomainSocketAddress) {
            DomainSocketAddress domainSocketAddress = (DomainSocketAddress) peerResolvedAddress;
            attributesBuilder.put(NETWORK_PEER_ADDRESS, domainSocketAddress.path());
            attributesBuilder.put(NETWORK_TRANSPORT, Constants.UNIX);
        } else {
            // This is unlikely since the resolved form is almost always an `InetSocketAddress`, and
            // we don't know what the format is, but at least we can try to give users the string
            // representation.
            attributesBuilder.put(NETWORK_PEER_ADDRESS, peerResolvedAddress.toString());
        }
    }
}
