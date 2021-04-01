/*
 * Copyright © 2018, 2021 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.examples.http.compression;

import io.servicetalk.encoding.api.ContentCodec;
import io.servicetalk.encoding.api.Identity;
import io.servicetalk.encoding.netty.ContentCodings;
import io.servicetalk.http.api.ContentCodingHttpRequesterFilter;
import io.servicetalk.http.api.HttpClient;
import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.http.netty.HttpClients;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import static io.servicetalk.http.api.HttpSerializationProviders.textDeserializer;
import static io.servicetalk.http.api.HttpSerializationProviders.textSerializer;

/**
 * Extends the async "Hello World" example to include compression of the request
 * and response bodies.
 */
public final class CompressionFilterExampleClient {

    /**
     * Encodings in preferred order.
     */
    private static final List<ContentCodec> PREFERRED_ENCODINGS =
            Collections.unmodifiableList(Arrays.asList(
                    ContentCodings.gzipDefault(),
                    ContentCodings.deflateDefault(),
                    Identity.identity()
            ));

    public static void main(String... args) throws Exception {
        try (HttpClient client = HttpClients.forSingleAddress("localhost", 8080)
                // Adds filter that provides compression for the request body when a request sets the encoding.
                // Also sets the accept encoding header for the server's response.
                .appendClientFilter(new ContentCodingHttpRequesterFilter(PREFERRED_ENCODINGS))
                .build()) {
            // This example is demonstrating asynchronous execution, but needs to prevent the main thread from exiting
            // before the response has been processed. This isn't typical usage for a streaming API but is useful for
            // demonstration purposes.
            CountDownLatch responseProcessedLatch = new CountDownLatch(2);

            // Make a request with an uncompressed payload.
            HttpRequest request = client.post("/sayHello")
                    // Request will be sent with no compression, which has the same effect as setting encoding to identity
                    // .encoding(ContentCodings.identity())
                    .payloadBody("George", textSerializer());
            client.request(request)
                    .afterFinally(responseProcessedLatch::countDown)
                    .subscribe(resp -> {
                        System.out.println(resp.toString((name, value) -> value));
                        System.out.println(resp.payloadBody(textDeserializer()));
                    });

            // Make a request with an gzip compressed payload.
            request = client.post("/sayHello")
                    // Encode the request using gzip.
                    .encoding(ContentCodings.gzipDefault())
                    .payloadBody("Gracie", textSerializer());
            client.request(request)
                    .afterFinally(responseProcessedLatch::countDown)
                    .subscribe(resp -> {
                        System.out.println(resp.toString((name, value) -> value));
                        System.out.println(resp.payloadBody(textDeserializer()));
                    });

            responseProcessedLatch.await();
        }
    }
}
