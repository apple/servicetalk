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

import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesGetter;

import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;

final class ServicetalkHttpClientAttributesGetter
    implements HttpClientAttributesGetter<HttpRequestMetaData, HttpResponseMetaData> {

    static final ServicetalkHttpClientAttributesGetter INSTANCE =
        new ServicetalkHttpClientAttributesGetter();

    private ServicetalkHttpClientAttributesGetter() {
    }

    @Override
    public String getHttpRequestMethod(HttpRequestMetaData httpRequestMetaData) {
        return httpRequestMetaData.method().name();
    }

    @Override
    public List<String> getHttpRequestHeader(HttpRequestMetaData httpRequestMetaData, String name) {
        CharSequence value = httpRequestMetaData.headers().get(name);
        if (value != null) {
            return Collections.singletonList(value.toString());
        }
        return Collections.emptyList();
    }

    @Override
    public Integer getHttpResponseStatusCode(HttpRequestMetaData httpRequestMetaData,
                                             HttpResponseMetaData httpResponseMetaData,
                                             @Nullable Throwable error) {
        return httpResponseMetaData.status().code();
    }

    @Override
    public List<String> getHttpResponseHeader(HttpRequestMetaData httpRequestMetaData,
                                              HttpResponseMetaData httpResponseMetaData,
                                              String name) {
        CharSequence value = httpResponseMetaData.headers().get(name);
        if (value != null) {
            return Collections.singletonList(value.toString());
        }
        return Collections.emptyList();
    }

    @Nullable
    @Override
    public String getUrlFull(HttpRequestMetaData request) {
        HostAndPort effectiveHostAndPort = request.effectiveHostAndPort();
        String requestScheme = request.scheme() != null ? request.scheme()
            : "http";
        CharSequence hostAndPort = request.headers().contains("host") ? request.headers().get("host")
            : effectiveHostAndPort != null ?
            String.format("%s:%d", effectiveHostAndPort.hostName(), effectiveHostAndPort.port()) : null;
        return hostAndPort != null ? String.format("%s://%s%s", requestScheme, hostAndPort, request.path()) :
            request.path();
    }
}
