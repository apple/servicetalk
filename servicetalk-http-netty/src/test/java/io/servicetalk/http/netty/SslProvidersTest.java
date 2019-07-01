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
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.test.resources.DefaultTestCerts;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.api.SslConfig.SslProvider;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Collection;

import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_TYPE;
import static io.servicetalk.http.api.HttpHeaderValues.TEXT_PLAIN_UTF_8;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.api.HttpSerializationProviders.textDeserializer;
import static io.servicetalk.http.api.HttpSerializationProviders.textSerializer;
import static io.servicetalk.transport.api.SslConfig.SslProvider.JDK;
import static io.servicetalk.transport.api.SslConfig.SslProvider.OPENSSL;
import static io.servicetalk.transport.api.SslConfigBuilder.forClientWithoutServerIdentity;
import static io.servicetalk.transport.api.SslConfigBuilder.forServer;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static java.util.Arrays.asList;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

@RunWith(Parameterized.class)
public class SslProvidersTest {

    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();

    private final ServerContext serverContext;
    private final BlockingHttpClient client;

    public SslProvidersTest(SslProvider serverSslProvider, SslProvider clientSslProvider) throws Exception {
        serverContext = HttpServers.forAddress(localAddress(0))
                .sslConfig(forServer(DefaultTestCerts::loadServerPem, DefaultTestCerts::loadServerKey)
                        .provider(serverSslProvider)
                        .build())
                .listenBlockingAndAwait((ctx, request, responseFactory) -> {
                    assertThat(request.path(), is("/path"));
                    assertThat(request.headers().get(CONTENT_TYPE), is(TEXT_PLAIN_UTF_8));
                    assertThat(request.payloadBody(textDeserializer()), is("request-payload-body"));

                    return responseFactory.ok()
                            .payloadBody("response-payload-body", textSerializer());
                });

        client = HttpClients.forSingleAddress(serverHostAndPort(serverContext))
                .sslConfig(forClientWithoutServerIdentity()
                        // required for generated test certificates
                        .trustManager(DefaultTestCerts::loadMutualAuthCaPem)
                        .provider(clientSslProvider)
                        .build())
                .buildBlocking();
    }

    @Parameterized.Parameters(name = "server={0} client={1}")
    public static Collection<SslProvider[]> sslProviders() {
        return asList(
                new SslProvider[]{JDK, JDK},
                new SslProvider[]{JDK, OPENSSL},
                new SslProvider[]{OPENSSL, JDK},
                new SslProvider[]{OPENSSL, OPENSSL}
        );
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
    public void testSecureClientToSecureServer() throws Exception {
        HttpResponse response = client.request(client.get("/path")
                .payloadBody("request-payload-body", textSerializer()));

        assertThat(response.status(), is(OK));
        assertThat(response.headers().get(CONTENT_TYPE), is(TEXT_PLAIN_UTF_8));
        assertThat(response.payloadBody(textDeserializer()), is("response-payload-body"));
    }
}
