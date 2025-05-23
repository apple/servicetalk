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

import io.servicetalk.http.api.DefaultHttpHeadersFactory;
import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.http.api.HttpRequestMethod;
import io.servicetalk.http.api.HttpResponseMetaData;
import io.servicetalk.transport.api.HostAndPort;

import io.opentelemetry.instrumentation.api.semconv.http.HttpClientAttributesGetter;
import org.junit.jupiter.api.Test;

import static io.servicetalk.http.api.HttpHeaderNames.HOST;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_1;
import static io.servicetalk.http.api.HttpRequestMetaDataFactory.newRequestMetaData;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

class ServiceTalkHttpAttributesGetterTest {

    @Test
    void clientUrlExtractionNoHostAndPort() {
        HttpClientAttributesGetter<HttpRequestMetaData, HttpResponseMetaData> getter =
                ServiceTalkHttpAttributesGetter.clientGetter(null);
        String pathQueryFrag = "/foo?bar=baz#frag";
        HttpRequestMetaData request = newRequest(pathQueryFrag);
        assertThat(getter.getUrlFull(request), nullValue());
    }

    @Test
    void clientUrlExtractionHostHeader() {
        HttpClientAttributesGetter<HttpRequestMetaData, HttpResponseMetaData> getter =
                // host and port should be unused since we have a host header
                ServiceTalkHttpAttributesGetter.clientGetter(HostAndPort.of("badservice", 443));
        String pathQueryFrag = "/foo?bar=baz#frag";
        HttpRequestMetaData request = newRequest(pathQueryFrag);
        request.addHeader(HOST, "myservice");
        assertThat(getter.getUrlFull(request), equalTo("http://myservice" + pathQueryFrag));
    }

    @Test
    void clientUrlExtractionNoLeadingSlashPath() {
        HttpClientAttributesGetter<HttpRequestMetaData, HttpResponseMetaData> getter =
                ServiceTalkHttpAttributesGetter.clientGetter(HostAndPort.of("myservice", 8080));
        String pathQueryFrag = "foo";
        HttpRequestMetaData request = newRequest(pathQueryFrag);
        assertThat(getter.getUrlFull(request), equalTo("http://myservice:8080/" + pathQueryFrag));
    }

    @Test
    void clientUrlExtractionHostAndPortHttpNonDefaultScheme() {
        HttpClientAttributesGetter<HttpRequestMetaData, HttpResponseMetaData> getter =
                ServiceTalkHttpAttributesGetter.clientGetter(HostAndPort.of("myservice", 8080));
        String pathQueryFrag = "/foo?bar=baz#frag";
        HttpRequestMetaData request = newRequest(pathQueryFrag);
        assertThat(getter.getUrlFull(request), equalTo("http://myservice:8080" + pathQueryFrag));
    }

    @Test
    void clientUrlExtractionHostAndPortHttpDefaultScheme() {
        HttpClientAttributesGetter<HttpRequestMetaData, HttpResponseMetaData> getter =
                ServiceTalkHttpAttributesGetter.clientGetter(HostAndPort.of("myservice", 80));
        String pathQueryFrag = "/foo?bar=baz#frag";
        HttpRequestMetaData request = newRequest(pathQueryFrag);
        assertThat(getter.getUrlFull(request), equalTo("http://myservice" + pathQueryFrag));
    }

    @Test
    void clientUrlExtractionHostAndPortHttpsDefaultScheme() {
        HttpClientAttributesGetter<HttpRequestMetaData, HttpResponseMetaData> getter =
                ServiceTalkHttpAttributesGetter.clientGetter(HostAndPort.of("myservice", 443));
        String pathQueryFrag = "/foo?bar=baz#frag";
        HttpRequestMetaData request = newRequest(pathQueryFrag);
        assertThat(getter.getUrlFull(request), equalTo("https://myservice" + pathQueryFrag));
    }

    @Test
    void clientAbsoluteUrl() {
        HttpClientAttributesGetter<HttpRequestMetaData, HttpResponseMetaData> getter =
                // host and port should be unused
                ServiceTalkHttpAttributesGetter.clientGetter(HostAndPort.of("badservice", 80));
        String requestTarget = "https://myservice/foo?bar=baz#frag";
        HttpRequestMetaData request = newRequest(requestTarget);
        request.addHeader(HOST, "badservice"); // should be unused
        assertThat(getter.getUrlFull(request), equalTo(requestTarget));
    }

    private static HttpRequestMetaData newRequest(String requestTarget) {
        return newRequestMetaData(HTTP_1_1, HttpRequestMethod.GET, requestTarget,
                DefaultHttpHeadersFactory.INSTANCE.newHeaders());
    }
}
