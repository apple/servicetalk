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
    @Nullable
    private BlockingHttpClient client;
    private final AtomicInteger proxyRequestCount = new AtomicInteger();

    @BeforeEach
    void setup() throws Exception {
        startProxy();
        startServer();
        createClient();
    }

    @AfterEach
    void tearDown() {
        safeClose(client);
        safeClose(proxyClient);
        safeClose(proxyContext);
        safeClose(serverContext);
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

    void startServer() throws Exception {
        serverContext = HttpServers.forAddress(localAddress(0))
                .listenAndAwait((ctx, request, responseFactory) -> succeeded(responseFactory.ok()
                        .payloadBody("host: " + request.headers().get(HOST), textSerializerUtf8())));
        serverAddress = serverHostAndPort(serverContext);
    }

    void createClient() {
        assert serverAddress != null && proxyAddress != null;
        client = HttpClients.forSingleAddressViaProxy(serverAddress, proxyAddress)
                .buildBlocking();
    }

    @Test
    void testRequest() throws Exception {
        assert client != null;
        final HttpResponse httpResponse = client.request(client.get("/path"));
        assertThat(httpResponse.status(), is(OK));
        assertThat(proxyRequestCount.get(), is(1));
        assertThat(httpResponse.payloadBody().toString(US_ASCII), is("host: " + serverAddress));
    }
}
