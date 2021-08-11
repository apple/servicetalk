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
package io.servicetalk.examples.http.mutualtls;

import io.servicetalk.http.netty.HttpServers;
import io.servicetalk.test.resources.DefaultTestCerts;
import io.servicetalk.transport.api.ServerSslConfigBuilder;
import io.servicetalk.transport.api.SslClientAuthMode;

import static io.servicetalk.http.api.HttpSerializers.textSerializerUtf8;

/**
 * A server that does mutual TLS.
 */
public final class HttpServerMutualTLS {

    public static void main(String[] args) throws Exception {
        HttpServers.forPort(8080)
                .sslConfig(new ServerSslConfigBuilder(DefaultTestCerts::loadServerPem, DefaultTestCerts::loadServerKey)
                        // Require clients to authenticate, otherwise a handshake may succeed without authentication.
                        .clientAuthMode(SslClientAuthMode.REQUIRE)
                        // The server only trusts the CA which signed the example client's certificate.
                        .trustManager(DefaultTestCerts::loadClientCAPem).build())
                // Note: this example demonstrates only blocking-aggregated programming paradigm, for asynchronous and
                // streaming API see helloworld examples.
                .listenBlockingAndAwait((ctx, request, responseFactory) -> responseFactory.ok()
                        .payloadBody("Client and Server completed Mutual TLS!", textSerializerUtf8()))
                .awaitShutdown();
    }
}
