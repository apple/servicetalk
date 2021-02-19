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
import io.servicetalk.transport.api.SecurityConfigurator.SslProvider;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.netty.NettyIoExecutors;
import io.servicetalk.transport.netty.internal.IoThreadFactory;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Collection;
import java.util.concurrent.ThreadLocalRandom;

import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_TYPE;
import static io.servicetalk.http.api.HttpHeaderValues.TEXT_PLAIN_UTF_8;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.api.HttpSerializationProviders.textDeserializer;
import static io.servicetalk.http.api.HttpSerializationProviders.textSerializer;
import static io.servicetalk.transport.api.SecurityConfigurator.SslProvider.JDK;
import static io.servicetalk.transport.api.SecurityConfigurator.SslProvider.OPENSSL;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static java.util.Arrays.asList;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertThat;

@RunWith(Parameterized.class)
public class SslProvidersTest {

    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();

    private final String payloadBody;
    private final ServerContext serverContext;
    private final BlockingHttpClient client;

    public SslProvidersTest(SslProvider serverSslProvider, SslProvider clientSslProvider, int payloadLength)
            throws Exception {

        payloadBody = randomString(payloadLength);

        serverContext = HttpServers.forAddress(localAddress(0))
                .secure()
                .provider(serverSslProvider)
                .keyManager(DefaultTestCerts::loadServerPem, DefaultTestCerts::loadServerKey).commit()
                .listenBlockingAndAwait((ctx, request, responseFactory) -> {
                    assertThat(ctx.sslSession(), is(notNullValue()));
                    assertThat(request.path(), is("/path"));
                    assertThat(request.headers().get(CONTENT_TYPE), is(TEXT_PLAIN_UTF_8));
                    assertThat(request.payloadBody(textDeserializer()), is("request-payload-body-" + payloadBody));

                    return responseFactory.ok()
                            .payloadBody("response-payload-body-" + payloadBody, textSerializer());
                });

        client = HttpClients.forSingleAddress(serverHostAndPort(serverContext))
                .ioExecutor(NettyIoExecutors.createIoExecutor(new IoThreadFactory("client-io")))
                .secure()
                .disableHostnameVerification()
                // required for generated test certificates
                .trustManager(DefaultTestCerts::loadServerCAPem)
                .provider(clientSslProvider)
                .commit()
                .buildBlocking();
    }

    @Parameterized.Parameters(name = "server={0} client={1} payloadLength={2}")
    public static Collection<Object[]> sslProviders() {
        return asList(
                new Object[]{JDK, JDK, 256},
                new Object[]{JDK, OPENSSL, 256},
                new Object[]{OPENSSL, JDK, 256},
                new Object[]{OPENSSL, OPENSSL, 256},
                new Object[]{JDK, JDK, 16384},
                new Object[]{JDK, OPENSSL, 16384},
                new Object[]{OPENSSL, JDK, 16384},
                new Object[]{OPENSSL, OPENSSL, 16384}
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
                .payloadBody("request-payload-body-" + payloadBody, textSerializer()));

        assertThat(response.status(), is(OK));
        assertThat(response.headers().get(CONTENT_TYPE), is(TEXT_PLAIN_UTF_8));
        assertThat(response.payloadBody(textDeserializer()), is("response-payload-body-" + payloadBody));
    }

    private static String randomString(final int length) {
        final StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append((char) ThreadLocalRandom.current().nextInt('a', 'z' + 1));
        }
        return sb.toString();
    }
}
