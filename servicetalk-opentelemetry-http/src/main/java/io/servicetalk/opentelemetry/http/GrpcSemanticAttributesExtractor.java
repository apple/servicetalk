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

import io.servicetalk.buffer.api.CharSequences;
import io.servicetalk.transport.api.ConnectionInfo;
import io.servicetalk.transport.api.DomainSocketAddress;
import io.servicetalk.transport.api.HostAndPort;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;

import java.net.Inet6Address;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import javax.annotation.Nullable;

/**
 * Extractor for gRPC semantic convention attributes using stable OpenTelemetry APIs.
 * <p>
 * This class manually implements RPC semantic conventions as defined by OpenTelemetry,
 * avoiding dependencies on alpha/incubator APIs.
 */
abstract class GrpcSemanticAttributesExtractor implements AttributesExtractor<RequestInfo, GrpcTelemetryStatus> {

    private static final CharSequence AUTHORITY = CharSequences.newAsciiString(":authority");

    // RPC semantic convention attribute keys
    // https://opentelemetry.io/docs/specs/semconv/rpc/rpc-spans/
    private static final AttributeKey<String> SERVER_ADDRESS = AttributeKey.stringKey("server.address");
    private static final AttributeKey<Long> SERVER_PORT = AttributeKey.longKey("server.port");
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

    GrpcSemanticAttributesExtractor(OpenTelemetryOptions openTelemetryOptions) {
        this.capturedHeadersExtractor = new GrpcCapturedHeadersExtractor(
                openTelemetryOptions.capturedRequestHeaders(),
                openTelemetryOptions.capturedResponseHeaders()
        );
    }

    @Override
    public void onStart(AttributesBuilder attributesBuilder, Context parentContext, RequestInfo requestInfo) {
        // Set the RPC system to gRPC
        attributesBuilder.put(RPC_SYSTEM, "grpc");

        // Extract service and method from gRPC path: /service.name/MethodName
        // Note that for grpc, the request target is always origin form.
        String path = requestInfo.request().requestTarget();
        extractServiceAndMethod(path, attributesBuilder);
        extractAddress(requestInfo, attributesBuilder, NETWORK_PEER_ADDRESS, NETWORK_PEER_PORT, true);
        addServerAddressAndPort(attributesBuilder, requestInfo);

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

    static void extractAddress(RequestInfo requestInfo,
                               AttributesBuilder attributesBuilder,
                               AttributeKey<String> addressKey,
                               AttributeKey<Long> portKey,
                               boolean addTransportAndType) {
        ConnectionInfo connectionInfo = requestInfo.connectionInfo();
        if (connectionInfo != null) {
            SocketAddress peerResolvedAddress = connectionInfo.remoteAddress();
            if (peerResolvedAddress instanceof InetSocketAddress) {
                InetSocketAddress inetSocketAddress = (InetSocketAddress) peerResolvedAddress;
                attributesBuilder.put(
                        addressKey, inetSocketAddress.getAddress().getHostAddress());
                attributesBuilder.put(portKey, inetSocketAddress.getPort());
                if (addTransportAndType) {
                    attributesBuilder.put(NETWORK_TRANSPORT, "tcp");
                    attributesBuilder.put(NETWORK_TYPE,
                            inetSocketAddress.getAddress() instanceof Inet6Address ? "ipv6" : "ipv4");
                }
            } else if (peerResolvedAddress instanceof DomainSocketAddress) {
                DomainSocketAddress domainSocketAddress = (DomainSocketAddress) peerResolvedAddress;
                attributesBuilder.put(addressKey, domainSocketAddress.getPath());
                if (addTransportAndType) {
                    attributesBuilder.put(NETWORK_TRANSPORT, "unix");
                }
            } else {
                // This is unlikely since the resolved form is almost always an `InetSocketAddress`, and
                // we don't know what the format is, but at least we can try to give users the string
                // representation.
                attributesBuilder.put(addressKey, peerResolvedAddress.toString());
            }
        }
    }

    // the 'server.address' and 'server.port' attributes.
    private static void addServerAddressAndPort(AttributesBuilder attributesBuilder, RequestInfo requestInfo) {
        // Add server.address and server.port for client spans (per RPC semantic conventions)
        HostAndPort hostAndPort = requestInfo.request().effectiveHostAndPort();
        if (hostAndPort == null) {
            // On the client side we lazily populate the attributes because adding the host header happens last.
            // For HTTP/2, this host header will get turned into an ':authority' header by the ServiceTalk internals
            // and that is what we typically observer after the request.
            CharSequence authority = requestInfo.request().headers().get(AUTHORITY);
            if (authority != null) {
                hostAndPort = HostAndPort.ofIpPort(authority.toString());
            }
        }
        if (hostAndPort != null) {
            attributesBuilder.put(SERVER_ADDRESS, hostAndPort.hostName());
            attributesBuilder.put(SERVER_PORT, (long) hostAndPort.port());
        }
    }
}
