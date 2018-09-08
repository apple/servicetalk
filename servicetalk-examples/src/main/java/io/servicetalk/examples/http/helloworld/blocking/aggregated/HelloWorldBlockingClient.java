/*
 * Copyright © 2018 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.examples.http.helloworld.blocking.aggregated;

import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.BlockingHttpClient;
import io.servicetalk.http.api.HttpPayloadChunk;
import io.servicetalk.http.netty.HttpClients;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.servicetalk.http.api.HttpRequests.newRequest;
import static io.servicetalk.http.api.HttpRequestMethods.GET;
import static java.nio.charset.StandardCharsets.US_ASCII;

public final class HelloWorldBlockingClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(HelloWorldBlockingClient.class);

    public static void main(String[] args) throws Exception {
        // Build the client with DNS and round robin load balancing
        try(BlockingHttpClient client = HttpClients
                .forSingleAddress("localhost",8080)
                .buildStreaming().asBlockingClient()) {

            // Create a request, send the request, and wait for the response.
            HttpResponse<HttpPayloadChunk> response = client.request(newRequest(GET, "/sayHello"));

            // Log the response meta data and headers, by default the header values will be filtered for
            // security reasons, however here we override the filter and print every value.
            LOGGER.info("got response {}", response.toString((name, value) -> value));

            // This aggregated all the response payload chunks in a single HttpPayloadChunk. Note that data is streaming
            // in the background so this may be synchronous as opposed to blocking, but if data is not available the
            // APIs will have to block.
            LOGGER.info("converted string chunk '{}'", response.getPayloadBody().getContent().toString(US_ASCII));
        }
    }
}
