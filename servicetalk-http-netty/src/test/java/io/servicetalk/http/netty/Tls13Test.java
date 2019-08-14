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

import io.servicetalk.http.api.BlockingHttpClient;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.test.resources.DefaultTestCerts;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.api.SslConfig.SslProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Collection;

import static io.netty.util.internal.PlatformDependent.javaVersion;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_TYPE;
import static io.servicetalk.http.api.HttpHeaderValues.TEXT_PLAIN_UTF_8;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.api.HttpSerializationProviders.textDeserializer;
import static io.servicetalk.http.api.HttpSerializationProviders.textSerializer;
import static io.servicetalk.transport.api.SslConfig.SslProvider.JDK;
import static io.servicetalk.transport.api.SslConfig.SslProvider.OPENSSL;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.Objects.requireNonNull;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

@RunWith(Parameterized.class)
public class Tls13Test {
    private static final String TLS1_3 = "TLSv1.3";
    private static final String TLS1_3_REQUIRED_CIPHER = "TLS_AES_128_GCM_SHA256";

    private final SslProvider serverSslProvider;
    private final SslProvider clientSslProvider;

    public Tls13Test(SslProvider serverSslProvider, SslProvider clientSslProvider) {
        this.serverSslProvider = requireNonNull(serverSslProvider);
        this.clientSslProvider = requireNonNull(clientSslProvider);
    }

    @Parameterized.Parameters(name = "server={0} client={1}")
    public static Collection<Object[]> sslProviders() {
        // TLSv1.3 is not currently supported in JDK8.
        return javaVersion() < 11 ? singletonList(new Object[]{OPENSSL, OPENSSL}) :
                asList(new Object[]{JDK, JDK},
                        new Object[]{JDK, OPENSSL},
                        new Object[]{OPENSSL, JDK},
                        new Object[]{OPENSSL, OPENSSL}
        );
    }

    @Test
    public void requiredCipher() throws Exception {
        try(ServerContext serverContext = HttpServers.forAddress(localAddress(0))
                .enableSsl(DefaultTestCerts::loadServerPem, DefaultTestCerts::loadServerKey)
                .protocols(TLS1_3)
                .ciphers(singletonList(TLS1_3_REQUIRED_CIPHER))
                .provider(serverSslProvider)
                .finish()
                .listenBlockingAndAwait((ctx, request, responseFactory) -> responseFactory.ok()
                        .payloadBody(request.payloadBody(textDeserializer()), textSerializer()))) {

            try (BlockingHttpClient client = HttpClients.forSingleAddress(serverHostAndPort(serverContext))
                    .enableSsl()
                    .protocols(TLS1_3)
                    .ciphers(singletonList(TLS1_3_REQUIRED_CIPHER))
                    .disableHostnameVerification()
                    // required for generated test certificates
                    .trustManager(DefaultTestCerts::loadMutualAuthCaPem)
                    .provider(clientSslProvider)
                    .finish()
                    .buildBlocking()) {

                String requestPayload = "hello " + TLS1_3;
                HttpResponse response = client.request(client.get("/").payloadBody(requestPayload, textSerializer()));

                assertThat(response.status(), is(OK));
                assertThat(response.headers().get(CONTENT_TYPE), is(TEXT_PLAIN_UTF_8));
                assertThat(response.payloadBody(textDeserializer()), is(requestPayload));
            }
        }
    }
}
