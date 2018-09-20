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
package io.servicetalk.examples.http.helloworld.async;

import io.servicetalk.http.api.HttpClient;
import io.servicetalk.http.netty.HttpClients;

import java.util.concurrent.CountDownLatch;

import static io.servicetalk.http.api.HttpSerializationProviders.deserializeText;

public final class HelloWorldClient {
    public static void main(String[] args) throws Exception {
        try (HttpClient client = HttpClients.forSingleAddress("localhost", 8080).build()) {
            // This example is demonstrating asynchronous execution, but needs to prevent the main thread from exiting
            // before the response has been processed. This isn't typical usage for a streaming API but is useful for
            // demonstration purposes.
            CountDownLatch responseProcessedLatch = new CountDownLatch(1);
            client.request(client.get("sayHello"))
                    .doFinally(responseProcessedLatch::countDown)
                    .subscribe(resp -> {
                        System.out.println(resp.toString((name, value) -> value));
                        System.out.println(resp.getPayloadBody(deserializeText()));
                    });

            responseProcessedLatch.await();
        }
    }
}
