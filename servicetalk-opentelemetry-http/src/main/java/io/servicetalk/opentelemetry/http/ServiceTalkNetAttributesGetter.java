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

final class ServiceTalkNetAttributesGetter implements
            NetClientAttributesGetter<HttpRequestMetaData, HttpResponseMetaData>,
            NetServerAttributesGetter<HttpRequestMetaData, HttpResponseMetaData> {

    static final ServiceTalkNetAttributesGetter INSTANCE = new ServiceTalkNetAttributesGetter();

    private ServiceTalkNetAttributesGetter() {
    }

    @Override
    public String getNetworkProtocolName(final HttpRequestMetaData request,
                                         @Nullable final HttpResponseMetaData response) {
        return "http";
    }

    @Override
    public String getNetworkProtocolVersion(final HttpRequestMetaData request,
                                            @Nullable final HttpResponseMetaData response) {
        if (response == null) {
            return request.version().fullVersion();
        }
        return response.version().fullVersion();
    }

    @Override
    @Nullable
    public String getServerAddress(final HttpRequestMetaData request) {
        final HostAndPort effectiveHostAndPort = request.effectiveHostAndPort();
        return effectiveHostAndPort != null ? effectiveHostAndPort.hostName() : null;
    }

    @Nullable
    @Override
    public Integer getServerPort(final HttpRequestMetaData request) {
        final HostAndPort effectiveHostAndPort = request.effectiveHostAndPort();
        return effectiveHostAndPort != null ? effectiveHostAndPort.port() : null;
    }

    @Nullable
    @Override
    public String getNetworkType(final HttpRequestMetaData requestMetaData,
                                 @Nullable final HttpResponseMetaData metaData) {
        return NetServerAttributesGetter.super.getNetworkType(requestMetaData, metaData);
    }
}
