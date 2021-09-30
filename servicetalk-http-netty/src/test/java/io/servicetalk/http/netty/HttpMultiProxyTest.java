/*
 * Copyright © 2021 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.api.ServerContext;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
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
import static org.hamcrest.Matchers.is;

class HttpMultiProxyTest {

    @Nullable
    private HttpClient proxyClient;
    @Nullable
    private ServerContext proxyContext;
    @Nullable
    private HostAndPort proxyAddress;

    @Nullable
    private ServerContext serverBehindProxyContext;
    @Nullable
    private HostAndPort serverBehindProxyAddress;

    @Nullable
    private ServerContext serverWithoutProxyContext;
    @Nullable
    private HostAndPort serverWithoutProxyAddress;

    @Nullable
    private BlockingHttpClient client;

    private final AtomicInteger proxyRequestCount = new AtomicInteger();

    @BeforeEach
    void setup() throws Exception {
        startProxy();
        startServerBehindProxy();
        startServerWithoutProxy();
        createClient();
    }

    @AfterEach
    void tearDown() {
        safeClose(client);
        safeClose(proxyClient);
        safeClose(proxyContext);
        safeClose(serverBehindProxyContext);
    }

    void startProxy() throws Exception {
        proxyClient = HttpClients.forMultiAddressUrl().build();
        proxyContext = HttpServers.forAddress(localAddress(0))
                .listenAndAwait((ctx, request, responseFactory) -> {
                    proxyRequestCount.incrementAndGet();
                    return proxyClient.request(request);
                });
        proxyAddress = serverHostAndPort(proxyContext);
    }

    void startServerBehindProxy() throws Exception {
        serverBehindProxyContext = HttpServers.forAddress(localAddress(0))
                .listenAndAwait((ctx, request, responseFactory) -> succeeded(responseFactory.ok()
                        .payloadBody("host: " + request.headers().get(HOST), textSerializerUtf8())));
        serverBehindProxyAddress = serverHostAndPort(serverBehindProxyContext);
    }

    void startServerWithoutProxy() throws Exception {
        serverWithoutProxyContext = HttpServers.forAddress(localAddress(0))
                .listenAndAwait((ctx, request, responseFactory) -> succeeded(responseFactory.ok()
                        .payloadBody("host: " + request.headers().get(HOST), textSerializerUtf8())));
        serverWithoutProxyAddress = serverHostAndPort(serverWithoutProxyContext);
    }

    void createClient() {
        assert serverBehindProxyAddress != null && proxyAddress != null && serverWithoutProxyAddress != null;
        client = HttpClients
                .forMultiAddressUrl()
                .initializer((scheme, address, builder) -> {
                    if (address.port() == serverBehindProxyAddress.port()) {
                        builder.proxyAddress(proxyAddress);
                    }
                })
                .buildBlocking();
    }

    @Test
    void testRequest() throws Exception {
        assert client != null;

        final HttpResponse proxiedHttpResponse =
                client.request(client.get("http://" + serverBehindProxyAddress + "/path"));
        assertThat(proxiedHttpResponse.status(), is(OK));
        assertThat(proxiedHttpResponse.payloadBody().toString(US_ASCII),
                is("host: " + serverBehindProxyAddress));

        final HttpResponse rawHttpResponse =
                client.request(client.get("http://" + serverWithoutProxyAddress + "/path"));
        assertThat(rawHttpResponse.status(), is(OK));
        assertThat(rawHttpResponse.payloadBody().toString(US_ASCII),
                is("host: " + serverWithoutProxyAddress));

        assertThat(proxyRequestCount.get(), is(1));
    }
}
