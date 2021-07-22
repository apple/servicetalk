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
package io.servicetalk.examples.http.serialization.blocking.streaming;

import io.servicetalk.concurrent.BlockingIterator;
import io.servicetalk.examples.http.serialization.CreatePojoRequest;
import io.servicetalk.examples.http.serialization.PojoResponse;
import io.servicetalk.http.api.BlockingStreamingHttpClient;
import io.servicetalk.http.api.BlockingStreamingHttpResponse;
import io.servicetalk.http.netty.HttpClients;

import static io.servicetalk.examples.http.serialization.SerializerUtils.REQ_STREAMING_SERIALIZER;
import static io.servicetalk.examples.http.serialization.SerializerUtils.RESP_STREAMING_SERIALIZER;
import static java.util.Arrays.asList;

public final class BlockingPojoStreamingClient {
    public static void main(String[] args) throws Exception {
        try (BlockingStreamingHttpClient client =
                     HttpClients.forSingleAddress("localhost", 8080).buildBlockingStreaming()) {
            BlockingStreamingHttpResponse response = client.request(client.post("/pojos")
                    .payloadBody(asList(
                            new CreatePojoRequest("value1"),
                            new CreatePojoRequest("value2"),
                            new CreatePojoRequest("value3")),
                            REQ_STREAMING_SERIALIZER));
            System.out.println(response);
            try (BlockingIterator<PojoResponse> payload = response.payloadBody(RESP_STREAMING_SERIALIZER).iterator()) {
                while (payload.hasNext()) {
                    System.out.println(payload.next());
                }
            }
        }
    }
}
