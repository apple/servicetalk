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
import io.servicetalk.http.api.BlockingHttpConnection;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.test.resources.DefaultTestCerts;
import io.servicetalk.transport.api.ClientSslConfigBuilder;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.api.ServerSslConfigBuilder;
import io.servicetalk.transport.api.SslProvider;
import io.servicetalk.transport.netty.internal.ExecutionContextRule;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Collection;
import javax.annotation.Nullable;
import javax.net.ssl.SSLSession;

import static io.servicetalk.http.api.HttpExecutionStrategies.defaultStrategy;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_TYPE;
import static io.servicetalk.http.api.HttpHeaderValues.TEXT_PLAIN_UTF_8;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.api.HttpSerializationProviders.textDeserializer;
import static io.servicetalk.http.api.HttpSerializationProviders.textSerializer;
import static io.servicetalk.logging.api.LogLevel.TRACE;
import static io.servicetalk.test.resources.DefaultTestCerts.serverPemHostname;
import static io.servicetalk.transport.api.SslProvider.JDK;
import static io.servicetalk.transport.api.SslProvider.OPENSSL;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static io.servicetalk.transport.netty.internal.ExecutionContextRule.cached;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.Objects.requireNonNull;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

@RunWith(Parameterized.class)
public class Tls13Test {

    @ClassRule
    public static final ExecutionContextRule SERVER_CTX = cached("server-io", "server-executor");
    @ClassRule
    public static final ExecutionContextRule CLIENT_CTX = cached("client-io", "client-executor");

    private static final String TLS1_3 = "TLSv1.3";
    private static final String TLS1_3_REQUIRED_CIPHER = "TLS_AES_128_GCM_SHA256";

    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();

    private final SslProvider serverSslProvider;
    private final SslProvider clientSslProvider;
    @Nullable
    private final String cipher;

    public Tls13Test(SslProvider serverSslProvider, SslProvider clientSslProvider, @Nullable String cipher) {
        this.serverSslProvider = requireNonNull(serverSslProvider);
        this.clientSslProvider = requireNonNull(clientSslProvider);
        this.cipher = cipher;
    }

    @Parameterized.Parameters(name = "server={0} client={1} cipher={2}")
    public static Collection<Object[]> sslProviders() {
        return asList(new Object[]{JDK, JDK, null},
                new Object[]{JDK, JDK, TLS1_3_REQUIRED_CIPHER},
                new Object[]{JDK, OPENSSL, null},
                new Object[]{JDK, OPENSSL, TLS1_3_REQUIRED_CIPHER},
                new Object[]{OPENSSL, JDK, null},
                new Object[]{OPENSSL, JDK, TLS1_3_REQUIRED_CIPHER},
                new Object[]{OPENSSL, OPENSSL, null},
                new Object[]{OPENSSL, OPENSSL, TLS1_3_REQUIRED_CIPHER}
        );
    }

    @Test
    public void requiredCipher() throws Exception {
        ServerSslConfigBuilder serverSslBuilder = new ServerSslConfigBuilder(
                DefaultTestCerts::loadServerPem, DefaultTestCerts::loadServerKey)
                .sslProtocols(TLS1_3).provider(serverSslProvider);
        if (cipher != null) {
            serverSslBuilder.ciphers(singletonList(cipher));
        }
        try (ServerContext serverContext = HttpServers.forAddress(localAddress(0))
                .ioExecutor(SERVER_CTX.ioExecutor())
                .executionStrategy(defaultStrategy(SERVER_CTX.executor()))
                .enableWireLogging("servicetalk-tests-wire-logger", TRACE, () -> false)
                .sslConfig(serverSslBuilder.build())
                .listenBlockingAndAwait((ctx, request, responseFactory) -> {
                    assertThat(request.payloadBody(textDeserializer()), equalTo("request-payload-body"));
                    SSLSession sslSession = ctx.sslSession();
                    assertThat(sslSession, is(notNullValue()));
                    return responseFactory.ok().payloadBody(sslSession.getProtocol(), textSerializer());
                })) {

            ClientSslConfigBuilder clientSslBuilder =
                    new ClientSslConfigBuilder(DefaultTestCerts::loadServerCAPem)
                    .sslProtocols(TLS1_3).peerHost(serverPemHostname()).provider(clientSslProvider);
            if (cipher != null) {
                clientSslBuilder.ciphers(singletonList(cipher));
            }
            try (BlockingHttpClient client = HttpClients.forSingleAddress(serverHostAndPort(serverContext))
                    .ioExecutor(CLIENT_CTX.ioExecutor())
                    .executionStrategy(defaultStrategy(CLIENT_CTX.executor()))
                    .enableWireLogging("servicetalk-tests-wire-logger", TRACE, () -> false)
                    .sslConfig(clientSslBuilder.build()).buildBlocking();
                 BlockingHttpConnection connection = client.reserveConnection(client.get("/"))) {

                SSLSession sslSession = connection.connectionContext().sslSession();
                assertThat(sslSession, is(notNullValue()));
                assertThat(sslSession.getProtocol(), equalTo(TLS1_3));
                if (cipher != null) {
                    assertThat(sslSession.getCipherSuite(), equalTo(cipher));
                }
                HttpResponse response = client.request(client.post("/")
                        .payloadBody("request-payload-body", textSerializer()));

                assertThat(response.status(), is(OK));
                assertThat(response.headers().get(CONTENT_TYPE), is(TEXT_PLAIN_UTF_8));
                assertThat(response.payloadBody(textDeserializer()), equalTo(TLS1_3));
            }
        }
    }
}
