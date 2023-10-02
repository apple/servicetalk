/*
 * Copyright © 2019, 2021-2022 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.http.api.HttpClient;
import io.servicetalk.http.api.HttpProtocolVersion;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.SingleAddressHttpClientBuilder;
import io.servicetalk.http.netty.HttpsProxyTest.TargetAddressCheckConnectionFactoryFilter;
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.api.ServerContext;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.http.api.HttpHeaderNames.HOST;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.api.HttpSerializers.textSerializerUtf8;
import static io.servicetalk.http.netty.HttpProtocol.HTTP_1;
import static io.servicetalk.http.netty.HttpProtocol.HTTP_2;
import static io.servicetalk.http.netty.HttpsProxyTest.safeClose;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.util.Arrays.asList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

class HttpProxyTest {

    @Nullable
    private HttpClient proxyClient;
    @Nullable
    private ServerContext proxyContext;
    @Nullable
    private HostAndPort proxyAddress;
    @Nullable
    private ServerContext serverContext;
    @Nullable
    private HostAndPort serverAddress;
    private final AtomicInteger proxyRequestCount = new AtomicInteger();
    private final AtomicReference<Object> targetAddress = new AtomicReference<>();

    private void setUp(HttpProtocol clientProtocol, HttpProtocol serverProtocol) throws Exception {
        startProxy(clientProtocol, serverProtocol);
        startServer(serverProtocol);
    }

    @AfterEach
    void tearDown() {
        safeClose(proxyClient);
        safeClose(proxyContext);
        safeClose(serverContext);
    }

    private void startProxy(HttpProtocol clientProtocol, HttpProtocol serverProtocol) throws Exception {
        proxyClient = HttpClients.forMultiAddressUrl(getClass().getSimpleName())
                .initializer((scheme, address, builder) -> builder.protocols(serverProtocol.config))
                .build();
        proxyContext = HttpServers.forAddress(localAddress(0))
                .protocols(clientProtocol.config)
                .listenAndAwait((ctx, request, responseFactory) -> {
                    proxyRequestCount.incrementAndGet();
                    return proxyClient.request(request.version(serverProtocol.version))
                            .map(response -> response.version(clientProtocol.version));
                });
        proxyAddress = serverHostAndPort(proxyContext);
    }

    private void startServer(HttpProtocol protocol) throws Exception {
        serverContext = HttpServers.forAddress(localAddress(0))
                .protocols(protocol.config)
                .listenAndAwait((ctx, request, responseFactory) -> succeeded(responseFactory.ok()
                        .payloadBody("host: " + request.headers().get(HOST), textSerializerUtf8())));
        serverAddress = serverHostAndPort(serverContext);
    }

    private static List<Arguments> protocols() {
        return asList(Arguments.of(HTTP_1, HTTP_1), Arguments.of(HTTP_2, HTTP_2),
                Arguments.of(HTTP_1, HTTP_2), Arguments.of(HTTP_2, HTTP_1));
    }

    @ParameterizedTest(name = "[{index}] clientProtocol={0} serverProtocol={1}")
    @MethodSource("protocols")
    void testRequestForSingleAddress(HttpProtocol clientProtocol, HttpProtocol serverProtocol) throws Exception {
        testRequest(clientProtocol, serverProtocol, HttpClients::forSingleAddress);
    }

    @ParameterizedTest(name = "[{index}] clientProtocol={0} serverProtocol={1}")
    @MethodSource("protocols")
    void testRequestForResolvedAddress(HttpProtocol clientProtocol, HttpProtocol serverProtocol) throws Exception {
        testRequest(clientProtocol, serverProtocol, HttpClients::forResolvedAddress);
    }

    private void testRequest(
            HttpProtocol clientProtocol, HttpProtocol serverProtocol,
            Function<HostAndPort, SingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress>> clientBuilderFactory)
            throws Exception {
        setUp(clientProtocol, serverProtocol);
        assert serverAddress != null && proxyAddress != null;

        try (BlockingHttpClient client = clientBuilderFactory.apply(serverAddress)
                .proxyAddress(proxyAddress)
                .protocols(clientProtocol.config)
                .appendConnectionFactoryFilter(new TargetAddressCheckConnectionFactoryFilter(targetAddress, false))
                .buildBlocking()) {

            assertResponse(client.request(client.get("/path")), clientProtocol.version);
        }
    }

    @ParameterizedTest(name = "[{index}] protocol={0}")
    @EnumSource(HttpProtocol.class)
    void testBuilderReuseEachClientUsesOwnProxy(HttpProtocol protocol) throws Exception {
        setUp(protocol, protocol);
        assert serverAddress != null && proxyAddress != null;

        final SingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> builder =
                HttpClients.forSingleAddress(serverAddress)
                        .protocols(protocol.config);

        final AtomicInteger otherProxyRequestCount = new AtomicInteger();
        try (BlockingHttpClient client = builder.proxyAddress(proxyAddress).buildBlocking();
            HttpClient otherProxyClient = HttpClients.forMultiAddressUrl(getClass().getSimpleName())
                .initializer((scheme, address, builder1) -> builder1.protocols(protocol.config))
                .build();
            ServerContext otherProxyContext = HttpServers.forAddress(localAddress(0))
                .protocols(protocol.config)
                .listenAndAwait((ctx, request, responseFactory) -> {
                    otherProxyRequestCount.incrementAndGet();
                    return otherProxyClient.request(request);
                });
             BlockingHttpClient otherClient = builder.proxyAddress(serverHostAndPort(otherProxyContext))
                     .protocols(protocol.config)
                     .appendConnectionFactoryFilter(new TargetAddressCheckConnectionFactoryFilter(targetAddress, false))
                     .buildBlocking()) {

            assertResponse(otherClient.request(client.get("/path")), protocol.version, otherProxyRequestCount);
            assertThat(proxyRequestCount.get(), is(0));
            assertResponse(client.request(client.get("/path")), protocol.version);
            assertThat(otherProxyRequestCount.get(), is(1));
        }
    }

    private void assertResponse(HttpResponse httpResponse, HttpProtocolVersion expectedVersion) {
        assertResponse(httpResponse, expectedVersion, proxyRequestCount);
    }

    private void assertResponse(HttpResponse httpResponse, HttpProtocolVersion expectedVersion,
                                AtomicInteger proxyRequestCount) {
        assert serverAddress != null;
        assertThat(httpResponse.status(), is(OK));
        assertThat(httpResponse.version(), is(expectedVersion));
        assertThat(proxyRequestCount.get(), is(1));
        assertThat(httpResponse.payloadBody().toString(US_ASCII), is("host: " + serverAddress));
        assertThat(targetAddress.get(), is(equalTo(serverAddress.toString())));
    }
}
