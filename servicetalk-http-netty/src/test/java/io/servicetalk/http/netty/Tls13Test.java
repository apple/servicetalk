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
import io.servicetalk.http.api.BlockingHttpConnection;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.test.resources.DefaultTestCerts;
import io.servicetalk.transport.api.ClientSslConfigBuilder;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.api.ServerSslConfigBuilder;
import io.servicetalk.transport.api.SslProvider;
import io.servicetalk.transport.netty.internal.ExecutionContextExtension;

import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;
import javax.annotation.Nullable;
import javax.net.ssl.SSLSession;

import static io.servicetalk.http.api.HttpExecutionStrategies.defaultStrategy;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_TYPE;
import static io.servicetalk.http.api.HttpHeaderValues.TEXT_PLAIN_UTF_8;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.api.HttpSerializers.textSerializerUtf8;
import static io.servicetalk.http.netty.HttpServers.forAddress;
import static io.servicetalk.logging.api.LogLevel.TRACE;
import static io.servicetalk.test.resources.DefaultTestCerts.serverPemHostname;
import static io.servicetalk.transport.api.SslProvider.JDK;
import static io.servicetalk.transport.api.SslProvider.OPENSSL;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static java.util.Collections.singletonList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

class Tls13Test {

    @RegisterExtension
    static final ExecutionContextExtension SERVER_CTX =
        ExecutionContextExtension.cached("server-io", "server-executor");
    @RegisterExtension
    static final ExecutionContextExtension CLIENT_CTX =
        ExecutionContextExtension.cached("client-io", "client-executor");

    private static final String TLS1_3 = "TLSv1.3";
    private static final String TLS1_3_REQUIRED_CIPHER = "TLS_AES_128_GCM_SHA256";

    @SuppressWarnings("unused")
    private static Stream<Arguments> sslProviders() {
        return Stream.of(
                Arguments.of(JDK, JDK, null),
                Arguments.of(JDK, JDK, TLS1_3_REQUIRED_CIPHER),
                Arguments.of(JDK, OPENSSL, null),
                Arguments.of(JDK, OPENSSL, TLS1_3_REQUIRED_CIPHER),
                Arguments.of(OPENSSL, JDK, null),
                Arguments.of(OPENSSL, JDK, TLS1_3_REQUIRED_CIPHER),
                Arguments.of(OPENSSL, OPENSSL, null),
                Arguments.of(OPENSSL, OPENSSL, TLS1_3_REQUIRED_CIPHER)
        );
    }

    @ParameterizedTest
    @MethodSource("sslProviders")
    void requiredCipher(SslProvider serverSslProvider, SslProvider clientSslProvider, @Nullable String cipher)
        throws Exception {
        ServerSslConfigBuilder serverSslBuilder = new ServerSslConfigBuilder(
                DefaultTestCerts::loadServerPem, DefaultTestCerts::loadServerKey)
                .sslProtocols(TLS1_3).provider(serverSslProvider);
        if (cipher != null) {
            serverSslBuilder.ciphers(singletonList(cipher));
        }
        try (ServerContext serverContext = forAddress(localAddress(0))
            .ioExecutor(SERVER_CTX.ioExecutor())
            .executionStrategy(defaultStrategy(SERVER_CTX.executor()))
            .enableWireLogging("servicetalk-tests-wire-logger", TRACE, () -> false)
            .sslConfig(serverSslBuilder.build())
            .listenBlockingAndAwait((ctx, request, responseFactory) -> {
                assertThat(request.payloadBody(textSerializerUtf8()), equalTo("request-payload-body"));
                SSLSession sslSession = ctx.sslSession();
                assertThat(sslSession, is(notNullValue()));
                return responseFactory.ok().payloadBody(sslSession.getProtocol(), textSerializerUtf8());
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
                                                           .payloadBody("request-payload-body", textSerializerUtf8()));

                assertThat(response.status(), is(OK));
                assertThat(response.headers().get(CONTENT_TYPE), is(TEXT_PLAIN_UTF_8));
                assertThat(response.payloadBody(textSerializerUtf8()), equalTo(TLS1_3));
            }
        }
    }
}
