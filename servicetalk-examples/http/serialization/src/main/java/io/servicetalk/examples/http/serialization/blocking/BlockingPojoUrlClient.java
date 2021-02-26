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
package io.servicetalk.examples.http.serialization.blocking;

import io.servicetalk.examples.http.serialization.CreatePojoRequest;
import io.servicetalk.http.api.BlockingHttpClient;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.netty.HttpClients;

import static io.servicetalk.examples.http.serialization.SerializerUtils.REQ_SERIALIZER;
import static io.servicetalk.examples.http.serialization.SerializerUtils.RESP_SERIALIZER;

public final class BlockingPojoUrlClient {
    public static void main(String[] args) throws Exception {
        try (BlockingHttpClient client = HttpClients.forMultiAddressUrl().buildBlocking()) {
            HttpResponse resp = client.request(client.post("http://localhost:8080/pojos")
                    .payloadBody(new CreatePojoRequest("value"), REQ_SERIALIZER));
            System.out.println(resp.toString((name, value) -> value));
            System.out.println(resp.payloadBody(RESP_SERIALIZER));
        }
    }
}
