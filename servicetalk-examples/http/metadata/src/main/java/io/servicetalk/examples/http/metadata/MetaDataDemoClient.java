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

import io.servicetalk.http.api.BlockingHttpClient;
import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.netty.HttpClients;

import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_LANGUAGE;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.api.HttpSerializers.textSerializerUtf8;

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
        // Try anything else to demonstrate incorrect response status error handling.

        // Note: this example demonstrates only blocking-aggregated programming paradigm, for asynchronous and
        // streaming API see helloworld examples.
        try (BlockingHttpClient client = HttpClients.forSingleAddress("localhost", 8080).buildBlocking()) {
            HttpRequest httpRequest = client.get("/sayHello")
                    .addQueryParameters(LANGUAGE_NAME, language);
            HttpResponse response = client.request(httpRequest);
            // The `BiFunction` to `toString` can be used to print all or some header values without
            // redaction/filtering.
            System.out.println(response.toString((name, value) -> value));

            if (!response.status().equals(OK)) {
                throw new RuntimeException("Bad response status: " + response.status());
            }
            // Case insensitively check if the expected language is present.
            if (!response.headers().containsIgnoreCase(CONTENT_LANGUAGE, language)) {
                throw new RuntimeException("Incorrect language: " +
                        response.headers().get(CONTENT_LANGUAGE));
            }

            String responseBody = response.payloadBody(textSerializerUtf8());
            System.out.println("Response body: " + responseBody);
        }
    }
}
