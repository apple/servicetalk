/*
 * Copyright © 2023 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.http.api.BlockingHttpClient;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.netty.HttpClients;
import io.servicetalk.test.resources.DefaultTestCerts;
import io.servicetalk.transport.api.ClientSslConfigBuilder;
import io.servicetalk.transport.api.HostAndPort;

import static io.servicetalk.http.api.HttpSerializers.textSerializerUtf8;

/**
 * A multi-address client that does mutual TLS when needed.
 */
public class HttpUrlClientMutualTLS {

    public static void main(String[] args) throws Exception {
        final HostAndPort serverAddress = HostAndPort.of("localhost", 8080);
        // Note: this example demonstrates only blocking-aggregated programming paradigm, for asynchronous and
        // streaming API see helloworld examples.
        try (BlockingHttpClient client = HttpClients.forMultiAddressUrl().initializer((scheme, address, builder) -> {
            if (scheme.equals("https") && address.equals(serverAddress)) {
                // Note: DefaultTestCerts contains self-signed certificates that may be used only for local testing.
                // or demonstration purposes. Never use those for real use-cases.
                builder.sslConfig(new ClientSslConfigBuilder(DefaultTestCerts::loadServerCAPem)
                        // Specify the client's certificate/key pair to use to authenticate to the server.
                        .keyManager(DefaultTestCerts::loadClientPem, DefaultTestCerts::loadClientKey).build());
            }
        }).buildBlocking()) {
            final String requestTarget =
                    String.format("https://%s:%d/", serverAddress.hostName(), serverAddress.port());
            final HttpResponse response = client.request(client.get(requestTarget));
            System.out.println(response.toString((name, value) -> value));
            System.out.println(response.payloadBody(textSerializerUtf8()));
        }
    }
}
