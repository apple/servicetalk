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
import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.http.netty.HttpClients;
import io.servicetalk.test.resources.DefaultTestCerts;
import io.servicetalk.transport.api.ClientSslConfigBuilder;
import io.servicetalk.transport.api.HostAndPort;

import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.examples.http.redirects.RedirectingServer.CUSTOM_HEADER;
import static io.servicetalk.examples.http.redirects.RedirectingServer.NON_SECURE_SERVER_PORT;
import static io.servicetalk.examples.http.redirects.RedirectingServer.SECURE_SERVER_PORT;
import static io.servicetalk.http.api.HttpHeaderNames.LOCATION;
import static io.servicetalk.http.api.HttpResponseStatus.StatusClass.REDIRECTION_3XX;
import static io.servicetalk.http.api.HttpSerializers.textSerializerAscii;

/**
 * Async "Hello World" example that demonstrates how redirects can be handled manually between multiple
 * {@link HttpClients#forSingleAddress(HostAndPort) single-address} clients with possibilities to:
 * <ol>
 *     <li>Change the target server or perform a relative redirect.</li>
 *     <li>Preserve headers while redirecting.</li>
 *     <li>Preserve payload body while redirecting.</li>
 * </ol>
 * This is a specialized use-case. For simplification, consider using one
 * {@link HttpClients#forMultiAddressUrl() multi-address} client, demonstrated in {@link MultiAddressUrlRedirectClient}
 * example.
 */
public final class ManualRedirectClient {
    public static void main(String... args) throws Exception {
        try (HttpClient secureClient = HttpClients.forSingleAddress("localhost", SECURE_SERVER_PORT)
                // The custom SSL configuration here is necessary only because this example uses self-signed
                // certificates. For cases when it's enough to use the local trust store, the trust cert supplier
                // argument for ClientSslConfigBuilder may be skipped.
                .sslConfig(new ClientSslConfigBuilder(DefaultTestCerts::loadServerCAPem).build()).build()) {

            try (HttpClient client = HttpClients.forSingleAddress("localhost", NON_SECURE_SERVER_PORT).build()) {
                System.out.println("- Redirect of a GET request with a custom header:");
                HttpRequest originalGet = client.get("/non-relative")
                        .addHeader(CUSTOM_HEADER, "value");
                client.request(originalGet)
                        .flatMap(response -> {
                            if (response.status().statusClass() == REDIRECTION_3XX) {
                                CharSequence location = response.headers().get(LOCATION);
                                HttpClient redirectClient = lookupClient(location, client, secureClient);
                                return redirectClient.request(redirectClient
                                        .newRequest(originalGet.method(), location.toString())
                                        .addHeader(CUSTOM_HEADER, originalGet.headers().get(CUSTOM_HEADER)));
                            }
                            // Decided not to follow redirect, return the original response or an error:
                            return succeeded(response);
                        })
                        .whenOnSuccess(resp -> {
                            System.out.println(resp.toString((name, value) -> value));
                            System.out.println(resp.payloadBody(textSerializerAscii()));
                            System.out.println();
                        })
                        // This example is demonstrating asynchronous execution, but needs to prevent the main thread
                        // from exiting before the response has been processed. This isn't typical usage for an
                        // asynchronous API but is useful for demonstration purposes.
                        .toFuture().get();

                System.out.println("- Redirect of a POST request with a payload body:");
                HttpRequest originalPost = client.post("/non-relative")
                        .payloadBody(client.executionContext().bufferAllocator().fromAscii("some_content"));
                client.request(originalPost)
                        .flatMap(response -> {
                            if (response.status().statusClass() == REDIRECTION_3XX) {
                                CharSequence location = response.headers().get(LOCATION);
                                HttpClient redirectClient = lookupClient(location, client, secureClient);
                                return redirectClient.request(redirectClient
                                        .newRequest(originalPost.method(), location.toString())
                                        .payloadBody(originalPost.payloadBody()));
                            }
                            // Decided not to follow redirect, return the original response or an error:
                            return succeeded(response);
                        })
                        .whenOnSuccess(resp -> {
                            System.out.println(resp.toString((name, value) -> value));
                            System.out.println(resp.payloadBody(textSerializerAscii()));
                        })
                        // This example is demonstrating asynchronous execution, but needs to prevent the main thread
                        // from exiting before the response has been processed. This isn't typical usage for an
                        // asynchronous API but is useful for demonstration purposes.
                        .toFuture().get();
            }
        }
    }

    private static HttpClient lookupClient(@Nullable CharSequence location, HttpClient sameClient,
                                           HttpClient secureClient) {
        if (location == null || location.length() < 1) {
            throw new IllegalArgumentException("Response does not contain redirect location");
        }
        String loc = location.toString();
        if (loc.charAt(0) == '/' || loc.startsWith("http://localhost:" + NON_SECURE_SERVER_PORT)) {
            return sameClient; // Relative redirect
        }
        if (loc.startsWith("https://localhost:" + SECURE_SERVER_PORT)) {
            return secureClient;    // Redirect to a different target server
        }
        throw new IllegalArgumentException("Attempt to redirect to unknown or untrusted target server");
    }
}
