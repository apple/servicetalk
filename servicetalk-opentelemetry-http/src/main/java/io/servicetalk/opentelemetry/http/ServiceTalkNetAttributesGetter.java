/*
 * Copyright © 2023 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.http.api.HttpResponseMetaData;
import io.servicetalk.transport.api.HostAndPort;

import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesGetter;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetServerAttributesGetter;

import javax.annotation.Nullable;

abstract class ServiceTalkNetAttributesGetter {

    static final NetClientAttributesGetter<HttpRequestMetaData, HttpResponseMetaData> CLIENT_INSTANCE =
            new ClientGetter();
    static final NetServerAttributesGetter<HttpRequestMetaData, HttpResponseMetaData> SERVER_INSTANCE =
            new ServerGetter();

    private ServiceTalkNetAttributesGetter() {
    }

    public String getNetworkProtocolName(final HttpRequestMetaData request,
                                         @Nullable final HttpResponseMetaData response) {
        return "http";
    }

    public String getNetworkProtocolVersion(final HttpRequestMetaData request,
                                            @Nullable final HttpResponseMetaData response) {
        if (response == null) {
            return request.version().fullVersion();
        }
        return response.version().fullVersion();
    }

    @Nullable
    public String getServerAddress(final HttpRequestMetaData request) {
        final HostAndPort effectiveHostAndPort = request.effectiveHostAndPort();
        return effectiveHostAndPort != null ? effectiveHostAndPort.hostName() : null;
    }

    @Nullable
    public Integer getServerPort(final HttpRequestMetaData request) {
        final HostAndPort effectiveHostAndPort = request.effectiveHostAndPort();
        return effectiveHostAndPort != null ? effectiveHostAndPort.port() : null;
    }

    private static final class ClientGetter extends ServiceTalkNetAttributesGetter implements
            NetClientAttributesGetter<HttpRequestMetaData, HttpResponseMetaData> {
    }

    private static final class ServerGetter extends ServiceTalkNetAttributesGetter implements
            NetServerAttributesGetter<HttpRequestMetaData, HttpResponseMetaData> {
    }
}
