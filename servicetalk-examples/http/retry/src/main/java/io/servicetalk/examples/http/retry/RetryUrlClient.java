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
package io.servicetalk.examples.http.retry;

import io.servicetalk.http.api.HttpClient;
import io.servicetalk.http.api.HttpResponseStatus;
import io.servicetalk.http.netty.HttpClients;
import io.servicetalk.http.netty.RetryingHttpRequesterFilter;

import java.time.Duration;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static io.servicetalk.http.api.HttpResponseStatus.BAD_GATEWAY;
import static io.servicetalk.http.api.HttpResponseStatus.GATEWAY_TIMEOUT;
import static io.servicetalk.http.api.HttpResponseStatus.SERVICE_UNAVAILABLE;
import static io.servicetalk.http.api.HttpResponseStatus.TOO_MANY_REQUESTS;
import static io.servicetalk.http.api.HttpSerializers.textSerializerUtf8;

/**
 * Extends the Async "Hello World" multi-address client to retry requests that receive a retryable status code in
 * response. Up to three attempts total (one initial attempt and up to two retries) with exponential backoff and jitter
 * will be made before failure.
 */
public class RetryUrlClient {

    private static final Set<HttpResponseStatus> RETRYABLE_STATUS_CODES;

    static {
        Set<HttpResponseStatus> set = new HashSet<>();
        // Modify the set of status codes as needed
        set.add(TOO_MANY_REQUESTS);   // 429
        set.add(BAD_GATEWAY);         // 502
        set.add(SERVICE_UNAVAILABLE); // 503
        set.add(GATEWAY_TIMEOUT);     // 504
        RETRYABLE_STATUS_CODES = Collections.unmodifiableSet(set);
    }

    public static void main(String[] args) throws Exception {
        try (HttpClient client = HttpClients.forMultiAddressUrl().initializer((scheme, address, builder) -> {
            // If necessary, users can set different retry strategies based on `scheme` and/or `address`.
            builder.appendClientFilter(new RetryingHttpRequesterFilter.Builder()
                    .responseMapper(httpResponseMetaData ->
                            RETRYABLE_STATUS_CODES.contains(httpResponseMetaData.status()) ?
                                    // Response status is retryable
                                    new RetryingHttpRequesterFilter.HttpResponseException(
                                            "Retry 5XX", httpResponseMetaData)
                                    // Not a retryable status
                                    : null)
                    .retryResponses((meta, error) -> RetryingHttpRequesterFilter.BackOffPolicy.ofExponentialBackoffDeltaJitter(
                            Duration.ofMillis(10),  // initialDelay
                            Duration.ofMillis(10),  // jitter
                            Duration.ofMillis(100), // maxDelay
                            2))                     // maxRetries
                    .build());
        }).build()) {
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
