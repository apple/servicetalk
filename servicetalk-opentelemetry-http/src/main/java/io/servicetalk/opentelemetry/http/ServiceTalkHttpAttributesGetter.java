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

import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.http.api.HttpResponseMetaData;
import io.servicetalk.transport.api.HostAndPort;

import io.opentelemetry.instrumentation.api.semconv.http.HttpClientAttributesGetter;
import io.opentelemetry.instrumentation.api.semconv.http.HttpCommonAttributesGetter;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerAttributesGetter;
import io.opentelemetry.instrumentation.api.semconv.network.NetworkAttributesGetter;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import javax.annotation.Nullable;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.Collections.unmodifiableList;

abstract class ServiceTalkHttpAttributesGetter
        implements NetworkAttributesGetter<HttpRequestMetaData, HttpResponseMetaData>,
        HttpCommonAttributesGetter<HttpRequestMetaData, HttpResponseMetaData> {

    private static final String HTTP_SCHEME = "http";
    private static final String HTTPS_SCHEME = "https";

    static final HttpClientAttributesGetter<HttpRequestMetaData, HttpResponseMetaData>
            CLIENT_INSTANCE = new ClientGetter();

    static final HttpServerAttributesGetter<HttpRequestMetaData, HttpResponseMetaData>
            SERVER_INSTANCE = new ServerGetter();

    private ServiceTalkHttpAttributesGetter() {}

    @Override
    public String getHttpRequestMethod(final HttpRequestMetaData httpRequestMetaData) {
        return httpRequestMetaData.method().name();
    }

    @Override
    public List<String> getHttpRequestHeader(
            final HttpRequestMetaData httpRequestMetaData, final String name) {
        return getHeaderValues(httpRequestMetaData.headers(), name);
    }

    @Override
    public Integer getHttpResponseStatusCode(
            final HttpRequestMetaData httpRequestMetaData,
            final HttpResponseMetaData httpResponseMetaData,
            @Nullable final Throwable error) {
        return httpResponseMetaData.status().code();
    }

    @Override
    public List<String> getHttpResponseHeader(
            final HttpRequestMetaData httpRequestMetaData,
            final HttpResponseMetaData httpResponseMetaData,
            final String name) {
        return getHeaderValues(httpResponseMetaData.headers(), name);
    }

    @Override
    @Nullable
    public final String getNetworkProtocolName(
            final HttpRequestMetaData request, @Nullable final HttpResponseMetaData response) {
        return HTTP_SCHEME;
    }

    @Override
    public final String getNetworkProtocolVersion(
            final HttpRequestMetaData request, @Nullable final HttpResponseMetaData response) {
        if (response == null) {
            return request.version().fullVersion();
        }
        return response.version().fullVersion();
    }

    private static List<String> getHeaderValues(final HttpHeaders headers, final String name) {
        final Iterator<? extends CharSequence> iterator = headers.valuesIterator(name);
        if (!iterator.hasNext()) {
            return emptyList();
        }
        final CharSequence firstValue = iterator.next();
        if (!iterator.hasNext()) {
            return singletonList(firstValue.toString());
        }
        final List<String> result = new ArrayList<>(2);
        result.add(firstValue.toString());
        result.add(iterator.next().toString());
        while (iterator.hasNext()) {
            result.add(iterator.next().toString());
        }
        return unmodifiableList(result);
    }

    private static final class ClientGetter extends ServiceTalkHttpAttributesGetter
            implements HttpClientAttributesGetter<HttpRequestMetaData, HttpResponseMetaData> {

        @Override
        @Nullable
        public String getUrlFull(final HttpRequestMetaData request) {
            HostAndPort effectiveHostAndPort = request.effectiveHostAndPort();
            if (effectiveHostAndPort == null) {
                return null;
            }
            String scheme = request.scheme();
            if (scheme == null) {
                scheme = HTTP_SCHEME;
            }
            String requestTarget = request.requestTarget();
            StringBuilder sb = new StringBuilder(
                    scheme.length() + 3 +
                    effectiveHostAndPort.hostName().length() +
                    ((effectiveHostAndPort.port()) >= 0 ? 3 : 0) +
                    requestTarget.length());
            sb.append(scheme == null ? HTTP_SCHEME : scheme)
              .append("://").append(effectiveHostAndPort.hostName());
            if (effectiveHostAndPort.port() >= 0) {
                sb.append(':').append(effectiveHostAndPort.port());
            }
            sb.append(requestTarget);
            return sb.toString();
        }

        @Override
        @Nullable
        public String getServerAddress(final HttpRequestMetaData request) {
            final HostAndPort effectiveHostAndPort = request.effectiveHostAndPort();
            return effectiveHostAndPort != null ? effectiveHostAndPort.hostName() : null;
        }

        @Nullable
        @Override
        public Integer getServerPort(HttpRequestMetaData metaData) {
            final HostAndPort effectiveHostAndPort = metaData.effectiveHostAndPort();
            if (effectiveHostAndPort != null) {
                return effectiveHostAndPort.port();
            }
            // No port. See if we can infer it from the scheme.
            String scheme = metaData.scheme();
            if (scheme != null) {
                if (HTTP_SCHEME.equals(scheme)) {
                    return 80;
                }
                if (HTTPS_SCHEME.equals(scheme)) {
                    return 443;
                }
            }
            return null;
        }
    }

    private static final class ServerGetter extends ServiceTalkHttpAttributesGetter
            implements HttpServerAttributesGetter<HttpRequestMetaData, HttpResponseMetaData> {

        @Nullable
        @Override
        public String getClientAddress(HttpRequestMetaData metaData) {
            return null;
        }

        @Nullable
        @Override
        public Integer getClientPort(HttpRequestMetaData metaData) {
            return null;
        }

        @Override
        public String getUrlScheme(final HttpRequestMetaData httpRequestMetaData) {
            final String scheme = httpRequestMetaData.scheme();
            return scheme == null ? HTTP_SCHEME : scheme;
        }

        @Override
        public String getUrlPath(final HttpRequestMetaData httpRequestMetaData) {
            return httpRequestMetaData.path();
        }

        @Nullable
        @Override
        public String getUrlQuery(final HttpRequestMetaData httpRequestMetaData) {
            return httpRequestMetaData.query();
        }
    }
}
