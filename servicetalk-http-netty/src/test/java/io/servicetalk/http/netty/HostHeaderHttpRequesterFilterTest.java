/*
 * Copyright © 2019-2021 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.http.api.BlockingHttpClient;
import io.servicetalk.http.api.BlockingHttpRequester;
import io.servicetalk.http.api.HttpProtocolConfig;
import io.servicetalk.http.api.HttpProtocolVersion;
import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.ReservedBlockingHttpConnection;
import io.servicetalk.transport.api.ServerContext;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import javax.annotation.Nullable;

import static io.servicetalk.http.api.HttpExecutionStrategies.noOffloadsStrategy;
import static io.servicetalk.http.api.HttpHeaderNames.HOST;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.api.HttpSerializers.textSerializerUtf8;
import static io.servicetalk.http.netty.HttpClients.forSingleAddress;
import static io.servicetalk.http.netty.HttpProtocolConfigs.h1Default;
import static io.servicetalk.http.netty.HttpProtocolConfigs.h2Default;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

@SuppressWarnings("PMD.AvoidUsingHardCodedIP")
class HostHeaderHttpRequesterFilterTest {

    private enum HttpVersionConfig {
        HTTP_1_0 {
            @Override
            HttpProtocolVersion version() {
                return HttpProtocolVersion.HTTP_1_0;
            }

            @Override
            HttpProtocolConfig config() {
                return h1Default();
            }
        },
        HTTP_1_1 {
            @Override
            HttpProtocolVersion version() {
                return HttpProtocolVersion.HTTP_1_1;
            }

            @Override
            HttpProtocolConfig config() {
                return h1Default();
            }
        },
        HTTP_2_0 {
            @Override
            HttpProtocolVersion version() {
                return HttpProtocolVersion.HTTP_2_0;
            }

            @Override
            HttpProtocolConfig config() {
                return h2Default();
            }
        };

        abstract HttpProtocolVersion version();

        abstract HttpProtocolConfig config();
    }

    private HttpVersionConfig httpVersionConfig;

    @ParameterizedTest
    @EnumSource(HttpVersionConfig.class)
    void ipv4NotEscaped(HttpVersionConfig httpVersionConfig) throws Exception {
        this.httpVersionConfig = httpVersionConfig;
        doHostHeaderTest("1.2.3.4", "1.2.3.4");
    }

    @ParameterizedTest
    @EnumSource(HttpVersionConfig.class)
    void ipv6IsEscaped(HttpVersionConfig httpVersionConfig) throws Exception {
        this.httpVersionConfig = httpVersionConfig;
        doHostHeaderTest("::1", "[::1]");
    }

    private void doHostHeaderTest(String hostHeader, String expectedValue) throws Exception {
        try (ServerContext context = buildServer();
             BlockingHttpClient client = forSingleAddress(serverHostAndPort(context))
                     .protocols(httpVersionConfig.config())
                     .unresolvedAddressToHost(addr -> hostHeader)
                     .buildBlocking()) {
            assertResponse(client, null, expectedValue);
        }
    }

    private ServerContext buildServer() throws Exception {
        return HttpServers.forAddress(localAddress(0))
                .protocols(httpVersionConfig.config())
                .listenBlockingAndAwait((ctx, request, responseFactory) -> {
                    assertThat(request.version(), equalTo(httpVersionConfig.version()));
                    final CharSequence host = request.headers().get(HOST);
                    return responseFactory.ok()
                            .version(httpVersionConfig.version())
                            .payloadBody(host != null ? host.toString() : "null", textSerializerUtf8());
                });
    }

    @ParameterizedTest
    @EnumSource(HttpVersionConfig.class)
    void clientBuilderAppendClientFilter(HttpVersionConfig httpVersionConfig) throws Exception {
        this.httpVersionConfig = httpVersionConfig;
        try (ServerContext context = buildServer();
             BlockingHttpClient client = forSingleAddress(serverHostAndPort(context))
                     .protocols(httpVersionConfig.config())
                     .disableHostHeaderFallback() // turn off the default
                     .appendClientFilter(new HostHeaderHttpRequesterFilter("foo.bar:-1"))
                     .buildBlocking()) {
            assertResponse(client, null, "foo.bar:-1");
        }
    }

    @ParameterizedTest
    @EnumSource(HttpVersionConfig.class)
    void clientBuilderAppendConnectionFilter(HttpVersionConfig httpVersionConfig) throws Exception {
        this.httpVersionConfig = httpVersionConfig;
        try (ServerContext context = buildServer();
             BlockingHttpClient client = forSingleAddress(serverHostAndPort(context))
                     .protocols(httpVersionConfig.config())
                     .disableHostHeaderFallback() // turn off the default
                     .appendConnectionFilter(new HostHeaderHttpRequesterFilter("foo.bar:-1"))
                     .buildBlocking()) {
            assertResponse(client, null, "foo.bar:-1");
        }
    }

    @ParameterizedTest
    @EnumSource(HttpVersionConfig.class)
    void reserveConnection(HttpVersionConfig httpVersionConfig) throws Exception {
        this.httpVersionConfig = httpVersionConfig;
        try (ServerContext context = buildServer();
             BlockingHttpClient client = HttpClients.forResolvedAddress(serverHostAndPort(context))
                     .protocols(httpVersionConfig.config())
                     .disableHostHeaderFallback() // turn off the default
                     .appendConnectionFilter(new HostHeaderHttpRequesterFilter("foo.bar:-1"))
                     .buildBlocking();
             ReservedBlockingHttpConnection conn = client.reserveConnection(client.get("/"))) {
            assertResponse(conn, null, "foo.bar:-1");
        }
    }

    @ParameterizedTest
    @EnumSource(HttpVersionConfig.class)
    void clientBuilderAppendClientFilterExplicitHostHeader(HttpVersionConfig httpVersionConfig)
            throws Exception {
        this.httpVersionConfig = httpVersionConfig;
        try (ServerContext context = buildServer();
             BlockingHttpClient client = forSingleAddress(serverHostAndPort(context))
                     .protocols(httpVersionConfig.config())
                     .disableHostHeaderFallback() // turn off the default
                     .appendClientFilter(new HostHeaderHttpRequesterFilter("foo.bar:-1"))
                     .buildBlocking()) {
            assertResponse(client, "bar.only:-1", "bar.only:-1");
        }
    }

    private void assertResponse(BlockingHttpRequester requester, @Nullable String hostHeader, String expectedValue)
            throws Exception {
        final HttpRequest request = requester.get("/").version(httpVersionConfig.version());
        if (hostHeader != null) {
            request.setHeader(HOST, hostHeader);
        }
        HttpResponse response = requester.request(noOffloadsStrategy(), request);
        assertThat(response.status(), equalTo(OK));
        assertThat(response.version(), equalTo(httpVersionConfig.version()));
        // "Host" header is not required for HTTP/1.0. Therefore, we may expect "null" here.
        assertThat(response.payloadBody(textSerializerUtf8()), equalTo(
                httpVersionConfig == HttpVersionConfig.HTTP_1_0 && hostHeader == null ? "null" : expectedValue));
    }
}
