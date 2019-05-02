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
package io.servicetalk.examples.http.metadata;

import io.servicetalk.http.api.HttpClient;
import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.http.netty.HttpClients;

import static io.servicetalk.concurrent.api.Single.failed;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_LANGUAGE;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.api.HttpSerializationProviders.textDeserializer;

/**
 * Demonstrates a few features:
 * <ul>
 * <li>Setting query parameters.</li>
 * <li>Checking response status.</li>
 * <li>Checking response headers.</li>
 * <li>Printing headers without redaction/filtering.</li>
 * </ul>
 */
public final class MetaDataDemoClient {

    public static final String LANGUAGE_NAME = "language";

    public static void main(String[] args) throws Exception {
        final String language = args.length == 1 ? args[0] : "en";
        // "en" returns "Hello World!"
        // "fr" returns "Bonjour monde!"
        // Try "broken" to demonstrate the incorrect language response error handling.
        // Try anything else to demonstrate incorrect response status error handling.

        try (HttpClient client = HttpClients.forSingleAddress("localhost", 8080).build()) {
            final HttpRequest httpRequest = client.get("/sayHello")
                    .addQueryParameters(LANGUAGE_NAME, language);
            String responseBody = client.request(httpRequest)
                    .flatMap(response -> {
                        // The `BiFunction` to `toString` can be used to print all or some header values without
                        // redaction/filtering.
                        System.out.println(response.toString((name, value) -> value));

                        if (!response.status().equals(OK)) {
                            return failed(new RuntimeException("Bad response status: " + response.status()));
                        }
                        // Case insensitively check if the expected language is present.
                        if (!response.headers().contains(CONTENT_LANGUAGE, language, true)) {
                            return failed(new RuntimeException("Incorrect language: " +
                                    response.headers().get(CONTENT_LANGUAGE)));
                        }
                        return succeeded(response.payloadBody(textDeserializer()));
                    })
                    // This example is demonstrating asynchronous execution, but needs to prevent the main thread from
                    // exiting before the response has been processed. This isn't typical usage for a streaming API but
                    // is useful for demonstration purposes.
                    .toFuture().get();
            System.out.println("Response body: " + responseBody);
        }
    }
}
