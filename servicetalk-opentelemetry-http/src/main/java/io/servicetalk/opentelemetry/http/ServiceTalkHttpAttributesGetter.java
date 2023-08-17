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

import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesGetter;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerAttributesGetter;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import javax.annotation.Nullable;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.Collections.unmodifiableList;

final class ServiceTalkHttpAttributesGetter implements
            HttpClientAttributesGetter<HttpRequestMetaData, HttpResponseMetaData>,
            HttpServerAttributesGetter<HttpRequestMetaData, HttpResponseMetaData> {

    static final ServiceTalkHttpAttributesGetter INSTANCE = new ServiceTalkHttpAttributesGetter();

    private ServiceTalkHttpAttributesGetter() {
    }

    @Override
    public String getHttpRequestMethod(final HttpRequestMetaData httpRequestMetaData) {
        return httpRequestMetaData.method().name();
    }

    @Override
    public List<String> getHttpRequestHeader(final HttpRequestMetaData httpRequestMetaData, final String name) {
        return getHeaderValues(httpRequestMetaData.headers(), name);
    }

    @Override
    public Integer getHttpResponseStatusCode(final HttpRequestMetaData httpRequestMetaData,
                                             final HttpResponseMetaData httpResponseMetaData,
                                             @Nullable final Throwable error) {
        return httpResponseMetaData.status().code();
    }

    @Override
    public List<String> getHttpResponseHeader(final HttpRequestMetaData httpRequestMetaData,
                                              final HttpResponseMetaData httpResponseMetaData,
                                              final String name) {
        return getHeaderValues(httpResponseMetaData.headers(), name);
    }

    @Nullable
    @Override
    public String getUrlFull(final HttpRequestMetaData request) {
        HostAndPort effectiveHostAndPort = request.effectiveHostAndPort();
        if (effectiveHostAndPort == null) {
            return null;
        }
        String requestScheme = request.scheme() == null ? "http" : request.scheme();
        String hostAndPort = effectiveHostAndPort.hostName() + ':' + effectiveHostAndPort.port();
        return requestScheme + "://" + hostAndPort + '/' + request.requestTarget();
    }

    @Override
    public String getUrlScheme(final HttpRequestMetaData httpRequestMetaData) {
        final String scheme = httpRequestMetaData.scheme();
        return scheme == null ? "http" : scheme;
    }

    @Override
    public String getUrlPath(final HttpRequestMetaData httpRequestMetaData) {
        return httpRequestMetaData.path();
    }

    @Override
    public String getUrlQuery(final HttpRequestMetaData httpRequestMetaData) {
        return httpRequestMetaData.query();
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
}
