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
package io.servicetalk.examples.http.serialization.async.streaming;

import io.servicetalk.examples.http.serialization.CreatePojoRequest;
import io.servicetalk.http.api.StreamingHttpClient;
import io.servicetalk.http.netty.HttpClients;

import static io.servicetalk.concurrent.api.Publisher.from;
import static io.servicetalk.examples.http.serialization.SerializerUtils.REQ_STREAMING_SERIALIZER;
import static io.servicetalk.examples.http.serialization.SerializerUtils.RESP_STREAMING_SERIALIZER;

public final class PojoStreamingUrlClient {
    public static void main(String[] args) throws Exception {
        try (StreamingHttpClient client = HttpClients.forMultiAddressUrl().buildStreaming()) {
            client.request(client.post("http://localhost:8080/pojos")
                    .payloadBody(from("value1", "value2", "value3").map(CreatePojoRequest::new),
                            REQ_STREAMING_SERIALIZER))
                    .beforeOnSuccess(response -> System.out.println(response.toString((name, value) -> value)))
                    .flatMapPublisher(resp -> resp.payloadBody(RESP_STREAMING_SERIALIZER))
                    .whenOnNext(System.out::println)
            // This example is demonstrating asynchronous execution, but needs to prevent the main thread from exiting
            // before the response has been processed. This isn't typical usage for an asynchronous API but is useful
            // for demonstration purposes.
                    .toFuture().get();
        }
    }
}
