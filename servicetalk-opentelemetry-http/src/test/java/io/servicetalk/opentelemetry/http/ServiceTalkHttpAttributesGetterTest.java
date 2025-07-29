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
import io.servicetalk.http.api.DefaultStreamingHttpRequestResponseFactory;
import io.servicetalk.http.api.HttpRequestMethod;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpRequestResponseFactory;

import org.junit.jupiter.api.Test;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.http.api.HttpHeaderNames.HOST;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_1;
import static io.servicetalk.opentelemetry.http.ServiceTalkHttpAttributesGetter.CLIENT_INSTANCE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

class ServiceTalkHttpAttributesGetterTest {

    private static final StreamingHttpRequestResponseFactory REQ_RES_FACTORY =
            new DefaultStreamingHttpRequestResponseFactory(DEFAULT_ALLOCATOR, DefaultHttpHeadersFactory.INSTANCE,
                    HTTP_1_1);

    @Test
    void clientUrlExtractionNoHostAndPort() {
        String pathQueryFrag = "/foo?bar=baz#frag";
        StreamingHttpRequest request = newRequest(pathQueryFrag);
        RequestInfo requestInfo = new RequestInfo(request, null);
        assertThat(CLIENT_INSTANCE.getUrlFull(requestInfo), nullValue());
    }

    @Test
    void clientUrlExtractionHostHeader() {
        String pathQueryFrag = "/foo?bar=baz#frag";
        StreamingHttpRequest request = newRequest(pathQueryFrag);
        request.addHeader(HOST, "myservice");
        RequestInfo requestInfo = new RequestInfo(request, null);
        assertThat(CLIENT_INSTANCE.getUrlFull(requestInfo), equalTo("http://myservice" + pathQueryFrag));
    }

    @Test
    void clientUrlExtractionNoLeadingSlashPath() {
        String pathQueryFrag = "foo";
        StreamingHttpRequest request = newRequest(pathQueryFrag);
        request.addHeader(HOST, "myservice:8080");
        RequestInfo requestInfo = new RequestInfo(request, null);
        assertThat(CLIENT_INSTANCE.getUrlFull(requestInfo), equalTo("http://myservice:8080/" + pathQueryFrag));
    }

    @Test
    void clientUrlExtractionHostAndPortHttpNonDefaultScheme() {
        String pathQueryFrag = "/foo?bar=baz#frag";
        StreamingHttpRequest request = newRequest(pathQueryFrag);
        request.addHeader(HOST, "myservice:8080");
        RequestInfo requestInfo = new RequestInfo(request, null);
        assertThat(CLIENT_INSTANCE.getUrlFull(requestInfo), equalTo("http://myservice:8080" + pathQueryFrag));
    }

    @Test
    void clientUrlExtractionHostAndPortHttpDefaultScheme() {
        String pathQueryFrag = "/foo?bar=baz#frag";
        StreamingHttpRequest request = newRequest(pathQueryFrag);
        request.addHeader(HOST, "myservice:80");
        RequestInfo requestInfo = new RequestInfo(request, null);
        assertThat(CLIENT_INSTANCE.getUrlFull(requestInfo), equalTo("http://myservice" + pathQueryFrag));
    }

    @Test
    void clientUrlExtractionHostAndPortHttpsDefaultScheme() {
        String pathQueryFrag = "/foo?bar=baz#frag";
        StreamingHttpRequest request = newRequest(pathQueryFrag);
        request.addHeader(HOST, "myservice:443");
        RequestInfo requestInfo = new RequestInfo(request, null);
        assertThat(CLIENT_INSTANCE.getUrlFull(requestInfo), equalTo("https://myservice" + pathQueryFrag));
    }

    @Test
    void clientAbsoluteUrl() {
        String requestTarget = "https://myservice/foo?bar=baz#frag";
        StreamingHttpRequest request = newRequest(requestTarget);
        request.addHeader(HOST, "badservice"); // should be unused
        RequestInfo requestInfo = new RequestInfo(request, null);
        assertThat(CLIENT_INSTANCE.getUrlFull(requestInfo), equalTo(requestTarget));
    }

    private static StreamingHttpRequest newRequest(String requestTarget) {
        return REQ_RES_FACTORY.newRequest(HttpRequestMethod.GET, requestTarget);
    }
}
