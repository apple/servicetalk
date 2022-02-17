/*
 * Copyright © 2022 Apple Inc. and the ServiceTalk project authors
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
import io.servicetalk.http.netty.HttpClients;
import io.servicetalk.http.netty.RetryingHttpRequesterFilter;

import static io.servicetalk.http.api.HttpResponseStatus.StatusClass.SERVER_ERROR_5XX;
import static io.servicetalk.http.api.HttpSerializers.textSerializerUtf8;

/**
 * Extends the Async "Hello World" client to immediately retry requests that get a 5XX response. Up to three attempts
 * * will be made, one initial attempt and up to two retries, before failure.
 */
public final class RetryUrlClient {
    public static void main(String[] args) throws Exception {
        try (HttpClient client = HttpClients.forMultiAddressUrl().initializer((scheme, address, builder) -> {
            builder.appendClientFilter(new RetryingHttpRequesterFilter.Builder()
                    .responseMapper(httpResponseMetaData -> SERVER_ERROR_5XX.contains(httpResponseMetaData.status()) ?
                            // Response status is 500-599, we request a retry
                            new RetryingHttpRequesterFilter.HttpResponseException("Retry 5XX", httpResponseMetaData) :
                            // Not a 5XX response, we do not know whether retry is required
                            null)
                    .retryResponses((meta, error) -> RetryingHttpRequesterFilter.BackOffPolicy.ofImmediate(2))
                    .build()
            );
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
