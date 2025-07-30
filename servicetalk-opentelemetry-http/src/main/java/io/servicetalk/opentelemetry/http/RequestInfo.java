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

import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.transport.api.ConnectionInfo;

import javax.annotation.Nullable;

/**
 * Wrapper for request information including HTTP metadata and connection details.
 * <p>
 * This provides access to both HTTP-level request information and network-level
 * connection information needed for complete observability attributes.
 */
final class RequestInfo {
    private final StreamingHttpRequest request;
    @Nullable
    private final ConnectionInfo connectionInfo;

    RequestInfo(StreamingHttpRequest request, @Nullable ConnectionInfo connectionInfo) {
        this.request = request;
        this.connectionInfo = connectionInfo;
    }

    StreamingHttpRequest request() {
        return request;
    }

    @Nullable
    ConnectionInfo connectionInfo() {
        return connectionInfo;
    }
}
