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

import io.servicetalk.http.api.BlockingHttpClient;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.netty.HttpClients;
import io.servicetalk.test.resources.DefaultTestCerts;

import static io.servicetalk.http.api.HttpSerializationProviders.textDeserializer;

/**
 * A client that does mutual TLS.
 */
public final class HttpClientMutualTLS {

    public static void main(String[] args) throws Exception {
        // Note: this example demonstrates only blocking-aggregated programming paradigm, for asynchronous and
        // streaming API see helloworld examples.
        try (BlockingHttpClient client = HttpClients.forSingleAddress("localhost", 8080)
                .secure()
                // Our self-signed certificates do not support hostname verification, but this MUST NOT be disabled in
                // production because it may leave you vulnerable to MITM attacks.
                .disableHostnameVerification()
                // The client only trusts the CA which signed the example server's certificate.
                .trustManager(DefaultTestCerts::loadServerCAPem)
                // Specify the client's certificate/key pair to use to authenticate to the server.
                .keyManager(DefaultTestCerts::loadClientPem, DefaultTestCerts::loadClientKey).commit()
                .buildBlocking()) {
            HttpResponse response = client.request(client.get("/"));
            System.out.println(response.toString((name, value) -> value));
            System.out.println(response.payloadBody(textDeserializer()));
        }
    }
}
