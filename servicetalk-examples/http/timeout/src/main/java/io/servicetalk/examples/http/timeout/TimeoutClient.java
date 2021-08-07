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
package io.servicetalk.examples.http.timeout;

import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.HttpClient;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.netty.HttpClients;
import io.servicetalk.http.utils.TimeoutHttpRequesterFilter;

import java.time.Duration;

import static io.servicetalk.concurrent.api.Single.collectUnorderedDelayError;
import static io.servicetalk.http.api.HttpSerializers.textSerializerUtf8;
import static java.time.Duration.ofSeconds;

/**
 * Extends the async 'Hello World!' example to demonstrate use of timeout filters and timeout operators. If a single
 * timeout can be applied to all transactions then the timeout should be applied using the
 * {@link TimeoutHttpRequesterFilter}. If only some transactions require a timeout then the timeout should be applied
 * using a {@link io.servicetalk.concurrent.api.Single#timeout(Duration)} Single.timeout()} or a
 * {@link io.servicetalk.concurrent.api.Publisher#timeoutTerminal(Duration)} (Duration)} Publisher.timeoutTerminal()}
 * operator.
 */
public final class TimeoutClient {
    public static void main(String[] args) throws Exception {
        try (HttpClient client = HttpClients.forSingleAddress("localhost", 8080)
                // Filter enforces that requests made with this client must fully complete
                // within 10 seconds or will be cancelled.
                .appendClientFilter(new TimeoutHttpRequesterFilter(ofSeconds(10), true))
                .build()) {
            // first request, with default timeout from HttpClient (this will succeed)
            Single<HttpResponse> respSingle1 = client.request(client.get("/defaultTimeout"))
                    .whenOnError(System.err::println)
                    .whenOnSuccess(resp -> {
                        System.out.println(resp.toString((name, value) -> value));
                        System.out.println(resp.payloadBody(textSerializerUtf8()));
                    });

            // second request, with custom timeout that is lower than the client default (this will timeout)
            Single<HttpResponse> respSingle2 = client.request(client.get("/3secondTimeout"))
                    // This request and response must complete within 3 seconds or the request will be cancelled.
                    .timeout(ofSeconds(3))
                    .whenOnError(System.err::println)
                    .whenOnSuccess(resp -> {
                        System.out.println(resp.toString((name, value) -> value));
                        System.out.println(resp.payloadBody(textSerializerUtf8()));
                    });

            // Issue the requests in parallel.
            collectUnorderedDelayError(respSingle1, respSingle2)
            // This example is demonstrating asynchronous execution, but needs to prevent the main thread from exiting
            // before the response has been processed. This isn't typical usage for an asynchronous API but is useful
            // for demonstration purposes.
                    .toFuture().get();
        }
    }
}
