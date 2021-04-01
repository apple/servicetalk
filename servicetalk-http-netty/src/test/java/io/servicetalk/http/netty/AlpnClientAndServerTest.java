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
import io.servicetalk.transport.api.ClientSslConfigBuilder;
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.api.ServerSslConfigBuilder;

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
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.VerificationTestUtils.assertThrows;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.api.HttpSerializationProviders.textDeserializer;
import static io.servicetalk.http.api.HttpSerializationProviders.textSerializer;
import static io.servicetalk.http.netty.AlpnIds.HTTP_1_1;
import static io.servicetalk.http.netty.AlpnIds.HTTP_2;
import static io.servicetalk.http.netty.HttpProtocolConfigs.h1Default;
import static io.servicetalk.http.netty.HttpProtocolConfigs.h2Default;
import static io.servicetalk.test.resources.DefaultTestCerts.serverPemHostname;
import static io.servicetalk.transport.api.SslProvider.OPENSSL;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

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
    @Nullable
    private final Class<? extends Throwable> optionalExceptionWrapperType;

    private final BlockingQueue<HttpServiceContext> serviceContext = new LinkedBlockingDeque<>();
    private final BlockingQueue<HttpProtocolVersion> requestVersion = new LinkedBlockingDeque<>();

    public AlpnClientAndServerTest(List<String> serverSideProtocols,
                                   List<String> clientSideProtocols,
                                   @Nullable HttpProtocolVersion expectedProtocol,
                                   @Nullable Class<? extends Throwable> expectedExceptionType,
                                   @Nullable Class<? extends Throwable> optionalExceptionWrapperType) throws Exception {
        serverContext = startServer(serverSideProtocols);
        client = startClient(serverHostAndPort(serverContext), clientSideProtocols);
        this.expectedProtocol = expectedProtocol;
        this.expectedExceptionType = expectedExceptionType;
        this.optionalExceptionWrapperType = optionalExceptionWrapperType;
    }

    @Parameters(name =
            "serverAlpnProtocols={0}, clientAlpnProtocols={1}, expectedProtocol={2}, expectedExceptionType={3}" +
            "optionalExceptionWrapperType={4}")
    public static Collection<Object[]> clientExecutors() {
        return asList(new Object[][] {
                {asList(HTTP_2, HTTP_1_1), asList(HTTP_2, HTTP_1_1), HttpProtocolVersion.HTTP_2_0, null, null},
                {asList(HTTP_2, HTTP_1_1), asList(HTTP_1_1, HTTP_2), HttpProtocolVersion.HTTP_2_0, null, null},
                {asList(HTTP_2, HTTP_1_1), singletonList(HTTP_2), HttpProtocolVersion.HTTP_2_0, null, null},
                {asList(HTTP_2, HTTP_1_1), singletonList(HTTP_1_1), HttpProtocolVersion.HTTP_1_1, null, null},

                {asList(HTTP_1_1, HTTP_2), asList(HTTP_2, HTTP_1_1), HttpProtocolVersion.HTTP_1_1, null, null},
                {asList(HTTP_1_1, HTTP_2), asList(HTTP_1_1, HTTP_2), HttpProtocolVersion.HTTP_1_1, null, null},
                {asList(HTTP_1_1, HTTP_2), singletonList(HTTP_2), HttpProtocolVersion.HTTP_2_0, null, null},
                {asList(HTTP_1_1, HTTP_2), singletonList(HTTP_1_1), HttpProtocolVersion.HTTP_1_1, null, null},

                {singletonList(HTTP_2), asList(HTTP_2, HTTP_1_1), HttpProtocolVersion.HTTP_2_0, null, null},
                {singletonList(HTTP_2), asList(HTTP_1_1, HTTP_2), HttpProtocolVersion.HTTP_2_0, null, null},
                {singletonList(HTTP_2), singletonList(HTTP_2), HttpProtocolVersion.HTTP_2_0, null, null},
                {singletonList(HTTP_2), singletonList(HTTP_1_1), null, DecoderException.class,
                        ClosedChannelException.class},

                {singletonList(HTTP_1_1), asList(HTTP_2, HTTP_1_1), HttpProtocolVersion.HTTP_1_1, null, null},
                {singletonList(HTTP_1_1), asList(HTTP_1_1, HTTP_2), HttpProtocolVersion.HTTP_1_1, null, null},
                {singletonList(HTTP_1_1), singletonList(HTTP_2), null, ClosedChannelException.class, null},
                {singletonList(HTTP_1_1), singletonList(HTTP_1_1), HttpProtocolVersion.HTTP_1_1, null, null},
        });
    }

    private ServerContext startServer(List<String> supportedProtocols) throws Exception {
        return HttpServers.forAddress(localAddress(0))
                .protocols(toProtocolConfigs(supportedProtocols))
                .sslConfig(new ServerSslConfigBuilder(DefaultTestCerts::loadServerPem,
                        DefaultTestCerts::loadServerKey).provider(OPENSSL).build())
                .listenBlocking((ctx, request, responseFactory) -> {
                    serviceContext.put(ctx);
                    requestVersion.put(request.version());
                    return responseFactory.ok().payloadBody(PAYLOAD_BODY, textSerializer());
                })
                .toFuture().get();
    }

    private static BlockingHttpClient startClient(HostAndPort hostAndPort, List<String> supportedProtocols) {
        return HttpClients.forSingleAddress(hostAndPort)
                .protocols(toProtocolConfigs(supportedProtocols))
                .sslConfig(new ClientSslConfigBuilder(DefaultTestCerts::loadServerCAPem)
                        .peerHost(serverPemHostname()).provider(OPENSSL).build())
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
    public void testAlpnConnection() throws Exception {
        if (expectedExceptionType != null) {
            assertThrows(expectedExceptionType, optionalExceptionWrapperType, () -> client.request(client.get("/")));
            return;
        }

        try (ReservedBlockingHttpConnection connection = client.reserveConnection(client.get("/"))) {
            assertThat(connection.connectionContext().protocol(), is(expectedProtocol));
            assertThat(connection.connectionContext().sslSession(), is(notNullValue()));

            assertResponseAndServiceContext(connection.request(client.get("/")));
        }
    }

    @Test
    public void testAlpnClient() throws Exception {
        if (expectedExceptionType != null) {
            assertThrows(expectedExceptionType, optionalExceptionWrapperType, () -> client.request(client.get("/")));
        } else {
            assertResponseAndServiceContext(client.request(client.get("/")));
        }
    }

    private void assertResponseAndServiceContext(HttpResponse response) throws Exception {
        assertThat(response.version(), is(expectedProtocol));
        assertThat(response.status(), is(OK));
        assertThat(response.payloadBody(textDeserializer()), is(PAYLOAD_BODY));

        HttpServiceContext serviceCtx = serviceContext.take();
        assertThat(serviceCtx.protocol(), is(expectedProtocol));
        assertThat(serviceCtx.sslSession(), is(notNullValue()));
        assertThat(requestVersion.take(), is(expectedProtocol));

        assertThat(serviceContext, is(empty()));
        assertThat(requestVersion, is(empty()));
    }
}
