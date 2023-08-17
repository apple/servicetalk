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

import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerAttributesGetter;

import java.util.List;
import javax.annotation.Nullable;

import static io.servicetalk.opentelemetry.http.HeadersPropagatorGetter.getHeaderValues;

final class ServicetalkHttpServerAttributesGetter
    implements HttpServerAttributesGetter<HttpRequestMetaData, HttpResponseMetaData> {

    static final ServicetalkHttpServerAttributesGetter INSTANCE =
        new ServicetalkHttpServerAttributesGetter();

    private ServicetalkHttpServerAttributesGetter() {
    }

    @Nullable
    @Override
    public String getUrlScheme(HttpRequestMetaData httpRequestMetaData) {
        return httpRequestMetaData.scheme() == null ? "http" : httpRequestMetaData.scheme();
    }

    @Override
    public String getUrlPath(HttpRequestMetaData httpRequestMetaData) {
        return httpRequestMetaData.path();
    }

    @Override
    public String getUrlQuery(HttpRequestMetaData httpRequestMetaData) {
        return httpRequestMetaData.query();
    }

    @Override
    public String getHttpRequestMethod(HttpRequestMetaData httpRequestMetaData) {
        return httpRequestMetaData.method().name();
    }

    @Override
    public List<String> getHttpRequestHeader(HttpRequestMetaData httpRequestMetaData, String name) {
        return getHeaderValues(httpRequestMetaData.headers(), name);
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
        return getHeaderValues(httpResponseMetaData.headers(), name);
    }
}
