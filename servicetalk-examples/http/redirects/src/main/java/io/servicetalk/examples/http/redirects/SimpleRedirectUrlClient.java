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
package io.servicetalk.examples.http.redirects;

import io.servicetalk.http.api.HttpClient;
import io.servicetalk.http.netty.HttpClients;
import io.servicetalk.test.resources.DefaultTestCerts;
import io.servicetalk.transport.api.ClientSslConfigBuilder;

import static io.servicetalk.http.api.HttpSerializers.textSerializerUtf8;

/**
 * Async `Hello World` example that demonstrates how redirects can be handled automatically by a
 * {@link HttpClients#forMultiAddressUrl() multi-address} client.
 *
 * Automatic redirects have limitations. See {@link RedirectWithStateUrlClient} for more information.
 */
public final class SimpleRedirectUrlClient {
    public static void main(String... args) throws Exception {
        try (HttpClient client = HttpClients.forMultiAddressUrl()
                // This is an optional configuration that applies more restrictive limit for the number or redirects:
                .maxRedirects(1)
                .initializer((scheme, address, builder) -> {
                    // The custom SSL configuration here is necessary only because this example uses self-signed
                    // certificates. For cases when it's enough to use the local trust store, MultiAddressUrl client
                    // already provides default SSL configuration and this step may be skipped.
                    if ("https".equalsIgnoreCase(scheme)) {
                        builder.sslConfig(new ClientSslConfigBuilder(DefaultTestCerts::loadServerCAPem).build());
                    }
                })
                .build()) {
            client.request(client.get("http://localhost:8080/sayHello"))
                    .whenOnSuccess(resp -> {
                        System.out.println(resp.toString((name, value) -> value));
                        System.out.println(resp.payloadBody(textSerializerUtf8()));
                    })
            // This example is demonstrating asynchronous execution, but needs to prevent the main thread from exiting
            // before the response has been processed. This isn't typical usage for an asynchronous API but is useful
            // for demonstration purposes.
                    .toFuture().get();
        }
    }
}
