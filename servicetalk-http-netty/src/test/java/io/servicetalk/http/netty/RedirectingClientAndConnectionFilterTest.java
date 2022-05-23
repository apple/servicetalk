/*
 * Copyright © 2019, 2021 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.http.netty;

import io.servicetalk.http.api.AbstractHttpRequesterFilterTest;
import io.servicetalk.http.api.BlockingHttpRequester;
import io.servicetalk.http.api.HttpClient;
import io.servicetalk.http.api.HttpConnection;
import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.netty.ConditionalFilterFactory.FilterFactory;
import io.servicetalk.http.utils.HostHeaderHttpRequesterFilter;
import io.servicetalk.http.utils.RedirectingHttpRequesterFilter;
import io.servicetalk.transport.api.HostAndPort;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.http.api.HttpHeaderNames.HOST;
import static io.servicetalk.http.api.HttpHeaderNames.LOCATION;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_0;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.api.HttpResponseStatus.PERMANENT_REDIRECT;
import static io.servicetalk.transport.netty.internal.AddressUtils.hostHeader;
import static java.lang.String.format;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

/**
 * This test-case is for integration testing the {@link RedirectingHttpRequesterFilter} with the various types
 * of {@link HttpClient} and {@link HttpConnection} builders.
 */
final class RedirectingClientAndConnectionFilterTest extends AbstractHttpRequesterFilterTest {

    @ParameterizedTest(name = "{displayName} [{index}] {0}-{1}")
    @MethodSource("requesterTypes")
    void redirectFilterNoHostHeaderRelativeLocation(final RequesterType type,
                                                    final SecurityType security) throws Exception {
        setUp(security);
        BlockingHttpRequester client = asBlockingRequester(createFilter(type, (responseFactory, request) -> {
            if (request.requestTarget().equals("/")) {
                return succeeded(responseFactory.permanentRedirect().addHeader(LOCATION, "/next"));
            }
            return succeeded(responseFactory.ok());

        }, newFilterFactory()));

        HttpRequest request = client.get("/");
        HttpResponse response = client.request(request);
        assertThat(response.status(), equalTo(PERMANENT_REDIRECT));

        response = client.request(request.addHeader("X-REDIRECT", "TRUE"));
        assertThat(response.status(), equalTo(OK));

        // HTTP/1.0 doesn't support HOST, ensure that we don't get any errors and perform relative redirect
        response = client.request(client.get("/")
                .version(HTTP_1_0)
                .addHeader("X-REDIRECT", "TRUE"));
        assertThat(response.status(), equalTo(OK));
    }

    @ParameterizedTest(name = "{displayName} [{index}] {0}-{1}")
    @MethodSource("requesterTypes")
    void redirectFilterNoHostHeaderAbsoluteLocation(final RequesterType type,
                                                    final SecurityType security) throws Exception {
        setUp(security);
        BlockingHttpRequester client = asBlockingRequester(createFilter(type, (responseFactory, request) -> {
            if (request.requestTarget().equals("/")) {
                return succeeded(responseFactory.permanentRedirect().addHeader(LOCATION,
                        format("http://%s/next", hostHeader(HostAndPort.of(remoteAddress())))));
            }
            return succeeded(responseFactory.ok());
        }, newFilterFactory()));
        HttpRequest request = client.get("/");
        HttpResponse response = client.request(request);
        assertThat(response.status(), equalTo(PERMANENT_REDIRECT));

        response = client.request(request.addHeader("X-REDIRECT", "TRUE"));
        assertThat(response.status(), equalTo(OK));

        // HTTP/1.0 doesn't support HOST => we can not infer that the absolute-form location is relative, don't redirect
        response = client.request(client.get("/")
                .version(HTTP_1_0)
                .addHeader("X-REDIRECT", "TRUE"));
        assertThat(response.status(), equalTo(PERMANENT_REDIRECT));
    }

    @ParameterizedTest(name = "{displayName} [{index}] {0}-{1}")
    @MethodSource("requesterTypes")
    void redirectFilterWithHostHeaderRelativeLocation(final RequesterType type,
                                                      final SecurityType security) throws Exception {
        setUp(security);
        BlockingHttpRequester client = asBlockingRequester(createFilter(type, (responseFactory, request) -> {
            if (request.requestTarget().equals("/")) {
                return succeeded(responseFactory.permanentRedirect()
                        .addHeader(LOCATION, "/next"));
            }
            return succeeded(responseFactory.ok());
        }, newFilterFactory()));
        HttpRequest request = client.get("/").addHeader(HOST, "servicetalk.io");
        HttpResponse response = client.request(request);
        assertThat(response.status(), equalTo(PERMANENT_REDIRECT));

        response = client.request(request.addHeader("X-REDIRECT", "TRUE"));
        assertThat(response.status(), equalTo(OK));
    }

    @ParameterizedTest(name = "{displayName} [{index}] {0}-{1}")
    @MethodSource("requesterTypes")
    void redirectFilterWithHostHeaderAbsoluteLocation(final RequesterType type,
                                                      final SecurityType security) throws Exception {
        setUp(security);
        BlockingHttpRequester client = asBlockingRequester(createFilter(type, (responseFactory, request) -> {
            if (request.requestTarget().equals("/")) {
                return succeeded(responseFactory.permanentRedirect()
                        .addHeader(LOCATION, "http://servicetalk.io:80/next"));
            }
            return succeeded(responseFactory.ok());
        }, newFilterFactory()));
        HttpRequest request = client.get("/").addHeader(HOST, "servicetalk.io:80");
        HttpResponse response = client.request(request);
        assertThat(response.status(), equalTo(PERMANENT_REDIRECT));

        response = client.request(request.addHeader("X-REDIRECT", "TRUE"));
        assertThat(response.status(), equalTo(OK));
    }

    private FilterFactory newFilterFactory() {
        return new ConditionalFilterFactory(request -> request.headers().contains("X-REDIRECT"),
                FilterFactory.from(new RedirectingHttpRequesterFilter()))
                .append(FilterFactory.from(
                        new HostHeaderHttpRequesterFilter(HostAndPort.of(remoteAddress()).toString())));
    }
}
