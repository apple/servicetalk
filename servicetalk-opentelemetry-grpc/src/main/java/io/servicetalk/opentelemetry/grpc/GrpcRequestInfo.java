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

import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.transport.api.ConnectionInfo;

import java.net.SocketAddress;
import javax.annotation.Nullable;

final class GrpcRequestInfo {

    private final HttpRequestMetaData metadata;

    @Nullable
    private final SocketAddress remoteAddress;
    @Nullable
    private final SocketAddress localAddress;

    GrpcRequestInfo(HttpRequestMetaData metadata,
                    @Nullable
                    ConnectionInfo connectionInfo) {
        this.metadata = metadata;
        if (connectionInfo != null) {
            remoteAddress = connectionInfo.remoteAddress();
            localAddress = connectionInfo.localAddress();
        } else {
            remoteAddress = null;
            localAddress = null;
        }
    }

    HttpRequestMetaData getMetadata() {
        return metadata;
    }

    @Nullable
    SocketAddress getRemoteAddress() {
        return remoteAddress;
    }

    @Nullable
    SocketAddress getLocalAddress() {
        return localAddress;
    }
}
