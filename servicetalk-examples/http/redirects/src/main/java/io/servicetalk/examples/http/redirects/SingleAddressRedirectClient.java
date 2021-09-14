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
import io.servicetalk.http.utils.RedirectingHttpRequesterFilter;

import static io.servicetalk.examples.http.redirects.RedirectingServer.CUSTOM_HEADER;
import static io.servicetalk.examples.http.redirects.RedirectingServer.NON_SECURE_SERVER_PORT;
import static io.servicetalk.http.api.HttpRequestMethod.GET;
import static io.servicetalk.http.api.HttpRequestMethod.POST;
import static io.servicetalk.http.api.HttpSerializers.textSerializerAscii;

/**
 * Async `Hello World` example that demonstrates how <b>relative</b> redirects can be handled automatically by a
 * {@link HttpClients#forSingleAddress(String, int) single-address} client.
 * <p>
 * Because single-address client can communicate with only one target server it can follow only relative redirects.
 */
public final class SingleAddressRedirectClient {
    public static void main(String... args) throws Exception {
        try (HttpClient client = HttpClients.forSingleAddress("localhost", NON_SECURE_SERVER_PORT)
                // Enables redirection:
                .appendClientFilter(new RedirectingHttpRequesterFilter.Builder()
                        .allowedMethods(GET, POST)  // by default, POST requests don't follow redirects:
                        .build())
                .build()) {

            // Simple GET request:
            client.request(client.get("/relative"))
                    .whenOnSuccess(resp -> {
                        System.out.println(resp.toString((name, value) -> value));
                        System.out.println(resp.payloadBody(textSerializerAscii()));
                        System.out.println();
                    })
                    // This example is demonstrating asynchronous execution, but needs to prevent the main thread from
                    // exiting before the response has been processed. This isn't typical usage for an asynchronous API
                    // but is useful for demonstration purposes.
                    .toFuture().get();

            // POST request with headers and payload body:
            client.request(client.post("/relative")
                    .addHeader(CUSTOM_HEADER, "value")
                    .payloadBody(client.executionContext().bufferAllocator().fromAscii("some_content")))
                    .whenOnSuccess(resp -> {
                        System.out.println(resp.toString((name, value) -> value));
                        System.out.println(resp.payloadBody(textSerializerAscii()));
                        System.out.println();
                    })
                    // This example is demonstrating asynchronous execution, but needs to prevent the main thread from
                    // exiting before the response has been processed. This isn't typical usage for an asynchronous API
                    // but is useful for demonstration purposes.
                    .toFuture().get();
        }
    }
}
