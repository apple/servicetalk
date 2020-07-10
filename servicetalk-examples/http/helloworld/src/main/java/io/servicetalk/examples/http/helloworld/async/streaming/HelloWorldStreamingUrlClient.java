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
package io.servicetalk.examples.http.helloworld.async.streaming;

import io.servicetalk.http.api.StreamingHttpClient;
import io.servicetalk.http.netty.HttpClients;

import java.util.concurrent.CountDownLatch;

import static io.servicetalk.http.api.HttpSerializationProviders.textDeserializer;

public final class HelloWorldStreamingUrlClient {

    public static void main(String[] args) throws Exception {
        try (StreamingHttpClient client = HttpClients.forMultiAddressUrl().buildStreaming()) {
            // This example is demonstrating asynchronous execution, but needs to prevent the main thread from exiting
            // before the response has been processed. This isn't typical usage for a streaming API but is useful for
            // demonstration purposes.
            CountDownLatch responseProcessedLatch = new CountDownLatch(1);
            client.request(client.get("http://localhost:8080/sayHello"))
                    .beforeOnSuccess(response -> System.out.println(response.toString((name, value) -> value)))
                    .flatMapPublisher(resp -> resp.payloadBody(textDeserializer()))
                    .afterFinally(responseProcessedLatch::countDown)
                    .forEach(System.out::println);

            responseProcessedLatch.await();
        }
    }
}
