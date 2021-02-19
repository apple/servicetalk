/*
 * Copyright © 2021 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.http.api.HttpResponseStatus;
import io.servicetalk.test.resources.DefaultTestCerts;
import io.servicetalk.transport.api.ServerContext;

import org.junit.Test;

import java.util.function.Function;
import javax.net.ssl.SSLHandshakeException;

import static io.servicetalk.transport.netty.internal.AddressUtils.localAddress;
import static io.servicetalk.transport.netty.internal.AddressUtils.serverHostAndPort;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class SniTest {
    private static final String SNI_HOSTNAME = "servicetalk.io";
    @Test
    public void sniSuccess() throws Exception {
        try (ServerContext serverContext = HttpServers.forAddress(localAddress(0))
                .secure()
                // Need a key that won't be trusted by the client, just use the client's key.
                .keyManager(DefaultTestCerts::loadClientPem, DefaultTestCerts::loadClientKey)
                .newSniConfig(SNI_HOSTNAME)
                .keyManager(DefaultTestCerts::loadServerPem, DefaultTestCerts::loadServerKey)
                .commit()
                .listenBlockingAndAwait((ctx, request, responseFactory) -> responseFactory.ok());
             BlockingHttpClient client = newClient(serverContext)) {
            assertEquals(HttpResponseStatus.OK, client.request(client.get("/")).status());
        }
    }

    @Test
    public void sniDefaultFallbackSuccess() throws Exception {
        sniDefaultFallbackSuccess(SniTest::newClient);
    }

    private static void sniDefaultFallbackSuccess(Function<ServerContext, BlockingHttpClient> clientFunc)
            throws Exception {
        try (ServerContext serverContext = HttpServers.forAddress(localAddress(0))
                .secure()
                .keyManager(DefaultTestCerts::loadServerPem, DefaultTestCerts::loadServerKey)
                .newSniConfig("no_match" + SNI_HOSTNAME)
                // Need a key that won't be trusted by the client, just use the client's key.
                .keyManager(DefaultTestCerts::loadClientPem, DefaultTestCerts::loadClientKey)
                .commit()
                .listenBlockingAndAwait((ctx, request, responseFactory) -> responseFactory.ok());
             BlockingHttpClient client = clientFunc.apply(serverContext)) {
            assertEquals(HttpResponseStatus.OK, client.request(client.get("/")).status());
        }
    }

    @Test
    public void sniFailExpected() throws Exception {
        try (ServerContext serverContext = HttpServers.forAddress(localAddress(0))
                .secure()
                .keyManager(DefaultTestCerts::loadServerPem, DefaultTestCerts::loadServerKey)
                .newSniConfig(SNI_HOSTNAME)
                // Need a key that won't be trusted by the client, just use the client's key.
                .keyManager(DefaultTestCerts::loadClientPem, DefaultTestCerts::loadClientKey)
                .commit()
                .listenBlockingAndAwait((ctx, request, responseFactory) -> responseFactory.ok());
             BlockingHttpClient client = newClient(serverContext)) {
            assertThrows(SSLHandshakeException.class, () -> client.request(client.get("/")));
        }
    }

    @Test
    public void sniDefaultFallbackFailExpected() throws Exception {
        try (ServerContext serverContext = HttpServers.forAddress(localAddress(0))
                .secure()
                // Need a key that won't be trusted by the client, just use the client's key.
                .keyManager(DefaultTestCerts::loadClientPem, DefaultTestCerts::loadClientKey)
                .newSniConfig("no_match" + SNI_HOSTNAME)
                .keyManager(DefaultTestCerts::loadServerPem, DefaultTestCerts::loadServerKey)
                .commit()
                .listenBlockingAndAwait((ctx, request, responseFactory) -> responseFactory.ok());
             BlockingHttpClient client = newClient(serverContext)) {
            assertThrows(SSLHandshakeException.class, () -> client.request(client.get("/")));
        }
    }

    @Test
    public void sniClientDefaultServerSuccess() throws Exception {
        try (ServerContext serverContext = HttpServers.forAddress(localAddress(0))
                .secure()
                .keyManager(DefaultTestCerts::loadServerPem, DefaultTestCerts::loadServerKey)
                .commit()
                .listenBlockingAndAwait((ctx, request, responseFactory) -> responseFactory.ok());
             BlockingHttpClient client = newClient(serverContext)) {
            assertEquals(HttpResponseStatus.OK, client.request(client.get("/")).status());
        }
    }

    @Test
    public void noSniClientDefaultServerFallbackSuccess() throws Exception {
        sniDefaultFallbackSuccess(serverContext -> HttpClients.forSingleAddress(serverHostAndPort(serverContext))
                .secure()
                .disableHostnameVerification() // test certificates hostname isn't coordinated with each test
                .trustManager(DefaultTestCerts::loadServerCAPem)
                .commit()
                .buildBlocking());
    }

    private static BlockingHttpClient newClient(ServerContext serverContext) {
        return HttpClients.forSingleAddress(serverHostAndPort(serverContext))
                .secure()
                .sniHostname(SNI_HOSTNAME)
                .disableHostnameVerification() // test certificates hostname isn't coordinated with each test
                .trustManager(DefaultTestCerts::loadServerCAPem)
                .commit()
                .buildBlocking();
    }
}
