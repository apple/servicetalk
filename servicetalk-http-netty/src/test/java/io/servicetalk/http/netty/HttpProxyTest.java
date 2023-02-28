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
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.SingleAddressHttpClientBuilder;
import io.servicetalk.http.netty.HttpsProxyTest.TargetAddressCheckConnectionFactoryFilter;
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.api.ServerContext;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.http.api.HttpHeaderNames.HOST;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.api.HttpSerializers.textSerializerUtf8;
import static io.servicetalk.http.netty.HttpsProxyTest.safeClose;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static java.nio.charset.StandardCharsets.US_ASCII;
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

    @BeforeEach
    void setup() throws Exception {
        startProxy();
        startServer();
    }

    @AfterEach
    void tearDown() {
        safeClose(proxyClient);
        safeClose(proxyContext);
        safeClose(serverContext);
    }

    void startProxy() throws Exception {
        proxyClient = HttpClients.forMultiAddressUrl(getClass().getSimpleName()).build();
        proxyContext = HttpServers.forAddress(localAddress(0))
                .listenAndAwait((ctx, request, responseFactory) -> {
                    proxyRequestCount.incrementAndGet();
                    return proxyClient.request(request);
                });
        proxyAddress = serverHostAndPort(proxyContext);
    }

    void startServer() throws Exception {
        serverContext = HttpServers.forAddress(localAddress(0))
                .listenAndAwait((ctx, request, responseFactory) -> succeeded(responseFactory.ok()
                        .payloadBody("host: " + request.headers().get(HOST), textSerializerUtf8())));
        serverAddress = serverHostAndPort(serverContext);
    }

    private enum ClientSource {
        SINGLE(HttpClients::forSingleAddress),
        RESOLVED(HttpClients::forResolvedAddress);

        private final Function<HostAndPort, SingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress>>
                clientBuilderFactory;

        ClientSource(Function<HostAndPort, SingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress>>
                             clientBuilderFactory) {
            this.clientBuilderFactory = clientBuilderFactory;
        }
    }

    @ParameterizedTest(name = "[{index}] client = {0}")
    @EnumSource
    void testRequest(ClientSource clientSource) throws Exception {
        assert serverAddress != null && proxyAddress != null;

        final BlockingHttpClient client = clientSource.clientBuilderFactory.apply(serverAddress)
                .proxyAddress(proxyAddress)
                .appendConnectionFactoryFilter(new TargetAddressCheckConnectionFactoryFilter(targetAddress, false))
                .buildBlocking();

        final HttpResponse httpResponse = client.request(client.get("/path"));
        assertThat(httpResponse.status(), is(OK));
        assertThat(proxyRequestCount.get(), is(1));
        assertThat(httpResponse.payloadBody().toString(US_ASCII), is("host: " + serverAddress));
        assertThat(targetAddress.get(), is(equalTo(serverAddress.toString())));
        safeClose(client);
    }

    @Test
    void testBuilderReuseEachClientUsesOwnProxy() throws Exception {
        final SingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> builder =
                HttpClients.forSingleAddress(serverAddress);
        final BlockingHttpClient client = builder.proxyAddress(proxyAddress).buildBlocking();

        final HttpClient otherProxyClient = HttpClients.forMultiAddressUrl(getClass().getSimpleName()).build();
        final AtomicInteger otherProxyRequestCount = new AtomicInteger();
        try (ServerContext otherProxyContext = HttpServers.forAddress(localAddress(0))
                .listenAndAwait((ctx, request, responseFactory) -> {
                    otherProxyRequestCount.incrementAndGet();
                    return otherProxyClient.request(request);
                });
             BlockingHttpClient otherClient = builder.proxyAddress(serverHostAndPort(otherProxyContext))
                     .appendConnectionFactoryFilter(new TargetAddressCheckConnectionFactoryFilter(targetAddress, false))
                     .buildBlocking()) {

            final HttpResponse httpResponse = otherClient.request(client.get("/path"));
            assertThat(httpResponse.status(), is(OK));
            assertThat(otherProxyRequestCount.get(), is(1));
            assertThat(httpResponse.payloadBody().toString(US_ASCII), is("host: " + serverAddress));
        }

        final HttpResponse httpResponse = client.request(client.get("/path"));
        assertThat(httpResponse.status(), is(OK));
        assertThat(proxyRequestCount.get(), is(1));
        assertThat(httpResponse.payloadBody().toString(US_ASCII), is("host: " + serverAddress));
        assertThat(targetAddress.get(), is(equalTo(serverAddress.toString())));
    }
}
