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
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.test.resources.DefaultTestCerts;
import io.servicetalk.transport.api.ClientSslConfigBuilder;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.api.ServerSslConfigBuilder;
import io.servicetalk.transport.api.SslProvider;
import io.servicetalk.transport.netty.NettyIoExecutors;
import io.servicetalk.transport.netty.internal.IoThreadFactory;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_TYPE;
import static io.servicetalk.http.api.HttpHeaderValues.TEXT_PLAIN_UTF_8;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.api.HttpSerializers.textSerializerUtf8;
import static io.servicetalk.test.resources.DefaultTestCerts.serverPemHostname;
import static io.servicetalk.transport.api.SslProvider.JDK;
import static io.servicetalk.transport.api.SslProvider.OPENSSL;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

class SslProvidersTest {

    private String payloadBody;
    private ServerContext serverContext;
    private BlockingHttpClient client;

    private void setUp(SslProvider serverSslProvider, SslProvider clientSslProvider, int payloadLength)
            throws Exception {

        payloadBody = randomString(payloadLength);

        serverContext = HttpServers.forAddress(localAddress(0))
                .sslConfig(new ServerSslConfigBuilder(
                        DefaultTestCerts::loadServerPem, DefaultTestCerts::loadServerKey)
                        .provider(serverSslProvider).build())
                .listenBlockingAndAwait((ctx, request, responseFactory) -> {
                    assertThat(ctx.sslSession(), is(notNullValue()));
                    assertThat(request.path(), is("/path"));
                    assertThat(request.headers().get(CONTENT_TYPE), is(TEXT_PLAIN_UTF_8));
                    assertThat(request.payloadBody(textSerializerUtf8()),
                            is("request-payload-body-" + payloadBody));

                    return responseFactory.ok()
                            .payloadBody("response-payload-body-" + payloadBody, textSerializerUtf8());
                });

        client = HttpClients.forSingleAddress(serverHostAndPort(serverContext))
                .ioExecutor(NettyIoExecutors.createIoExecutor(new IoThreadFactory("client-io")))
                .sslConfig(new ClientSslConfigBuilder(DefaultTestCerts::loadServerCAPem)
                        .peerHost(serverPemHostname()).provider(clientSslProvider).build())
                .buildBlocking();
    }

    @SuppressWarnings("unused")
    private static Stream<Arguments> sslProviders() {
        return Stream.of(
                Arguments.of(JDK, JDK, 256),
                Arguments.of(JDK, OPENSSL, 256),
                Arguments.of(OPENSSL, JDK, 256),
                Arguments.of(OPENSSL, OPENSSL, 256),
                Arguments.of(JDK, JDK, 16384),
                Arguments.of(JDK, OPENSSL, 16384),
                Arguments.of(OPENSSL, JDK, 16384),
                Arguments.of(OPENSSL, OPENSSL, 16384));
    }

    @AfterEach
    void tearDown() throws Exception {
        try {
            client.close();
        } finally {
            serverContext.close();
        }
    }

    @ParameterizedTest
    @MethodSource("sslProviders")
    void testSecureClientToSecureServer(SslProvider serverSslProvider,
                                        SslProvider clientSslProvider,
                                        int payloadLength)
        throws Exception {
        setUp(serverSslProvider, clientSslProvider, payloadLength);

        HttpResponse response = client.request(client.get("/path")
                .payloadBody("request-payload-body-" + payloadBody, textSerializerUtf8()));

        assertThat(response.status(), is(OK));
        assertThat(response.headers().get(CONTENT_TYPE), is(TEXT_PLAIN_UTF_8));
        assertThat(response.payloadBody(textSerializerUtf8()), is("response-payload-body-" + payloadBody));
    }

    private static String randomString(final int length) {
        final StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append((char) ThreadLocalRandom.current().nextInt('a', 'z' + 1));
        }
        return sb.toString();
    }
}
