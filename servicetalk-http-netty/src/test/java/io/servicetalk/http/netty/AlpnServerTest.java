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
import io.servicetalk.http.api.HttpProtocolVersion;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.test.resources.DefaultTestCerts;
import io.servicetalk.transport.api.HostAndPort;
import io.servicetalk.transport.api.ServerContext;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import java.nio.channels.ClosedChannelException;
import java.util.Collection;
import javax.annotation.Nullable;

import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.api.HttpSerializationProviders.textDeserializer;
import static io.servicetalk.http.api.HttpSerializationProviders.textSerializer;
import static io.servicetalk.http.netty.ApplicationProtocolNames.HTTP_1_1;
import static io.servicetalk.http.netty.ApplicationProtocolNames.HTTP_2;
import static io.servicetalk.transport.api.SecurityConfigurator.ApplicationProtocolNegotiation.ALPN;
import static io.servicetalk.transport.api.SecurityConfigurator.SelectedListenerFailureBehavior.ACCEPT;
import static io.servicetalk.transport.api.SecurityConfigurator.SelectorFailureBehavior.NO_ADVERTISE;
import static io.servicetalk.transport.api.SecurityConfigurator.SslProvider.OPENSSL;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertThat;
import static org.junit.rules.ExpectedException.none;

@RunWith(Parameterized.class)
public class AlpnServerTest {

    private static final String PAYLOAD_BODY = "Hello World!";

    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();

    @Rule
    public final ExpectedException expectedException = none();

    private final ServerContext serverContext;
    private final BlockingHttpClient client;
    private final HttpProtocolVersion expectedProtocol;
    @Nullable
    private final Class<? extends Throwable> expectedExceptionType;

    public AlpnServerTest(Collection<String> serverAlpnProtocols,
                          Collection<String> clientAlpnProtocols,
                          HttpProtocolVersion expectedProtocol,
                          @Nullable Class<? extends Throwable> expectedExceptionType) throws Exception {
        serverContext = startServer(serverAlpnProtocols);
        client = startClient(serverHostAndPort(serverContext), clientAlpnProtocols, expectedProtocol);
        this.expectedProtocol = expectedProtocol;
        this.expectedExceptionType = expectedExceptionType;
    }

    @Parameters(name =
            "serverAlpnProtocols={0}, clientAlpnProtocols={1}, expectedProtocol={2}, expectedExceptionType={3}")
    public static Collection<Object[]> clientExecutors() {
        return asList(new Object[][] {
                {asList(HTTP_2, HTTP_1_1), asList(HTTP_2, HTTP_1_1), HttpProtocolVersion.of(2, 0), null},
                {asList(HTTP_2, HTTP_1_1), asList(HTTP_1_1, HTTP_2), HttpProtocolVersion.of(2, 0), null},
                {asList(HTTP_2, HTTP_1_1), singletonList(HTTP_2), HttpProtocolVersion.of(2, 0), null},
                {asList(HTTP_2, HTTP_1_1), singletonList(HTTP_1_1), HttpProtocolVersion.HTTP_1_1, null},
                {asList(HTTP_2, HTTP_1_1), singletonList("unknown"), HttpProtocolVersion.HTTP_1_1, null},

                {asList(HTTP_1_1, HTTP_2), asList(HTTP_2, HTTP_1_1), HttpProtocolVersion.HTTP_1_1, null},
                {asList(HTTP_1_1, HTTP_2), asList(HTTP_1_1, HTTP_2), HttpProtocolVersion.HTTP_1_1, null},
                {asList(HTTP_1_1, HTTP_2), singletonList(HTTP_2), HttpProtocolVersion.of(2, 0), null},
                {asList(HTTP_1_1, HTTP_2), singletonList(HTTP_1_1), HttpProtocolVersion.HTTP_1_1, null},
                {asList(HTTP_1_1, HTTP_2), singletonList("unknown"), HttpProtocolVersion.HTTP_1_1, null},

                {singletonList(HTTP_2), asList(HTTP_2, HTTP_1_1), HttpProtocolVersion.of(2, 0), null},
                {singletonList(HTTP_2), asList(HTTP_1_1, HTTP_2), HttpProtocolVersion.of(2, 0), null},
                {singletonList(HTTP_2), singletonList(HTTP_2), HttpProtocolVersion.of(2, 0), null},
                {singletonList(HTTP_2), singletonList(HTTP_1_1), HttpProtocolVersion.HTTP_1_1, null},
                {singletonList(HTTP_2), singletonList("unknown"), HttpProtocolVersion.HTTP_1_1, null},

                {singletonList(HTTP_1_1), asList(HTTP_2, HTTP_1_1), HttpProtocolVersion.HTTP_1_1, null},
                {singletonList(HTTP_1_1), asList(HTTP_1_1, HTTP_2), HttpProtocolVersion.HTTP_1_1, null},
                {singletonList(HTTP_1_1), singletonList(HTTP_2), HttpProtocolVersion.HTTP_1_1, null},
                {singletonList(HTTP_1_1), singletonList(HTTP_1_1), HttpProtocolVersion.HTTP_1_1, null},
                {singletonList(HTTP_1_1), singletonList("unknown"), HttpProtocolVersion.HTTP_1_1, null},

                {singletonList("unknown"), asList(HTTP_2, HTTP_1_1), HttpProtocolVersion.HTTP_1_1, null},
                {singletonList("unknown"), asList(HTTP_1_1, HTTP_2), HttpProtocolVersion.HTTP_1_1, null},
                {singletonList("unknown"), singletonList(HTTP_2), HttpProtocolVersion.HTTP_1_1, null},
                {singletonList("unknown"), singletonList(HTTP_1_1), HttpProtocolVersion.HTTP_1_1, null},
                {singletonList("unknown"), singletonList("unknown"), null, ClosedChannelException.class},
        });
    }

    private static ServerContext startServer(Collection<String> supportedProtocols) throws Exception {
        return HttpServers.forAddress(localAddress(0))
                .secure()
                .provider(OPENSSL)
                .applicationProtocolNegotiation(ALPN, NO_ADVERTISE, ACCEPT, supportedProtocols)
                .commit(DefaultTestCerts::loadServerPem, DefaultTestCerts::loadServerKey)
                .listenBlocking((ctx, request, responseFactory) -> {
                    assertThat(ctx.sslSession(), is(notNullValue()));
                    return responseFactory.ok().payloadBody(PAYLOAD_BODY, textSerializer());
                })
                .toFuture().get();
    }

    private static BlockingHttpClient startClient(HostAndPort hostAndPort, Collection<String> supportedProtocols,
                                                  HttpProtocolVersion expectedProtocol) {
        return HttpClients.forSingleAddress(hostAndPort)
                // TODO: remove h2PriorKnowledge setting when client will support ALPN
                .h2PriorKnowledge(HttpProtocolVersion.of(2, 0).equals(expectedProtocol))
                .secure()
                .disableHostnameVerification()
                // required for generated test certificates
                .trustManager(DefaultTestCerts::loadMutualAuthCaPem)
                .provider(OPENSSL)
                .applicationProtocolNegotiation(ALPN, NO_ADVERTISE, ACCEPT, supportedProtocols)
                .commit()
                .buildBlocking();
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
            expectedException.expect(expectedExceptionType);
        }
        HttpResponse response = client.request(client.get("/"));
        if (expectedExceptionType == null) {
            assertThat(response.version(), is(expectedProtocol));
            assertThat(response.status(), is(OK));
            assertThat(response.payloadBody(textDeserializer()), is(PAYLOAD_BODY));
        }
    }
}
