/*
 * Copyright © 2020 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.http.api.BlockingHttpClient;
import io.servicetalk.http.api.BlockingHttpConnection;
import io.servicetalk.http.api.HttpConnectionContext.HttpProtocol;
import io.servicetalk.http.api.HttpProtocolConfig;
import io.servicetalk.http.api.HttpProtocolVersion;
import io.servicetalk.http.api.HttpServerBuilder;
import io.servicetalk.http.api.SingleAddressHttpClientBuilder;
import io.servicetalk.test.resources.DefaultTestCerts;
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.api.ServerContext;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import java.net.InetSocketAddress;

import static io.servicetalk.http.api.HttpSerializationProviders.textDeserializer;
import static io.servicetalk.http.api.HttpSerializationProviders.textSerializer;
import static io.servicetalk.http.netty.HttpProtocolConfigs.h1Default;
import static io.servicetalk.http.netty.HttpProtocolConfigs.h2Default;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

@RunWith(Parameterized.class)
public class HttpConnectionContextProtocolTest {

    private enum Config {

        HTTP_1_1(new HttpProtocolConfig[]{h1Default()}, false, HttpProtocolVersion.HTTP_1_1),
        HTTP_2_0(new HttpProtocolConfig[]{h2Default()}, false, HttpProtocolVersion.HTTP_2_0),
        SECURE_HTTP_1_1(new HttpProtocolConfig[]{h1Default()}, true, HttpProtocolVersion.HTTP_1_1),
        SECURE_HTTP_2_0(new HttpProtocolConfig[]{h2Default()}, true, HttpProtocolVersion.HTTP_2_0),
        ALPN_PREFER_HTTP_1_1(new HttpProtocolConfig[]{h1Default(), h2Default()}, true,
                HttpProtocolVersion.HTTP_1_1),
        ALPN_PREFER_HTTP_2_0(new HttpProtocolConfig[]{h2Default(), h1Default()}, true,
                HttpProtocolVersion.HTTP_2_0);

        final HttpProtocolConfig[] protocols;
        final boolean secure;
        final HttpProtocol expectedProtocol;

        Config(HttpProtocolConfig[] protocols, boolean secure, HttpProtocolVersion expectedProtocol) {
            this.protocols = protocols;
            this.secure = secure;
            this.expectedProtocol = expectedProtocol;
        }
    }

    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();

    private final Config config;

    public HttpConnectionContextProtocolTest(Config config) {
        this.config = config;
    }

    @Parameters(name = "config={0}")
    public static Object[] data() {
        return Config.values();
    }

    @Test
    public void testProtocol() throws Exception {
        try (ServerContext serverContext = startServer(config);
             BlockingHttpClient client = newClient(serverContext, config);
             BlockingHttpConnection connection = client.reserveConnection(client.get("/"))) {

            assertThat("Client-side connection protocol does not match expected value",
                    connection.connectionContext().protocol(), equalTo(config.expectedProtocol));
            assertThat("Server-side connection protocol does not match expected value",
                    connection.request(connection.get("/")).payloadBody(textDeserializer()),
                    equalTo(config.expectedProtocol.name()));
        }
    }

    private static ServerContext startServer(Config config) throws Exception {
        final HttpServerBuilder builder = HttpServers.forAddress(localAddress(0))
                .protocols(config.protocols);
        if (config.secure) {
            builder.secure().commit(DefaultTestCerts::loadServerPem, DefaultTestCerts::loadServerKey);
        }
        return builder.listenBlockingAndAwait((ctx, request, responseFactory) -> responseFactory.ok()
                .payloadBody(ctx.protocol().name(), textSerializer()));
    }

    private static BlockingHttpClient newClient(ServerContext serverContext, Config config) {
        SingleAddressHttpClientBuilder<HostAndPort, InetSocketAddress> builder =
                HttpClients.forSingleAddress(serverHostAndPort(serverContext)).protocols(config.protocols);
        if (config.secure) {
            builder.secure().disableHostnameVerification().trustManager(DefaultTestCerts::loadServerCAPem).commit();
        }
        return builder.buildBlocking();
    }
}
