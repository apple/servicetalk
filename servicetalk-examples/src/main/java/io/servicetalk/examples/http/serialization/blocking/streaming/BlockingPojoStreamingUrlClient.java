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
import io.servicetalk.data.jackson.JacksonSerializationProvider;
import io.servicetalk.examples.http.serialization.MyPojo;
import io.servicetalk.examples.http.serialization.PojoRequest;
import io.servicetalk.http.api.BlockingStreamingHttpClient;
import io.servicetalk.http.api.BlockingStreamingHttpResponse;
import io.servicetalk.http.api.HttpSerializationProvider;
import io.servicetalk.http.netty.HttpClients;

import static io.servicetalk.http.api.HttpSerializationProviders.serializeJson;
import static java.util.Arrays.asList;

public class BlockingPojoStreamingUrlClient {

    public static void main(String[] args) throws Exception {
        HttpSerializationProvider serializer = serializeJson(new JacksonSerializationProvider());
        try (BlockingStreamingHttpClient client = HttpClients.forMultiAddressUrl().buildBlockingStreaming()) {
            BlockingStreamingHttpResponse response = client.request(client.get("http://localhost:8080/pojo")
                    .serializePayloadBody(asList(new PojoRequest("1"), new PojoRequest("2"), new PojoRequest("3")),
                            serializer.serializerFor(PojoRequest.class)));
            System.out.println(response);
            try (BlockingIterator<MyPojo> payload =
                         response.deserializePayloadBody(serializer.deserializerFor(MyPojo.class)).iterator()) {
                while (payload.hasNext()) {
                    System.out.println(payload.next());
                }
            }
        }
    }
}
