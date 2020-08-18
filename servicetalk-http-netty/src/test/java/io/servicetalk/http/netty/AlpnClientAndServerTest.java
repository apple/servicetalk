/*
 * Copyright © 2019 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.http.api.HttpProtocolConfig;
import io.servicetalk.http.api.HttpProtocolVersion;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.ReservedBlockingHttpConnection;
import io.servicetalk.test.resources.DefaultTestCerts;
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.api.ServerContext;

import io.netty.handler.codec.DecoderException;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import java.nio.channels.ClosedChannelException;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nullable;

import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.api.HttpSerializationProviders.textDeserializer;
import static io.servicetalk.http.api.HttpSerializationProviders.textSerializer;
import static io.servicetalk.http.netty.AlpnIds.HTTP_1_1;
import static io.servicetalk.http.netty.AlpnIds.HTTP_2;
import static io.servicetalk.http.netty.HttpProtocolConfigs.h1Default;
import static io.servicetalk.http.netty.HttpProtocolConfigs.h2Default;
import static io.servicetalk.transport.api.SecurityConfigurator.SslProvider.OPENSSL;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertThrows;

@RunWith(Parameterized.class)
public class AlpnClientAndServerTest {

    private static final String PAYLOAD_BODY = "Hello World!";

    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();

    private final ServerContext serverContext;
    private final BlockingHttpClient client;
    @Nullable
    private final HttpProtocolVersion expectedProtocol;
    @Nullable
    private final Class<? extends Throwable> expectedExceptionType;

    private final AtomicReference<HttpServiceContext> serviceContext = new AtomicReference<>();
    private final AtomicReference<HttpProtocolVersion> requestVersion = new AtomicReference<>();

    public AlpnClientAndServerTest(List<String> serverSideProtocols,
                                   List<String> clientSideProtocols,
                                   @Nullable HttpProtocolVersion expectedProtocol,
                                   @Nullable Class<? extends Throwable> expectedExceptionType) throws Exception {
        serverContext = startServer(serverSideProtocols, expectedProtocol);
        client = startClient(serverHostAndPort(serverContext), clientSideProtocols);
        this.expectedProtocol = expectedProtocol;
        this.expectedExceptionType = expectedExceptionType;
    }

    @Parameters(name =
            "serverAlpnProtocols={0}, clientAlpnProtocols={1}, expectedProtocol={2}, expectedExceptionType={3}")
    public static Collection<Object[]> clientExecutors() {
        return asList(new Object[][] {
                {asList(HTTP_2, HTTP_1_1), asList(HTTP_2, HTTP_1_1), HttpProtocolVersion.HTTP_2_0, null},
                {asList(HTTP_2, HTTP_1_1), asList(HTTP_1_1, HTTP_2), HttpProtocolVersion.HTTP_2_0, null},
                {asList(HTTP_2, HTTP_1_1), singletonList(HTTP_2), HttpProtocolVersion.HTTP_2_0, null},
                {asList(HTTP_2, HTTP_1_1), singletonList(HTTP_1_1), HttpProtocolVersion.HTTP_1_1, null},

                {asList(HTTP_1_1, HTTP_2), asList(HTTP_2, HTTP_1_1), HttpProtocolVersion.HTTP_1_1, null},
                {asList(HTTP_1_1, HTTP_2), asList(HTTP_1_1, HTTP_2), HttpProtocolVersion.HTTP_1_1, null},
                {asList(HTTP_1_1, HTTP_2), singletonList(HTTP_2), HttpProtocolVersion.HTTP_2_0, null},
                {asList(HTTP_1_1, HTTP_2), singletonList(HTTP_1_1), HttpProtocolVersion.HTTP_1_1, null},

                {singletonList(HTTP_2), asList(HTTP_2, HTTP_1_1), HttpProtocolVersion.HTTP_2_0, null},
                {singletonList(HTTP_2), asList(HTTP_1_1, HTTP_2), HttpProtocolVersion.HTTP_2_0, null},
                {singletonList(HTTP_2), singletonList(HTTP_2), HttpProtocolVersion.HTTP_2_0, null},
                {singletonList(HTTP_2), singletonList(HTTP_1_1), null, DecoderException.class},

                {singletonList(HTTP_1_1), asList(HTTP_2, HTTP_1_1), HttpProtocolVersion.HTTP_1_1, null},
                {singletonList(HTTP_1_1), asList(HTTP_1_1, HTTP_2), HttpProtocolVersion.HTTP_1_1, null},
                {singletonList(HTTP_1_1), singletonList(HTTP_2), null, ClosedChannelException.class},
                {singletonList(HTTP_1_1), singletonList(HTTP_1_1), HttpProtocolVersion.HTTP_1_1, null},
        });
    }

    private ServerContext startServer(List<String> supportedProtocols,
                                      @Nullable HttpProtocolVersion expectedProtocol) throws Exception {
        return HttpServers.forAddress(localAddress(0))
                .protocols(toProtocolConfigs(supportedProtocols))
                .secure()
                .provider(OPENSSL)
                .commit(DefaultTestCerts::loadServerPem, DefaultTestCerts::loadServerKey)
                .listenBlocking((ctx, request, responseFactory) -> {
                    serviceContext.set(ctx);
                    requestVersion.set(request.version());
                    return responseFactory.ok().payloadBody(PAYLOAD_BODY, textSerializer());
                })
                .toFuture().get();
    }

    private static BlockingHttpClient startClient(HostAndPort hostAndPort, List<String> supportedProtocols) {
        return HttpClients.forSingleAddress(hostAndPort)
                .protocols(toProtocolConfigs(supportedProtocols))
                .secure()
                .disableHostnameVerification()
                // required for generated test certificates
                .trustManager(DefaultTestCerts::loadMutualAuthCaPem)
                .provider(OPENSSL)
                .commit()
                .buildBlocking();
    }

    private static HttpProtocolConfig[] toProtocolConfigs(List<String> supportedProtocols) {
        return supportedProtocols.stream()
                .map(id -> {
                    switch (id) {
                        case HTTP_1_1:
                            return h1Default();
                        case HTTP_2:
                            return h2Default();
                        default:
                            throw new IllegalArgumentException("Unsupported protocol: " + id);
                    }
                }).toArray(HttpProtocolConfig[]::new);
    }

    @After
    public void tearDown() throws Exception {
        try {
            client.close();
        } finally {
            serverContext.close();
        }
    }

    @Test
    public void testAlpn() throws Exception {
        if (expectedExceptionType != null) {
            assertThrows(expectedExceptionType, () -> client.request(client.get("/")));
            return;
        }

        try (ReservedBlockingHttpConnection connection = client.reserveConnection(client.get("/"))) {
            assertThat(connection.connectionContext().protocol(), is(expectedProtocol));
            assertThat(connection.connectionContext().sslSession(), is(notNullValue()));

            HttpResponse response = connection.request(client.get("/"));
            assertThat(response.version(), is(expectedProtocol));
            assertThat(response.status(), is(OK));
            assertThat(response.payloadBody(textDeserializer()), is(PAYLOAD_BODY));

            assertThat(serviceContext.get().protocol(), is(expectedProtocol));
            assertThat(serviceContext.get().sslSession(), is(notNullValue()));
            assertThat(requestVersion.get(), is(expectedProtocol));
        }
    }
}
