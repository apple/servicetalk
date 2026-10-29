/*
 * Copyright © 2026 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.http.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import javax.annotation.Nullable;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.http.api.DefaultHttpHeadersFactory.INSTANCE;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_1;
import static io.servicetalk.http.api.HttpRequestMethod.GET;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class DefaultStreamingHttpRequestResponseFactoryTest {

    @ParameterizedTest(name = "{displayName} [{index}]: target={0}, scheme={1}")
    @CsvSource({"/hello,", "http://example.com/hello, http", "https://example.com/hello, https"})
    void newRequestWithoutDefaultSchemeUsesRequestTarget(String target, @Nullable String expectedScheme) {
        StreamingHttpRequest request = new DefaultStreamingHttpRequestResponseFactory(DEFAULT_ALLOCATOR, INSTANCE,
                HTTP_1_1).get(target);
        assertThat(request.scheme(), is(expectedScheme));
        assertThat(request.requestTarget(), is(target));
    }

    @ParameterizedTest(name = "{displayName} [{index}]: default={0}, target={1}, scheme={2}")
    @CsvSource({"http, /hello, http", "https, /hello, https", "HTTPS, /hello, https",
            "https, http://example.com/hello, http", "http, https://example.com/hello, https"})
    void newRequestUsesDefaultSchemeWhenTargetHasNoScheme(String defaultScheme, String target, String expectedScheme) {
        StreamingHttpRequest request = new DefaultStreamingHttpRequestResponseFactory(DEFAULT_ALLOCATOR, INSTANCE,
                HTTP_1_1, defaultScheme).get(target);
        assertThat(request.scheme(), is(expectedScheme));
        assertThat(request.requestTarget(), is(target));
    }

    @ParameterizedTest(name = "{displayName} [{index}]: method={0}, target={1}")
    @CsvSource({"CONNECT, example.com:443", "OPTIONS, *"})
    void newRequestSpecialTargetUsesDefaultScheme(String method, String target) {
        StreamingHttpRequest request = new DefaultStreamingHttpRequestResponseFactory(DEFAULT_ALLOCATOR, INSTANCE,
                HTTP_1_1, "https").newRequest(HttpRequestMethod.of(method), target);
        assertThat(request.scheme(), is("https"));
        assertThat(request.requestTarget(), is(target));
    }

    @Test
    void newRequestTargetChangesPreserveDefaultScheme() {
        StreamingHttpRequest request = new DefaultStreamingHttpRequestResponseFactory(DEFAULT_ALLOCATOR, INSTANCE,
                HTTP_1_1, "https").newRequest(GET, "/hello");
        assertThat(request.scheme(), is("https"));
        request.addQueryParameter("foo", "bar");
        assertThat(request.requestTarget(), is("/hello?foo=bar"));
        request.rawPath("/world");
        assertThat(request.scheme(), is("https"));
        assertThat(request.requestTarget(), is("/world?foo=bar"));
        request.requestTarget("http://example.com/hello");
        assertThat(request.scheme(), is("http"));
        request.requestTarget("/hello");
        assertThat(request.scheme(), is("https"));
    }

    @Test
    void newRequestConversionsPreserveDefaultScheme() throws Exception {
        StreamingHttpRequest request = new DefaultStreamingHttpRequestResponseFactory(DEFAULT_ALLOCATOR, INSTANCE,
                HTTP_1_1, "https").get("http://example.com/hello");
        HttpRequest aggregated = request.toRequest().toFuture().get();
        assertThat(aggregated.scheme(), is("http"));
        StreamingHttpRequest converted = aggregated.toStreamingRequest();
        assertThat(converted.scheme(), is("http"));
        converted.requestTarget("/hello");
        assertThat(converted.scheme(), is("https"));
        assertThat(converted.toBlockingStreamingRequest().scheme(), is("https"));
        assertThat(converted.requestTarget(), is("/hello"));
    }
}
