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
package io.servicetalk.examples.http.serialization.async;

import io.servicetalk.examples.http.serialization.CreatePojoRequest;
import io.servicetalk.http.api.HttpClient;
import io.servicetalk.http.netty.HttpClients;

import static io.servicetalk.examples.http.serialization.SerializerUtils.REQ_SERIALIZER;
import static io.servicetalk.examples.http.serialization.SerializerUtils.RESP_SERIALIZER;

public final class PojoUrlClient {
    public static void main(String[] args) throws Exception {
        try (HttpClient client = HttpClients.forMultiAddressUrl().build()) {
            client.request(client.post("http://localhost:8080/pojos")
                    .payloadBody(new CreatePojoRequest("value"), REQ_SERIALIZER))
                    .whenOnSuccess(resp -> {
                        System.out.println(resp.toString((name, value) -> value));
                        System.out.println(resp.payloadBody(RESP_SERIALIZER));
                    })
            // This example is demonstrating asynchronous execution, but needs to prevent the main thread from exiting
            // before the response has been processed. This isn't typical usage for an asynchronous API but is useful
            // for demonstration purposes.
                    .toFuture().get();
        }
    }
}
