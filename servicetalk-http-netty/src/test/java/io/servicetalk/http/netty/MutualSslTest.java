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
import io.servicetalk.http.api.HttpResponseStatus;
import io.servicetalk.test.resources.DefaultTestCerts;
import io.servicetalk.transport.api.SecurityConfigurator.SslProvider;
import io.servicetalk.transport.api.ServerContext;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Collection;

import static io.servicetalk.transport.api.SecurityConfigurator.SslProvider.JDK;
import static io.servicetalk.transport.api.SecurityConfigurator.SslProvider.OPENSSL;
import static io.servicetalk.transport.api.ServerSecurityConfigurator.ClientAuth.REQUIRE;
import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;

@RunWith(Parameterized.class)
public class MutualSslTest {
    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();
    private final SslProvider serverSslProvider;
    private final SslProvider clientSslProvider;

    public MutualSslTest(final SslProvider serverSslProvider, final SslProvider clientSslProvider) {
        this.serverSslProvider = serverSslProvider;
        this.clientSslProvider = clientSslProvider;
    }

    @Parameterized.Parameters(name = "server={0} client={1}")
    public static Collection<Object[]> sslProviders() {
        return asList(
                new Object[]{JDK, JDK},
                new Object[]{JDK, OPENSSL},
                new Object[]{OPENSSL, JDK},
                new Object[]{OPENSSL, OPENSSL}
        );
    }

    @Test
    public void mutualSsl() throws Exception {
        try (ServerContext serverContext = HttpServers.forAddress(localAddress(0))
                .secure()
                .provider(serverSslProvider)
                .clientAuth(REQUIRE)
                .trustManager(DefaultTestCerts::loadClientCAPem)
                .keyManager(DefaultTestCerts::loadServerPem, DefaultTestCerts::loadServerKey).commit()
                .listenBlockingAndAwait((ctx, request, responseFactory) -> responseFactory.ok());
             BlockingHttpClient client = HttpClients.forSingleAddress(serverHostAndPort(serverContext))
                     .secure()
                     .provider(clientSslProvider)
                     .disableHostnameVerification() // test certificates hostname isn't coordinated with each test
                     .trustManager(DefaultTestCerts::loadServerCAPem)
                     .keyManager(DefaultTestCerts::loadClientPem, DefaultTestCerts::loadClientKey)
                     .commit()
                     .buildBlocking()) {
            assertEquals(HttpResponseStatus.OK, client.request(client.get("/")).status());
        }
    }
}
