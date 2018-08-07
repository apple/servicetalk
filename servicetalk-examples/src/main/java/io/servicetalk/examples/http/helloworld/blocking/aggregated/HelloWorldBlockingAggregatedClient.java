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

import io.servicetalk.http.api.AggregatedHttpResponse;
import io.servicetalk.http.api.BlockingAggregatedHttpClient;
import io.servicetalk.http.api.HttpPayloadChunk;
import io.servicetalk.http.netty.DefaultHttpClientBuilder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.servicetalk.http.api.AggregatedHttpRequests.newRequest;
import static io.servicetalk.http.api.HttpRequestMethods.GET;
import static io.servicetalk.http.netty.DefaultHttpClientBuilder.forSingleAddress;
import static java.nio.charset.StandardCharsets.US_ASCII;

public final class HelloWorldBlockingAggregatedClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(HelloWorldBlockingAggregatedClient.class);

    public static void main(String[] args) throws Exception {
        // Build the client with DNS and round robin load balancing
        try(BlockingAggregatedHttpClient client = DefaultHttpClientBuilder
                .forSingleAddress("localhost",8080)
                .build().asBlockingAggregatedClient()) {

            // Create a request, send the request, and wait for the response.
            AggregatedHttpResponse<HttpPayloadChunk> response = client.request(newRequest(GET, "/sayHello"));

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
