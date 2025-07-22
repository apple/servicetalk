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

package io.servicetalk.opentelemetry.grpc;

import io.opentelemetry.instrumentation.api.semconv.network.NetworkAttributesGetter;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import javax.annotation.Nullable;

import static io.servicetalk.opentelemetry.grpc.GrpcUtil.httpVersionAsString;

final class ServicetalkNetClientAttributesGetter
    implements NetworkAttributesGetter<GrpcRequestInfo, GrpcTelemetryStatus> {
    static final ServicetalkNetClientAttributesGetter INSTANCE = new ServicetalkNetClientAttributesGetter();

    private ServicetalkNetClientAttributesGetter() {
    }

    @Override
    public String getNetworkProtocolName(GrpcRequestInfo requestInfo, @Nullable GrpcTelemetryStatus response) {
        return "http";
    }

    @Override
    public String getNetworkProtocolVersion(GrpcRequestInfo requestInfo,
                                            @Nullable GrpcTelemetryStatus response) {
        return httpVersionAsString(requestInfo.getMetadata());
    }

    @Nullable
    @Override
    public InetSocketAddress getNetworkLocalInetSocketAddress(GrpcRequestInfo requestInfo,
                                                              @Nullable GrpcTelemetryStatus grpcTelemetryStatus) {
        SocketAddress remoteAddress = requestInfo.getRemoteAddress();
        if (remoteAddress instanceof InetSocketAddress) {
            return ((InetSocketAddress) remoteAddress);
        }
        return null;
    }

    @Nullable
    @Override
    public Integer getNetworkLocalPort(GrpcRequestInfo grpcRequestInfo,
                                       @Nullable GrpcTelemetryStatus grpcTelemetryStatus) {
        InetSocketAddress socketAddress = getNetworkLocalInetSocketAddress(grpcRequestInfo, grpcTelemetryStatus);
        if (socketAddress != null) {
            return socketAddress.getPort();
        }
        return null;
    }
}
